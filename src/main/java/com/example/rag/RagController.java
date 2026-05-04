package com.example.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class RagController {

    private static final String SYSTEM_PROMPT = """
            You are a helpful assistant. Answer the user's question using ONLY the provided context.
            If the answer is not in the context, say "I don't know based on the provided documents."
            Cite the source filename in parentheses after each fact.
            """;

    private final OllamaClient ollama;
    private final QdrantClient qdrant;
    private final ClaudeClient claude;
    private final int topK;

    public RagController(OllamaClient ollama, QdrantClient qdrant, ClaudeClient claude,
                         @Value("${rag.top-k}") int topK) {
        this.ollama = ollama;
        this.qdrant = qdrant;
        this.claude = claude;
        this.topK = topK;
    }

    public record AskRequest(String question) {}
    public record AskResponse(String answer, List<String> sources) {}

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest req) {
        List<Float> queryEmb = ollama.embed(req.question());
        List<QdrantClient.Hit> hits = qdrant.search(queryEmb, topK);

        String context = hits.stream()
                .map(h -> "[" + h.source() + "]\n" + h.text())
                .collect(Collectors.joining("\n\n---\n\n"));

        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + req.question();
        String answer = claude.chat(SYSTEM_PROMPT, userPrompt);

        List<String> sources = hits.stream().map(QdrantClient.Hit::source).distinct().toList();
        return new AskResponse(answer, sources);
    }
}
