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

### Pendências
- O teste de latência pela aplicação (`/api/smoke/chat`) com o modelo novo ainda não foi feito; até aqui as medições foram direto na API do Ollama.
- Aumentar o contexto (`num_ctx`) no passo 4 e conferir se os dois modelos continuam cabendo na VRAM.
