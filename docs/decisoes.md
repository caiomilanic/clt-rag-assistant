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

### D3. Modelo local: `qwen3:8b`. *Substituída pela D15: `qwen3:4b-instruct-2507`.*
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

### D6. Embeddings: Voyage AI (`voyage-4`). *Revista pela D9: a Voyage passa a ser só o perfil `cloud`.*
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

### D9. Offline por padrão, com perfis `offline` e `cloud` (2026-11-12)
**Escolha:** o sistema roda inteiro dentro da rede, com embeddings e LLM no Ollama. Um perfil `cloud` opcional usa a Voyage AI para embeddings e, no futuro, o Claude como LLM.
**Alternativa:** a arquitetura original, com a Voyage obrigatória.
**Por quê:** com a Voyage, toda pergunta faria uma chamada externa (o embedding da pergunta é calculado a cada consulta), e o sistema não funcionaria sem internet. Rodar 100% local é requisito para o posicionamento on-premise (D10).
**Consequências:**
- O modelo de embeddings local (candidatos: `bge-m3`, multilíngue e bom em português; `nomic-embed-text`) divide a GPU com o LLM. É preciso medir o consumo de VRAM.
- O corpus da CLT fica empacotado no repositório; a ingestão não baixa nada em tempo de execução.
- O eval set roda nos dois perfis e mede o custo de qualidade de ficar offline.

### D10. Reposicionamento: assistente de normas trabalhistas para RH (2026-11-12)
**Escolha:** *"Assistente de normas trabalhistas para RH, com RAG, que roda on-premise: reúne o que dizem a CLT, a convenção coletiva e as políticas internas sobre cada dúvida, indica a hierarquia entre essas normas e cita a fonte de cada trecho. Nenhum dado sai da rede da empresa."*
**Alternativas:**
- *Assistente da CLT para o trabalhador* (posicionamento original): bom para divulgação, mas sem diferencial técnico forte.
- *RAG genérico offline para qualquer documento:* mercado já ocupado por ferramentas prontas (AnythingLLM, Open WebUI, PrivateGPT).
**Por quê:** no direito do trabalho, as normas têm hierarquia (CLT, convenção coletiva, acordo coletivo, política interna; Arts. 611-A, 611-B e 620 da CLT), e um RAG genérico não sabe disso. É esse conhecimento de domínio que diferencia o projeto. O argumento de privacidade também só se sustenta com documentos da empresa: a CLT é pública.
**Limites do escopo:**
- **Usuário:** analista de RH/DP, que confere a fonte antes de agir. Autoatendimento para funcionários fica para depois.
- **O sistema organiza e cita; não decide.** Dizer qual norma prevalece num caso concreto é interpretação jurídica. O texto diz "indica a hierarquia", e não "aplica".
- **Sem dados de funcionários** (salário, ponto, histórico) e **sem ações** (cálculos, lançamentos). Isso simplifica a LGPD.

### D11. Entrega em camadas (2026-11-12)
**Escolha:** três camadas, cada uma funcional e publicável sozinha.
1. **MVP:** RAG offline só com a CLT, com eval e números.
2. **Molde:** segunda fonte (uma convenção coletiva pública real) e respostas mostrando as duas fontes.
3. **Visão:** políticas internas, acordos, atualização offline do corpus e controle de acesso.

**Por quê:** a visão completa é grande para um projeto solo, e convenções em PDF são bem menos estruturadas que a CLT. A camada 1 já demonstra RAG, avaliação e operação offline; as seguintes acrescentam o diferencial de domínio.
**Consequência:** a ingestão é projetada desde o início com adaptadores por tipo de fonte e metadados comuns (tipo da fonte, documento, seção, vigência, link), mesmo que a camada 1 só implemente o adaptador da CLT.

### D12. Corpus versionado no repositório (2026-11-12)
**Escolha:** o HTML oficial da CLT (`corpus/clt/DEL5452.html`, baixado do Planalto pelo navegador) fica versionado no Git, com o SHA-256 registrado no eval set. O arquivo é marcado em `.gitattributes` como binário (`-text`), para o Git não converter o encoding (cp1252) nem as quebras de linha.
**Alternativa:** baixar a CLT em tempo de execução.
**Por quê:** (1) o sistema precisa rodar sem internet (D9); (2) o eval só faz sentido contra uma versão fixa da lei; (3) o servidor do Planalto derruba conexões do `curl` (`Connection was reset`), então o download automático nem é confiável.
**Consequência:** atualizar a lei vira uma ação explícita: trocar o arquivo, atualizar o hash e revisar o eval. Na camada 3, isso vira o "pacote de atualização do corpus".

