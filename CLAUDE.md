# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Audaque DataAgent — enterprise-grade intelligent data analyst built on Spring AI. Converts natural language to SQL, executes Python deep analysis, generates reports with ECharts charts, and exposes capabilities as MCP tools.

## Build & Development Commands

```bash
# Compile (skip tests)
./mvnw -B clean compile -DskipTests=true

# Package JAR
./mvnw -B clean package -DskipTests=true

# Run tests (uses H2 in-memory DB via application-h2.yml profile)
./mvnw test -pl data-agent-management

# Format code (Spring Java Format)
./mvnw spring-javaformat:apply -pl data-agent-management

# Format check
./mvnw spring-javaformat:validate -pl data-agent-management

# Checkstyle
./mvnw checkstyle:check -pl data-agent-management

# Single test class
./mvnw test -pl data-agent-management -Dtest=MarkdownParserUtilTest

# Make shortcuts (if mvnd available)
make build          # package
make test           # run tests
make format-check   # format validation
make format-fix     # auto-format
make checkstyle-check
```

Output JAR: `data-agent-management/target/spring-ai-audaque-data-agent-management-1.0.0-SNAPSHOT.jar`

## Code Style

- Java uses **tabs** (size 4), YAML/JSON/XML use **spaces** (size 2)
- Apache License 2.0 headers required on all Java files (enforced by Spotless)
- Spring Java Format enforced — run `spring-javaformat:apply` before committing
- Lombok used throughout (`@Slf4j`, `@Data`, `@Builder`, `@AllArgsConstructor`, etc.)
- `@Value` for config injection, not `@ConfigurationProperties` (except `DataAgentProperties`)

## Architecture

### Module Structure

Single Maven module: `data-agent-management` (Spring Boot 3.4.8, Java 17).
Frontend: `data-agent-frontend` (Vue 3 + Vite, separate build).

### Core: Graph-Based AI Workflow

The heart of the system is a **StateGraph** (Spring AI Alibaba) defined in `DataAgentConfiguration.nl2sqlGraph()`. User queries flow through 18 nodes with 12 dispatchers routing between them:

```
START → IntentRecognition → [EvidenceRecall → QueryEnhance] → SchemaRecall → TableRelation
  → FeasibilityAssessment → Planner → PlanValidator → [HumanFeedback?]
  → PlanExecutor → [SqlGenerate → SemanticConsistency → SqlExecute
    | PythonGenerate → PythonExecute → PythonAnalyze
    | ReportGenerator] → END
```

- **Nodes** (`workflow/node/`): Each node extends `NodeAction`, processes `OverAllState`, returns partial state updates
- **Dispatchers** (`workflow/dispatcher/`): Conditional edge routers that determine next node based on state
- **Graph state**: Uses `KeyStrategy.REPLACE` for all keys; `interruptBefore(HUMAN_FEEDBACK)` for human-in-the-loop
- **Streaming**: Reactor `Sinks.Many<ServerSentEvent<GraphNodeResponse>>` for SSE push to frontend

### Dynamic Model Management

LLM models are NOT auto-configured. Instead:
- `DynamicModelFactory` creates `OpenAiChatModel` / `OpenAiEmbeddingModel` on demand from DB-stored `ModelConfig`
- `AiModelRegistry` caches instances with double-checked locking + `volatile`
- `RateLimitedChatModel` wraps ChatModel with a Semaphore for concurrency control
- Models support custom auth headers (e.g., `szc-api-key`) via custom `WebClient`
- An `EmbeddingModel` Spring AOP proxy in `DataAgentConfiguration` ensures downstream components always get the latest model

### LLM Service Layer

- `LlmService` interface returns `Flux<ChatResponse>` for uniform reactive handling
- `StreamLlmService` (default): uses `ChatClient.stream()` (WebClient-based, SSE)
- `BlockLlmService`: wraps `ChatClient.call()` in `Mono.fromCallable()`
- Selected via `DataAgentProperties.llmServiceType` (default: `stream`)

