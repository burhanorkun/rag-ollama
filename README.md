# RAG Claude + Ollama — Tamamen Local Embedding

Tek API key (Anthropic), embedding tamamen local.

- **Vector DB:** Qdrant (Docker)
- **Embeddings:** Ollama + `nomic-embed-text` (768-dim, Apache 2.0, ~270 MB)
- **Chat:** Claude Opus 4.7 via Anthropic Java SDK

## Setup

```bash
cd rag-ollama
cp .env.example .env
# .env'e ANTHROPIC_API_KEY yaz
```

## Run

```bash
docker compose up --build
```

İlk açılışta:
1. Qdrant başlar (port 6333)
2. Ollama başlar (port 11434)
3. `ollama-pull` container'ı `nomic-embed-text` modelini indirir (~270 MB, **internet gerekir**, sadece bir kez)
4. App build edilir, 15 saniye bekler, sonra docs'u embed eder
5. App 8080'de hazır

İkinci açılışta model ve embedding cache'leri volume'larda kaldığı için çok daha hızlı.

## Test

```bash
curl -X POST http://localhost:8080/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"Where is EPAM headquartered?"}'
```

## Servisleri Görsel Kontrol

| Servis | URL |
|---|---|
| Qdrant Dashboard | http://localhost:6333/dashboard |
| Ollama API | http://localhost:11434/api/tags |

```bash
# Ollama'da hangi modeller var?
curl http://localhost:11434/api/tags
```

## Mimari

```
docs/*.txt
   │
   ▼ (startup, 15s wait)
DocumentLoader ── chunk ──▶ OllamaClient (embed)
                                 │ (HTTP, local)
                                 ▼
                            Ollama container
                              nomic-embed-text
                                 │
                                 ▼ (768-dim vector)
                            QdrantClient ──▶ Qdrant (Docker)
                                                  ▲
POST /ask ──▶ OllamaClient (embed query) ─────────┘
                                 │
                                 ▼ (top-k chunks)
                            ClaudeClient ──▶ Claude API (internet)
                                                       │
                                                       ▼
                                                    Answer
```

## Önemli Notlar

- **Tamamen local embedding:** `nomic-embed-text` ile her embedding sıfır maliyet. Sadece Claude'a giden istekler ücretli.
- **CPU yeterli:** Ollama embedding modelleri CPU'da rahat çalışır (LLM gibi GPU şart değil).
- **Boyut:** 768-dim (`qdrant.vector-size=768` ile uyumlu). Voyage'da 512 idi, kod tarafında bir tek bu değer değişti.
- **`startup-delay-seconds=15`:** Ollama'nın model'i indirip yüklemesi için. İkinci açılışta gereksiz ama zararsız. İstersen `0` yapabilirsin.
- **Kalite:** Voyage / OpenAI'a göre biraz daha düşük ama küçük dokümanlarda farkı pratikte hissetmezsin. Daha büyük modeller: `mxbai-embed-large` (1024-dim), `bge-m3` (1024-dim, çok dilli — Türkçe için iyi).

## Türkçe doküman?

`nomic-embed-text` İngilizce odaklı. Türkçe içerik için `bge-m3` daha iyi:

1. `application.properties`:
   ```
   ollama.embedding-model=bge-m3
   qdrant.vector-size=1024
   ```
2. `docker-compose.yml` içindeki `ollama-pull` komutunu güncelle:
   ```yaml
   entrypoint: >
     sh -c "sleep 5 && OLLAMA_HOST=http://ollama:11434 ollama pull bge-m3"
   ```
3. Qdrant volume'unu sil (eski 768'lik koleksiyon kalır):
   ```bash
   docker compose down -v
   docker compose up --build
   ```
