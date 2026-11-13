import { CircleCheck, ChevronDown, ExternalLink } from "lucide-react";
import { formatarContexto, textoDaLei } from "../api";

interface Props {
  indice: number;
  referencia: string;
  documento: string;
  contexto: string;
  link: string;
  texto: string;
  citada?: boolean;
  aberta: boolean;
  onAlternar: () => void;
  /** Dispositivo que fez o artigo aparecer na busca (só na tela de pesquisa). */
  dispositivo?: string | null;
  resumo?: string;
}

/** Cartão de fonte: "FONTE 01 · LEGISLAÇÃO / CLT — Art. 73", com o texto do artigo ao abrir. */
export default function SourceCard(p: Props) {
  const lei = textoDaLei(p.texto);
  return (
    <div
      className={`rounded-xl border bg-white transition ${
        p.aberta ? "border-brand/50 shadow-md ring-4 ring-brand/10" : "border-slate-200 hover:border-slate-300"
      }`}
    >
      <button onClick={p.onAlternar} className="flex w-full items-start gap-3 px-4 py-3 text-left" aria-expanded={p.aberta}>
        <div className="min-w-0 flex-1">
          <div className="text-[11px] font-semibold tracking-wider text-brand uppercase">
            Fonte {String(p.indice).padStart(2, "0")} · Legislação
          </div>
          <div className="mt-0.5 flex flex-wrap items-center gap-x-2 text-[15px] font-medium text-navy">
            {p.documento} — {p.referencia}
            {p.citada && (
              <span className="inline-flex items-center gap-1 text-xs font-medium text-success">
                <CircleCheck size={13} /> citada na resposta
              </span>
            )}
            {p.dispositivo && p.dispositivo !== "artigo" && (
              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-medium text-slate-500">trecho encontrado: {p.dispositivo}</span>
            )}
          </div>
          {p.contexto && <div className="mt-0.5 truncate text-xs text-slate-500" title={formatarContexto(p.contexto)}>{formatarContexto(p.contexto)}</div>}
          {!p.aberta && p.resumo && <p className="mt-2 line-clamp-2 text-sm text-slate-600">{p.resumo}</p>}
        </div>
        <ChevronDown size={18} className={`mt-1 shrink-0 text-slate-400 transition ${p.aberta ? "rotate-180" : ""}`} />
      </button>
      {p.aberta && (
        <div className="border-t border-slate-100 px-4 pt-3 pb-4">
          <div className="max-h-96 overflow-y-auto pr-2 text-sm leading-6 whitespace-pre-line text-slate-700">{lei}</div>
          {p.link && (
            <a href={p.link} target="_blank" rel="noopener noreferrer" className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-brand hover:underline">
              Ver no site do Planalto <ExternalLink size={12} />
            </a>
          )}
        </div>
      )}
    </div>
  );
}
