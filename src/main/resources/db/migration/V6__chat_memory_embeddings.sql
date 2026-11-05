-- Original text stays in memory_entries. One durable embedding task per ADDON.
CREATE TABLE memory_embeddings (
 entry_id varchar(255) NOT NULL,
 source_hash varchar(64) NOT NULL,
 model varchar(80) NOT NULL,
 status varchar(16) NOT NULL,
 dimensions int NOT NULL,
 attempts int NOT NULL,
 vectors mediumblob,
 next_attempt_at datetime(6) NOT NULL,
 updated_at datetime(6) NOT NULL,
 version bigint NOT NULL,
 PRIMARY KEY (entry_id),
 KEY idx_embedding_work (status, next_attempt_at),
 CONSTRAINT fk_embedding_entry FOREIGN KEY (entry_id) REFERENCES memory_entries(id) ON DELETE CASCADE
) ENGINE=InnoDB;
