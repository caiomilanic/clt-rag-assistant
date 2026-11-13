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
- **Problemas de dispositivo:** respostas em que um § ou inciso citado não existe no artigo recuperado, ou em que a frase traz um número que não está no dispositivo citado (`respostasComProblemaDeDispositivo`, D19). Meta: 0. É uma parte automática e parcial da fidelidade: pega "Art. 145, I" (não existe) e "24 dias (Art. 130, I)" (o 24 está no inciso II), mas não um inciso errado numa frase sem números.

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

### Verificador de dispositivos sobre as respostas gravadas (2026-11-12)

Antes de rodar o modelo de novo, o `VerificadorDeCitacoes` (D19) foi aplicado às 24 respostas da rodada anterior, com o texto dos artigos recuperados:

| Pergunta | Citação | Problema detectado | Correto? |
|---|---|---|---|
| Q02 | "24 dias corridos … de 6 a 14 faltas (Art. 130, I)" | `numero_ausente`: 24, 6 e 14 estão no inciso II | Sim |
| Q05 | "(Art. 145, I)" | `dispositivo_inexistente` | Sim |
| Q09 | "(Art. 59, I)" | `dispositivo_inexistente` | Sim |
| Q20 | "(Art. 611-B, I)" no lugar do XI | não detectado (frase sem números) | Limitação |

Nenhum falso positivo nas outras 20 respostas. Os acertos do inciso certo com número por extenso (Q12: "30 minutos" ↔ "trinta minutos"; Q13: 50% no § 4º) passaram, como deviam.

### Rodada 6b: condições no prompt + verificador (2026-11-12)

[Resultado com revisão manual](resultados/2026-11-12-respostas-6b-k5.json).

| | Rodada anterior | **6b** |
|---|---|---|
| Citação (artigo) | 100% | 100% |
| Fiéis / parciais / infiéis | 18 / 2 / 1 | **17 / 3 / 1** |
| Citações de § ou inciso erradas | 4 (Q02, Q05, Q09, Q20) | 4 (Q09, Q12, Q17, Q20) |
| Detectadas pelo verificador | 3 de 4 | 2 de 4 (Q09, Q12) |
| Recusas corretas / indevidas | 3/3 · 0 | 3/3 · 0 |
| Latência média | 1,9 s | 2,5 s (12,2 s na 1ª chamada, com carga do modelo) |

- **As mudanças no prompt não melhoraram o resultado no agregado.** Uma rodada só, com variação natural do modelo, não separa efeito de ruído; o placar ficou praticamente igual.
- **Regra 4 (condições):** ajudou na Q10, que passou a dizer o limite de 6 meses, mas não na Q15 (continua sem os 5 e 10 minutos). A Q07 ficou curta demais ("Não."), perdendo a explicação.
- **Regra 2 (conferir antes de citar § ou inciso):** o 4B não seguiu. Corrigiu Q02 e Q05, mas errou em Q12 e Q17, e trocou o erro da Q20 (de I para X; o certo é XI). Os erros só mudaram de lugar.
- **O ganho real foi o verificador:** os erros de dispositivo agora aparecem na métrica, em vez de passarem como acerto. A rodada também revelou um bug nele (falso positivo na Q10) e um ponto cego (números por extenso na resposta, Q09), ambos corrigidos; veja a D19.
- **Conclusão:** pedir ao modelo de 4B que confira o próprio inciso não funciona. A próxima alavanca está no sistema, não no prompt: quando o verificador marca um dispositivo, o sistema pode **rebaixar a citação para o artigo** (o artigo está sempre certo) ou pedir uma nova geração. A outra é testar um modelo maior (`qwen3:8b`).

### Rebaixamento da citação marcada (D20)

A partir daqui, a resposta entregue já sai com as citações marcadas pelo verificador trocadas pelo artigo; `respostaOriginal` guarda o texto do modelo. Na revisão manual, avalie a resposta entregue e use `problemasDeDispositivo` e `respostaOriginal` para saber o que o sistema corrigiu. Aplicado às rodadas gravadas: 5 citações rebaixadas, todas erradas, nenhuma certa alterada.

## Conjunto de validação (D21)

[`validacao-set.json`](validacao-set.json): 26 perguntas novas (19 diretas, 1 multi-artigo, 2 de hierarquia, 1 de cobertura parcial, 3 fora do escopo), de temas que o conjunto de desenvolvimento não cobre. Congelado em 2026-11-13, antes da primeira execução.

**Regra:** nada no sistema é ajustado olhando para essas perguntas. Se uma falha da validação motivar uma mudança, a pergunta passa para o conjunto de desenvolvimento e outra nova entra no lugar.

