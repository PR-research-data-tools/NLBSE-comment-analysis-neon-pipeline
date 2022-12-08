CREATE TABLE IF NOT EXISTS "{{data}}_11_classifier_outputs"
(
    "category"           TEXT    NOT NULL,
    "classifier"         TEXT    NOT NULL,
    "features_tfidf"     INTEGER NOT NULL,
    "features_heuristic" INTEGER NOT NULL,
    "type"               TEXT    NOT NULL,
    "tp"                 INTEGER NOT NULL,
    "fp"                 INTEGER NOT NULL,
    "tn"                 INTEGER NOT NULL,
    "fn"                 INTEGER NOT NULL,
    "w_pr"               NUMERIC,
    "w_re"               NUMERIC,
    "w_f_measure"        NUMERIC
)
