package com.example.rag;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    private final OllamaClient ollama;
    private final QdrantClient qdrant;
    private final String docsPath;
    private final int chunkSize;
    private final int overlap;
    private final int startupDelay;

    public DocumentLoader(OllamaClient ollama, QdrantClient qdrant,
                          @Value("${rag.docs-path}") String docsPath,
                          @Value("${rag.chunk-size}") int chunkSize,
                          @Value("${rag.chunk-overlap}") int overlap,
                          @Value("${rag.startup-delay-seconds}") int startupDelay) {
        this.ollama = ollama;
        this.qdrant = qdrant;
        this.docsPath = docsPath;
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        this.startupDelay = startupDelay;
    }

    @PostConstruct
    public void load() throws IOException, InterruptedException {
        // Ollama'nın model'i indirmesini bekle (ilk açılışta nomic-embed-text ~270 MB)
        log.info("Waiting {}s for Ollama to be ready...", startupDelay);
        Thread.sleep(startupDelay * 1000L);

        qdrant.ensureCollection();

        Path dir = Path.of(docsPath);
        if (!Files.exists(dir)) {
            log.warn("Docs path not found: {}", dir.toAbsolutePath());
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".txt"))
                 .forEach(this::ingest);
        }
        log.info("Ingestion complete.");
    }

    private void ingest(Path file) {
        try {
            String content = Files.readString(file);
            String source = file.getFileName().toString();
            int count = 0;
            for (String chunk : split(content)) {
                List<Float> embedding = ollama.embed(chunk);
                qdrant.upsert(UUID.randomUUID().toString(), embedding, chunk, source);
                count++;
            }
            log.info("Indexed {} chunks from {}", count, source);
        } catch (IOException e) {
            log.error("Failed to read {}", file, e);
        }
    }

    private List<String> split(String text) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(i + chunkSize, text.length());
            out.add(text.substring(i, end));
            if (end == text.length()) break;
            i += chunkSize - overlap;
        }
        return out;
    }
}
