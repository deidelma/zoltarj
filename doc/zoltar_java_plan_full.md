# Zoltar — Java/JavaFX Desktop RAG App Plan  
*(Modules under `ca.zoltar.*` — e.g., `ca.zoltar.app`, `ca.zoltar.gui`, etc.)*

---

## 0) Purpose & Scope (Java Version)

Zoltar is a cross-platform desktop application that ingests topic-specific PDFs (published articles, reports, etc.) and uses them as context to assess the **novelty** of new PubMed abstracts via hybrid retrieval (embeddings + BM25) and OpenAI.

This plan mirrors the original Python/PySide6 Zoltar plan, but the technology stack is now:

- **Language**: Java 21+ (LTS, modular).
- **GUI**: JavaFX (controls, FXML).
- **Storage**: SQLite via JDBC.
- **Hybrid Retrieval**:
  - Dense semantic search: OpenAI embeddings (HTTP client).
  - Sparse lexical search: Apache Lucene with BM25 similarity.
- **Packaging**: `jlink` + `jpackage` to build small, self-contained installers for:
  - **macOS** (`.app` in `.dmg` or `.pkg`).
  - **Windows** (`.exe` / `.msi`).

High-level goals:

1. Allow a user to define **topics** (e.g., *“Macrophages in pulmonary fibrosis”*).
2. Ingest and index **PDFs** into a hybrid (semantic + lexical) search space.
3. Periodically poll **PubMed** for new abstracts matching a topic query.
4. For each new abstract:
   - Retrieve the most relevant chunks from the corpus.
   - Ask OpenAI (via a structured prompt) to judge whether the abstract appears *novel* or *incremental* relative to the corpus.
5. Store all results in a **local database** for review, filtering, and export.
6. Ship as a **native-like desktop app** on macOS and Windows, without requiring a separate JDK installation.

Non-goals (for the first version):

- No built-in PDF viewer / annotation.
- No multi-user networked mode (single-install, single-database).
- No self-hosted LLM (OpenAI only to start, with potential future extensibility).

---

## 1) Architecture at a Glance

```text
+----------------------+          +---------------------+          +----------------------+
| JavaFX Desktop GUI   |  <---->  |  Core Services      |  <---->  |  Storage & Indexing  |
|  (ca.zoltar.gui)     |          |  (ca.zoltar.core)   |          |  (ca.zoltar.db,      |
+----------------------+          +---------------------+          |   ca.zoltar.search)  |
          |                               |                        +----------------------+
          v                               v                                   |
  +---------------------+       +---------------------+                       v
  | PDF Ingestion       |       | Hybrid Retrieval    |               +------------------+
  | (ca.zoltar.core)    |       | (semantic + BM25)   |               | PubMed Client     |
  +---------------------+       +---------------------+               | (ca.zoltar.pubmed)|
                                          |                          +------------------+
                                          v
                                   +-------------------+
                                   | LLM Evaluation    |
                                   | (OpenAI API)      |
                                   +-------------------+
```

### Java Modules (JPMS)

Use Java Platform Module System to organize the app and to enable `jlink` to trim the runtime:

- **`ca.zoltar.app`**
  - Main launcher, JavaFX `Application` class.
- **`ca.zoltar.gui`**
  - JavaFX views/controllers, FXML, CSS, resources.
- **`ca.zoltar.core`**
  - Business logic: ingestion, indexing orchestration, retrieval, evaluation, settings.
- **`ca.zoltar.db`**
  - SQLite schema bootstrap, migrations, DAO layer.
- **`ca.zoltar.search`**
  - Lucene indexing and search; vector similarity utilities.
- **`ca.zoltar.pubmed`**
  - PubMed API client (E-utilities).
- **`ca.zoltar.util`**
  - Utilities: text cleaning, tokenization, config/loading helpers, logging wrappers.

Each module has a `module-info.java` with minimal `requires` and `exports` to keep the dependency graph slim and improve `jlink` trimming.

Example (simplified):

