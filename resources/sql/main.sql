-- :name get-valid-gene-symbols :? :*
-- :doc Select genes of approved hugo symbols

SELECT DISTINCT hugo_symbol
FROM BIOBANK.PD_HUGO_SYMBOLS_TB
WHERE hugo_symbol IN (:v*:genes)
ORDER BY hugo_symbol;

-- :name get-msigdb-suggestions :? :*
-- :doc Search MSigDB gene sets by name or genes like for the suggestion

SELECT *
FROM BIOBANK.VS_MSIGDB_GENESETS_TB
WHERE (name LIKE :like OR genes LIKE :like)
  AND (LENGTH(genes) - LENGTH(REPLACE(genes, ',', ''))) < :limit;

