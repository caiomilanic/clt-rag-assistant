# Diário de desenvolvimento

Registro cronológico do que foi feito, do que deu errado e de como foi resolvido. Serve de base para a documentação final e para o post de divulgação.

---

## 2026-10-27: Planejamento e esqueleto (passo 2)

- Domínio, arquitetura e roadmap definidos (ver [decisoes.md](decisoes.md)).
- Pesquisa de versões: Spring AI 2.0.0 GA (12/06/2026), que exige Spring Boot 4.
- Pesquisa da Voyage AI: a franquia gratuita agora vale só para a família voyage-4, e sem cartão o rate limit é baixo (3 req/min).
- Esqueleto criado:
  - `pom.xml` com Spring Boot 4.0.0, Spring AI 2.0.0 (BOM), starter do Ollama, JDBC e Postgres
  - `docker-compose.yml` com `pgvector/pgvector:pg17` e init SQL que cria a extensão `vector`
  - `ChatClientConfig` com prompt de sistema base
  - `Disclaimer` centralizado
  - `POST /api/smoke/chat`, que devolve resposta, modelo, latência, tokens e aviso
- Java atualizado de 21 para **25** (LTS), por escolha do autor.

## 2026-11-05: Primeira execução local (Windows)

A aplicação compilou e subiu de primeira (`Started CltAssistantApplication in 2.165 seconds`). Os problemas vieram do ambiente, na ordem em que apareceram:

1. **`db: DOWN` com "autenticação do tipo senha falhou".** A mensagem veio em português, mas a imagem Docker do Postgres responde em inglês. Ou seja, quem respondeu foi um PostgreSQL instalado no Windows, ocupando a porta 5432. **Solução:** container mudado para a porta **5433** (`docker-compose.yml` e `application.yml`).
2. **`Connection refused` na 5433.** O container nunca tinha sido criado, porque o Docker não estava instalado. Isso explicava também o item 1.
3. **`wsl` não reconhecido.** Pré-requisito do Docker Desktop. Resolvido com a instalação do WSL e do Docker Desktop.
4. **`docker: command not found` no Git Bash após instalar.** O terminal aberto antes da instalação não enxerga o PATH novo. **Solução:** reabrir o terminal.
5. **Pasta duplicada** (`clt-rag-assistant/clt-rag-assistant`) ao extrair o zip. Comandos do Docker precisam rodar na pasta interna, onde está o `docker-compose.yml`.
6. **`HTTP 400` com `Invalid UTF-8 middle byte`.** O Git Bash enviou o "é" de "férias" em outra codificação. **Solução:** escape Unicode (`férias`) ou enviar o JSON por arquivo (`--data-binary @arquivo.json`).
7. **`Connection refused` na 11434.** Ollama não estava instalado/rodando.
8. **`HTTP 404 model 'qwen3:8b' not found`.** Faltava o `ollama pull qwen3:8b`.

**Resultado:** primeira resposta de ponta a ponta. ✅ Passo 2 concluído.

### Linha de base: resposta sem RAG
Pergunta: "Quantos dias de férias o trabalhador tem direito por ano?"

- Acertou: 30 dias.
- Errou todas as citações: atribuiu os 30 dias ao Art. 133 (o certo é o **Art. 130**; o 133 trata da perda do direito), chamou o terço constitucional de "adiantamento" previsto na CLT (é adicional da **CF, art. 7º, XVII**) e citou um "Art. 133, §2º" para o período concessivo (é o **Art. 134**).
- Latência: **111,6 s**; tokens: 100 de entrada, **606 de saída**.

JSON completo em [exemplos/antes-do-rag.json](exemplos/antes-do-rag.json). Essa pergunta entra no eval set.

### Arrumação
- Projeto movido para fora do OneDrive (`C:\Users\caiom\IdeaProjects\clt-rag-assistant`), sem a pasta duplicada.
- SDK do IntelliJ trocado do JDK 26 para o 25.
- Repositório Git iniciado e publicado no GitHub.

