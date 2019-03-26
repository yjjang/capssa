-- :name get-cohort-exp-values :? :*
-- :doc Get TPM values by genes and cancer type

SELECT participant_id,
       hugo_symbol,
       tpm
FROM BIOBANK.VS_RNASEQRAW_CURATED_TB
WHERE institute_short = :institute
  AND sample_type = 'T'
  AND cancer_type = :cancer-type
  AND hugo_symbol IN (:v*:genes)
GROUP BY participant_id, hugo_symbol;

-- :name get-cohort-prognostic-values :? :*
-- :doc Get log2 TPM values, overall survival days and status by genes and cancer type
/* :require [clojure.string :as s] */
SELECT A.participant_id,
       A.hugo_symbol,
       log2(A.tpm + 1) tpm,
	   B.os_days,
	   B.os_status
FROM BIOBANK.VS_RNASEQRAW_CURATED_TB A,
--~ (str "BIOBANK.VS_" (s/upper-case (:institute params)) "_CLINICAL_FEATURES_" (s/upper-case (:cancer-type params)) "_TB B")
WHERE A.institute_short = :institute
  AND A.sample_type = 'T'
  AND A.cancer_type = :cancer-type
  AND A.hugo_symbol IN (:v*:genes)
  AND A.participant_id = B.participant_id
  AND B.os_days IS NOT NULL
GROUP BY A.participant_id, A.hugo_symbol;

