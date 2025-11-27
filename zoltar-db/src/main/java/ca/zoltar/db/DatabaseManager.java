package ca.zoltar.db;

import ca.zoltar.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DB_FILE_NAME = "zoltar.db";
    private static final String DB_OVERRIDE_PROPERTY = "zoltar.db.path";
    
    private static DatabaseManager instance;
    private final String connectionString;

    private DatabaseManager() {
        Path dbPath = resolveDatabasePath();
        this.connectionString = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionString);
    }

    public static synchronized void resetForTests() {
        instance = null;
    }

    private Path resolveDatabasePath() {
        String overridePath = System.getProperty(DB_OVERRIDE_PROPERTY);
        if (overridePath != null && !overridePath.isBlank()) {
            Path override = Path.of(overridePath).toAbsolutePath();
            Path parent = override.getParent();
            if (parent != null && !Files.exists(parent)) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create override database directory", e);
                }
            }
            logger.info("Using overridden database path: {}", override);
            return override;
        }

        Path appDir = ConfigManager.getInstance().getAppDir();
        Path dbPath = appDir.resolve(DB_FILE_NAME);
        return dbPath;
    }

    private void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Enable foreign keys
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Create tables
            createTables(stmt);
            
            logger.info("Database initialized at {}", connectionString);
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void createTables(Statement stmt) throws SQLException {
        // 1. Topics
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS topic (
                id          INTEGER PRIMARY KEY,
                name        TEXT NOT NULL,
                query_string TEXT,
                notes       TEXT,
                created_at  TEXT NOT NULL,
                updated_at  TEXT NOT NULL
            );
        """);

        // 2. Documents
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS document (
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
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_document_topic ON document(topic_id);");

        // 3. Chunks
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS chunk (
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
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunk_topic ON chunk(topic_id);");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunk_document ON chunk(document_id);");

        // 4. Embeddings
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS embedding (
                id        INTEGER PRIMARY KEY,
                chunk_id  INTEGER NOT NULL,
                model     TEXT NOT NULL,
                vector    BLOB NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (chunk_id) REFERENCES chunk(id)
            );
        """);
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_embedding_chunk_model ON embedding(chunk_id, model);");

        // 5. PubMed Seen
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS pubmed_seen (
                id           INTEGER PRIMARY KEY,
                topic_id     INTEGER NOT NULL,
                pmid         TEXT NOT NULL,
                first_seen_at TEXT NOT NULL,
                UNIQUE(topic_id, pmid)
            );
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_pubmed_seen_topic ON pubmed_seen(topic_id);");

        // 6. Abstracts
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS abstract (
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
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_abstract_topic ON abstract(topic_id);");

        // 7. Evaluation Runs & Results
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS evaluation_run (
                id          INTEGER PRIMARY KEY,
                topic_id    INTEGER NOT NULL,
                pmid        TEXT NOT NULL,
                created_at  TEXT NOT NULL,
                llm_model   TEXT NOT NULL,
                params_json TEXT NOT NULL,
                FOREIGN KEY (topic_id) REFERENCES topic(id)
            );
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS evaluation_result (
                id                    INTEGER PRIMARY KEY,
                run_id                INTEGER NOT NULL,
                novelty_label         TEXT NOT NULL,
                novelty_score         INTEGER NOT NULL,
                rationale             TEXT,
                used_chunk_ids_json   TEXT,
                hybrid_scores_json    TEXT,
                FOREIGN KEY (run_id) REFERENCES evaluation_run(id)
            );
        """);
    }
}
