(ns ona.decision
  "Decision making and goal realization for ONA.

  Core algorithm:
  1. Given goal G!, find implications <A =/> G>
  2. Check if precondition A is believed
  3. Calculate desire = deduction(belief(A), <A =/> G>, goal(G))
  4. If desire > threshold, execute action
  5. Otherwise, derive subgoal A!"
  (:require [ona.event :as event]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.implication :as implication]
            [ona.inference :as inference]))

;; =============================================================================
;; Constants
;; =============================================================================

(def decision-threshold 0.5)  ;; Minimum desire to execute
(def motor-babbling-chance 0.2)  ;; Probability of random exploration
(def motor-babbling-suppression-threshold 0.7)  ;; High desire suppresses babbling

;; =============================================================================
;; Decision Structure
;; =============================================================================

(defrecord Decision
  [operation-id    ;; ID of operation to execute
   operation-term  ;; Term of the operation
   desire          ;; Truth expectation (how much we want to do it)
   reason          ;; Precondition event that justifies this
   execute?])      ;; Whether desire > threshold

(defn make-decision
  "Create a decision record.

  Args:
    operation-id - ID of operation (or nil)
    operation-term - Term of operation
    desire - Desire value (truth expectation)
    reason - Precondition event
    execute? - Whether to execute

  Returns:
    Decision record"
  [operation-id operation-term desire reason execute?]
  (map->Decision
   {:operation-id operation-id
    :operation-term operation-term
    :desire desire
    :reason reason
    :execute? execute?}))

(defn null-decision
  "Create empty/null decision (no action)"
  []
  (make-decision nil nil 0.0 nil false))

;; =============================================================================
;; Goal Deduction
;; =============================================================================

(defn goal-deduction
  "Backward chaining: {Goal b!, Implication <a =/> b>} |- Goal a!

  From C ONA: Inference_GoalDeduction

  Given:
  - Goal for B
  - Implication A =/> B
  Derive:
  - Goal for A (subgoal)

  Truth function: Deduction

  Args:
    goal-event - Goal event for postcondition
    impl - Implication <precondition =/> postcondition>
    current-time - Current system time

  Returns:
    Derived subgoal event"
  [goal-event impl current-time]
  (let [precondition-term (implication/get-precondition impl)
        goal-truth (:truth goal-event)
        impl-truth (:truth impl)

        ;; Apply deduction: desire(A) = deduction(desire(B), <A =/> B>)
        subgoal-truth (truth/deduction impl-truth goal-truth)

        ;; Occurrence time: goal time minus implication offset
        occurrence-time (if (event/eternal? goal-event)
                          event/eternal-occurrence
                          (- (:occurrence-time goal-event)
                             (:occurrence-time-offset impl)))

        ;; Create subgoal
        subgoal (event/make-goal precondition-term
                                 subgoal-truth
                                 occurrence-time
                                 current-time
                                 {:stamp (:stamp goal-event)
                                  :processed? false
                                  :input? false})]
    subgoal))

;; =============================================================================
;; Decision Consideration
;; =============================================================================

(defn consider-implication
  "Consider a single implication for decision making.

  Given:
  - Goal G!
  - Implication <A =/> G>
  - Belief for A

  Calculate desire = expectation(deduction(belief(A), <A =/> G>, goal(G)))

  Args:
    goal - Goal event
    impl - Implication <precondition =/> goal>
    precondition-belief - Belief event for precondition
    current-time - Current system time

  Returns:
    Decision record"
  [goal impl precondition-belief current-time]
  (if (nil? precondition-belief)
    (null-decision)
    (let [;; Extract operation from implication precondition
          operation-term (implication/get-precondition impl)

          ;; Calculate desire using goal deduction
          contextual-operation (goal-deduction goal impl current-time)
          desire-truth (:truth contextual-operation)
          desire-expectation (truth/expectation desire-truth)

          ;; Check if desire is high enough
          execute? (>= desire-expectation decision-threshold)]

      (make-decision nil  ;; operation-id (not used yet)
                     operation-term
                     desire-expectation
                     precondition-belief
                     execute?))))

;; =============================================================================
;; Best Candidate Selection
;; =============================================================================

(defn find-matching-implications
  "Find implications whose postcondition matches the goal.

  Args:
    state - Current NAR state
    goal - Goal event

  Returns:
    Vector of [implication precondition-concept] pairs"
  [state goal]
  (let [goal-term (:term goal)]
    ;; Scan all concepts for implications
    (for [concept (vals (:concepts state))
          impl (vals (:implications concept))
          :let [postcondition (implication/get-postcondition impl)]
          :when (= (:representation postcondition)
                   (:representation goal-term))]
      [impl concept])))

