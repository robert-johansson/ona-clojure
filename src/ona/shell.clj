(ns ona.shell
  "Shell/REPL interface for ONA.
   Implements commands compatible with C ONA shell for differential testing.
   Based on analysis of src/Shell.c"
  (:require [ona.core :as core]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.event :as event]
            [ona.cycle :as cycle]
            [ona.query :as query]
            [ona.operation :as operation]
            [clojure.string :as str]))

;; =============================================================================
;; Output Functions
;; =============================================================================

(defn should-output?
  "Check if output should be produced based on volume setting"
  [state priority]
  (>= (:volume state) (* priority 100)))

(defn output
  "Conditional output based on volume"
  [state priority & args]
  (when (should-output? state priority)
    (apply println args))
  (flush))

;; =============================================================================
;; Command: *concepts
;; =============================================================================

(defn format-concept-json
  "Format concept in JSON-like format matching C ONA output"
  [concept]
  (let [{:keys [term priority usefulness use-count last-used belief belief-spike predicted-belief implications]} concept
        term-str (term/format-term term)
        ;; Use belief-spike if it exists, otherwise eternal belief
        active-belief (or belief-spike belief)]
    (str "//term: " term-str " "
         "{ \"priority\": " (format "%.2f" priority)
         ", \"usefulness\": " (format "%.2f" usefulness)
         ", \"useCount\": " use-count
         ", \"lastUsed\": " last-used
         (when active-belief
           (str ", \"frequency\": " (format "%.2f" (:frequency (:truth active-belief)))
                ", \"confidence\": " (format "%.2f" (:confidence (:truth active-belief)))))
         (when predicted-belief
           (str ", \"predicted\": { \"frequency\": " (format "%.2f" (:frequency (:truth predicted-belief)))
                ", \"confidence\": " (format "%.2f" (:confidence (:truth predicted-belief)))
                ", \"occurrenceTime\": " (:occurrence-time predicted-belief)
                " }"))
         (when (seq implications)
           (str ", \"implications\": ["
                (str/join ", "
                          (map (fn [impl]
                                 (str "{ \"term\": \"" (term/format-term (:term impl)) "\""
                                      ", \"frequency\": " (format "%.2f" (:frequency (:truth impl)))
                                      ", \"confidence\": " (format "%.2f" (:confidence (:truth impl)))
                                      ", \"offset\": " (:occurrence-time-offset impl)
                                      " }"))
                               (vals implications)))
                "]"))
         ", \"termlinks\": []"  ; Placeholder for now
         "}")))

(defn cmd-concepts
  "Dump all concepts (implements *concepts)"
  [state]
  (println "//*concepts")
  (doseq [concept (sort-by (comp term/format-term :term) (vals (:concepts state)))]
    (println (format-concept-json concept)))
  (println "//*done")
  (flush)
  state)

;; =============================================================================
;; Command: *stats
;; =============================================================================

(defn cmd-stats
  "Print system statistics (implements *stats)"
  [state]
  (println "//*stats")
  (println "Statistics")
  (println "----------")
  (let [stats (core/calculate-stats state)]
    (println "currentTime:                   " (:current-time stats))
    (println "total concepts:                " (:total-concepts stats))
    (println "current belief events cnt:     " (:belief-events-count stats))
    (println "current goal events cnt:       " (:goal-events-count stats))
    (println "current average concept priority:" (format "%.6f" (:average-concept-priority stats))))
  (println "//*done")
  (flush)
  state)

;; =============================================================================
;; Command: *volume
;; =============================================================================

(defn cmd-volume
  "Set output volume (implements *volume=N)"
  [state value]
  (let [vol (Integer/parseInt value)]
    (assoc state :volume vol)))

;; =============================================================================
;; Command: *reset
;; =============================================================================

(defn cmd-reset
  "Reset NAR state (implements *reset)"
  [_state]
  (core/reset-state))

;; =============================================================================
;; Command: *currenttime
;; =============================================================================

