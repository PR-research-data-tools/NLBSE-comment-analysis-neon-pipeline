CREATE VIEW "{{data}}_3_sentence_mapping_clean" AS
SELECT s.class     AS class,
       r.stratum   AS stratum,
       s.id        AS comment_sentence_id,
       s.sentence  AS comment_sentence,
       ms.id       AS category_sentence_id,
       ms.sentence AS category_sentence,
       ms.category AS category
FROM "{{data}}_2_sentence" s
    JOIN "{{data}}_0_raw" r
ON (r.class = s.class)
    JOIN "{{data}}_3_sentence_mapping" m ON (m.comment_sentence_id = s.id)
    JOIN "{{data}}_2_sentence" ms ON (ms.id = m.category_sentence_id)
WHERE s.category = 'comment'
  AND (
    m.strategy = 'equals'
   OR (m.strategy = 'contains-stripped'
  AND m.similarity
    > 1)
   OR (m.strategy = 'contains'
  AND length(ms.sentence)
    > 1) -- exclude single char matches
-- exclude contains-a-z-0-9, not useful
    )
GROUP BY s.id,
    ms.category
HAVING m.similarity = max(m.similarity)
   AND m.rowid = min(m.rowid); -- ensure unique category match, there might be duplicates of shorter matches using a different category sentence and strategy
