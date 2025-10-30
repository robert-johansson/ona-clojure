(ns ona.query-test
  "Tests for question answering"
  (:require [clojure.test :refer :all]
            [ona.query :as query]
            [ona.core :as core]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.event :as event]
            [ona.cycle :as cycle]))

(deftest test-belief-query
  (testing "Question about a belief returns the belief"
    (let [state (core/init-state)
          term-a (term/parse-term "A")
          truth-val (truth/make-truth 1.0 0.9)
          state (core/add-input-belief state term-a truth-val true)
          [state' answers] (query/answer-question state term-a)]

      (is (= 1 (count answers)) "Should find one answer")
      (let [answer (first answers)]
        (is (= term-a (:term answer)))
        (is (= 1.0 (:frequency (:truth answer))))
        (is (= 0.9 (:confidence (:truth answer))))
        (is (= :eternal-belief (:source-type answer)))))))

(deftest test-temporal-belief-query
  (testing "Question about temporal belief returns most recent spike"
    (let [state (core/init-state)
          term-a (term/parse-term "A")
          truth-val (truth/make-truth 1.0 0.9)
          state (core/add-input-belief state term-a truth-val false)
          [state' answers] (query/answer-question state term-a)]

      (is (= 1 (count answers)) "Should find one answer")
      (let [answer (first answers)]
        (is (= :belief (:source-type answer)))))))

(deftest test-implication-query
  (testing "Question about implication returns learned implication"
    (let [state (core/init-state)
          term-a (term/parse-term "A")
          term-b (term/parse-term "B")
          truth-val (truth/make-truth 1.0 0.9)

          ;; Add temporal sequence A, B
          state (core/add-input-belief state term-a truth-val false)
          state (cycle/perform-cycle state)
          state (core/add-input-belief state term-b truth-val false)
          state (cycle/perform-cycles state 10)

          ;; Query for implication
          impl-term (term/make-temporal-implication term-a term-b)
          [state' answers] (query/answer-question state impl-term)]

      (is (>= (count answers) 1) "Should find at least one implication")
      (let [answer (first answers)]
        (is (= impl-term (:term answer)))
        (is (= :implication (:source-type answer)))
        (is (> (:expectation answer) 0.0) "Should have positive expectation")))))

(deftest test-no-answer
  (testing "Question about unknown term returns empty"
    (let [state (core/init-state)
          unknown-term (term/parse-term "UNKNOWN")
          [state' answers] (query/answer-question state unknown-term)]

      (is (empty? answers) "Should return no answers for unknown term"))))

(deftest test-multiple-answers-ranked
  (testing "Multiple answers are ranked by expectation"
    (let [state (core/init-state)
          term-a (term/parse-term "A")

          ;; Add belief with high confidence
          high-truth (truth/make-truth 1.0 0.9)
          state (core/add-input-belief state term-a high-truth true)

          ;; The belief-spike overwrites, so we can't easily test multiple answers
          ;; for the same term. But we can test the ranking function directly
          ;; expectation = c * (f - 0.5) + 0.5
          ;; answer1: 0.9 * (1.0 - 0.5) + 0.5 = 0.95
          ;; answer2: 0.5 * (1.0 - 0.5) + 0.5 = 0.75
          ;; answer3: 0.9 * (0.5 - 0.5) + 0.5 = 0.50
          answer1 (query/make-answer term-a (truth/make-truth 1.0 0.9) :belief term-a)
          answer2 (query/make-answer term-a (truth/make-truth 1.0 0.5) :belief term-a)
          answer3 (query/make-answer term-a (truth/make-truth 0.5 0.9) :belief term-a)

          ranked (query/rank-answers [answer3 answer1 answer2])]

      (is (= answer1 (first ranked)) "Highest expectation should be first (0.95)")
      (is (= answer2 (second ranked)) "Second highest should be second (0.75)")
      (is (= answer3 (nth ranked 2)) "Lowest should be last (0.50)"))))

(deftest test-question-priming
  (testing "Question priming boosts concept priority"
    (let [state (core/init-state)
          term-a (term/parse-term "A")
          truth-val (truth/make-truth 1.0 0.9)

          ;; Add belief and get initial priority
          state (core/add-input-belief state term-a truth-val true)
          initial-priority (get-in state [:concepts term-a :priority])

          ;; Ask question with priming enabled
          state (assoc-in state [:config :question-priming] 0.2)
          [state' answers] (query/answer-question state term-a)

          ;; Check priority increased
          final-priority (get-in state' [:concepts term-a :priority])]

      (is (> final-priority initial-priority) "Priority should increase after question"))))

(deftest test-format-answer
  (testing "Answer formatting produces readable output"
    (let [term-a (term/parse-term "A")
          truth-val (truth/make-truth 1.0 0.9)
          answer (query/make-answer term-a truth-val :belief term-a)
          formatted (query/format-answer answer)]

      (is (string? formatted))
      (is (clojure.string/includes? formatted "A."))
      (is (clojure.string/includes? formatted "1.000000"))
      (is (clojure.string/includes? formatted "0.900000"))
      (is (clojure.string/includes? formatted "belief")))))

(deftest test-predicted-belief-answer
  (testing "Question can return predicted belief"
    (let [state (core/init-state)
          term-a (term/parse-term "A")
          term-b (term/parse-term "B")
          truth-val (truth/make-truth 1.0 0.9)

          ;; Learn A =/> B
          state (core/add-input-belief state term-a truth-val false)
          state (cycle/perform-cycle state)
          state (core/add-input-belief state term-b truth-val false)
          state (cycle/perform-cycles state 10)

          ;; Observe A again (should predict B)
          state (core/add-input-belief state term-a truth-val false)
          state (cycle/perform-cycle state)

          ;; Query for B
          [state' answers] (query/answer-question state term-b)]

      ;; Should find answer (either belief or predicted belief)
      (is (>= (count answers) 1) "Should find answer for B")
      (when (seq answers)
        (let [answer (first answers)]
          (is (= term-b (:term answer))))))))

(run-tests)
