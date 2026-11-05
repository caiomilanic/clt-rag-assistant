# Decisões de arquitetura

Registro curto das decisões do projeto: o que foi escolhido, por quê e o que ficou de fora. Novas decisões entram no fim.

---

### D1. Domínio: CLT
**Escolha:** Consolidação das Leis do Trabalho.
**Alternativas:** documentação técnica, normas do setor financeiro.
**Por quê:** impacto real e público amplo (qualquer trabalhador entende o valor). O texto é público, oficial e estruturado em artigos, o que facilita o chunking e a citação.
**Consequência:** requisito obrigatório de produto, em que toda resposta traz um aviso de que a ferramenta é informativa e não é aconselhamento jurídico (`shared/Disclaimer.java`).

### D2. LLM: Ollama local primeiro, Claude só no fim
**Escolha:** desenvolver o projeto inteiro com um modelo aberto rodando no Ollama.
**Alternativas:** API da Anthropic desde o início; créditos promocionais de nuvem (AWS Bedrock, Azure Foundry).
**Por quê:** é a única rota de custo **garantidamente zero** e sem limite de uso. A API da Anthropic é barata para este volume, mas não é gratuita, e créditos de nuvem nem sempre cobrem modelos de terceiros.
**Consequência:** o código depende só das abstrações do Spring AI (`ChatClient`). A troca para Claude é o passo 12 do roadmap: trocar o starter e as propriedades, rodar o eval set de novo e comparar.

### D3. Modelo local: `qwen3:8b`
**Escolha:** Qwen3 8B como padrão, configurável por `OLLAMA_MODEL`.
**Alternativas:** Gemma 3 (4B/12B), Llama 3.x, Mistral.
**Por quê:** bom suporte a português e cabe em máquina de 16 GB (~5 GB).
**Observação:** o Qwen3 tem modo de raciocínio ("thinking"). No primeiro teste, uma resposta curta gastou 606 tokens e 111 s. Desligar o raciocínio está pendente (ver diário, 2026-11-05).

### D4. Java 25 · Spring Boot 4 · Spring AI 2
**Escolha:** Java 25 (LTS), Spring Boot 4.0.0, Spring AI 2.0.0.
**Por quê:** o Spring AI 2.0 saiu como GA em 12/06/2026 e exige Boot 4. O Boot 3.5 saiu do suporte open source em 30/06/2026, então um projeto novo já nasce na linha atual.

### D5. Banco vetorial: PostgreSQL + pgvector
**Escolha:** imagem `pgvector/pgvector:pg17` via Docker Compose.
**Alternativas:** bancos vetoriais dedicados (Qdrant, Milvus, Chroma).
**Por quê:** um banco só para dados relacionais e vetores; suporte nativo no Spring AI (`PgVectorStore`); fácil de hospedar no Azure.
**Detalhe:** porta **5433** no host, porque a 5432 costuma estar ocupada por um PostgreSQL instalado localmente (aconteceu na máquina de desenvolvimento).

### D6. Embeddings: Voyage AI (`voyage-4`)
**Escolha:** Voyage AI, modelo `voyage-4` ou `voyage-4-lite`.
**Alternativas:** embeddings do próprio Ollama (ex.: `nomic-embed-text`), OpenAI.
**Por quê:** a Anthropic não tem API própria de embeddings e recomenda a Voyage. A família voyage-4 tem 200M tokens gratuitos, que cobrem o corpus da CLT reprocessado muitas vezes.
**Cuidados:** modelos antigos (voyage-3.x, context-3, code-3) não têm franquia grátis. Sem cartão cadastrado, o limite é 3 req/min e 10K tokens/min, então a ingestão precisa de batching e backoff, ou de um cartão cadastrado (os 200M grátis continuam valendo).
**Consequência:** `spring.ai.model.embedding: none` desliga o embedding do Ollama. Um `EmbeddingModel` próprio para a Voyage entra no passo 3.

### D7. Chunking por artigo
**Escolha:** um chunk por artigo da CLT, com o número do artigo como metadado.
**Por quê:** o artigo é a unidade de citação. Recuperar o artigo inteiro permite citar com precisão e linkar para o texto oficial.
**A validar no passo 3:** artigos muito longos (com muitos parágrafos e incisos) talvez precisem ser subdivididos, mantendo o metadado do artigo.

### D8. Endpoint de fumaça antes do RAG
**Escolha:** `POST /api/smoke/chat`, que chama o LLM direto e já devolve modelo, latência, tokens e aviso.
**Por quê:** valida a integração com o Ollama isoladamente, e a resposta sem RAG serve de linha de base ("antes") para comparar com o RAG. O formato da resposta já antecipa a observabilidade do passo 5.
**Consequência:** será substituído pelo endpoint de RAG no passo 4.
