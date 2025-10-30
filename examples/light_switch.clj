(ns examples.light-switch
  "Interactive example demonstrating operations and sensorimotor learning.

   This example shows:
   1. Operation registration (motor commands)
   2. Temporal implication learning (state =/> outcome)
   3. Goal-driven decision making
   4. Motor babbling (exploration)"
  (:require [ona.core :as core]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.cycle :as cycle]
            [ona.operation :as operation]))

;; =============================================================================
;; Light Switch Environment
;; =============================================================================

(def light-state (atom :off))

(defn light-on
  "Turn the light on"
  [_args]
  (println ">>> MOTOR: Turning light ON")
  (reset! light-state :on)
  {:state :on})

(defn light-off
  "Turn the light off"
  [_args]
  (println ">>> MOTOR: Turning light OFF")
  (reset! light-state :off)
  {:state :off})

(defn observe-light
  "Create a belief about current light state"
  []
  (let [state @light-state
        term-str (if (= state :on)
                   "<light --> on>"
                   "<light --> off>")
        term (term/parse-term term-str)]
    term))

;; =============================================================================
;; Setup
;; =============================================================================

(defn setup-environment
  "Initialize ONA with light switch operations"
  []
  (println "=== Setting up Light Switch Environment ===")
  (reset! light-state :off)
  (-> (core/init-state)
      (assoc :volume 100)
      (assoc-in [:config :motor-babbling] true)
      (operation/register-operation "^on" light-on)
      (operation/register-operation "^off" light-off)))

;; =============================================================================
;; Scenarios
;; =============================================================================

(defn scenario-1-learn-operations
  "Teach the system how operations work through direct experience"
  []
  (println "\n=== SCENARIO 1: Learning Operations ===")
  (println "Teaching: turning light on from off state makes it on\n")

  (let [state (setup-environment)]

    ;; Episode 1: Off -> turn on -> On
    (println "--- Episode 1: Off -> ^on -> On ---")
    (reset! light-state :off)
    (let [state (core/add-input-belief state (term/parse-term "<light --> off>")
                                       (truth/make-truth) false)
          state (cycle/perform-cycle state)
          state (core/add-input-belief state (term/parse-term "^on")
                                       (truth/make-truth) false)
          state (cycle/perform-cycle state)]
      (reset! light-state :on)
      (let [state (core/add-input-belief state (term/parse-term "<light --> on>")
                                         (truth/make-truth) false)
            state (cycle/perform-cycles state 20)]

        (println "\n--- What did we learn? ---")
        (println "Checking concept for <light --> off>:")
        (when-let [concept (get-in state [:concepts (term/parse-term "<light --> off>")])]
          (doseq [impl (vals (:implications concept))]
            (println (str "  Learned: " (term/format-term (:term impl))
                         " " (truth/format-truth (:truth impl))))))

        state))))

(defn scenario-2-goal-driven
  "Give the system a goal and let it figure out how to achieve it"
  []
  (println "\n=== SCENARIO 2: Goal-Driven Behavior ===")
  (println "Goal: Make the light on!\n")

  (let [state (setup-environment)]

    ;; First, teach it the implication
    (println "--- Teaching phase ---")
    (reset! light-state :off)
    (let [state (core/add-input-belief state (term/parse-term "<light --> off>")
                                       (truth/make-truth) false)
          state (cycle/perform-cycle state)
          state (core/add-input-belief state (term/parse-term "^on")
                                       (truth/make-truth) false)
          state (cycle/perform-cycle state)]
      (reset! light-state :on)
      (let [state (core/add-input-belief state (term/parse-term "<light --> on>")
                                         (truth/make-truth) false)
            state (cycle/perform-cycles state 50)

            ;; Now give it a goal!
            _ (println "\n--- Testing phase: Give goal! ---")
            _ (reset! light-state :off)
            state (core/add-input-belief state (term/parse-term "<light --> off>")
                                         (truth/make-truth) false)
            state (cycle/perform-cycle state)
            _ (println "Giving goal: <light --> on>!")
            state (core/add-input-goal state (term/parse-term "<light --> on>")
                                       (truth/make-truth) false)
            state (cycle/perform-cycles state 10)]

        (println "\n--- Did it achieve the goal? ---")
        (println (str "Current light state: " @light-state))
        state))))

(defn scenario-3-motor-babbling
  "Let the system explore randomly"
  []
  (println "\n=== SCENARIO 3: Motor Babbling (Exploration) ===")
  (println "Let the system try random actions to discover consequences\n")

  (let [state (setup-environment)]
    (println "Giving vague goal: <light --> on>!")
    (println "Motor babbling enabled - system will explore\n")

    (reset! light-state :off)
    (let [state (core/add-input-belief state (term/parse-term "<light --> off>")
                                       (truth/make-truth) false)
          state (cycle/perform-cycle state)
          state (core/add-input-goal state (term/parse-term "<light --> on>")
                                     (truth/make-truth) false)
          state (cycle/perform-cycles state 30)]

      (println "\n--- What happened? ---")
      (println "The system should have tried random operations")
      (println (str "Final light state: " @light-state))
      state)))

(defn interactive-demo
  "Run all scenarios in sequence"
  []
  (println "\n╔════════════════════════════════════════╗")
  (println "║  ONA Sensorimotor Learning Demo      ║")
  (println "╚════════════════════════════════════════╝\n")

  (scenario-1-learn-operations)
  (Thread/sleep 2000)

  (scenario-2-goal-driven)
  (Thread/sleep 2000)

  (scenario-3-motor-babbling)

  (println "\n=== Demo Complete! ==="))

;; =============================================================================
;; Entry Point
;; =============================================================================

(defn -main [& args]
  (interactive-demo))
