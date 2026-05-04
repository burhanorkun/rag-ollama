package com.example.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Local embedding via Ollama's HTTP API.
 * Endpoint: POST {ollama_url}/api/embeddings
 * Body:    { "model": "nomic-embed-text", "prompt": "..." }
 * Returns: { "embedding": [0.12, -0.34, ...] }   // 768-dim for nomic
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final WebClient client;
    private final String model;

    public OllamaClient(@Value("${ollama.url}") String url,
                        @Value("${ollama.embedding-model}") String model) {
        this.model = model;
        this.client = WebClient.builder()
                .baseUrl(url)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @SuppressWarnings("unchecked")
    public List<Float> embed(String text) {
        Map<String, Object> body = Map.of("model", model, "prompt", text);

        Map<String, Object> response = client.post()
                .uri("/api/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                // Model henüz indirilmemiş olabilir — birkaç saniye sonra tekrar dene
                .retryWhen(Retry.backoff(5, Duration.ofSeconds(3))
                        .doBeforeRetry(s -> log.warn("Ollama embed retry: {}", s.failure().getMessage())))
                .block();

        return ((List<Number>) response.get("embedding"))
                .stream().map(Number::floatValue).toList();
    }
}
