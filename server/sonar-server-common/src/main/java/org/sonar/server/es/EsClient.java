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
package org.sonar.server.es;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;
import org.apache.http.HttpHost;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheRequest;
import org.elasticsearch.action.admin.indices.cache.clear.ClearIndicesCacheResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeRequest;
import org.elasticsearch.action.admin.indices.forcemerge.ForceMergeResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.Cancellable;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.Priority;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.server.es.response.ClusterStatsResponse;
import org.sonar.server.es.response.IndicesStatsResponse;
import org.sonar.server.es.response.NodeStatsResponse;

import static org.sonar.server.es.EsRequestDetails.computeDetailsAsString;

/**
 * Wrapper to connect to Elasticsearch node. Handles correctly errors (logging + exceptions
 * with context) and profiling of requests.
 */
public class EsClient implements Closeable {

    private final RestHighLevelClient restHighLevelClient;
    private final Gson gson;

    public static final Logger LOGGER = Loggers.get("es");
    // public static final Logger LOGGER = Loggers.get(EsClient.class);

    public EsClient(HttpHost... hosts) {
        this(new MinimalRestHighLevelClient(hosts));
    }

    public EsClient(String user, String password, HttpHost... hosts) {
        this(new MinimalRestHighLevelClient(user, password, hosts));
    }

    EsClient(RestHighLevelClient restHighLevelClient) {
        this.restHighLevelClient = restHighLevelClient;
        this.gson = new GsonBuilder().create();
    }

