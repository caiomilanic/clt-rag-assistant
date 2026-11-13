# Model configuration and performance

> Details moved out of the [README](../README.md) to keep it short.

From [`application.yml`](../src/main/resources/application.yml):

```yaml
spring:
  ai:
    model:
      embedding: ollama
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:qwen3:4b-instruct-2507-q4_K_M}
          temperature: 0.2
```

| Property | Value | What it does |
|---|---|---|
| `spring.ai.model.embedding` | `ollama` | Embeddings come from Ollama (`bge-m3`, 1024 dimensions), so nothing leaves the machine. |
| `ollama.base-url` | `http://localhost:11434` | Address of Ollama's HTTP API (11434 is the default port). Changes on deployment, when Ollama runs on another host or container. |
| `chat.options.model` | `qwen3:4b-instruct-2507-q4_K_M` | Which model answers: Qwen3 4B, *instruct* variant (no reasoning mode), 4-bit quantized. It must be pulled first (`ollama pull`), otherwise the API returns `404 model not found`. Details below. |
| `chat.options.temperature` | `0.2` | How much randomness goes into word choice. Details below. |

## `temperature`: creativity vs. predictability

An LLM generates text **one token at a time**. For each next token, the model scores every option in its vocabulary and turns those scores into probabilities. Then it **samples** one of them according to those probabilities.

Temperature comes in **before** sampling: the scores are divided by it.

- **Low temperature (close to 0):** the gaps between scores grow. The most likely option takes almost all the probability, so the model nearly always picks "the obvious word". Output is predictable, repeatable and conservative.
- **Temperature 1:** probabilities stay as the model learned them.
- **High temperature (above 1):** probabilities flatten and unlikely options gain ground. Output is more varied and "creative", but also more likely to drift off topic and make mistakes.

An intuitive example, completing *"O trabalhador tem direito a 30 dias de…"* ("Workers are entitled to 30 days of…"):

| Next token | Temp. 0.2 | Temp. 1.0 | Temp. 1.5 |
|---|---|---|---|
| "férias" (vacation) | ~100% | 80% | ~67% |
| "descanso" (rest) | <1% | 15% | ~22% |
| "folga" (time off) | ~0% | 5% | ~11% |

*(Illustrative: starts from the 1.0 column's probabilities and applies the temperature formula.)*

**Why `0.2` here:** in a RAG-based legal assistant, the answer should **faithfully reproduce the retrieved articles**, not "rewrite the law creatively". A low temperature keeps the model close to the source text and gives similar answers to the same question. It also matters for the **eval set**: with little randomness, a change in results tends to come from a change in the code, not from luck.

**Why not `0`:** at zero temperature (always picking the most likely token), small models tend to fall into loops and repetition. `0.2` keeps things predictable with a minimal margin of variation.

**What temperature does not fix:** a low temperature **does not prevent hallucination**. It only makes the model more *consistent* in what it says, even when it is wrong. The no-RAG test (see the [README](../README.md#why-rag)) already ran at `0.2` and confidently invented the articles. What fixes hallucination is RAG: giving the model the text of the law and requiring it to cite the source.

> Typical use: 0–0.3 for extraction, factual answers and RAG; 0.5–0.8 for general chat; above 1 for brainstorming and creative writing.

## Reasoning models and the instruct variant

Some models generate internal reasoning (*chain of thought*) before answering. That helps with math or multi-step problems, but in RAG the hard part (finding the information) is already done by the vector search; the model only needs to **read the retrieved excerpts and write the answer**. Reasoning just adds latency.

Turning it off turned out to be the tricky part:
- `qwen3:8b` is a hybrid model. Ollama's API accepts `"think": false`, but setting `think: false` through Spring AI **had no effect** (still ~600 output tokens).
- The default `qwen3:4b` tag is a **reasoning-only** model: it ignored `"think": false` and wrote a long English monologue before the answer (**1,230 tokens** for an answer of under 100).
- The **instruct** variant (`qwen3:4b-instruct-2507`) has no reasoning mode at all. Same size, direct answers. That's the one used here.

## Model choice and performance

Measured on the development machine (RTX 3060 Laptop, 6 GB VRAM; 16 GB RAM), same question, through Ollama's API:

| Model | Runs on | Speed | Total time | Notes |
|---|---|---|---|---|
| `qwen3:8b` (old NVIDIA driver) | 100% CPU | 6.4 tokens/s | ~89 s | GPU not detected |
| `qwen3:8b` (driver updated) | 30% CPU / 70% GPU | 13.7 tokens/s | ~43 s | Doesn't fit in 6 GB; no room left for embeddings |
| `qwen3:4b` (reasoning) | 100% GPU | 78 tokens/s | ~16 s | 1,230 tokens, mostly reasoning |
| **`qwen3:4b-instruct-2507`** | **100% GPU** | **85 tokens/s** | **~5 s** | **Chosen.** Fits on the GPU alongside `bge-m3` |

That's **~13x faster** than where the project started. Two lessons for on-premise sizing: **the model has to fit entirely in VRAM** (a partial split runs at a fraction of GPU speed), and **the chat model and the embedding model share that VRAM**.

**The trade-off:** a 4B model knows less than an 8B one, and without RAG it hallucinates just as freely (see the [README](../README.md#why-rag)). That's acceptable by design: with RAG the model doesn't need to *know* the law, it needs to *read* it. The eval sets show where that holds and where it doesn't: retrieval is no longer the bottleneck, and the remaining errors are mostly the 4B model misreading excerpts it did retrieve. A larger model is one configuration change away.