### D13. Regras de extração e chunking validados com um protótipo (2026-11-12)
**Escolha:** antes do adaptador Java, um protótipo em Python ([`tools/corpus/parse_clt.py`](../tools/corpus/parse_clt.py)) validou as regras de extração do HTML: descartar texto riscado e versões de MP com "Vigência encerrada", tirar as notas de alteração do texto, normalizar "§ 1 o", ignorar os artigos do decreto-lei de aprovação. As regras estão em [`tools/README.md`](../tools/README.md).
**Resultado:** 826 artigos vigentes, mediana de ~380 caracteres e máximo de ~5.000. **Isso confirma a D7: um chunk por artigo**, sem precisar subdividir os longos, que cabem no contexto dos modelos de embedding candidatos.
**Por quê um protótipo:** o HTML do Planalto é irregular (FrontPage, texto revogado misturado com o vigente, duplicatas). Descobrir as regras em script descartável é mais rápido que em Java, e as regras viram especificação e casos de teste do adaptador.

### D14. Desenho do eval set (2026-11-12)
**Escolha:** 24 perguntas na linguagem do analista de RH, de cinco tipos: `direta`, `multi_artigo`, `hierarquia`, `cobertura_parcial` e `fora_do_escopo`. Cada uma tem os artigos esperados, uma resposta de referência e os trechos justificativos **copiados do corpus por script**. Detalhes em [`eval/README.md`](eval/README.md).
**Por quê os tipos além de `direta`:**
- `hierarquia`: é o diferencial do produto (D10) e precisa de medição desde o MVP.
- `cobertura_parcial` e `fora_do_escopo`: medem honestidade. O teste da linha de base mostrou que o problema do modelo não é não saber, é inventar com segurança. Por isso há perguntas trabalhistas cuja resposta o modelo "sabe" mas que não estão no corpus (alíquota do FGTS, aviso prévio proporcional).

**Métricas:** acerto de recuperação (artigo esperado entre os 5 trechos recuperados, meta ≥ 90%) e citação (100%).
**Atualização (v0.2):** entrou a métrica de **fidelidade** (o conteúdo da resposta é sustentado pelo trecho citado). Motivo: o `qwen3:4b-instruct` citou o Art. 130 corretamente e afirmou "12 dias úteis". Citação certa com conteúdo errado é o pior caso, porque a fonte dá credibilidade ao erro, e a métrica de citação sozinha não pega.

### D15. Modelo de chat: `qwen3:4b-instruct-2507` (2026-11-12)
**Escolha:** `qwen3:4b-instruct-2507-q4_K_M` (Qwen3 4B, variante *instruct*, quantização de 4 bits) como padrão. Os embeddings ficam com o `bge-m3`. Os dois rodam 100% na GPU (RTX 3060 Laptop, 6 GB): 3,2 GB + 0,7 GB.
**Medições** (mesma pergunta, API do Ollama):

| Modelo | Onde roda | Velocidade | Tempo total |
|---|---|---|---|
| `qwen3:8b` (driver NVIDIA antigo) | 100% CPU | 6,4 tok/s | ~89 s |
| `qwen3:8b` (driver atualizado) | 30% CPU / 70% GPU | 13,7 tok/s | ~43 s |
| `qwen3:4b` (raciocínio) | 100% GPU | 78 tok/s | ~16 s (1.230 tokens, quase tudo raciocínio) |
| **`qwen3:4b-instruct-2507`** | **100% GPU** | **85 tok/s** | **~5 s** |

**Alternativas descartadas:**
- `qwen3:8b`: não cabe inteiro nos 6 GB, e a divisão CPU/GPU roda a uma fração da velocidade. Além disso, não sobraria VRAM para o modelo de embeddings.
- `qwen3:4b` (tag padrão): é uma versão só de raciocínio. Ignorou `"think": false` e gastou 1.230 tokens num monólogo em inglês antes de uma resposta de menos de 100.
- `gemma3:4b`: alternativa sem raciocínio, mantida como plano B.

**Trade-off aceito:** sem RAG, o 4B alucina tanto quanto o 8B ("13 dias úteis, Lei nº 6.015", que na verdade é a Lei de Registros Públicos). No RAG, o modelo não precisa *saber* a lei, precisa *ler* os trechos recuperados. O eval set vai confirmar se ele lê bem o bastante; o 8B continua a uma variável de distância para comparação.
**Lições para o dimensionamento on-premise:** (1) o modelo precisa caber inteiro na VRAM; (2) o modelo de chat e o de embeddings dividem a mesma VRAM; (3) GPU é requisito prático: na CPU, uma resposta leva mais de 1 minuto.
**Consequência:** o `think: false` saiu do `application.yml`, porque não tinha efeito pelo Spring AI e a variante instruct não precisa dele.