    public BulkResponse bulk(BulkRequest bulkRequest) {
        LOGGER.info("--- EsClient.bulk");
        return execute(() -> restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT));
    }

    public Cancellable bulkAsync(BulkRequest bulkRequest, ActionListener<BulkResponse> listener) {
        LOGGER.info("--- EsClient.bulkAsync");
        return restHighLevelClient.bulkAsync(bulkRequest, RequestOptions.DEFAULT, listener);
    }

    public static SearchRequest prepareSearch(String indexName) {
        LOGGER.info("--- EsClient.prepareSearch");
        return Requests.searchRequest(indexName);
    }

    public static SearchRequest prepareSearch(IndexType.IndexMainType mainType) {
        LOGGER.info("--- EsClient.prepareSearch");
        return Requests.searchRequest(mainType.getIndex().getName()).types(mainType.getType());
    }

    public static SearchRequest prepareSearch(String index, String type) {
        LOGGER.info("--- EsClient.prepareSearch");
        return Requests.searchRequest(index).types(type);
    }

    public SearchResponse search(SearchRequest searchRequest) {
        LOGGER.info("--- EsClient.");
        return execute(() -> restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(searchRequest));
    }

    public SearchResponse scroll(SearchScrollRequest searchScrollRequest) {
        LOGGER.info("--- EsClient.scroll");
        return execute(() -> restHighLevelClient.scroll(searchScrollRequest, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(searchScrollRequest));
    }

    public ClearScrollResponse clearScroll(ClearScrollRequest clearScrollRequest) {
        LOGGER.info("--- EsClient.clearScroll");
        return execute(() -> restHighLevelClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT));
    }

    public DeleteResponse delete(DeleteRequest deleteRequest) {
        LOGGER.info("--- EsClient.delete");
        return execute(() -> restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(deleteRequest));
    }

    public RefreshResponse refresh(Index... indices) {
        LOGGER.info("--- EsClient.refresh");
        RefreshRequest refreshRequest = new RefreshRequest()
            .indices(Arrays.stream(indices).map(Index::getName).toArray(String[]::new));
        return execute(() -> restHighLevelClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(refreshRequest));
    }

    public ForceMergeResponse forcemerge(ForceMergeRequest forceMergeRequest) {
        LOGGER.info("--- EsClient.forcemerge");
        return execute(() -> restHighLevelClient.indices().forcemerge(forceMergeRequest, RequestOptions.DEFAULT));
    }

    public AcknowledgedResponse putSettings(UpdateSettingsRequest req) {
        LOGGER.info("--- EsClient.putSettings");
        return execute(() -> restHighLevelClient.indices().putSettings(req, RequestOptions.DEFAULT));
    }

    public ClearIndicesCacheResponse clearCache(ClearIndicesCacheRequest request) {
        LOGGER.info("--- EsClient.clearCache");
        return execute(() -> restHighLevelClient.indices().clearCache(request, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(request));
    }

    public IndexResponse index(IndexRequest indexRequest) {
        LOGGER.info("--- EsClient.index");
        return execute(() -> restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(indexRequest));
    }

    public GetResponse get(GetRequest request) {
        LOGGER.info("--- EsClient.get");
        return execute(() -> restHighLevelClient.get(request, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(request));
    }

    public GetIndexResponse getIndex(GetIndexRequest getRequest) {
        LOGGER.info("--- EsClient.");
        return execute(() -> restHighLevelClient.indices().get(getRequest, RequestOptions.DEFAULT));
    }

    public boolean indexExists(GetIndexRequest getIndexRequest) {
        LOGGER.info("--- EsClient.indexExists");
        return execute(() -> restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(getIndexRequest));
    }

    public CreateIndexResponse create(CreateIndexRequest createIndexRequest) {
        LOGGER.info("--- EsClient.create");
        return execute(() -> restHighLevelClient.indices().create(createIndexRequest, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(createIndexRequest));
    }

    public AcknowledgedResponse deleteIndex(DeleteIndexRequest deleteIndexRequest) {
        LOGGER.info("--- EsClient.deleteIndex");
        return execute(() -> restHighLevelClient.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT));
    }

    public AcknowledgedResponse putMapping(PutMappingRequest request) {
        LOGGER.info("--- EsClient.putMapping");
        return execute(() -> restHighLevelClient.indices().putMapping(request, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(request));
    }

    public ClusterHealthResponse clusterHealth(ClusterHealthRequest clusterHealthRequest) {
        LOGGER.info("--- EsClient.clusterHealth");
        return execute(() -> restHighLevelClient.cluster().health(clusterHealthRequest, RequestOptions.DEFAULT),
                       () -> computeDetailsAsString(clusterHealthRequest));
    }

    public void waitForStatus(ClusterHealthStatus clusterHealthStatus) {
        LOGGER.info("--- EsClient.waitForStatus");
        clusterHealth(new ClusterHealthRequest()
                      .waitForEvents(Priority.LANGUID)
                      .waitForStatus(clusterHealthStatus));
    }

    // https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-nodes-stats.html
    public NodeStatsResponse nodesStats() {
        LOGGER.info("--- EsClient.nodesStats");
        return execute(() -> {
                Request request = new Request("GET", "/_nodes/stats/fs,process,jvm,indices,breaker");
                Response response = restHighLevelClient.getLowLevelClient().performRequest(request);
                return NodeStatsResponse.toNodeStatsResponse(gson
                                                             .fromJson(EntityUtils.toString(response.getEntity()),
                                                                       JsonObject.class));
            });
    }

    // https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-stats.html
    public IndicesStatsResponse indicesStats(String... indices) {
        LOGGER.info("--- EsClient.indicesStats");
        return execute(() -> {
                Request request = new Request("GET", "/" +
                                              (indices.length > 0 ? (String.join(",", indices) + "/") : "") +
                                              "_stats");
                request.addParameter("level", "shards");
                Response response = restHighLevelClient
                    .getLowLevelClient()
                    .performRequest(request);
                return IndicesStatsResponse
                    .toIndicesStatsResponse(gson
                                            .fromJson(EntityUtils.toString(response.getEntity()),
                                                      JsonObject.class));
            }, () -> computeDetailsAsString(indices));
    }

    // https://www.elastic.co/guide/en/elasticsearch/reference/current/cluster-stats.html
    public ClusterStatsResponse clusterStats() {
        LOGGER.info("--- EsClient.clusterStats");
        return execute(() -> {
                Request request = new Request("GET", "/_cluster/stats");
                Response response = restHighLevelClient.getLowLevelClient().performRequest(request);
                return ClusterStatsResponse
                    .toClusterStatsResponse(gson
                                            .fromJson(EntityUtils.toString(response.getEntity()),
                                                      JsonObject.class));
            });
    }

    public GetSettingsResponse getSettings(GetSettingsRequest getSettingsRequest) {
        LOGGER.info("--- EsClient.getSettings");
        return execute(() -> restHighLevelClient
                       .indices()
                       .getSettings(getSettingsRequest, RequestOptions.DEFAULT));
    }

    public GetMappingsResponse getMapping(GetMappingsRequest getMappingsRequest) {
        LOGGER.info("--- EsClient.getMapping");
        return execute(() -> restHighLevelClient
                       .indices()
                       .getMapping(getMappingsRequest, RequestOptions.DEFAULT));
    }

    @Override
    public void close() {
        try {
            restHighLevelClient.close();
        } catch (IOException e) {
            throw new ElasticsearchException("Could not close ES Rest high level client", e);
        }
    }

    /**
     * Internal usage only
     *
     * @return native ES client object
     */
    RestHighLevelClient nativeClient() {
        return restHighLevelClient;
    }

    static class MinimalRestHighLevelClient extends RestHighLevelClient {

        public MinimalRestHighLevelClient(HttpHost... hosts) {
            super(RestClient.builder(hosts));
        }

        public MinimalRestHighLevelClient(String user, String password, HttpHost... hosts) {
            super(RestClient
                  .builder(hosts)
                  .setHttpClientConfigCallback(builder -> {
                          CredentialsProvider provider = new BasicCredentialsProvider();
                          provider.setCredentials(AuthScope.ANY,
                                                  new UsernamePasswordCredentials(user, password));
                          return builder
                              .disableAuthCaching()
                              .setDefaultCredentialsProvider(provider);
                      }));
        }

        MinimalRestHighLevelClient(RestClient restClient) {
            super(restClient, RestClient::close, Lists.newArrayList());
        }
    }

    <R> R execute(EsRequestExecutor<R> executor) {
        return execute(executor, () -> "");
    }

    <R> R execute(EsRequestExecutor<R> executor, Supplier<String> requestDetails) {
        Profiler profiler = Profiler.createIfTrace(EsClient.LOGGER).start();
        try {
            return executor.execute();
        } catch (Exception e) {
            throw new ElasticsearchException("Fail to execute es request" + requestDetails.get(), e);
        } finally {
            if (profiler.isTraceEnabled()) {
                profiler.stopTrace(requestDetails.get());
            }
        }
    }

    @FunctionalInterface
    interface EsRequestExecutor<R> {
        R execute() throws IOException;
    }

}
