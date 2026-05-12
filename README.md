<div align="center">

# NexusAI

[![License: CC BY-NC 4.0](https://img.shields.io/badge/License-CC%20BY--NC%204.0-lightgrey.svg)](./LICENSE)

**Plataforma full-stack de IA documental — chat multi-modelo, RAG, vision, generación de imágenes y persistencia cross-browser**

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring_AI-1.0-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)

</div>

---

## ✦ Qué hace

NexusAI unifica en **una sola conversación** todas las primitivas modernas de un asistente de IA documental:

- **Chat multi-modelo** con streaming SSE — selector en vivo entre `gpt-4.1`, `gpt-4.1-mini`, `gpt-4o`, `gpt-4o-mini`.
- **RAG** sobre PDFs / TXT / MD / HTML — pipeline asíncrono RabbitMQ → chunking → embeddings → pgvector → respuestas con citas exactas del documento. Si adjuntas un PDF junto con una pregunta, la pregunta se envía **automáticamente** cuando termina la indexación.
- **Vision** — adjunta una imagen y el modelo la describe en el chat.
- **Generación de imágenes / diagramas** — `gpt-image-1` (texto nítido) o `dall-e-3`, invocable con un toggle 🪄. Las imágenes generadas se persisten en MinIO (no dependen del URL temporal de OpenAI), por lo que sobreviven al refresh y al cierre del navegador.
- **Cache semántica de respuestas** — Redis (exact match) + pgvector (similaridad coseno). Badge ⚡ visible en la respuesta cuando viene de cache. Defensa contra entradas vacías en el cache (rotura silenciosa) y chunking del contenido cacheado para una entrega progresiva equivalente a un stream LLM real.
- **Persistencia cross-browser** — toda la conversación, adjuntos e imágenes viven en Postgres + MinIO. Inicia sesión en otro navegador y verás todo intacto.
- **UI responsive** — `md+` muestra sidebar fija; en móvil se colapsa en un drawer con backdrop accesible (cierra con Escape o tocando fuera). Burbujas y previews escalan al ancho disponible.
- **Slash commands** estilo Slack/Discord — escribe `/` en el chat para abrir el menú de comandos (`/export`, etc.).
- **Export del chat a ZIP** — pipeline `RabbitMQ → ZIP en memoria → MinIO → mensaje con descarga`.
- **Dashboard de estadísticas** — uso personal, cache hit rate, llamadas LLM, tokens, profundidad de colas RabbitMQ.

---

## ✦ Capturas

```
┌─────────────────────────────────────────────────────────────────┐
│ NexusAI                              GPT-4.1 ▾   Generando…    │
├──────────┬──────────────────────────────────────────────────────┤
│ Hoy      │  U  ¿Qué horarios tiene Bases de Datos 1?            │
│  · curso │                                                       │
│ Ayer     │  ✦  ⚡ Cache · exact                                 │
│  · setup │      Miércoles 11–13 (Virtual)                        │
│          │      Viernes 14–16 (Virtual)                          │
│          │                                                       │
│ 📊 Dashboard │                                                   │
│ 📁 Docs RAG  │  ┌──────────────────────────────────────────────┐ │
│              │  │ /export — Exportar este chat como ZIP        │ │
│ ⚙️ admin     │  └──────────────────────────────────────────────┘ │
│              │  /| 📎 🪄 ▸                                       │
└──────────┴──────────────────────────────────────────────────────┘
```

---

## ✦ Stack

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 3.4 (WebFlux reactivo) · Spring AI 1.0 · R2DBC · Java 21 · Virtual Threads |
| Frontend | React 19 · TypeScript 5 · Vite 6 · Tailwind CSS 4 · Zustand · TanStack Query |
| LLMs | OpenAI: `gpt-4.1`, `gpt-image-1`, `text-embedding-3-small`. Pluggable a Ollama / Gemini |
| Storage | PostgreSQL + pgvector · MinIO (S3-compatible) · Redis |
| Async | RabbitMQ (ingesta de docs · embeddings · jobs de export) |
| Observability | Micrometer · Prometheus · Grafana · OpenTelemetry tracing |
| Auth | JWT (HS256/HS512) + Spring Security reactiva |

---

## ✦ Quick start

### Requisitos

- Docker + Docker Compose
- JDK 21 (sólo para desarrollo local del backend)
- Node.js 20+ (sólo para desarrollo local del frontend)
- Una **`OPENAI_API_KEY`** válida

### En 30 segundos (modo demo, todo en Docker)

```bash
git clone https://github.com/sahernandezz/nexus-ai.git
cd nexus-ai

# 1) Configura los secretos (mínimo OPENAI_API_KEY + JWT_SECRET)
cp .env.example .env
$EDITOR .env

# 2) Levanta toda la stack (Postgres + Redis + RabbitMQ + MinIO + backend + frontend + Grafana + Prometheus + Jaeger)
docker compose up -d --build

# 3) Espera a que app y frontend reporten "healthy"
docker compose ps
```

Abre http://localhost (el frontend nginx hace reverse-proxy al backend en `:8080`).
Login: `admin / admin123` · `user / user123` · `agent / agent123`.

### Modo desarrollo (HMR + reload del backend)

```bash
# Backend solo (sin nexusai-app del compose)
docker compose up -d postgres redis rabbitmq minio
cd nexusai-backend && ./gradlew bootRun

# Frontend con HMR en :5173
cd nexusai-frontend && npm install && npm run dev
```

Vite ya tiene proxy a `http://localhost:8080` configurado para `/api/*` y `/auth/*`.

### Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `OPENAI_API_KEY` | — | Requerida para chat / vision / embeddings / image gen |
| `OPENAI_CHAT_MODEL` | `gpt-4.1` | Modelo por defecto del chat |
| `OPENAI_IMAGE_MODEL` | `gpt-image-1` | Si tu org no está verificada, usa `dall-e-3` |
| `OPENAI_IMAGE_QUALITY` | `high` | `low|medium|high|auto` para gpt-image-1 |
| `JWT_SECRET` | — | HS256 secret, mínimo 32 chars |
| `CACHE_SIMILARITY_THRESHOLD` | `0.85` | Umbral de la cache semántica (0..1) |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | — | PostgreSQL con pgvector |
| `REDIS_HOST` `REDIS_PORT` | — | Cache exact-match |
| `RABBITMQ_HOST` `RABBITMQ_USER` `RABBITMQ_PASS` | — | Async ingestion + export |
| `MINIO_ENDPOINT` `MINIO_ACCESS_KEY` `MINIO_SECRET_KEY` `MINIO_BUCKET` | — | Object storage |

---

## ✦ Arquitectura

```
                       ┌────────────────────┐
                       │  React 19 + Vite   │
                       │  Tailwind 4 (glass)│
                       │  Zustand + Query   │
                       └─────────┬──────────┘
                                 │ HTTPS / SSE
            ┌────────────────────┼─────────────────────┐
            │                    │                     │
   ┌────────▼─────────┐ ┌────────▼─────────┐ ┌────────▼─────────┐
   │   /api/chat/*    │ │   /api/rag/*     │ │  /api/exports/*  │
   │   /api/conv/*    │ │   /api/files/*   │ │  /api/stats      │
   └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
            │                    │                     │
   ┌────────▼─────────────────────▼─────────────────────▼────────┐
   │            Spring Boot 3.4 — WebFlux reactivo                │
   │   ChatService (cache-aware SSE)  ·  RagService (advisor)     │
   │   ConversationService (R2DBC)    ·  FileService (MinIO)      │
   │   ChatExportService + Consumer   ·  StatsService             │
   └────┬────────────┬────────────┬─────────────┬─────────────────┘
        │            │            │             │
   ┌────▼───┐  ┌─────▼────┐  ┌────▼─────┐  ┌────▼─────┐
   │ OpenAI │  │ Postgres │  │ RabbitMQ │  │  MinIO   │
   │  API   │  │ pgvector │  │  3 queues│  │ buckets  │
   └────────┘  └──────────┘  └──────────┘  └──────────┘
        │            │
   ┌────▼────┐  ┌────▼────┐
   │Embedding│  │  Redis  │ (exact-match cache)
   └─────────┘  └─────────┘
```

---

## ✦ Estructura

```
nexus-ai/
├── nexusai-backend/                   Spring Boot 3.4 + WebFlux + Spring AI
│   └── src/main/java/com/sahernandezz/nexusai/
│       ├── auth/                      JWT, UserDirectory (auto-provisioning)
│       ├── chat/                      ChatService cache-aware SSE
│       │   ├── advisor/               SafeGuard + Metrics advisors
│       │   └── dto/
│       ├── rag/
│       │   ├── ingestion/             RabbitMQ producer + consumer
│       │   └── retrieval/             RagService con system prompt anclado
│       ├── multimodal/                Vision + gpt-image-1 / dall-e-3
│       ├── conversations/             CRUD + upsert idempotente por clientId
│       ├── files/                     /api/files genérico (MinIO)
│       ├── exports/                   RabbitMQ → ZIP → MinIO
│       ├── stats/                     /api/stats para el dashboard
│       ├── cache/                     SemanticCacheService (Redis + pgvector)
│       ├── memory/                    Spring AI ChatMemoryAdvisor
│       ├── observability/             NexusAiMetrics (Prometheus)
│       └── config/                    SecurityConfig, RabbitMQConfig, OpenAiConfig...
│
├── nexusai-frontend/                  React 19 + Vite + Tailwind 4
│   └── src/
│       ├── components/chat/           ChatWindow, ChatInput, MessageBubble,
│       │                              ModelSelector, AuthenticatedAsset,
│       │                              RagDocumentsModal, SettingsModal
│       ├── components/layout/         AppLayout, Sidebar
│       ├── pages/                     ChatPage, DashboardPage, LoginPage
│       ├── api/                       conversationsApi, ragApi, multimodalApi,
│       │                              statsApi, errorMessage
│       ├── stores/                    chatStore (sync optimista), authStore
│       └── hooks/                     useChat, useStream (parsea event:meta)
│
├── prometheus/                        Scrape config
├── grafana/                           Dashboards provisioning
└── docker-compose.yml                 Postgres + Redis + RabbitMQ + MinIO + app
```

---

## ✦ Endpoints REST

<details>
<summary><b>Chat</b></summary>

| Method | Path | Descripción |
|---|---|---|
| POST | `/api/chat/stream` | SSE: emite `event: meta {cached, layer}` + `event: message <token>` |
| GET  | `/api/chat/models` | Providers disponibles |

</details>

<details>
<summary><b>RAG</b></summary>

| Method | Path | Descripción |
|---|---|---|
| POST   | `/api/rag/upload` | Sube documento (PDF/TXT/MD/HTML) |
| GET    | `/api/rag/documents` | Lista documentos del usuario |
| GET    | `/api/rag/documents/{id}` | Estado del documento |
| GET    | `/api/rag/documents/{id}/file` | Descarga el archivo desde MinIO |
| DELETE | `/api/rag/documents/{id}` | Elimina del índice vectorial |
| POST   | `/api/rag/stream` | Chat con contexto del documento (SSE cache-aware) |

</details>

<details>
<summary><b>Conversaciones (persistencia cross-browser)</b></summary>

| Method | Path | Descripción |
|---|---|---|
| GET    | `/api/conversations` | Lista del usuario actual |
| POST   | `/api/conversations` | Crear nueva |
| GET    | `/api/conversations/{id}` | Detalle con mensajes |
| PATCH  | `/api/conversations/{id}` | Renombrar |
| DELETE | `/api/conversations/{id}` | Eliminar |
| PUT    | `/api/conversations/{id}/messages` | Upsert (key: clientId UUID) |
| DELETE | `/api/conversations/{id}/messages/{clientId}` | Borrar mensaje |

</details>

<details>
<summary><b>Multimodal</b></summary>

| Method | Path | Descripción |
|---|---|---|
| POST | `/api/multimodal/describe` | Vision: imagen → texto |
| POST | `/api/multimodal/generate` | Genera imagen — body `{prompt, size?, quality?, model?}` |

</details>

<details>
<summary><b>Files (MinIO)</b></summary>

| Method | Path | Descripción |
|---|---|---|
| POST | `/api/files` | Multipart upload → `{id, url}` |
| GET  | `/api/files/{id}` | Stream del archivo (auth-protegido) |

</details>

<details>
<summary><b>Exports</b></summary>

| Method | Path | Descripción |
|---|---|---|
| POST | `/api/exports` | Encola export `{conversationId?}` (sin id = todo el historial) |
| GET  | `/api/exports/{id}` | Polling: `{status, attachmentId?, downloadUrl?}` |

</details>

<details>
<summary><b>Stats / Auth</b></summary>

| Method | Path | Descripción |
|---|---|---|
| GET  | `/api/stats` | Overview para el dashboard (uso, cache, LLM, colas, daily) |
| POST | `/auth/login` | Login → access + refresh JWT |
| POST | `/auth/refresh` | Renovar access token |

</details>

---

## ✦ Detalles de diseño

### Cache de respuestas

```
POST /api/chat/stream    (o /api/rag/stream — misma forma, key con docId prefix)
  └─ SemanticCacheService.lookup(prompt)
       ├─ HIT (content válido)
       │     → emit event:meta {cached:true, layer:exact|semantic}
       │     → emit event:message <content> en chunks de 80 chars
       │       (robustez SSE + UX progresivo, igual que un stream LLM real)
       │
       ├─ HIT con content vacío
       │     → log warn + tratar como MISS (auto-curado del cache)
       │
       └─ MISS
             → emit event:meta {cached:false}
             → stream LLM tokens como event:message
             → on complete: cacheService.store(prompt, fullResponse)
```

Frontend (`useStream.ts`) extrae el `event:` de cada chunk SSE; los `meta` van por `onMeta` y se proyectan al último mensaje del asistente como `cached + cacheLayer`. `MessageBubble` muestra **⚡ Cache · exact / semantic**. Si el stream completa sin emitir ningún chunk no vacío, `useChat` reemplaza la burbuja por una alerta visible — un problema silencioso ya no se queda como bubble vacío.

### Auto-envío después de indexar (PDF + pregunta)

```
1. handleFileSubmit(text="¿qué dice del crédito?", file=plan.pdf)
2. Aparece tarjeta "Indexar en RAG" + hint "Tu pregunta se enviará automáticamente"
3. Usuario click "Indexar"
4. ragApi.upload → pollUntilIndexed → setActiveDocumentId(doc.id)
5. sendMessage(deferredText, provider, chatModel, doc.id)   ← auto, con docId explícito
   (no espera la propagación de setActiveDocumentId)
```

Si el usuario pulsa "Omitir" en lugar de "Indexar", la pregunta se envía igual pero como chat normal (sin RAG) — la pregunta nunca se pierde.

### Imágenes generadas (text → image)

`POST /api/multimodal/generate` ya no devuelve sólo el URL temporal de OpenAI:

```
1. MultimodalService.generateImage → URL hospedada (DALL-E) o base64 (gpt-image-1)
2. MultimodalHandler descarga / decodifica los bytes
3. FileService.storeBytes(userId, conversationId, bytes, "generated-…png", …)
4. Responde {refId, refKind:"attachment", url:"/api/files/{refId}", filename, contentType, size}
5. Frontend guarda solo refId; <AuthenticatedAsset> hace fetch autenticado
```

Beneficio: la imagen sobrevive al refresh, al cierre del navegador y a la expiración del URL OpenAI (~1 h). El payload del `chat_messages.attachments` queda pequeño (UUID en vez de un base64 de varios MB).

### Persistencia cross-browser

- Cada mensaje del frontend genera un `clientId` (`crypto.randomUUID`) en el browser.
- `PUT /api/conversations/{id}/messages` hace `INSERT … ON CONFLICT (client_id) DO UPDATE` → idempotente.
- En login, `ProtectedRoute.useEffect` llama `chatStore.hydrateFromBackend()` que descarga `GET /api/conversations` → puebla el store local.
- Imágenes y PDFs van a `/api/files` (MinIO). El mensaje guarda solo `refId` + `refKind`. El componente `<AuthenticatedAsset>` hace fetch autenticado y muestra como blob URL — `<img src>` no puede llevar `Authorization: Bearer`.

### Export pipeline

```
1. Frontend  POST /api/exports {conversationId}
2. Backend   INSERT chat_exports (status=PENDING) + publish RabbitMQ
3. Worker    SELECT conversaciones → ZIP en memoria → MinIO
4. Worker    UPDATE chat_exports SET status=READY, attachment_id=…
5. Frontend  poll GET /api/exports/{id} cada 1.5s
6. Frontend  status=READY → renderiza mensaje con botón Descargar
```

### Seguridad y manejo de 401

Spring Security (WebFlux reactivo) con JWT HS256/HS512:

- `JwtAuthFilter` parsea `Authorization: Bearer …`, valida firma + expiración + claim `type=access`, y monta el `SecurityContext` reactivo.
- `httpBasic.disable()` + `formLogin.disable()` — el frontend es un SPA puro.
- **Custom `ServerAuthenticationEntryPoint`**: en 401 devuelve `{"error":"unauthorized","message":…}` con `Content-Type: application/json` y **sin** el header `WWW-Authenticate: Basic …`. Esto evita que Chrome / Edge / Firefox muestren el popup nativo de Basic-auth en respuestas de XHR/fetch — el axios interceptor del frontend captura el 401, intenta refresh y, si falla, redirige a `/login`.
- `ProtectedRoute` además verifica que `chatStore.ownerUsername === user.username`; si difiere, descarta el caché local y re-hidrata desde el backend (defensa en profundidad contra cambios de usuario en la misma pestaña).

### Slash commands

Tipea `/` en el chat para abrir el menú con autocompletado. Comando actual: `/export`. Extensible vía la prop `slashCommands` de `<ChatInput>` (cada uno: `{id, label, description, icon, action}`).

---

## ✦ Modelos por defecto

| Caso | Modelo | Override |
|---|---|---|
| Chat / RAG | `gpt-4.1` | UI selector + `OPENAI_CHAT_MODEL` |
| Embeddings | `text-embedding-3-small` (1536 dims) | — |
| Generación imagen | `gpt-image-1` (texto nítido) | UI selector + `OPENAI_IMAGE_MODEL` |
| Calidad imagen | `high` | `OPENAI_IMAGE_QUALITY` |
| Vision | hereda del chat | — |

> ⚠️ `gpt-image-1` requiere **organización verificada en OpenAI**. Si no, exporta `OPENAI_IMAGE_MODEL=dall-e-3` antes de levantar el backend.

---

## ✦ Migraciones (Flyway)

| Versión | Cambio |
|---|---|
| V1 | Schema inicial (users, conversations, chat_messages, documents, vector_store, audit_log) |
| V2 | Tabla documents (tracking) |
| V3 | semantic_cache (pgvector) |
| V4 | Fix de dimensiones de vectores |
| V5 | spring_ai_chat_memory |
| V6 | Vector dimensions → 1536 (text-embedding-3-small) |
| V7 | chat_messages extendida + chat_attachments + chat_exports |

---

## ✦ Tests

| Suite | Cantidad | Stack | Comando |
|---|---|---|---|
| Backend unit | **39** tests | JUnit 5 · Mockito · StepVerifier | `./gradlew test` |
| Backend integration | **14** tests | Testcontainers (Postgres + Redis + RabbitMQ + MinIO) | `./gradlew integrationTest` |
| Frontend | **60** tests | Vitest · Testing Library · jsdom | `npm test` |
| **Total** | **113 tests** verdes ✅ | | |

```bash
# Backend
cd nexusai-backend
./gradlew test                    # unit tests, ~6s, no Docker required
./gradlew integrationTest         # integration tests, requires Docker running
./gradlew check                   # test + lint (no incluye integrationTest)

# Frontend
cd nexusai-frontend
npm test                          # vitest run
npm run type-check                # tsc --noEmit
npm run build                     # tsc + vite build
```

> El target `check` no engloba a `integrationTest` porque éste depende de Docker
> (Testcontainers detecta `OrbStack` / `colima` / `Docker Desktop` automáticamente).
> En CI se invocan ambos por separado.

---

## ✦ Credenciales por defecto (dev)

| Usuario | Contraseña | Rol |
|---|---|---|
| admin | admin123 | ADMIN, USER |
| user  | user123  | USER |
| agent | agent123 | AGENT |

> Hasheadas con BCrypt; cambia en producción y/o conecta `ReactiveUserDetailsService` a tu propio backend.

---

## ✦ Despliegue / CI

Comprobaciones que cualquier rama debe pasar antes del merge:

```bash
# Backend
cd nexusai-backend
./gradlew check                    # compileJava + test (39 unit tests)
./gradlew integrationTest          # 14 integration tests con Testcontainers

# Frontend
cd nexusai-frontend
npm ci
npm run type-check                 # tsc --noEmit
npm run lint
npm test
npm run build                      # produce dist/ listo para nginx

# Imágenes
docker compose build app frontend  # valida los Dockerfiles + multi-stage build
```

Las migraciones Flyway (`V1`–`V7`) corren automáticamente al arrancar el backend; Testcontainers replica el mismo schema en cada integration test, por lo que un cambio de migración se valida en CI sin gestos extra.

---

## ✦ Roadmap

- [ ] Búsqueda full-text dentro del historial (Postgres GIN)
- [ ] Compartir conversaciones con un link público
- [ ] Plugins / herramientas adicionales para el agente
- [ ] Soporte multi-tenant
- [ ] Migración a `text-embedding-3-large` (3072 dims) con re-indexación

---

## ✦ Licencia

CC BY-NC 4.0 © Sergio Hernández

---

<div align="center">

**Construido con Spring AI, React 19 y mucho café ☕**

</div>
