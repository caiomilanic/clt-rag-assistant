import type { Resposta } from "./api";

/**
 * Conversas ficam só no navegador (localStorage): o backend não guarda histórico nem tem usuários.
 * Cada pergunta é respondida de forma independente; a conversa é um agrupamento para o analista.
 */
export interface Mensagem {
  id: string;
  papel: "usuario" | "assistente";
  texto: string;
  resposta?: Resposta;
  erro?: string;
}

export interface Conversa {
  id: string;
  titulo: string;
  atualizadaEm: number;
  mensagens: Mensagem[];
}

const CHAVE = "laborai.conversas.v1";

export function carregar(): Conversa[] {
  try {
    const bruto = localStorage.getItem(CHAVE);
    return bruto ? (JSON.parse(bruto) as Conversa[]) : [];
  } catch {
    return [];
  }
}

export function salvar(conversas: Conversa[]) {
  try {
    localStorage.setItem(CHAVE, JSON.stringify(conversas.slice(0, 30)));
  } catch {
    /* sem armazenamento (navegação privada, cota cheia): a conversa vale só para esta aba */
  }
}

export const novoId = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36);

export const tituloDe = (pergunta: string) => (pergunta.length > 48 ? pergunta.slice(0, 47).trimEnd() + "…" : pergunta);
