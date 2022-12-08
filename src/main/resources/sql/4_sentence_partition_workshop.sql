CREATE TABLE IF NOT EXISTS "{{data}}_4_sentence_partition_workshop"
(
    "comment_sentence_id" INTEGER NOT NULL,
    "partition"           INTEGER NOT NULL,
    "category"            TEXT NOT NULL,
    "instance_type"       TEXT NOT NULL,
    FOREIGN KEY ("comment_sentence_id") REFERENCES "{{data}}_2_sentence" ("id")
)
