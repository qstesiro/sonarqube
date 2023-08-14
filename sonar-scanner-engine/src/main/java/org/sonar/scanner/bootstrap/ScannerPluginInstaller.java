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
package org.sonar.scanner.bootstrap;

import com.google.gson.Gson;
import java.io.File;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.core.platform.PluginInfo;
import org.sonar.api.config.Configuration;
import org.sonarqube.ws.client.GetRequest;

import static java.lang.String.format;

/**
 * Downloads the plugins installed on server and stores them in a local user cache
 */
public class ScannerPluginInstaller implements PluginInstaller {

    private static final Logger LOG = Loggers.get(ScannerPluginInstaller.class);
    private static final String PLUGINS_WS_URL = "api/plugins/installed";

    private final PluginFiles pluginFiles;
    private final DefaultScannerWsClient wsClient;
    private final Configuration config;

    public ScannerPluginInstaller(PluginFiles pluginFiles, DefaultScannerWsClient wsClient, Configuration config) {

        // Stream.of(Thread.currentThread().getStackTrace())
        //     .forEach(e -> LOG.info("--- {}", e)); // ???
        this.pluginFiles = pluginFiles;
        this.wsClient = wsClient;
        this.config = config;
    }

    @Override
    public Map<String, ScannerPlugin> installRemotes() {
        loadPluginConfig();
        Profiler profiler = Profiler.create(LOG).startInfo("Load/download plugins");
        try {
            Map<String, ScannerPlugin> result = new HashMap<>();
            Loaded loaded = loadPlugins(result);
            if (!loaded.ok) {
                // retry once, a plugin may have been uninstalled during downloads
                result.clear();
                loaded = loadPlugins(result);
                if (!loaded.ok) {
                    throw new IllegalStateException(
                        format(
                            "Fail to download plugin [%s]. Not found.",
                            loaded.notFoundPlugin
                        )
                    );
                }
            }
            return result;
        } finally {
            profiler.stopInfo();
        }
    }

    private Map<String, Boolean> inPlugins = new HashMap<>();
    private Map<String, Boolean> exPlugins = new HashMap<>();

    private static final String PluginInclusions = "sonar.plugin.inclusions";
    private static final String PluginExclusions = "sonar.plugin.exclusions";

    private void loadPluginConfig() {
        for (String key : config.get(PluginInclusions).orElse("").trim().split(",")) {
            key = key.trim();
            if (!key.equals("")) {
                inPlugins.put(key, false);
            }
        }
        inPlugins.forEach((k, v) -> LOG.info("--- key: {}, value: {}", k, v));
        for (String key : config.get(PluginExclusions).orElse("").trim().split(",")) {
            key = key.trim();
            if (!key.equals("")) {
                exPlugins.put(key.trim(), false);
            }
        }
        exPlugins.forEach((k, v) -> LOG.info("--- key: {}, value: {}", k, v));
    }

    private Loaded loadPlugins(Map<String, ScannerPlugin> result) {
        for (InstalledPlugin plugin : listInstalledPlugins()) {
            String key = plugin.key.trim();
            if (inPlugins.size() > 0) {
                if (inPlugins.containsKey(key)) {
                    Loaded loaded = loadPlugin(result, plugin);
                    if (!loaded.ok) {
                        return loaded;
                    }
                    inPlugins.put(key, true);
                }
                continue;
            }
            if (exPlugins.size() > 0) {
                if (!exPlugins.containsKey(key)) {
                    Loaded loaded = loadPlugin(result, plugin);
                    if (!loaded.ok) {
                        return loaded;
                    }
                    exPlugins.put(key, true);
                }
                continue;
            }
            Loaded loaded = loadPlugin(result, plugin);
            if (!loaded.ok) {
                return loaded;
            }
        }
        return new Loaded(true, null);
    }

    private Loaded loadPlugin(Map<String, ScannerPlugin> result, InstalledPlugin plugin) {
        Optional<File> jarFile = pluginFiles.get(plugin);
        if (!jarFile.isPresent()) {
            return new Loaded(false, plugin.key);
        }
        PluginInfo info = PluginInfo.create(jarFile.get());
        result.put(info.getKey(), new ScannerPlugin(plugin.key, plugin.updatedAt, info));
        return new Loaded(true, null);
    }

    /**
     * Returns empty on purpose. This method is used only by medium tests.
     */
    @Override
    public List<Object[]> installLocals() {
        return Collections.emptyList();
    }

    /**
     * Gets information about the plugins installed on server (filename, checksum)
     */
    private InstalledPlugin[] listInstalledPlugins() {
        Profiler profiler = Profiler.create(LOG).startInfo("Load plugins index");
        GetRequest getRequest = new GetRequest(PLUGINS_WS_URL);
        InstalledPlugins installedPlugins;
        try (Reader reader = wsClient.call(getRequest).contentReader()) {
            installedPlugins = new Gson().fromJson(reader, InstalledPlugins.class);
        } catch (Exception e) {
            throw new IllegalStateException("Fail to parse response of " + PLUGINS_WS_URL, e);
        }
        profiler.stopInfo();
        return installedPlugins.plugins;
    }

    private static class InstalledPlugins {

        InstalledPlugin[] plugins;

        public InstalledPlugins() {
            // http://stackoverflow.com/a/18645370/229031
        }
    }

    static class InstalledPlugin {

        String key;
        String hash;
        long updatedAt;

        public InstalledPlugin() {
            // http://stackoverflow.com/a/18645370/229031
        }
    }

    private static class Loaded {

        private final boolean ok;
        @Nullable
        private final String notFoundPlugin;

        private Loaded(boolean ok, @Nullable String notFoundPlugin) {
            this.ok = ok;
            this.notFoundPlugin = notFoundPlugin;
        }
    }
}