### Investigação da latência
1. **`think: false` no `application.yml`:** quase nenhum efeito (100,6 s; 560 tokens de saída, contra 111,6 s e 606 antes). O Spring Boot ignora propriedades desconhecidas sem erro, então a propriedade provavelmente não é repassada ao Ollama nesta versão do Spring AI.
2. **Teste direto na API do Ollama com `"think": false`:** o raciocínio desligou (a resposta não tem campo `thinking`). Foram 499 tokens em 77,4 s de geração, ou **~6,4 tokens/s**.
3. **`ollama ps`:** `100% CPU`, contexto de 4096 tokens.
4. **Hardware:** RTX 3060 Laptop (6 GB de VRAM) + Intel UHD, 16 GB de RAM.
5. **Log do Ollama:** `inference compute ... library=cpu`, ou seja, **a GPU não foi detectada**. Driver NVIDIA 537.42 (2023), CUDA 12.2. O mesmo log mostrou **só 1,3 GB de RAM disponível** com Docker, IntelliJ, navegadores, Steam e Epic abertos.

**Conclusão:** a lentidão vem da CPU (e da falta de RAM), não do thinking. O thinking é um ajuste secundário.

### Segundo exemplo de alucinação
No teste direto (item 2), **sem prompt de sistema**, o modelo afirmou que o trabalhador tem "15 dias + 1 por ano de serviço, até 30", com base na "Lei nº 11.091/2005". A lei citada trata da carreira dos técnico-administrativos das universidades federais, e a regra é inventada (são 30 dias, Art. 130 da CLT). O modelo ainda pôs uma bandeira da Índia em "outros países". Registrado em [exemplos/sem-prompt-de-sistema.json](exemplos/sem-prompt-de-sistema.json).

Isso fecha a escada usada no README: **sem prompt, inventa a regra e a lei → com prompt, acerta a regra e inventa os artigos → com RAG, deve citar a fonte certa.**

### Pendências abertas
- **GPU:** atualizar o driver NVIDIA, reiniciar e confirmar `library=cuda` no log e `GPU` no `ollama ps`. Remedir a latência.
- **RAM:** fechar Steam e Epic durante o desenvolvimento; limitar o WSL com `.wslconfig` (`memory=3GB`).
- **Thinking pelo Spring AI:** testar `/no_think` no prompt de sistema e medir `completionTokens`.
- **Modelo:** comparar `qwen3:8b` (5,9 GB; mal cabe nos 6 GB de VRAM) com `qwen3:4b` (cabe inteiro na GPU).
- **Contexto:** o Ollama está usando 4096 tokens. No passo 4, o prompt leva vários trechos e pode estourar; aumentar o contexto (`num_ctx`).

## 2026-11-12: Reposicionamento do projeto

Discussão sobre rodar 100% offline levou a três decisões (detalhes em [decisoes.md](decisoes.md)):

- **D9, offline por padrão:** com a Voyage, toda pergunta dependeria de internet, porque o embedding da pergunta é calculado a cada consulta. Embeddings passam para o Ollama no perfil `offline`; a Voyage fica no perfil `cloud`, como comparação.
- **D10, novo posicionamento:** de "assistente da CLT para o trabalhador" para **assistente de normas trabalhistas para RH e DP, on-premise**. Ele reúne CLT, convenção coletiva e políticas internas, indica a hierarquia entre as normas e cita a fonte. A CLT passa a ser a base legal do sistema e a base de testes. O sistema organiza e cita; quem decide é o analista.
- **D11, entrega em camadas:** MVP só com a CLT → segunda fonte (uma convenção coletiva real) → visão completa.

O README foi reescrito com o novo posicionamento, o diagrama on-premise e o roadmap em camadas.

## 2026-11-12: Passo 1, corpus e eval set (v0.1)