```java
module ca.zoltar.core {
    requires java.sql;
    requires java.net.http;
    requires ca.zoltar.db;
    requires ca.zoltar.search;
    requires ca.zoltar.pubmed;
    requires ca.zoltar.util;

    exports ca.zoltar.core.service;
}
```

---

## 2) Data Model (SQLite via JDBC)

The logical schema mirrors the Python version but is implemented via JDBC and DAO classes in `ca.zoltar.db`.

### Tables

1. **Topics**

```sql
CREATE TABLE topic (
    id          INTEGER PRIMARY KEY,
    name        TEXT NOT NULL,
    query_string TEXT,
    notes       TEXT,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);
```

2. **Documents**

```sql
CREATE TABLE document (
    id           INTEGER PRIMARY KEY,
    topic_id     INTEGER NOT NULL,
    title        TEXT,
    source_path  TEXT NOT NULL,
    doi          TEXT,
    pmid         TEXT,
    year         INTEGER,
    venue        TEXT,
    hash_sha256  TEXT NOT NULL UNIQUE,
    added_at     TEXT NOT NULL,
    FOREIGN KEY (topic_id) REFERENCES topic(id)
);
CREATE INDEX idx_document_topic ON document(topic_id);
```

3. **Chunks**

```sql
CREATE TABLE chunk (
    id           INTEGER PRIMARY KEY,
    document_id  INTEGER NOT NULL,
    topic_id     INTEGER NOT NULL,
    chunk_index  INTEGER NOT NULL,
    text         TEXT NOT NULL,
    tokens       INTEGER,
    lucene_doc_id INTEGER,
    added_at     TEXT NOT NULL,
    FOREIGN KEY (document_id) REFERENCES document(id),
    FOREIGN KEY (topic_id) REFERENCES topic(id)
);
CREATE INDEX idx_chunk_topic ON chunk(topic_id);
CREATE INDEX idx_chunk_document ON chunk(document_id);
```

4. **Embeddings**

```sql
CREATE TABLE embedding (
    id        INTEGER PRIMARY KEY,
    chunk_id  INTEGER NOT NULL,
    model     TEXT NOT NULL,
    vector    BLOB NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (chunk_id) REFERENCES chunk(id)
);
CREATE UNIQUE INDEX idx_embedding_chunk_model ON embedding(chunk_id, model);
```

5. **PubMed Seen**

```sql
CREATE TABLE pubmed_seen (
    id           INTEGER PRIMARY KEY,
    topic_id     INTEGER NOT NULL,
    pmid         TEXT NOT NULL,
    first_seen_at TEXT NOT NULL,
    UNIQUE(topic_id, pmid)
);
CREATE INDEX idx_pubmed_seen_topic ON pubmed_seen(topic_id);
```

6. **Abstracts (Stored PubMed Abstracts)**

```sql
CREATE TABLE abstract (
    id            INTEGER PRIMARY KEY,
    topic_id      INTEGER NOT NULL,
    pmid          TEXT NOT NULL,
    title         TEXT,
    authors_json  TEXT,
    journal       TEXT,
    pub_date      TEXT,
    abstract_text TEXT,
    added_at      TEXT NOT NULL,
    FOREIGN KEY (topic_id) REFERENCES topic(id),
    UNIQUE(topic_id, pmid)
);
CREATE INDEX idx_abstract_topic ON abstract(topic_id);
```

7. **Evaluation Runs & Results**

```sql
CREATE TABLE evaluation_run (
    id          INTEGER PRIMARY KEY,
    topic_id    INTEGER NOT NULL,
    pmid        TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    llm_model   TEXT NOT NULL,
    params_json TEXT NOT NULL,
    FOREIGN KEY (topic_id) REFERENCES topic(id)
);

CREATE TABLE evaluation_result (
    id                    INTEGER PRIMARY KEY,
    run_id                INTEGER NOT NULL,
    novelty_label         TEXT NOT NULL,
    novelty_score         INTEGER NOT NULL,
    rationale             TEXT,
    used_chunk_ids_json   TEXT,
    hybrid_scores_json    TEXT,
    FOREIGN KEY (run_id) REFERENCES evaluation_run(id)
);
```