(defn cmd-currenttime
  "Set current time (implements *currenttime=N)"
  [state value]
  (assoc state :current-time (Long/parseLong value)))

;; =============================================================================
;; Command: *stampid
;; =============================================================================

(defn cmd-stampid
  "Set stamp ID (implements *stampid=N)"
  [state value]
  (assoc state :stamp-id (Long/parseLong value)))

;; =============================================================================
;; Command: *motorbabbling
;; =============================================================================

(defn cmd-motorbabbling
  "Set motor babbling (implements *motorbabbling=true/false or =N)"
  [state value]
  (let [v (str/lower-case value)]
    (cond
      (= v "true") (assoc-in state [:config :motor-babbling] true)
      (= v "false") (assoc-in state [:config :motor-babbling] false)
      :else (-> state
                (assoc-in [:config :motor-babbling] true)
                (assoc-in [:config :motor-babbling-chance] (Double/parseDouble value))))))

;; =============================================================================
;; Command: *seed
;; =============================================================================

(defn cmd-seed
  "Set random seed for reproducibility (implements *seed=N)"
  [state value]
  (let [seed (Long/parseLong value)]
    (println (str "Setting random seed: " seed))
    ;; In Clojure, we use Java's Random with a seed
    (assoc state :rng (java.util.Random. seed))))

;; =============================================================================
;; Command: *setopname
;; =============================================================================

(defn dummy-operation
  "Dummy operation callback for shell-registered operations"
  [op-name]
  (fn [_args]
    (println (str op-name " executed with args"))
    {:executed true}))

