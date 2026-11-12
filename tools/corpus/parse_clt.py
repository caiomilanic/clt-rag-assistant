"""Protótipo do parser da CLT (HTML do Planalto) -> artigos vigentes em JSON.

Serve para validar a estrutura do HTML antes de escrever o adaptador em Java (passo 3)
e para extrair o texto exato usado no eval set.

Uso: python3 -I parse_clt.py <entrada.html> <saida.json>
"""
import json
import re
import sys

from bs4 import BeautifulSoup

ART_RE = re.compile(
    r"^Art\.\s*(\d{1,4}(?:\.\d{3})?)\s*(?:º|°|o\b)?\s*(?:-([A-Z]{1,2})\b)?\s*[\.\-–:]?\s*",
)
HEADING_RE = re.compile(r"^(T[ÍI]TULO|CAP[ÍI]TULO|SE[ÇC][ÃA]O|SUBSE[ÇC][ÃA]O)\s+[IVXLCDM\-A-Z]+", re.I)
NOTE_RE = re.compile(
    r"\((?:\s*)(Reda[çc][ãa]o|Declarad|Inclu[íi]d|Inciso|Al[íi]nea|Caput|Artigo|Nova reda|Revogad|Vide|Vig[êe]ncia|Renumerad|Acrescid|Suprimid|Par[áa]grafo|Express[ãa]o|Produ[çc][ãa]o|Regulamento|Promulga|Ret|Vetad|Execu[çc][ãa]o|Convers|Mantid|Restabelecid)[^()]*(?:\([^()]*\)[^()]*)*\)",
    re.I,
)
REVOKED_ONLY_RE = re.compile(r"^\(?\s*(revogad|vetad|suprimid)", re.I)


UNCLOSED_NOTE_RE = re.compile(r"\((?:Reda[çc]|Inclu[íi]d|Revogad|Vide|Vig[êe]ncia|Acrescid|Renumerad)[^()]*$", re.I)
STUB_RE = re.compile(
    # Sobras de dispositivos revogados/vetados: "§ 1º.", "I -;", "a);", ")", "A", ","
    r"^((§\s*\d+\s*º?|Par[áa]grafo [úu]nico|[IVXLC]+|[a-z]\))\s*[-–.:;]*\s*\.?|[\W_]{1,3}|[A-Z]|Vig[êe]ncia( encerrada)?|Produ[çc][ãa]o de efeitos?)$",
    re.I,
)


SUBHEADING_RE = re.compile(r"^(?!§|Par[áa]grafo|[IVXLC]+\s*[-–.]|[a-z]\))[A-ZÁÉÍÓÚÂÊÔÃÕÇ][^.;:()]{2,79}$")


def norm(text: str) -> str:
    text = text.replace("\xa0", " ")
    text = re.sub(r"\s+", " ", text).strip()
    # "§ 1 o" / "Art. 1 o" (sobrescrito quebrado) -> "§ 1º"
    text = re.sub(r"((?:§|Art\.)\s*\d+)\s*[oº°]\b", r"\1º", text)
    # "Lei no 8.036" / "Lei n o 8.036" -> "Lei nº 8.036" (mesma regra do parser Java)
    text = re.sub(r"\b(Lei|Decreto-[Ll]ei|Decreto|Medida Provis[óo]ria|Lei Complementar)\s+n\s*[oº°]\s*(?=\d)", r"\1 nº ", text)
    return re.sub(r"\s+([,.;:])", r"\1", text)


def main(src: str, dst: str) -> None:
    html = open(src, "rb").read().decode("cp1252")
    soup = BeautifulSoup(html, "lxml")

    # Texto revogado aparece riscado; não é lei vigente.
    for s in soup.find_all(["strike", "s", "del"]):
        s.decompose()

    articles = []
    current = None
    context = {"titulo": None, "capitulo": None, "secao": None}
    heading_buffer = None

    for p in soup.find_all(["p", "h1", "h2", "h3", "h4", "h5", "h6"]):
        raw = norm(p.get_text(" "))
        if not raw:
            continue
        notes = [norm(m.group(0)) for m in NOTE_RE.finditer(raw)]
        text = norm(UNCLOSED_NOTE_RE.sub("", NOTE_RE.sub("", raw)))
        text = norm(re.sub(r"\bVig[êe]ncia encerrada\b", "", text))
        if not text or STUB_RE.match(text):
            continue

        h = HEADING_RE.match(text)
        if h:
            kind = h.group(1).upper()
            key = "titulo" if kind.startswith("T") else "capitulo" if kind.startswith("CAP") else "secao"
            context[key] = text
            if key == "titulo":
                context["capitulo"] = context["secao"] = None
            elif key == "capitulo":
                context["secao"] = None
            heading_buffer = key
            continue
        # Linha seguinte a um cabeçalho costuma ser o nome dele ("DAS FÉRIAS ANUAIS").
        if heading_buffer and text.isupper() and len(text) < 200:
            context[heading_buffer] = f"{context[heading_buffer]} - {text}"
            heading_buffer = None
            continue
        heading_buffer = None

        m = ART_RE.match(text)
        if m:
            num = m.group(1).replace(".", "")
            suffix = m.group(2)
            art_id = f"{num}-{suffix}" if suffix else num
            body = text[m.end():].strip()
            current = {
                "artigo": art_id,
                "titulo": context["titulo"],
                "capitulo": context["capitulo"],
                "secao": context["secao"],
                "subtitulo": context.get("subtitulo"),
                "texto": [text],
                "notas": notes,
                "revogado": bool(REVOKED_ONLY_RE.match(body)) or body == "",
            }
            # O HTML começa com os arts. 1º e 2º do decreto-lei que aprova a CLT;
            # quando o "Art. 1º" aparece de novo, começa a CLT propriamente dita.
            if art_id == "1" and any(a["artigo"] == "1" for a in articles):
                articles.clear()
            articles.append(current)
        elif current is not None and SUBHEADING_RE.match(text):
            # Subtítulo sem número entre artigos (ex.: "Trabalho aos domingos"): vira contexto.
            context["subtitulo"] = text
        elif current is not None:
            current["texto"].append(text)
            current["notas"].extend(notes)

    for a in articles:
        a["texto"] = "\n".join(a["texto"])

    # Versões de medidas provisórias que caducaram continuam no HTML sem riscado.
    articles = [a for a in articles if not any("encerrada" in n.lower() for n in a["notas"][:3])]

    # Duplicatas restantes: o Planalto às vezes repete o artigo (ex.: caput isolado + versão
    # completa). Fica a versão mais longa; a lista vai para revisão manual.
    by_id = {}
    dups = set()
    for a in articles:
        if a["artigo"] in by_id:
            dups.add(a["artigo"])
            if len(a["texto"]) > len(by_id[a["artigo"]]["texto"]):
                by_id[a["artigo"]] = a
        else:
            by_id[a["artigo"]] = a
    articles = list(by_id.values())
    if dups:
        print("duplicatas resolvidas (revisar):", sorted(dups, key=lambda x: (int(x.split("-")[0]), x)))

    json.dump(articles, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    vigentes = [a for a in articles if not a["revogado"]]
    print(f"artigos: {len(articles)} | vigentes: {len(vigentes)} | revogados: {len(articles) - len(vigentes)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
