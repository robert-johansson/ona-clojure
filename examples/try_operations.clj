#!/usr/bin/env clj

;; Quick script to try operations interactively
;; Run: clj examples/try_operations.clj

(require '[ona.core :as core])
(require '[ona.term :as term])
(require '[ona.truth :as truth])
(require '[ona.cycle :as cycle])
(require '[ona.operation :as operation])

;; Simple environment
(def state-atom (atom :off))

(defn toggle-state [_]
  (let [new-state (if (= @state-atom :off) :on :off)]
    (println (str ">>> MOTOR ACTION: State changed from " @state-atom " to " new-state))
    (reset! state-atom new-state)
    {:state new-state}))

;; Setup
(println "=== ONA Operations Demo ===\n")
(def ona-state
  (-> (core/init-state)
      (assoc :volume 100)
      (operation/register-operation "^toggle" toggle-state)))

(println "Registered operation: ^toggle")
(println "Current state: " @state-atom "\n")

;; Teaching: Show the system what toggle does
(println "--- Teaching Phase ---")
(println "Input: <switch --> off>. :|:")
(def ona-state (core/add-input-belief ona-state
                                      (term/parse-term "<switch --> off>")
                                      (truth/make-truth) false))
(def ona-state (cycle/perform-cycle ona-state))

(println "Input: ^toggle. :|:")
(def ona-state (core/add-input-belief ona-state
                                      (term/parse-term "^toggle")
                                      (truth/make-truth) false))
(def ona-state (cycle/perform-cycle ona-state))

;; Simulate the outcome
(reset! state-atom :on)
(println "Input: <switch --> on>. :|:")
(def ona-state (core/add-input-belief ona-state
                                      (term/parse-term "<switch --> on>")
                                      (truth/make-truth) false))
(def ona-state (cycle/perform-cycles ona-state 50))

(println "\n--- What Was Learned? ---")
(when-let [concept (get-in ona-state [:concepts (term/parse-term "<switch --> off>")])]
  (println "Implications from <switch --> off>:")
  (doseq [impl (vals (:implications concept))]
    (println (str "  " (term/format-term (:term impl))
                 " " (truth/format-truth (:truth impl))))))

(println "\n--- Testing Phase ---")
(reset! state-atom :off)
(println "Current state: " @state-atom)
(println "Input: <switch --> off>. :|:")
(def ona-state (core/add-input-belief ona-state
                                      (term/parse-term "<switch --> off>")
                                      (truth/make-truth) false))
(def ona-state (cycle/perform-cycle ona-state))

(println "Input: <switch --> on>! :|: (GOAL)")
(def ona-state (core/add-input-goal ona-state
                                    (term/parse-term "<switch --> on>")
                                    (truth/make-truth) false))
(def ona-state (cycle/perform-cycles ona-state 20))

(println "\n--- Result ---")
(println (str "Final state: " @state-atom))
(println "Did the system execute ^toggle to achieve the goal?")