(defn cmd-setopname
  "Register an operation (implements *setopname ID NAME)
   Example: *setopname 1 ^left"
  [state value]
  (let [parts (str/split value #"\s+")
        op-id (Integer/parseInt (first parts))
        op-name (second parts)]
    (when (= (:volume state) 100)
      (println (str "Registered operation " op-id ": " op-name)))
    (operation/register-operation state op-name (dummy-operation op-name))))

;; =============================================================================
;; Narsese Input Processing (Minimal)
;; =============================================================================

(defn parse-narsese-line
  "Parse a line of Narsese input.
   Format: <term><punct> [truth] [:temporal]
   Where <punct> is . (belief), ! (goal), or ? (question)"
  [line]
  (let [line (str/trim line)
        temporal? (str/includes? line ":|:")

        ;; Extract term and truth (split on {)
        parts (str/split line #"\{")
        term-part (first parts)
        truth-part (when (> (count parts) 1)
                     (str "{" (second parts)))

        ;; Detect punctuation type BEFORE removing it
        ;; Check the term part (before truth value) for punctuation
        goal? (and (str/includes? term-part "!")
                  (not (str/includes? term-part "?")))
        question? (str/includes? term-part "?")
        belief? (and (not goal?) (not question?)
                    (or (str/includes? term-part ".")
                        (str/includes? term-part ":")))

        ;; Clean term - remove punctuation and temporal markers
        term-str (-> term-part
                     (str/replace #"[.!?]" "")
                     (str/replace #":\|:" "")
                     str/trim)

        ;; Parse truth
        truth-val (if truth-part
                    (truth/parse-truth truth-part)
                    (truth/make-truth))]

    {:term (term/parse-term term-str)
     :truth truth-val
     :type (cond
             question? :question
             goal? :goal
             belief? :belief
             :else :unknown)
     :temporal? temporal?}))

(defn process-narsese
  "Process Narsese input and update state"
  [state line]
  (try
    (let [parsed (parse-narsese-line line)
          {:keys [term truth type temporal?]} parsed]

      (when (= (:volume state) 100)
        (println (str "Input: " (term/format-term term)
                      (case type
                        :belief "."
                        :goal "!"
                        :question "?"
                        "")
                      (when temporal? " :|:")
                      " " (truth/format-truth truth))))

      ;; Add event to system or process question
      (let [result-state
            (if (= type :question)
              ;; Handle question
              (let [[state' answers] (query/answer-question state term)]
                (if (empty? answers)
                  (do
                    (when (= (:volume state) 100)
                      (println "Answer: None"))
                    state')
                  (do
                    (when (= (:volume state) 100)
                      ;; Display best answer
                      (let [best (first answers)]
                        (println (str "Answer: " (query/format-answer best)))))
                    state')))

              ;; Handle belief/goal
              (let [state-after-input
                    (case type
                      :belief (core/add-input-belief state term truth (not temporal?))
                      :goal (core/add-input-goal state term truth (not temporal?))
                      state)]

                ;; Auto-execute one cycle for temporal inputs (matches C ONA NAR_AddInput behavior)
                ;; In C ONA: NAR_AddInput() -> Memory_AddInputEvent() -> NAR_Cycles(1) -> currentTime++
                ;; This ensures temporal events get distinct occurrence times
                (if temporal?
                  (cycle/perform-cycle state-after-input)
                  state-after-input)))]

        ;; Flush output to ensure it's sent to subprocess
        (flush)
        result-state))
    (catch Exception e
      (println "ERROR parsing Narsese:" (.getMessage e))
      (flush)
      state)))

;; =============================================================================
;; Command Dispatch
;; =============================================================================

(defn process-command
  "Process a shell command (starts with *)"
  [state line]
  (let [result-state
        (cond
          ;; Commands with = separator
          (str/includes? line "=")
          (let [parts (str/split line #"=")
                cmd (first parts)
                value (when (> (count parts) 1) (str/join "=" (rest parts)))]
            (case cmd
              "*reset" (cmd-reset state)
              "*volume" (cmd-volume state value)
              "*currenttime" (cmd-currenttime state value)
              "*stampid" (cmd-stampid state value)
              "*motorbabbling" (cmd-motorbabbling state value)
              "*seed" (cmd-seed state value)
              ;; Unknown command
              (do (println "Unknown command:" cmd) state)))

          ;; Commands with space separator (e.g., *setopname 1 ^left)
          (str/starts-with? line "*setopname")
          (let [value (subs line (count "*setopname "))]
            (cmd-setopname state value))

          ;; Commands without arguments
          :else
          (case line
            "*reset" (cmd-reset state)
            "*concepts" (cmd-concepts state)
            "*stats" (cmd-stats state)
            ;; Unknown command
            (do (println "Unknown command:" line) state)))]

    ;; Flush output after command processing
    (flush)
    result-state))

;; =============================================================================
;; Main Input Processing
;; =============================================================================

(defn process-input
  "Process a line of input (command or Narsese)"
  [state line]
  (let [line (str/trim line)]
    (cond
      ;; Empty line - execute cycle (TODO)
      (empty? line)
      (do
        (output state 0.5 "// cycle")
        state)

      ;; Comment - echo if volume high
      (str/starts-with? line "//")
      (do
        (when (> (:volume state) 0)
          (println line))
        state)

      ;; Command
      (str/starts-with? line "*")
      (process-command state line)

      ;; Number - execute N cycles (perform reasoning)
      (re-matches #"\d+" line)
      (let [n (Integer/parseInt line)
            new-state (cycle/perform-cycles state n)]
        (output state 0.5 "// executing" n "cycles")
        (println (str "done with " n " additional inference steps."))
        (flush)
        new-state)

      ;; Narsese input
      :else
      (process-narsese state line))))

;; =============================================================================
;; REPL
;; =============================================================================

(defn repl
  "Main REPL loop"
  []
  (println "OpenNARS for Applications (Clojure)")
  (println "Type Narsese or commands. Use 'quit' to exit.")
  (flush)

  (loop [state (core/init-state)]
    (when-let [line (read-line)]
      (if (= (str/trim line) "quit")
        (println "Goodbye!")
        (recur (process-input state line))))))

;; =============================================================================
;; Main Entry Point
;; =============================================================================

(defn -main
  "Entry point for shell"
  [& args]
  (if (and (seq args) (= (first args) "shell"))
    (repl)
    (println "Usage: clj -M:ona shell")))
