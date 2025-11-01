(ns ona.core
  "Core NAR state and operations"
  (:require [ona.term :as term]
            [ona.truth :as truth]
            [ona.event :as event]
            [ona.implication :as implication]
            [ona.operation :as operation]
            [ona.config :as config]
            [clojure.data.priority-map :refer [priority-map]]))

;; =============================================================================
;; Concept Structure
;; =============================================================================

(defrecord Concept
  [term                  ; Term - the concept's identifier
   priority              ; double - attention priority
   usefulness            ; double - long-term usefulness
   use-count             ; long - number of times used
   last-used             ; long - last usage time
   belief                ; Event - best eternal belief
   belief-spike          ; Event - most recent belief event
   predicted-belief      ; Event - predicted belief (from forward chaining)
   active-prediction     ; Prediction - current prediction awaiting validation
   precondition-beliefs  ; Vector[11] of Maps - operation-indexed implication tables
   implication-links     ; Map - non-operational implications
   ])

(defn make-concept
  "Create a new concept with default values.

  Initializes precondition-beliefs as vector of 11 empty maps:
  - Index 0: Declarative temporal implications (no operation)
  - Index 1-10: Procedural implications per registered operation

  Matches C ONA Concept.precondition_beliefs[OPERATIONS_MAX+1]"
  [term]
  (map->Concept
   {:term term
    :priority 0.5
    :usefulness 0.5
    :use-count 0
    :last-used 0
    :belief nil
    :belief-spike nil
    :predicted-belief nil
    :active-prediction nil
    :precondition-beliefs (vec (repeat 11 {}))  ; 11 empty maps (indices 0-10)
    :implication-links {}}))

(defn get-operation-index
  "Determine which precondition_beliefs table to use for implication.

  Follows C ONA logic (Memory.c:298-320):
  - Extract subject from <subject =/> predicate>
  - If subject is sequence, check rightmost term
  - If rightmost is operation, get its ID (1-10)
  - Otherwise return 0 (declarative temporal)

  Args:
    impl-term - Implication term
    state - NAR state (for operation lookup)

  Returns:
    Integer 0-10:
    - 0 = no operation (declarative)
    - 1-10 = operation ID (procedural)"
  [impl-term state]
  (let [subject (term/get-subject impl-term)]
    (if (term/sequence? subject)
      ;; Check rightmost term in sequence (predicate is rightmost in binary tree)
      (let [rightmost (:predicate subject)]
        (if (term/operation-term? rightmost)
          ;; Look up operation ID
          (or (operation/get-operation-id state rightmost) 0)
          0))
      0)))

;; =============================================================================
;; NAR State
;; =============================================================================

(defrecord NARState
  [concepts           ; Map of term -> Concept
   current-time       ; long - current system time
   volume             ; int - output verbosity (0-100)
   stamp-id           ; long - next stamp ID
   cycling-beliefs    ; Priority map of belief events
   cycling-goals      ; Priority map of goal events
   operations         ; Map of registered operations
   config             ; Configuration map
   last-execution     ; String - name of last executed operation (for experiments)
   ])

(defn init-state
  "Initialize NAR state"
  []
  (map->NARState
   {:concepts {}
    :current-time 1
    :volume 100  ; Default to full output
    :debug false ; Default debug traces off (use *debug=1 to enable)
    :stamp-id 1
    :cycling-beliefs (priority-map)
    :cycling-goals (priority-map)
    :operations {}
    :config config/default-config
    :last-execution nil}))

(defn reset-state
  "Reset NAR state (implements *reset command)"
  []
  (init-state))

;; =============================================================================
;; Concept Operations
;; =============================================================================

