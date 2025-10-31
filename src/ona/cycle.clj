(ns ona.cycle
  "Event processing cycle for ONA.

  Each cycle:
  1. Selects high-priority events from queues
  2. Applies inference rules
  3. Stores derived events and implications
  4. Processes goals through decision making"
  (:require [ona.core :as core]
            [ona.event :as event]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.inference :as inference]
            [ona.decision :as decision]
            [ona.operation :as operation]
            [ona.prediction :as prediction]
            [ona.inverted-atom-index :as idx]
            [ona.variable :as var]
            [clojure.data.priority-map :as pm]))

;; =============================================================================
;; Constants
;; =============================================================================

(def belief-event-selections 1)  ;; How many belief events to process per cycle
(def goal-event-selections 1)    ;; How many goal events to process per cycle
(def min-events-for-sequence 2)  ;; Need at least 2 events to form sequence

;; =============================================================================
;; Event Selection
;; =============================================================================

(defn pop-highest-priority-events
  "Pop N highest priority events from a priority queue.

  Args:
    pq - Priority map (event -> priority)
    n - Number to pop

  Returns:
    [remaining-queue selected-events]"
  [pq n]
  (loop [queue pq
         selected []
         count 0]
    (if (or (>= count n) (empty? queue))
      [queue selected]
      (let [[event priority] (peek queue)]
        (recur (pop queue)
               (conj selected event)
               (inc count))))))

;; =============================================================================
;; RELATED_CONCEPTS Pattern (Cycle.c lines 31-46)
;; =============================================================================

(defn related-concepts
  "Find concepts semantically related to an event term using InvertedAtomIndex.

  Implements the RELATED_CONCEPTS_FOREACH pattern from C ONA Cycle.c.
  This is a critical optimization that avoids O(n) scan of all concepts.

  NAL-6 Enhancement: Also includes variable concepts that could potentially
  unify with any term.

  Algorithm:
  1. Extract atoms from event term
  2. For each atom, lookup concepts in InvertedAtomIndex
  3. Add all concepts with variable terms (they can unify with anything)
  4. Return union of all matching concept Terms

  From C ONA Cycle.c:31-46:
    #define RELATED_CONCEPTS_FOREACH(CONCEPT, TERM, CODE) { \\
        HashTable_Borrow(&HT, &HTconcepts); \\
        for(...) { \\
            CONCEPT = HashTable_Get(&HTconcepts, TERM->atoms[i]); \\
            CODE \\
        } \\
    }

  Args:
    state - Current NAR state
    event-term - Term to find related concepts for

  Returns:
    Set of related concept Term objects (used as keys in :concepts map)"
  [state event-term]
  (let [;; Get concepts that share atoms
        atom-related (if-let [index (:inverted-atom-index state)]
                       (idx/related-concepts index event-term)
                       (set (keys (:concepts state))))

        ;; NAL-6: Add all concepts with variables (they can unify with anything)
        variable-concepts (filter var/has-variable? (keys (:concepts state)))]

    (into atom-related variable-concepts)))

(defn related-concept-map
  "Get actual concept records for related terms.

  Args:
    state - Current NAR state
    event-term - Term to find related concepts for

  Returns:
    Map of {concept-term → concept-record} for related concepts"
  [state event-term]
  (let [related-terms (related-concepts state event-term)]
    (into {}
          (keep (fn [term]
                  (when-let [concept (get-in state [:concepts term])]
                    [term concept]))
                related-terms))))

;; =============================================================================
;; Temporal Sequence Mining
;; =============================================================================

(defn find-recent-events
  "Find recent events for a given term within time window.

  Args:
    state - Current NAR state
    term - Term to look for
    current-time - Current system time
    time-window - How far back to look (default 20)

  Returns:
    Vector of recent events for this term"
  ([state term current-time]
   (find-recent-events state term current-time 20))
  ([state term current-time time-window]
   (if-let [concept (get-in state [:concepts term])]
     (let [spike (:belief-spike concept)]
       (if (and spike
                (not (event/eternal? spike))
                (<= (- current-time (:occurrence-time spike)) time-window))
         [spike]
         []))
     [])))

