import { Fragment, useRef, useState, type ReactNode } from "react";
import { TriangleAlert, Check, CircleCheck, Copy, Languages, SearchX, ShieldCheck, Sparkles } from "lucide-react";
import { numeroDoArtigo, type Resposta } from "../api";
import type { Mensagem } from "../conversas";
import SourceCard from "./SourceCard";

/**
 * "Art. 130, II", "Art. 59-A", "art. 73, § 1º", "Art. 487, I e II", "§ 6º do Art. 477": a citação inteira vira um
 * botão quando o artigo está entre os trechos consultados (o resto, como "art. 18 da Lei nº 8.036", fica como texto).
 */
const CITACAO =
  /(?:§\s*\d+\s*º?\s+d[oa]\s+)?\bArt(?:igo)?\.?\s*(\d{1,4})(?:\s*[º°])?(?:-([A-Z]))?(?:\s*,\s*(?:§\s*\d+\s*º?|par[áa]grafo [úu]nico|[IVXLC]+\b)(?:\s+e\s+[IVXLC]+\b)?)*/gi;

export default function AssistantMessage({ mensagem }: { mensagem: Mensagem }) {
  const r = mensagem.resposta;
  const [aberta, setAberta] = useState<string | null>(null);
  const [copiado, setCopiado] = useState(false);
  const cartoes = useRef<Record<string, HTMLDivElement | null>>({});

  if (mensagem.erro || !r) {
    return (
      <Moldura>
        <div className="flex items-start gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 ring-1 ring-red-200">
          <TriangleAlert size={16} className="mt-0.5 shrink-0" />
          Não foi possível obter a resposta. {mensagem.erro}
        </div>
      </Moldura>
    );
  }

  const artigos = new Set(r.fontes.map((f) => numeroDoArtigo(f.referencia)));
  const citados = new Set(r.citacao.citados);
  const fontes = [...r.fontes].sort((a, b) => Number(citados.has(numeroDoArtigo(b.referencia))) - Number(citados.has(numeroDoArtigo(a.referencia))));

  const abrir = (art: string) => {
    setAberta(art);
    requestAnimationFrame(() => cartoes.current[art]?.scrollIntoView({ behavior: "smooth", block: "nearest" }));
  };

  return (
    <Moldura>
      {r.encontrou ? (
        <Corpo texto={r.resposta} artigos={artigos} onCitacao={abrir} />
      ) : (
        <div className="flex items-start gap-2.5 rounded-lg bg-canvas px-4 py-3 text-[15px] text-slate-700">
          <SearchX size={18} className="mt-0.5 shrink-0 text-slate-400" />
          <div>
            {r.resposta}
            <p className="mt-1 text-sm text-slate-500">O assistente só responde com base nos trechos da CLT que encontrou. Tente reformular a pergunta ou use a Pesquisa na CLT.</p>
          </div>
        </div>
      )}

      <Notas r={r} />

      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-400">
        <button
          onClick={() => {
            navigator.clipboard?.writeText(r.resposta).then(() => {
              setCopiado(true);
              setTimeout(() => setCopiado(false), 1500);
            });
          }}
          className="inline-flex items-center gap-1 rounded px-1 py-0.5 hover:text-slate-600"
        >
          {copiado ? <Check size={13} /> : <Copy size={13} />} {copiado ? "Copiado" : "Copiar"}
        </button>
        <span>
          {r.metricas.modelo} · {(r.metricas.latenciaTotalMs / 1000).toFixed(1).replace(".", ",")} s
        </span>
      </div>

      {fontes.length > 0 && (
        <section className="mt-5 border-t border-slate-200 pt-4">
          <h3 className="text-sm font-semibold text-navy">Fontes legais</h3>
          <div className="mt-2.5 space-y-2.5">
            {fontes.map((f, i) => {
              const art = numeroDoArtigo(f.referencia);
              return (
                <div key={f.referencia} ref={(el) => void (cartoes.current[art] = el)}>
                  <SourceCard
                    indice={i + 1}
                    referencia={f.referencia}
                    documento={f.documento}
                    contexto={f.contexto}
                    link={f.link}
                    texto={f.texto}
                    citada={citados.has(art)}
                    aberta={aberta === art}
                    onAlternar={() => setAberta(aberta === art ? null : art)}
                  />
                </div>
              );
            })}
          </div>
        </section>
      )}

      <p className="mt-4 text-xs leading-relaxed text-slate-400">{r.aviso}</p>
    </Moldura>
  );
}

function Moldura({ children }: { children: ReactNode }) {
  return (
    <div>
      <div className="mb-2 flex items-center gap-2">
        <span className="grid size-7 place-items-center rounded-lg bg-brand-soft text-brand">
          <Sparkles size={15} />
        </span>
        <span className="text-sm font-semibold text-navy">LaborAI</span>
      </div>
      {children}
    </div>
  );
}

