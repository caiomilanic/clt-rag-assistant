package br.com.caiomilani.cltassistant.api;

import br.com.caiomilani.cltassistant.shared.Disclaimer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de fumaça do passo 2: prova que app -> Ollama funciona, SEM RAG ainda.
 * Será substituído pelo endpoint de RAG no passo 4.
 */
@RestController
@RequestMapping("/api/smoke")
public class SmokeController {

    private final ChatClient chatClient;
    private final String modelLabel;

    public SmokeController(ChatClient chatClient, @Value("${app.llm.model-label}") String modelLabel) {
        this.chatClient = chatClient;
        this.modelLabel = modelLabel;
    }

    public record AskRequest(@NotBlank @Size(max = 1000) String question) {
    }

    public record AskResponse(String answer, String model, long latencyMs,
                              Integer promptTokens, Integer completionTokens, String disclaimer) {
    }

    @PostMapping("/chat")
    public AskResponse chat(@Valid @RequestBody AskRequest request) {
        long start = System.nanoTime();

        ChatResponse response = chatClient.prompt()
                .user(request.question())
                .call()
                .chatResponse();

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        Usage usage = response.getMetadata().getUsage();

        return new AskResponse(
                response.getResult().getOutput().getText(),
                modelLabel,
                latencyMs,
                usage != null ? usage.getPromptTokens() : null,
                usage != null ? usage.getCompletionTokens() : null,
                Disclaimer.TEXT);
    }
}
