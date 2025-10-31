(ns ona.inference
  "Temporal inference operations for ONA.

  Core operations:
  - Sequence formation: {a., b.} |- (a &/ b).
  - Temporal induction: {a., b.} |- <a =/> b>."
  (:require [ona.event :as event]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.implication :as implication]
            [ona.variable :as var]))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn- weighted-average
  "Calculate weighted average of two values"
  [a1 a2 w1 w2]
  (/ (+ (* a1 w1) (* a2 w2))
     (+ w1 w2)))

;; =============================================================================
;; Sequence Formation (BeliefIntersection)
;; =============================================================================

(defn belief-intersection
  "Form sequence from two consecutive events: {a., b.} |- (a &/ b).

  From C ONA:Inference_BeliefIntersection

  Conditions:
  - Both events must be beliefs
  - Event b must occur after event a (b.occurrenceTime >= a.occurrenceTime)

  Truth function: Intersection (f = f1 * f2, c = c1 * c2)
  Time: conclusion has occurrence time of b
  Stamp: merged from a and b

  Args:
    event-a - Earlier event
    event-b - Later event
    current-time - Current system time
    projection-decay - Temporal projection decay factor

  Returns:
    [success? derived-event]
    - success? is false if conditions not met
    - derived-event is the sequence event (a &/ b)"
  [event-a event-b current-time projection-decay]
  (if (or (not= (:type event-a) :belief)
          (not= (:type event-b) :belief)
          (event/eternal? event-a)
          (event/eternal? event-b)
          (< (:occurrence-time event-b) (:occurrence-time event-a)))
    [false nil]  ;; Invalid conditions
    (let [conclusion-time (:occurrence-time event-b)
          creation-time (max (:creation-time event-a) (:creation-time event-b))

          ;; Project event-a's truth to conclusion time
          truth-a (truth/projection (:truth event-a)
                                    (:occurrence-time event-a)
                                    conclusion-time
                                    projection-decay)
          truth-b (:truth event-b)

          ;; Apply intersection truth function
          conclusion-truth (truth/intersection truth-a truth-b)

          ;; Form sequence term: (a &/ b)
          seq-term (term/make-sequence (:term event-a) (:term event-b))

          ;; Merge stamps (simplified - just use later stamp for now)
          conclusion-stamp (:stamp event-b)

          ;; Create derived event
          derived-event (event/make-belief seq-term
                                           conclusion-truth
                                           conclusion-time
                                           creation-time
                                           {:stamp conclusion-stamp
                                            :processed? false
                                            :input? false})]
      [true derived-event])))

;; =============================================================================
;; Variable Introduction for Implications
;; =============================================================================