(defn get-concept-key
  "Get the key to use for indexing a concept.

  For atomic terms: return the term itself
  For inheritance (<A --> B>): return the subject A
  For other compounds: return the term itself

  This aligns with C ONA's architecture where concepts are indexed by
  atomic terms, and inheritance statements store beliefs on the subject's concept.

  Examples:
  - 'context' → 'context' (atomic)
  - '<context --> present>' → 'context' (subject of inheritance)
  - '(A &/ B)' → '(A &/ B)' (other compound)"
  [term]
  (if (and (term/compound-term? term)
           (= (:copula term) term/copula-inheritance))
    ;; Inheritance: use subject as key
    (:subject term)
    ;; All others: use term itself
    term))

(defn get-concept
  "Get concept by term, creating if necessary.

  Uses get-concept-key to determine the proper indexing key.
  For inheritance terms like <A --> B>, this indexes by the subject A."
  [state term]
  (let [key (get-concept-key term)]
    (if-let [concept (get-in state [:concepts key])]
      [state concept]
      (let [new-concept (make-concept key)
            new-state (assoc-in state [:concepts key] new-concept)]
        [new-state new-concept]))))

(defn update-concept-belief
  "Update concept with a new belief event.

  Logic:
  - If eternal, store as :belief (best eternal belief)
  - If temporal, store as :belief-spike (most recent spike)
  - Also update priority based on event"
  [concept event current-time]
  (let [updated (if (event/eternal? event)
                  (assoc concept :belief event)
                  (assoc concept :belief-spike event))
        ;; Update usage stats
        updated (-> updated
                    (update :use-count inc)
                    (assoc :last-used current-time)
                    ;; Boost priority when new evidence arrives
                    (update :priority (fn [p] (min 1.0 (+ p 0.1)))))]
    updated))

(defn update-concept-prediction
  "Update concept with a predicted belief event.

  Predictions are stored separately from regular beliefs
  to enable comparison when actual outcomes arrive.

  Args:
    concept - Concept to update
    predicted-event - Predicted belief event
    current-time - Current system time

  Returns:
    Updated concept"
  [concept predicted-event current-time]
  (-> concept
      (assoc :predicted-belief predicted-event)
      (update :use-count inc)
      (assoc :last-used current-time)))

(defn add-event
  "Add an event (belief or goal) to the system.

  Flow:
  1. Update the concept with the event
  2. Add event to appropriate cycling queue
  3. Return updated state

  Args:
    state - Current NAR state
    event - Event to add (from ona.event namespace)

  Returns:
    Updated state"
  [state event]
  (let [term (:term event)
        current-time (:current-time state)
        event-type (:type event)

        ;; Get or create concept and update it
        [state concept] (get-concept state term)
        concept-key (get-concept-key term)  ; Use proper key for storage
        updated-concept (update-concept-belief concept event current-time)
        state (assoc-in state [:concepts concept-key] updated-concept)

        ;; Add to appropriate queue with priority
        priority (event/event-priority event)
        queue-key (case event-type
                    :belief :cycling-beliefs
                    :goal :cycling-goals
                    :cycling-beliefs)  ; default to beliefs

        ;; Use event as value, priority as key in priority-map
        state (update state queue-key assoc event priority)]
    state))

(defn add-input-belief
  "Convenience function to add a belief from external input.

  Args:
    state - Current NAR state
    term - Term (from ona.term)
    truth - Truth value (from ona.truth)
    eternal? - Whether this is eternal knowledge (vs temporal)

  Returns:
    Updated state"
  [state term truth eternal?]
  (let [current-time (:current-time state)
        occurrence-time (if eternal? event/eternal-occurrence current-time)
        belief-event (event/make-belief term truth occurrence-time current-time
                                        {:input? true})]
    (add-event state belief-event)))

(defn add-input-goal
  "Convenience function to add a goal from external input.

  Args:
    state - Current NAR state
    term - Term (from ona.term)
    truth - Truth value (from ona.truth)
    eternal? - Whether this is eternal goal (vs temporal)

  Returns:
    Updated state"
  [state term truth eternal?]
  (let [current-time (:current-time state)
        occurrence-time (if eternal? event/eternal-occurrence current-time)
        goal-event (event/make-goal term truth occurrence-time current-time
                                    {:input? true})]
    (add-event state goal-event)))

