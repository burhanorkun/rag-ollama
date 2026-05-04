package com.example.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/** Minimal Qdrant REST client. */
@Component
public class QdrantClient {

    public record Hit(String text, String source, double score) {}

    private final WebClient client;
    private final String collection;
    private final int vectorSize;

    public QdrantClient(@Value("${qdrant.url}") String url,
                        @Value("${qdrant.collection}") String collection,
                        @Value("${qdrant.vector-size}") int vectorSize) {
        this.collection = collection;
        this.vectorSize = vectorSize;
        this.client = WebClient.builder()
                .baseUrl(url)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    public void ensureCollection() {
        Map<String, Object> body = Map.of(
                "vectors", Map.of("size", vectorSize, "distance", "Cosine")
        );
        client.put()
                .uri("/collections/{name}", collection)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                .block();
    }

    public void upsert(String id, List<Float> vector, String text, String source) {
        Map<String, Object> point = Map.of(
                "id", id,
                "vector", vector,
                "payload", Map.of("text", text, "source", source)
        );
        client.put()
                .uri("/collections/{name}/points?wait=true", collection)
                .bodyValue(Map.of("points", List.of(point)))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<Hit> search(List<Float> queryVector, int topK) {
        Map<String, Object> body = Map.of(
                "vector", queryVector,
                "limit", topK,
                "with_payload", true
        );
        Map<String, Object> response = client.post()
                .uri("/collections/{name}/points/search", collection)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        assert response != null;
        List<Map<String, Object>> result = (List<Map<String, Object>>) response.get("result");
        List<Hit> hits = new ArrayList<>();
        for (Map<String, Object> r : result) {
            Map<String, Object> payload = (Map<String, Object>) r.get("payload");
            hits.add(new Hit(
                    (String) payload.get("text"),
                    (String) payload.get("source"),
                    ((Number) r.get("score")).doubleValue()
            ));
        }
        return hits;
    }
}