(defn mine-temporal-sequences
  "Mine temporal sequences from a new event and recent history.

  C ONA approach - creates compound implications:
  - For each new event C, look for recent events/sequences where they occurred before C
  - Derive sequences from individual events: (A &/ B)
  - Derive implications from both:
    - Individual events to new event: <A =/> C>
    - Sequences to new event: <(A &/ B) =/> C>  (COMPOUND IMPLICATION)

  Only mines from INPUT events (not derived sequences) to avoid infinite recursion.

  Args:
    state - Current NAR state
    new-event - Newly added event
    current-time - Current system time

  Returns:
    [sequences implications] - Vectors of derived sequences and implications"
  [state new-event current-time]
  (if (or (not= (:type new-event) :belief)
          (event/eternal? new-event)
          (not (:input? new-event)))  ;; Only mine from input events
    [[] []]  ;; Skip derived events, eternal events, non-beliefs
    (let [projection-decay (get-in state [:config :truth-projection-decay])
          new-time (:occurrence-time new-event)

          ;; Find all recent events/sequences from all concepts
          ;; Allow both input events AND sequences (for recursive extension)
          max-seq-len (get-in state [:config :max-sequence-len] 3)
          max-timediff (get-in state [:config :max-sequence-timediff] 20)
          all-concepts (vals (:concepts state))

          recent-events (mapcat
                         (fn [concept]
                           (let [spike (:belief-spike concept)]
                             (if (and spike
                                      (not (event/eternal? spike))
                                      (< (:occurrence-time spike) new-time)
                                      (<= (- new-time (:occurrence-time spike)) max-timediff)
                                      ;; Only extend sequences if they're not at max length
                                      (if (term/sequence? (:term spike))
                                        (< (term/sequence-length (:term spike)) max-seq-len)
                                        (:input? spike)))  ;; Non-sequences must be input events
                               [spike]
                               [])))
                         all-concepts)

          ;; Find recent SEQUENCE events (these can be preconditions for implications)
          ;; These are sequences that can form compound implications: <(A &/ B) =/> C>
          recent-sequences (mapcat
                            (fn [concept]
                              (let [spike (:belief-spike concept)]
                                (if (and spike
                                         (not (event/eternal? spike))
                                         (term/sequence? (:term spike))  ;; Only sequences
                                         (< (:occurrence-time spike) new-time)
                                         (<= (- new-time (:occurrence-time spike)) max-timediff))
                                  [spike]
                                  [])))
                            all-concepts)

          ;; Form sequences from individual events: (A &/ B)
          sequence-results (map
                            (fn [old-event]
                              (let [[seq-success? seq-event]
                                    (inference/belief-intersection old-event new-event
                                                                   current-time
                                                                   projection-decay)]
                                (when seq-success? seq-event)))
                            recent-events)

          sequences (filterv some? sequence-results)

          ;; Form implications from individual events to new event: <A =/> C>
          individual-implications (map
                                   (fn [old-event]
                                     (let [[impl-success? impl]
                                           (inference/belief-induction old-event new-event
                                                                       current-time
                                                                       projection-decay)]
                                       (when impl-success? impl)))
                                   recent-events)

          ;; Form implications from sequences to new event: <(A &/ B) =/> C>
          ;; This is the C ONA compound implication approach!
          sequence-implications (map
                                 (fn [seq-event]
                                   (let [[impl-success? impl]
                                         (inference/belief-induction seq-event new-event
                                                                     current-time
                                                                     projection-decay)]
                                     (when impl-success? impl)))
                                 recent-sequences)

          implications (filterv some? (concat individual-implications sequence-implications))]

      [sequences implications])))

;; =============================================================================
;; Derived Event/Implication Storage
;; =============================================================================