### Corpus
- O `curl` para o Planalto falhou de duas formas: primeiro em silêncio (o `-s` escondia o erro) e depois com `Recv failure: Connection was reset`. O servidor derruba clientes automatizados. **Solução:** salvar pelo navegador (Ctrl+S, "somente HTML").
- O navegador salvou como `DEL5452.html` (maiúsculas, `.html`), e não `del5452.htm`, por isso o `ls` não achou o arquivo.
- Arquivo: 3,5 MB, encoding windows-1252, gerado pelo FrontPage. SHA-256 `df64b204…883c`, registrado no eval set.
- Com os embeddings no Ollama (D9), o limite de requisições da Voyage deixou de existir. Por isso o corpus é **a CLT inteira**, e não mais o recorte por temas planejado antes.

### Protótipo do parser
Escrito em Python para descobrir as regras antes do adaptador Java. Problemas encontrados no HTML, e como cada um foi tratado:
1. Texto revogado convive com o vigente, riscado com `<strike>`. É descartado.
2. Versões de medidas provisórias que caducaram (ex.: MP 808/2017) aparecem **sem** riscado, só com a nota "Vigência encerrada". Também são descartadas.
3. O arquivo começa com os arts. 1º e 2º do decreto-lei que aprova a CLT, o que gerava duplicatas dos arts. 1º e 2º.
4. Bug do regex: "Art. 58 - A duração…" era lido como "Art. 58-A". Corrigido: o sufixo só vale colado ao hífen.
5. Sobrescritos quebrados ("§ 1 o") e sobras de dispositivos revogados ("I -;", "a);").
6. 11 artigos ainda aparecem duplicados sem riscado; ficou a versão mais longa, com a lista registrada para revisão.

Resultado: **826 artigos vigentes**, mediana de ~380 caracteres, máximo de ~5.000. Confirma um chunk por artigo (D13).

### Eval set v0.1
24 perguntas: 15 diretas, 2 multi-artigo, 3 de hierarquia (611-A, 611-B, 620), 1 de cobertura parcial (aviso prévio proporcional, que está em outra lei) e 3 fora do escopo (penal, FGTS, INSS). Trechos justificativos extraídos do corpus por script. A pergunta da linha de base (Q01, férias) está incluída. Pendente: revisão das perguntas e respostas pelo Caio.

Um cuidado registrado no item Q15 (tolerância do ponto): a leitura de "5 minutos **por marcação**" vem da jurisprudência do TST, não do texto da CLT. A resposta de referência segue só o texto da lei.

## 2026-11-12: GPU e escolha do modelo

- **Driver NVIDIA atualizado** (537.42 → 621.14, CUDA 13.5). O log do Ollama passou a mostrar `library=CUDA` com a RTX 3060 e 5,0 GB de VRAM livre.
- **`qwen3:8b`:** `30%/70% CPU/GPU`, 13,7 tok/s (o dobro da CPU, mas longe do potencial da GPU, porque não cabe inteiro).
- **`qwen3:4b`:** `100% GPU`, 78 tok/s. Mas a tag padrão é um modelo **só de raciocínio**: ignorou `"think": false`, e a resposta veio com um monólogo em inglês ("Wait, no… Let me check…") até `</think>`. Foram 1.230 tokens para menos de 100 de resposta.
- **`qwen3:4b-instruct-2507-q4_K_M`:** `100% GPU`, **85 tok/s, ~5 s no total**, sem raciocínio. **Escolhido (D15).**
- **`bge-m3`** baixado. Os dois modelos juntos ficam `100% GPU` (3,2 GB + 0,7 GB).
- `application.yml` e `.env.example` atualizados para o modelo novo; `think: false` removido.

### Mais alucinações para a coleção
Todas sem prompt de sistema, mesma pergunta de férias:
- `qwen3:8b`, chamada direta com a temperatura padrão do Ollama: "3 dias para contratos de 1 a 12 meses, 5 dias de 13 a 24…, 15 dias acima de 36 meses". Diferente da invenção anterior do mesmo modelo ("15 dias + 1 por ano, Lei 11.091/2005").
- `qwen3:4b` (raciocínio): acertou 30 dias e o Art. 130 depois de muita hesitação no raciocínio. O "6 meses = 15 dias" que ele acrescentou corresponde às férias proporcionais na rescisão (Arts. 146 e 147), mas foi apresentado como regra geral.
- `qwen3:4b-instruct`: "**13 dias úteis** de férias, conforme a **Lei nº 6.015/1974**". A regra não existe, e a Lei 6.015 é a Lei de Registros Públicos. Registrado em [exemplos/sem-prompt-4b-instruct.json](exemplos/sem-prompt-4b-instruct.json).