---

## 3) Text Processing, PDF Ingestion & Chunking

### PDF Parsing (Apache PDFBox)

- Use **Apache PDFBox** to parse PDFs page by page:
  - Extract text per page.
  - Concatenate or process page-wise for later de-duplication.

### Text Cleaning

Utility methods in `ca.zoltar.util.text`:

- Normalize whitespace (collapse multiple spaces/newlines).
- Remove or mitigate:
  - Page headers/footers (heuristics).
  - Repeated running titles.
  - Obvious reference section (heuristic: detect “References”, “Bibliography”, etc., near the end).
- De-hyphenate words split across lines.

### Chunking Strategy

- Use a simple tokenizer (e.g., split on whitespace and punctuation).
- Aim for approximate token-based chunks (e.g. 600–800 tokens) with overlap (e.g. 150–200 tokens).
- Each chunk stores:
  - `document_id`, `topic_id`, `chunk_index`, `text`, approximate `tokens`.

Algorithm:

1. Split normalized document text into tokens.
2. Slide a window of size `CHUNK_SIZE` with stride `CHUNK_STRIDE`.
3. For each window:
   - Join into a chunk string.
   - Persist as a row in `chunk`.

---

## 4) Embeddings & Hybrid Search (BM25 + Embeddings)

### Embeddings

- Use OpenAI embeddings endpoint via `java.net.http.HttpClient`.
- A service in `ca.zoltar.core.service.EmbeddingService`:
  - Given `chunk_id` and text:
    - Fetch embedding vector.
    - Serialize to a `BLOB` using `ByteBuffer` or a small custom format.
    - Store in `embedding` table.

### Lexical Search (Lucene BM25)

Module: `ca.zoltar.search`.

- Maintain a per-topic Lucene index under an app-specific data directory:
  - e.g. `~/.zoltar-java/indexes/{topic_id}/` on Unix-like systems.
- For each chunk:
  - Index fields:
    - `topic_id` (stored, indexed).
    - `document_id` (stored).
    - `chunk_id` (stored).
    - `content` (indexed, analyzed).
- Configure `IndexSearcher` with `BM25Similarity`.
- Provide:
  - `List<SearchHit> search(String query, int k)` where `SearchHit` includes `chunk_id` and BM25 score.

### Hybrid Retrieval

Module: `ca.zoltar.core` (e.g., class `HybridRetrievalService`).

Given a PubMed abstract text:

1. **Semantic retrieval**:
   - Compute embedding for the abstract using the same model as chunks.
   - For the topic’s chunks, compute cosine similarity between the abstract vector and each stored chunk vector.
   - Take top `K_sem` candidates (e.g. 200).
   - Implement a fast vector similarity routine in Java, possibly with streaming from SQLite.

2. **Lexical retrieval**:
   - Use Lucene with the abstract text as a query.
   - Take top `K_lex` candidates.

3. **Merge & Normalize**:
   - Combine the candidate sets by `chunk_id`.
   - Normalize both semantic and BM25 scores to [0, 1] (min-max or rank-based).

4. **Hybrid scoring**:
   - `H = α * semantic_score + (1 - α) * bm25_score`, with α ~ 0.6 as default.
   - Optionally, apply a small penalty for very short chunks or low absolute scores.

5. **Selection**:
   - Sort by hybrid score descending.
   - Take top `K_ctx` chunks (e.g. 20–40), subject to token budget constraints for the LLM context.

6. **Optional diversification**:
   - Use a simple MMR-like scheme or heuristic to reduce redundancy:
     - Prefer chunks from different documents and different parts of each document.

---

## 5) LLM Evaluation Design (OpenAI)

### Prompt Design

System message example:

> You are an expert research assistant in biomedical science.  
> You receive a topic, a new PubMed abstract, and a set of context passages from existing articles on that topic.  
> Your task is to judge whether the abstract describes a novel contribution relative to the context, focusing on methods, population, and outcomes.

