(ns ona.core
  "Core NAR state and operations"
  (:require [ona.term :as term]
            [ona.truth :as truth]
            [ona.event :as event]
            [ona.implication :as implication]
            [ona.config :as config]
            [clojure.data.priority-map :refer [priority-map]]))

;; =============================================================================
;; Concept Structure
;; =============================================================================

(defrecord Concept
  [term               ; Term - the concept's identifier
   priority           ; double - attention priority
   usefulness         ; double - long-term usefulness
   use-count          ; long - number of times used
   last-used          ; long - last usage time
   belief             ; Event - best eternal belief
   belief-spike       ; Event - most recent belief event
   predicted-belief   ; Event - predicted belief (from forward chaining)
   active-prediction  ; Prediction - current prediction awaiting validation
   implications       ; Map of implications
   ])

(defn make-concept
  "Create a new concept with default values"
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
    :implications {}}))

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
   ])

(defn init-state
  "Initialize NAR state"
  []
  (map->NARState
   {:concepts {}
    :current-time 1
    :volume 100  ; Default to full output
    :stamp-id 1
    :cycling-beliefs (priority-map)
    :cycling-goals (priority-map)
    :operations {}
    :config config/default-config}))

(defn reset-state
  "Reset NAR state (implements *reset command)"
  []
  (init-state))

;; =============================================================================
;; Concept Operations
;; =============================================================================

(defn get-concept
  "Get concept by term, creating if necessary"
  [state term]
  (if-let [concept (get-in state [:concepts term])]
    [state concept]
    (let [new-concept (make-concept term)
          new-state (assoc-in state [:concepts term] new-concept)]
      [new-state new-concept])))

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
        updated-concept (update-concept-belief concept event current-time)
        state (assoc-in state [:concepts term] updated-concept)

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

  In C ONA, implications are stored in precondition_beliefs tables indexed by
  operation ID. For now, we use a simple map keyed by implication term.

  If an implication with the same term already exists, revise them together.

  Args:
    state - Current NAR state
    impl - Implication to add
    precondition-term - Term of the precondition (subject of implication)

  Returns:
    Updated state"
  [state impl precondition-term]
  (let [[state concept] (get-concept state precondition-term)
        impl-term (:term impl)
        existing-impl (get-in concept [:implications impl-term])

        ;; Revise if exists, otherwise add new
        final-impl (if existing-impl
                     (implication/revise-implication existing-impl impl)
                     impl)

        ;; Update concept
        updated-concept (assoc-in concept [:implications impl-term] final-impl)
        updated-concept (-> updated-concept
                            (update :use-count inc)
                            (assoc :last-used (:current-time state)))]

    (assoc-in state [:concepts precondition-term] updated-concept)))

(defn get-implications
  "Get all implications from a concept.

  Args:
    state - Current NAR state
    term - Term of the concept

  Returns:
    Vector of implications (or empty vector if concept doesn't exist)"
  [state term]
  (if-let [concept (get-in state [:concepts term])]
    (vec (vals (:implications concept)))
    []))

(defn get-implication
  "Get a specific implication from a concept.

  Args:
    state - Current NAR state
    precondition-term - Precondition term (concept key)
    implication-term - Full implication term <A =/> B>

  Returns:
    Implication or nil if not found"
  [state precondition-term implication-term]
  (get-in state [:concepts precondition-term :implications implication-term]))

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
