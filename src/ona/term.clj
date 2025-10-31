(ns ona.term
  "Term representation and parsing.

   Supports:
   - Atomic terms: simple strings (e.g., 'bird', 'animal')
   - Compound terms with copulas:
     - Inheritance: <a --> b>
     - Sequence: (a &/ b)
     - Temporal implication: <a =/> b>
     - And more..."
  (:require [clojure.string :as str]))

;; =============================================================================
;; Copula Constants
;; =============================================================================

(def copula-inheritance "-->")
(def copula-sequence "&/")
(def copula-temporal-implication "=/>")
(def copula-implication "==>")
(def copula-conjunction "&")
(def copula-disjunction "|")

;; =============================================================================
;; Term Structure
;; =============================================================================

(defrecord Term
  [type          ;; :atomic, :compound
   representation ;; Original string (for simple output)
   copula        ;; For compound: the operator (-->, &/, =/>, etc.)
   subject       ;; For compound: left subterm
   predicate]    ;; For compound: right subterm

  Object
  (toString [this]
    representation)

  Comparable
  (compareTo [this other]
    (compare representation (:representation other))))

(defn atomic-term?
  "Check if term is atomic (not compound)"
  [term]
  (= (:type term) :atomic))

(defn compound-term?
  "Check if term has a copula"
  [term]
  (= (:type term) :compound))

(defn make-atomic-term
  "Create an atomic term (simple atom)"
  [atom-str]
  (map->Term
   {:type :atomic
    :representation atom-str
    :copula nil
    :subject nil
    :predicate nil}))

;; =============================================================================
;; Formatting (defined early for use in make-compound-term)
;; =============================================================================

(defn format-term
  "Format a term for output"
  [term]
  (:representation term))

;; =============================================================================
;; Compound Term Construction
;; =============================================================================

(defn make-compound-term
  "Create a compound term with copula and subterms.

  Args:
    copula - The operator (-->, &/, =/>, etc.)
    subject - Left subterm
    predicate - Right subterm

  Returns:
    Compound term"
  [copula subject predicate]
  (let [repr (case copula
               "-->" (str "<" (format-term subject) " " copula " " (format-term predicate) ">")
               "=/>" (str "<" (format-term subject) " " copula " " (format-term predicate) ">")
               "==>" (str "<" (format-term subject) " " copula " " (format-term predicate) ">")
               "&/" (str "(" (format-term subject) " " copula " " (format-term predicate) ")")
               (str "(" (format-term subject) " " copula " " (format-term predicate) ")"))]
    (map->Term
     {:type :compound
      :representation repr
      :copula copula
      :subject subject
      :predicate predicate})))

(defn sequence?
  "Check if term is a sequence (&/)"
  [term]
  (and (compound-term? term)
       (= (:copula term) copula-sequence)))

(defn implication?
  "Check if term is an implication (==> or =/>)"
  [term]
  (and (compound-term? term)
       (or (= (:copula term) copula-implication)
           (= (:copula term) copula-temporal-implication))))

(defn temporal-implication?
  "Check if term is a temporal implication (=/>)"
  [term]
  (and (compound-term? term)
       (= (:copula term) copula-temporal-implication)))

(defn sequence-length
  "Count the number of elements in a sequence.

   Examples:
   - A → 1 (atomic term)
   - (A &/ B) → 2
   - ((A &/ B) &/ C) → 3
   - (((A &/ B) &/ C) &/ D) → 4"
  [term]
  (if (sequence? term)
    (+ (sequence-length (:subject term))
       (sequence-length (:predicate term)))
    1))  ; Atomic term or non-sequence compound

;; =============================================================================
;; Operation Term Helpers
;; =============================================================================

(defn operation-term?
  "Check if term is an operation (starts with ^).

   Operations are atomic terms that start with ^ (e.g., ^left, ^right, ^pick).
   They represent motor commands/actions in sensorimotor reasoning.

   Args:
     term - Term to check

   Returns:
     Boolean - true if term is an operation"
  [term]
  (and (atomic-term? term)
       (some? (:representation term))
       (str/starts-with? (:representation term) "^")))

