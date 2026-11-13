export default function StatusBadge({ conectado }: { conectado: boolean | null }) {
  if (conectado === null) {
    return <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] font-semibold tracking-wide text-slate-500 uppercase">Verificando…</span>;
  }
  return conectado ? (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-green-50 px-2.5 py-0.5 text-[11px] font-semibold tracking-wide text-success uppercase ring-1 ring-green-200">
      <span className="size-1.5 rounded-full bg-success" /> Base conectada
    </span>
  ) : (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-red-50 px-2.5 py-0.5 text-[11px] font-semibold tracking-wide text-red-600 uppercase ring-1 ring-red-200">
      <span className="size-1.5 rounded-full bg-red-500" /> Serviço indisponível
    </span>
  );
}