### NL2SQL

`Nl2SqlServiceImpl` handles SQL generation with:
- Dynamic temperature (0.0 first attempt, 0.3 retries 1-5, 0.5 retries 6+)
- Configurable retry thresholds per error type (syntax/semantic/execution)
- Multi-dialect support: MySQL, PostgreSQL, SQL Server, Dameng, H2
- `SqlDialectResolver` determines dialect from datasource metadata

### Key Service Packages

| Package | Responsibility |
|---|---|
| `service/graph` | Graph orchestration, SSE streaming, multi-turn context |
| `service/nl2sql` | NL2SQL conversion, SQL retry logic |
| `service/aimodelconfig` | Model registry, factory, rate limiting |
| `service/code` | Python execution (Docker/Local/AI simulation) |
| `service/knowledge` | RAG: agent knowledge + business knowledge management |
| `service/vectorstore` | Vector store abstraction (simple/Milvus/ES) |
| `service/hybrid` | Hybrid retrieval: RRF fusion, weighted average |
| `service/prompt` | Prompt template loading (internal + external directory) |
| `service/mcp` | MCP server exposing NL2SQL as tools |

### Database Connector Layer

`connector/` package abstracts database access with implementations for MySQL, PostgreSQL, SQL Server, H2, DamengDB. Used by `SqlExecuteNode` to run generated SQL against user-configured datasources.

## Configuration

- **Dev config**: `data-agent-management/src/main/resources/application.yml`
- **Production template**: `application.yml.sample` (extensively commented, copy to deploy)
- **Test profile**: `application-h2.yml` (in-memory H2, `MODE=MySQL`)
- **Prompt templates**: `data-agent-management/src/main/resources/prompts/` (19 files)
- **External prompts**: Set `DATAAGENT_PROMPT_DIR` to override prompts without recompiling

### Key Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `DATA_AGENT_DATASOURCE_URL` | Metadata DB JDBC URL | `jdbc:mysql://127.0.0.1:3306/data_agent` |
| `DATASOURCE_PLATFORM` | `mysql` or `dameng` | `mysql` |
| `VECTOR_STORE_TYPE` | `simple`, `milvus`, `elasticsearch` | `simple` |
| `VECTOR_STORE_EXCLUDE` | Autoconfigure class exclusion | (empty) |
| `CODE_EXECUTOR` | `docker` or `local` | `docker` |
| `DATAAGENT_THINK_ENABLE` | Enable LLM thinking mode | `false` |
| `LLM_READ_TIMEOUT` | LLM API read timeout (ms) | `120000` |

## Testing

- Tests use H2 in-memory database via `application-h2.yml` profile
- TestContainers available for MySQL/Dameng integration tests
- Test classes in `data-agent-management/src/test/java/.../dataagent/`
- Run a single test: `./mvnw test -pl data-agent-management -Dtest=ClassName`

## Deployment

- Backend port: **8065**, Frontend dev: **3000**, Frontend prod (Nginx): **80**
- Docker Compose files in `docker-file/`: full stack, Dameng variant, datasource-only
- Deployment scripts: `deploy-dataagent.sh.sample`, `deploy-dataagent.sh.mysql.sample`
- Pre-built frontend assets served from `data-agent-management/src/main/resources/static/`

## CI/CD

GitHub Actions workflows in `.github/workflows/`:
- `build-and-test.yml`: format check → checkstyle → test → build (on push/PR to main)
- `frontend-check.yml`: frontend linting
- `license-check.yml`: license header verification
- `linter.yml`: YAML lint, codespell, newline check
- `secret-check.yml`: secrets detection

## Incremental Migrations

SQL migration scripts in `data-agent-management/src/main/resources/sql/migration/` (V1, V2, U1). Apply manually when upgrading — `spring.sql.init.mode` is set to `never` in production.