User message template:

- Topic description.
- New abstract:
  - Title, PMID, journal, year.
  - Abstract text.
- Summary of retrieved context:
  - For each selected chunk:
    - Document title, year, optional citation info, and the chunk text.
- Clear instructions to:
  - Output a **JSON** object with fields:
    - `novelty_label`: `"novel" | "uncertain" | "not_novel"`.
    - `novelty_score`: integer 0–10.
    - `rationale`: free-text explanation.
    - `supporting_evidence`: list of chunk IDs with brief explanations.

### Implementation

Module: `ca.zoltar.core` (e.g., `EvaluationService`).

- Use `java.net.http.HttpClient` for OpenAI Chat/Responses API.
- Build a typed request class describing:
  - Model (e.g. `gpt-5.1` or similar).
  - Messages array.
  - Response format (JSON).
- Parse the JSON response into a `NoveltyEvaluationResult` Java record/class:
  - Validate fields.
  - Use defensive parsing with helpful error messages.

### Persistence

For each evaluation run:

1. Insert into `evaluation_run` with:
   - `topic_id`, `pmid`, `created_at`, `llm_model`, `params_json` (includes α, K values, model name, etc.).
2. Insert corresponding `evaluation_result` row:
   - `novelty_label`, `novelty_score`, `rationale`, `used_chunk_ids_json`, `hybrid_scores_json`.

---

## 6) JavaFX GUI Design (ca.zoltar.gui)

### Main Structure

- `MainApp` in `ca.zoltar.app`:
  - Extends `javafx.application.Application`.
  - Loads primary stage, scene, menu bar, and sets initial view.
- Views managed via FXML:
  - `HomeView.fxml`
  - `TopicView.fxml`
  - `LibraryView.fxml`
  - `PubMedMonitorView.fxml`
  - `EvaluateView.fxml`
  - `ResultsView.fxml`
  - `SettingsView.fxml`

### Screens & Workflows

1. **Home / Topics View**
   - Table/list of topics.
   - Buttons:
     - “Add topic” → dialog with name, PubMed query, notes.
     - “Edit topic”.
     - “Delete topic” (with confirmation).
   - Status column indicating:
     - Number of documents.
     - Last ingestion.
     - Last PubMed check.

2. **Library View (Documents & Ingestion)**
   - Shows all PDFs for selected topic.
   - Buttons:
     - “Add PDF(s)” (file chooser, multiple select).
     - “Re-index selected”.
   - Columns:
     - Title, file name, year, status (not indexed / indexed / error).
   - Progress bar at the bottom while ingestion or indexing runs in the background.

3. **PubMed Monitor View**
   - Shows saved PubMed query string for topic.
   - Buttons:
     - “Dry run search” → show list of candidate PMIDs and mark which are unseen.
     - “Fetch new abstracts” → retrieves and stores new ones; updates `pubmed_seen` and `abstract`.
   - Table:
     - PMID, title, journal, pub date, new/seen flag.

4. **Evaluate Abstracts View**
   - Table of stored abstracts for the topic (from `abstract`):
     - Columns: PMID, title, journal, pub date, evaluation status.
   - Controls:
     - LLM model selection (drop-down).
     - Slider or fields for `α`, `K_sem`, `K_lex`, `K_ctx`.
   - Buttons:
     - “Evaluate selected”.
     - “Evaluate all un-evaluated”.
   - Progress area:
     - Logs evaluation progress with a small console panel.

5. **Results View**
   - Table of evaluation runs/results:
     - Columns: Date, PMID, title, novelty label, novelty score.
   - Filters:
     - By label (novel / uncertain / not_novel).
     - By score threshold.
     - By topic.
   - Detail pane:
     - Shows abstract, selected context chunks, and the rationale.
   - Export:
     - “Export results to CSV”.
     - “Export single result to JSON.”

