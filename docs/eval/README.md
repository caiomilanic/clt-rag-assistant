# Eval set

[`eval-set.json`](eval-set.json) é a régua do projeto: perguntas reais de RH/DP, cada uma com o artigo da CLT esperado e o trecho da lei que justifica a resposta. Ele mede a qualidade da recuperação e das respostas a cada mudança (passo 6) e compara os perfis `offline` e `cloud`.

## Composição (v0.2)

| Tipo | Qtd. | O que testa |
|---|---|---|
| `direta` | 15 | A resposta está num único dispositivo |
| `multi_artigo` | 2 | A resposta completa combina mais de um dispositivo |
| `hierarquia` | 3 | Relação entre lei, convenção e acordo coletivo (Arts. 611-A, 611-B, 620) |
| `cobertura_parcial` | 1 | O corpus responde só uma parte (aviso prévio proporcional está em outra lei); o sistema deve dizer o que falta |
| `fora_do_escopo` | 3 | O corpus não responde (direito penal, FGTS, INSS); o sistema deve dizer isso sem citar artigos |

Temas: férias, jornada, rescisão, ausências e hierarquia entre normas. Os itens marcados com `camada2` são os que vão ganhar uma cláusula de convenção coletiva para comparação na camada 2.

## Métricas

- **Acerto de recuperação:** fração das perguntas (exceto `fora_do_escopo`) em que todos os `artigos_esperados` aparecem entre os 5 trechos recuperados. Meta: ≥ 90%.
- **Citação:** fração das respostas que citam ao menos um artigo esperado ou, em `fora_do_escopo`, que declaram não ter encontrado a resposta sem citar artigos. Meta: 100%.
- **Fidelidade:** fração das respostas cujo conteúdo é sustentado pelos trechos citados. Meta: 100%. Entrou na v0.2 depois de um caso real: o `qwen3:4b-instruct`, sem RAG, citou o **artigo certo (Art. 130) com a regra errada** ("12 dias úteis"). Conferir só a citação teria dado esse caso como acerto ([exemplo](../exemplos/com-prompt-4b-instruct.json)). Por enquanto é avaliada à mão numa amostra; a automação (modelo juiz comparando resposta e trechos) fica para o passo 6.

## Regras

- **A verdade de referência é o texto do corpus versionado** (`corpus/clt/DEL5452.html`, SHA-256 registrado no JSON), não o que se "sabe" sobre a lei. Se o corpus for atualizado, atualize o hash e revise as respostas.
- **Os trechos justificativos são copiados do corpus por script** ([`tools/eval/build_eval.py`](../../tools/eval/build_eval.py)), nunca digitados à mão.
- Respostas de referência dizem o essencial. Na avaliação automática, o que se mede é a fonte; a qualidade do texto é revisada à mão numa amostra.
- Perguntas novas entram no script, não direto no JSON.

## Como rodar

Com a aplicação de pé e a CLT ingerida (`POST /api/ingestao/clt`):

```bash
curl -s -X POST "http://localhost:8080/api/avaliacao/recuperacao?k=5"
```

A resposta traz `acertoRecuperacao` (meta ≥ 0,9), `mrr` e, para cada pergunta, os artigos recuperados e a posição de cada artigo esperado. Nas perguntas fora do escopo, o `scoreTop1` mostra quão "parecido" ficou o melhor resultado. Isso vai servir para calibrar o limiar de "não encontrei" no passo 8.

Para avaliar as respostas (citação, citações fora dos trechos, recusas), rode o eval pelo RAG completo. Leva alguns minutos, porque faz uma chamada ao LLM por pergunta:

```bash
curl -s -X POST "http://localhost:8080/api/avaliacao/respostas?k=5"
```

A fidelidade (o conteúdo bate com o trecho?) é revisada à mão a partir do campo `resposta` de cada resultado; a automação com um modelo juiz fica para o passo 6.

## Resultados

| Data | Configuração | Acerto de recuperação (top-5) | MRR | Artigo certo em 1º | Detalhes |
|---|---|---|---|---|---|
| 2026-11-12 | `bge-m3`, um trecho por artigo com cabeçalho de contexto | **90,5%** (19/21) | 0,655 | 10/21 | [JSON](resultados/2026-11-12-recuperacao-k5.json) |
| 2026-11-12 | artigo + híbrida (vetorial + texto, RRF) | 76,2% (16/21) | 0,599 | 10/21 | [JSON](resultados/2026-11-12-busca-comparacao-k5.json) |
| 2026-11-12 | **dispositivo + vetorial** (novo padrão) | **100%** (21/21) | 0,716 | 11/21 | idem |
| 2026-11-12 | dispositivo + híbrida | 85,7% (18/21) | **0,740** | **14/21** | idem |

### Respostas (RAG completo)

