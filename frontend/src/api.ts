/** Contratos da API (espelham os records de RagService e BuscaController). */

export interface Fonte {
  referencia: string;
  documento: string;
  contexto: string;
  link: string;
  dispositivo: string | null;
  score: number | null;
  texto: string;
}

export interface ProblemaDeDispositivo {
  citacao: string;
  tipo: "dispositivo_inexistente" | "numero_ausente";
  detalhe: string;
}

export interface Resposta {
  pergunta: string;
  resposta: string;
  encontrou: boolean;
  fontes: Fonte[];
  citacao: {
    citados: string[];
    foraDosTrechos: string[];
    problemasDeDispositivo: ProblemaDeDispositivo[];
    respostaOriginal: string | null;
  };
  metricas: {
    modelo: string;
    latenciaBuscaMs: number;
    latenciaLlmMs: number;
    latenciaTotalMs: number;
    tokensEntrada: number | null;
    tokensSaida: number | null;
    termosGlossario: string[];
  };
  aviso: string;
}

export interface ResultadoBusca {
  referencia: string;
  documento: string;
  contexto: string;
  link: string;
  dispositivo: string | null;
  scoreVetorial: number | null;
  posicaoVetorial: number | null;
  posicaoTextual: number | null;
  trecho: string;
  texto: string;
}

async function json<T>(r: Response): Promise<T> {
  if (!r.ok) throw new Error(`A aplicação respondeu ${r.status}.`);
  return r.json() as Promise<T>;
}

export const perguntar = (pergunta: string) =>
  fetch("/api/perguntas", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ pergunta, k: 5 }),
  }).then((r) => json<Resposta>(r));

export const buscar = (q: string, k = 10) =>
  fetch(`/api/busca?k=${k}&q=${encodeURIComponent(q)}`).then((r) => json<ResultadoBusca[]>(r));

export async function saude(): Promise<boolean> {
  try {
    const r = await fetch("/actuator/health");
    if (!r.ok) return false;
    const h = (await r.json()) as { status?: string };
    return h.status === "UP";
  } catch {
    return false;
  }
}

/** "Art. 130" → "130". */
export const numeroDoArtigo = (referencia: string) => referencia.replace(/^Art\.\s*/, "");

/** Texto do artigo sem a linha de cabeçalho usada nos embeddings ("CLT, Art. 130 | …"). */
export function textoDaLei(texto: string): string {
  const linhas = (texto ?? "").split("\n");
  if (linhas.length && !/^Art/i.test(linhas[0])) linhas.shift();
  return linhas.join("\n").trim();
}

/**
 * "TÍTULO II - DAS NORMAS GERAIS DE TUTELA DO TRABALHO > CAPÍTULO II - DA DURAÇÃO DO TRABALHO"
 * → "Título II · Das normas gerais de tutela do trabalho › Capítulo II · Da duração do trabalho".
 * Só reescreve trechos que vieram em maiúsculas; o resto fica como está.
 */
export function formatarContexto(contexto: string): string {
  return (contexto ?? "")
    .split(" > ")
    .map((parte) => {
      if (parte !== parte.toUpperCase()) return parte;
      const [rotulo, ...resto] = parte.split(/\s+-\s+/);
      const minusculo = resto.join(" - ").toLowerCase();
      const nome = minusculo.charAt(0).toUpperCase() + minusculo.slice(1);
      const r = rotulo.toLowerCase().replace(/^(\p{L})/u, (c) => c.toUpperCase()).replace(/\b([ivxlc]+)\b$/i, (n) => n.toUpperCase());
      return nome ? `${r} · ${nome}` : r;
    })
    .join(" › ");
}