6. **Settings View**
   - OpenAI configuration:
     - API key (stored securely in a local config file, not in DB).
     - Default model.
   - Paths:
     - Data directory.
     - Logs directory.
   - Retrieval defaults:
     - Chunk size, stride.
     - Default α, K values.
   - Network:
     - Proxy, timeout values.

### JavaFX Technical Considerations

- Use `javafx.concurrent.Task` and `Service` for background operations (PDF ingestion, embedding generation, Lucene indexing, evaluation).
- Bind progress to UI elements (`ProgressBar`, `Label`).
- Ensure thread-safety: all UI updates happen on the JavaFX Application Thread (`Platform.runLater` when needed).
- Use CSS for styling (e.g. `zoltar.css`).

---

## 7) Packaging, Modules, jlink & jpackage

### Module Graph

At minimum:

- `ca.zoltar.app`
  - `requires javafx.controls;`
  - `requires javafx.fxml;`
  - `requires ca.zoltar.gui;`

- `ca.zoltar.gui`
  - `requires javafx.controls;`
  - `requires javafx.fxml;`
  - `requires ca.zoltar.core;`
  - `opens ca.zoltar.gui.controller to javafx.fxml;`
  - `exports ca.zoltar.gui;`

- `ca.zoltar.core`
  - `requires java.sql;`
  - `requires java.net.http;`
  - `requires ca.zoltar.db;`
  - `requires ca.zoltar.search;`
  - `requires ca.zoltar.pubmed;`
  - `requires ca.zoltar.util;`
  - `exports ca.zoltar.core.service;`

- `ca.zoltar.db`
  - `requires java.sql;`
  - `exports ca.zoltar.db;`

- `ca.zoltar.search`
  - `requires org.apache.lucene.core;` (automatic or named module depending on Lucene version)
  - `exports ca.zoltar.search;`

- `ca.zoltar.pubmed`
  - `requires java.net.http;`
  - `exports ca.zoltar.pubmed;`

- `ca.zoltar.util`
  - `exports ca.zoltar.util;`

Keep each module’s `requires` list minimal to allow aggressive `jlink` trimming.

### Build Tool: Maven (Multi-module)

Top-level `pom.xml` with modules:

- `zoltar-app` → `ca.zoltar.app`
- `zoltar-gui` → `ca.zoltar.gui`
- `zoltar-core` → `ca.zoltar.core`
- `zoltar-db` → `ca.zoltar.db`
- `zoltar-search` → `ca.zoltar.search`
- `zoltar-pubmed` → `ca.zoltar.pubmed`
- `zoltar-util` → `ca.zoltar.util`

Integrate:

- `javafx-maven-plugin` for running the app.
- `maven-jlink-plugin` or `moditect-maven-plugin` for creating a custom runtime image.
- Scripts or Maven profiles to call `jpackage` for each platform.

### jlink (Custom Runtime Image)

- Use `jlink` to create a runtime containing:
  - Required Java base modules (e.g. `java.base`, `java.sql`, `java.logging`, `java.net.http`).
  - JavaFX modules (`javafx.controls`, `javafx.fxml`, `javafx.graphics`, etc. as appropriate).
- Exclude unnecessary locales and debug info to shrink size.

Example (conceptual):

```bash
jlink   --module-path "$JAVA_HOME/jmods:target/modules"   --add-modules ca.zoltar.app   --strip-debug   --no-header-files   --no-man-pages   --compress=2   --output target/zoltar-runtime
```

### jpackage (Native Installers)

- Use `jpackage` with the custom runtime image:

```bash
jpackage   --name Zoltar   --app-version 1.0.0   --type dmg \  # or pkg on macOS; msi/exe on Windows
  --input target/zoltar-runtime   --main-jar zoltar-app.jar   --main-class ca.zoltar.app.MainApp   --icon assets/zoltar.icns
```

Platform specifics:

- **macOS**:
  - Output `.app` bundle inside `.dmg` or `.pkg`.
  - Code signing and notarization steps can be added later.

- **Windows**:
  - Output `.msi` or `.exe`.
  - Include start menu entry and desktop shortcut where appropriate.

