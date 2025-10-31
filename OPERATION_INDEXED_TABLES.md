# Operation-Indexed Implication Tables - Architectural Analysis

**Date:** 2025-10-31
**Status:** Design document for architectural alignment with C ONA

---

## Problem Statement

Clojure ONA currently stores all implications in a single map `(:implications concept)`, while C ONA uses 11 operation-indexed tables. This causes incorrect behavior where non-executable implications (without operations) are selected for decision-making.

**Bug manifestation:**
- Clojure executes: `(red &/ bright)` (sensory input, not an operation!)
- C ONA executes: `^left` (correct operation)

---

## C ONA Architecture (src/Memory.c lines 298-328)

### Data Structure

```c
typedef struct {
    ...
    Table precondition_beliefs[OPERATIONS_MAX+1];  // 11 tables
    Table implication_links;                        // Declarative implications
    ...
} Concept;
```

**Table allocation:**
- `precondition_beliefs[0]`: Temporal implications WITHOUT operations (declarative)
- `precondition_beliefs[1..10]`: Temporal implications WITH operation 1..10 (procedural)
- `implication_links`: Regular implications (==> not =/>, not operation-related)

### Implication Classification Logic

**When adding implication** `<subject =/> predicate>`:

1. Extract subject and predicate
2. Concept created for predicate term
3. Determine operation index `opi`:

```c
int opi = 0;  // Default: no operation

if (TEMPORAL_IMPLICATION && subject is SEQUENCE) {
    Term potential_op = rightmost_term_of(subject);

    if (is_operation(potential_op) && is_executable(potential_op)) {
        opi = Memory_getOperationID(&potential_op);  // Returns 1..10
        sourceConceptTerm = strip_operation(subject);  // Remove ^op
    }
    // else: opi remains 0 (no operation)
}
```

4. Add implication to `precondition_beliefs[opi]`

**Examples:**

| Implication | Subject | Operation? | opi | Table |
|-------------|---------|------------|-----|-------|
| `<(red &/ bright) =/> goal>` | `(red &/ bright)` | No | 0 | `precondition_beliefs[0]` |
| `<((red &/ bright) &/ ^left) =/> goal>` | `((red &/ bright) &/ ^left)` | Yes: ^left | 1 | `precondition_beliefs[1]` |
| `<(context &/ ^right) =/> goal>` | `(context &/ ^right)` | Yes: ^right | 2 | `precondition_beliefs[2]` |

### Memory_getOperationID

```c
int Memory_getOperationID(Term *term) {
    Atom op_atom = Narsese_getOperationAtom(term);
    if (op_atom) {
        for (int k=1; k<=OPERATIONS_MAX; k++) {
            if (operations[k-1].term.atoms[0] == op_atom) {
                return k;  // Returns 1..10
            }
        }
    }
    return 0;  // Not found
}
```

**Key insight:** Operation IDs are 1-indexed (1..10), matching array indices `precondition_beliefs[1..10]`.

---

## Decision Making (src/Decision.c lines 423-477)

### Implication Retrieval

```c
for(int opi=1; opi<=OPERATIONS_MAX && operations[opi-1].term.atoms[0] != 0; opi++)
{
    for(int j=0; j<goalconcept->precondition_beliefs[opi].itemsAmount; j++)
    {
        Implication imp = goalconcept->precondition_beliefs[opi].array[j];
        // ... check if precondition matches, calculate desire
    }
}
```

**Critical observations:**
1. Loop starts at `opi=1`, NOT `opi=0`
2. Only scans operation-indexed tables (1..10)
3. **Never looks at `precondition_beliefs[0]`** for decision-making
4. Guarantees all implications have operations

**Why this works:**
- Only registered operations have implications in tables 1..10
- Automatically filters out non-executable implications
- O(1) lookup by operation ID

---

## Clojure ONA Current Implementation

### Current Structure

