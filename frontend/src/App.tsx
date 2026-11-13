import { useEffect, useState } from "react";
import { Menu, X } from "lucide-react";
import { BASE, PRODUTO, SUBTITULO } from "./config";
import { carregar, salvar, type Conversa } from "./conversas";
import { perguntar, saude } from "./api";
import { novoId, tituloDe, type Mensagem } from "./conversas";
import Logo from "./components/Logo";
import Sidebar, { type Tela } from "./components/Sidebar";
import ChatView from "./components/ChatView";
import SearchView from "./components/SearchView";
import DocumentsView from "./components/DocumentsView";

export default function App() {
  const [tela, setTela] = useState<Tela>("assistente");
  const [conversas, setConversas] = useState<Conversa[]>(carregar);
  const [ativa, setAtiva] = useState<string | null>(null);
  const [conectado, setConectado] = useState<boolean | null>(null);
  const [menuAberto, setMenuAberto] = useState(false);

  useEffect(() => salvar(conversas), [conversas]);

  useEffect(() => {
    let vivo = true;
    const checar = () => saude().then((ok) => vivo && setConectado(ok));
    checar();
    const t = setInterval(checar, 30_000);
    return () => {
      vivo = false;
      clearInterval(t);
    };
  }, []);

  /** Conversas com pergunta em andamento: id → instante do envio (para o contador de segundos). */
  const [pendentes, setPendentes] = useState<Record<string, number>>({});

  const conversaAtiva = conversas.find((c) => c.id === ativa) ?? null;

  /** Acrescenta uma mensagem à conversa e a leva para o topo da lista, sem mudar a conversa que está na tela. */
  const acrescentar = (id: string, m: Mensagem) =>
    setConversas((todas) => {
      const c = todas.find((x) => x.id === id);
      if (!c) return todas; // apagada enquanto a resposta não chegava
      return [{ ...c, atualizadaEm: Date.now(), mensagens: [...c.mensagens, m] }, ...todas.filter((x) => x.id !== id)];
    });

  async function enviar(pergunta: string) {
    let id = conversaAtiva?.id;
    if (!id) {
      id = novoId();
      const nova: Conversa = { id, titulo: tituloDe(pergunta), atualizadaEm: Date.now(), mensagens: [] };
      setConversas((todas) => [nova, ...todas]);
      setAtiva(id);
    }
    const alvo = id;
    acrescentar(alvo, { id: novoId(), papel: "usuario", texto: pergunta });
    setPendentes((p) => ({ ...p, [alvo]: Date.now() }));
    try {
      const r = await perguntar(pergunta);
      acrescentar(alvo, { id: novoId(), papel: "assistente", texto: r.resposta, resposta: r });
    } catch (e) {
      acrescentar(alvo, { id: novoId(), papel: "assistente", texto: "", erro: e instanceof Error ? e.message : String(e) });
    } finally {
      setPendentes((p) => {
        const resto = { ...p };
        delete resto[alvo];
        return resto;
      });
    }
  }

  const irPara = (t: Tela) => {
    setTela(t);
    setMenuAberto(false);
  };

  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center justify-between bg-navy px-4 text-white">
        <div className="flex items-center gap-3">
          <button
            className="rounded-md p-1.5 text-slate-300 hover:bg-white/10 md:hidden"
            onClick={() => setMenuAberto((v) => !v)}
            aria-label={menuAberto ? "Fechar menu" : "Abrir menu"}
          >
            {menuAberto ? <X size={20} /> : <Menu size={20} />}
          </button>
          <Logo />
          <div className="leading-tight">
            <div className="text-[15px] font-semibold">{PRODUTO}</div>
            <div className="text-[10px] font-medium tracking-[0.14em] text-slate-400 uppercase">{SUBTITULO}</div>
          </div>
        </div>
        <span
          className="hidden items-center gap-1.5 rounded-full border border-white/15 px-3 py-1 text-xs text-slate-300 sm:inline-flex"
          title="Modelo de linguagem, embeddings e banco rodam nesta rede"
        >
          <span className="size-1.5 rounded-full bg-success" />
          On-premise · nenhum dado sai da rede
        </span>
      </header>

      <div className="relative flex min-h-0 flex-1">
        <Sidebar
          tela={tela}
          conversas={conversas}
          ativa={tela === "assistente" ? ativa : null}
          aberto={menuAberto}
          base={BASE}
          onNovaConversa={() => {
            setAtiva(null);
            irPara("assistente");
          }}
          onAbrirConversa={(id) => {
            setAtiva(id);
            irPara("assistente");
          }}
          onApagarConversa={(id) => {
            setConversas((todas) => todas.filter((c) => c.id !== id));
            if (ativa === id) setAtiva(null);
          }}
          onTela={irPara}
        />
        {menuAberto && (
          <button className="fixed inset-0 top-14 z-10 bg-navy/30 md:hidden" aria-label="Fechar menu" onClick={() => setMenuAberto(false)} />
        )}

        <main className="min-w-0 flex-1 overflow-hidden bg-white">
          {tela === "assistente" && (
            <ChatView
              conversa={conversaAtiva}
              conectado={conectado}
              enviadaEm={conversaAtiva ? pendentes[conversaAtiva.id] : undefined}
              onEnviar={enviar}
            />
          )}
          {tela === "pesquisa" && <SearchView conectado={conectado} />}
          {tela === "documentos" && <DocumentsView />}
        </main>
      </div>
    </div>
  );
}