(defn advance-time
  "Advance system time by n cycles (default 1).

  In C ONA, time advances with each Cycle_Perform().
  Here we just increment the counter - actual cycle execution
  will be implemented later.

  Args:
    state - Current NAR state
    cycles - Number of cycles to advance (default 1)

  Returns:
    Updated state"
  ([state]
   (advance-time state 1))
  ([state cycles]
   (update state :current-time + cycles)))

;; =============================================================================
;; Implication Management
;; =============================================================================

(defn add-implication
  "Add an implication to a concept's implication table.

  CRITICAL: C ONA stores implications in the POSTCONDITION (goal) concept!
  For implication <A =/> G>, store it in concept G (not A).

  This enables goal-driven decision making:
  When goal G! is input, system finds all implications <? =/> G>
  by looking in concept G's implication table.

  If an implication with the same term already exists, revise them together.

  Args:
    state - Current NAR state
    impl - Implication to add

  Returns:
    Updated state"
  [state impl]
  (let [;; Extract postcondition (goal) from implication <A =/> B> → B
        postcondition-term (implication/get-postcondition impl)
        [state concept] (get-concept state postcondition-term)
        concept-key (get-concept-key postcondition-term)  ; Use proper key for storage
        impl-term (:term impl)

        ;; NEW: Determine operation index (0-10) following C ONA Memory.c:298-328
        opi (get-operation-index impl-term state)

        ;; Check for existing implication in operation-indexed table
        existing-impl (get-in concept [:precondition-beliefs opi impl-term])

        ;; Revise if exists, otherwise add new
        final-impl (if existing-impl
                     (implication/revise-implication existing-impl impl)
                     impl)

        ;; Update concept - store in operation-indexed table
        updated-concept (assoc-in concept [:precondition-beliefs opi impl-term] final-impl)
        updated-concept (-> updated-concept
                            (update :use-count inc)
                            (assoc :last-used (:current-time state)))]

    (assoc-in state [:concepts concept-key] updated-concept)))

(defn get-implications
  "Get all implications from a concept.

  Returns implications from ALL tables (0-10) for completeness.
  Use this for queries and display, NOT for decision-making.

  Args:
    state - Current NAR state
    term - Term of the concept

  Returns:
    Vector of implications (or empty vector if concept doesn't exist)"
  [state term]
  (let [key (get-concept-key term)]
    (if-let [concept (get-in state [:concepts key])]
      (vec (mapcat vals (:precondition-beliefs concept)))
      [])))

(defn get-implication
  "Get a specific implication from a concept.

  Must search all tables (0-10) since we don't know which table it's in.

  Args:
    state - Current NAR state
    precondition-term - Precondition term (concept key)
    implication-term - Full implication term <A =/> B>

  Returns:
    Implication or nil if not found"
  [state precondition-term implication-term]
  (let [key (get-concept-key precondition-term)]
    ;; Search all 11 tables (0-10)
    (some (fn [table-idx]
            (get-in state [:concepts key :precondition-beliefs table-idx implication-term]))
          (range 11))))

(defn get-last-execution
  "Get the name of the last executed operation.

  Args:
    state - Current NAR state

  Returns:
    String of last executed operation name (e.g., \"^left\") or nil"
  [state]
  (:last-execution state))

;; =============================================================================
;; Statistics
;; =============================================================================

(defn calculate-stats
  "Calculate system statistics (implements *stats command)"
  [state]
  {:total-concepts (count (:concepts state))
   :current-time (:current-time state)
   :belief-events-count (count (:cycling-beliefs state))
   :goal-events-count (count (:cycling-goals state))
   :average-concept-priority (if (empty? (:concepts state))
                                0.0
                                (/ (reduce + (map :priority (vals (:concepts state))))
                                   (count (:concepts state))))})
