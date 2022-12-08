CREATE TABLE IF NOT EXISTS "{{data}}_5_sentence_heuristic_mapping"
(
    "comment_sentence_id"       INTEGER NOT NULL,
    "comment_sentence"          TEXT    NOT NULL,
    "heuristics"                TEXT,
    "category"                  TEXT    NOT NULL,
    FOREIGN KEY ("comment_sentence_id") REFERENCES "{{data}}_2_sentence" ("id"),
    PRIMARY KEY ("comment_sentence_id", "category")
)