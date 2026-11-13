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

### D16. Pipeline de ingestão e busca (2026-11-12)
**Escolha:**
- **Modelo de dados comum a todas as fontes** (`TrechoNormativo`: tipo da fonte, documento, referência, contexto, texto, link, notas) e um **adaptador por tipo de fonte** (`AdaptadorFonte`). Na camada 1 só existe o `CltAdapter`; convenções e políticas entram como novos adaptadores, sem mexer no resto.
- **Parser da CLT em Java** (`CltHtmlParser`, com jsoup), portado do protótipo Python e coberto por testes, um para cada problema real do HTML. Contra o arquivo real, ele extrai **835 artigos** (834 vigentes), mais que o protótipo (826): recupera artigos que o protótipo perdia, como o 12, os 554 e 555 (escritos "Art 554." sem ponto) e o 556 (descartado por engano, porque notas de dispositivos vizinhos tinham "Vigência encerrada"). O critério de MP caducada passou a olhar só as notas da linha do caput. **O parser Java é a referência**; o protótipo fica como ferramenta do eval.
- **Texto do embedding com cabeçalho:** "CLT, Art. 130 | TÍTULO II > CAPÍTULO IV - DAS FÉRIAS ANUAIS" + texto do artigo. O capítulo dá ao vetor o tema, que o caput nem sempre repete.
- **Ingestão idempotente:** o id de cada documento é um UUID derivado do id do trecho (`clt:art-130`). Rodar de novo substitui, não duplica.
- **Ingestão disparada por endpoint** (`POST /api/ingestao/clt`), não na inicialização. A aplicação sobe rápido, e reingerir é uma ação explícita.
- **pgvector:** tabela `trechos_normativos`, 1024 dimensões (bge-m3), distância cosseno, índice HNSW.
- **Busca e avaliação sem LLM:** `GET /api/busca` e `POST /api/avaliacao/recuperacao`. A recuperação é medida isoladamente antes do RAG, para saber se um erro futuro vem da busca ou da geração.

**Adiado:** o perfil `cloud` (Voyage AI) foi para o passo 6, onde ele existe para ser comparado. Implementá-lo agora seria código sem uso.
**Limitação conhecida:** artigos que saírem do corpus numa atualização da lei continuam no banco até uma limpeza da fonte inteira. Fica para o pacote de atualização (camada 3).

### D17. Endpoint de RAG: pipeline explícito com verificação de citações (2026-11-12)
**Escolha:** `POST /api/perguntas` com um pipeline escrito à mão (`RagService`): busca top-k (padrão 5) → prompt com os trechos numerados → `qwen3:4b-instruct` → extração das citações da resposta → comparação com os trechos recuperados.
**Alternativa:** os advisors prontos do Spring AI (`QuestionAnswerAdvisor` / `RetrievalAugmentationAdvisor`), que fazem busca e montagem do prompt automaticamente.
**Por quê:** o pipeline explícito deixa cada etapa visível e mensurável (latência da busca × latência do LLM), devolve as fontes junto com a resposta e permite a **verificação de citações**. Num projeto de portfólio, mostrar o mecanismo vale mais que esconder atrás de uma abstração. Trocar para os advisors depois é simples, se fizer sentido.

**Regras do prompt, cada uma ligada a uma falha observada:**
1. Responder só com os trechos e citar o dispositivo de cada afirmação: contra a invenção de artigos (linha de base: Art. 133 no lugar do 130).
2. Copiar números exatamente, sem converter "dias corridos" em "dias úteis": contra o caso "Art. 130 certo, 12 dias úteis errado".
3. Dizer o que os trechos não cobrem: para a pergunta de cobertura parcial (aviso prévio proporcional).
4. Frase fixa de recusa quando nada responde: a primeira avaliação mostrou que um limiar de similaridade não separa as perguntas fora do escopo. A recusa precisa vir do modelo, e a frase fixa permite detectá-la.
5. Explicar o que a lei permite negociar: para as perguntas de hierarquia (611-A, 611-B, 620).
6. Respostas curtas, sem repetir o aviso jurídico, que o sistema já adiciona.

