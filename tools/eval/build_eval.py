"""Monta docs/eval/eval-set.json a partir dos artigos extraídos da CLT.

Os trechos justificativos são copiados do corpus (nunca digitados à mão): cada um é
localizado por um pedaço do texto e o script falha se não encontrar.

Uso: python3 -I build_eval.py <artigos.json> <corpus.html> <saida.json>
"""
import hashlib
import json
import sys

Q = [
    # ---------- Férias ----------
    dict(id="Q01", tema="ferias", tipo="direta",
         pergunta="Quantos dias de férias o trabalhador tem direito por ano?",
         artigos=["130"], trechos=[("130", "Após cada período"), ("130", "I - 30 (trinta) dias")],
         resposta="30 dias corridos após cada período aquisitivo de 12 meses, se o empregado não tiver mais de 5 faltas injustificadas; o número cai conforme as faltas (Art. 130).",
         obs="Pergunta da linha de base sem RAG: o modelo citou Art. 133 (errado). Ver docs/exemplos/antes-do-rag.json."),
    dict(id="Q02", tema="ferias", tipo="direta",
         pergunta="Um funcionário completou 12 meses de casa com 8 faltas injustificadas no período. Quantos dias de férias ele tem?",
         artigos=["130"], trechos=[("130", "II - 24 (vinte e quatro)")],
         resposta="24 dias corridos, porque teve de 6 a 14 faltas (Art. 130, II)."),
    dict(id="Q03", tema="ferias", tipo="direta",
         pergunta="Posso dividir as férias de um colaborador em três partes? Existe tamanho mínimo para cada parte?",
         artigos=["134"], trechos=[("134", "§ 1º Desde que haja concordância")],
         resposta="Sim, com a concordância do empregado: até três períodos, um deles com pelo menos 14 dias corridos e os demais com pelo menos 5 dias corridos cada (Art. 134, § 1º)."),
    dict(id="Q04", tema="ferias", tipo="direta",
         pergunta="O colaborador quer vender parte das férias. Quanto ele pode vender e até quando precisa pedir?",
         artigos=["143"], trechos=[("143", "É facultado ao empregado converter"), ("143", "§ 1º - O abono de férias")],
         resposta="Até 1/3 do período de férias pode ser convertido em abono pecuniário; o pedido deve ser feito até 15 dias antes do fim do período aquisitivo (Art. 143, caput e § 1º)."),
    dict(id="Q05", tema="ferias", tipo="direta",
         pergunta="Qual o prazo para a empresa pagar as férias?",
         artigos=["145"], trechos=[("145", "O pagamento da remuneração das férias")],
         resposta="Até 2 dias antes do início do período de férias, incluindo o abono, se houver (Art. 145)."),
    dict(id="Q06", tema="ferias", tipo="multi_artigo",
         pergunta="A empresa perdeu o prazo para conceder as férias de um funcionário. O que acontece?",
         artigos=["137", "134"], trechos=[("137", "Sempre que as férias forem concedidas após o prazo"), ("134", "As férias serão concedidas por ato do empregador")],
         resposta="As férias devem ser concedidas nos 12 meses seguintes à aquisição do direito (Art. 134); se concedidas depois desse prazo, a remuneração é paga em dobro (Art. 137)."),
    dict(id="Q07", tema="ferias", tipo="direta",
         pergunta="As férias de um funcionário podem começar numa quinta-feira, se sexta-feira for feriado?",
         artigos=["134"], trechos=[("134", "§ 3º É vedado o início das férias")],
         resposta="Não. É proibido iniciar as férias nos dois dias que antecedem feriado ou dia de repouso semanal remunerado (Art. 134, § 3º)."),

    # ---------- Jornada ----------
    dict(id="Q08", tema="jornada", tipo="direta",
         pergunta="Qual o adicional mínimo da hora extra?",
         artigos=["59"], trechos=[("59", "§ 1º A remuneração da hora extra")],
         resposta="Pelo menos 50% sobre a hora normal (Art. 59, § 1º).",
         obs="O Art. 611-B, X também protege esse mínimo contra redução por convenção; citar é um bônus."),
    dict(id="Q09", tema="jornada", tipo="direta",
         pergunta="Quantas horas extras por dia um funcionário pode fazer?",
         artigos=["59"], trechos=[("59", "A duração diária do trabalho poderá ser acrescida")],
         resposta="No máximo duas horas extras por dia (Art. 59, caput)."),
    dict(id="Q10", tema="jornada", tipo="multi_artigo",
         pergunta="Posso fazer banco de horas por acordo individual com o funcionário, sem passar pelo sindicato?",
         artigos=["59"], trechos=[("59", "§ 5º O banco de horas"), ("59", "§ 2º Poderá ser dispensado")],
         resposta="Sim, por acordo individual escrito, desde que a compensação ocorra em até 6 meses (Art. 59, § 5º). Para compensar em até 1 ano, é preciso acordo ou convenção coletiva (Art. 59, § 2º).",
         camada2="Tema em que convenções coletivas costumam ter regras próprias."),
    dict(id="Q11", tema="jornada", tipo="direta",
         pergunta="Qual o intervalo mínimo de almoço para quem trabalha 8 horas por dia?",
         artigos=["71"], trechos=[("71", "Em qualquer trabalho contínuo")],
         resposta="No mínimo 1 hora e, salvo acordo escrito ou contrato coletivo, no máximo 2 horas (Art. 71, caput).",
         camada2="Ver Q12: a convenção coletiva pode reduzir esse intervalo."),
    dict(id="Q12", tema="hierarquia", tipo="hierarquia",
         pergunta="A convenção coletiva da categoria pode reduzir o intervalo de almoço para 30 minutos?",
         artigos=["611-A"], trechos=[("611-A", "têm prevalência sobre a lei"), ("611-A", "III - intervalo intrajornada")],
         resposta="Sim. A convenção ou o acordo coletivo prevalecem sobre a lei quanto ao intervalo intrajornada, respeitado o mínimo de 30 minutos para jornadas acima de 6 horas (Art. 611-A, III).",
         obs="Bônus: mencionar que a regra geral é de 1 hora (Art. 71).",
         camada2="Caso central da camada 2: mostrar CLT (Art. 71) e cláusula da CCT lado a lado."),
    dict(id="Q13", tema="jornada", tipo="direta",
         pergunta="Se o funcionário não tirou o intervalo de almoço completo, quanto a empresa precisa pagar?",
         artigos=["71"], trechos=[("71", "§ 4º A não concessão ou a concessão parcial")],
         resposta="Apenas o tempo suprimido, com acréscimo de 50% sobre a hora normal, com natureza indenizatória (Art. 71, § 4º)."),
    dict(id="Q14", tema="jornada", tipo="direta",
         pergunta="Qual o descanso mínimo entre o fim de um expediente e o início do próximo?",
         artigos=["66"], trechos=[("66", "Entre 2 (duas) jornadas")],
         resposta="11 horas consecutivas (Art. 66)."),
    dict(id="Q15", tema="jornada", tipo="direta",
         pergunta="Atrasos ou saídas de poucos minutos no ponto contam como hora extra ou geram desconto?",
         artigos=["58"], trechos=[("58", "§ 1º Não serão descontadas")],
         resposta="Não, se as variações de horário no registro de ponto não passarem de 5 minutos, observado o limite de 10 minutos diários (Art. 58, § 1º).",
         obs="Não exigir 'por marcação': essa leitura vem da jurisprudência (TST), não do texto da CLT."),

    # ---------- Rescisão e ausências ----------
    dict(id="Q16", tema="rescisao", tipo="direta",
         pergunta="Qual o prazo para pagar as verbas rescisórias?",
         artigos=["477"], trechos=[("477", "§ 6º A entrega ao empregado")],
         resposta="Até 10 dias contados do término do contrato, incluindo a entrega dos documentos da rescisão (Art. 477, § 6º)."),
    dict(id="Q17", tema="rescisao", tipo="direta",
         pergunta="Como funciona a demissão por acordo entre empresa e funcionário? O que ele recebe?",
         artigos=["484-A"], trechos=[("484-A", "O contrato de trabalho poderá ser extinto por acordo"), ("484-A", "§ 1º A extinção do contrato"), ("484-A", "§ 2º A extinção do contrato por acordo")],
         resposta="Metade do aviso prévio, se indenizado, e metade da indenização sobre o saldo do FGTS; as demais verbas integralmente; saque de até 80% do FGTS; sem direito ao seguro-desemprego (Art. 484-A)."),
    dict(id="Q18", tema="rescisao", tipo="cobertura_parcial",
         pergunta="Quantos dias de aviso prévio o funcionário tem direito?",
         artigos=["487"], trechos=[("487", "II - trinta dias")],
         resposta="A CLT fixa 30 dias para quem recebe por quinzena ou mês ou tem mais de 12 meses de empresa (Art. 487, II). A proporcionalidade por tempo de serviço está em lei própria, fora do corpus: a resposta deve dizer isso em vez de inventar a regra.",
         obs="Teste de honestidade: o aviso prévio proporcional (Lei 12.506/2011) não está na CLT. O Art. 611-B, XVI só menciona que ele existe 'nos termos da lei'."),
    dict(id="Q19", tema="ausencias", tipo="direta",
         pergunta="Um funcionário vai se casar. Quantos dias ele pode faltar sem desconto?",
         artigos=["473"], trechos=[("473", "II - até 3 (três) dias consecutivos")],
         resposta="Até 3 dias consecutivos (Art. 473, II)."),

    # ---------- Hierarquia entre normas ----------
    dict(id="Q20", tema="hierarquia", tipo="hierarquia",
         pergunta="A convenção coletiva pode reduzir o número de dias de férias?",
         artigos=["611-B"], trechos=[("611-B", "Constituem objeto ilícito"), ("611-B", "XI - número de dias de férias")],
         resposta="Não. Reduzir o número de dias de férias é objeto ilícito de convenção ou acordo coletivo (Art. 611-B, XI)."),
    dict(id="Q21", tema="hierarquia", tipo="hierarquia",
         pergunta="Se o acordo coletivo da empresa e a convenção da categoria tratam do mesmo tema de forma diferente, qual vale?",
         artigos=["620"], trechos=[("620", "As condições estabelecidas em acordo coletivo")],
         resposta="O acordo coletivo sempre prevalece sobre a convenção coletiva (Art. 620).",
         camada2="Base para a regra de hierarquia na resposta com múltiplas fontes."),

    # ---------- Fora do escopo / fora do corpus ----------
    dict(id="F01", tema="fora_do_escopo", tipo="fora_do_escopo",
         pergunta="Qual a pena para o crime de furto?",
         artigos=[], trechos=[],
         resposta="Deve recusar: direito penal está fora do escopo do assistente.",
         obs="Não pode citar nenhum artigo da CLT."),
    dict(id="F02", tema="fora_do_escopo", tipo="fora_do_escopo",
         pergunta="Qual a alíquota de depósito do FGTS sobre o salário?",
         artigos=[], trechos=[],
         resposta="Deve dizer que a resposta não está no corpus: a alíquota do FGTS é definida em lei própria, não na CLT.",
         obs="Pergunta trabalhista e tentadora para alucinar: o modelo 'sabe' a resposta, mas ela não está nas fontes."),
    dict(id="F03", tema="fora_do_escopo", tipo="fora_do_escopo",
         pergunta="Como calculo a contribuição do INSS descontada do salário?",
         artigos=[], trechos=[],
         resposta="Deve dizer que a resposta não está no corpus: contribuição previdenciária é regida pela legislação da Previdência, não pela CLT."),
]


