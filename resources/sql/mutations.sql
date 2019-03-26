-- :name get-mutation-list :? :*
-- :doc Get mutations in all patients for the given cancer type and genes

SELECT DISTINCT participant_id participant_id,
                hugo_symbol gene,
                variant_classification type
FROM BIOBANK.VS_MUTATION_CNV_TB
WHERE institute_short = :institute
  AND cancer_type = :cancer-type
  AND hugo_symbol IN (:v*:genes)
ORDER BY gene;

-- :name get-subtype-list :? :*
-- :doc Get patient subtypes for the given cancer type
/* :require [clojure.string :as s] */

SELECT participant_id,
/*~
(s/join "," (for [[stype sname] (:subtypes params)]
                (str "ifnull(" stype ", 'NA') '" sname "'")))
~*/
FROM --~ (str "BIOBANK.VS_" (s/upper-case (:institute params)) "_CLINICAL_FEATURES_" (s/upper-case (:cancer-type params)) "_TB")
WHERE institute_short = :institute
  AND participant_id IN
      (SELECT DISTINCT B.participant_id
       FROM BIOBANK.VS_MUTATION_CNV_TB B
       WHERE B.institute_short = :institute
         AND B.cancer_type = :cancer-type);

