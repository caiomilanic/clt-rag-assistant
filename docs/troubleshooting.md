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
**Primeiro, descubra onde o modelo está rodando:**
```bash
ollama run qwen3:8b "oi" > /dev/null && ollama ps   # coluna PROCESSOR
```
O `ollama ps` só lista modelos carregados na memória. Se vier vazio, carregue o modelo antes, como no comando acima.

**Causas prováveis:**
- **`100% CPU`:** a GPU não está sendo usada. Na CPU, espere ~5–10 tokens/s, ou seja, de 1 a 2 minutos por resposta. Veja a próxima seção.
- A primeira chamada carrega o modelo na memória e é sempre mais lenta.
- O modo de raciocínio do Qwen3 gera centenas de tokens antes da resposta. Sinal: `completionTokens` alto para uma resposta curta.

**Para medir a velocidade real**, chame o Ollama direto e divida `eval_count` por `eval_duration` (em nanossegundos):
```bash
curl -s http://localhost:11434/api/chat -d '{"model":"qwen3:8b","messages":[{"role":"user","content":"oi"}],"think":false,"stream":false}'
```

### `100% CPU` mesmo com placa NVIDIA
**Causa:** o Ollama não detectou a GPU. Confirme no log:
```bash
grep -i "inference compute" "$LOCALAPPDATA/Ollama/server.log" | tail -2
# library=cpu → GPU não detectada; library=cuda → detectada
```
**Soluções, em ordem:**
1. Atualizar o driver NVIDIA (GeForce Experience → Drivers, ou nvidia.com/drivers) e reiniciar o PC. Drivers antigos não atendem às bibliotecas CUDA das versões atuais do Ollama.
2. Em notebooks: *Configurações → Sistema → Tela → Elementos gráficos*, adicionar `ollama.exe` e `ollama app.exe` (em `%LOCALAPPDATA%\Programs\Ollama\`) como **Alto desempenho**, e manter o notebook na tomada.
3. Fechar o Ollama pelo ícone da bandeja (*Quit*) e abrir de novo depois de cada ajuste.

### Pouca RAM disponível para o modelo
**Sintoma:** o log do Ollama mostra `available` bem menor que o tamanho do modelo, e o sistema fica lento, porque parte do modelo vai para o disco (swap).
**Soluções:**
- Fechar aplicativos pesados em segundo plano (launchers de jogos, muitas abas de navegador).
- Limitar a memória do WSL, que o Docker Desktop usa. Crie `C:\Users\<usuario>\.wslconfig` com:
  ```ini
  [wsl2]
  memory=3GB
  ```
  Depois rode `wsl --shutdown` e abra o Docker Desktop de novo. O Postgres do projeto precisa de muito pouco.

### `think: false` no `application.yml` não muda nada
**Causa:** a propriedade não é repassada ao Ollama nesta versão do Spring AI, e o Spring ignora propriedades desconhecidas sem erro. Chamando a API do Ollama direto com `"think": false`, funciona.
**Solução em teste:** `/no_think` no final do prompt de sistema (ver diário).

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
