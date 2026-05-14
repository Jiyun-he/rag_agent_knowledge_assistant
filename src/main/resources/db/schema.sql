CREATE TABLE IF NOT EXISTS kb_space (
                                        id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                        name VARCHAR(100) NOT NULL COMMENT 'Knowledge space name',
    description VARCHAR(500) DEFAULT NULL COMMENT 'Knowledge space description',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1 active, 0 deleted',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    UNIQUE KEY uk_kb_space_name (name),
    KEY idx_kb_space_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge space table';

CREATE TABLE IF NOT EXISTS kb_document (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                           space_id BIGINT NOT NULL COMMENT 'Knowledge space ID',
                                           title VARCHAR(255) NOT NULL COMMENT 'Document title',
    content TEXT NOT NULL COMMENT 'Document content',
    source_type VARCHAR(64) NOT NULL DEFAULT 'MANUAL' COMMENT 'Source type',
    source_uri VARCHAR(512) DEFAULT NULL COMMENT 'Source URI',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1 active, 0 deleted',
    chunk_count INT NOT NULL DEFAULT 0 COMMENT 'Chunk count',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    KEY idx_kb_document_space_status (space_id, status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge document table';

CREATE TABLE IF NOT EXISTS kb_chunk (
                                        id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                        document_id BIGINT NOT NULL COMMENT 'Document ID',
                                        space_id BIGINT NOT NULL COMMENT 'Knowledge space ID',
                                        chunk_index INT NOT NULL COMMENT 'Chunk index in document',
                                        content TEXT NOT NULL COMMENT 'Chunk content',
                                        char_count INT NOT NULL DEFAULT 0 COMMENT 'Character count',
                                        embedding_status TINYINT NOT NULL DEFAULT 0 COMMENT 'Embedding status: 0 pending, 1 done, 2 failed',
                                        vector_id VARCHAR(128) DEFAULT NULL COMMENT 'Vector database point ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1 active, 0 deleted',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    KEY idx_kb_chunk_document_status (document_id, status),
    KEY idx_kb_chunk_space_status (space_id, status),
    KEY idx_kb_chunk_embedding_status (embedding_status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge chunk table';

CREATE TABLE IF NOT EXISTS kb_embedding_task (
                                                 id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                 document_id BIGINT NOT NULL,
                                                 space_id BIGINT NOT NULL,
                                                 task_type VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    total_chunk_count INT NOT NULL DEFAULT 0,
    pending_chunk_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) DEFAULT NULL,
    started_at DATETIME DEFAULT NULL,
    finished_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_document_id (document_id),
    INDEX idx_space_id (space_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
    );

CREATE TABLE IF NOT EXISTS qa_session (
                                          id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                          space_id BIGINT NOT NULL COMMENT 'Knowledge space ID',
                                          title VARCHAR(255) NOT NULL COMMENT 'Session title',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1 active, 0 deleted',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    KEY idx_qa_session_space_status (space_id, status),
    KEY idx_qa_session_updated_at (updated_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='QA session table';


CREATE TABLE IF NOT EXISTS qa_message (
                                          id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                          session_id BIGINT NOT NULL COMMENT 'QA session ID',
                                          space_id BIGINT NOT NULL COMMENT 'Knowledge space ID',
                                          role VARCHAR(32) NOT NULL COMMENT 'Message role: user or assistant',
    content LONGTEXT NOT NULL COMMENT 'Message content',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    KEY idx_qa_message_session_id (session_id),
    KEY idx_qa_message_space_id (space_id),
    KEY idx_qa_message_created_at (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='QA message table';


CREATE TABLE IF NOT EXISTS qa_reference (
                                            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                            message_id BIGINT NOT NULL COMMENT 'Assistant message ID',
                                            session_id BIGINT NOT NULL COMMENT 'QA session ID',
                                            reference_index INT NOT NULL COMMENT 'Reference index in answer',
                                            chunk_id BIGINT NOT NULL COMMENT 'Referenced chunk ID',
                                            document_id BIGINT NOT NULL COMMENT 'Referenced document ID',
                                            space_id BIGINT NOT NULL COMMENT 'Knowledge space ID',
                                            chunk_index INT DEFAULT NULL COMMENT 'Chunk index in document',
                                            score DOUBLE DEFAULT NULL COMMENT 'Retrieval score',
                                            content_preview VARCHAR(1000) DEFAULT NULL COMMENT 'Referenced content preview',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    KEY idx_qa_reference_message_id (message_id),
    KEY idx_qa_reference_session_id (session_id),
    KEY idx_qa_reference_chunk_id (chunk_id),
    KEY idx_qa_reference_space_id (space_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='QA reference table';


CREATE TABLE IF NOT EXISTS eval_dataset (
                                            id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                            space_id BIGINT NOT NULL COMMENT 'Knowledge space ID',
                                            name VARCHAR(255) NOT NULL COMMENT 'Evaluation dataset name',
    description VARCHAR(1000) DEFAULT NULL COMMENT 'Evaluation dataset description',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1 active, 0 deleted',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    KEY idx_eval_dataset_space_status (space_id, status),
    KEY idx_eval_dataset_status_created_at (status, created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG evaluation dataset table';


CREATE TABLE IF NOT EXISTS eval_case (
                                         id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                         dataset_id BIGINT NOT NULL COMMENT 'Evaluation dataset ID',
                                         question TEXT NOT NULL COMMENT 'Evaluation question',
                                         expected_answer TEXT DEFAULT NULL COMMENT 'Expected answer',
                                         expected_chunk_ids TEXT DEFAULT NULL COMMENT 'Expected chunk ID list in JSON string',
                                         expected_keywords TEXT DEFAULT NULL COMMENT 'Expected keyword list in JSON string',
                                         difficulty VARCHAR(32) DEFAULT NULL COMMENT 'Case difficulty',
    status TINYINT NOT NULL DEFAULT 1 COMMENT 'Status: 1 active, 0 deleted',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    KEY idx_eval_case_dataset_status (dataset_id, status),
    KEY idx_eval_case_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG evaluation case table';


CREATE TABLE IF NOT EXISTS eval_run (
                                        id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                        dataset_id BIGINT NOT NULL COMMENT 'Evaluation dataset ID',
                                        run_name VARCHAR(255) NOT NULL COMMENT 'Evaluation run name',
    retrieval_mode VARCHAR(64) NOT NULL COMMENT 'Retrieval mode',
    top_k INT NOT NULL DEFAULT 5 COMMENT 'Top K',
    candidate_k INT NOT NULL DEFAULT 20 COMMENT 'Candidate K',
    vector_weight DOUBLE DEFAULT NULL COMMENT 'Vector score weight',
    keyword_weight DOUBLE DEFAULT NULL COMMENT 'Keyword score weight',
    enable_answer_generation TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether to generate answer: 1 yes, 0 no',
    status TINYINT NOT NULL DEFAULT 0 COMMENT 'Run status: 0 pending, 1 running, 2 success, 3 failed, 4 partial success',
    total_case_count INT NOT NULL DEFAULT 0 COMMENT 'Total case count',
    success_case_count INT NOT NULL DEFAULT 0 COMMENT 'Success case count',
    failed_case_count INT NOT NULL DEFAULT 0 COMMENT 'Failed case count',
    avg_recall_at_k DOUBLE DEFAULT NULL COMMENT 'Average Recall@K',
    hit_rate_at_k DOUBLE DEFAULT NULL COMMENT 'Hit Rate@K',
    mrr DOUBLE DEFAULT NULL COMMENT 'Mean Reciprocal Rank',
    avg_answer_keyword_hit DOUBLE DEFAULT NULL COMMENT 'Average answer keyword hit',
    citation_correct_rate DOUBLE DEFAULT NULL COMMENT 'Citation correct rate',
    avg_latency_ms DOUBLE DEFAULT NULL COMMENT 'Average latency in milliseconds',
    error_message TEXT DEFAULT NULL COMMENT 'Error message',
    started_at DATETIME DEFAULT NULL COMMENT 'Started time',
    ended_at DATETIME DEFAULT NULL COMMENT 'Ended time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    KEY idx_eval_run_dataset_id (dataset_id),
    KEY idx_eval_run_status (status),
    KEY idx_eval_run_created_at (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG evaluation run table';


CREATE TABLE IF NOT EXISTS eval_case_result (
                                                id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Primary key ID',
                                                run_id BIGINT NOT NULL COMMENT 'Evaluation run ID',
                                                case_id BIGINT NOT NULL COMMENT 'Evaluation case ID',
                                                question TEXT NOT NULL COMMENT 'Evaluation question',
                                                answer LONGTEXT DEFAULT NULL COMMENT 'Generated answer',
                                                retrieved_chunk_ids TEXT DEFAULT NULL COMMENT 'Retrieved chunk ID list in JSON string',
                                                reference_chunk_ids TEXT DEFAULT NULL COMMENT 'Reference chunk ID list in JSON string',
                                                recall_at_k DOUBLE DEFAULT NULL COMMENT 'Recall@K',
                                                hit_at_k TINYINT DEFAULT NULL COMMENT 'Hit@K: 1 hit, 0 miss',
                                                mrr DOUBLE DEFAULT NULL COMMENT 'Reciprocal rank',
                                                answer_keyword_hit DOUBLE DEFAULT NULL COMMENT 'Answer keyword hit score',
                                                citation_correct TINYINT DEFAULT NULL COMMENT 'Citation correct: 1 yes, 0 no',
                                                grounded TINYINT DEFAULT NULL COMMENT 'Grounded: 1 yes, 0 no',
                                                latency_ms BIGINT DEFAULT NULL COMMENT 'Latency in milliseconds',
                                                error_message TEXT DEFAULT NULL COMMENT 'Error message',
                                                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
                                                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
                                                KEY idx_eval_case_result_run_id (run_id),
    KEY idx_eval_case_result_case_id (case_id),
    KEY idx_eval_case_result_run_case (run_id, case_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG evaluation case result table';