Conclusão para o README: a resposta inventada muda de execução para execução e de modelo para modelo, e o modelo menor alucina tão livremente quanto o maior. A memória do modelo não serve como fonte, e é exatamente isso que o RAG resolve.

### Teste pela aplicação
`/api/smoke/chat` com o modelo novo (prompt de sistema, temperatura 0.2): **6,9 s**, 98 tokens de entrada e 295 de saída (antes: 111 s e 606). A aplicação sobe e funciona com o modelo novo.

A resposta trouxe um tipo novo de erro: **artigo certo, regra errada**. "12 dias úteis de férias, conforme o Art. 130", mais um "13º dia de férias" inventado. É o inverso do `qwen3:8b` com prompt, que acertou a regra e errou o artigo, e é mais perigoso, porque a citação correta dá credibilidade ao erro. Por isso o eval ganhou a métrica de **fidelidade** (v0.2): além de citar o artigo certo, a resposta precisa dizer só o que o trecho sustenta. Registrado em [exemplos/com-prompt-4b-instruct.json](exemplos/com-prompt-4b-instruct.json).

### Pendências
- Aumentar o contexto (`num_ctx`) no passo 4 e conferir se os dois modelos continuam cabendo na VRAM.

## 2026-11-12: Passo 3, ingestão (código)

- **Parser portado para Java** (`CltHtmlParser`, jsoup). O Maven Central é bloqueado no ambiente do Claude, mas havia um jar do jsoup local (1.15.3, vindo do Gradle), então deu para compilar e rodar o parser contra a CLT real antes de entregar.
- A comparação com o protótipo Python achou **bugs nos dois**:
  - O protótipo perdia o Art. 12 (diferença de parser HTML) e os Arts. 554 e 555, escritos "Art 554." sem ponto no HTML.
  - O Java perdia o Art. 556, porque o critério de "MP caducada" olhava as primeiras notas do artigo inteiro, e elas vinham de dispositivos vizinhos. Corrigido: o critério olha só a linha do caput.
  - Âncoras erradas no próprio HTML (o Art. 553 tem `name="art936"`). O link usa `art553` quando essa âncora existe.
  - Normalização de "Lei no 8.036" para "Lei nº 8.036", aplicada também no protótipo, para os trechos do eval baterem com o parser Java.
- Resultado: **835 artigos (834 vigentes)**. Todos os 33 trechos justificativos do eval aparecem literalmente na saída do parser Java.
- Testes JUnit (`CltHtmlParserTest`): um caso por problema do HTML, mais um teste contra o arquivo real. Os casos foram conferidos aqui com um harness, porque o JUnit também não estava disponível.
- O código com Spring (adaptador, ingestão, busca, avaliação) foi compilado contra stubs das APIs do Spring AI. Isso garante a sintaxe e os tipos, mas **a primeira build real será na máquina do Caio**.
- Endpoints novos: `POST /api/ingestao/clt`, `GET /api/busca`, `POST /api/avaliacao/recuperacao`.

### Primeira execução na máquina do Caio
- O primeiro teste deu 404 nos endpoints novos: a aplicação em execução era a build antiga. O `mvn clean` falhou com `Failed to delete target\classes`, porque um `java.exe` órfão de uma execução anterior segurava os arquivos. Resolvido com `tasklist | grep -i java` + `taskkill //F //PID <n>` (sem os sinais `< >`, que no bash viram redirecionamento).
- **Compilou e subiu de primeira** depois disso. As APIs do Spring AI 2.0 que só tinham sido testadas contra stubs estavam certas.
- **Ingestão:** 834 trechos em **40 s** (bge-m3 na GPU).
- **Busca:** "prazo para pagar as férias" → **Art. 145 em 1º lugar**, seguido de 137 e 134.