```clojure
(defrecord Concept
  [term
   ...
   implications])  ; Single map: {impl-term → Implication}
```

### Current Implication Addition (core.clj:270-278)

```clojure
(defn add-implication [concept impl-term impl truth ...]
  (let [existing-impl (get-in concept [:implications impl-term])
        final-impl (if existing-impl
                     (revise-implication existing-impl impl)
                     impl)]
    (assoc-in concept [:implications impl-term] final-impl)))
```

**Problem:** No operation-based classification!

### Current Decision Retrieval (decision.clj:238)

```clojure
(for [[impl sequence-concept goal-subst] matches
      :let [impls (vals (:implications concept))]]  ; Gets ALL implications!
  ...)
```

**Problem:** Includes implications without operations, leading to invalid executions.

---

## Proposed Clojure Architecture

### New Structure

```clojure
(defrecord Concept
  [term
   ...
   precondition-beliefs  ; Vector of 11 maps: [{impl-term → Implication}, ...]
   implication-links])   ; Map for regular (non-temporal) implications
```

**Rationale:**
- `precondition-beliefs` is a vector of size 11
- `precondition-beliefs[0]` = declarative temporal (no operation)
- `precondition-beliefs[1..10]` = procedural per-operation
- Matches C ONA structure exactly

### Implication Classification Logic

```clojure
(defn get-operation-index
  "Determine which table to use for implication.

  Args:
    impl-term - Implication term <subject =/> predicate>
    state - NAR state (for operation registry lookup)

  Returns:
    Integer 0-10:
    - 0 = no operation (declarative temporal)
    - 1..10 = operation ID (procedural)"
  [impl-term state]
  (let [subject (implication/get-precondition impl-term)]
    (if (term/sequence-term? subject)
      ;; Extract rightmost term
      (let [rightmost (term/get-rightmost-term subject)]
        (if (term/operation-term? rightmost)
          ;; Look up operation ID in registry
          (or (get-operation-id state rightmost) 0)
          0))
      0)))  ; Not a sequence = no operation
```

### Implication Addition

```clojure
(defn add-implication [concept impl-term impl truth state]
  (let [opi (get-operation-index impl-term state)
        existing-impl (get-in concept [:precondition-beliefs opi impl-term])
        final-impl (if existing-impl
                     (revise-implication existing-impl impl)
                     impl)]
    (assoc-in concept [:precondition-beliefs opi impl-term] final-impl)))
```

### Decision Retrieval

```clojure
(defn find-matching-implications [state goal]
  (let [goal-term (:term goal)
        all-concepts (vals (:concepts state))]
    (for [concept all-concepts
          concept-term (:term concept)
          ;; Iterate through operation tables ONLY (1..10)
          opi (range 1 11)
          :let [impl-table (nth (:precondition-beliefs concept) opi)]
          impl (vals impl-table)  ; Implications in this operation's table
          :let [postcondition (implication/get-postcondition impl)
                substitution (var/unify postcondition goal-term)]
          :when (:success substitution)]
      [impl concept substitution])))
```

**Key change:** Loop through `opi` 1..10, skip table 0.

---

## Implementation Plan

### Phase 1: Concept Structure Update

**Files to modify:**
- `src/ona/core.clj` - Concept record definition

**Changes:**
```clojure
(defrecord Concept
  [term
   priority
   usefulness
   use-count
   last-used
   belief
   belief-spike
   predicted-belief
   active-prediction
   precondition-beliefs   ; NEW: Vector of 11 maps
   implication-links])    ; Renamed from 'implications'

(defn make-concept [term]
  (->Concept term 0.0 0.0 0 0
             nil nil nil nil
             (vec (repeat 11 {}))  ; Initialize 11 empty maps
             {}))  ; Empty implication-links
```

### Phase 2: Operation Index Helper

**File:** `src/ona/core.clj`