The goal is that the user can install Zoltar on macOS or Windows without separately installing Java.

---

## 8) Testing Strategy

### Unit Tests (JUnit 5)

- **Text & Chunking**:
  - Split/cleaning logic.
  - Chunking boundaries and overlap correctness.
- **SQLite DAOs**:
  - CRUD for `topic`, `document`, `chunk`, `embedding`, `abstract`, etc.
  - Unique constraints and error handling.
- **Embedding Logic**:
  - Vector normalization and cosine similarity.
  - Serialization/deserialization of embedding BLOBs.
- **Lucene**:
  - Index creation, query, scoring behavior.
- **Hybrid Retrieval**:
  - Merging and score normalization.
  - Edge cases (empty results, all from one side, etc.).

### Integration Tests

- In-memory or temporary SQLite DB.
- End-to-end pipeline (mocked OpenAI and PubMed):
  1. Create topic.
  2. Ingest synthetic documents.
  3. Chunk, index, and embed.
  4. Insert synthetic abstract.
  5. Run retrieval and evaluation with mocked LLM.
  6. Confirm DB records and output JSON.

### GUI Tests (Optional)

- Use TestFX or similar for smoke tests:
  - Launch app.
  - Navigate between views.
  - Trigger an ingestion or evaluation using test data.

### Performance / Load Tests

- Measure:
  - Retrieval latency with thousands of chunks.
  - Memory usage during embedding and Lucene search.

---

## 9) Telemetry, Logging & Reproducibility

- Use SLF4J + Logback (or similar) for structured logging.
- Log:
  - Each ingestion batch.
  - Each PubMed query.
  - Each evaluation run (including model, parameters).
- Optionally, store a **JSONL** log of evaluation inputs/outputs in an `evaluations.log.jsonl` file.
- Ensure reproducibility:
  - Save the retrieval parameters used for each run in `params_json`.
  - Maintain version information in the DB (schema version, app version).

---

## 10) Risks & Mitigations (Java-Specific)

1. **Packaging Complexity**
   - Risk: `jlink` / `jpackage` configuration can be fiddly, especially with JavaFX and Lucene.
   - Mitigation:
     - Start from known working examples (e.g., OpenJFX samples).
     - Gradually add modules and libraries, ensuring each step builds and runs.

2. **Runtime Image Size**
   - Risk: Bundled runtime images might still be large.
   - Mitigation:
     - Aggressive `jlink` trimming (strip debug, no man pages, compress).
     - Minimal module `requires` usage in `module-info.java`.

3. **Lucene Index Growth**
   - Risk: Index directories may grow large with many topics and documents.
   - Mitigation:
     - Allow topic pruning and document removal.
     - Provide a “compact index” or “rebuild index” command.

4. **OpenAI Dependency**
   - Risk: Network failures, API changes, or cost concerns.
   - Mitigation:
     - Robust error handling and retries.
     - Configurable rate limiting.
     - Design `EvaluationService` so that alternative backends could be introduced later.

---

## 11) Implementation Phases & Milestones

These phases map 1:1 to concrete work units that can be implemented and tested independently.

### Phase 1 — Project Scaffold & Modules (`ca.zoltar.*`)

**Goals**

- Create multi-module Maven project with all `ca.zoltar.*` modules.
- Set up JavaFX entry point (`ca.zoltar.app.MainApp`).
- Implement configuration load/save (JSON config file).
- Initialize SQLite schema at first run.

**Acceptance Criteria**

- `mvn javafx:run` launches the app with an empty main window titled “Zoltar”.
- Application data directory is created (e.g., `~/.zoltar-java/`).
- SQLite file and initial tables exist.

---

### Phase 2 — PDF Ingestion & Chunking (Completed)

**Goals**

- Implement `PdfIngestionService` in `ca.zoltar.core`.
- Implement text cleaning and chunking utilities in `ca.zoltar.util`.
- Implement DAOs for `document` and `chunk`.

**Acceptance Criteria**

