/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.ce.taskprocessor;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.ce.queue.InternalCeQueue;
import org.sonar.ce.task.CeTask;
import org.sonar.ce.task.CeTaskInterruptedException;
import org.sonar.ce.task.CeTaskResult;
import org.sonar.ce.task.taskprocessor.CeTaskProcessor;
import org.sonar.core.util.logs.Profiler;
import org.sonar.db.ce.CeActivityDto;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static org.sonar.ce.task.CeTaskInterruptedException.isTaskInterruptedException;
import static org.sonar.ce.taskprocessor.CeWorker.Result.DISABLED;
import static org.sonar.ce.taskprocessor.CeWorker.Result.NO_TASK;
import static org.sonar.ce.taskprocessor.CeWorker.Result.TASK_PROCESSED;
import static org.sonar.db.ce.CeActivityDto.Status.FAILED;

public class CeWorkerImpl implements CeWorker {

    private static final Logger LOG = Loggers.get(CeWorkerImpl.class);

    private final int ordinal;
    private final String uuid;
    private final InternalCeQueue queue;
    private final CeTaskProcessorRepository taskProcessorRepository;
    private final CeWorkerController ceWorkerController;
    private final List<ExecutionListener> listeners;
    private final AtomicReference<RunningState> runningState = new AtomicReference<>();
    private boolean indexationTaskLookupEnabled;
    private boolean excludeIndexationJob;

    public CeWorkerImpl(int ordinal, String uuid,
                        InternalCeQueue queue, CeTaskProcessorRepository taskProcessorRepository,
                        CeWorkerController ceWorkerController,
                        ExecutionListener... listeners) {
        LOG.info("--- CeWorkerImpl.CeWorkerImpl");
        this.ordinal = checkOrdinal(ordinal);
        this.uuid = uuid;
        this.queue = queue;
        this.taskProcessorRepository = taskProcessorRepository;
        this.ceWorkerController = ceWorkerController;
        this.listeners = Arrays.asList(listeners);
        indexationTaskLookupEnabled = true;
        excludeIndexationJob = false;
    }

    private static int checkOrdinal(int ordinal) {
        checkArgument(ordinal >= 0, "Ordinal must be >= 0");
        return ordinal;
    }

    @Override
    public Result call() {
        try (TrackRunningState trackRunningState = new TrackRunningState(this::findAndProcessTask)) {
            LOG.info("--- CeWorkerImpl.call {}", Thread.currentThread().getId());
            // ???
            // Stream.of(Thread.currentThread().getStackTrace())
            //     .forEach(e -> LOG.info("--- call - {}", e));
            return trackRunningState.get();
        }
    }

    @Override
    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public String getUUID() {
        return uuid;
    }

    @Override
    public boolean isExecutedBy(Thread thread) {
        return Optional.ofNullable(runningState.get())
            .filter(state -> state.runningThread.equals(thread))
            .isPresent();
    }

    @Override
    public Optional<CeTask> getCurrentTask() {
        return Optional.ofNullable(runningState.get())
            .flatMap(RunningState::getTask);
    }

    private class TrackRunningState implements AutoCloseable, Supplier<Result> {

        private final RunningState localRunningState;
        private final Function<RunningState, Result> delegate;
        private final String oldName;

        private TrackRunningState(Function<RunningState, Result> delegate) {
            Thread currentThread = Thread.currentThread();
            localRunningState = new RunningState(currentThread);
            if (!runningState.compareAndSet(null, localRunningState)) {
                LOG.warn("Worker {} (UUID=%s) starts executing with new Thread {} " +
                         "while running state isn't null. " +
                         "Forcefully updating Workers's running state to new Thread.",
                         getOrdinal(), getUUID(), currentThread);
                runningState.set(localRunningState);
            }
            this.delegate = delegate;
            this.oldName = currentThread.getName();
        }

        @Override
        public Result get() {
            localRunningState.runningThread
                .setName(String.format("Worker %s (UUID=%s) on %s",
                                       getOrdinal(), getUUID(), oldName));
            return delegate.apply(localRunningState);
        }

