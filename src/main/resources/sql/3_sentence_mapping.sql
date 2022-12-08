CREATE TABLE "{{data}}_3_sentence_mapping"
(
    "comment_sentence_id"  INTEGER NOT NULL,
    "category_sentence_id" INTEGER NOT NULL,
    "strategy"             TEXT    NOT NULL,
    "similarity"           NUMERIC NOT NULL,
    FOREIGN KEY ("comment_sentence_id") REFERENCES "{{data}}_2_sentence" ("id"),
    PRIMARY KEY ("comment_sentence_id", "category_sentence_id"),
    FOREIGN KEY ("category_sentence_id") REFERENCES "{{data}}_2_sentence" ("id")
)