- User can add PDFs for a topic from a simple debug GUI or CLI.
- Chunks appear in the DB with sensible sizes and indices.
- Re-ingesting the same PDF does not create duplicates (based on SHA-256).

---

### Phase 3 — Embeddings & Lucene Indexing

**Goals**

- Implement OpenAI embedding client (configurable via settings).
- Implement `EmbeddingService` to generate/store chunk embeddings.
- Implement Lucene index builder and query service in `ca.zoltar.search`.

**Acceptance Criteria**

- For a topic with ingested PDFs:
  - Embeddings can be generated and stored for all chunks.
  - A Lucene index is created and search queries return plausible results.

---

### Phase 4 — PubMed Monitor & Abstract Storage

**Goals**

- Implement `PubMedClient` using E-utilities (search + fetch).
- Implement `pubmed_seen` tracking and `abstract` persistence.
- Provide a UI to run PubMed searches and store new abstracts.

**Acceptance Criteria**

- Given a PubMed query:
  - App lists candidate PMIDs and identifies new ones.
  - User can fetch and store new abstracts, which appear in the DB and UI.

---

### Phase 5 — Hybrid Retrieval Service

**Goals**

- Implement `HybridRetrievalService` in `ca.zoltar.core`.
- Combine semantic and lexical scores as described.
- Allow tunable parameters (α, `K_sem`, `K_lex`, `K_ctx`).

**Acceptance Criteria**

- For each stored abstract, the service returns a ranked list of context chunks with hybrid scores.
- Behavior is stable and parameter changes behave as expected in tests.

---

### Phase 6 — LLM Evaluation Pipeline

**Goals**

- Implement prompt builder and OpenAI chat client for novelty evaluation.
- Implement `EvaluationService` that orchestrates retrieval + evaluation + persistence.
- Add robust error handling and JSON validation.

**Acceptance Criteria**

- At least one real PubMed abstract can be evaluated end-to-end.
- Output is parsed into `NoveltyEvaluationResult` and saved into `evaluation_run` and `evaluation_result`.

---

### Phase 7 — JavaFX GUI Completion

**Goals**

- Implement all main views:
  - Topics, Library, PubMed Monitor, Evaluate, Results, Settings.
- Wire up background tasks for all long-running operations.
- Provide basic error messages and logging view.

**Acceptance Criteria**

- A non-technical user can:
  1. Create a topic.
  2. Add and index PDFs.
  3. Fetch new PubMed abstracts.
  4. Run novelty evaluations.
  5. Review and export results.

---

### Phase 8 — Packaging for macOS and Windows

**Goals**

- Configure `jlink` to build a trimmed runtime for Zoltar.
- Configure `jpackage` to produce:
  - `.dmg` or `.pkg` for macOS.
  - `.msi` / `.exe` for Windows.
- Provide build scripts or Maven profiles to automate these.

**Acceptance Criteria**

- On macOS:
  - User installs from `.dmg` or `.pkg`.
  - A “Zoltar” app icon appears and launches successfully.
- On Windows:
  - User installs from `.msi` or `.exe`.
  - A Start Menu entry and optional desktop shortcut are created.
  - App runs without a system-wide JDK installed.

---

### Phase 9 — Refinements & Future Directions (Optional)

Potential enhancements once the core system is working:

- **OCR integration**:
  - Use Tesseract or another OCR engine for image-based PDFs.
- **Advanced ranking**:
  - Implement MMR or other more sophisticated re-ranking strategies.
  - Use document-level and section-level metadata (e.g. methods vs. results).
- **Plugin architecture**:
  - Allow alternative vector stores (e.g., local approximate nearest neighbor libraries).
  - Allow alternative LLM providers.
- **Visualization**:
  - Add graphs or timelines showing novelty scores over time.
- **Multi-user / Team mode**:
  - Synchronize a shared DB across a local network or via a sync service (future).

---

This markdown file defines the full **Java/JavaFX Zoltar plan** with modules under `ca.zoltar.*`, ready to guide implementation and tooling (including code generation agents) through clearly defined phases.
