import { FileSearch, FileText, GitCompareArrows, Layers, CloudUpload } from "lucide-react";

const PLANO = [
  { Icone: Layers, titulo: "Convenções e acordos coletivos", texto: "PDFs divididos por cláusula, pesquisados junto com a CLT." },
  { Icone: GitCompareArrows, titulo: "Fontes lado a lado", texto: "O que diz a lei e o que diz a convenção, com a hierarquia entre elas (Arts. 611-A, 611-B e 620)." },
  { Icone: FileSearch, titulo: "Políticas internas", texto: "As regras da própria empresa como mais uma fonte citável." },
];

/** Tela reservada para a camada 2. Não simula funcionalidade: o envio fica desabilitado até o backend existir. */
export default function DocumentsView() {
  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-3xl px-5 pt-6 pb-12">
        <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] font-semibold tracking-wide text-slate-500 uppercase">Em breve</span>
        <h1 className="mt-3 text-2xl font-semibold tracking-tight text-navy">Documentos</h1>
        <p className="mt-1 text-sm text-slate-500">
          Hoje o assistente responde só com base na CLT. A próxima etapa acrescenta os documentos que completam a resposta de RH.
        </p>

        <div className="mt-6 flex flex-col items-center rounded-2xl border-2 border-dashed border-slate-200 bg-canvas px-6 py-10 text-center" aria-disabled="true">
          <span className="grid size-12 place-items-center rounded-xl bg-white text-slate-400 shadow-sm ring-1 ring-slate-200">
            <CloudUpload size={22} />
          </span>
          <p className="mt-3 font-medium text-slate-600">Envio de PDF e DOCX</p>
          <p className="mt-1 max-w-md text-sm text-slate-500">Disponível na próxima versão. Os documentos serão indexados nesta mesma máquina, sem sair da rede da empresa.</p>
          <button disabled className="mt-4 inline-flex cursor-not-allowed items-center gap-2 rounded-lg bg-slate-200 px-4 py-2 text-sm font-medium text-slate-400">
            <FileText size={15} /> Selecionar arquivos
          </button>
        </div>

        <h2 className="mt-8 text-sm font-semibold text-navy">O que vem</h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-3">
          {PLANO.map(({ Icone, titulo, texto }) => (
            <div key={titulo} className="rounded-xl border border-slate-200 bg-white p-4">
              <Icone size={18} className="text-brand" />
              <div className="mt-2 text-sm font-medium text-navy">{titulo}</div>
              <p className="mt-1 text-sm text-slate-500">{texto}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