(defn best-candidate
  "Find the best action to achieve a goal.

  Algorithm:
  1. Find all implications <A =/> G> where G matches goal
  2. For each, check if precondition A is believed
  3. Calculate desire for each valid implication
  4. Return decision with highest desire

  Args:
    state - Current NAR state
    goal - Goal event
    current-time - Current system time

  Returns:
    Best decision (or null-decision if none found)"
  [state goal current-time]
  (let [;; Find matching implications
        matches (find-matching-implications state goal)

        ;; Consider each implication
        decisions (for [[impl precondition-concept] matches
                        :let [precondition-belief (:belief-spike precondition-concept)]
                        :when precondition-belief]
                    (consider-implication goal impl precondition-belief current-time))

        ;; Select best by desire
        best (reduce (fn [best current]
                       (if (> (:desire current) (:desire best))
                         current
                         best))
                     (null-decision)
                     decisions)]
    best))

;; =============================================================================
;; Motor Babbling
;; =============================================================================

(defn motor-babbling
  "Random action selection for exploration.

  Args:
    state - Current NAR state

  Returns:
    Random decision from available operations"
  [state]
  (let [operations (vals (:operations state))]
    (if (empty? operations)
      (null-decision)
      (let [;; Use seeded RNG if available
            rng (:rng state)
            random-idx (if rng
                        (.nextInt rng (count operations))
                        (rand-int (count operations)))
            random-op (nth operations random-idx)]
        (make-decision (:id random-op)
                       (:name random-op)  ; Use :name field from Operation
                       0.6  ;; Fixed moderate desire for exploration
                       nil
                       true)))))

;; =============================================================================
;; Main Decision Suggestion
;; =============================================================================

(defn suggest-decision
  "Suggest an action to achieve a goal.

  Main decision-making entry point. Combines:
  - Motor babbling (exploration)
  - Best candidate selection (exploitation)

  Motor babbling is suppressed if best candidate has high desire.

  Args:
    state - Current NAR state
    goal - Goal event
    current-time - Current system time

  Returns:
    Decision record"
  [state goal current-time]
  (let [;; Try motor babbling with certain probability
        motor-babbling-enabled (get-in state [:config :motor-babbling] false)
        babbling-chance (get-in state [:config :motor-babbling-chance] motor-babbling-chance)
        ;; Use seeded RNG if available, otherwise default rand
        rng (:rng state)
        random-value (if rng (.nextDouble rng) (rand))
        should-babble (and motor-babbling-enabled
                          (< random-value babbling-chance))
        babble-decision (when should-babble (motor-babbling state))

        ;; Find best candidate through reasoning
        best-decision (best-candidate state goal current-time)

        ;; Choose between babbling and reasoning
        ;; Suppress babbling if best decision has high desire
        final-decision (if (and babble-decision
                               (:execute? babble-decision)
                               (< (:desire best-decision)
                                  motor-babbling-suppression-threshold))
                         babble-decision
                         best-decision)]
    final-decision))

(comment
  ;; Example usage:

  (require '[ona.core :as core]
           '[ona.term :as term]
           '[ona.truth :as truth]
           '[ona.event :as event]
           '[ona.implication :as implication])

  ;; Setup: Learn <key-pressed =/> door-opened>
  (def state (core/init-state))

  ;; Add implication
  (def impl (implication/make-implication
             (term/make-temporal-implication
              (term/make-atomic-term "key-pressed")
              (term/make-atomic-term "door-opened"))
             (truth/make-truth 0.9 0.8)
             10.0
             100))
  (def state (core/add-implication state impl (term/make-atomic-term "key-pressed")))

  ;; Add belief that key-pressed is true
  (def state (core/add-input-belief state
                                     (term/make-atomic-term "key-pressed")
                                     (truth/make-truth 1.0 0.9)
                                     false))  ;; temporal

  ;; Create goal: door-opened!
  (def goal (event/make-goal (term/make-atomic-term "door-opened")
                              (truth/make-truth 1.0 0.9)
                              110
                              110
                              {:input? true}))

  ;; Suggest decision
  (def decision (suggest-decision state goal 110))
  (:desire decision)  ;; Should be high (>0.5)
  (:execute? decision)  ;; Should be true
  )