**Verificação de citações:** a resposta traz `citacao.foraDosTrechos`, com os artigos citados que não estavam entre os trechos recuperados. Se não estava no contexto, o modelo tirou da memória: é um sinal objetivo de alucinação, medido em toda resposta e na avaliação (`POST /api/avaliacao/respostas`).
**Contexto:** `num-ctx: 8192` (o padrão do Ollama, 4096, pode cortar 5 trechos longos), ao custo estimado de ~0,6 GB a mais de VRAM; conferir com `ollama ps` que os dois modelos continuam 100% na GPU.
**Limitações conhecidas:** "Arts. 134 e 137" só registra o 134 na extração de citações; a detecção da recusa depende do modelo usar a frase combinada; o texto da pergunta é inserido no prompt sem sanitização (guardrails no passo 8).

**Atualização da D17 após a primeira avaliação (2026-11-12):** o detector de citações passou a reconhecer "artigo N" por extenso e a ignorar artigos de outras leis citados dentro da CLT ("art. 18 da Lei nº 8.036"). A recusa passou a valer só quando a resposta começa com a frase combinada, ou a contém sem citar artigos, porque o modelo às vezes responde citando artigos e acrescenta a frase de recusa no fim. Resultados e análise em `docs/eval/README.md`.

### D18. Busca por dispositivo e busca híbrida, como opções medidas (2026-11-12)
**Problema medido:** os dois erros de recuperação (Q10, banco de horas, Art. 59, § 5º; Q15, tolerância do ponto, Art. 58, § 1º) têm o assunto num parágrafo cujo caput trata de outra coisa, e a Q10 gerou a falha de resposta mais perigosa (resposta confiante baseada no Art. 617).

**Duas hipóteses, implementadas como opções, não como substituição:**
1. **Granularidade `dispositivo` ("small-to-big"):** cada artigo também é indexado por dispositivo (caput com seus incisos; cada parágrafo com os seus), com 1.863 documentos além dos 834 artigos. A busca acha pelo parágrafo, mas devolve **o artigo inteiro** (guardado no metadado `texto_artigo`). Assim a citação continua por artigo e o LLM recebe o contexto completo, já que um § 5º que diz "o banco de horas de que trata o § 2º" precisa do § 2º.
2. **Estratégia `hibrida`:** busca vetorial + busca textual do Postgres (`to_tsvector('portuguese', …)`, com stemming e stopwords; termos da pergunta com OU, ordenados por `ts_rank_cd`), combinadas por **Reciprocal Rank Fusion** (score = Σ 1/(60 + posição)). Termos exatos como "banco de horas" aparecem literalmente no Art. 59, e o RRF dispensa calibrar escalas de score diferentes.

**Por que opções:** as duas podem ajudar, atrapalhar ou se sobrepor. Granularidade e estratégia viraram parâmetros (`app.busca.*` como padrão, e sobrescrevíveis na URL de `/api/busca` e das avaliações). Assim as 4 combinações são medidas no mesmo eval sem reiniciar a aplicação, e o padrão é escolhido pelo número.
**Custos:** a ingestão passa de 834 para ~2.700 documentos; a busca textual roda sem índice (`to_tsvector` na hora, ~1.900 linhas), o que é aceitável neste volume. Um índice GIN fica para quando o corpus crescer (camadas 2 e 3).
**Alternativas não escolhidas agora:** reranker (um modelo que reordena os candidatos; custaria mais VRAM e latência) e reescrita da pergunta pelo LLM (custaria uma chamada a mais por pergunta). Ficam como próximas hipóteses, se as duas opções não bastarem.

