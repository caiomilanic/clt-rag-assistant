# Troubleshooting

Problemas reais encontrados ao rodar o projeto (principalmente no Windows), com a causa e a solução.

## Banco de dados

### `db: DOWN` com "password authentication failed" / "autenticação do tipo senha falhou"
**Causa:** outro PostgreSQL (instalado na máquina) está respondendo na porta. Uma pista: se a mensagem vier em português, não é o container, porque a imagem Docker responde em inglês.
**Solução:** o projeto já usa a porta **5433** no host. Confira se `docker-compose.yml` e `application.yml` apontam para ela. Se o container foi criado antes com outra senha, recrie o volume:
```bash
docker compose down -v && docker compose up -d postgres
```

### `Connection to localhost:5433 refused`
**Causa:** o container não está rodando.
**Solução:**
```bash
docker ps -a --filter name=cltrag   # o container existe? qual status?
docker compose up -d postgres       # rodar na pasta do docker-compose.yml
docker logs cltrag-postgres         # se o status for "Exited"
```

## Docker

### `docker: command not found` logo após instalar
**Causa:** terminais abertos antes da instalação não enxergam o PATH atualizado. Se o terminal está dentro do IntelliJ, ele herda o PATH de quando a IDE abriu.
**Solução:** fechar e reabrir o terminal (ou a IDE inteira). Se persistir, confirme que `C:\Program Files\Docker\Docker\resources\bin` está no `Path` do sistema.

### `wsl` não é reconhecido
**Causa:** WSL não instalado, PowerShell de 32 bits (x86) ou Windows desatualizado.
**Solução:** abrir o PowerShell de 64 bits como administrador e rodar `wsl --install`, ou ativar os recursos manualmente:
```powershell
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
```
Reiniciar depois. A virtualização precisa estar habilitada na BIOS (Gerenciador de Tarefas → Desempenho → CPU → "Virtualização").

### `no configuration file provided`
**Causa:** comando rodado fora da pasta do `docker-compose.yml`. Ao extrair o zip, é comum ficar com a pasta duplicada (`clt-rag-assistant/clt-rag-assistant`).
**Solução:** `cd` para a pasta onde estão o `pom.xml` e o `docker-compose.yml`.

## Ollama

### `Connection refused` em `localhost:11434`
**Causa:** o Ollama não está rodando.
**Solução:** abrir o Ollama pelo menu Iniciar ou rodar `ollama serve` num terminal separado. Para testar: `curl -s http://localhost:11434/api/tags`.

### `HTTP 404 - model 'qwen3:8b' not found`
**Causa:** o modelo configurado não foi baixado.
**Solução:** `ollama pull qwen3:8b` (ou o modelo definido em `OLLAMA_MODEL`). Confira com `ollama list`.

### Respostas muito lentas (mais de 60 s)
**Causas prováveis:**
- A primeira chamada carrega o modelo na memória e é sempre mais lenta.
- O modo de raciocínio do Qwen3 gera centenas de tokens antes da resposta. Sinal: `completionTokens` alto para uma resposta curta.
- Sem GPU, o modelo roda na CPU.

**Solução:** desligar o raciocínio (ver diário, pendências) ou usar um modelo menor (`qwen3:4b`).

## Requisições

### `HTTP 400` com `Invalid UTF-8 middle byte` no log
**Causa:** o terminal (Git Bash no Windows) enviou acentos em outra codificação, e o JSON chegou inválido.
**Solução:** enviar o JSON por arquivo, salvo em UTF-8:
```bash
curl -s -X POST http://localhost:8080/api/smoke/chat \
  -H "Content-Type: application/json" \
  --data-binary @docs/exemplos/pergunta-ferias.json
```
Ou usar escape Unicode no texto: `"f\u00e9rias"`.

### `curl` no PowerShell não funciona como no Linux
**Causa:** no Windows PowerShell, `curl` é um apelido para `Invoke-WebRequest`, e as aspas do JSON se comportam diferente.
**Solução:** usar o Git Bash ou `Invoke-RestMethod`:
```powershell
$body = @{ question = "Quantos dias de férias o trabalhador tem direito por ano?" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/smoke/chat `
  -ContentType "application/json; charset=utf-8" -Body $body
```

## Java

### `release version 25 not supported`
**Causa:** o Maven está usando um JDK mais antigo.
**Solução:** confira `java -version` e `mvn -v`. O Maven usa o `JAVA_HOME`, não o JDK configurado na IDE.