(defn store-derived-event
  "Store a derived event back into the system.

  Args:
    state - Current NAR state
    derived-event - Event to store

  Returns:
    Updated state"
  [state derived-event]
  (core/add-event state derived-event))

(defn store-derived-implication
  "Store a derived implication in the appropriate concept.

  Args:
    state - Current NAR state
    impl - Implication to store

  Returns:
    Updated state"
  [state impl]
  (let [precondition-term (term/get-subject (:term impl))]
    (core/add-implication state impl precondition-term)))

;; =============================================================================
;; Forward Chaining (Belief Deduction)
;; =============================================================================

(defn apply-forward-chaining
  "Apply forward chaining from a belief event using RELATED_CONCEPTS.

  NAL-6 Enhanced: Uses RELATED_CONCEPTS pattern with variable unification.

  Algorithm:
  1. Find concepts related to belief term (via InvertedAtomIndex)
  2. For each related concept's implications:
     a. Unify implication precondition with belief term
     b. If unification succeeds, apply substitution
     c. Predict postcondition via belief-deduction
  3. Store predictions for validation

  This enables variable-based forward chaining:
  - Belief: bird.
  - Related concept has: <$1 =/> flies>
  - Unification: $1 ← bird
  - Prediction: flies.

  Args:
    state - Current NAR state
    belief-event - Belief event to chain from
    current-time - Current system time

  Returns:
    Updated state with predictions stored"
  [state belief-event current-time]
  (let [belief-term (:term belief-event)
        projection-decay (get-in state [:config :truth-projection-decay])

        ;; RELATED_CONCEPTS: Find all concepts that share atoms with belief
        related-concepts (related-concept-map state belief-term)]

    ;; Process implications from ALL related concepts
    (reduce-kv
     (fn [state concept-term concept]
       (let [implications (vals (:implications concept))]
         ;; Try each implication
         (reduce
          (fn [state impl]
            (let [precondition (term/get-subject (:term impl))

                  ;; NAL-6: Unify implication precondition with belief term
                  substitution (var/unify precondition belief-term)]

              (if (:success substitution)
                ;; Unification succeeded - apply substitution and predict
                (let [;; Substitute variables in implication
                      instantiated-impl-term (var/substitute (:term impl) substitution)
                      instantiated-impl (assoc impl :term instantiated-impl-term)

                      ;; Apply belief deduction with instantiated implication
                      [success? predicted-event]
                      (inference/belief-deduction belief-event instantiated-impl
                                                  current-time projection-decay)]

                  (if success?
                    ;; Store prediction in postcondition concept
                    (let [postcondition-term (:term predicted-event)
                          [state postcondition-concept] (core/get-concept state postcondition-term)

                          ;; Create Prediction record for validation
                          pred (prediction/make-prediction predicted-event
                                                           instantiated-impl
                                                           belief-term
                                                           current-time)

                          ;; Update concept with prediction
                          updated-concept (-> postcondition-concept
                                              (assoc :predicted-belief predicted-event)
                                              (assoc :active-prediction pred)
                                              (update :use-count inc)
                                              (assoc :last-used current-time))]
                      (assoc-in state [:concepts postcondition-term] updated-concept))
                    state))
                ;; Unification failed - skip this implication
                state)))
          state
          implications)))
     state
     related-concepts)))

;; =============================================================================
;; Prediction Validation
;; =============================================================================