**Resultado da D18 (2026-11-12):** medidas as 4 combinações no eval (top-5): artigo + vetorial 90,5%; artigo + híbrida 76,2%; **dispositivo + vetorial 100%**; dispositivo + híbrida 85,7% (com o melhor MRR, 0,740). **Novo padrão: dispositivo + vetorial.** A busca híbrida ficou como opção, com o resultado negativo documentado: a consulta textual com OU sobre todas as palavras e o peso igual no RRF trazem ruído. Ressalva: o eval é pequeno e foi usado para desenhar a mudança, então a confirmação precisa de perguntas novas (validação separada).

### D19. Condições no prompt e verificação de parágrafos e incisos (2026-11-12)
**Problemas medidos** na rodada com busca por dispositivo: (1) respostas certas no essencial, mas **sem a condição** (Q10 sem o limite de 6 meses do banco de horas; Q15 sem os 5 e 10 minutos); (2) **inciso errado** na citação, com o artigo certo: "Art. 130, I" quando a regra está no II (Q02), "Art. 145, I" e "Art. 59, I", que não têm inciso (Q05, Q09), "Art. 611-B, I" no lugar do XI (Q20). O detector de citações só conferia o artigo, então nada disso aparecia nas métricas.

**1. Prompt (regras 2 e 4 da `PromptRag`):** o parágrafo ou o inciso só entra na citação depois de o modelo conferir que o texto está naquele dispositivo; na dúvida, cita só o artigo. E prazos, limites, percentuais e condições ("desde que", "até", "no máximo", "mediante", "por escrito") passam a fazer parte da resposta, porque para o analista de RH a condição é a regra.

**2. `VerificadorDeCitacoes` (sem LLM, determinístico):** para cada citação com § ou inciso ("(Art. 130, I)", "§ 6º do Art. 477", "Art. 487, I e II", "Art. 59-B, parágrafo único") de um artigo que estava entre os trechos:
- **`dispositivo_inexistente`**: o artigo recuperado não tem aquele § ou inciso;
- **`numero_ausente`**: a frase que a citação sustenta traz um número que não está no dispositivo citado. Os números por extenso da lei contam ("30 minutos" ↔ "trinta minutos"), e números que já estão na pergunta ("8 horas por dia") são contexto, não afirmação.

A resposta da API traz `citacao.problemasDeDispositivo` e a avaliação conta `respostasComProblemaDeDispositivo` (meta: 0).

**Validação antes de medir:** rodado sobre as 24 respostas reais da última avaliação, o verificador marcou exatamente Q02 (24, 6 e 14 não estão no inciso I), Q05 e Q09 (incisos inexistentes), sem falso positivo. Dois ajustes vieram desse teste: citação que abre a frase ("(Art. 71, § 1º) menciona…") usa o resto da frase, e números da pergunta são descontados.

**Alternativa não escolhida agora:** modelo juiz (LLM) comparando cada afirmação com o trecho. Pega mais casos, mas custa uma chamada a mais, tem variação própria e precisa ele mesmo ser avaliado. O verificador determinístico é barato, explicável e roda em toda resposta; o juiz fica para depois, se o verificador não bastar.
**Limitação conhecida:** inciso que existe, mas é o errado, só é pego quando a frase tem números. O "Art. 611-B, I" da Q20, numa frase sem números, passa. Citações só com artigo não são conferidas no nível de dispositivo.
**Risco registrado:** prompt e verificador foram desenhados olhando as mesmas 24 perguntas. A confirmação vem do conjunto de validação com perguntas novas (próximo passo).

**Resultado da D19 (2026-11-12):** placar de fidelidade praticamente igual (17 fiéis, 3 parciais, 1 infiel; antes 18, 2, 1) e o mesmo número de citações de dispositivo erradas (4). A regra 4 ajudou na Q10 (passou a dizer os 6 meses), mas não na Q15. A regra 2 o modelo de 4B não seguiu: os erros de inciso mudaram de lugar. O verificador foi o ganho: pegou 2 dos 4 erros novos e tornou o problema mensurável. As regras ficam (não pioraram e são corretas para modelos maiores), mas a conclusão é que **a correção do dispositivo tem de vir do sistema**, não do prompt.

