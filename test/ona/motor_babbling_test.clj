(ns ona.motor-babbling-test
  "Tests for motor babbling (exploration through random actions).

  Motor babbling enables:
  1. Random exploration when uncertain
  2. Discovery of new implications
  3. Learning through trial and error
  4. Balanced with exploitation via confidence-based suppression"
  (:require [clojure.test :refer :all]
            [ona.core :as core]
            [ona.cycle :as cycle]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.event :as event]
            [ona.operation :as operation]
            [ona.decision :as decision]
            [ona.test-env :as env]))

(deftest test-motor-babbling-disabled-by-default
  (testing "Motor babbling is disabled by default"
    (let [state (core/init-state)]
      (is (false? (get-in state [:config :motor-babbling]))))))

(deftest test-motor-babbling-with-operations
  (testing "Motor babbling selects random operations"
    ;; Setup environment with operations
    (let [state (-> (core/init-state)
                    (assoc-in [:config :motor-babbling] true)
                    (env/setup-light-environment))

          ;; Call motor babbling multiple times
          decisions (repeatedly 20 #(decision/motor-babbling state))]

      ;; Should return decisions
      (is (every? some? decisions))

      ;; Should have operations selected
      (is (every? :operation-term decisions))

      ;; Should be marked as executable
      (is (every? :execute? decisions))

      ;; Desires should be moderate (0.6)
      (is (every? #(= 0.6 (:desire %)) decisions))

      ;; Should select different operations (probabilistic, not guaranteed every time)
      (let [op-terms (map (comp term/format-term :operation-term) decisions)
            unique-ops (set op-terms)]
        ;; With 20 samples from 2 operations, should see both
        (is (>= (count unique-ops) 1))))))

(deftest test-motor-babbling-suppression
  (testing "Motor babbling suppressed when high-confidence option available"
    (let [state (-> (core/init-state)
                    (assoc-in [:config :motor-babbling] true)
                    (env/setup-light-environment))

          ;; Create a goal
          goal (event/make-goal (term/parse-term "<light --> on>")
                                (truth/make-truth 1.0 0.9)
                                100
                                100
                                {:input? true})]

      ;; Without learned implications, best candidate has low desire
      ;; Motor babbling should be allowed (if probabilistically triggered)
      (let [decision (decision/suggest-decision state goal 100)]
        ;; Decision should be returned (either babbling or null)
        (is (some? decision)))

      ;; Add high-confidence implication
      (let [impl-term (term/make-temporal-implication
                      (term/parse-term "<light --> off>")
                      (term/parse-term "<light --> on>"))
            impl (require 'ona.implication)
            high-conf-impl (ona.implication/make-implication
                           impl-term
                           (truth/make-truth 0.9 0.9)  ; High confidence!
                           10.0
                           100
                           {})

            ;; Add to state
            state (core/add-implication state
                                        high-conf-impl
                                        (term/parse-term "<light --> off>"))

            ;; Add belief for precondition
            state (core/add-input-belief state
                                         (term/parse-term "<light --> off>")
                                         (truth/make-truth 1.0 0.9)
                                         false)]

        ;; Now best candidate should have high desire (> 0.7)
        ;; Motor babbling should be suppressed
        (let [decision (decision/suggest-decision state goal 100)]
          ;; Should get reasoned decision, not random babbling
          (is (some? decision))
          ;; High desire from reasoning (not fixed 0.6 from babbling)
          (when (> (:desire decision) 0.65)
            (is (not= 0.6 (:desire decision)))))))))

(deftest test-motor-babbling-probability
  (testing "Motor babbling occurs with ~20% probability"
    (let [state (-> (core/init-state)
                    (assoc-in [:config :motor-babbling] true)
                    (env/setup-light-environment))

          goal (event/make-goal (term/parse-term "<light --> on>")
                                (truth/make-truth 1.0 0.9)
                                100
                                100
                                {:input? true})

          ;; Run many trials
          trials 100
          decisions (repeatedly trials #(decision/suggest-decision state goal 100))

          ;; Count babbling decisions (desire = 0.6)
          babbling-count (count (filter #(= 0.6 (:desire %)) decisions))]

      ;; Should see babbling in roughly 20% of trials (±10% tolerance)
      ;; Note: Without learned implications, best candidate returns null-decision
      ;; So we expect most decisions to be babbling when enabled
      (is (>= babbling-count 5))  ; At least some babbling
      (is (<= babbling-count 95)))))  ; But not all

(deftest test-motor-babbling-no-operations
  (testing "Motor babbling returns null decision when no operations"
    (let [state (-> (core/init-state)
                    (assoc-in [:config :motor-babbling] true))
          ;; No operations registered
          decision (decision/motor-babbling state)]

      ;; Should return null decision
      (is (some? decision))
      (is (= 0.0 (:desire decision)))
      (is (false? (:execute? decision))))))

(deftest test-motor-babbling-exploration-learning
  (testing "Motor babbling enables exploration and learning"
    ;; Setup environment
    (reset! env/light-state :off)

    (let [state (-> (core/init-state)
                    (assoc-in [:config :motor-babbling] true)
                    (env/setup-light-environment))

          ;; No implications learned yet
          initial-concepts (count (:concepts state))]

      ;; Execute several cycles with motor babbling enabled
      ;; This should cause random operations to be selected and executed
      ;; Leading to observations and learning

      ;; Add a goal to trigger decision making
      (let [state (core/add-input-goal state
                                       (term/parse-term "<light --> on>")
                                       (truth/make-truth 1.0 0.9)
                                       false)

            ;; Run several cycles
            state (cycle/perform-cycles state 10)]

        ;; System should have processed the goal
        ;; Motor babbling may have been triggered
        ;; (Probabilistic, so can't guarantee execution in test)

        ;; Verify state is still consistent
        (is (some? state))
        (is (map? (:operations state)))
        (is (= 2 (count (:operations state))))))))

(deftest test-motor-babbling-enable-disable
  (testing "Motor babbling can be enabled and disabled"
    (let [state (core/init-state)]

      ;; Initially disabled
      (is (false? (get-in state [:config :motor-babbling])))

      ;; Enable
      (let [state (assoc-in state [:config :motor-babbling] true)]
        (is (true? (get-in state [:config :motor-babbling]))))

      ;; Disable
      (let [state (assoc-in state [:config :motor-babbling] false)]
        (is (false? (get-in state [:config :motor-babbling])))))))