(defn validate-predictions-for-event
  "Check if an incoming belief event confirms or refutes any predictions.

  For the concept matching this event's term, check if there's an active
  prediction and validate it. If confirmed/refuted, revise the source implication.

  Args:
    state - Current NAR state
    belief-event - New belief event to validate against predictions
    current-time - Current system time

  Returns:
    Updated state with revised implications"
  [state belief-event current-time]
  (let [event-term (:term belief-event)
        concept (get-in state [:concepts event-term])]

    (if (and concept (:active-prediction concept))
      (let [active-pred (:active-prediction concept)
            config (:config state)

            ;; Validate prediction against observed event
            [status updated-pred] (prediction/validate-prediction
                                   active-pred
                                   belief-event
                                   current-time
                                   config)]

        (case status
          ;; Prediction confirmed - strengthen implication
          :confirmed
          (let [source-impl (:source-implication active-pred)
                source-term (:source-concept-term active-pred)
                pred-event (:predicted-event active-pred)

                ;; Revise implication on confirmation
                revised-impl (prediction/revise-on-confirmation
                             source-impl
                             pred-event
                             belief-event
                             current-time)

                ;; Update source concept's implication
                source-concept (get-in state [:concepts source-term])
                impl-term (:term source-impl)
                updated-source (assoc-in source-concept
                                        [:implications impl-term]
                                        revised-impl)

                ;; Clear prediction from current concept
                updated-concept (assoc concept :active-prediction nil)]

            (when (= (:volume state) 100)
              (println (str "^prediction-confirmed: " (term/format-term event-term))))

            ;; Update both concepts
            (-> state
                (assoc-in [:concepts source-term] updated-source)
                (assoc-in [:concepts event-term] updated-concept)))

          ;; Prediction refuted - weaken implication
          :refuted
          (let [source-impl (:source-implication active-pred)
                source-term (:source-concept-term active-pred)
                pred-event (:predicted-event active-pred)

                ;; Revise implication on refutation
                revised-impl (prediction/revise-on-refutation
                             source-impl
                             pred-event
                             belief-event
                             current-time)

                ;; Update source concept's implication
                source-concept (get-in state [:concepts source-term])
                impl-term (:term source-impl)
                updated-source (assoc-in source-concept
                                        [:implications impl-term]
                                        revised-impl)

                ;; Clear prediction from current concept
                updated-concept (assoc concept :active-prediction nil)]

            (when (= (:volume state) 100)
              (println (str "^prediction-refuted: " (term/format-term event-term))))

            ;; Update both concepts
            (-> state
                (assoc-in [:concepts source-term] updated-source)
                (assoc-in [:concepts event-term] updated-concept)))

          ;; Otherwise (pending, timeout, already-resolved): no change
          state))

      ;; No active prediction for this concept
      state)))

;; =============================================================================
;; Main Cycle
;; =============================================================================

(defn process-belief-events
  "Process selected belief events to derive sequences and implications.

  Also validates predictions against incoming observations.

  Args:
    state - Current NAR state
    selected-events - Events selected for processing

  Returns:
    Updated state with derived knowledge"
  [state selected-events]
  (let [current-time (:current-time state)]
    (reduce
     (fn [state event]
       ;; Validate predictions for this event
       (let [state (validate-predictions-for-event state event current-time)

             ;; Mine temporal sequences and implications
             [sequences implications] (mine-temporal-sequences state event current-time)

             ;; Store derived sequences as events
             state (reduce store-derived-event state sequences)

             ;; Store derived implications in concepts
             state (reduce store-derived-implication state implications)

             ;; Apply forward chaining (belief deduction)
             state (apply-forward-chaining state event current-time)]

         state))
     state
     selected-events)))

(defn try-execute-operation
  "Attempt to execute an operation from a decision.

  For now, this looks for operation terms directly.
  Future: extract operations from sequences/implications.

  Args:
    state - Current NAR state
    decision - Decision record

  Returns:
    [executed? result-event state]
    - executed? boolean indicating if operation was run
    - result-event event representing execution outcome (or nil)
    - state potentially updated state"
  [state decision]
  (let [op-term (:operation-term decision)
        current-time (:current-time state)]
    ;; Check if this is a registered operation
    (if-let [operation (operation/get-operation-by-term state op-term)]
      ;; Execute the operation
      (let [exec-result (operation/execute-operation operation {})
            ;; Create event for the execution
            exec-event (event/make-belief (:executed-term exec-result)
                                          (truth/make-truth 1.0 0.9)
                                          current-time
                                          current-time
                                          {:input? true
                                           :executed? true})]
        [true exec-event state])
      ;; Not a registered operation - just log decision
      [false nil state])))