**Correções no verificador depois da rodada:**
- **Falso positivo na Q10:** "(Art. 59-B). … (Art. 59, § 6º)" virava "Art. 59-B, § 6º". Causa: no Java, um grupo de captura dentro de um quantificador possessivo (`?+`) pode manter o valor de uma tentativa que falhou e contaminar a próxima. Os possessivos foram trocados por lookaheads (`(?!\d)`, `(?![-º°])`). O mesmo bug existia no `Citacoes` (detector de artigos): "art. 20-A da Lei nº 8.036 e o Art. 477" virava "477-A". Corrigido nos dois, com testes.
- **Números por extenso na resposta:** "até duas horas extras (Art. 59, § 1º)" passava, porque só algarismos eram lidos na resposta. Agora "duas" conta como 2 ("um/uma" fica de fora, por ser quase sempre artigo indefinido).

### D20. Citação de dispositivo marcada é rebaixada para o artigo (2026-11-12)
**Problema:** a D19 mostrou que o `qwen3:4b-instruct` não confere o próprio inciso, mesmo instruído: em duas rodadas, 4 citações de § ou inciso erradas em 21 respostas, enquanto o **artigo** veio certo em 100% delas.
**Escolha:** quando o `VerificadorDeCitacoes` marca uma citação, o sistema a reescreve só com o artigo antes de entregar a resposta: "(Art. 130, I)" vira "(Art. 130)"; "§ 6º do Art. 477" vira "Art. 477"; "Art. 611-A, § 3º, III" vira "Art. 611-A". O texto do modelo fica em `citacao.respostaOriginal` (null quando nada mudou), e o problema continua em `problemasDeDispositivo`, para que a métrica não esconda o erro.
**Alternativas:** (1) nova chamada ao LLM pedindo a correção: mais cara, mais lenta e sem garantia de acerto com o mesmo modelo; (2) só avisar o analista: deixa uma citação errada na frente dele; (3) remover a citação: perde a fonte. Rebaixar é determinístico, sem custo, e deixa uma fonte que o analista abre e lê inteira.
**Por quê:** para o analista de RH, uma citação menos precisa e certa vale mais que uma precisa e errada. O artigo completo já aparece nas fontes da resposta.
**Validação:** aplicado às duas rodadas gravadas, rebaixou 5 citações (Q02, Q05 e Q09 na anterior; Q09 e Q12 na 6b), todas erradas, sem tocar nas certas.
**Limitação:** só corrige o que o verificador detecta. Inciso que existe, mas é o errado, numa frase sem números (Q17, Q20) continua passando.

### D21. Conjunto de validação separado (2026-11-13)
**Problema:** busca (D18), prompt (D17, D19) e verificador (D19, D20) foram ajustados olhando as mesmas 24 perguntas. Os números delas podem estar otimistas: é o sobreajuste de quem calibra e mede no mesmo conjunto.
**Escolha:** um segundo conjunto, `docs/eval/validacao-set.json`, com 26 perguntas de temas que o primeiro não cobre (trabalho noturno, tempo parcial, 12x36, insalubridade, periculosidade, CTPS, salário, descontos, transferência, intermitente, teletrabalho, gestante, aviso de férias, justa causa, aviso prévio trabalhado, negociação individual do hipersuficiente). Mesmo formato e mesmo gerador: gabarito copiado do corpus pelo `tools/eval/build_eval.py`. A API escolhe o conjunto por `?conjunto=validacao`; o padrão continua sendo o de desenvolvimento.
**Regras:**
1. O conjunto foi congelado antes da primeira execução.
2. Nada no sistema é ajustado olhando para ele. Se uma falha da validação motivar uma mudança, a pergunta vira caso de desenvolvimento e entra uma nova no lugar, para a validação continuar limpa.
3. O desenvolvimento continua sendo onde se itera; a validação é rodada só para confirmar.
**Armadilhas de propósito:** V23 (a CLT garante a estabilidade da gestante, mas a duração está na Constituição), VF01 e VF02 (13º salário e seguro-desemprego aparecem no Art. 611-B, mas as regras estão em outras leis), V07 (combinar a regra do trabalho noturno com a exceção da 12x36).
**Limitação:** as perguntas foram escritas pelo mesmo par (Caio e Claude) que construiu o sistema, ainda que antes de qualquer execução. Perguntas escritas por um analista de RH de fora seriam o teste mais forte.

