CREATE TABLE IF NOT EXISTS "{{data}}_6_dataset_workshop"
(
    "partition"            INTEGER NOT NULL,
    "extractors_partition" INTEGER NOT NULL,
    "category"             STRING    NOT NULL,
    "dataset"              BLOB    NOT NULL,
    FOREIGN KEY ("extractors_partition") REFERENCES "{{data}}_5_extractors" ("partition")
)
