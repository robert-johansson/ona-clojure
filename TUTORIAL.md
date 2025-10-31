# ONA-Clojure Tutorial

Step-by-step guide to using ONA-Clojure for NAL 6-8 reasoning.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Tutorial 1: Basic Temporal Reasoning](#tutorial-1-basic-temporal-reasoning)
3. [Tutorial 2: Variable-Based Pattern Learning](#tutorial-2-variable-based-pattern-learning)
4. [Tutorial 3: Goal-Driven Decision Making](#tutorial-3-goal-driven-decision-making)
5. [Tutorial 4: Advanced Pattern Matching](#tutorial-4-advanced-pattern-matching)
6. [Tutorial 5: Building a Simple Agent](#tutorial-5-building-a-simple-agent)

---

## Getting Started

### Installation

```bash
cd ona-clojure
clj -P  # Download dependencies
```

### Running the Shell

```bash
clj -M:ona shell
```

### Basic Shell Commands

```narsese
*reset                    // Clear all memory
*volume=100              // Set output verbosity
*currenttime=1           // Set system time
*motorbabbling=false    // Disable exploration
*concepts               // Show all concepts
*stats                  // Show statistics
quit                    // Exit shell
```

---

## Tutorial 1: Basic Temporal Reasoning

**Goal**: Learn that birds fly through temporal association.

### Step 1: Reset and Configure

```narsese
*reset
*volume=100
*currenttime=1
```

### Step 2: Input Temporal Events

```narsese
// Observe a bird at time 2
<bird --> observed>. :|: {1.0 0.9}
5  // Execute 5 cycles

// Bird flies at time 7
<bird --> flies>. :|: {1.0 0.9}
10  // Execute 10 cycles
```

###Step 3: Check Learned Implication

```narsese
*concepts
```

**Expected Output:**
```
term: <bird --> observed> { ...
  implications: [{ "term": "<<bird --> observed> =/> <bird --> flies>>", ...}]
}
```

**What Happened:**
- System observed two events with temporal offset
- Formed temporal implication: `<bird-observed =/> bird-flies>`
- Can now predict "flies" when "bird observed"

### Step 4: Test Prediction

```narsese
// Observe bird again
<bird --> observed>. :|: {1.0 0.9}
5

*concepts  // Should show prediction for "bird flies"
```

**Key Concepts:**
- `.:|:` - Temporal belief (occurred now)
- `<A =/> B>` - Temporal implication (A precedes B)
- Numbers execute cycles

---

## Tutorial 2: Variable-Based Pattern Learning

**Goal**: Learn general pattern that applies to all animals.

### Step 1: Setup

```narsese
*reset
*volume=100
```

### Step 2: Learn Pattern with Multiple Examples

```narsese
// Example 1: Robin
<robin --> animal>. {1.0 0.9}
1
<robin --> flies>. :|: {1.0 0.9}
10

// Example 2: Sparrow
<sparrow --> animal>. {1.0 0.9}
1
<sparrow --> flies>. :|: {1.0 0.9}
10
```

### Step 3: System Generalizes with Variables

The system internally creates:
```
<$1 --> animal> =/> <$1 --> flies>
```

Where `$1` is an independent variable that can match any term.

### Step 4: Apply to New Animal

```narsese
// New bird we haven't seen fly yet
<bluebird --> animal>. {1.0 0.9}
5

*concepts  // Should predict: bluebird flies
```

**Expected:**
```
term: <bluebird --> flies> {
  "predicted-belief": { "frequency": ..., "confidence": ...}
}
```

**Key Concepts:**
- `$1`, `$2`, ... - Independent variables
- Variable unification matches patterns
- Forward chaining applies learned rules

### Programming Example

```clojure
(require '[ona.core :as core])
(require '[ona.cycle :as cycle])
(require '[ona.term :as term])
(require '[ona.truth :as truth])
(require '[ona.event :as event])
(require '[ona.variable :as var])

;; 1. Initialize
(def state (core/init-state))

;; 2. Create pattern with variable
(def $1 (term/make-atomic-term "$1"))
(def flies (term/make-atomic-term "flies"))
(def impl-term (term/make-temporal-implication $1 flies))

;; 3. Add to system (normally learned automatically)
(def impl (ona.implication/make-implication
            impl-term
            (truth/make-truth 0.9 0.8)
            10.0 100))
(def state (core/add-implication state impl $1))

;; 4. Test with concrete bird
(def bluebird (term/make-atomic-term "bluebird"))
(def belief (event/make-belief bluebird (truth/make-truth 1.0 0.9) 10 10 {:input? true}))
(def state (core/add-event state belief))

;; 5. Forward chain
(def state (cycle/apply-forward-chaining state belief 10))

;; 6. Check prediction
(let [[state flies-concept] (core/get-concept state flies)]
  (println "Predicted:" (:predicted-belief flies-concept)))
```

---

## Tutorial 3: Goal-Driven Decision Making

**Goal**: Teach system to open door by pressing key.

### Step 1: Setup

```narsese
*reset
*volume=100
*currenttime=1
```

### Step 2: Learn Causal Relationship

```narsese
// Teach: pressing key opens door
<key-pressed --> action>. :|: {1.0 0.9}
5
<door-opened --> state>. :|: {1.0 0.9}
10
```

System learns: `<key-pressed =/> door-opened>`

### Step 3: Create Goal

```narsese
// Goal: want door opened
<door-opened --> state>! {1.0 0.9}
5
```

### Step 4: System Derives Subgoal

System reasoning:
1. Goal: `door-opened!`
2. Has implication: `<key-pressed =/> door-opened>`
3. Backward chaining derives: `key-pressed!` (subgoal)

### Step 5: Check Result

```narsese
*concepts
```

**Expected:**
```
term: <key-pressed --> action> {
  "goal-spike": { "truth": { "frequency": 0.9, "confidence": ...}}
}
```

**Key Concepts:**
- `!` - Goal (desired state)
- Backward chaining: Work backwards from goal
- Subgoal derivation: Find preconditions

### Programming Example

```clojure
(require '[ona.decision :as dec])

;; 1. Setup state with learned implication
(def press-key (term/make-atomic-term "press-key"))
(def door-opens (term/make-atomic-term "door-opens"))

;; (Assume implication already learned)

;; 2. Create goal
(def goal (event/make-goal door-opens (truth/make-truth 1.0 0.9) 20 10 {:input? true}))

;; 3. Find best action
(def decision (dec/best-candidate state goal 10))

;; 4. Check decision
(println "Should execute?" (:execute? decision))
(println "Desire:" (:desire decision))
(println "Action:" (:representation (:operation-term decision)))
```

---

## Tutorial 4: Advanced Pattern Matching

**Goal**: Use compound patterns with variables.

### Step 1: Learn General Rule

```narsese
*reset
*volume=100

// Pattern: All animals are living
<robin --> animal>. {1.0 0.9}
1
<robin --> living>. :|: {1.0 0.9}
10
```

System generalizes to:
```
<($1 --> animal) =/> ($1 --> living)>
```

### Step 2: Apply to New Case

```narsese
<eagle --> animal>. {1.0 0.9}
5

*concepts  // Should predict: eagle is living
```

**What's Different:**
- Pattern involves compound terms: `($1 --> animal)`
- Variable `$1` appears in both subject positions
- More complex unification

### Step 3: Dependent Variables

```narsese
*reset

// Pattern: Seeing same thing twice = "same"
<bird --> seen>. :|: {1.0 0.9}
2
<bird --> seen>. :|: {1.0 0.9}
4
<same --> result>. :|: {1.0 0.9}
6
```

System learns:
```
<(#1 &/ #1) =/> same>  // #1 must bind consistently
```

**Test Consistent Binding:**
```narsese
<cat --> seen>. :|: {1.0 0.9}
1
<cat --> seen>. :|: {1.0 0.9}
3
// Should predict: same
```

**Test Inconsistent Binding:**
```narsese
<cat --> seen>. :|: {1.0 0.9}
1
<dog --> seen>. :|: {1.0 0.9}
3
// Should NOT predict same (different terms)
```

**Key Concepts:**
- `$1` - Independent variable (can differ)
- `#1` - Dependent variable (must be same)
- `?1` - Query variable (for questions)

---

## Tutorial 5: Building a Simple Agent

**Goal**: Create an agent that learns and acts.

### Complete Example: Door-Opening Agent

```clojure
(ns my-agent
  (:require [ona.core :as core]
            [ona.cycle :as cycle]
            [ona.term :as term]
            [ona.truth :as truth]
            [ona.event :as event]
            [ona.decision :as dec]))

(defn init-agent []
  "Initialize agent with motor operations"
  (let [state (core/init-state)]
    ;; Register operations
    (-> state
        (assoc-in [:operations "press-key"]
                  {:id 1 :name (term/make-atomic-term "press-key")})
        (assoc-in [:config :motor-babbling] true)
        (assoc-in [:config :motor-babbling-chance] 0.1))))

(defn observe [state observation time]
  "Add observation to agent's memory"
  (let [belief (event/make-belief
                 observation
                 (truth/make-truth 1.0 0.9)
                 time time
                 {:input? true})
        state (core/add-event state belief)]
    (cycle/perform-cycles state 3)))

(defn set-goal [state goal time]
  "Give agent a goal"
  (let [goal-event (event/make-goal
                     goal
                     (truth/make-truth 1.0 0.9)
                     time time
                     {:input? true})
        state (core/add-event state goal-event)]
    (cycle/perform-cycles state 5)))

(defn get-decision [state goal time]
  "Get agent's decision for achieving goal"
  (dec/best-candidate state goal time))

;; Usage:
(comment
  ;; 1. Create agent
  (def agent (init-agent))

  ;; 2. Teach: key opens door
  (def agent (observe agent
                      (term/make-atomic-term "key-pressed")
                      10))
  (def agent (observe agent
                      (term/make-atomic-term "door-opened")
                      20))

  ;; 3. Set goal: want door opened
  (def goal-term (term/make-atomic-term "door-opened"))
  (def agent (set-goal agent goal-term 30))

  ;; 4. Get decision
  (def decision (get-decision agent
                              (event/make-goal goal-term
                                              (truth/make-truth 1.0 0.9)
                                              40 40 {:input? true})
                              40))

  (println "Decision:" decision)
  (println "Should press key:" (:execute? decision))
  )
```

### Adding Anticipation

```clojure
(defn execute-with-anticipation [state decision impl belief time]
  "Execute decision and create anticipation"
  (let [anticipation (dec/create-anticipation decision impl belief time)
        state (update state :anticipations (fnil conj []) anticipation)]
    state))

(defn check-expectations [state time]
  "Validate anticipations and learn from failures"
  (let [[state remaining] (dec/check-anticipations state time)]
    (assoc state :anticipations remaining)))

;; Usage:
(comment
  ;; After executing action
  (def agent (execute-with-anticipation agent decision impl belief 40))

  ;; Later, check if expectation met
  (def agent (check-expectations agent 60))
  ;; If door didn't open, system weakens implication
  )
```

### Complete Agent Loop

```clojure
(defn agent-loop [state observations goals max-cycles]
  "Main agent loop"
  (loop [state state
         cycle-count 0
         current-time 0]
    (if (>= cycle-count max-cycles)
      state
      (let [;; Process observations
            state (reduce (fn [s obs]
                           (observe s obs current-time))
                         state
                         observations)

            ;; Process goals
            state (reduce (fn [s goal]
                           (set-goal s goal current-time))
                         state
                         goals)

            ;; Check anticipations
            state (check-expectations state current-time)

            ;; Execute one reasoning cycle
            state (cycle/perform-cycles state 1)]

        (recur state
               (inc cycle-count)
               (inc current-time))))))

;; Usage:
(comment
  (def final-agent (agent-loop
                     agent
                     [(term/make-atomic-term "key-pressed")
                      (term/make-atomic-term "door-opened")]
                     [(term/make-atomic-term "door-opened")]
                     100))
  )
```

---

## Common Patterns

### Pattern 1: Learning from Sequence

```narsese
// Input sequence
A. :|: {1.0 0.9}
[time-offset]
B. :|: {1.0 0.9}
[cycles]

// System learns: <A =/> B>
```

### Pattern 2: Variable Generalization

```narsese
// Multiple examples
<x1 --> type>. {1.0 0.9}
1
<x1 --> property>. :|: {1.0 0.9}
10

<x2 --> type>. {1.0 0.9}
1
<x2 --> property>. :|: {1.0 0.9}
10

// System generalizes: <($1 --> type) =/> ($1 --> property)>
```

### Pattern 3: Goal Decomposition

```narsese
// Learn causal chain
A. :|: {1.0 0.9}
5
B. :|: {1.0 0.9}
10
C. :|: {1.0 0.9}
15

// Creates: <A =/> B> and <B =/> C>

// Goal: C!
C!
5

// Derives: B! (subgoal)
// Derives: A! (sub-subgoal)
```

---

## Troubleshooting

### Issue: Implication Not Learned

**Possible Causes:**
1. Time offset too large or too small
2. Not enough cycles after second event
3. Truth values too low

**Solution:**
```narsese
// Ensure reasonable time offset
A. :|: {1.0 0.9}
3-10  // Good range
B. :|: {1.0 0.9}
10  // Give system time to process
```

### Issue: No Prediction Generated

**Possible Causes:**
1. Variable concept not in InvertedAtomIndex
2. Truth projection decayed confidence too much
3. Related concepts not found

**Solution:**
- Check `*concepts` to verify implication exists
- Ensure implications have high truth values
- Add more training examples

### Issue: Goal Not Decomposed

**Possible Causes:**
1. No matching implication
2. Precondition not believed
3. Desire below threshold

**Solution:**
```narsese
// Strengthen implication with revision
<A =/> B>. {0.95 0.9}  // Higher truth

// Ensure precondition known
A. {1.0 0.9}
```

---

## Next Steps

1. **Read API Documentation**: See [API.md](API.md) for complete reference
2. **Run Differential Tests**: `bash scripts/run_differential.sh`
3. **Explore Examples**: See `examples/` directory
4. **Build Custom Agent**: Use patterns from Tutorial 5
5. **Contribute**: Add your own test cases and examples

---

## Resources

- [README.md](README.md) - Project overview
- [API.md](API.md) - Complete API reference
- [Differential Tests](test/ona/differential/fixtures/) - Test examples
- [C ONA Source](../src/) - Reference implementation

---

## Tips

1. **Start simple**: Begin with 2-3 events before complex scenarios
2. **Use variables explicitly**: When needed, create variable terms directly
3. **Check intermediate states**: Use `*concepts` frequently during development
4. **Time matters**: Temporal offset affects implication learning
5. **Truth values matter**: Higher confidence = stronger predictions
6. **Cycle count matters**: Give system time to process (5-10 cycles typical)

---

## Quick Reference Card

```narsese
// Configuration
*reset                        // Clear memory
*volume=N                     // Output level
*currenttime=N               // Set time
*motorbabbling=true/false   // Exploration

// Input
<term>. {f c}                // Belief
<term>. :|: {f c}           // Temporal belief
<term>! {f c}               // Goal
<term>?                     // Question
N                           // Execute N cycles

// Introspection
*concepts                   // Show all concepts
*stats                      // Statistics

// Terms
<A --> B>                   // Inheritance
<A =/> B>                   // Temporal implication
<A &/ B>                    // Sequence
$1, $2                      // Independent variables
#1, #2                      // Dependent variables
?1, ?2                      // Query variables
```

**Happy reasoning!** 🧠
