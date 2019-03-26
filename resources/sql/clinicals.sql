-- :name get-patient-list :? :*
-- :doc Get a list of patients of the given cancer type with their clinical values
/* :require [clojure.string :as s] */
SELECT *
FROM --~ (str "BIOBANK.VS_" (s/upper-case (:institute params)) "_CLINICAL_FEATURES_" (s/upper-case (:cancer-type params)) "_TB")
ORDER BY participant_id;

-- :name get-subtype-value-summary :? :*
-- :doc Get a list summarizing values of the given subtype
/* :require [clojure.string :as s] */
SELECT
:v:subtype-name subtype,
:i:subtype value,
count(*) cnt
FROM --~ (str "BIOBANK.VS_" (s/upper-case (:institute params)) "_CLINICAL_FEATURES_" (s/upper-case (:cancer-type params)) "_TB")
GROUP BY value;

-- :name drop-cohort-subtype-table! :!
-- :doc Drop VS_COHORT_SUBTYPE_TB

DROP TABLE IF EXISTS VS_COHORT_SUBTYPE_TB;

-- :name create-cohort-subtype-table! :!
-- :doc Create VS_COHORT_SUBTYPE_TB

CREATE TABLE VS_COHORT_SUBTYPE_TB (
	source varchar(10) NOT NULL DEFAULT 'NA',
	cancer_type varchar(4) NOT NULL DEFAULT 'NA',
	column_type varchar(10) NOT NULL DEFAULT 'value',
	subtype varchar(64) NOT NULL DEFAULT 'NA',
	value varchar(50) DEFAULT NULL,
	cnt bigint(20) NOT NULL DEFAULT 0
);

-- :name insert-subtype-values! :! :n
-- :doc Insert subtype values into VS_COHORT_SUBTYPE_TB

INSERT INTO VS_COHORT_SUBTYPE_TB
VALUES :tuple*:subtype-vals;

-- :name get-subtype-values :? :*
-- :doc Get clinical features and their values for the given cancer type

SELECT subtype,
       column_type,
       value
FROM VS_COHORT_SUBTYPE_TB
WHERE source = :institute
  AND cancer_type = :cancer-type
  AND column_type = 'value';

-- :name get-all-subtype-specs :? :*
-- :doc Get clinical subtype ids and names for a cancer type

SELECT *
FROM BIOBANK.VS_CLINICAL_SUBTYPE_SPECS_TB
ORDER BY institute, cancer_type, idx;

