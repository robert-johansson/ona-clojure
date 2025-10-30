(ns ona.term
  "Term representation and parsing.

   Supports:
   - Atomic terms: simple strings (e.g., 'bird', 'animal')
   - Compound terms with copulas:
     - Inheritance: <a --> b>
     - Sequence: (a &/ b)
     - Temporal implication: <a =/> b>
     - And more...")

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
;; Parsing (Simplified)
;; =============================================================================

(defn parse-term
  "Parse a term from string.

  Handles:
  - Atomic: 'bird', 'animal'
  - Inheritance: '<a --> b>'
  - Sequence: '(a &/ b)'
  - Implication: '<a =/> b>'

  For now, simple regex-based parsing. Will be enhanced later."
  [s]
  (let [s (clojure.string/trim s)]
    (cond
      ;; Inheritance: <subject --> predicate>
      (re-matches #"<(.+)\s+-->\s+(.+)>" s)
      (let [[_ subj pred] (re-matches #"<(.+)\s+-->\s+(.+)>" s)]
        (make-compound-term copula-inheritance
                            (parse-term subj)
                            (parse-term pred)))

      ;; Temporal implication: <subject =/> predicate>
      (re-matches #"<(.+)\s+=/>\s+(.+)>" s)
      (let [[_ subj pred] (re-matches #"<(.+)\s+=/>\s+(.+)>" s)]
        (make-compound-term copula-temporal-implication
                            (parse-term subj)
                            (parse-term pred)))

      ;; Regular implication: <subject ==> predicate>
      (re-matches #"<(.+)\s+==>\s+(.+)>" s)
      (let [[_ subj pred] (re-matches #"<(.+)\s+==>\s+(.+)>" s)]
        (make-compound-term copula-implication
                            (parse-term subj)
                            (parse-term pred)))

      ;; Sequence: (subject &/ predicate)
      (re-matches #"\((.+)\s+&/\s+(.+)\)" s)
      (let [[_ subj pred] (re-matches #"\((.+)\s+&/\s+(.+)\)" s)]
        (make-compound-term copula-sequence
                            (parse-term subj)
                            (parse-term pred)))

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
