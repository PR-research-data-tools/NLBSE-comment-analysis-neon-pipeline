CREATE TABLE "{{data}}_7_ranked_heuristic"
(
    "id"       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    "category"             TEXT    NOT NULL,
    "heuristics"           TEXT    NOT NULL,
    "importance_factor"    NUMERIC NOT NULL
)