(defn introduce-variables-simple
  "Simple variable introduction: Replace precondition of implication with variable.

  NAL-6 Enhancement: Generalizes concrete implications by introducing variables.
  Example: <bird =/> flies> → <$1 =/> flies>

  This is a simplified version focusing on the most common pattern - generalizing
  the precondition to allow pattern matching.

  Args:
    impl - Implication to generalize
    var-name - Variable name to use (default \"$1\")

  Returns:
    Generalized implication with variable in precondition"
  ([impl]
   (introduce-variables-simple impl "$1"))
  ([impl var-name]
   (let [;; Extract precondition and postcondition
         precond (implication/get-precondition impl)
         postcond (implication/get-postcondition impl)

         ;; Replace precondition with variable
         var-term (term/make-atomic-term var-name)
         generalized-impl-term (term/make-temporal-implication var-term postcond)

         ;; Create new implication with variable
         generalized-impl (implication/make-implication
                           generalized-impl-term
                           (:truth impl)
                           (:occurrence-time-offset impl)
                           (:creation-time impl)
                           {:stamp (:stamp impl)})]
     generalized-impl)))

;; =============================================================================
;; Temporal Induction (BeliefInduction)
;; =============================================================================

(defn belief-induction
  "Derive temporal implication from two consecutive events: {a., b.} |- <a =/> b>.

  From C ONA:Inference_BeliefInduction

  Conditions:
  - Both events must be beliefs
  - Event b must occur after event a (b.occurrenceTime >= a.occurrenceTime)

  Truth function: Induction (frequency = fb, confidence = w2c(fa * ca * cb))
  Occurrence time offset: b.time - a.time
  Stamp: merged from a and b

  Args:
    event-a - Earlier event (precondition)
    event-b - Later event (postcondition)
    current-time - Current system time
    projection-decay - Temporal projection decay factor

  Returns:
    [success? derived-implication]
    - success? is false if conditions not met
    - derived-implication is <a =/> b>"
  [event-a event-b current-time projection-decay]
  (if (or (not= (:type event-a) :belief)
          (not= (:type event-b) :belief)
          (event/eternal? event-a)
          (event/eternal? event-b)
          (< (:occurrence-time event-b) (:occurrence-time event-a)))
    [false nil]  ;; Invalid conditions
    (let [conclusion-time (:occurrence-time event-b)
          creation-time (max (:creation-time event-a) (:creation-time event-b))

          ;; Project event-a's truth to conclusion time
          truth-a (truth/projection (:truth event-a)
                                    (:occurrence-time event-a)
                                    conclusion-time
                                    projection-decay)
          truth-b (:truth event-b)

          ;; Apply induction truth function
          conclusion-truth (truth/induction truth-b truth-a)

          ;; Calculate occurrence time offset
          time-offset (- (:occurrence-time event-b) (:occurrence-time event-a))

          ;; Form implication term: <a =/> b>
          impl-term (term/make-temporal-implication (:term event-a) (:term event-b))

          ;; Merge stamps (simplified - just use later stamp for now)
          conclusion-stamp (:stamp event-b)

          ;; Create derived implication
          derived-impl (implication/make-implication impl-term
                                                      conclusion-truth
                                                      time-offset
                                                      creation-time
                                                      {:stamp conclusion-stamp})]
      [true derived-impl])))

;; =============================================================================
;; Belief Deduction (Forward Chaining)
;; =============================================================================

(defn belief-deduction
  "Derive predicted belief from belief and implication: {a., <a =/> b>} |- b.

  From C ONA:Inference_BeliefDeduction

  Given:
  - Belief event for A
  - Temporal implication <A =/> B> (may contain variables)

  Derive:
  - Predicted belief event for B

  NAL-6 Enhancement:
  - Unifies belief term with implication precondition (pattern matching)
  - Substitutes variable bindings into postcondition
  - Example: {bird., <$1 =/> ($1 * wings)>} |- (bird * wings).

  Truth function: Deduction (f = fa * fi, c = ca * ci * fa)
  Occurrence time: belief-time + implication-offset

  Args:
    belief-event - Belief event for precondition
    impl - Implication <precondition =/> postcondition>
    current-time - Current system time
    projection-decay - Temporal projection decay factor

  Returns:
    [success? predicted-event]
    - success? is false if conditions not met or unification fails
    - predicted-event is the derived belief for postcondition"
  [belief-event impl current-time projection-decay]
  (if (not= (:type belief-event) :belief)
    [false nil]
    (let [;; Get precondition and postcondition from implication
          precondition-term (implication/get-precondition impl)
          postcondition-term (implication/get-postcondition impl)

          ;; NAL-6: Unify belief term with implication precondition
          ;; This enables pattern matching: belief 'bird' matches precondition '$1'
          substitution (var/unify precondition-term (:term belief-event))]

      ;; Check if unification succeeded
      (if-not (:success substitution)
        [false nil]  ;; Unification failed - belief doesn't match pattern

        ;; Unification succeeded - apply substitution to postcondition
        (let [;; NAL-6: Substitute variable bindings into postcondition
              ;; Example: postcondition '($1 * wings)' with $1←bird becomes '(bird * wings)'
              substituted-postcondition (var/substitute postcondition-term substitution)

              ;; Calculate predicted occurrence time
              predicted-time (if (event/eternal? belief-event)
                              event/eternal-occurrence
                              (+ (:occurrence-time belief-event)
                                 (:occurrence-time-offset impl)))

              creation-time current-time

              ;; Get truth values
              belief-truth (:truth belief-event)
              impl-truth (:truth impl)

              ;; Apply deduction truth function
              predicted-truth (truth/deduction impl-truth belief-truth)

              ;; Merge stamps (simplified - use belief stamp)
              predicted-stamp (:stamp belief-event)

              ;; Create predicted event with substituted term
              predicted-event (event/make-belief substituted-postcondition
                                                 predicted-truth
                                                 predicted-time
                                                 creation-time
                                                 {:stamp predicted-stamp
                                                  :processed? false
                                                  :input? false
                                                  :predicted? true})]
          [true predicted-event])))))

;; =============================================================================
;; Revision
;; =============================================================================

(defn event-revision
  "Revise two events about the same statement: {a., a.} |- a.

  From C ONA:Inference_EventRevision

  Combines evidence from two sources to strengthen confidence.

  Args:
    event-a - First event
    event-b - Second event
    current-time - Current system time
    projection-decay - Temporal projection decay factor

  Returns:
    Revised event with combined evidence"
  [event-a event-b current-time projection-decay]
  (let [conclusion-time (:occurrence-time event-b)
        creation-time (max (:creation-time event-a) (:creation-time event-b))

        ;; Project event-a's truth to conclusion time
        truth-a (if (event/eternal? event-a)
                  (:truth event-a)
                  (truth/projection (:truth event-a)
                                    (:occurrence-time event-a)
                                    conclusion-time
                                    projection-decay))
        truth-b (:truth event-b)

        ;; Apply revision truth function
        conclusion-truth (truth/revision truth-a truth-b)

        ;; Merge stamps (simplified)
        conclusion-stamp (:stamp event-b)]

    (event/make-event (:term event-a)
                      (:type event-a)
                      conclusion-truth
                      conclusion-time
                      creation-time
                      {:stamp conclusion-stamp
                       :processed? false
                       :input? false})))

(comment
  ;; Example usage:

  (require '[ona.event :as event]
           '[ona.term :as term]
           '[ona.truth :as truth])

  ;; Create two consecutive events
  (def event-a (event/make-belief (term/make-atomic-term "a")
                                   (truth/make-truth 1.0 0.9)
                                   10  ;; occurred at time 10
                                   10
                                   {:input? true}))

  (def event-b (event/make-belief (term/make-atomic-term "b")
                                   (truth/make-truth 1.0 0.9)
                                   20  ;; occurred at time 20
                                   20
                                   {:input? true}))

  ;; Form sequence: (a &/ b)
  (def [success? seq-event] (belief-intersection event-a event-b 20 0.99))
  success?  ;; => true
  (term/format-term (:term seq-event))  ;; => "(a &/ b)"
  (:truth seq-event)  ;; => {0.81 0.81} (intersection)

  ;; Derive temporal implication: <a =/> b>
  (def [success? impl] (belief-induction event-a event-b 20 0.99))
  success?  ;; => true
  (term/format-term (:term impl))  ;; => "<a =/> b>"
  (:occurrence-time-offset impl)  ;; => 10.0
  )
