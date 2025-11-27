# Zoltar

A cross-platform desktop application for assessing the novelty of PubMed abstracts using hybrid retrieval (semantic + lexical search) and LLM evaluation.

## Overview

Zoltar ingests topic-specific PDFs (published articles, reports, etc.) and uses them as context to evaluate whether new PubMed abstracts represent **novel contributions** or **incremental work** relative to existing literature. It combines:

- **Semantic search** via OpenAI embeddings
- **Lexical search** via Apache Lucene with BM25 similarity
- **LLM evaluation** via OpenAI GPT for novelty assessment

## Key Features

- **Topic-based organization**: Manage multiple research topics, each with its own corpus and PubMed query
- **Topic editing & cleanup**: Rename topics, update PubMed queries, and delete topics (with document/abstract counts) directly from the GUI
- **PDF ingestion**: Extract and chunk text from PDFs for indexing
- **Hybrid retrieval**: Combine dense semantic search and sparse lexical search for optimal context selection
- **PubMed monitoring**: Automatically discover new abstracts matching topic queries
- **Novelty evaluation**: Use LLM to assess abstract novelty relative to corpus
- **Local storage**: All data stored in local SQLite database
- **Cross-platform**: Built for macOS and Windows with JavaFX

## Technology Stack

- **Language**: Java 25
- **GUI Framework**: JavaFX 21.0.1
- **Database**: SQLite 3.43.0.0
- **Search Engine**: Apache Lucene 9.8.0
- **PDF Processing**: Apache PDFBox 3.0.0
- **Embeddings**: OpenAI API (text-embedding-3-small)
- **LLM**: OpenAI API (GPT models)
- **Build Tool**: Apache Maven 3.9+
- **Module System**: Java Platform Module System (JPMS)

## Architecture

```
┌──────────────────────┐
│  JavaFX Desktop GUI  │
│   (ca.zoltar.gui)    │
└──────────┬───────────┘
           │
┌──────────▼───────────┐
│   Core Services      │
│  (ca.zoltar.core)    │
│                      │
│  - PDF Ingestion     │
│  - Hybrid Retrieval  │
│  - LLM Evaluation    │
└──────────┬───────────┘
           │
┌──────────▼───────────┬──────────────────┐
│  Storage & Indexing  │  External APIs   │
│                      │                  │
│  - SQLite Database   │  - PubMed        │
│    (ca.zoltar.db)    │    E-utilities   │
│                      │    (ca.zoltar.   │
│  - Lucene Index      │     pubmed)      │
│    (ca.zoltar.       │                  │
│     search)          │  - OpenAI API    │
└──────────────────────┴──────────────────┘
```

## Modules

The project uses JPMS (Java Platform Module System) with the following modules:

- **ca.zoltar.app** - Main application launcher
- **ca.zoltar.gui** - JavaFX views, controllers, and FXML
- **ca.zoltar.core** - Business logic and service layer
- **ca.zoltar.db** - SQLite database access and DAOs
- **ca.zoltar.search** - Lucene indexing and search
- **ca.zoltar.pubmed** - PubMed E-utilities client
- **ca.zoltar.util** - Utilities and configuration

## Project Structure

```
zoltarj/
├── pom.xml                    # Parent POM
├── zoltar-app/               # Application launcher module
├── zoltar-core/              # Core business logic
│   └── src/main/java/ca/zoltar/core/
│       ├── service/          # Services (PDF, indexing, retrieval, evaluation)
│       └── module-info.java
├── zoltar-db/                # Database layer
│   └── src/main/java/ca/zoltar/db/
│       ├── DatabaseManager.java
│       ├── *Dao.java         # Data Access Objects
│       └── module-info.java
├── zoltar-search/            # Lucene search
├── zoltar-pubmed/            # PubMed client
├── zoltar-gui/               # JavaFX GUI (future)
└── zoltar-util/              # Utilities
```

## Implementation Status

### Completed Phases

- ✅ **Phase 1**: Topic & Document Management
- ✅ **Phase 2**: PDF Ingestion & Chunking
- ✅ **Phase 3**: Embeddings & Lucene Indexing
- ✅ **Phase 4**: PubMed Monitor & Abstract Storage
- ✅ **Phase 5**: Hybrid Retrieval Service
- ✅ **Phase 6**: LLM Evaluation Pipeline
- ✅ **Phase 7**: JavaFX GUI Foundation (7 views, navigation, settings)
- ✅ **Phase 8**: Packaging & Distribution (jlink, jpackage, CI/CD)

### In Progress

- 🚧 **Phase 7**: Full CRUD implementation and service integration

## Prerequisites

- **Java 25** or later
- **Maven 3.9+**
- **OpenAI API key** (required for embeddings and LLM evaluation)

## Configuration

Create `~/.zoltar-java/config.json`:

