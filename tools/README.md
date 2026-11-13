# Ferramentas de apoio (Python)

Scripts usados para preparar o corpus e o eval set. Não fazem parte da aplicação.

> **O parser de referência agora é o Java** (`src/main/java/.../ingestao/clt/CltHtmlParser.java`, desde o passo 3). Ele extrai 835 artigos, contra 826 do protótipo, e corrige casos que o protótipo perde (ver D16 em `docs/decisoes.md`). O protótipo continua aqui porque gera os trechos do eval set. No passo 3 foi conferido que os 33 trechos do eval aparecem literalmente na saída do parser Java; se o eval for regenerado, vale repetir essa conferência.

Requisitos: Python 3.10+ com `beautifulsoup4` e `lxml` (`pip install beautifulsoup4 lxml`).

## `corpus/parse_clt.py`: protótipo do parser da CLT

Converte o HTML do Planalto (`corpus/clt/DEL5452.html`) em uma lista de artigos vigentes, em JSON.

```bash
python tools/corpus/parse_clt.py corpus/clt/DEL5452.html target/clt-artigos.json
```

Serve para duas coisas: validar as regras de extração antes de escrevê-las em Java e gerar o texto exato usado no eval set.

**Regras de extração** (o adaptador Java deve seguir as mesmas):

| Situação no HTML | Tratamento |
|---|---|
| Arquivo em `windows-1252` | Decodificar como cp1252 antes de qualquer coisa |
| Texto revogado aparece riscado (`<strike>`) | Descartar: não é lei vigente |
| Notas de alteração, como "(Redação dada pela Lei nº…)", "(Incluído…)", "(Vigência)" | Remover do texto; guardar como metadado `notas` |
| Versões de medidas provisórias que perderam a validade ("Vigência encerrada"), que **não** aparecem riscadas | Descartar a versão inteira |
| Arts. 1º e 2º no início do arquivo | São do decreto-lei que aprova a CLT, não da CLT: ignorar |
| `Art. 59-A` (sufixo colado) × `Art. 58 - A duração…` (hífen + palavra) | Só é sufixo se não houver espaço depois do hífen |
| Sobrescrito quebrado: "§ 1 o", "Art. 1 o" | Normalizar para "§ 1º" |
| Sobras de dispositivos revogados: "§ 1º.", "I -;", "a);" | Descartar a linha |
| Títulos, capítulos e seções; subtítulos sem número ("Trabalho aos domingos") | Guardar como metadados de contexto, não como texto do artigo |

**Resultado com o arquivo de 2026-11-12:** 826 artigos vigentes. Tamanho do artigo: mediana de ~380 caracteres, máximo de ~5.000. Cabe com folga num chunk por artigo.

**Problemas conhecidos** (a revisar no adaptador Java):
- 11 artigos aparecem mais de uma vez no HTML sem estarem riscados (ex.: 47, 59-A, 611-A). Hoje fica a versão mais longa; o script lista quais são.
- Alguns dispositivos declarados inconstitucionais pelo STF continuam no texto, só com uma nota. A nota fica em `notas`, mas o texto não é removido.
- Os metadados de título/capítulo falham em parte do Título VI (ex.: art. 611-A sem capítulo).
- Restam poucas linhas residuais isoladas (ex.: "Art. 275", "g) creches;" repetido).

## `eval/build_eval.py`: geração do eval set

Monta `docs/eval/eval-set.json`. As perguntas e respostas de referência ficam no próprio script; **os trechos justificativos são copiados do corpus**, nunca digitados à mão. Se um trecho não for encontrado, o script falha.

```bash
python tools/corpus/parse_clt.py corpus/clt/DEL5452.html target/clt-artigos.json
python tools/eval/build_eval.py target/clt-artigos.json corpus/clt/DEL5452.html docs/eval/eval-set.json
python tools/eval/build_eval.py target/clt-artigos.json corpus/clt/DEL5452.html docs/eval/validacao-set.json validacao
```

O último argumento escolhe o conjunto: `desenvolvimento` (padrão, lista `Q`) ou `validacao` (lista `V`, D21). A lista `V` está congelada: não a edite para fazer o sistema passar.

O SHA-256 do HTML fica registrado no eval set, para saber contra qual versão da lei ele foi montado.