(defn process-goal-events
  "Process selected goal events through decision making.

  For each goal:
  1. Find matching implications
  2. Select best action
  3. If desire > threshold: execute operation if available
  4. Otherwise: derive subgoal and add to queue

  Args:
    state - Current NAR state
    selected-goals - Goal events selected for processing

  Returns:
    Updated state"
  [state selected-goals]
  (let [current-time (:current-time state)]
    (reduce
     (fn [state goal]
       ;; Suggest decision for this goal
       (let [dec (decision/suggest-decision state goal current-time)]
         (if (:execute? dec)
           ;; High desire - try to execute operation
           (let [[executed? exec-event state] (try-execute-operation state dec)]
             (if executed?
               ;; Operation was executed - add execution event to system
               (do
                 (when (= (:volume state) 100)
                   (println (str "^executed: " (term/format-term (:operation-term dec))
                                " desire=" (format "%.2f" (:desire dec)))))
                 ;; Add execution event back into system
                 (core/add-event state exec-event))
               ;; No registered operation - just log decision
               (do
                 (when (= (:volume state) 100)
                   (println (str "^decide: " (term/format-term (:operation-term dec))
                                " desire=" (format "%.2f" (:desire dec)))))
                 state)))
           ;; Low desire - derive subgoal (backward chaining)
           ;; For now, just log it
           (do
             (when (= (:volume state) 100)
               (println (str "^subgoal: desire too low (" (format "%.2f" (:desire dec)) ")")))
             state))))
     state
     selected-goals)))

(defn perform-cycle
  "Execute one reasoning cycle.

  Flow:
  1. Select high-priority belief and goal events
  2. Process belief events to derive sequences and implications
  3. Process goal events through decision making
  4. Store derived knowledge
  5. Advance time

  Args:
    state - Current NAR state

  Returns:
    Updated state"
  [state]
  (let [;; Step 1: Select events
        [remaining-beliefs selected-beliefs]
        (pop-highest-priority-events (:cycling-beliefs state)
                                     belief-event-selections)

        [remaining-goals selected-goals]
        (pop-highest-priority-events (:cycling-goals state)
                                     goal-event-selections)

        ;; Update state with remaining queues
        state (assoc state
                     :cycling-beliefs remaining-beliefs
                     :cycling-goals remaining-goals)

        ;; Step 2: Process belief events
        state (if (seq selected-beliefs)
                (process-belief-events state selected-beliefs)
                state)

        ;; Step 3: Process goal events
        state (if (seq selected-goals)
                (process-goal-events state selected-goals)
                state)

        ;; Step 4: Advance time
        state (core/advance-time state 1)]

    state))

(defn perform-cycles
  "Execute N reasoning cycles.

  Args:
    state - Current NAR state
    n - Number of cycles to execute

  Returns:
    Updated state"
  [state n]
  (reduce (fn [s _] (perform-cycle s))
          state
          (range n)))

(comment
  ;; Example usage:

  (require '[ona.core :as core]
           '[ona.term :as term]
           '[ona.truth :as truth]
           '[ona.event :as event])

  ;; Initialize
  (def state (core/init-state))

  ;; Add two temporal events
  (def state (core/add-input-belief state
                                     (term/make-atomic-term "a")
                                     (truth/make-truth 1.0 0.9)
                                     false))  ;; temporal

  (def state (core/advance-time state 10))

  (def state (core/add-input-belief state
                                     (term/make-atomic-term "b")
                                     (truth/make-truth 1.0 0.9)
                                     false))  ;; temporal

  ;; Process cycles - should derive (a &/ b) and <a =/> b>
  (def state (perform-cycles state 5))

  ;; Check for implications
  (core/get-implications state (term/make-atomic-term "a"))
  ;; Should see <a =/> b>

  ;; Check concepts
  (keys (:concepts state))
  ;; Should see: a, b, (a &/ b)
  )
