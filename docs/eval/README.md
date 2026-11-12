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

Citação e fidelidade dependem das respostas do LLM e entram no passo 6.

## Resultados

| Data | Configuração | Acerto de recuperação (top-5) | MRR | Artigo certo em 1º | Detalhes |
|---|---|---|---|---|---|
| 2026-11-12 | `bge-m3`, um trecho por artigo com cabeçalho de contexto | **90,5%** (19/21) | 0,655 | 10/21 | [JSON](resultados/2026-11-12-recuperacao-k5.json) |

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