### Primeiro número: recuperação 90,5% (top-5)
19 de 21 perguntas com o artigo esperado entre os 5 primeiros; MRR 0,655; artigo certo em 1º lugar em 10 de 21. Os dois erros (Q10, banco de horas, Art. 59 § 5º; Q15, tolerância do ponto, Art. 58 § 1º) têm a mesma causa: **o assunto está num parágrafo e o caput trata de outra coisa**, então um vetor por artigo inteiro dilui o tema. Os scores das perguntas fora do escopo (0,54–0,60) se misturam com os das válidas (0,53–0,71): um limiar de similaridade sozinho não resolve a recusa. Análise e hipóteses (chunk por dispositivo, busca híbrida) em [eval/README.md](eval/README.md#resultados). Resultado bruto em [eval/resultados/](eval/resultados/2026-11-12-recuperacao-k5.json).

## 2026-11-12: Passo 4, endpoint de RAG (código)

- `POST /api/perguntas`: busca top-5 → prompt com regras → `qwen3:4b-instruct` → verificação de citações. Desenho e motivo de cada regra do prompt na D17.
- `POST /api/avaliacao/respostas`: roda as 24 perguntas pelo RAG completo e mede citação, citações fora dos trechos, recusas e latência. A fidelidade é revisada à mão a partir das respostas devolvidas.
- O carregamento do eval set virou um componente compartilhado (`EvalSetRepositorio`) pelas duas avaliações.
- `num-ctx: 8192` no Ollama, para caberem 5 trechos.
- Testes: `CitacoesTest` (extração de "Art. 130, I", "art. 145", "Art 59-A", "Art. 611-B"; parágrafo não vira artigo; recusa sem citação). Conferidos aqui com um harness; o código com Spring foi compilado contra stubs, como no passo 3.

### Primeira execução: o "depois" da escada
Pergunta de férias pelo RAG: **"30 dias corridos, quando não houver faltado ao serviço mais de 5 vezes (Art. 130, I)"**. Regra certa, artigo e inciso certos, nenhuma citação fora dos trechos. Foram **1,7 s** (132 ms de busca + 1,6 s de LLM), 1.817 tokens de entrada e 62 de saída. Com `num_ctx` 8192, o `qwen3:4b-instruct` passou a ocupar 3,8 GB; somado ao `bge-m3` (0,7 GB), tudo continua 100% na GPU.

| Configuração | Resposta |
|---|---|
| 8B sem prompt | "15 dias + 1 por ano, Lei 11.091/2005" |
| 8B com prompt | 30 dias, mas "Art. 133" |
| 4B com prompt | "Art. 130", mas "12 dias úteis" |
| **4B com RAG** | **30 dias corridos, Art. 130, I** |

### Avaliação das respostas (24 perguntas)
Citação 91,7% · fidelidade 81% (revisão manual) · recusas corretas 3/3 · latência média 1,6 s (máx. 3,4 s). As duas falhas de citação coincidem com as duas falhas de recuperação. Três tipos de falha de fidelidade: erro de raciocínio com o trecho certo (Q02: 8 faltas → respondeu 30 dias em vez de 24; Q03: contradição), trecho parecido sem recusa (Q10, a mais perigosa) e recusa por falha de recuperação (Q15). Detalhes em [eval/README.md](eval/README.md#análise-das-respostas-2026-11-12) e revisão por pergunta em [eval/resultados/](eval/resultados/2026-11-12-respostas-k5.json).

O detector de citações foi ajustado com base nessa rodada ("artigo 617" por extenso; "art. 18 da Lei 8.036" não conta; recusa no fim de uma resposta com citações não conta como recusa), com testes novos no `CitacoesTest`.

## 2026-11-12: Passo 6a, melhorias na busca (código)

- **`DivisorDeDispositivos`:** divide o artigo em caput (com incisos) e parágrafos. Na CLT: 1.863 dispositivos.
- **Ingestão em dois níveis**, marcados pelo metadado `granularidade` (`artigo` | `dispositivo`). Cada documento de dispositivo guarda o artigo inteiro em `texto_artigo`.
- **`Recuperador`:** busca vetorial com filtro de granularidade, busca textual em SQL (`to_tsquery('portuguese', …)` com OU) e fusão por RRF, sempre devolvendo artigos (o melhor dispositivo de cada um).
- O RAG, a busca e as duas avaliações passaram a usar o `Recuperador`; as avaliações aceitam `granularidade` e `estrategia` na URL.
- Testes: `DivisorDeDispositivosTest` (inclui os casos reais do Art. 59 e do Art. 130) e `RecuperadorTest` (agrupamento por artigo, RRF promovendo o artigo que aparece nas duas listas, artigo vindo só da busca textual, sanitização dos termos para o `to_tsquery`). Conferidos aqui com um harness; o SQL só roda de verdade na máquina do Caio.

### Medição das 4 combinações
- **Ingestão:** 2.695 documentos (834 artigos + 1.861 dispositivos) em 67 s.
- **Teste manual** com "banco de horas por acordo individual" (dispositivo + híbrida): o Art. 59 apareceu com `"dispositivo":"§ 5º"`, mas em 2º lugar, atrás do 59-B. Foi o primeiro sinal de que a parte textual trazia ruído.
- **Eval:** artigo + vetorial 90,5% · artigo + híbrida 76,2% · **dispositivo + vetorial 100%** · dispositivo + híbrida 85,7%. Q10 e Q15 foram para 1º lugar com a busca por dispositivo.
- **Novo padrão:** dispositivo + vetorial (`application.yml`). A busca híbrida virou um resultado negativo documentado, que é tão útil para o portfólio quanto um positivo: mostra que a decisão veio de medição, não de moda.
- Ressalva registrada: risco de sobreajuste ao eval. Falta validar com perguntas novas.

### Respostas com a busca por dispositivo
`POST /api/avaliacao/respostas?granularidade=dispositivo&estrategia=vetorial`: **citação 100%**, recusas 3/3, 0 recusas indevidas, latência 1,9 s em média. Revisão manual: **18 fiéis, 2 parciais, 1 infiel** (antes: 17 fiéis, 3 infiéis, 1 recusa indevida).
- Banco de horas (Q10) e tolerância do ponto (Q15) passaram a responder certo no essencial, mas **omitindo a condição** (6 meses; 5 e 10 minutos).
- **Regressão na Q11** (intervalo de almoço): o Art. 71 veio em 3º e o modelo disse que a regra "não é especificada". Erro de leitura do 4B.
- **Padrão novo:** inciso errado na citação ("Art. 130, I" em vez de II; "Art. 611-B, I" em vez de XI). O artigo sempre certo, o inciso não, e o detector só checa o artigo.

### Próximos ajustes candidatos
1. Prompt: exigir prazos, limites e condições do dispositivo na resposta.
2. Verificar o dispositivo citado (§ ou inciso) contra o texto do trecho, além do artigo.
3. Conjunto de validação com perguntas novas, para medir sem sobreajuste.
4. Comparar o `qwen3:8b` nas perguntas de leitura e raciocínio (Q02, Q03, Q11).

## 2026-11-12: Passo 6b, condições no prompt e verificador de dispositivos

- **Prompt:** duas regras novas na `PromptRag`. Parágrafo ou inciso só depois de conferir o texto (na dúvida, só o artigo). Prazos, limites, percentuais e condições fazem parte da resposta.
- **`VerificadorDeCitacoes`:** confere cada "(Art. N, § X)" ou "(Art. N, inciso)" contra o artigo recuperado. Marca dispositivo que não existe e número da frase que não está no dispositivo citado (aceitando número por extenso, como "trinta", e descontando números da pergunta).
- **Teste com dados reais antes do Ollama:** o verificador rodou sobre as 24 respostas gravadas da rodada anterior. Primeira versão: 3 acertos (Q02, Q05, Q09) e 2 falsos positivos na Q11, porque a citação vinha no início da frase e o "8 horas" era da pergunta. Corrigido, ficou em 3 acertos e 0 falsos positivos. A Q20 (inciso I no lugar de XI, sem números na frase) continua passando: limitação documentada na D19.
- **Integração:** `citacao.problemasDeDispositivo` na resposta da API; `respostasComProblemaDeDispositivo` no relatório da avaliação.
- **Testes:** `VerificadorDeCitacoesTest` com os casos reais (11 testes). Sem acesso ao Maven Central no ambiente do Claude, rodaram com um runner mínimo; o `mvn test` na máquina do Caio é a confirmação.
- **Próximo:** medir o efeito das duas mudanças no eval, e depois montar o conjunto de validação com perguntas novas.

### Medição da rodada 6b
- **Resultado:** 17 fiéis, 3 parciais, 1 infiel (antes 18, 2, 1); 4 citações de dispositivo erradas, como antes, mas em perguntas diferentes. Q10 passou a dizer os 6 meses; Q15 continua sem os 5 e 10 minutos; Q11 continua concluindo errado (cita o mínimo de 1 hora acima de 6 horas e diz que não há regra para 8 horas).
- **Leitura honesta:** as duas regras novas no prompt não mudaram o placar. O modelo de 4B não consegue conferir o próprio inciso. Isso é um resultado: aponta para corrigir no sistema (rebaixar a citação marcada para o artigo) ou trocar o modelo.
- **Bug encontrado pelo próprio eval:** o verificador marcou "Art. 59-B, § 6º" na Q10, citação que não existe na resposta. Causa: grupos de captura dentro de quantificadores possessivos no Java guardam o valor de tentativas que falharam. O mesmo padrão estava no `Citacoes`, que já estava em produção desde o passo 4 ("art. 20-A da Lei" + "Art. 477" virava "477-A"). Corrigido nos dois, com testes de regressão.
- **Ponto cego corrigido:** números por extenso na resposta ("duas horas") agora são conferidos. A Q09 passou a ser detectada.
- Com as correções, aplicado às duas rodadas gravadas: anterior, 3 detecções (Q02, Q05, Q09); 6b, 2 (Q09, Q12); nenhum falso positivo. Não detectados: Q17 e Q20, com dispositivo errado em frases sem números.

## 2026-11-12: Passo 6c, rebaixar a citação marcada

- **`VerificadorDeCitacoes.corrigir`:** devolve a resposta com cada citação marcada trocada pelo artigo, e os problemas encontrados. Leva junto um inciso que vem logo depois do parágrafo ("§ 3º, III").
- **`RagService`:** entrega a resposta corrigida; o texto do modelo fica em `citacao.respostaOriginal`. A avaliação grava as duas versões.
- **Teste com dados reais:** nas duas rodadas gravadas, 5 citações rebaixadas, todas erradas (Q02, Q05, Q09; Q09, Q12), nenhuma certa alterada.
- **Testes:** mais 4 em `VerificadorDeCitacoesTest` (17 no total).
- Decisão na D20.

## 2026-11-13: Passo 6d, conjunto de validação

- **Por quê:** tudo até aqui foi ajustado e medido nas mesmas 24 perguntas. Sem um conjunto separado, não dá para saber se as melhorias generalizam.
- **O quê:** 26 perguntas novas em `docs/eval/validacao-set.json`, com gabarito copiado do corpus pelo mesmo `build_eval.py` (agora com o argumento `validacao`). Regenerar o conjunto de desenvolvimento pelo script deu um arquivo idêntico ao salvo, então a mudança no gerador não alterou nada.
- **Escolha dos temas:** só assuntos fora do conjunto de desenvolvimento; o único artigo repetido é o 611-B, num inciso diferente (XIII, licença-maternidade). Todos os artigos esperados existem no índice.
- **Armadilhas:** estabilidade da gestante (garantia na CLT, prazo na Constituição), 13º salário e seguro-desemprego (citados no 611-B, regulados em outras leis), 12x36 noturna (regra geral mais exceção).
- **API:** `?conjunto=validacao` nos dois endpoints de avaliação; arquivo configurável em `app.eval.validacao`.
- Decisão na D21.

### Primeira execução da validação
- **Busca:** 78,3% (18/23) contra 100% no desenvolvimento. Cinco falhas totais, quatro por jargão: "12x36", "home office", "gerente com diploma e salário alto", "das 22h às 5h".
- **Respostas:** 11 fiéis, 5 parciais, 3 infiéis, 4 recusas indevidas (3 por falha de busca, 1 do modelo, V14). Fora do escopo: 3/3, inclusive as duas armadilhas citadas no 611-B.
- **Falhas do modelo com a busca certa:** V08 usou o Art. 311 (jornalistas) no lugar do 29; V22 atribuiu ao 611-A o que está no 611-B; V23 caiu na armadilha da estabilidade da gestante.
- **Bugs achados:** "art. 10 do Ato das Disposições Constitucionais Transitórias" contava como citação da CLT fora dos trechos; "cento e vinte" virava 20 no verificador. Corrigidos com testes (o verificador agora reconhece números por extenso até 999). Rodado de novo nas três rodadas gravadas: nada mudou nas anteriores.
- **Leitura:** o conjunto de desenvolvimento, escrito com a lei aberta, compartilhava o vocabulário dela. O gargalo real é a distância entre o jeito que o RH pergunta e o jeito que a lei escreve.

## 2026-11-13: Passo 6e, glossário RH → lei

- **Ordem, para a medição sair limpa:** (1) V01, V03, V07, V13 e V21 passaram para o desenvolvimento (Q22 a Q26) e entraram V24 a V28 na validação, escritas e congeladas antes do glossário; (2) o glossário foi montado só com termos das perguntas de desenvolvimento; (3) medir com e sem.
- **Conferência automática:** cada termo do glossário aparece em alguma pergunta de desenvolvimento e em nenhuma de validação. "presencial" saiu por não aparecer em nenhuma.
- **Código:** `ExpansorDeConsulta` (normaliza acentos e maiúsculas, palavra inteira, a mesma expansão entra uma vez só); `Recuperador` busca pela consulta expandida; o LLM recebe a pergunta original. `?expansao=true|false` na busca e nas avaliações; `metricas.termosGlossario` na resposta.
- **Testes:** `ExpansorDeConsultaTest` (5). O stub de log ganhou `warn`.
- **Expectativa registrada antes de medir (D22):** ganho em Q22 a Q26 e efeito quase nulo na validação.

### Medição do glossário
- **Primeira tentativa:** os seis arquivos vieram vazios em 2 segundos. A aplicação não estava no ar: havia um `java.exe` antigo travando `target`, e depois o `clean` apagou as classes sem o IntelliJ recompilar. Ao subir, falhou com "No default constructor found" no `ExpansorDeConsulta`: dois construtores (o da aplicação e o dos testes) e nenhum marcado com `@Autowired`. Os stubs usados na compilação do lado do Claude não executam a injeção de dependências, então só a aplicação real pegou isso. Corrigido.
- **Busca:** desenvolvimento 80,8% → 92,3% (MRR 0,597 → 0,840); validação 87,0% nos dois casos. Quatro das cinco perguntas de jargão passaram a achar o artigo em 1º.
- **Efeito colateral:** Q12 perdeu o Art. 611-A ("almoço" puxou para o Art. 71) e a resposta ficou errada; Q24 continua sem o 59-A, com quatro expansões diluindo a consulta.
- **Respostas:** desenvolvimento 21/26 fiéis; validação 13/23. Q25: busca certa, resposta errada (o modelo inverteu os §§ 1º e 2º do Art. 75-C). Q10, Q11 e Q15 fiéis pela primeira vez; Q02 voltou a errar sem relação com o glossário.
- **Leitura:** o glossário funciona no que cobre e precisa de outra forma de aplicação (buscar separado e fundir). Jargão fora dele passa direto (V24, V27, V28).