| Data | Configuração | Citação | Fidelidade (revisão manual) | Citações fora dos trechos | Recusas corretas | Recusas indevidas | Latência média / máx. | Detalhes |
|---|---|---|---|---|---|---|---|---|
| 2026-11-12 | `qwen3:4b-instruct`, top-5, `num_ctx` 8192, busca artigo + vetorial | **91,7%** (22/24) | **81%** (17/21) | 0 reais (1 falso positivo) | **3/3** | 1 (Q15) | **1,6 s / 3,4 s** | [JSON com revisão](resultados/2026-11-12-respostas-k5.json) |
| 2026-11-12 | idem, busca **dispositivo + vetorial** | **100%** (24/24) | **85,7%** (18/21) + 2 parciais | 0 | **3/3** | **0** | 1,9 s / 5,2 s | [JSON com revisão](resultados/2026-11-12-respostas-dispositivo-k5.json) |

### Análise da primeira rodada (2026-11-12)
- **A meta de ≥ 90% foi atingida, mas no limite.** Com 21 perguntas, cada uma vale ~4,8 pontos: um erro a mais derruba para 85,7%.
- **Os dois erros têm a mesma causa: o assunto está num parágrafo, e o caput do artigo trata de outra coisa.**
  - Q10 (banco de horas por acordo individual): a regra está no Art. 59, § 5º, mas o caput do 59 fala de horas extras. A busca trouxe o 59-A (12x36) e artigos de outros capítulos.
  - Q15 (tolerância de minutos no ponto): a regra está no Art. 58, § 1º, mas o caput do 58 fala da jornada de 8 horas.
  Com um vetor por artigo inteiro, o tema do parágrafo fica "diluído" no tema do caput.
- **O artigo certo vem em 1º lugar em só metade dos casos** (MRR 0,655). Na geração isso pesa menos, porque o modelo recebe os 5 trechos, mas mostra margem para melhorar.
- **Fora do escopo:** os melhores scores das perguntas fora do escopo (0,537, 0,551 e 0,603) caem dentro da faixa das perguntas válidas (0,531 a 0,705). **Um limiar de similaridade sozinho não separa "não está no corpus" de "está".** A recusa vai depender também do LLM avaliar se os trechos respondem à pergunta (passos 4 e 8).

### Hipóteses para a próxima rodada (passo 6)
1. **Chunk por dispositivo** (caput, § e inciso como trechos separados, com o artigo como metadado e a citação continuando por artigo). Ataca diretamente Q10 e Q15.
2. **Busca híbrida** (vetorial + texto completo do Postgres). Termos exatos como "banco de horas" aparecem literalmente no Art. 59.
3. Medir as duas separadamente, para saber qual contribui.

### Análise das respostas (2026-11-12)
- **Quando a busca acerta, a geração cita certo.** As duas falhas de citação (Q10 e Q15) são exatamente as duas falhas de recuperação. Nas 19 perguntas com o artigo recuperado, a resposta citou o artigo esperado em todas.
- **A recusa funciona pelo modelo, não pelo score.** As 3 perguntas fora do escopo foram recusadas sem citar artigos, o que um limiar de similaridade não conseguia fazer.
- **Latência:** 1,6 s em média e 3,4 s no pior caso, bem abaixo da meta de 15 s.
- **Fidelidade de 81% (17/21), com três tipos de falha:**
  1. **Erro de raciocínio com o trecho certo** (Q02 e Q03). Na Q02, o modelo recuperou e citou o Art. 130, mas com 8 faltas respondeu 30 dias, quando o correto é 24 (inciso II): escreveu que 8 faltas "está dentro do limite de 5". Na Q03, começou com "Não pode dividir" e em seguida explicou como dividir. São limites de compreensão e aritmética do modelo de 4B.
  2. **Trecho parecido que não responde, sem recusa** (Q10). Sem o Art. 59 entre os trechos, o modelo respondeu "Não" com base no Art. 617, que trata de outra coisa. É a falha mais perigosa: resposta confiante e errada, com uma citação real.
  3. **Recusa correta, mas por falha de recuperação** (Q15). Melhor que inventar, mas a pergunta tinha resposta na CLT.
- **Problemas menores:** incisos inexistentes ("Art. 145, I", "Art. 59, I"), "§ III" no lugar de "inciso III", resposta seca demais (Q07) e falta do aviso de cobertura parcial (Q18).
- **Ajustes feitos no detector depois desta rodada:** reconhecer "artigo 617" por extenso (na Q10 ele passou despercebido); ignorar artigos de outras leis citados dentro da CLT ("art. 18 da Lei nº 8.036", que gerou o falso positivo da Q17); e não contar como recusa uma resposta que cita artigos e acrescenta a frase de recusa no fim (Q06).

### Como comparar configurações de busca (passo 6)
```bash
for g in artigo dispositivo; do for e in vetorial hibrida; do
  curl -s -X POST "http://localhost:8080/api/avaliacao/recuperacao?k=5&granularidade=$g&estrategia=$e" > recuperacao-$g-$e.json
done; done
```
Cada arquivo traz `acertoRecuperacao`, `mrr` e o resultado por pergunta da combinação.

