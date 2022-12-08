CREATE TABLE IF NOT EXISTS "{{data}}_4_sentence_partition"
(
    "comment_sentence_id" INTEGER NOT NULL,
    "partition"           INTEGER NOT NULL,
    FOREIGN KEY ("comment_sentence_id") REFERENCES "{{data}}_2_sentence" ("id"),
    PRIMARY KEY ("comment_sentence_id")
)