(defn extract-operation-from-sequence
  "Extract operation from rightmost position in a sequence.

   Recursively searches right side of sequence for operation term.
   Returns both the operation and the remaining sequence without it.

   Examples:
   - (A &/ ^op) → [^op, A]
   - ((A &/ B) &/ ^op) → [^op, (A &/ B)]
   - (A &/ B) → [nil, (A &/ B)]  ; No operation

   Args:
     seq-term - Sequence term to search

   Returns:
     Vector [operation-term remaining-sequence]
     - operation-term: The ^op term if found, nil otherwise
     - remaining-sequence: The sequence without the operation"
  [seq-term]
  (cond
    ;; Not a sequence - check if it's an operation
    (not (sequence? seq-term))
    (if (operation-term? seq-term)
      [seq-term nil]  ; Found operation, no remaining sequence
      [nil seq-term])  ; Not an operation, return as remaining

    ;; Sequence: check if predicate (right side) is an operation
    (operation-term? (:predicate seq-term))
    [(:predicate seq-term) (:subject seq-term)]  ; Found it!

    ;; Sequence: predicate is compound, recurse on it
    (sequence? (:predicate seq-term))
    (let [[op remaining] (extract-operation-from-sequence (:predicate seq-term))]
      (if op
        ;; Found operation in predicate, reconstruct remaining with subject
        [op (if remaining
              (make-compound-term copula-sequence (:subject seq-term) remaining)
              (:subject seq-term))]
        ;; No operation found
        [nil seq-term]))

    ;; Predicate is not a sequence and not an operation
    :else
    [nil seq-term]))

(defn sequence-contains-operation?
  "Check if a sequence contains an operation anywhere.

   Recursively searches both sides of sequence.

   Args:
     term - Term to check

   Returns:
     Boolean - true if sequence contains an operation"
  [term]
  (cond
    (operation-term? term) true
    (not (sequence? term)) false
    :else (or (sequence-contains-operation? (:subject term))
              (sequence-contains-operation? (:predicate term)))))

(defn get-precondition-without-op
  "Get precondition with all operations stripped.

   From C ONA: Narsese_GetPreconditionWithoutOp
   Recursively removes operations from right side of sequences.

   Examples:
   - (context &/ ^op) → context
   - ((A &/ B) &/ ^op) → (A &/ B)
   - (((A &/ B) &/ C) &/ ^op) → ((A &/ B) &/ C)
   - context → context  (no operation)

   Args:
     precondition - Precondition term (may be sequence with operations)

   Returns:
     Term with all operations removed"
  [precondition]
  (if (sequence? precondition)  ; Is it a sequence?
    (let [potential-op (:predicate precondition)]  ; Check right side
      (if (operation-term? potential-op)
        ;; Right side is an operation - strip it and recurse on left
        (get-precondition-without-op (:subject precondition))
        ;; Right side is not an operation - return as is
        precondition))
    ;; Not a sequence - return as is
    precondition))

;; =============================================================================
;; Parsing (Simplified)
;; =============================================================================

(defn find-main-copula
  "Find the main copula in a compound term string.
   Returns [copula-str start-pos end-pos] or nil if not found.
   Correctly handles nested brackets to find top-level copula only."
  [s]
  (let [s (clojure.string/trim s)
        ;; Try each copula pattern in priority order
        ;; Must check multi-char copulas BEFORE single-char ones
        patterns ["=/>" "==>" "&/" "-->"]
        len (count s)]
    (some
     (fn [pattern]
       (let [pattern-len (count pattern)]
         ;; Scan through string looking for pattern at nesting level 0
         (loop [i 0
                nesting 0]
           (cond
             ;; Reached end of string without finding pattern
             (>= i len)
             nil

             ;; Check for pattern at nesting level 0 FIRST (before updating nesting)
             (and (zero? nesting)
                  (<= (+ i pattern-len) len)
                  (= pattern (subs s i (+ i pattern-len))))
             [pattern i (+ i pattern-len)]

             ;; Update nesting level and continue
             :else
             (let [ch (get s i)
                   ;; Check if > is part of a copula (=/>, ==>, -->)
                   ;; If so, don't treat it as a closing bracket
                   prev-char (when (> i 0) (get s (dec i)))
                   is-copula-gt? (and (= ch \>)
                                     (or (and (= prev-char \/) (= (get s (- i 2)) \=))  ; =/>
                                         (and (= prev-char \=) (= (get s (- i 2)) \=))   ; ==>
                                         (and (= prev-char \-) (= (get s (- i 2)) \-)))) ; -->
                   new-nesting (cond
                                 (or (= ch \<) (= ch \() (= ch \[)) (inc nesting)
                                 (and (= ch \>) (not is-copula-gt?)) (dec nesting)  ; Only decrement if NOT part of copula
                                 (or (= ch \)) (= ch \])) (dec nesting)
                                 :else nesting)]
               (recur (inc i) new-nesting))))))
     patterns)))