### Prioridades para o passo 6
1. **Recuperação** (chunk por dispositivo e/ou busca híbrida): corrige a origem de Q10 e Q15.
2. **Comparar o `qwen3:8b`** nas perguntas que exigem aplicar a regra ao caso (Q02, Q03), para medir quanto do erro de raciocínio é do tamanho do modelo, sabendo que o 8B não cabe inteiro na GPU de 6 GB.
3. **Juiz automático de fidelidade**, para não depender só da revisão manual.

### Comparação de configurações de busca (2026-11-12)

Posição do artigo esperado em cada configuração (— = fora do top-5):

| Pergunta | artigo + vetorial | artigo + híbrida | **dispositivo + vetorial** | dispositivo + híbrida |
|---|---|---|---|---|
| Q04 vender férias (143) | 1 | — | 5 | — |
| Q05 prazo pagamento férias (145) | 1 | — | 1 | — |
| Q08 adicional hora extra (59) | 2 | 1 | 2 | — |
| **Q10 banco de horas (59, § 5º)** | **—** | 2 | **1** | 1 |
| **Q15 tolerância do ponto (58, § 1º)** | **—** | — | **1** | 1 |
| Q17 demissão por acordo (484-A) | 3 | — | 3 | 5 |
| Q21 acordo × convenção (620) | 3 | — | 2 | 3 |

(Só as linhas em que alguma configuração difere; o JSON tem todas.)

- **A busca por dispositivo resolveu exatamente os dois casos-alvo.** Q10 e Q15 foram de "fora do top-5" para **1º lugar**, sem perder nenhuma outra pergunta: **100% no top-5**.
- **A busca híbrida piorou o acerto nas duas granularidades** (−14 pontos com artigo, −14 com dispositivo), apesar de melhorar a ordenação quando acerta (MRR 0,740, artigo certo em 1º em 14/21). A causa provável é a consulta textual com OU sobre todas as palavras da pergunta: termos genéricos ("empresa", "funcionário", "prazo", "férias") trazem muitos artigos parecidos, e o RRF dá à lista textual o mesmo peso da vetorial, empurrando artigos certos para fora do top-5 (Q04, Q05, Q08).
- **Decisão:** novo padrão = **dispositivo + vetorial**. A híbrida fica disponível como opção, com o resultado negativo registrado. Ajustes possíveis: dar peso menor à lista textual no RRF, usar só os termos raros da pergunta, ou aumentar o k.
- **Fora do escopo:** os scores do 1º resultado continuam se misturando com os das perguntas válidas em todas as configurações. A recusa segue a cargo do LLM.

**Cuidado com o número:** 100% em 21 perguntas não significa 100% em produção. As mudanças foram desenhadas olhando os erros deste mesmo eval, então há risco de sobreajuste. O próximo passo de validação é um **conjunto separado de perguntas novas**, não usadas para decidir nada, para confirmar o ganho.

### Respostas com a busca por dispositivo (2026-11-12)

Critérios da revisão manual: **fiel** = conteúdo correto, com as condições essenciais; **parcial** = o essencial está certo, mas falta uma condição essencial ou há uma afirmação sem suporte; **infiel** = afirma algo errado.

| | Busca por artigo | **Busca por dispositivo** |
|---|---|---|
| Citação | 91,7% | **100%** |
| Fiéis | 17/21 | **18/21** |
| Parciais | — | 2 (Q10, Q15) |
| Infiéis | 3 (Q02, Q03, Q10) | **1** (Q11) |
| Recusas indevidas | 1 (Q15) | **0** |
| Recusas corretas (fora do escopo) | 3/3 | 3/3 |

- **A falha perigosa sumiu.** Banco de horas (Q10): de "Não, por causa do Art. 617" para "Sim, por acordo individual (Art. 59, § 5º)". Tolerância do ponto (Q15): de recusa para resposta com o Art. 58, § 1º.
- **Os dois erros de raciocínio da rodada anterior também foram corrigidos:** Q02 agora dá 24 dias (era 30) e Q03 não se contradiz mais. Com variação natural do modelo, não dá para atribuir isso só à busca.
- **Problema que restou nas respostas corrigidas: omitir a condição.** Q10 não diz o limite de 6 meses e Q15 não diz os limites de 5 e 10 minutos. Para o analista de RH, a condição é a regra. Próximo ajuste no prompt: "inclua prazos, limites e condições do dispositivo".
- **Regressão nova (Q11):** o Art. 71 veio em 3º lugar e o modelo afirmou que o intervalo para 8 horas "não é especificado", quando o caput diz 1 hora. Na rodada anterior, com o Art. 71 em 1º, a resposta estava certa. É um erro de leitura do modelo de 4B, sensível à posição do trecho.
- **Padrão novo: inciso errado na citação.** O modelo tende a escrever "inciso I": "Art. 130, I" quando é o II (Q02); "Art. 611-B, I" quando é o XI (Q20); "Art. 145, I" e "Art. 59, I", que nem têm inciso. O artigo está sempre certo; o inciso não. O detector de citações verifica só o artigo, então isso passa sem ser notado. Possível evolução: verificar o dispositivo citado contra o texto do trecho.
- **Latência:** 1,9 s em média; o máximo de 5,2 s foi a primeira chamada (carga do modelo).