**Resultado da primeira execução (2026-11-13):** recuperação de 78,3% (contra 100% no desenvolvimento) e 11 de 23 respostas fiéis (48%, contra 81%). O sobreajuste existia: o conjunto de desenvolvimento usava o vocabulário da lei, e a validação, o do RH. Detalhes em `docs/eval/README.md`. Duas correções de bug nos detectores (ADCT e números acima de cem) vieram desta rodada; são genéricas e não mexem em busca nem em prompt, então a validação continua válida para medir essas duas partes.

### D22. Expansão da consulta com glossário RH → lei (2026-11-13)
**Problema:** na primeira execução da validação (D21), 4 das 5 falhas de busca foram vocabulário: "12x36" não alcança "doze horas seguidas por trinta e seis" (Art. 59-A), "home office" não alcança "teletrabalho" (Art. 75-C). O conjunto de desenvolvimento não mostrava isso porque foi escrito com a lei aberta.
**Escolha:** `ExpansorDeConsulta` + `glossario/rh-clt.json`. Quando a pergunta contém um termo do glossário (sem diferenciar acento nem maiúscula, palavra inteira), a expansão correspondente é acrescentada à consulta da busca vetorial (e da textual, na híbrida). O LLM continua recebendo a pergunta original. Liga e desliga por `app.busca.expansao` e por `?expansao=` nas avaliações e na busca; os termos aplicados aparecem em `metricas.termosGlossario` e nos resultados das avaliações.
**Alternativas:**
1. **Reescrita da pergunta pelo LLM** (ou HyDE: o modelo escreve um trecho de lei hipotético e busca por ele): cobre jargão que ninguém previu, mas custa uma chamada a mais por pergunta e pode alucinar o vocabulário. Fica como próxima comparação.
2. **Sinônimos no dicionário do Postgres** (busca textual): a híbrida já foi rejeitada na D18.
3. **Trocar o modelo de embeddings:** caro de medir e não resolve "12x36", que é convenção do mercado, não da língua.

**Por quê:** é determinístico, explicável ("expandi '12x36' para…"), não custa chamada de modelo e roda on-premise. O arquivo fica fora do código: cada empresa acrescenta o próprio jargão (nomes internos de benefícios, siglas do sindicato) sem recompilar, o que encaixa no produto como molde.
**Regra desta versão:** o glossário só tem termos que aparecem nas perguntas de **desenvolvimento** (e variações de grafia deles). As cinco perguntas que motivaram o glossário saíram da validação e viraram Q22 a Q26; entraram cinco novas (V24 a V28), escritas e congeladas antes do glossário, com jargão que ele não cobre ("pediu as contas", "contrato de experiência", "férias coletivas", "ônibus fretado", "rebaixar"). Assim a validação mede duas coisas: se o glossário piora alguma pergunta que não deveria afetar, e quanto do jargão novo fica de fora.
**Expectativa registrada antes de medir:** melhora em Q22 a Q26 no desenvolvimento; efeito quase nulo na validação, porque nenhum termo do glossário aparece nela. Se isso se confirmar, a conclusão é que o glossário corrige o que cobre e que a cobertura é o problema, o que motiva medir a reescrita pelo LLM.

**Resultado da D22 (2026-11-13):** a expectativa registrada se confirmou. Busca no desenvolvimento de 80,8% para 92,3% (MRR de 0,597 para 0,840); validação inalterada em 87,0%, porque nenhum termo do glossário aparece nela, e três das cinco perguntas novas com jargão falharam (V24, V27, V28). Efeito colateral: na Q12, a expansão de "almoço" tirou o Art. 611-A do top-5 e a resposta ficou errada. Na Q24, quatro expansões juntas diluíram a consulta. **Próxima mudança, motivada por perguntas de desenvolvimento (Q12, Q24):** buscar pela pergunta original e pela expansão separadamente e fundir as listas, em vez de concatenar.

