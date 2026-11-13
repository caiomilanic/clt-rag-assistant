import { useEffect, useRef, useState, type CSSProperties } from "react";
import { ArrowUp, BriefcaseBusiness, CalendarDays, Clock, Handshake } from "lucide-react";
import type { Conversa } from "../conversas";
import StatusBadge from "./StatusBadge";
import AssistantMessage from "./AssistantMessage";

const SUGESTOES = [
  { Icone: CalendarDays, tema: "Férias", pergunta: "Um funcionário teve 8 faltas injustificadas no período. Quantos dias de férias ele tem?" },
  { Icone: Clock, tema: "Jornada", pergunta: "Posso adotar a escala 12x36 combinando direto com o funcionário?" },
  { Icone: BriefcaseBusiness, tema: "Rescisão", pergunta: "Qual o prazo para pagar as verbas rescisórias?" },
  { Icone: Handshake, tema: "Convenção coletiva", pergunta: "A convenção coletiva pode reduzir o intervalo de almoço para 30 minutos?" },
];

interface Props {
  conversa: Conversa | null;
  conectado: boolean | null;
  /** Instante em que a pergunta pendente desta conversa foi enviada; undefined se não há pergunta pendente. */
  enviadaEm?: number;
  onEnviar: (pergunta: string) => void;
}

export default function ChatView({ conversa, conectado, enviadaEm, onEnviar }: Props) {
  const [texto, setTexto] = useState("");
  const [agora, setAgora] = useState(Date.now());
  const fim = useRef<HTMLDivElement>(null);
  const campo = useRef<HTMLTextAreaElement>(null);
  const mensagens = conversa?.mensagens ?? [];
  const enviando = enviadaEm !== undefined;
  const segundos = enviando ? Math.max(0, Math.floor((agora - enviadaEm) / 1000)) : 0;

  useEffect(() => {
    fim.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [mensagens.length, enviando]);

  useEffect(() => {
    if (!enviando) return;
    const t = setInterval(() => setAgora(Date.now()), 1000);
    return () => clearInterval(t);
  }, [enviando]);

  // Trocar de conversa (inclusive "Nova conversa") limpa o campo e devolve o foco a ele.
  useEffect(() => {
    setTexto("");
    campo.current?.focus();
  }, [conversa?.id]);

  function enviar(pergunta: string) {
    const q = pergunta.trim();
    if (!q || enviando) return;
    setTexto("");
    setAgora(Date.now());
    onEnviar(q);
  }

  return (
    <div className="flex h-full flex-col">
      <div className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto max-w-3xl px-5 pt-6 pb-10">
          <StatusBadge conectado={conectado} />
          <h1 className="mt-3 text-2xl font-semibold tracking-tight text-navy">Assistente trabalhista</h1>
          <p className="mt-1 text-sm text-slate-500">Pesquisa na CLT e cita o artigo de cada afirmação. Clique numa citação para ler o texto da lei.</p>

          {mensagens.length === 0 && !enviando && (
            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              {SUGESTOES.map(({ Icone, tema, pergunta }) => (
                <button
                  key={tema}
                  onClick={() => enviar(pergunta)}
                  className="group rounded-xl border border-slate-200 bg-white p-4 text-left shadow-sm transition hover:border-brand/40 hover:shadow"
                >
                  <span className="flex items-center gap-2 text-xs font-semibold tracking-wide text-brand uppercase">
                    <Icone size={14} /> {tema}
                  </span>
                  <span className="mt-1.5 block text-sm text-slate-700 group-hover:text-navy">{pergunta}</span>
                </button>
              ))}
            </div>
          )}

          <div className="mt-6 space-y-6">
            {mensagens.map((m) =>
              m.papel === "usuario" ? (
                <div key={m.id} className="rounded-xl bg-canvas px-4 py-3">
                  <div className="text-[11px] font-semibold tracking-wider text-slate-400 uppercase">Você</div>
                  <p className="mt-0.5 text-[15px] whitespace-pre-wrap text-navy">{m.texto}</p>
                </div>
              ) : (
                <AssistantMessage key={m.id} mensagem={m} />
              ),
            )}
            {enviando && <Pensando segundos={segundos} />}
          </div>
          <div ref={fim} />
        </div>
      </div>

      <div className="shrink-0 border-t border-slate-100 bg-white px-5 pt-3 pb-4">
        <form
          className="mx-auto max-w-3xl"
          onSubmit={(e) => {
            e.preventDefault();
            enviar(texto);
          }}
        >
          <div className="flex items-end gap-2 rounded-xl border border-slate-200 bg-white p-2 shadow-sm focus-within:border-brand/50 focus-within:ring-4 focus-within:ring-brand/10">
            <textarea
              ref={campo}
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  enviar(texto);
                }
              }}
              rows={1}
              maxLength={1000}
              placeholder={mensagens.length ? "Faça outra pergunta…" : "Pergunte sobre a CLT…"}
              className="max-h-40 min-h-[40px] flex-1 resize-none bg-transparent px-2 py-2 text-[15px] outline-none placeholder:text-slate-400"
              style={{ fieldSizing: "content" } as CSSProperties}
            />
            <button
              type="submit"
              disabled={!texto.trim() || enviando}
              className="grid size-9 place-items-center rounded-lg bg-brand text-white transition hover:bg-blue-700 disabled:bg-slate-200 disabled:text-slate-400"
              aria-label="Enviar pergunta"
            >
              <ArrowUp size={18} />
            </button>
          </div>
          <p className="mt-2 text-center text-xs text-slate-400">
            Informação gerada por IA a partir do texto da CLT · confira a legislação aplicável · cada pergunta é respondida de forma independente
          </p>
        </form>
      </div>
    </div>
  );
}

function Pensando({ segundos }: { segundos: number }) {
  return (
    <div className="flex items-center gap-3 text-sm text-slate-500">
      <span className="flex gap-1">
        {[0, 150, 300].map((d) => (
          <span key={d} className="size-1.5 animate-bounce rounded-full bg-brand/60" style={{ animationDelay: `${d}ms` }} />
        ))}
      </span>
      Consultando a CLT e redigindo a resposta… {segundos > 0 && `${segundos} s`}
    </div>
  );
}