```clojure
(defn get-operation-index
  "Determine operation table index for implication.
  Returns 0-10."
  [impl-term state]
  (let [subject (implication/get-precondition impl-term)]
    (if (term/sequence-term? subject)
      (let [rightmost (term/get-rightmost-term subject)]
        (if (term/operation-term? rightmost)
          (let [op-id (get-in state [:operations rightmost])]
            (or op-id 0))
          0))
      0)))
```

### Phase 3: Update Implication Addition

**File:** `src/ona/core.clj` lines 270-278

**Before:**
```clojure
(get-in concept [:implications impl-term])
(assoc-in concept [:implications impl-term] final-impl)
```

**After:**
```clojure
(let [opi (get-operation-index impl-term state)]
  (get-in concept [:precondition-beliefs opi impl-term])
  (assoc-in concept [:precondition-beliefs opi impl-term] final-impl))
```

### Phase 4: Update Decision Retrieval

**File:** `src/ona/decision.clj` line 238

**Before:**
```clojure
impls (vals (:implications concept))
```

**After:**
```clojure
;; Iterate through operation tables 1..10
[opi (range 1 11)
 :let [impl-table (nth (:precondition-beliefs concept) opi)]
 impl (vals impl-table)]
```

### Phase 5: Update All Other Access Points

**Files:**
- `src/ona/cycle.clj` - 5 access points
- `src/ona/core.clj` - 3 access points
- `src/ona/decision.clj` - 3 access points

**Strategy:**
- Determine if each access needs procedural (tables 1..10) or all implications (tables 0..10)
- Update accordingly

### Phase 6: Testing

**Test 1:** Simple operation implication
```bash
./ona shell < test/integration/01_single_pattern.nal
```

**Test 2:** Compound conditions
```bash
./ona shell < test/integration/03b_compound_simple.nal
```

**Test 3:** Differential against C ONA
```bash
python3 test/integration/run_differential_tests.py
```

**Expected:** Operations execute correctly, no sensory input treated as operation.

---

## Migration Path

### Step 1: Add New Fields (Backward Compatible)

```clojure
(defrecord Concept
  [term
   ...
   implications           ; OLD: Keep temporarily
   precondition-beliefs   ; NEW: Add alongside
   implication-links])    ; NEW: Add alongside
```

### Step 2: Dual-Write Phase

Write to both old and new structures during transition.

### Step 3: Update Readers One-by-One

Systematically update each access point to use new structure.

### Step 4: Remove Old Field

Once all readers updated, remove `implications` field.

---

## Expected Impact

### Correctness
- ✅ Only operations in decision-making
- ✅ Matches C ONA behavior
- ✅ No more invalid "executions" of sensory input

### Performance
- ✅ Faster decision queries (only scan relevant operation tables)
- ✅ Better memory locality
- ✅ O(1) operation lookup

### Maintainability
- ✅ Architecture matches C ONA (easier verification)
- ✅ Clear separation: declarative vs. procedural
- ✅ Future changes can follow C ONA patterns

---

## Open Questions

1. **Operation ID mapping:** How to map operation terms to IDs 1..10?
   - Need operation registry in state
   - Similar to C's global `operations[]` array

2. **Regular implications:** Where do non-temporal implications go?
   - C ONA: `implication_links` table
   - Should Clojure do the same?

3. **Query handling:** Do queries need all implications or just procedural?
   - Need to check C ONA query implementation

4. **Implication capacity:** C has TABLE_SIZE=120 per table
   - Should Clojure enforce same limit?
   - Or use unbounded maps?

---

## Next Steps

1. ✅ Study C ONA implementation
2. ✅ Document architecture
3. ⏭️  Implement operation registry in state
4. ⏭️  Update Concept structure
5. ⏭️  Implement get-operation-index
6. ⏭️  Update all 11 access points
7. ⏭️  Test comprehensively

---

**This document will guide the architectural refactoring to align Clojure ONA with C ONA's proven design.**
