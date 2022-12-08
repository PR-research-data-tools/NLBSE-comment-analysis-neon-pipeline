CREATE TABLE IF NOT EXISTS "{{data}}_5_sentences_partitions"
(
    "class"    TEXT    NOT NULL,
    "comment_sentence"          TEXT    NOT NULL,
    "partition"           INTEGER NOT NULL,
    "category"                  TEXT    NOT NULL,
    FOREIGN KEY ("class") REFERENCES "{{data}}_0_raw" ("class")
)