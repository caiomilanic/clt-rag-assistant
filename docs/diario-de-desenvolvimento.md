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

### Pendências abertas
- **Latência alta:** os 606 tokens de saída para uma resposta curta indicam que o modo de raciocínio do Qwen3 está ativo. Testar `spring.ai.ollama.chat.options.think: false`; se a propriedade não existir nesta versão, usar `/no_think` no prompt de sistema. Medir a latência antes e depois.
- Confirmar RAM e GPU da máquina para validar o modelo (`qwen3:8b` vs `qwen3:4b`).
- O IntelliJ está rodando com o JDK 26; trocar o SDK do projeto para o 25.
- Tirar o projeto do OneDrive antes do primeiro commit (sincronização da pasta `target/`).
- Inicializar o repositório Git.