**Mudança na aplicação do glossário (2026-11-13), motivada por Q12 e Q24 (desenvolvimento):** a expansão deixou de ser concatenada à pergunta. Agora a busca vetorial roda uma vez pela pergunta original e uma vez por entrada do glossário encontrada, e as listas são fundidas por RRF (k = 60), contando cada artigo pela sua melhor posição em cada lista. Em empate, a lista da pergunta original vem primeiro, e o candidato (dispositivo e score) de cada artigo é o da primeira lista em que ele aparece. Assim a expansão **acrescenta** candidatos sem tirar os da pergunta (Q12), e duas expansões diferentes não se diluem numa consulta só (Q24: "12x36" e trabalho noturno viram buscas separadas). Custo: uma consulta de embedding a mais por entrada encontrada, só nas perguntas com termos do glossário. Na estratégia híbrida, a busca textual usa a pergunta seguida das expansões.

**Fusão por RRF, medida e descartada (2026-11-13):** a busca no desenvolvimento caiu de 92,3% (concatenação) para 88,5% (MRR de 0,840 para 0,670). A Q12 continuou sem o 611-A e a Q23 perdeu o 59-A, que a concatenação tinha resolvido. Causa: o RRF soma os pontos de cada artigo em todas as listas (20 candidatos cada) e por isso favorece quem aparece nas duas buscas, mesmo em posições medianas, contra o 1º lugar exclusivo da expansão. **Substituído por intercalação:** o 1º da pergunta, o 1º de cada expansão, o 2º da pergunta, e assim por diante, sem repetir artigo. Isso garante no topo os melhores de cada busca. Lição: o RRF serve para combinar rankings que concordam, como na busca híbrida; aqui as buscas são complementares de propósito.

**Intercalação com a expansão sozinha, medida e descartada (2026-11-13):** 92,3% (MRR 0,663). Recuperou a Q12, mas perdeu a Q23: a expansão sozinha ("doze horas seguidas por trinta e seis…") não acha o Art. 59-A. Quem acha é a pergunta concatenada com a expansão, porque a pergunta traz o contexto ("combinando direto com o funcionário" → acordo individual). **Versão final:** intercalar a busca pela pergunta original com a busca pela pergunta concatenada com as expansões. Simulada sobre as listas já medidas (exata no top-5, porque nenhuma pergunta precisou de candidatos além do 5º): 96,2% (25/26), MRR 0,689. O MRR fica menor que o da concatenação (0,840) porque os acertos dela descem para o 2º lugar. Para o RAG, o que importa é o artigo estar entre os 5 trechos, que vão todos ao modelo. Só a Q24 continua falhando.
**Variação do modelo:** com o mesmo top-5, respostas mudaram entre rodadas (Q11 voltou a errar, Q26 inverteu a conclusão). Nesta etapa, as configurações de busca são comparadas pela recuperação, que é determinística; comparar respostas exige repetir execuções.

**Versão final confirmada (2026-11-13):** desenvolvimento 96,2% (25/26), MRR 0,689; validação 87,0%, inalterada. Bateu exatamente com a simulação. Respostas: desenvolvimento 21/26 fiéis, validação 13/23, os mesmos totais da rodada anterior, mas com trocas pergunta a pergunta (Q12 e Q02 corrigidas; Q11, Q15 e Q26 pioraram, com o mesmo top-5 ou equivalente). **Conclusão do passo:** a busca deixou de ser o gargalo no desenvolvimento (só a Q24 falha). O que limita agora é a leitura do modelo de 4B, que também varia entre execuções (Q11, Q25, Q26, V08, V14, V23), e o jargão fora do glossário na validação (V24, V27, V28).
