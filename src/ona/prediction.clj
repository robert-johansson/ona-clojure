(ns ona.prediction
  "Prediction tracking and validation for negative evidence.

  Tracks predictions made by forward chaining (belief deduction),
  compares them with actual observations, and revises implications
  based on success or failure.

  From C ONA: Decision_Anticipate, Implication revision on outcomes"
  (:require [ona.event :as event]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.implication :as implication]))

;; =============================================================================
;; Prediction Structure
;; =============================================================================

(defrecord Prediction
  [predicted-event        ; Event - what we predicted would happen
   source-implication     ; Implication - which implication generated this
   source-concept-term    ; Term - precondition concept that triggered prediction
   expected-time          ; Long - when we expect the outcome
   creation-time          ; Long - when prediction was made
   confirmed?             ; Boolean - whether prediction matched reality
   refuted?])             ; Boolean - whether prediction contradicted reality

(defn make-prediction
  "Create a prediction record.

  Args:
    predicted-event - Event that was predicted
    source-impl - Implication that generated the prediction
    source-term - Term of the precondition concept
    current-time - Current system time

  Returns:
    Prediction record"
  [predicted-event source-impl source-term current-time]
  (map->Prediction
   {:predicted-event predicted-event
    :source-implication source-impl
    :source-concept-term source-term
    :expected-time (:occurrence-time predicted-event)
    :creation-time current-time
    :confirmed? false
    :refuted? false}))

;; =============================================================================
;; Prediction Comparison
;; =============================================================================

(defn matches-prediction?
  "Check if an observed event matches a prediction.

  Considers:
  - Term must match
  - Occurrence time should be close (within tolerance)

  Args:
    prediction - Prediction record
    observed-event - Observed event
    time-tolerance - Max time difference to consider a match (default 5 cycles)

  Returns:
    Boolean - true if observed event matches prediction"
  [prediction observed-event time-tolerance]
  (let [predicted-event (:predicted-event prediction)
        predicted-term (:term predicted-event)
        observed-term (:term observed-event)
        predicted-time (:occurrence-time predicted-event)
        observed-time (:occurrence-time observed-event)]

    (and
     ;; Terms must match
     (= (:representation predicted-term)
        (:representation observed-term))

     ;; Times must be close
     (if (event/eternal? predicted-event)
       true  ; Eternal predictions always match timing
       (<= (abs (- observed-time predicted-time))
           time-tolerance)))))

(defn prediction-truth-match?
  "Check if prediction truth value is close to observed truth.

  Used to determine if prediction was accurate (not just present).

  Args:
    prediction - Prediction record
    observed-event - Observed event
    frequency-tolerance - Max frequency difference (default 0.5)

  Returns:
    Boolean - true if truth values are similar"
  [prediction observed-event frequency-tolerance]
  (let [predicted-truth (:truth (:predicted-event prediction))
        observed-truth (:truth observed-event)
        freq-diff (abs (- (:frequency predicted-truth)
                          (:frequency observed-truth)))]
    (<= freq-diff frequency-tolerance)))

;; =============================================================================
;; Implication Revision
;; =============================================================================

(defn revise-on-confirmation
  "Revise implication after prediction confirmed.

  Strengthens the implication by slightly increasing confidence.

  Args:
    impl - Implication to revise
    predicted-event - What was predicted
    observed-event - What was observed
    current-time - Current system time

  Returns:
    Revised implication"
  [impl predicted-event observed-event current-time]
  (let [;; Get current implication truth
        impl-truth (:truth impl)

        ;; Create confirmation evidence
        ;; Frequency = 1.0 (implication held)
        ;; Confidence = 0.1 (small boost)
        confirmation-truth (truth/make-truth 1.0 0.1)

        ;; Revise with confirmation
        revised-truth (truth/revision impl-truth confirmation-truth)

        ;; Update implication
        revised-impl (assoc impl
                            :truth revised-truth
                            :creation-time current-time)]
    revised-impl))

