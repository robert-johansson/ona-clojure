(ns ona.implication
  "Implication representation for temporal inference in ONA.

  Temporal implications allow the system to predict events from other events.
  Format: <A =/> B> means 'A temporally implies B'

  Learning scenarios:
  - Positive evidence: successful prediction
  - Negative evidence: prediction failure"
  (:require [ona.term :as term]
            [ona.truth :as truth]))

;; =============================================================================
;; Implication Structure
;; =============================================================================

(defrecord Implication
  [term                    ;; Term - the implication term <A =/> B>
   truth                   ;; Truth - predictive value (how often A preceded B)
   stamp                   ;; Evidential base (for now, simple ID)
   occurrence-time-offset  ;; Double - time gap between A and B
   source-concept          ;; Reference to source concept for spike propagation
   source-concept-id       ;; Long - validity check for source concept
   creation-time])         ;; Long - when this implication was created

(defn make-implication
  "Create a new implication.

  Args:
    term - Implication term <A =/> B>
    truth - Truth value (frequency, confidence)
    occurrence-time-offset - Time gap between precondition and postcondition
    creation-time - Current system time

  Options:
    :stamp - Evidential base (default: creation-time as ID)
    :source-concept - Back-reference to source concept
    :source-concept-id - ID for validity checking

  Returns:
    Implication record"
  ([term truth occurrence-time-offset creation-time]
   (make-implication term truth occurrence-time-offset creation-time {}))
  ([term truth occurrence-time-offset creation-time opts]
   {:pre [(term/temporal-implication? term)]}
   (map->Implication
    (merge
     {:term term
      :truth truth
      :stamp (:stamp opts creation-time)
      :occurrence-time-offset occurrence-time-offset
      :source-concept (:source-concept opts nil)
      :source-concept-id (:source-concept-id opts 0)
      :creation-time creation-time}
     opts))))

(defn implication-expectation
  "Calculate expectation value of implication for decision making.

  Uses truth expectation: E = c * (f - 0.5) + 0.5"
  [implication]
  (truth/expectation (:truth implication)))

(defn get-precondition
  "Extract precondition (subject) from implication term <A =/> B>
   Returns: A"
  [implication]
  (term/get-subject (:term implication)))

(defn get-postcondition
  "Extract postcondition (predicate) from implication term <A =/> B>
   Returns: B"
  [implication]
  (term/get-predicate (:term implication)))

(defn revise-implication
  "Revise two implications with overlapping evidence using truth revision.

  From Inference.c:Inference_ImplicationRevision

  Args:
    imp1 - First implication
    imp2 - Second implication

  Returns:
    Revised implication with combined evidence"
  [imp1 imp2]
  {:pre [(= (:term imp1) (:term imp2))]}  ; Must be same implication term
  (let [revised-truth (truth/revision (:truth imp1) (:truth imp2))
        ;; Use average time offset (could be weighted by confidence)
        avg-offset (/ (+ (:occurrence-time-offset imp1)
                        (:occurrence-time-offset imp2))
                      2.0)
        ;; Use more recent creation time
        creation-time (max (:creation-time imp1) (:creation-time imp2))]
    (make-implication
     (:term imp1)
     revised-truth
     avg-offset
     creation-time
     {:stamp (:stamp imp1)  ; TODO: merge stamps properly
      :source-concept (:source-concept imp1)
      :source-concept-id (:source-concept-id imp1)})))

(defn format-implication
  "Format implication for display/debugging"
  [implication]
  (str (term/format-term (:term implication))
       " " (truth/format-truth (:truth implication))
       " offset=" (format "%.1f" (:occurrence-time-offset implication))))

(comment
  ;; Example usage:

  ;; Create implication: <a =/> b>
  (def a (term/make-atomic-term "a"))
  (def b (term/make-atomic-term "b"))
  (def impl-term (term/make-temporal-implication a b))
  (def impl (make-implication impl-term
                              (truth/make-truth 0.8 0.9)
                              10.0  ;; 10 time units between a and b
                              100)) ;; created at time 100

  (format-implication impl)
  ;; => "<a =/> b> {0.800000 0.900000} offset=10.0"

  (implication-expectation impl)
  ;; => 0.77 (high expectation - likely to be true)

  (get-precondition impl)
  ;; => a

  (get-postcondition impl)
  ;; => b
  )
