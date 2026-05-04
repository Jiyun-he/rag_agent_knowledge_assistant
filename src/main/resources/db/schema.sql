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