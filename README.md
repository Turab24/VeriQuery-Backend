# VeriQuery — Backend

Spring Boot service that ingests documents, indexes them into a pgvector store, and answers
questions against them with verifiable citations.

For the product overview, architecture write-up and Docker instructions, see the
[root README](../README.md) and [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).
This file covers only what you need to work on the backend module itself.

---

## What this module is responsible for

| Concern | Where |
| --- | --- |
| HTTP surface, DTO boundary, status codes | `web/`, `dto/` |
| Business rules and transaction boundaries | `service/` |
| Retrieval pipeline (embed → search → threshold → diversify → context) | `rag/` |
| Model providers behind two narrow ports | `ai/` |
| Text extraction and chunking | `document/` |
| Persistence, including the pgvector JDBC layer | `repository/`, `domain/` |
| Authentication, authorisation, JWT | `security/` |
| Error contract, correlation ids, timings | `exception/`, `observability/` |

---

## Prerequisites

- **Java 21** (`java -version` should report 21.x)
- **Maven 3.9+**
- **PostgreSQL 16 with the pgvector extension**

> The database **must** have pgvector available. A plain `postgres:16` image fails on
> migration `V1` at `CREATE EXTENSION vector`. Use `pgvector/pgvector:pg16`.

```bash
docker run --name veriquery-pg \
  -e POSTGRES_DB=veriquery \
  -e POSTGRES_USER=veriquery \
  -e POSTGRES_PASSWORD=veriquery \
  -p 5432:5432 -d pgvector/pgvector:pg16
```

---

## Running it

```bash
export JWT_SECRET="$(openssl rand -base64 48)"   # PowerShell: $env:JWT_SECRET="..."
mvn spring-boot:run
```

Flyway creates the schema and seeds the roles and demo accounts on first start. Nothing else
to set up.

| | |
| --- | --- |
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |


## Configuring the AI providers

**Generation and embedding are configured separately.** They are different capabilities and
no single vendor covers both well — most importantly, **Anthropic publishes no embeddings
API**, so a Claude-powered deployment still has to source its vectors elsewhere.

```
app.ai.chat-provider       demo | openai | anthropic
app.ai.embedding-provider  demo | openai
```

### The three setups that make sense

**1. Offline (default) — no key, no network**

```bash
AI_PROVIDER=demo
```
Deterministic hashing embeddings plus an extractive reader. The full pipeline runs and the
integration tests pass, but retrieval is lexical and answers are quoted verbatim rather than
generated. Good for development and CI; not good enough for a demo video.

**2. Claude for answers, OpenAI for retrieval — recommended**

```bash
AI_CHAT_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...
AI_EMBEDDING_PROVIDER=openai
OPENAI_API_KEY=sk-...
```
Real generated answers and real semantic retrieval. OpenAI embeddings cost roughly nothing
(fractions of a cent per document), so the second key is cheap insurance.

**3. Claude for answers, offline retrieval — one key only**

```bash
AI_CHAT_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...
AI_EMBEDDING_PROVIDER=demo
```
Claude genuinely generates the answers, but passages are matched on shared terms rather than
meaning. The dashboard and the startup log both say so while this is active.

Setting `AI_EMBEDDING_PROVIDER=anthropic` fails fast at startup with an explanation, rather
than with an opaque missing-bean error.

### Switching embedding providers invalidates your index

Vectors from different models are not comparable. After changing
`AI_EMBEDDING_PROVIDER`, **delete and re-upload your documents**, or re-index them from the
document page. Old documents will silently stop matching otherwise.

---

## Environment variables

| Variable | Default | Notes |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/enterprise_ai` | |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `enterprise` | |
| `JWT_SECRET` | dev-only value | **Required in production.** ≥ 32 bytes |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `PT30M` / `P14D` | ISO-8601 durations |
| `AI_CHAT_PROVIDER` | falls back to `AI_PROVIDER`, then `demo` | |
| `AI_EMBEDDING_PROVIDER` | falls back to `AI_PROVIDER`, then `demo` | |
| `ANTHROPIC_API_KEY` | — | Required when chat provider is `anthropic` |
| `ANTHROPIC_CHAT_MODEL` | `claude-sonnet-4-5` | Verify against Anthropic's current model list |
| `ANTHROPIC_MAX_TOKENS` | `2048` | Too low truncates answers mid-sentence |
| `OPENAI_API_KEY` | — | Required when either provider is `openai` |
| `OPENAI_BASE_URL` | `https://api.openai.com` | Point at any OpenAI-compatible endpoint |
| `EMBEDDING_DIMENSIONS` | `1536` | Must match the `vector(n)` column |
| `RAG_TOP_K` | `6` | Passages given to the model |
| `RAG_MIN_SIMILARITY` | `0.15` | **The refusal threshold.** Raise to be stricter |
| `STORAGE_LOCATION` | `./data/documents` | Where original files are written |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `prod` |

`StartupValidator` refuses to boot if the embedding dimension disagrees with the database
column, or if the development JWT secret is still in use under the `prod` profile.

---

## Testing

```bash
mvn test      # unit + slice tests. No Docker needed
mvn verify    # also runs the Testcontainers integration test
```

`RagPipelineIT` spins up a real `pgvector/pgvector:pg16` container and exercises the whole
pipeline — sign in, upload, wait for async indexing, semantic search, chat with citations,
refusal on an unrelated question, RBAC, delete. It runs with `app.ai.chat-provider=demo` so it
needs no API key and is deterministic. It is tagged `integration`, excluded from `mvn test`,
and skipped automatically when no Docker daemon is present.

---



Prompts live in `ai/PromptTemplates` — one versioned class, reviewed like any other
behaviour-defining code rather than scattered as string literals.

---

## Note on naming

The product is **VeriQuery**, but the source tree still carries identifiers from the project's
earlier working name: the Java package `com.enterpriseai.hub`, the Maven artifact
`enterprise-ai-hub-backend`, the default database name `enterprise_ai`, and the seeded demo
e-mail domain `@enterpriseai.local`.

None of this affects behaviour. It is a mechanical rename across roughly 130 files — package
declarations, imports, `pom.xml`, `application.yml`, the Flyway seed migration, Docker Compose
service and volume names — whenever you want it done in one pass.
