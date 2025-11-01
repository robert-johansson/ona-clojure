(ns ona.decision
  "Decision making and goal realization for ONA.

  Core algorithm:
  1. Given goal G!, find implications <A =/> G>
  2. Check if precondition A is believed
  3. Calculate desire = deduction(belief(A), <A =/> G>, goal(G))
  4. If desire > threshold, execute action
  5. Otherwise, derive subgoal A!"
  (:require [ona.core :as core]
            [ona.event :as event]
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

  For sensorimotor implications like <(context &/ ^op) =/> goal>,
  extracts the operation ^op from the precondition sequence.

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
    (let [precondition-term (implication/get-precondition impl)

          ;; CRITICAL FIX: Extract operation from sequence if present
          ;; For sensorimotor implications like <(context &/ ^left) =/> goal>,
          ;; we need to extract ^left, not use the whole sequence
          [extracted-op remaining-context] (term/extract-operation-from-sequence precondition-term)

          ;; Use extracted operation if found, otherwise use precondition as-is
          ;; (for non-sensorimotor implications like <condition =/> goal>)
          operation-term (or extracted-op precondition-term)

          ;; Step 1: Calculate desire for precondition using goal deduction
          ;; From C ONA: Inference_GoalDeduction(goal, impl)
          ;; Result: desire((context &/ ^op)!) based on goal and implication
          contextual-operation (goal-deduction goal impl current-time)

          ;; Step 2: Apply sequence deduction with belief
          ;; From C ONA: Inference_GoalSequenceDeduction(contextualOp, belief)
          ;; This combines the contextual operation desire with the belief truth
          ;; CRITICAL: We must use the BELIEF truth, not just impl truth!
          belief-truth (:truth precondition-belief)
          contextual-op-truth (:truth contextual-operation)

          ;; Apply deduction: desire(^op!) = deduction(desire((ctx &/ ^op)!), belief(ctx))
          final-desire-truth (truth/deduction contextual-op-truth belief-truth)
          desire-expectation (truth/expectation final-desire-truth)

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
  (let [goal-term (:term goal)
        debug? (:debug state)
        all-concepts (vals (:concepts state))]

    ;; DEBUG logging
    (when debug?
      (println (str "// DEBUG[find-matching-implications]: Goal = " (term/format-term goal-term)))
      (println (str "// DEBUG[find-matching-implications]: Scanning " (count all-concepts) " concepts")))

    ;; Scan all concepts for implications
    ;; CRITICAL: Only scan operation tables 1-10 (procedural), skip table 0 (declarative)
    ;; Matches C ONA: for(int opi=1; opi<=OPERATIONS_MAX; opi++) (Decision.c:423-477)
    (let [results
          (for [concept all-concepts
                :let [concept-term (:term concept)]
                opi (range 1 11)  ; Tables 1-10 ONLY, skip 0
                :let [impl-table (nth (:precondition-beliefs concept) opi)
                      impls (vals impl-table)]
                :when (seq impls)  ; Only process if table has implications
                :let [_ (when debug?
                          (println (str "// DEBUG[find-matching-implications]:   Concept " (term/format-term concept-term)
                                       " table[" opi "] has " (count impls) " implications")))]
                impl impls
                :let [postcondition (implication/get-postcondition impl)
                      _ (when debug?
                          (println (str "// DEBUG[find-matching-implications]:     Impl: " (term/format-term (:term impl))))
                          (println (str "// DEBUG[find-matching-implications]:       Postcond: " (term/format-term postcondition))))
                      ;; NAL-6: Unify postcondition (pattern) with goal (specific)
                      substitution (var/unify postcondition goal-term)
                      _ (when debug?
                          (println (str "// DEBUG[find-matching-implications]:       Match: " (:success substitution))))]
                :when (:success substitution)]
            [impl concept substitution])]

      (when debug?
        (println (str "// DEBUG[find-matching-implications]: Found " (count results) " matching implications")))

      results)))

(defn get-sequence-components
  "Extract all components from a sequence term.
   For sequences like (A &/ B), returns [A B].
   For nested sequences like ((A &/ B) &/ C), returns [A B C].
   For non-sequences, returns [term]."
  [term]
  (if (and (= (:type term) :compound)
           (= (:copula term) "&/"))
    ;; It's a sequence - recursively extract components
    (concat (get-sequence-components (:subject term))
            (get-sequence-components (:predicate term)))
    ;; Not a sequence - return as single-element list
    [term]))

(defn context-has-recent-beliefs?
  "Check if a context term (possibly a sequence) has recent beliefs.
   For atomic terms: check if the concept has a recent belief-spike.
   For sequences: check if ALL components have recent beliefs."
  [state context-term current-time]
  (let [components (get-sequence-components context-term)
        debug? (:debug state)]
    ;; Debug logging
    (when debug?
      (println (str "// DEBUG: checking context " (term/format-term context-term)))
      (println (str "// DEBUG:   components: " (mapv term/format-term components))))

    ;; Check if ALL components have recent beliefs
    (every? (fn [component]
              (let [key (core/get-concept-key component)]
                (when debug?
                  (println (str "// DEBUG:   component " (term/format-term component)
                               " → key " (term/format-term key))))
                (if-let [concept (get-in state [:concepts key])]
                  (do
                    (when debug?
                      (println (str "// DEBUG:     concept found, belief-spike: "
                                   (if (:belief-spike concept) "YES" "NO"))))
                    (if-let [belief-spike (:belief-spike concept)]
                      (let [is-belief (= (:type belief-spike) :belief)
                            occ-time (:occurrence-time belief-spike)
                            age (- current-time occ-time)
                            is-recent (<= age 20)]
                        (when debug?
                          (println (str "// DEBUG:       type=" (:type belief-spike)
                                       ", occ-time=" occ-time
                                       ", current-time=" current-time
                                       ", age=" age
                                       ", recent=" is-recent)))
                        (and is-belief is-recent))
                      (do
                        (when debug?
                          (println "// DEBUG:       NO belief-spike"))
                        false)))
                  (do
                    (when debug?
                      (println "// DEBUG:     concept NOT found"))
                    false))))
            components)))

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
  (let [debug? (:debug state)
        ;; Step 1: Find implications whose postcondition unifies with goal
        matches (find-matching-implications state goal)

        ;; Step 2-5: For each matching implication, check if precondition context is believed
        ;; CRITICAL: Following C ONA, we check the context WITHOUT operations
        ;; For <((context &/ ^op) =/> goal>, we check if 'context' concept has recent belief
        decisions
        (for [[impl sequence-concept goal-subst] matches
              :let [;; Get precondition from implication
                    precondition-term (implication/get-precondition impl)

                    ;; CRITICAL: Strip operations to get context
                    ;; E.g., (context &/ ^op) → context
                    context-term (term/get-precondition-without-op precondition-term)]

              ;; Check if context has recent beliefs (handles both atomic and sequence contexts)
              :when (context-has-recent-beliefs? state context-term current-time)

              ;; For sequences, use the first component's belief as representative
              :let [components (get-sequence-components context-term)
                    first-component (first components)
                    first-key (core/get-concept-key first-component)
                    first-concept (get-in state [:concepts first-key])
                    belief-spike (:belief-spike first-concept)]

              ;; Now we have: implication with operation, and belief for context
              :let [;; Apply goal substitution to implication (for variable cases)
                    impl-after-goal (var/substitute (:term impl) goal-subst)

                    ;; Create instantiated implication with belief
                    fully-instantiated-impl (implication/make-implication
                                              impl-after-goal
                                              (:truth impl)
                                              (:occurrence-time-offset impl)
                                              (:creation-time impl)
                                              {:stamp (:stamp impl)})]]

          ;; Step 5: Calculate desire using first component's belief spike
          (let [decision (consider-implication goal fully-instantiated-impl belief-spike current-time)
                ;; Count components in context for specificity ranking
                ;; More specific (more components) should be preferred
                specificity (count components)]
            (assoc decision :specificity specificity)))

        ;; Select best by SPECIFICITY first, then by desire
        ;; This ensures compound conditions (red & bright) are preferred over simple ones (bright)
        best (reduce (fn [best current]
                       (when debug?
                         (println (str "// DEBUG[best-candidate]: Comparing decisions"))
                         (println (str "// DEBUG[best-candidate]:   current: op=" (term/format-term (:operation-term current))
                                      " specificity=" (:specificity current)
                                      " desire=" (format "%.3f" (:desire current))))
                         (println (str "// DEBUG[best-candidate]:   best:    op=" (term/format-term (:operation-term best))
                                      " specificity=" (:specificity best)
                                      " desire=" (format "%.3f" (:desire best)))))
                       (let [result (cond
                                      ;; If current is more specific, always prefer it
                                      (> (:specificity current) (:specificity best))
                                      current

                                      ;; If same specificity, prefer higher desire
                                      (and (= (:specificity current) (:specificity best))
                                           (> (:desire current) (:desire best)))
                                      current

                                      ;; Otherwise keep best
                                      :else
                                      best)]
                         (when debug?
                           (println (str "// DEBUG[best-candidate]:   → selected: " (term/format-term (:operation-term result)))))
                         result))
                     (assoc (null-decision) :specificity 0)  ; null-decision has 0 specificity
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
        concept-key (core/get-concept-key concept-term)
        impl-term (:term impl)
        opi (core/get-operation-index impl-term state)

        existing-impl (get-in state [:concepts concept-key :precondition-beliefs opi impl-term])
        revised-impl (if existing-impl
                      (implication/revise-implication existing-impl negative-impl)
                      negative-impl)]

    ;; Update state with revised implication in operation-indexed table
    (assoc-in state [:concepts concept-key :precondition-beliefs opi impl-term] revised-impl)))

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
  (let [debug? (:debug state)
        _ (when debug?
            (println (str "// DEBUG[suggest-decision]: CALLED for goal " (term/format-term (:term goal)))))

        ;; Try motor babbling with certain probability
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

  ;; Add implication <key-pressed =/> door-opened>
  ;; Note: This will be stored in the "door-opened" concept (postcondition)
  (def impl (implication/make-implication
             (term/make-temporal-implication
              (term/make-atomic-term "key-pressed")
              (term/make-atomic-term "door-opened"))
             (truth/make-truth 0.9 0.8)
             10.0
             100))
  (def state (core/add-implication state impl))

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
