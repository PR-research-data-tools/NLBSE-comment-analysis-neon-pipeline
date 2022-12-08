CREATE TABLE IF NOT EXISTS "{{data}}_5_category_heuristic_mapping"
(
    "id"       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "category"             TEXT    NOT NULL,
    "heuristics"           TEXT    NOT NULL
)