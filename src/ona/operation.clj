(ns ona.operation
  "Operation registration and execution for ONA.

  Operations are the motor commands that allow ONA to interact with
  its environment. Each operation has:
  - A name (Term like ^left, ^right, ^on, ^off)
  - A callback function that executes the action
  - A unique ID for indexing

  From C ONA: NAR_AddOperation, Memory.operations[]"
  (:require [ona.term :as term]))

;; =============================================================================
;; Operation Structure
;; =============================================================================

(defrecord Operation
  [id          ; Long - unique operation ID
   name        ; Term - operation name (e.g., ^left, ^right)
   callback    ; Function - (fn [args] ...) -> result map
   ])

(defn make-operation
  "Create an operation record.

  Args:
    id - Unique operation ID
    name - Operation name as Term
    callback - Function (fn [args] ...) that executes the operation

  Returns:
    Operation record"
  [id name callback]
  {:pre [(some? name)
         (fn? callback)]}
  (map->Operation
   {:id id
    :name name
    :callback callback}))

;; =============================================================================
;; Operation Execution
;; =============================================================================

(defn execute-operation
  "Execute an operation by calling its callback.

  Args:
    operation - Operation record
    args - Arguments map (optional, for operations with parameters)

  Returns:
    Execution result map:
    {:success? boolean
     :executed-term Term (the operation term)
     :result any (return value from callback)}"
  [operation args]
  (try
    (let [result ((:callback operation) args)]
      {:success? true
       :executed-term (:name operation)
       :result result})
    (catch Exception e
      {:success? false
       :executed-term (:name operation)
       :error (.getMessage e)})))

;; =============================================================================
;; Operation Registry
;; =============================================================================

(defn register-operation
  "Register an operation in the NAR state.

  Args:
    state - Current NAR state
    name-str - Operation name string (e.g., \"^left\", \"^on\")
    callback - Function to execute when operation is selected

  Returns:
    Updated state with operation registered"
  [state name-str callback]
  (let [;; Parse operation name to term
        op-term (term/parse-term name-str)

        ;; Generate ID (next available)
        next-id (inc (count (:operations state)))

        ;; Create operation record
        operation (make-operation next-id op-term callback)]

    ;; Store in operations map keyed by term
    (assoc-in state [:operations op-term] operation)))

(defn get-operation-by-term
  "Get an operation by its term.

  Args:
    state - Current NAR state
    term - Operation term

  Returns:
    Operation record or nil if not found"
  [state term]
  (get-in state [:operations term]))

(defn get-all-operations
  "Get all registered operations.

  Args:
    state - Current NAR state

  Returns:
    Vector of Operation records"
  [state]
  (vec (vals (:operations state))))

;; =============================================================================
;; Helper: Operation Term Extraction
;; =============================================================================

(defn extract-operation-from-decision
  "Extract operation term from a decision's operation-term.

  The decision's operation-term might be:
  - An implication: <<precond> =/> <postcond>>
  - A sequence: (precond &/ ^op)
  - An operation: ^op

  For now, we'll handle simple cases where the operation-term
  IS the implication itself, and we need to find the operation within it.

  Args:
    decision - Decision record

  Returns:
    Operation term (or nil if no operation found)"
  [decision]
  (let [op-term (:operation-term decision)]
    ;; For now, return the term itself
    ;; In full implementation, would extract ^op from sequences/implications
    op-term))

(comment
  ;; Example usage:

  (require '[ona.core :as core]
           '[ona.term :as term])

  ;; Initialize state
  (def state (core/init-state))

  ;; Register a simple operation
  (def state
    (register-operation state "^left"
                       (fn [_args]
                         (println "Moving left!")
                         {:moved :left})))

  (def state
    (register-operation state "^right"
                       (fn [_args]
                         (println "Moving right!")
                         {:moved :right})))

  ;; Get operations
  (count (:operations state))  ;; => 2

  ;; Execute an operation
  (let [op (get-operation-by-term state (term/parse-term "^left"))]
    (execute-operation op {}))
  ;; => {:success? true, :executed-term ^left, :result {:moved :left}}
  )
