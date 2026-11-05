package br.com.caiomilani.cltassistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    /**
     * Prompt de sistema base. No passo 4 ele ganha as regras de RAG
     * (responder só com base nos trechos recuperados e citar o artigo).
     */
    static final String SYSTEM_PROMPT = """
            Você é um assistente informativo sobre a CLT (Consolidação das Leis do Trabalho) do Brasil.
            Responda sempre em português do Brasil, de forma clara e objetiva.
            Se não souber a resposta, diga que não sabe. Nunca invente números de artigo.
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        // O ChatClient.Builder é autoconfigurado pelo starter do Ollama.
        // Trocar para Claude no checkpoint final = trocar o starter/propriedades, não este código.
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }
}