def find_line(articles, art, needle):
    text = articles[art]["texto"]
    for line in text.split("\n"):
        if needle in line:
            return line
    raise SystemExit(f"Trecho não encontrado: Art. {art} / '{needle}'")


def main(arts_path, corpus_path, dst):
    articles = {a["artigo"]: a for a in json.load(open(arts_path, encoding="utf-8"))}
    sha = hashlib.sha256(open(corpus_path, "rb").read()).hexdigest()

    perguntas = []
    for q in Q:
        for art in q["artigos"]:
            assert art in articles, f"{q['id']}: Art. {art} não está no corpus"
        item = {
            "id": q["id"],
            "tema": q["tema"],
            "tipo": q["tipo"],
            "pergunta": q["pergunta"],
            "artigos_esperados": q["artigos"],
            "resposta_referencia": q["resposta"],
            "trechos_justificativos": [
                {"artigo": art, "texto": find_line(articles, art, needle)} for art, needle in q["trechos"]
            ],
        }
        if q.get("obs"):
            item["observacoes"] = q["obs"]
        if q.get("camada2"):
            item["camada2"] = q["camada2"]
        perguntas.append(item)

    out = {
        "versao": "0.2",
        "descricao": "Eval set do assistente de normas trabalhistas. Perguntas na linguagem do analista de RH/DP, com o artigo esperado e o trecho da CLT que justifica a resposta.",
        "corpus": {
            "fonte": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del5452.htm",
            "arquivo": "corpus/clt/DEL5452.html",
            "sha256": sha,
            "obtido_em": "2026-11-12",
            "observacao": "A verdade de referência é o texto desta versão do arquivo. Se a CLT mudar, atualize o arquivo, o hash e revise as respostas.",
        },
        "tipos": {
            "direta": "A resposta está num único dispositivo.",
            "multi_artigo": "A resposta completa combina mais de um dispositivo.",
            "hierarquia": "A resposta depende da relação entre lei, convenção e acordo coletivo.",
            "cobertura_parcial": "O corpus responde só uma parte; o sistema deve dizer o que falta em vez de inventar.",
            "fora_do_escopo": "O corpus não responde; o sistema deve dizer isso e não citar artigos.",
        },
        "metricas": {
            "acerto_recuperacao": "Fração das perguntas (exceto fora_do_escopo) em que todos os artigos_esperados aparecem entre os k trechos recuperados (k = 5). Meta: >= 90%.",
            "citacao": "Fração das respostas que citam ao menos um artigo esperado, ou, em fora_do_escopo, que declaram não ter encontrado a resposta sem citar artigos. Meta: 100%.",
            "fidelidade": "Fração das respostas cujo conteúdo é sustentado pelos trechos citados (sem regra inventada ao lado de um artigo correto). Avaliada à mão numa amostra na v0.1; candidata a avaliação automática com um modelo juiz. Meta: 100%.",
        },
        "perguntas": perguntas,
    }
    json.dump(out, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    tipos = {}
    for p in perguntas:
        tipos[p["tipo"]] = tipos.get(p["tipo"], 0) + 1
    print(f"{len(perguntas)} perguntas | {tipos}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