        @Override
        public void close() {
            localRunningState.runningThread.setName(oldName);
            if (!runningState.compareAndSet(localRunningState, null)) {
                LOG.warn("Worker {} (UUID=%s) ending execution in Thread {}" +
                         "while running state has already changed." +
                         " Keeping this new state.",
                         getOrdinal(), getUUID(), localRunningState.runningThread);
            }
        }
    }

    private Result findAndProcessTask(RunningState localRunningState) {
        LOG.info("--- CeWorkerImpl.findAndProcessTask");
        if (!ceWorkerController.isEnabled(this)) {
            return DISABLED;
        }
        Optional<CeTask> ceTask = tryAndFindTaskToExecute();
        if (!ceTask.isPresent()) {
            return NO_TASK;
        }
        LOG.info("--- CeWorkerImpl.findAndProcessTask {}", ceTask.get());
        try (CeWorkerController.ProcessingRecorderHook processing = ceWorkerController.registerProcessingFor(this);
             ExecuteTask executeTask = new ExecuteTask(localRunningState, ceTask.get())) {
            executeTask.run();
        } catch (Exception e) {
            LOG.error(format("An error occurred while executing task with uuid '%s'", ceTask.get().getUuid()), e);
        }
        return TASK_PROCESSED;
    }

    private Optional<CeTask> tryAndFindTaskToExecute() {
        try {
            if (indexationTaskLookupEnabled) { // 每次启动sonar查看一次
                return tryAndFindTaskToExecuteIncludingIndexation();
            } else { // 启动后第二次开始执行以下逻辑
                return queue.peek(uuid, true, false);
            }
        } catch (Exception e) {
            LOG.error("Failed to pop the queue of analysis reports", e);
        }
        return Optional.empty();
    }

    private Optional<CeTask> tryAndFindTaskToExecuteIncludingIndexation() {
        excludeIndexationJob = !excludeIndexationJob;
        Optional<CeTask> peek = queue.peek(uuid, excludeIndexationJob, true);
        if (peek.isPresent()) {
            return peek;
        }
        if (excludeIndexationJob) {
            peek = queue.peek(uuid, false, true);
            if (peek.isPresent()) {
                return peek;
            }
            // do not lookup for indexation tasks anymore
            indexationTaskLookupEnabled = false;
            LOG.info(String.format("worker %s found no pending task (including indexation task). Disabling indexation task lookup for this worker until next SonarQube restart.", uuid));
        }
        return Optional.empty();
    }

    private final class ExecuteTask implements Runnable, AutoCloseable {

        private final CeTask task;
        private final RunningState localRunningState; // 没有实际使用 ???
        private final Profiler ceProfiler;
        private CeActivityDto.Status status = FAILED;
        private CeTaskResult taskResult = null;
        private Throwable error = null;

        private ExecuteTask(RunningState localRunningState, CeTask task) {
            this.task = task;
            this.localRunningState = localRunningState;
            this.ceProfiler = startLogProfiler(task);
        }

        @Override
        public void run() {
            beforeExecute();
            LOG.info("--- component: {}", task.getComponent().toString());
            Optional<CeTask.Component> component = task.getComponent();
            if (component.isPresent()) {
                LOG.info("--- component.key {}", component.get().getKey());
                final String key = component.get().getKey().get();
                if (key.equals("console:wuxing-cs") ||
                    key.equals("console.wuxing-cs")) {
                    final long seconds = 300;
                    LOG.info("--- simulate time-consuming task enter {}s, threadId: {}",
                             seconds, Thread.currentThread().getId());
                    try {
                        Thread.sleep(seconds * 1000L);
                    } catch (Exception e) {
                        LOG.info("{}", e);
                    } finally {
                        LOG.info("--- simulate time-consuming task leave {}s, threadId: {}",
                             seconds, Thread.currentThread().getId());
                    }
                }
            }
            executeTask();
        }

        @Override
        public void close() {
            afterExecute();
        }