```json
{
  "openai": {
    "apiKey": "sk-your-api-key-here",
    "embeddingModel": "text-embedding-3-small",
    "chatModel": "gpt-4"
  },
  "database": {
    "path": "~/.zoltar-java/zoltar.db"
  },
  "indexing": {
    "chunkSize": 512,
    "chunkOverlap": 128
  }
}
```

### Development tips

- Set the system property `-Dzoltar.db.path=/absolute/path/to/zoltar.db` to override the default SQLite location. This is useful for test runs and isolates developer experiments from production data.

## Building

```bash
# Compile all modules
mvn clean compile

# Run tests
mvn test

# Package (install to local Maven repo)
mvn clean install
```

## Running Demos

Each implementation phase includes a demo class:

```bash
# Phase 1: Topic Management
mvn exec:java -pl zoltar-core -Dexec.mainClass="ca.zoltar.core.service.Phase1Demo"

# Phase 2: PDF Ingestion
mvn exec:java -pl zoltar-core -Dexec.mainClass="ca.zoltar.core.service.Phase2Demo"

# Phase 3: Embeddings & Indexing
mvn exec:java -pl zoltar-core -Dexec.mainClass="ca.zoltar.core.service.Phase3Demo"

# Phase 4: PubMed Monitoring
mvn exec:java -pl zoltar-core -Dexec.mainClass="ca.zoltar.core.service.Phase4Demo"

# Phase 5: Hybrid Retrieval
mvn exec:java -pl zoltar-core -Dexec.mainClass="ca.zoltar.core.service.Phase5Demo"

# Phase 6: LLM Evaluation Pipeline
mvn exec:java -pl zoltar-core -Dexec.mainClass="ca.zoltar.core.service.Phase6Demo"

# Phase 7: JavaFX GUI Application
cd zoltar-gui && mvn javafx:run
# Or use the launcher script:
./run-gui.sh
```

## Packaging and Distribution

See [PACKAGING.md](PACKAGING.md) for detailed instructions on creating native installers.

**Quick start:**

```bash
# macOS - Create DMG installer
./build-runtime.sh
./package-macos.sh dmg

# Windows - Create MSI installer
build-runtime-windows.sh
package-windows.bat msi
```

The installers are self-contained and include everything needed to run Zoltar without requiring a separate JDK installation.

## Database Schema

The SQLite database includes tables for:

- **topic** - Research topics
- **document** - Ingested PDFs with metadata
- **chunk** - Text chunks extracted from documents
- **embedding** - Vector embeddings for chunks
- **abstract** - PubMed abstracts
- **pubmed_seen** - Tracking of processed PMIDs
- **evaluation_run** - LLM evaluation runs
- **evaluation_result** - Novelty assessment results

## Hybrid Retrieval Algorithm

1. **Query Embedding**: Generate embedding vector for query text (e.g., PubMed abstract)
2. **Semantic Retrieval**: Calculate cosine similarity with all chunk embeddings → top K_sem candidates
3. **Lexical Retrieval**: Use Lucene BM25 to find top K_lex candidates
4. **Score Normalization**: Min-max normalization of both score types to [0,1]
5. **Hybrid Scoring**: `H = α * semantic_score + (1-α) * lexical_score` (default α=0.6)
6. **Ranking**: Sort by hybrid score, return top K_ctx chunks

## LLM Evaluation Pipeline

Phase 6 implements end-to-end novelty evaluation using GPT:

1. **Abstract Retrieval**: Load PubMed abstract from database
2. **Context Retrieval**: Use hybrid retrieval to find relevant chunks from topic corpus
3. **Prompt Construction**: 
   - System prompt: Define expert research assistant role
   - User prompt: Include topic, abstract text, and retrieved context chunks
4. **LLM Evaluation**: Call OpenAI GPT with JSON response format
5. **Response Parsing**: Extract novelty_label, novelty_score (0-10), rationale, and supporting_evidence
6. **Validation**: Ensure all fields present and values valid
7. **Persistence**: Save evaluation run metadata and results to database

### Novelty Classification

- **novel**: Represents significant new contribution (score 7-10)
- **uncertain**: Difficult to determine novelty (score 4-6)
- **not_novel**: Incremental work or duplicate (score 0-3)

### Evaluation Output

```json
{
  "novelty_label": "novel",
  "novelty_score": 8,
  "rationale": "This abstract presents a novel CRISPR-based approach...",
  "supporting_evidence": [
    {
      "chunk_id": 42,
      "explanation": "This chunk describes standard methods, highlighting novelty"
    }
  ]
}
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Third-Party Licenses

See [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) for licenses of all dependencies.

## Contributing

This is a research prototype. Contributions, suggestions, and bug reports are welcome.

## Acknowledgments

- OpenAI for embeddings and LLM APIs
- Apache Lucene for lexical search
- Apache PDFBox for PDF processing
- PubMed/NCBI for E-utilities API
