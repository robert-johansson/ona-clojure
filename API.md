# ONA-Clojure API Documentation

Complete API reference for ONA-Clojure, organized by namespace.

## Table of Contents

1. [Core](#onacore) - NAR state and concept management
2. [Term](#onaterm) - Term representation
3. [Truth](#onatruth) - Truth value functions
4. [Event](#onaevent) - Belief and goal events
5. [Variable](#onavariable) - Variable unification
6. [Implication](#onaimplication) - Temporal implications
7. [Inference](#onainference) - Inference rules
8. [Decision](#onadecision) - Decision making
9. [Cycle](#onacycle) - Main reasoning cycle
10. [InvertedAtomIndex](#onainverted-atom-index) - O(1) concept lookup

---

## ona.core

Core NAR state management and concept storage.

### init-state

```clojure
(init-state) => state
```

Initialize a new NAR state with default configuration.

**Returns:** Fresh NAR state with empty concepts, queues, and default config.

**Example:**
```clojure
(require '[ona.core :as core])
(def state (core/init-state))
```

### get-concept

```clojure
(get-concept state term) => [updated-state concept]
```

Get or create a concept for a term.

**Parameters:**
- `state` - Current NAR state
- `term` - Term to get concept for

**Returns:** Tuple of `[updated-state concept]` where concept is created if it doesn't exist.

**Example:**
```clojure
(require '[ona.term :as term])
(def bird (term/make-atomic-term "bird"))
(let [[state concept] (core/get-concept state bird)]
  (println "Concept:" (:term concept)))
```

### add-event

```clojure
(add-event state event) => updated-state
```

Add an event (belief or goal) to the system.

**Parameters:**
- `state` - Current NAR state
- `event` - Event to add (from `ona.event`)

**Returns:** Updated state with event added to appropriate queue.

**Example:**
```clojure
(require '[ona.event :as event])
(require '[ona.truth :as truth])

(def belief (event/make-belief
              (term/make-atomic-term "bird")
              (truth/make-truth 1.0 0.9)
              10 10 {:input? true}))
(def state (core/add-event state belief))
```

### add-implication

```clojure
(add-implication state impl precondition-term) => updated-state
```

Add a temporal implication to the appropriate concept.

**Parameters:**
- `state` - Current NAR state
- `impl` - Implication record
- `precondition-term` - Term of the precondition

**Returns:** Updated state with implication stored.

**Example:**
```clojure
(require '[ona.implication :as implication])

(def impl-term (term/make-temporal-implication
                 (term/make-atomic-term "bird")
                 (term/make-atomic-term "flies")))
(def impl (implication/make-implication
            impl-term
            (truth/make-truth 0.9 0.8)
            10.0
            100))
(def state (core/add-implication state impl (term/make-atomic-term "bird")))
```

---

## ona.term

Term representation and construction.

### make-atomic-term

```clojure
(make-atomic-term representation) => term
```

Create an atomic term (simplest term type).

**Parameters:**
- `representation` - String representation of the term

**Returns:** Atomic term record.

**Example:**
```clojure
(require '[ona.term :as term])
(def bird (term/make-atomic-term "bird"))
(def animal (term/make-atomic-term "animal"))
```

### make-inheritance

```clojure
(make-inheritance subject predicate) => term
```

Create an inheritance statement: `<subject --> predicate>`.

**Parameters:**
- `subject` - Subject term
- `predicate` - Predicate term

**Returns:** Compound term representing inheritance.

**Example:**
```clojure
(def bird (term/make-atomic-term "bird"))
(def animal (term/make-atomic-term "animal"))
(def inheritance (term/make-inheritance bird animal))
;; Represents: <bird --> animal>
```

### make-temporal-implication

```clojure
(make-temporal-implication precondition postcondition) => term
```

Create a temporal implication: `<precondition =/> postcondition>`.

**Parameters:**
- `precondition` - Precondition term
- `postcondition` - Postcondition term

**Returns:** Compound term representing temporal implication.

**Example:**
```clojure
(def bird (term/make-atomic-term "bird"))
(def flies (term/make-atomic-term "flies"))
(def impl (term/make-temporal-implication bird flies))
;; Represents: <bird =/> flies>
```

### make-sequence

```clojure
(make-sequence left right) => term
```

Create a temporal sequence: `<left &/ right>`.

**Parameters:**
- `left` - First term in sequence
- `right` - Second term in sequence

**Returns:** Compound term representing sequence.

**Example:**
```clojure
(def press-key (term/make-atomic-term "press-key"))
(def door-opens (term/make-atomic-term "door-opens"))
(def sequence (term/make-sequence press-key door-opens))
;; Represents: <press-key &/ door-opens>
```

### get-subject

```clojure
(get-subject term) => subject-term
```

Extract the subject from a compound term.

**Parameters:**
- `term` - Compound term

**Returns:** Subject term (left side of copula).

### get-predicate

```clojure
(get-predicate term) => predicate-term
```

Extract the predicate from a compound term.

**Parameters:**
- `term` - Compound term

**Returns:** Predicate term (right side of copula).

---

## ona.truth

Truth value representation and NAL truth functions.

### make-truth

```clojure
(make-truth frequency confidence) => truth
```

Create a truth value.

**Parameters:**
- `frequency` - Frequency value [0.0, 1.0]
- `confidence` - Confidence value [0.0, 1.0)

**Returns:** Truth value record.

**Example:**
```clojure
(require '[ona.truth :as truth])
(def t (truth/make-truth 0.9 0.8))
```

### expectation

```clojure
(expectation truth) => number
```

Calculate expectation: `frequency * confidence`.

Used for decision making and event selection.

**Parameters:**
- `truth` - Truth value

**Returns:** Expectation value [0.0, 1.0].

**Example:**
```clojure
(def t (truth/make-truth 0.9 0.8))
(truth/expectation t)  ;; => 0.72
```

### revision

```clojure
(revision truth1 truth2) => truth
```

NAL revision: Combine two truth values with overlapping evidence.

**Parameters:**
- `truth1` - First truth value
- `truth2` - Second truth value

**Returns:** Revised truth value with higher confidence.

**Example:**
```clojure
(def t1 (truth/make-truth 0.9 0.8))
(def t2 (truth/make-truth 0.85 0.7))
(truth/revision t1 t2)
```

### deduction

```clojure
(deduction premise-truth implication-truth) => truth
```

NAL deduction: Forward chaining truth calculation.

Given: `A. <A --> B>.` derive `B.`

**Parameters:**
- `premise-truth` - Truth of premise
- `implication-truth` - Truth of implication

**Returns:** Deduced truth value.

**Example:**
```clojure
(def premise (truth/make-truth 1.0 0.9))
(def impl (truth/make-truth 0.9 0.8))
(truth/deduction premise impl)
```

### induction

```clojure
(induction evidence-truth premise-truth) => truth
```

NAL induction: Learn implication from temporal sequence.

Given: `A. B.` derive `<A =/> B>.`

**Parameters:**
- `evidence-truth` - Truth of observed consequence
- `premise-truth` - Truth of premise

**Returns:** Induced implication truth.

**Example:**
```clojure
(def consequence (truth/make-truth 1.0 0.9))
(def premise (truth/make-truth 1.0 0.9))
(truth/induction consequence premise)
```

### projection

```clojure
(projection truth original-time target-time) => truth
```

Project truth value over time (decay confidence).

**Parameters:**
- `truth` - Original truth value
- `original-time` - Original occurrence time
- `target-time` - Target time

**Returns:** Projected truth with decayed confidence.

**Example:**
```clojure
(def t (truth/make-truth 1.0 0.9))
(truth/projection t 10 100)  ;; Project 90 time units forward
```

---

## ona.event

Event (belief and goal) representation.

### make-belief

```clojure
(make-belief term truth occurrence-time creation-time meta) => event
```

Create a belief event.

**Parameters:**
- `term` - Term of belief
- `truth` - Truth value
- `occurrence-time` - When event occurred (or `eternal-occurrence` for eternal)
- `creation-time` - System time when created
- `meta` - Metadata map (`:input?`, `:processed?`, etc.)

**Returns:** Belief event record.

**Example:**
```clojure
(require '[ona.event :as event])

(def belief (event/make-belief
              (term/make-atomic-term "bird")
              (truth/make-truth 1.0 0.9)
              10   ;; occurrence time
              10   ;; creation time
              {:input? true}))
```

### make-goal

```clojure
(make-goal term truth occurrence-time creation-time meta) => event
```

Create a goal event.

**Parameters:**
- `term` - Term of goal
- `truth` - Desired truth value
- `occurrence-time` - When goal should be achieved
- `creation-time` - System time when created
- `meta` - Metadata map

**Returns:** Goal event record.

**Example:**
```clojure
(def goal (event/make-goal
            (term/make-atomic-term "door-opened")
            (truth/make-truth 1.0 0.9)
            20   ;; desired time
            10   ;; creation time
            {:input? true}))
```

### eternal-occurrence

```clojure
eternal-occurrence  ;; => -1
```

Constant representing eternal (timeless) events.

**Example:**
```clojure
(def eternal-belief (event/make-belief
                      (term/make-atomic-term "bird")
                      (truth/make-truth 1.0 0.9)
                      event/eternal-occurrence
                      10
                      {:input? true}))
```

### eternal?

```clojure
(eternal? event) => boolean
```

Check if an event is eternal (timeless).

**Parameters:**
- `event` - Event to check

**Returns:** `true` if eternal, `false` otherwise.

---

## ona.variable

NAL-6 variable unification and substitution.

### Unification

```clojure
(unify pattern term) => substitution
```

Unify a pattern (may contain variables) with a concrete term.

**Parameters:**
- `pattern` - Pattern term (may contain `$1`, `#1`, etc.)
- `term` - Concrete term to unify with

**Returns:** Substitution record with `:success` boolean and `:map` of bindings.

**Example:**
```clojure
(require '[ona.variable :as var])

;; Independent variable: unifies with anything
(def $1 (term/make-atomic-term "$1"))
(def bird (term/make-atomic-term "bird"))
(var/unify $1 bird)
;; => {:map {$1 bird}, :success true}

;; Compound pattern
(def pattern (term/make-inheritance $1 (term/make-atomic-term "animal")))
(def concrete (term/make-inheritance bird (term/make-atomic-term "animal")))
(var/unify pattern concrete)
;; => {:map {$1 bird}, :success true}
```

### Substitution

```clojure
(substitute term substitution) => term
```

Apply a substitution to a term, replacing variables.

**Parameters:**
- `term` - Term with variables
- `substitution` - Substitution from `unify`

**Returns:** Term with variables replaced.

**Example:**
```clojure
(def $1 (term/make-atomic-term "$1"))
(def pattern (term/make-temporal-implication $1 (term/make-atomic-term "flies")))
(def subst (var/unify $1 (term/make-atomic-term "bird")))
(var/substitute pattern subst)
;; => <bird =/> flies>
```

### Variable Checks

```clojure
(variable? term) => boolean
(independent-variable? term) => boolean
(dependent-variable? term) => boolean
(query-variable? term) => boolean
(has-variable? term) => boolean
```

Check variable types in terms.

**Example:**
```clojure
(var/variable? (term/make-atomic-term "$1"))  ;; => true
(var/independent-variable? (term/make-atomic-term "$1"))  ;; => true
(var/dependent-variable? (term/make-atomic-term "#1"))  ;; => true
(var/has-variable? (term/make-inheritance
                     (term/make-atomic-term "$1")
                     (term/make-atomic-term "animal")))  ;; => true
```

---

## ona.decision

NAL-8 decision making and goal processing.

### best-candidate

```clojure
(best-candidate state goal current-time) => decision
```

Find the best action to achieve a goal.

Uses variable unification to match goal with implications.

**Parameters:**
- `state` - Current NAR state
- `goal` - Goal event
- `current-time` - System time

**Returns:** Decision record with highest desire.

**Example:**
```clojure
(require '[ona.decision :as dec])

(def goal (event/make-goal
            (term/make-atomic-term "flies")
            (truth/make-truth 1.0 0.9)
            20 10 {:input? true}))

(def decision (dec/best-candidate state goal 10))
(println "Desire:" (:desire decision))
(println "Execute?" (:execute? decision))
```

### create-anticipation

```clojure
(create-anticipation decision impl precondition-belief current-time) => anticipation
```

Create an anticipation after executing a decision.

**Parameters:**
- `decision` - Executed decision
- `impl` - Implication used
- `precondition-belief` - Belief that satisfied precondition
- `current-time` - System time

**Returns:** Anticipation record for validation.

### check-anticipations

```clojure
(check-anticipations state current-time) => [updated-state remaining]
```

Check if anticipations were satisfied or expired.

**Parameters:**
- `state` - Current NAR state
- `current-time` - System time

**Returns:** Tuple of `[state-with-negative-evidence remaining-anticipations]`.

---

## ona.cycle

Main reasoning cycle implementation.

### perform-cycles

```clojure
(perform-cycles state n) => updated-state
```

Execute N reasoning cycles.

**Parameters:**
- `state` - Current NAR state
- `n` - Number of cycles to execute

**Returns:** Updated state after N cycles.

**Example:**
```clojure
(require '[ona.cycle :as cycle])

(def state (core/init-state))
(def state (cycle/perform-cycles state 10))
```

### related-concepts

```clojure
(related-concepts state event-term) => concept-terms
```

Find concepts semantically related to an event term.

Implements the RELATED_CONCEPTS_FOREACH pattern from C ONA Cycle.c:31-46.

Uses InvertedAtomIndex for O(1) lookup and includes variable concepts.

**Parameters:**
- `state` - Current NAR state
- `event-term` - Term to find related concepts for

**Returns:** Set of related concept Terms.

**Example:**
```clojure
(def bird (term/make-atomic-term "bird"))
(def related (cycle/related-concepts state bird))
;; Returns all concepts sharing atoms with "bird"
;; plus all variable concepts like $1, <$1 --> animal>, etc.
```

### apply-forward-chaining

```clojure
(apply-forward-chaining state belief-event current-time) => updated-state
```

Apply forward chaining from a belief event using RELATED_CONCEPTS.

NAL-6 Enhanced: Uses variable unification for pattern matching.

**Parameters:**
- `state` - Current NAR state
- `belief-event` - Belief to chain from
- `current-time` - System time

**Returns:** Updated state with predictions stored.

**Example:**
```clojure
;; Given: belief bird. + implication <$1 =/> flies>
;; Derives: prediction flies.
(def belief (event/make-belief
              (term/make-atomic-term "bird")
              (truth/make-truth 1.0 0.9)
              10 10 {:input? true}))
(def state (cycle/apply-forward-chaining state belief 10))
```

---

## ona.inverted-atom-index

O(1) concept lookup by atom.

### init

```clojure
(init) => index
```

Initialize an empty InvertedAtomIndex.

**Returns:** Fresh index.

### add-concept

```clojure
(add-concept index term) => updated-index
```

Add a concept's term to the index.

**Parameters:**
- `index` - Current index
- `term` - Term to index

**Returns:** Updated index with term's atoms indexed.

**Example:**
```clojure
(require '[ona.inverted-atom-index :as idx])

(def index (idx/init))
(def bird (term/make-atomic-term "bird"))
(def animal (term/make-atomic-term "animal"))
(def inheritance (term/make-inheritance bird animal))

(def index (idx/add-concept index inheritance))
;; Now lookup by "bird" or "animal" will find this term
```

### related-concepts

```clojure
(related-concepts index term) => concept-terms
```

Find all concepts that share atoms with the given term.

**Parameters:**
- `index` - InvertedAtomIndex
- `term` - Term to search for

**Returns:** Set of Terms that share at least one atom.

**Example:**
```clojure
(def related (idx/related-concepts index bird))
;; Returns all terms containing the "bird" atom
```

---

## ona.inference

Inference rules (deduction, induction, etc.) on events.

### belief-deduction

```clojure
(belief-deduction belief impl current-time projection-decay) => [success? derived-event]
```

Forward chaining: Derive conclusion from belief and implication.

Given: `A. <A =/> B>.` derive `B.`

**Parameters:**
- `belief` - Belief event for precondition
- `impl` - Temporal implication
- `current-time` - System time
- `projection-decay` - Truth projection decay rate

**Returns:** Tuple of `[success? predicted-event]`.

**Example:**
```clojure
(require '[ona.inference :as inf])

(def bird-belief (event/make-belief
                   (term/make-atomic-term "bird")
                   (truth/make-truth 1.0 0.9)
                   10 10 {:input? true}))

(def impl (implication/make-implication
            (term/make-temporal-implication
              (term/make-atomic-term "bird")
              (term/make-atomic-term "flies"))
            (truth/make-truth 0.9 0.8)
            10.0
            100))

(let [[success? prediction] (inf/belief-deduction bird-belief impl 20 0.99)]
  (when success?
    (println "Predicted:" (:representation (:term prediction)))))
```

### temporal-induction

```clojure
(temporal-induction precondition-event postcondition-event current-time) => implication
```

Learn temporal implication from sequence.

Given: `A. :|:` followed by `B. :|:` derive `<A =/> B>.`

**Parameters:**
- `precondition-event` - Earlier event
- `postcondition-event` - Later event
- `current-time` - System time

**Returns:** Learned implication.

---

## Usage Examples

### Complete Forward Chaining Example

```clojure
(require '[ona.core :as core])
(require '[ona.cycle :as cycle])
(require '[ona.term :as term])
(require '[ona.truth :as truth])
(require '[ona.event :as event])
(require '[ona.implication :as implication])

;; 1. Initialize
(def state (core/init-state))

;; 2. Learn pattern: bird → flies
(def bird (term/make-atomic-term "bird"))
(def flies (term/make-atomic-term "flies"))

(def bird-belief (event/make-belief bird (truth/make-truth 1.0 0.9) 10 10 {:input? true}))
(def state (core/add-event state bird-belief))

(def flies-belief (event/make-belief flies (truth/make-truth 1.0 0.9) 20 20 {:input? true}))
(def state (core/add-event state flies-belief))

;; 3. Process cycles to learn implication
(def state (cycle/perform-cycles state 5))

;; 4. Apply to new bird (robin)
(def robin (term/make-atomic-term "robin"))
(def robin-belief (event/make-belief robin (truth/make-truth 1.0 0.9) 30 30 {:input? true}))
(def state (core/add-event state robin-belief))

;; 5. Forward chain with variable matching
(def state (cycle/perform-cycles state 2))

;; 6. Check predictions
(let [[state flies-concept] (core/get-concept state flies)]
  (println "Predicted belief:" (:predicted-belief flies-concept)))
```

### Complete Decision Making Example

```clojure
;; 1. Initialize
(def state (core/init-state))

;; 2. Learn: press-key → door-opens
(def press-key (term/make-atomic-term "press-key"))
(def door-opens (term/make-atomic-term "door-opens"))

(def key-belief (event/make-belief press-key (truth/make-truth 1.0 0.9) 10 10 {:input? true}))
(def state (core/add-event state key-belief))

(def door-belief (event/make-belief door-opens (truth/make-truth 1.0 0.9) 20 20 {:input? true}))
(def state (core/add-event state door-belief))

;; 3. Process to learn implication
(def state (cycle/perform-cycles state 5))

;; 4. Create goal: want door opened
(def door-goal (event/make-goal door-opens (truth/make-truth 1.0 0.9) 30 30 {:input? true}))
(def state (core/add-event state door-goal))

;; 5. Process cycles - should derive subgoal: press-key!
(def state (cycle/perform-cycles state 3))

;; 6. Check derived subgoals
(let [[state key-concept] (core/get-concept state press-key)]
  (println "Goal spike:" (:goal-spike key-concept)))
```

---

## Configuration

State configuration in `:config` map:

```clojure
{:config
 {:truth-projection-decay 0.99        ;; Truth decay rate
  :motor-babbling false               ;; Enable exploration
  :motor-babbling-chance 0.2          ;; Probability of random action
  :volume 100}}                       ;; Output verbosity
```

Access:
```clojure
(get-in state [:config :truth-projection-decay])
(assoc-in state [:config :motor-babbling] true)
```

---

## Best Practices

1. **Always use tuple returns**: Many functions return `[state ...]` - destructure properly
2. **Immutable state**: Never mutate state, always use returned updated state
3. **Term equality**: Terms are compared by value, use as map keys directly
4. **Time management**: Keep track of `current-time` and pass consistently
5. **Event priorities**: Higher expectation = higher priority in queues
6. **Variable naming**: Use `$1-$9` for independent, `#1-#9` for dependent
7. **Testing**: Use differential tests to verify correctness against C ONA

---

## See Also

- [README.md](README.md) - Project overview and quick start
- [Differential Testing Guide](scripts/run_differential.sh) - Testing framework
- [C ONA Source](../src/) - Reference implementation
