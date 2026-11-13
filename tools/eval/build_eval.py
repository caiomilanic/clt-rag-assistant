"""Monta docs/eval/eval-set.json a partir dos artigos extraídos da CLT.

Os trechos justificativos são copiados do corpus (nunca digitados à mão): cada um é
localizado por um pedaço do texto e o script falha se não encontrar.

Uso: python3 -I build_eval.py <artigos.json> <corpus.html> <saida.json> [desenvolvimento|validacao]
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

    # ---------- Vindas da validação 0.1 (D21, D22) ----------
    dict(id="Q22", tema="jornada", tipo="direta",
         pergunta="Um funcionário trabalha das 22h às 5h. Quanto ele recebe a mais e como as horas dele são contadas?",
         artigos=["73"], trechos=[("73", "acréscimo de 20 %"), ("73", "§ 1º A hora do trabalho noturno"), ("73", "§ 2º Considera-se noturno")],
         resposta="Trabalho entre 22h e 5h é noturno; tem acréscimo de pelo menos 20% sobre a hora diurna, e cada hora noturna conta como 52 minutos e 30 segundos (Art. 73, caput e §§ 1º e 2º).",
         obs="Veio da validação (V01) em 2026-11-13: a busca falhou por jargão de RH, e a correção (glossário, D22) foi desenhada olhando para ela (regra da D21)."),
    dict(id="Q23", tema="jornada", tipo="direta",
         pergunta="Posso adotar a escala 12x36 combinando direto com o funcionário, sem o sindicato?",
         artigos=["59-A"], trechos=[("59-A", "mediante acordo individual escrito")],
         resposta="Sim. A escala de 12 horas de trabalho por 36 de descanso pode ser feita por acordo individual escrito, convenção ou acordo coletivo, observados ou indenizados os intervalos (Art. 59-A).",
         obs="Veio da validação (V03) em 2026-11-13: a busca falhou por jargão de RH, e a correção (glossário, D22) foi desenhada olhando para ela (regra da D21)."),
    dict(id="Q24", tema="jornada", tipo="multi_artigo",
         pergunta="Funcionário em escala 12x36 no turno da noite: ele tem adicional noturno e hora reduzida?",
         artigos=["59-A", "73"], trechos=[("59-A", "Parágrafo único. A remuneração mensal pactuada"), ("73", "§ 1º A hora do trabalho noturno"), ("73", "acréscimo de 20 %")],
         resposta="Sim, o trabalho entre 22h e 5h segue o Art. 73 (acréscimo de pelo menos 20% e hora de 52 minutos e 30 segundos). Na 12x36, a remuneração mensal já abrange o descanso semanal e os feriados, e as prorrogações do trabalho noturno são consideradas compensadas (Art. 59-A, parágrafo único).",
         obs="Pergunta difícil de propósito: exige combinar a regra geral do trabalho noturno com a exceção da 12x36. Veio da validação (V07) em 2026-11-13: a busca falhou por jargão de RH, e a correção (glossário, D22) foi desenhada olhando para ela (regra da D21)."),
    dict(id="Q25", tema="teletrabalho", tipo="direta",
         pergunta="Quero trazer de volta ao escritório um funcionário que está em home office. Preciso da concordância dele?",
         artigos=["75-C"], trechos=[("75-C", "§ 2º Poderá ser realizada a alteração do regime de teletrabalho para o presencial")],
         resposta="Não. A volta ao presencial pode ser determinada pelo empregador, com prazo de transição de pelo menos 15 dias e registro em aditivo contratual (Art. 75-C, § 2º).",
         obs="Veio da validação (V13) em 2026-11-13: a busca falhou por jargão de RH, e a correção (glossário, D22) foi desenhada olhando para ela (regra da D21)."),
    dict(id="Q26", tema="hierarquia", tipo="hierarquia",
         pergunta="Um gerente com diploma de nível superior e salário alto pode negociar direto com a empresa condições diferentes das da convenção coletiva?",
         artigos=["444"], trechos=[("444", "Parágrafo único. A livre estipulação")],
         resposta="Sim, nos temas do Art. 611-A: o empregado com diploma de nível superior e salário mensal igual ou superior a duas vezes o teto dos benefícios do RGPS pode negociar individualmente, com a mesma eficácia e preponderância sobre os instrumentos coletivos (Art. 444, parágrafo único).",
         obs="Citar também o Art. 611-A é correto e esperado como bônus. Veio da validação (V21) em 2026-11-13: a busca falhou por jargão de RH, e a correção (glossário, D22) foi desenhada olhando para ela (regra da D21)."),
]

# Conjunto de VALIDAÇÃO (D21): perguntas novas, de temas que o conjunto acima não cobre.
# Congelado em 2026-11-13, antes da primeira execução. Não ajuste prompt, busca ou verificador
# olhando para estas perguntas: elas medem se o que foi ajustado no conjunto acima generaliza.
V = [
    # ---------- Jornada e adicionais ----------
        dict(id="V02", tema="jornada", tipo="direta",
         pergunta="Qual o limite de horas por semana no regime de meio período? Esse funcionário pode fazer hora extra?",
         artigos=["58-A"], trechos=[("58-A", "Considera-se trabalho em regime de tempo parcial"), ("58-A", "§ 3º As horas suplementares")],
         resposta="Até 30 horas semanais sem hora extra, ou até 26 horas semanais com até 6 horas extras por semana, pagas com acréscimo de 50% (Art. 58-A, caput e § 3º)."),
        dict(id="V04", tema="jornada", tipo="direta",
         pergunta="O descanso semanal do funcionário precisa cair no domingo?",
         artigos=["67"], trechos=[("67", "descanso semanal de 24 (vinte e quatro) horas"), ("67", "escala de revezamento")],
         resposta="O descanso semanal é de 24 horas consecutivas e deve coincidir com o domingo, no todo ou em parte, salvo conveniência pública ou necessidade imperiosa do serviço; onde há trabalho aos domingos, faz-se escala de revezamento (Art. 67)."),
    dict(id="V05", tema="adicionais", tipo="direta",
         pergunta="Quais os percentuais do adicional de insalubridade e sobre qual valor eles são calculados?",
         artigos=["192"], trechos=[("192", "40% (quarenta por cento)")],
         resposta="40%, 20% ou 10% do salário mínimo da região, conforme o grau seja máximo, médio ou mínimo (Art. 192)."),
    dict(id="V06", tema="adicionais", tipo="direta",
         pergunta="Um vigilante de segurança patrimonial tem direito a adicional de periculosidade? De quanto?",
         artigos=["193"], trechos=[("193", "II - roubos ou outras espécies"), ("193", "§ 1º - O trabalho em condições de periculosidade")],
         resposta="Sim. A atividade de segurança patrimonial com exposição a roubos ou violência é perigosa, e o adicional é de 30% sobre o salário, sem gratificações, prêmios ou participação nos lucros (Art. 193, II e § 1º)."),
    # ---------- Contrato e salário ----------
    dict(id="V08", tema="contrato", tipo="direta",
         pergunta="Contratei um funcionário hoje. Qual o prazo para registrar a carteira de trabalho dele?",
         artigos=["29"], trechos=[("29", "prazo de 5 (cinco) dias úteis")],
         resposta="5 dias úteis para anotar a data de admissão, a remuneração e as condições especiais na CTPS (Art. 29)."),
    dict(id="V09", tema="salario", tipo="direta",
         pergunta="Até que dia do mês a empresa precisa pagar o salário mensal?",
         artigos=["459"], trechos=[("459", "até o quinto dia útil")],
         resposta="Até o quinto dia útil do mês seguinte ao trabalhado (Art. 459, § 1º)."),
    dict(id="V10", tema="salario", tipo="direta",
         pergunta="Um funcionário quebrou um equipamento da empresa sem querer. Posso descontar do salário dele?",
         artigos=["462"], trechos=[("462", "§ 1º - Em caso de dano causado pelo empregado")],
         resposta="Só se essa possibilidade tiver sido acordada com ele; sem acordo, o desconto só é lícito em caso de dolo, o que não é o caso de um dano sem querer (Art. 462, § 1º)."),
    dict(id="V11", tema="contrato", tipo="direta",
         pergunta="Preciso transferir um funcionário para outra cidade. Posso fazer isso sem ele concordar? Tenho que pagar algo a mais?",
         artigos=["469"], trechos=[("469", "Ao empregador é vedado transferir"), ("469", "§ 3º - Em caso de necessidade de serviço")],
         resposta="Em regra, não sem a anuência dele (exceto cargo de confiança, contrato que preveja transferência por real necessidade, ou extinção do estabelecimento). Por necessidade de serviço, a transferência é possível, mas com pagamento suplementar de pelo menos 25% do salário enquanto durar (Art. 469, caput, §§ 1º a 3º)."),
    dict(id="V12", tema="contrato", tipo="direta",
         pergunta="No contrato intermitente, com quanta antecedência preciso chamar o funcionário e quanto tempo ele tem para responder?",
         artigos=["452-A"], trechos=[("452-A", "três dias corridos de antecedência"), ("452-A", "prazo de um dia útil para responder")],
         resposta="A convocação deve ser feita com pelo menos 3 dias corridos de antecedência; o empregado tem 1 dia útil para responder, e o silêncio é considerado recusa (Art. 452-A, §§ 1º e 2º)."),

    # ---------- Teletrabalho ----------
        dict(id="V14", tema="teletrabalho", tipo="direta",
         pergunta="A empresa é obrigada a pagar a internet e o computador de quem trabalha de casa?",
         artigos=["75-D"], trechos=[("75-D", "serão previstas em contrato escrito"), ("75-D", "não integram a remuneração")],
         resposta="A CLT não fixa quem paga: a responsabilidade pelos equipamentos, pela infraestrutura e pelo reembolso de despesas deve estar prevista em contrato escrito; esses valores não integram a remuneração (Art. 75-D)."),

    # ---------- Gestante ----------
    dict(id="V15", tema="gestante", tipo="direta",
         pergunta="Uma funcionária descobriu que está grávida durante o aviso prévio. Ela tem estabilidade?",
         artigos=["391-A"], trechos=[("391-A", "ainda que durante o prazo do aviso prévio")],
         resposta="Sim. A confirmação da gravidez no curso do contrato, ainda que durante o aviso prévio trabalhado ou indenizado, garante a estabilidade provisória da gestante (Art. 391-A)."),
    dict(id="V16", tema="gestante", tipo="direta",
         pergunta="Quantos dias de licença-maternidade a funcionária tem, e a partir de quando ela pode começar?",
         artigos=["392"], trechos=[("392", "licença-maternidade de 120 (cento e vinte) dias"), ("392", "28º (vigésimo oitavo) dia antes do parto")],
         resposta="120 dias, sem prejuízo do emprego e do salário; o afastamento pode começar entre o 28º dia antes do parto e o parto, mediante atestado médico (Art. 392, caput e § 1º)."),
    dict(id="V17", tema="gestante", tipo="direta",
         pergunta="A funcionária voltou da licença e está amamentando. Ela tem direito a pausas durante o expediente?",
         artigos=["396"], trechos=[("396", "2 (dois) descansos especiais de meia hora"), ("396", "§ 2º Os horários dos descansos")],
         resposta="Sim, até o bebê completar 6 meses: dois descansos de meia hora cada durante a jornada, em horários definidos em acordo individual com o empregador (Art. 396)."),

    # ---------- Férias e rescisão ----------
    dict(id="V18", tema="ferias", tipo="direta",
         pergunta="Com quanto tempo de antecedência preciso avisar o funcionário das férias dele?",
         artigos=["135"], trechos=[("135", "com antecedência de, no mínimo, 30 (trinta) dias")],
         resposta="Pelo menos 30 dias antes, por escrito, com recibo do empregado (Art. 135)."),
    dict(id="V19", tema="rescisao", tipo="direta",
         pergunta="Em quais situações a empresa pode demitir por justa causa?",
         artigos=["482"], trechos=[("482", "Constituem justa causa"), ("482", "e) desídia"), ("482", "i) abandono de emprego")],
         resposta="Nas hipóteses do Art. 482, entre elas: improbidade, mau procedimento, desídia, embriaguez habitual ou em serviço, violação de segredo da empresa, indisciplina ou insubordinação, abandono de emprego e perda da habilitação profissional por conduta dolosa (Art. 482)."),
    dict(id="V20", tema="rescisao", tipo="direta",
         pergunta="O funcionário foi demitido e está cumprindo aviso prévio. A jornada dele diminui?",
         artigos=["488"], trechos=[("488", "será reduzido de 2 (duas) horas diárias"), ("488", "7 (sete) dias corridos")],
         resposta="Sim, quando a demissão foi pela empresa: a jornada é reduzida em 2 horas por dia sem prejuízo do salário; ou o empregado pode trabalhar sem a redução e faltar 7 dias corridos (no aviso de 30 dias) (Art. 488)."),

    # ---------- Hierarquia ----------
        dict(id="V22", tema="hierarquia", tipo="hierarquia",
         pergunta="A convenção coletiva pode reduzir a licença-maternidade para 90 dias?",
         artigos=["611-B"], trechos=[("611-B", "XIII - licença-maternidade")],
         resposta="Não. Reduzir a licença-maternidade abaixo de 120 dias é objeto ilícito de convenção ou acordo coletivo (Art. 611-B, XIII)."),

    # ---------- Cobertura parcial ----------
    dict(id="V23", tema="gestante", tipo="cobertura_parcial",
         pergunta="Por quanto tempo dura a estabilidade da funcionária gestante?",
         artigos=["391-A"], trechos=[("391-A", "Ato das Disposições Constitucionais Transitórias")],
         resposta="A CLT garante a estabilidade (Art. 391-A), mas remete a duração ao art. 10, II, b, do ADCT da Constituição, que não está nos trechos; o sistema deve dizer isso em vez de informar um prazo.",
         obs="Armadilha: o prazo (da confirmação da gravidez até 5 meses após o parto) é conhecido, mas está na Constituição, não na CLT."),

    # ---------- Validação 0.2 (2026-11-13): substitutas das que foram para o desenvolvimento ----------
    # Escritas e congeladas antes do glossário (D22), em linguagem de RH.
    dict(id="V24", tema="rescisao", tipo="direta",
         pergunta="Um funcionário pediu as contas e não quer cumprir o aviso. O que a empresa pode fazer?",
         artigos=["487"], trechos=[("487", "§ 2º - A falta de aviso prévio por parte do empregado")],
         resposta="Se o empregado não der o aviso prévio, a empresa pode descontar os salários correspondentes ao prazo do aviso (Art. 487, § 2º)."),
    dict(id="V25", tema="contrato", tipo="direta",
         pergunta="Qual o prazo máximo do contrato de experiência?",
         artigos=["445"], trechos=[("445", "O contrato de experiência não poderá exceder")],
         resposta="90 dias (Art. 445, parágrafo único)."),
    dict(id="V26", tema="ferias", tipo="direta",
         pergunta="Quero dar férias coletivas para a empresa toda no fim do ano. Preciso avisar alguém?",
         artigos=["139"], trechos=[("139", "§ 2º - Para os fins previstos neste artigo"), ("139", "§ 3º - Em igual prazo")],
         resposta="Sim: comunicar o órgão local do Ministério do Trabalho com pelo menos 15 dias de antecedência, com as datas de início e fim e os setores abrangidos; no mesmo prazo, enviar cópia aos sindicatos da categoria e afixar aviso nos locais de trabalho (Art. 139, §§ 2º e 3º). As férias coletivas podem ser divididas em até 2 períodos de pelo menos 10 dias corridos (§ 1º)."),
    dict(id="V27", tema="jornada", tipo="direta",
         pergunta="O tempo que o funcionário passa no ônibus fretado pela empresa conta como hora trabalhada?",
         artigos=["58"], trechos=[("58", "§ 2º O tempo despendido pelo empregado")],
         resposta="Não. O tempo de deslocamento de casa até o posto de trabalho e a volta, por qualquer meio de transporte, inclusive o fornecido pelo empregador, não é computado na jornada (Art. 58, § 2º)."),
    dict(id="V28", tema="contrato", tipo="direta",
         pergunta="Posso rebaixar um funcionário que está num cargo de confiança e cortar a gratificação dele?",
         artigos=["468"], trechos=[("468", "§ 1º Não se considera alteração unilateral"), ("468", "§ 2º A alteração de que trata o § 1º")],
         resposta="Sim. A reversão ao cargo efetivo, deixando a função de confiança, não é considerada alteração unilateral proibida, e o empregado não tem direito a manter a gratificação, que não se incorpora ao salário, independentemente do tempo na função (Art. 468, §§ 1º e 2º)."),

    # ---------- Fora do escopo ----------
    dict(id="VF01", tema="fora_do_escopo", tipo="fora_do_escopo",
         pergunta="Como calculo o 13º salário proporcional de quem foi demitido?",
         artigos=[], trechos=[],
         resposta="Deve dizer que a resposta não está no corpus: o 13º salário é regido pela Lei nº 4.090/1962, não pela CLT.",
         obs="Armadilha: o Art. 611-B, V menciona o 13º salário, mas não diz como calculá-lo."),
    dict(id="VF02", tema="fora_do_escopo", tipo="fora_do_escopo",
         pergunta="Quantas parcelas de seguro-desemprego o funcionário demitido sem justa causa vai receber?",
         artigos=[], trechos=[],
         resposta="Deve dizer que a resposta não está no corpus: o seguro-desemprego é regido pela Lei nº 7.998/1990.",
         obs="Armadilha: o Art. 611-B, II e o Art. 484-A mencionam o seguro-desemprego, sem as parcelas."),
    dict(id="VF03", tema="fora_do_escopo", tipo="fora_do_escopo",
         pergunta="Qual o desconto máximo do vale-transporte no salário do funcionário?",
         artigos=[], trechos=[],
         resposta="Deve dizer que a resposta não está no corpus: o vale-transporte é regido pela Lei nº 7.418/1985 (desconto de até 6%)."),
]


def find_line(articles, art, needle):
    text = articles[art]["texto"]
    for line in text.split("\n"):
        if needle in line:
            return line
    raise SystemExit(f"Trecho não encontrado: Art. {art} / '{needle}'")


def main(arts_path, corpus_path, dst, conjunto="desenvolvimento"):
    articles = {a["artigo"]: a for a in json.load(open(arts_path, encoding="utf-8"))}
    sha = hashlib.sha256(open(corpus_path, "rb").read()).hexdigest()

    perguntas = []
    for q in (V if conjunto == "validacao" else Q):
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
        "versao": "validacao-0.2" if conjunto == "validacao" else "0.3",
        "descricao": ("Conjunto de VALIDAÇÃO do assistente de normas trabalhistas (D21): perguntas novas, congeladas "
                      "em 2026-11-13 antes da primeira execução e nunca usadas para ajustar o sistema."
                      if conjunto == "validacao" else
                      "Eval set do assistente de normas trabalhistas. Perguntas na linguagem do analista de RH/DP, com o artigo esperado e o trecho da CLT que justifica a resposta."),
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
    main(*sys.argv[1:5])
