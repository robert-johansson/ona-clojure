(ns ona.test-env
  "Test environments with executable operations for ONA testing.

  Provides simple deterministic environments for testing sensorimotor learning:
  - Light switch environment
  - Gridworld navigation
  - Simple control tasks"
  (:require [ona.operation :as operation]
            [ona.core :as core]
            [ona.term :as term]
            [ona.event :as event]
            [ona.truth :as truth]))

;; =============================================================================
;; Light Switch Environment
;; =============================================================================

(def light-state (atom :off))

(defn light-on
  "Turn light on"
  [_args]
  (reset! light-state :on)
  {:state :on})

(defn light-off
  "Turn light off"
  [_args]
  (reset! light-state :off)
  {:state :off})

(defn get-light-state
  "Get current light state as term"
  []
  (if (= @light-state :on)
    (term/parse-term "<light --> on>")
    (term/parse-term "<light --> off>")))

(defn setup-light-environment
  "Setup light switch environment with operations.

  Registers:
  - ^on - turn light on
  - ^off - turn light off

  Args:
    state - NAR state

  Returns:
    Updated state with operations registered"
  [state]
  (reset! light-state :off)
  (-> state
      (operation/register-operation "^on" light-on)
      (operation/register-operation "^off" light-off)))

;; =============================================================================
;; Gridworld Environment
;; =============================================================================

(def agent-position (atom [0 0]))  ; [x y]

(defn move-left [_args]
  (swap! agent-position update 0 dec)
  {:position @agent-position})

(defn move-right [_args]
  (swap! agent-position update 0 inc)
  {:position @agent-position})

(defn move-up [_args]
  (swap! agent-position update 1 inc)
  {:position @agent-position})

(defn move-down [_args]
  (swap! agent-position update 1 dec)
  {:position @agent-position})

(defn get-position-term
  "Get current position as term"
  []
  (let [[x y] @agent-position]
    (term/parse-term (str "<agent --> at_" x "_" y ">"))))

(defn setup-gridworld-environment
  "Setup gridworld navigation environment.

  Registers:
  - ^left - move left
  - ^right - move right
  - ^up - move up
  - ^down - move down

  Args:
    state - NAR state

  Returns:
    Updated state with operations registered"
  [state]
  (reset! agent-position [0 0])
  (-> state
      (operation/register-operation "^left" move-left)
      (operation/register-operation "^right" move-right)
      (operation/register-operation "^up" move-up)
      (operation/register-operation "^down" move-down)))

;; =============================================================================
;; Helper: Execute Operation and Get Outcome
;; =============================================================================

(defn execute-and-observe
  "Execute an operation and generate outcome event.

  Helper for testing: executes operation and returns the resulting
  observation as an event.

  Args:
    state - NAR state
    op-name - Operation name string (e.g., \"^on\")
    outcome-fn - Function () -> Term that produces outcome term

  Returns:
    [state op-event outcome-event]"
  [state op-name outcome-fn]
  (let [current-time (:current-time state)
        op-term (term/parse-term op-name)

        ;; Execute operation
        operation (operation/get-operation-by-term state op-term)
        _ (when operation
            (operation/execute-operation operation {}))

        ;; Create operation event
        op-event (event/make-belief op-term
                                    (truth/make-truth 1.0 0.9)
                                    current-time
                                    current-time
                                    {:input? true :executed? true})

        ;; Create outcome event (get outcome term after execution)
        outcome-term (outcome-fn)
        outcome-event (event/make-belief outcome-term
                                         (truth/make-truth 1.0 0.9)
                                         current-time
                                         current-time
                                         {:input? true})]

    [state op-event outcome-event]))

(comment
  ;; Example usage:

  (require '[ona.core :as core])

  ;; Setup environment
  (def state (-> (core/init-state)
                 (setup-light-environment)))

  ;; Check operations registered
  (count (:operations state))  ;; => 2

  ;; Execute operation and observe
  (let [[state op-event outcome-event]
        (execute-and-observe state "^on" get-light-state)]

    (println "Light state:" @light-state)  ;; => :on
    (println "Outcome:" (term/format-term (:term outcome-event)))  ;; => <light --> on>

    ;; Add events to system
    (-> state
        (core/add-event op-event)
        (core/add-event outcome-event)))
  )
