CREATE TABLE IF NOT EXISTS "{{data}}_6_dataset"
(
    "partition"            INTEGER NOT NULL,
    "extractors_partition" INTEGER NOT NULL,
    "dataset"              BLOB    NOT NULL,
    FOREIGN KEY ("extractors_partition") REFERENCES "{{data}}_5_extractors" ("partition"),
    PRIMARY KEY ("partition", "extractors_partition")
)