        private void beforeExecute() {
            LOG.info("--- ExecuteTask.beforeExecute {}", task);
            localRunningState.setTask(task);
            callListeners(t -> t.onStart(task));
        }

        private void executeTask() {
            try {
                // TODO delegate the message to the related task processor, according to task type
                Optional<CeTaskProcessor> taskProcessor = taskProcessorRepository.getForCeTask(task);
                if (taskProcessor.isPresent()) {
                    taskResult = taskProcessor.get().process(task);
                    status = CeActivityDto.Status.SUCCESS;
                } else {
                    LOG.error("No CeTaskProcessor is defined for task of type {}." +
                              "Plugin configuration may have changed",
                              task.getType());
                    status = FAILED;
                }
            } catch (MessageException e) {
                // error
                error = e;
            } catch (Throwable e) {
                Optional<CeTaskInterruptedException> taskInterruptedException = isTaskInterruptedException(e);
                if (taskInterruptedException.isPresent()) {
                    LOG.trace("Task interrupted", e);
                    CeTaskInterruptedException exception = taskInterruptedException.get();
                    CeActivityDto.Status interruptionStatus = exception.getStatus();
                    status = interruptionStatus;
                    error = (interruptionStatus == FAILED ? exception : null);
                } else {
                    // error
                    LOG.error("Failed to execute task {}", task.getUuid(), e);
                    error = e;
                }
            }
        }

        private void afterExecute() {
            LOG.info("--- ExecuteTask.afterExecute {}", task);
            localRunningState.setTask(null);
            finalizeTask(task, ceProfiler, status, taskResult, error);
        }

        private void finalizeTask(CeTask task, Profiler ceProfiler, CeActivityDto.Status status,
                                  @Nullable CeTaskResult taskResult, @Nullable Throwable error) {
            try {
                queue.remove(task, status, taskResult, error);
            } catch (Exception e) {
                if (error != null) {
                    e.addSuppressed(error);
                }
                LOG.error(format("Failed to finalize task with uuid '%s' and" +
                                 "persist its state to db",
                                 task.getUuid()), e);
            } finally {
                ceProfiler.addContext("status", status.name());
                long durationMs = ceProfiler.stopInfo("Executed task");
                Duration duration = Duration.of(durationMs, ChronoUnit.MILLIS);
                callListeners(t -> t.onEnd(task, status, duration, taskResult, error));
            }
        }

        private void callListeners(Consumer<ExecutionListener> call) {
            listeners.forEach(listener -> {
                    try {
                        Loggers.get(CeWorkerImpl.class).info("--- call accept enter");
                        call.accept(listener);
                        Loggers.get(CeWorkerImpl.class).info("--- call accept leave");
                    } catch (Throwable t) {
                        LOG.error(format("Call to listener %s failed.", listener.getClass().getSimpleName()), t);
                    }
                });
        }
    }

    private static Profiler startLogProfiler(CeTask task) {
        Profiler profiler = Profiler.create(LOG)
            .logTimeLast(true)
            .addContext("project", task.getMainComponent().flatMap(CeTask.Component::getKey).orElse(null))
            .addContext("type", task.getType());
        for (Map.Entry<String, String> characteristic : task.getCharacteristics().entrySet()) {
            profiler.addContext(characteristic.getKey(), characteristic.getValue());
        }
        return profiler
            .addContext("id", task.getUuid())
            .addContext("submitter", submitterOf(task))
            .startInfo("Execute task");
    }

    @CheckForNull
    private static String submitterOf(CeTask task) {
        CeTask.User submitter = task.getSubmitter();
        if (submitter == null) {
            return null;
        }
        String submitterLogin = submitter.getLogin();
        if (submitterLogin != null) {
            return submitterLogin;
        } else {
            return submitter.getUuid();
        }
    }

    private static final class RunningState {
        private final Thread runningThread;
        private CeTask task;

        private RunningState(Thread runningThread) {
            this.runningThread = runningThread;
        }

        public Optional<CeTask> getTask() {
            return Optional.ofNullable(task);
        }

        public void setTask(@Nullable CeTask task) {
            this.task = task;
        }
    }

}
