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
            [ona.inference :as inference]
            [ona.variable :as var]))

;; =============================================================================
;; Constants
;; =============================================================================

(def decision-threshold 0.5)  ;; Minimum desire to execute
(def motor-babbling-chance 0.2)  ;; Probability of random exploration
(def motor-babbling-suppression-threshold 0.7)  ;; High desire suppresses babbling
(def anticipation-threshold 0.501)  ;; Minimum expectation for anticipation
(def anticipation-confidence 0.01)  ;; Confidence for negative evidence

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
;; Anticipation Structure
;; =============================================================================

(defrecord Anticipation
  [predicted-event   ;; Event we expect to occur
   implication       ;; Implication that generated this anticipation
   precondition-belief ;; Belief that satisfied precondition
   deadline          ;; Time by which event should occur
   created-time])    ;; When anticipation was created

(defn make-anticipation
  "Create an anticipation record.

  Args:
    predicted-event - Event we anticipate
    impl - Implication that generated prediction
    precondition-belief - Belief event for precondition
    deadline - Expected occurrence time
    created-time - Current system time

  Returns:
    Anticipation record"
  [predicted-event impl precondition-belief deadline created-time]
  (map->Anticipation
   {:predicted-event predicted-event
    :implication impl
    :precondition-belief precondition-belief
    :deadline deadline
    :created-time created-time}))

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

  NAL-6 Enhancement: Uses variable unification for pattern matching.
  - Exact match: <A =/> door-opened> matches goal door-opened!
  - Variable match: <A =/> $1> matches goal door-opened! with $1←door-opened

  Args:
    state - Current NAR state
    goal - Goal event

  Returns:
    Vector of [implication precondition-concept substitution] triples"
  [state goal]
  (let [goal-term (:term goal)]
    ;; Scan all concepts for implications
    (for [concept (vals (:concepts state))
          impl (vals (:implications concept))
          :let [postcondition (implication/get-postcondition impl)
                ;; NAL-6: Unify postcondition (pattern) with goal (specific)
                substitution (var/unify postcondition goal-term)]
          :when (:success substitution)]
      [impl concept substitution])))

