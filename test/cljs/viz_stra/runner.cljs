(ns viz-stra.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [viz-stra.core-test]))

(doo-tests 'viz-stra.core-test)
