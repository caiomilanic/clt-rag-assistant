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