(defn revise-on-refutation
  "Revise implication after prediction failed.

  Weakens the implication by decreasing confidence and/or frequency.

  Args:
    impl - Implication to revise
    predicted-event - What was predicted
    observed-event - What was observed (or nil if timeout)
    current-time - Current system time

  Returns:
    Revised implication"
  [impl predicted-event observed-event current-time]
  (let [;; Get current implication truth
        impl-truth (:truth impl)

        ;; Create refutation evidence
        ;; Frequency = 0.0 (implication didn't hold)
        ;; Confidence = 0.1 (small penalty)
        refutation-truth (truth/make-truth 0.0 0.1)

        ;; Revise with refutation
        revised-truth (truth/revision impl-truth refutation-truth)

        ;; Update implication
        revised-impl (assoc impl
                            :truth revised-truth
                            :creation-time current-time)]
    revised-impl))

;; =============================================================================
;; Prediction Validation
;; =============================================================================

(defn validate-prediction
  "Check if a prediction was confirmed or refuted by an observation.

  Args:
    prediction - Prediction record
    observed-event - Observed event
    current-time - Current system time
    config - Configuration map with tolerances

  Returns:
    [status revised-prediction]
    - status: :confirmed, :refuted, or :pending
    - revised-prediction: Updated prediction record"
  [prediction observed-event current-time config]
  (let [time-tolerance (get config :prediction-time-tolerance 5)
        freq-tolerance (get config :prediction-frequency-tolerance 0.5)]

    (cond
      ;; Already confirmed or refuted
      (or (:confirmed? prediction) (:refuted? prediction))
      [:already-resolved prediction]

      ;; Check if observation matches
      (matches-prediction? prediction observed-event time-tolerance)
      (if (prediction-truth-match? prediction observed-event freq-tolerance)
        ;; Prediction confirmed!
        [:confirmed (assoc prediction :confirmed? true)]
        ;; Matched term but wrong truth value - partial refutation
        [:refuted (assoc prediction :refuted? true)])

      ;; Check for timeout
      (and (not (event/eternal? (:predicted-event prediction)))
           (> current-time (+ (:expected-time prediction) time-tolerance)))
      ;; Prediction timed out - refuted
      [:timeout (assoc prediction :refuted? true)]

      ;; Still pending
      :else
      [:pending prediction])))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn expired-prediction?
  "Check if a prediction has expired (past expected time + tolerance).

  Args:
    prediction - Prediction record
    current-time - Current system time
    time-tolerance - Timeout grace period (default 10 cycles)

  Returns:
    Boolean - true if prediction is expired"
  [prediction current-time time-tolerance]
  (let [expected (:expected-time prediction)]
    (and (not (event/eternal? (:predicted-event prediction)))
         (> current-time (+ expected time-tolerance))
         (not (:confirmed? prediction))
         (not (:refuted? prediction)))))

(defn active-prediction?
  "Check if prediction is still active (not confirmed, refuted, or expired).

  Args:
    prediction - Prediction record
    current-time - Current system time
    time-tolerance - Timeout grace period

  Returns:
    Boolean - true if prediction is active"
  [prediction current-time time-tolerance]
  (and (not (:confirmed? prediction))
       (not (:refuted? prediction))
       (not (expired-prediction? prediction current-time time-tolerance))))

(comment
  ;; Example usage:

  (require '[ona.event :as event]
           '[ona.term :as term]
           '[ona.truth :as truth]
           '[ona.implication :as implication])

  ;; Create a prediction
  (def impl (implication/make-implication
             (term/parse-term "<a =/> b>")
             (truth/make-truth 0.9 0.8)
             10.0
             100))

  (def predicted-event (event/make-belief
                        (term/parse-term "b")
                        (truth/make-truth 0.9 0.7)
                        110  ; expected at t=110
                        100
                        {:predicted? true}))

  (def prediction (make-prediction predicted-event
                                   impl
                                   (term/parse-term "a")
                                   100))

  ;; Later: observe matching event
  (def observed (event/make-belief
                 (term/parse-term "b")
                 (truth/make-truth 1.0 0.9)
                 110
                 110
                 {:input? true}))

  ;; Validate
  (validate-prediction prediction observed 110 {:prediction-time-tolerance 5})
  ;; => [:confirmed <updated-prediction>]

  ;; Revise implication on confirmation
  (revise-on-confirmation impl predicted-event observed 110)
  ;; => <implication with increased confidence>
  )