(defn best-candidate
  "Find the best action to achieve a goal.

  NAL-6/8 Enhanced Algorithm (following C ONA Decision_BestCandidate):
  1. Find all implications <A =/> G> where G unifies with goal (with subst1)
  2. Apply subst1 to implication → <A' =/> G'>
  3. For each concept with belief, unify A' with belief term (with subst2)
  4. Apply subst2 to implication → fully instantiated <A'' =/> G''>
  5. Calculate desire for fully instantiated implication
  6. Return decision with highest desire

  Args:
    state - Current NAR state
    goal - Goal event
    current-time - Current system time

  Returns:
    Best decision (or null-decision if none found)"
  [state goal current-time]
  (let [;; Step 1: Find implications whose postcondition unifies with goal
        matches (find-matching-implications state goal)

        all-concepts (vals (:concepts state))

        ;; Step 2-5: For each matching implication, find best belief match
        decisions
        (for [[impl _concept goal-subst] matches
              :let [;; Apply goal substitution to implication
                    impl-after-goal (var/substitute (:term impl) goal-subst)
                    precondition-term (term/get-subject impl-after-goal)]

              ;; Step 3: Try to unify precondition with each concept's belief
              belief-concept all-concepts
              :let [belief-spike (:belief-spike belief-concept)]
              :when (and belief-spike
                         (= (:type belief-spike) :belief))
              :let [belief-term (:term belief-spike)
                    ;; Unify precondition (pattern) with belief (specific)
                    belief-subst (var/unify precondition-term belief-term)]
              :when (:success belief-subst)

              ;; Step 4: Apply belief substitution to get fully instantiated implication
              :let [fully-instantiated-term (var/substitute impl-after-goal belief-subst)
                    fully-instantiated-impl (implication/make-implication
                                              fully-instantiated-term
                                              (:truth impl)
                                              (:occurrence-time-offset impl)
                                              (:creation-time impl)
                                              {:stamp (:stamp impl)})]]

          ;; Step 5: Calculate desire
          (consider-implication goal fully-instantiated-impl belief-spike current-time))

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
;; Anticipation and Negative Evidence
;; =============================================================================

(defn create-anticipation
  "Create an anticipation after executing a decision.

  When we execute an action based on <A =/> B>, we anticipate B will occur.
  This creates an Anticipation record for later verification.

  From C ONA: Decision_Execute and Decision_Anticipate

  Args:
    decision - The decision that was executed
    impl - The implication used for decision
    precondition-belief - Belief that satisfied precondition
    current-time - Current system time

  Returns:
    Anticipation record"
  [decision impl precondition-belief current-time]
  (let [;; Predict when the outcome should occur
        predicted-time (+ current-time (:occurrence-time-offset impl))

        ;; Use belief-deduction to predict the outcome
        [success? predicted-event] (inference/belief-deduction
                                     precondition-belief
                                     impl
                                     current-time
                                     0.99)

        ;; Set deadline slightly after predicted time (allow some slack)
        deadline (+ predicted-time 10.0)]

    (when success?
      (make-anticipation predicted-event
                        impl
                        precondition-belief
                        deadline
                        current-time))))

(defn add-negative-confirmation
  "Add negative evidence to an implication when anticipated outcome doesn't occur.

  From C ONA: Decision_AddNegativeConfirmation (lines 34-48)

  Algorithm:
  1. Create negative truth: {frequency=0.0, confidence=ANTICIPATION_CONFIDENCE}
  2. Apply induction with precondition belief
  3. Revise existing implication with negative evidence

  Args:
    state - Current NAR state
    anticipation - Failed anticipation
    concept-term - Term of concept containing implication

  Returns:
    Updated state with weakened implication"
  [state anticipation concept-term]
  (let [impl (:implication anticipation)
        precondition-belief (:precondition-belief anticipation)

        ;; Create negative evidence: outcome didn't happen
        negative-truth (truth/make-truth 0.0 anticipation-confidence)

        ;; Apply induction to get negative implication truth
        precond-truth (:truth precondition-belief)
        negative-impl-truth (truth/induction negative-truth precond-truth)

        ;; Create negative version of implication
        negative-impl (implication/make-implication
                       (:term impl)
                       negative-impl-truth
                       (:occurrence-time-offset impl)
                       (:created-time anticipation)
                       {:stamp (:stamp impl)})

        ;; Revise existing implication with negative evidence
        concept-key (:representation concept-term)
        impl-key (:representation (:term impl))

        existing-impl (get-in state [:concepts concept-key :implications impl-key])
        revised-impl (if existing-impl
                      (implication/revise-implication existing-impl negative-impl)
                      negative-impl)]

    ;; Update state with revised implication
    (assoc-in state [:concepts concept-key :implications impl-key] revised-impl)))

(defn check-anticipations
  "Check if anticipations were satisfied or failed.

  For each active anticipation:
  - Check if predicted event occurred
  - If deadline passed without occurrence, add negative evidence
  - Remove satisfied or expired anticipations

  Args:
    state - Current NAR state
    current-time - Current system time

  Returns:
    [updated-state remaining-anticipations]"
  [state current-time]
  (let [anticipations (get state :anticipations [])

        ;; Partition into expired and active
        {expired true, active false}
        (group-by #(> current-time (:deadline %)) anticipations)

        ;; Add negative evidence for expired anticipations
        state-with-negatives
        (reduce (fn [s anticipation]
                  (let [postcondition (implication/get-postcondition
                                       (:implication anticipation))]
                    (add-negative-confirmation s anticipation postcondition)))
                state
                expired)]

    [state-with-negatives (vec active)]))

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
