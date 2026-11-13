import { useState } from "react";
import { LoaderCircle, Search } from "lucide-react";
import { buscar, textoDaLei, type ResultadoBusca } from "../api";
import { BASE } from "../config";
import StatusBadge from "./StatusBadge";
import SourceCard from "./SourceCard";

const EXEMPLOS = ["banco de horas", "intervalo para almoço", "trabalho noturno", "estabilidade da gestante", "aviso prévio"];

/** Pesquisa direta na base: a mesma busca do assistente, sem o modelo de linguagem. */
export default function SearchView({ conectado }: { conectado: boolean | null }) {
  const [q, setQ] = useState("");
  const [consulta, setConsulta] = useState("");
  const [resultados, setResultados] = useState<ResultadoBusca[] | null>(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [aberto, setAberto] = useState<string | null>(null);

  async function pesquisar(termo: string) {
    const t = termo.trim();
    if (!t) return;
    setQ(t);
    setConsulta(t);
    setCarregando(true);
    setErro(null);
    setAberto(null);
    try {
      setResultados(await buscar(t, 10));
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e));
      setResultados(null);
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-3xl px-5 pt-6 pb-12">
        <StatusBadge conectado={conectado} />
        <h1 className="mt-3 text-2xl font-semibold tracking-tight text-navy">Pesquisa na CLT</h1>
        <p className="mt-1 text-sm text-slate-500">
          Busca direta nos artigos, sem o modelo de linguagem: a mesma busca que o assistente usa antes de responder. Texto oficial de {BASE.obtidaEm}.
        </p>

        <form
          className="mt-5 flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            pesquisar(q);
          }}
        >
          <div className="flex flex-1 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 shadow-sm focus-within:border-brand/50 focus-within:ring-4 focus-within:ring-brand/10">
            <Search size={17} className="text-slate-400" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Assunto ou pergunta: férias proporcionais, horas extras, justa causa…"
              className="h-11 flex-1 bg-transparent text-[15px] outline-none placeholder:text-slate-400"
            />
          </div>
          <button type="submit" disabled={!q.trim() || carregando} className="rounded-xl bg-brand px-5 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-slate-200 disabled:text-slate-400">
            Pesquisar
          </button>
        </form>

        {!resultados && !carregando && !erro && (
          <div className="mt-4 flex flex-wrap gap-2">
            {EXEMPLOS.map((e) => (
              <button key={e} onClick={() => pesquisar(e)} className="rounded-full border border-slate-200 px-3 py-1 text-sm text-slate-600 hover:border-brand/40 hover:text-brand">
                {e}
              </button>
            ))}
          </div>
        )}

        {carregando && (
          <p className="mt-6 flex items-center gap-2 text-sm text-slate-500">
            <LoaderCircle size={16} className="animate-spin" /> Pesquisando…
          </p>
        )}
        {erro && <p className="mt-6 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 ring-1 ring-red-200">Não foi possível pesquisar. {erro}</p>}

        {resultados && !carregando && (
          <>
            <p className="mt-6 text-sm text-slate-500">
              {resultados.length} artigos mais próximos de <span className="font-medium text-slate-700">“{consulta}”</span>, do mais ao menos parecido.
            </p>
            <div className="mt-3 space-y-2.5">
              {resultados.map((r, i) => (
                <SourceCard
                  key={r.referencia}
                  indice={i + 1}
                  referencia={r.referencia}
                  documento={r.documento}
                  contexto={r.contexto}
                  link={r.link}
                  texto={r.texto}
                  dispositivo={r.dispositivo}
                  resumo={textoDaLei(r.trecho)}
                  aberta={aberto === r.referencia}
                  onAlternar={() => setAberto(aberto === r.referencia ? null : r.referencia)}
                />
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