/** Parágrafos e listas simples ("- item", "1. item"), com **negrito** e citações clicáveis. */
function Corpo({ texto, artigos, onCitacao }: { texto: string; artigos: Set<string>; onCitacao: (art: string) => void }) {
  const linhas = texto.split("\n").map((l) => l.trim()).filter(Boolean);
  const blocos: { lista: boolean; linhas: string[] }[] = [];
  for (const l of linhas) {
    const item = /^([-*•]|\d+[.)])\s+/.test(l);
    const ultimo = blocos[blocos.length - 1];
    if (item && ultimo?.lista) ultimo.linhas.push(l.replace(/^([-*•]|\d+[.)])\s+/, ""));
    else blocos.push({ lista: item, linhas: [item ? l.replace(/^([-*•]|\d+[.)])\s+/, "") : l] });
  }
  return (
    <div className="space-y-3 text-[15px] leading-7 text-slate-800">
      {blocos.map((b, i) =>
        b.lista ? (
          <ul key={i} className="space-y-2">
            {b.linhas.map((l, j) => (
              <li key={j} className="flex gap-2.5">
                <CircleCheck size={17} className="mt-[5px] shrink-0 text-success" />
                <span>{inline(l, artigos, onCitacao)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p key={i}>{inline(b.linhas[0], artigos, onCitacao)}</p>
        ),
      )}
    </div>
  );
}

function inline(texto: string, artigos: Set<string>, onCitacao: (art: string) => void): ReactNode[] {
  return texto.split(/(\*\*[^*]+\*\*)/g).map((parte, i) => {
    if (/^\*\*[^*]+\*\*$/.test(parte)) return <strong key={i}>{comCitacoes(parte.slice(2, -2), artigos, onCitacao)}</strong>;
    return <Fragment key={i}>{comCitacoes(parte, artigos, onCitacao)}</Fragment>;
  });
}

function comCitacoes(texto: string, artigos: Set<string>, onCitacao: (art: string) => void): ReactNode[] {
  const saida: ReactNode[] = [];
  let ultimo = 0;
  for (const m of texto.matchAll(CITACAO)) {
    const art = m[2] ? `${m[1]}-${m[2].toUpperCase()}` : m[1];
    if (!artigos.has(art)) continue;
    saida.push(texto.slice(ultimo, m.index));
    saida.push(
      <button
        key={m.index}
        onClick={() => onCitacao(art)}
        className="rounded bg-brand-soft px-1 py-px font-medium whitespace-nowrap text-brand ring-1 ring-blue-100 hover:bg-blue-100"
        title={`Ver o texto do Art. ${art}`}
      >
        {m[0]}
      </button>,
    );
    ultimo = m.index + m[0].length;
  }
  saida.push(texto.slice(ultimo));
  return saida;
}

function Notas({ r }: { r: Resposta }) {
  const notas: ReactNode[] = [];
  if (r.metricas.termosGlossario?.length) {
    notas.push(
      <Nota key="g" Icone={Languages} tom="info">
        <strong className="font-medium">Busca ampliada:</strong> {r.metricas.termosGlossario.join(", ")} → vocabulário da CLT, pelo glossário de RH.
      </Nota>,
    );
  }
  r.citacao.problemasDeDispositivo?.forEach((p, i) =>
    notas.push(
      <Nota key={`p${i}`} Icone={ShieldCheck} tom="ajuste">
        <strong className="font-medium">Citação ajustada:</strong> {p.citacao} foi trocada pelo artigo, porque {p.detalhe.charAt(0).toLowerCase() + p.detalhe.slice(1)}.
      </Nota>,
    ),
  );
  if (r.citacao.foraDosTrechos?.length) {
    notas.push(
      <Nota key="f" Icone={TriangleAlert} tom="alerta">
        <strong className="font-medium">Atenção:</strong> a resposta cita {r.citacao.foraDosTrechos.map((a) => `Art. ${a}`).join(", ")}, que não estava entre os trechos consultados.
      </Nota>,
    );
  }
  return notas.length ? <div className="mt-4 space-y-2">{notas}</div> : null;
}

const TONS = {
  info: "bg-slate-50 text-slate-600 ring-slate-200",
  ajuste: "bg-amber-50 text-amber-900 ring-amber-200",
  alerta: "bg-red-50 text-red-800 ring-red-200",
};

function Nota({ Icone, tom, children }: { Icone: typeof Languages; tom: keyof typeof TONS; children: ReactNode }) {
  return (
    <div className={`flex items-start gap-2 rounded-lg px-3 py-2 text-[13px] leading-5 ring-1 ${TONS[tom]}`}>
      <Icone size={15} className="mt-0.5 shrink-0 opacity-70" />
      <span>{children}</span>
    </div>
  );
}