(defn parse-term
  "Parse a term from string.

  Handles:
  - Atomic: 'bird', 'animal'
  - Inheritance: '<a --> b>'
  - Sequence: '(a &/ b)'
  - Implication: '<a =/> b>'

  Uses proper nesting-aware parsing to handle complex nested terms."
  [s]
  (let [s (clojure.string/trim s)]
    (cond
      ;; Compound term enclosed in < >
      (and (str/starts-with? s "<")
           (str/ends-with? s ">"))
      (let [inner (subs s 1 (dec (count s)))
            copula-info (find-main-copula inner)]
        (if copula-info
          (let [[copula start end] copula-info
                subj (subs inner 0 start)
                pred (subs inner end)
                subj-parsed (parse-term (str/trim subj))
                pred-parsed (parse-term (str/trim pred))]
            (case copula
              "-->" (make-compound-term copula-inheritance subj-parsed pred-parsed)
              "=/>" (make-compound-term copula-temporal-implication subj-parsed pred-parsed)
              "==>" (make-compound-term copula-implication subj-parsed pred-parsed)
              (make-atomic-term s)))  ; Unknown copula
          (make-atomic-term s)))  ; No copula found

      ;; Sequence enclosed in ( )
      (and (str/starts-with? s "(")
           (str/ends-with? s ")"))
      (let [inner (subs s 1 (dec (count s)))
            copula-info (find-main-copula inner)]
        (if (and copula-info (= "&/" (first copula-info)))
          (let [[copula start end] copula-info
                subj (subs inner 0 start)
                pred (subs inner end)
                subj-parsed (parse-term (str/trim subj))
                pred-parsed (parse-term (str/trim pred))]
            (make-compound-term copula-sequence subj-parsed pred-parsed))
          (make-atomic-term s)))  ; No sequence copula found

      ;; Atomic term
      :else
      (make-atomic-term s))))

(defn make-term
  "Alias for parse-term (backward compatibility)"
  [s]
  (parse-term s))

;; =============================================================================
;; Term Construction Helpers
;; =============================================================================

(defn make-sequence
  "Create a sequence term: (a &/ b)"
  [a b]
  (make-compound-term copula-sequence a b))

(defn make-temporal-implication
  "Create a temporal implication: <a =/> b>"
  [a b]
  (make-compound-term copula-temporal-implication a b))

(defn make-inheritance
  "Create an inheritance: <a --> b>"
  [a b]
  (make-compound-term copula-inheritance a b))

;; =============================================================================
;; Subterm Extraction
;; =============================================================================

(defn get-subject
  "Get subject (left subterm) of compound term"
  [term]
  (when (compound-term? term)
    (:subject term)))

(defn get-predicate
  "Get predicate (right subterm) of compound term"
  [term]
  (when (compound-term? term)
    (:predicate term)))

(comment
  ;; Example usage:

  ;; Atomic term
  (def bird (make-atomic-term "bird"))
  (format-term bird)  ;; => "bird"

  ;; Inheritance
  (def bird-animal (make-inheritance bird (make-atomic-term "animal")))
  (format-term bird-animal)  ;; => "<bird --> animal>"

  ;; Sequence
  (def a (make-atomic-term "a"))
  (def b (make-atomic-term "b"))
  (def seq (make-sequence a b))
  (format-term seq)  ;; => "(a &/ b)"

  ;; Temporal implication
  (def impl (make-temporal-implication a b))
  (format-term impl)  ;; => "<a =/> b>"

  ;; Parsing
  (parse-term "<bird --> animal>")
  (parse-term "(a &/ b)")
  (parse-term "<a =/> b>")
  )
