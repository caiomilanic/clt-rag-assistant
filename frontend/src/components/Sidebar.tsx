import { BookOpen, FolderOpen, MessageSquare, Plus, Trash2 } from "lucide-react";
import type { ReactNode } from "react";
import type { Conversa } from "../conversas";

export type Tela = "assistente" | "pesquisa" | "documentos";

interface Props {
  tela: Tela;
  conversas: Conversa[];
  ativa: string | null;
  aberto: boolean;
  base: { nome: string; obtidaEm: string };
  onNovaConversa: () => void;
  onAbrirConversa: (id: string) => void;
  onApagarConversa: (id: string) => void;
  onTela: (t: Tela) => void;
}

const ITENS: { tela: Tela; rotulo: string; Icone: typeof MessageSquare; emBreve?: boolean }[] = [
  { tela: "assistente", rotulo: "Assistente", Icone: MessageSquare },
  { tela: "pesquisa", rotulo: "Pesquisa na CLT", Icone: BookOpen },
  { tela: "documentos", rotulo: "Documentos", Icone: FolderOpen, emBreve: true },
];

export default function Sidebar(p: Props) {
  return (
    <aside
      className={`absolute inset-y-0 left-0 z-20 flex w-72 shrink-0 flex-col border-r border-slate-200 bg-canvas transition-transform md:static md:translate-x-0 ${
        p.aberto ? "translate-x-0" : "-translate-x-full"
      }`}
    >
      <div className="p-3">
        <button
          onClick={p.onNovaConversa}
          className="flex w-full items-center gap-2 rounded-lg bg-brand px-3 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
        >
          <Plus size={16} /> Nova conversa
        </button>
      </div>

      <nav className="px-3">
        <Titulo>Workspace</Titulo>
        {ITENS.map(({ tela, rotulo, Icone, emBreve }) => (
          <button
            key={tela}
            onClick={() => p.onTela(tela)}
            className={`flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-sm ${
              p.tela === tela ? "bg-white font-medium text-navy shadow-sm ring-1 ring-slate-200" : "text-slate-600 hover:bg-white/70"
            }`}
          >
            <Icone size={16} className={p.tela === tela ? "text-brand" : "text-slate-400"} />
            {rotulo}
            {emBreve && (
              <span className="ml-auto rounded-full bg-slate-200 px-2 py-0.5 text-[10px] font-medium text-slate-500 uppercase">
                em breve
              </span>
            )}
          </button>
        ))}
      </nav>

      <div className="mt-5 min-h-0 flex-1 overflow-y-auto px-3">
        <Titulo>Conversas recentes</Titulo>
        {p.conversas.length === 0 && <p className="px-2.5 text-xs text-slate-400">As conversas ficam salvas neste navegador.</p>}
        {p.conversas.map((c) => (
          <div
            key={c.id}
            className={`group flex items-center rounded-md ${p.ativa === c.id ? "bg-white shadow-sm ring-1 ring-slate-200" : "hover:bg-white/70"}`}
          >
            <button onClick={() => p.onAbrirConversa(c.id)} className="min-w-0 flex-1 truncate px-2.5 py-2 text-left text-sm text-slate-600" title={c.titulo}>
              {c.titulo}
            </button>
            <button
              onClick={() => p.onApagarConversa(c.id)}
              className="mr-1 rounded p-1 text-slate-400 opacity-0 group-hover:opacity-100 hover:text-red-600 focus:opacity-100"
              aria-label={`Apagar a conversa "${c.titulo}"`}
            >
              <Trash2 size={14} />
            </button>
          </div>
        ))}
      </div>

      <div className="border-t border-slate-200 px-4 py-3 text-xs text-slate-500">
        Base de conhecimento: <span className="font-medium text-slate-600">{p.base.nome}</span>
        <br />
        Texto oficial de {p.base.obtidaEm}
      </div>
    </aside>
  );
}

function Titulo({ children }: { children: ReactNode }) {
  return <div className="mb-1.5 px-2.5 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">{children}</div>;
}