```bash
curl -s -X POST "http://localhost:8080/api/avaliacao/recuperacao?k=5&conjunto=validacao" > target/validacao-recuperacao.json
curl -s -X POST "http://localhost:8080/api/avaliacao/respostas?k=5&conjunto=validacao" > target/validacao-respostas.json
```

Para regenerar o arquivo (só se o corpus mudar):

```bash
python3 -I tools/eval/build_eval.py <artigos.json> corpus/clt/DEL5452.html docs/eval/validacao-set.json validacao
```

### Primeira execução da validação (2026-11-13)

[Resultado com revisão manual](resultados/2026-11-13-validacao-k5.json). Mesma configuração da rodada 6b (dispositivo + vetorial, `qwen3:4b-instruct`).

| | Desenvolvimento (6b) | **Validação** |
|---|---|---|
| Recuperação top-5 | 100% (MRR 0,716) | **78,3%** (18/23, MRR 0,71) |
| Fiéis | 17/21 (81%) | **11/23 (48%)** |
| Parciais | 3 | 5 |
| Infiéis | 1 | 3 |
| Recusas indevidas | 0 | 4 |
| Fora do escopo recusadas | 3/3 | 3/3 |
| Latência média / máx. | 2,5 s / 12,2 s | 2,6 s / 5,8 s |

**1. A busca não entende jargão de RH (5 falhas, todas totais).** O MRR igual mostra que, quando acha, a busca acerta a posição; quando erra, o artigo some do top-5. Quatro das cinco falhas são vocabulário: "12x36" (V03, V07) não chega a "doze horas seguidas por trinta e seis" (Art. 59-A); "home office"/"escritório" (V13) não chega a "teletrabalho"/"presencial" (Art. 75-C); "gerente com diploma e salário alto" (V21) não chega ao Art. 444; "das 22h às 5h" (V01) não chega ao Art. 73. As perguntas de desenvolvimento foram escritas com a lei aberta e já usavam o vocabulário dela; daí os 100%.

**2. Quando a busca falha, o modelo em geral recusa (3 de 5).** V01, V03 e V07 terminaram em "Não encontrei…", a falha segura. V13 e V21 responderam mesmo assim: a V21 inventou relações entre artigos ("pelo Art. 622, a convenção tem prevalência sobre a lei").

**3. Com a busca certa, o 4B ainda erra a escolha do trecho.** V08: o Art. 29 veio em 2º, mas o modelo usou o Art. 311 (registro de jornalistas) e respondeu "60 dias" no lugar de 5 dias úteis. V14: o Art. 75-D veio em 1º e o modelo recusou. V22: conclusão certa, mas atribuída ao 611-A no lugar do 611-B.

**4. A armadilha da cobertura parcial pegou (V23).** O modelo afirmou que a estabilidade da gestante dura "o período da licença" e que "não há limite de tempo", quando o Art. 391-A remete o prazo ao ADCT. As duas armadilhas de fora do escopo (13º e seguro-desemprego, citados no 611-B) foram recusadas corretamente.

**5. O verificador ajudou pouco aqui.** Marcou só a V22, e o rebaixamento manteve o artigo errado ("Art. 611-A"): ele corrige o dispositivo, não o artigo. A rodada também revelou dois bugs nos detectores, corrigidos sem mudar o comportamento da busca nem do prompt: "art. 10 do Ato das Disposições Constitucionais Transitórias" contava como artigo da CLT fora dos trechos (V23), e "cento e vinte" gerava o número 20 (V22).

**Conclusão:** os números do conjunto de desenvolvimento estavam otimistas, como a D21 temia. O maior gargalo é a distância entre o vocabulário do analista e o da lei; o segundo, a leitura do modelo de 4B. Próximos passos candidatos: expansão da pergunta com glossário RH → lei, e comparação com um modelo maior. Pela regra da D21, o que for ajustado por causa dessas falhas leva as perguntas correspondentes para o desenvolvimento, e entram perguntas novas na validação.

## Conjuntos depois da D22 (2026-11-13)

- **Desenvolvimento 0.3** (29 perguntas): as 24 originais + Q22 a Q26, que vieram da validação (V01, V03, V07, V13, V21) porque o glossário foi desenhado olhando para elas.
- **Validação 0.2** (26 perguntas): as 21 que ficaram + V24 a V28, novas, escritas e congeladas antes do glossário, em linguagem de RH que ele não cobre.

Medir com e sem o glossário:

