package com.flashgif.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Ensures the {@code media} alias exists, pointing at {@code media_v<n>}, on
 * startup. Idempotent: silent no-op if the alias already exists. Adding a v2
 * index later is a manual op (build v2 + atomic alias swap); this class only
 * bootstraps the v1 case.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class IndexInitializer implements ApplicationRunner {

    private final ElasticsearchClient client;

    @Value("${flashgif.search.index-alias}")
    private String alias;

    @Value("${flashgif.search.index-version}")
    private String version;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) throws Exception {
        boolean aliasExists = client.indices()
                .existsAlias(ExistsAliasRequest.of(b -> b.name(alias)))
                .value();
        if (aliasExists) {
            log.info("Elasticsearch alias '{}' already present — skipping bootstrap", alias);
            return;
        }

        String concreteIndex = alias + "_" + version;
        boolean indexExists = client.indices()
                .exists(ExistsRequest.of(b -> b.index(concreteIndex)))
                .value();
        if (!indexExists) {
            String mappingJson;
            try (InputStream in = new ClassPathResource("elasticsearch/media-mapping.json").getInputStream()) {
                mappingJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            client.indices().create(CreateIndexRequest.of(b -> b
                    .index(concreteIndex)
                    .withJson(new java.io.StringReader("{\"mappings\":" + mappingJson + "}"))));
            log.info("Created Elasticsearch index '{}'", concreteIndex);
        }

        client.indices().updateAliases(UpdateAliasesRequest.of(b -> b
                .actions(Action.of(a -> a.add(add -> add.index(concreteIndex).alias(alias))))));
        log.info("Pointed alias '{}' at index '{}'", alias, concreteIndex);
    }
}
