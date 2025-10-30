(ns ona.query
  "Question answering and query processing.
   Based on C ONA NAR.c query handling (lines 135-283)."
  (:require [ona.term :as term]
            [ona.truth :as truth]
            [ona.event :as event]))

;; =============================================================================
;; Answer Records
;; =============================================================================

(defrecord Answer
  [term           ; Term - the answer term
   truth          ; Truth - the answer truth value
   source-type    ; Keyword - :belief, :implication, :predicted
   source-concept ; Term - which concept provided this answer
   expectation])  ; Double - ranking metric (frequency × confidence)

(defn make-answer
  "Create an answer record"
  [term truth source-type source-concept]
  (let [exp (truth/expectation truth)]
    (map->Answer
     {:term term
      :truth truth
      :source-type source-type
      :source-concept source-concept
      :expectation exp})))

;; =============================================================================
;; Answer Search
;; =============================================================================

(defn search-belief-answer
  "Search a concept for a belief matching the query term"
  [concept query-term]
  (let [{:keys [term belief belief-spike predicted-belief]} concept]
    (when (= term query-term)
      (let [;; Prefer spike, then predicted, then eternal belief
            answer-event (or belief-spike predicted-belief belief)]
        (when answer-event
          (let [source-type (cond
                              (= answer-event belief-spike) :belief
                              (= answer-event predicted-belief) :predicted
                              :else :eternal-belief)]
            (make-answer term
                        (:truth answer-event)
                        source-type
                        term)))))))

(defn search-implication-answer
  "Search a concept for implications matching the query term"
  [concept query-term]
  (let [{:keys [term implications]} concept]
    ;; Check if any implications have a term matching the query
    (when (seq implications)
      (->> (vals implications)
           (filter #(= (:term %) query-term))
           (map (fn [impl]
                  (make-answer (:term impl)
                              (:truth impl)
                              :implication
                              term)))
           (seq)))))

(defn search-all-concepts
  "Search all concepts for answers to the query"
  [state query-term]
  (let [concepts (vals (:concepts state))]
    (->> concepts
         (mapcat (fn [concept]
                   (concat
                    ;; Search for direct belief match
                    (when-let [belief-answer (search-belief-answer concept query-term)]
                      [belief-answer])
                    ;; Search for implication match
                    (search-implication-answer concept query-term))))
         (filter some?)
         (vec))))

;; =============================================================================
;; Answer Ranking
;; =============================================================================

(defn rank-answers
  "Sort answers by expectation (frequency × confidence), highest first"
  [answers]
  (sort-by :expectation > answers))

(defn best-answer
  "Get the best answer from a collection of answers"
  [answers]
  (first (rank-answers answers)))

;; =============================================================================
;; Question Priming
;; =============================================================================

(defn prime-concept
  "Boost concept priority after being involved in question answering"
  [concept boost]
  (update concept :priority #(min 1.0 (+ % boost))))

(defn apply-question-priming
  "Boost priorities of concepts involved in answering"
  [state answers priming-amount]
  (if (zero? priming-amount)
    state
    (reduce
     (fn [state answer]
       (let [concept-term (:source-concept answer)]
         (update-in state [:concepts concept-term]
                    #(when % (prime-concept % priming-amount)))))
     state
     answers)))

;; =============================================================================
;; Main Query Interface
;; =============================================================================

(defn answer-question
  "Answer a question by searching through all concepts.
   Returns [state answers] where answers is a vector of Answer records."
  [state query-term]
  (let [;; Search all concepts
        answers (search-all-concepts state query-term)

        ;; Rank by expectation
        ranked-answers (rank-answers answers)

        ;; Get priming amount from config (default 0.1)
        priming-amount (get-in state [:config :question-priming] 0.1)

        ;; Apply priming if enabled
        state' (apply-question-priming state ranked-answers priming-amount)]

    [state' ranked-answers]))

(defn format-answer
  "Format an answer for display"
  [answer]
  (str (term/format-term (:term answer))
       ". "
       (truth/format-truth (:truth answer))
       " (from " (name (:source-type answer))
       " in " (term/format-term (:source-concept answer)) ")"))