```bash
for c in desenvolvimento validacao; do
  for e in true false; do
    curl -s -X POST "http://localhost:8080/api/avaliacao/recuperacao?k=5&conjunto=$c&expansao=$e" > target/glossario-recuperacao-$c-$e.json
  done
done
curl -s -X POST "http://localhost:8080/api/avaliacao/respostas?k=5&conjunto=desenvolvimento" > target/glossario-respostas-desenvolvimento.json
curl -s -X POST "http://localhost:8080/api/avaliacao/respostas?k=5&conjunto=validacao" > target/glossario-respostas-validacao.json
```

### Resultado do glossário (2026-11-13)

Resultados com revisão manual: [desenvolvimento](resultados/2026-11-13-glossario-desenvolvimento-k5.json) e [validação](resultados/2026-11-13-glossario-validacao-k5.json).

**Busca, com e sem glossário:**

| | Sem glossário | **Com glossário** |
|---|---|---|
| Desenvolvimento 0.3 (26 perguntas) | 80,8% (MRR 0,597) | **92,3%** (MRR 0,840) |
| Validação 0.2 (23 perguntas) | 87,0% (MRR 0,775) | 87,0% (MRR 0,775) |

- **Desenvolvimento:** das cinco perguntas de jargão, quatro passaram a achar o artigo em 1º lugar (Q22 "22h", Q23 "12x36", Q25 "home office", Q26 "diploma"). Outras subiram de posição: Q04 (5º → 2º), Q16 e Q17 (3º → 1º), Q14 (2º → 1º).
- **Efeito colateral (Q12):** "A convenção coletiva pode reduzir o intervalo de almoço para 30 minutos?" A expansão de "almoço" puxou a busca para o Art. 71, e o Art. 611-A (o que responde) saiu do top-5. Acrescentar a expansão à consulta **dilui o conceito principal da pergunta** quando o termo do glossário é secundário.
- **Q24 continua falhando:** com quatro termos expandidos ao mesmo tempo, a consulta ficou dominada pelo trabalho noturno e o Art. 59-A (12x36) não entrou. É a mesma diluição.
- **Validação: nenhum efeito**, como previsto na D22, porque nenhum termo do glossário aparece nela. Das cinco perguntas novas com jargão, três falharam na busca: "pediu as contas" (V24), "ônibus fretado" (V27) e "rebaixar" (V28). **O glossário corrige o que cobre; a cobertura é o limite.**

**Respostas (com glossário):**

| | Desenvolvimento 0.3 | Validação 0.2 |
|---|---|---|
| Fiéis | **21/26 (81%)** | 13/23 (57%) |
| Parciais | 1 | 4 |
| Infiéis | 3 | 3 |
| Recusas indevidas | 1 | 3 |
| Fora do escopo recusadas | 3/3 | 3/3 |
| Latência média | 2,2 s | 2,4 s |

- **Busca certa não garante resposta certa (Q25):** o glossário trouxe o Art. 75-C em 1º, mas o modelo inverteu os parágrafos e disse que a volta ao presencial exige acordo mútuo. O § 2º diz o contrário.
- **Melhoras no desenvolvimento:** Q10, Q11 e Q15 ficaram fiéis pela primeira vez (Q11 era infiel desde a rodada 6a). Com uma execução só, não dá para separar o efeito do glossário (o Art. 71 subiu de posição na Q11) da variação do modelo.
- **Variação do modelo:** a Q02, sem nenhum termo do glossário, voltou a errar (30 dias no lugar de 24).
- **Quando a busca erra, o modelo recusa na maioria das vezes:** das cinco falhas de busca desta rodada, três terminaram em recusa (Q24, V24, V27). As outras duas viraram respostas erradas montadas com artigos vizinhos: Q12 e V28.
- **Verificador:** cinco citações rebaixadas nesta rodada, todas erradas (Q04, Q09, Q22, V19, V22).

**Conclusões:**
1. O glossário funciona no que cobre (+11,5 pontos na busca do desenvolvimento) e não piora a validação.
2. A forma de aplicar precisa mudar: em vez de acrescentar a expansão à pergunta (que dilui o conceito principal, Q12 e Q24), buscar pela pergunta original **e** pela expansão e fundir as duas listas. Assim a expansão acrescenta candidatos sem tirar os da pergunta.
3. A cobertura é o limite: jargão novo ("pediu as contas", "rebaixar") passa direto. Em produção, o glossário cresce com o uso de cada empresa; a alternativa automática é a reescrita da pergunta pelo LLM.
4. O gargalo seguinte é a leitura do modelo de 4B (Q25, V08, V14, V22, V23).
