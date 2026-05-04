package com.example.rag;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClaudeClient {

    private final AnthropicClient client;

    public ClaudeClient(@Value("${anthropic.api-key}") String apiKey) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    public String chat(String systemPrompt, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_7)
                .maxTokens(4096L)
                .system(systemPrompt)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .addUserMessage(userPrompt)
                .build();

        StringBuilder out = new StringBuilder();
        for (ContentBlock block : client.messages().create(params).content()) {
            block.text().ifPresent(t -> out.append(t.text()));
        }
        return out.toString();
    }
}
