CREATE TABLE "{{data}}_2_sentence"
(
    "id"       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "class"    TEXT    NOT NULL,
    "category" TEXT    NOT NULL,
    "sentence" TEXT    NOT NULL,
    FOREIGN KEY ("class") REFERENCES "{{data}}_0_raw" ("class")
)
