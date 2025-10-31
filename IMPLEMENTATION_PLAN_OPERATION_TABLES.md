# Implementation Plan: Operation-Indexed Implication Tables

**Date:** 2025-10-31
**Status:** Ready to implement
**Estimated Time:** 3-4 hours
**Complexity:** Medium-High (core data structure change)

---

## Executive Summary

### Problem
Clojure ONA stores all implications in single map, causing non-executable implications to be selected for decision-making. Results in system trying to "execute" sensory input like `(red & bright)` instead of operations like `^left`.

### Solution
Replicate C ONA's operation-indexed table architecture: 11 tables per concept, where tables 1-10 are indexed by operation ID, and decision-making only scans those tables.

### Impact
- **Correctness**: Only operations selected for execution
- **Performance**: O(1) operation lookup, targeted queries
- **Architecture**: Aligns with C ONA for easier verification

---

## Prerequisites

### Required Reading
1. `/research/02-algorithms/procedural-inference.md` - Decision making algorithm
2. `/research/01-data-structures/concept.md` - Concept structure
3. `OPERATION_INDEXED_TABLES.md` - Architectural analysis (just created)

### C ONA Reference Points
- **Implication storage**: `src/Memory.c` lines 298-328
- **Operation ID lookup**: `src/Memory.c` lines 244-257
- **Decision retrieval**: `src/Decision.c` lines 423-477
- **Concept structure**: `src/Concept.h` lines 14-30

### Test Baseline
Before starting, run and record results:
```bash
python3 test/integration/run_integration_tests.py > baseline_before.txt
python3 test/integration/run_differential_tests.py > differential_before.txt
```

---

## Phase 1: Add Helper Functions

**Goal**: Create utilities for operation index determination

### Step 1.1: Add get-operation-id to operation.clj

**File**: `src/ona/operation.clj`
**Location**: After line 93 (after register-operation)
**Reference**: C ONA `Memory_getOperationID` (Memory.c:244-257)

```clojure
(defn get-operation-id
  "Get operation ID by term.

  Matches C ONA Memory_getOperationID behavior:
  - Returns 1..10 for registered operations
  - Returns nil if operation not found

  Args:
    state - NAR state with :operations map
    op-term - Operation term (e.g., ^left)

  Returns:
    Integer ID (1-10) or nil"
  [state op-term]
  (when-let [op (get-in state [:operations op-term])]
    (:id op)))
```

**Test**:
```clojure
(let [state (-> (make-initial-state)
                (register-operation "^left" (fn [_] {}))
                (register-operation "^right" (fn [_] {})))]
  (assert (= 1 (get-operation-id state (term/parse-term "^left"))))
  (assert (= 2 (get-operation-id state (term/parse-term "^right"))))
  (assert (nil? (get-operation-id state (term/parse-term "^unknown")))))
```

### Step 1.2: Add get-operation-index to core.clj

**File**: `src/ona/core.clj`
**Location**: After line 40 (after make-concept)
**Reference**: C ONA logic in Memory.c:298-320

```clojure
(defn get-operation-index
  "Determine which precondition_beliefs table to use for implication.

  Follows C ONA logic (Memory.c:298-320):
  - Extract subject from <subject =/> predicate>
  - If subject is sequence, check rightmost term
  - If rightmost is operation, get its ID (1-10)
  - Otherwise return 0 (declarative temporal)

  Args:
    impl-term - Implication term
    state - NAR state (for operation lookup)

  Returns:
    Integer 0-10:
    - 0 = no operation (declarative)
    - 1-10 = operation ID (procedural)"
  [impl-term state]
  (let [subject (implication/get-precondition impl-term)]
    (if (term/sequence-term? subject)
      ;; Check rightmost term in sequence
      (let [rightmost (term/get-rightmost-term subject)]
        (if (term/operation-term? rightmost)
          ;; Look up operation ID
          (or (operation/get-operation-id state rightmost) 0)
          0))
      0)))
```

**Test**:
```clojure
(let [state (-> (make-initial-state)
                (operation/register-operation "^left" (fn [_] {})))]
  ;; No operation
  (assert (= 0 (get-operation-index
                 (term/parse-term "<red =/> goal>") state)))

  ;; With operation
  (assert (= 1 (get-operation-index
                 (term/parse-term "<(red &/ ^left) =/> goal>") state))))
```

**Checkpoint 1.2**: Both helper functions pass tests.

---

## Phase 2: Update Concept Structure

**Goal**: Change `:implications` to `:precondition-beliefs` + `:implication-links`

### Step 2.1: Update Concept record

**File**: `src/ona/core.clj`
**Location**: Lines 14-25
**Reference**: C Concept struct (Concept.h:14-30)

**Before**:
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
   implications])  ; OLD: single map
```

**After**:
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
   precondition-beliefs   ; NEW: vector of 11 maps
   implication-links])    ; NEW: for non-temporal implications
```

### Step 2.2: Update make-concept

**File**: `src/ona/core.clj`
**Location**: Lines 27-40
**Reference**: C concept initialization

**Before**:
```clojure
(defn make-concept
  "Create a new concept with default values"
  [term]
  (->Concept term 0.0 0.0 0 0
             nil nil nil nil
             {}))  ; Empty implications map
```

**After**:
```clojure
(defn make-concept
  "Create a new concept with default values.

  Initializes precondition-beliefs as vector of 11 empty maps:
  - Index 0: Declarative temporal implications (no operation)
  - Index 1-10: Procedural implications per operation"
  [term]
  (->Concept term 0.0 0.0 0 0
             nil nil nil nil
             (vec (repeat 11 {}))  ; 11 empty maps
             {}))                  ; Empty implication-links
```

**Checkpoint 2.2**: Code compiles, tests may fail (expected).

---

## Phase 3: Update Implication Addition

**Goal**: Route implications to correct table based on operation

### Step 3.1: Update add-implication function

**File**: `src/ona/core.clj`
**Location**: Lines 260-280
**Reference**: C ONA Memory_AddEvent (Memory.c:298-336)

**Current code**:
```clojure
(defn add-implication [concept impl-term impl truth ...]
  (let [existing-impl (get-in concept [:implications impl-term])
        final-impl (if existing-impl
                     (revise-implication existing-impl impl)
                     impl)]
    (assoc-in concept [:implications impl-term] final-impl)))
```

**New code**:
```clojure
(defn add-implication
  "Add or revise implication in concept.

  Routes to operation-indexed table following C ONA (Memory.c:298-328):
  - Determine operation index (0-10)
  - Check if implication exists in that table
  - Revise if exists, add if new
  - Store in precondition-beliefs[opi]

  Args:
    concept - Target concept
    impl-term - Implication term
    impl - Implication record
    truth - Truth value
    state - NAR state (for operation lookup)

  Returns:
    Updated concept"
  [concept impl-term impl truth state]
  (let [;; Determine which table (0-10)
        opi (get-operation-index impl-term state)

        ;; Check for existing implication in this table
        existing-impl (get-in concept [:precondition-beliefs opi impl-term])

        ;; Revise or use new
        final-impl (if existing-impl
                     (revise-implication existing-impl impl truth)
                     impl)]

    ;; Store in operation-indexed table
    (assoc-in concept [:precondition-beliefs opi impl-term] final-impl)))
```

**IMPORTANT**: This changes function signature (adds `state` parameter).

### Step 3.2: Update all callers of add-implication

**Locations to update**:

1. **File**: `src/ona/core.clj`
   **Line**: ~278 (in event processing)
   **Change**: Pass `state` to add-implication

2. **File**: `src/ona/cycle.clj`
   **Lines**: 433, 465 (in implication formation)
   **Change**: Pass `state` to add-implication

3. **File**: `src/ona/decision.clj`
   **Line**: ~529 (in implication revision)
   **Change**: Pass `state` to add-implication

**Example change**:
```clojure
;; Before
(add-implication concept impl-term impl truth)

;; After
(add-implication concept impl-term impl truth state)
```

**Checkpoint 3.2**: All add-implication calls updated, code compiles.

---

## Phase 4: Update Decision Retrieval

**Goal**: Query only operation tables (1-10), skip declarative (0)

### Step 4.1: Update find-matching-implications

**File**: `src/ona/decision.clj`
**Location**: Lines 211-258
**Reference**: C ONA Decision_BestCandidate (Decision.c:423-477)

**Current code** (line 238):
```clojure
impls (vals (:implications concept))
```

**New code**:
```clojure
;; Iterate through operation tables 1-10 ONLY
;; This matches C ONA: for(int opi=1; opi<=OPERATIONS_MAX; opi++)
[opi (range 1 11)  ; Tables 1-10, skip 0
 :let [impl-table (nth (:precondition-beliefs concept) opi)]
 impl (vals impl-table)]
```

**Full updated function**:
```clojure
(defn find-matching-implications
  "Find implications whose postcondition matches the goal.

  CRITICAL: Only scans operation tables 1-10 (procedural implications).
  Skips table 0 (declarative temporal) to match C ONA behavior.

  Reference: C ONA Decision_BestCandidate (Decision.c:423-477)

  Args:
    state - NAR state
    goal - Goal event

  Returns:
    Sequence of [impl concept substitution] tuples"
  [state goal]
  (let [goal-term (:term goal)
        volume (:volume state)
        all-concepts (vals (:concepts state))]

    (when (= volume 100)
      (println (str "^debug[find-matching-implications]: Goal = " (term/format-term goal-term)))
      (println (str "^debug[find-matching-implications]: Scanning " (count all-concepts) " concepts")))

    (let [results
          (for [concept all-concepts
                :let [concept-term (:term concept)]

                ;; CRITICAL: Only scan operation tables 1-10
                ;; Matches C ONA: for(int opi=1; opi<=OPERATIONS_MAX; opi++)
                opi (range 1 11)
                :let [impl-table (nth (:precondition-beliefs concept) opi)]

                :when (seq impl-table)  ; Skip empty tables
                :let [_ (when (= volume 100)
                          (println (str "^debug[find-matching-implications]:   Concept "
                                       (term/format-term concept-term)
                                       " table[" opi "] has " (count impl-table) " implications")))]

                impl (vals impl-table)
                :let [postcondition (implication/get-postcondition impl)
                      _ (when (= volume 100)
                          (println (str "^debug[find-matching-implications]:     Impl: "
                                       (term/format-term (:term impl)))))
                      substitution (var/unify postcondition goal-term)
                      _ (when (= volume 100)
                          (println (str "^debug[find-matching-implications]:       Match: "
                                       (:success substitution))))]
                :when (:success substitution)]
            [impl concept substitution])]

      (when (= volume 100)
        (println (str "^debug[find-matching-implications]: Found " (count results) " matching implications")))

      results)))
```

**Key changes**:
1. Added `opi (range 1 11)` to iterate tables 1-10
2. Extract table: `(nth (:precondition-beliefs concept) opi)`
3. Added debug output showing which table being scanned
4. Skip table 0 entirely

**Checkpoint 4.1**: Decision code now queries correct tables.

---

## Phase 5: Update Cycle Processing

**Goal**: Update implication formation and prediction code

### Step 5.1: Update cycle.clj implication access

**File**: `src/ona/cycle.clj`
**Locations**: Lines 334, 433, 465

**Line 334** (in process-belief-events or similar):
```clojure
;; Before
(let [implications (vals (:implications concept))]
  ...)

;; After - need ALL implications for belief processing
(let [;; Concatenate all tables (0-10) for belief processing
      all-implications (mapcat vals (:precondition-beliefs concept))]
  ...)
```

**Lines 433, 465** (in implication formation):
```clojure
;; Already updated in Phase 3.2
;; Just verify state is passed correctly
(assoc-in state
          [:concepts concept-key :precondition-beliefs opi impl-term]
          impl)
```

**Checkpoint 5.1**: Cycle processing uses correct table access.

---

## Phase 6: Update Other Access Points

**Goal**: Fix remaining implications access

### Step 6.1: Update get-concept-implications

**File**: `src/ona/core.clj`
**Location**: Line 297

**Before**:
```clojure
(defn get-concept-implications [concept]
  (vec (vals (:implications concept))))
```

**After**:
```clojure
(defn get-concept-implications
  "Get all implications from concept.

  Returns implications from ALL tables (0-10) for completeness.
  Use this for queries and display, NOT for decision-making."
  [concept]
  (vec (mapcat vals (:precondition-beliefs concept))))
```

### Step 6.2: Update get-implication

**File**: `src/ona/core.clj`
**Location**: Line 312

**Before**:
```clojure
(defn get-implication [state implication-term]
  (get-in state [:concepts key :implications implication-term]))
```

**After**:
```clojure
(defn get-implication
  "Get specific implication from concept.

  Note: Must search all tables since we don't know which table it's in."
  [state concept-key implication-term]
  ;; Search all 11 tables
  (some (fn [table-idx]
          (get-in state [:concepts concept-key :precondition-beliefs table-idx implication-term]))
        (range 11)))
```

### Step 6.3: Update implication revision in decision.clj

**File**: `src/ona/decision.clj`
**Location**: Lines 523-530

**Before**:
```clojure
(let [existing-impl (get-in state [:concepts concept-key :implications impl-term])
      ...]
  (assoc-in state [:concepts concept-key :implications impl-term] revised-impl))
```

**After**:
```clojure
(let [;; Determine table index
      opi (core/get-operation-index impl-term state)

      ;; Get existing from correct table
      existing-impl (get-in state [:concepts concept-key :precondition-beliefs opi impl-term])
      ...]

  ;; Store in correct table
  (assoc-in state [:concepts concept-key :precondition-beliefs opi impl-term] revised-impl))
```

**Checkpoint 6.3**: All implications access updated.

---

## Phase 7: Testing and Verification

**Goal**: Verify correctness against C ONA

### Step 7.1: Unit tests (manual verification)

Test each helper function:
```bash
# In REPL
(require '[ona.core :as core])
(require '[ona.operation :as operation])
(require '[ona.term :as term])

; Test get-operation-id
(def state (-> (core/make-initial-state)
               (operation/register-operation "^left" (fn [_] {}))
               (operation/register-operation "^right" (fn [_] {}))))

(operation/get-operation-id state (term/parse-term "^left"))
; => 1

; Test get-operation-index
(core/get-operation-index
  (term/parse-term "<(red &/ ^left) =/> goal>")
  state)
; => 1

(core/get-operation-index
  (term/parse-term "<red =/> goal>")
  state)
; => 0
```

### Step 7.2: Integration test - Single pattern

**Test**: `test/integration/01_single_pattern.nal`

```bash
./ona shell < test/integration/01_single_pattern.nal 2>&1 | grep "executed"
```

**Expected**: Operation executes (same as before fix)
**Success criteria**: Test still passes

### Step 7.3: Integration test - Compound conditions

**Test**: `test/integration/03b_compound_simple.nal`

```bash
./ona shell < test/integration/03b_compound_simple.nal 2>&1 | grep -A2 "Execute?: true"
```

**Expected**:
- Test 1: Execute `^left` (NOT `(red &/ bright)`)
- Test 2: Execute `^right` (NOT `(blue &/ bright)`)
- Test 3: Execute `^left`

**Success criteria**: Operations are atomic terms (^left, ^right), not compound sequences

### Step 7.4: Differential testing

```bash
python3 test/integration/run_differential_tests.py > differential_after.txt
diff differential_before.txt differential_after.txt
```

**Expected changes**:
- Execution counts may change (implementation difference)
- Operations executed should be ATOMIC TERMS only
- No compound sequences in execution list

### Step 7.5: C ONA comparison

**Run same test on C ONA**:
```bash
cd /Users/robert/claude/ONA/OpenNARS-for-Applications
./NAR shell < ona-clojure/test/integration/03b_compound_simple.nal 2>&1 | grep "executed"
```

**Compare**:
- Both should execute operations (^left, ^right)
- Neither should execute compound sequences
- Exact execution counts may differ (different strategies)

**Checkpoint 7.5**: Behavior matches C ONA (operations only).

---

## Phase 8: Documentation and Cleanup

### Step 8.1: Add inline comments

Add references to C ONA in key locations:
```clojure
;; C Reference: Memory.c:298-320 - Operation index determination
;; C Reference: Decision.c:423-477 - Only scan tables 1-10
```

### Step 8.2: Update OPERATION_INDEXED_TABLES.md

Mark implementation as complete, add "Lessons Learned" section.

### Step 8.3: Create test verification summary

Document in new file: `OPERATION_TABLES_VERIFICATION.md`
- What changed
- Test results before/after
- C ONA comparison results
- Known differences (execution counts, etc.)

**Checkpoint 8.3**: All documentation updated.

---

## Rollback Plan

If implementation fails:

### Quick rollback
```bash
git checkout HEAD -- src/ona/core.clj src/ona/decision.clj src/ona/cycle.clj
./scripts/build_native.sh
```

### Incremental rollback
Each phase is a checkpoint. Can rollback to any phase by:
1. Identify last working phase
2. Cherry-pick changes from that phase
3. Discard later changes

---

## Success Criteria

### Must Have
1. ✅ Code compiles and builds
2. ✅ Integration tests pass (01, 02_v2 at minimum)
3. ✅ Operations are atomic terms (^left, ^right), not sequences
4. ✅ No more "executing" sensory input like `(red & bright)`

### Should Have
1. ✅ All 6 integration tests execute (may not all pass)
2. ✅ Differential tests show operation execution
3. ✅ Behavior similar to C ONA (operations selected)

### Nice to Have
1. Execution counts closer to C ONA
2. All tests pass with 100% accuracy
3. Performance improvement measurable

---

## Estimated Timeline

- **Phase 1** (Helpers): 20 minutes
- **Phase 2** (Structure): 15 minutes
- **Phase 3** (Addition): 30 minutes
- **Phase 4** (Decision): 30 minutes
- **Phase 5** (Cycle): 20 minutes
- **Phase 6** (Other): 30 minutes
- **Phase 7** (Testing): 45 minutes
- **Phase 8** (Docs): 30 minutes

**Total**: ~3.5 hours

Add 30-60 minutes buffer for unexpected issues.

---

## Implementation Checklist

Copy this for tracking progress:

```markdown
## Phase 1: Helpers
- [ ] Add get-operation-id to operation.clj
- [ ] Add get-operation-index to core.clj
- [ ] Test both functions
- [ ] Checkpoint 1.2 passed

## Phase 2: Structure
- [ ] Update Concept record
- [ ] Update make-concept
- [ ] Code compiles
- [ ] Checkpoint 2.2 passed

## Phase 3: Addition
- [ ] Update add-implication signature
- [ ] Update caller in core.clj
- [ ] Update callers in cycle.clj (2 places)
- [ ] Update caller in decision.clj
- [ ] Checkpoint 3.2 passed

## Phase 4: Decision
- [ ] Update find-matching-implications
- [ ] Add debug output
- [ ] Test manually with 03b test
- [ ] Checkpoint 4.1 passed

## Phase 5: Cycle
- [ ] Update line 334 (all implications access)
- [ ] Verify lines 433, 465 (already updated)
- [ ] Checkpoint 5.1 passed

## Phase 6: Other
- [ ] Update get-concept-implications
- [ ] Update get-implication
- [ ] Update decision.clj revision
- [ ] Checkpoint 6.3 passed

## Phase 7: Testing
- [ ] Unit tests (REPL)
- [ ] Test 01 passes
- [ ] Test 03b shows atomic operations
- [ ] Differential test comparison
- [ ] C ONA comparison
- [ ] Checkpoint 7.5 passed

## Phase 8: Documentation
- [ ] Add C ONA references
- [ ] Update OPERATION_INDEXED_TABLES.md
- [ ] Create OPERATION_TABLES_VERIFICATION.md
- [ ] Checkpoint 8.3 passed

## Final
- [ ] All tests pass
- [ ] Git commit with summary
- [ ] Push to GitHub
```

---

## Questions to Resolve During Implementation

1. **Implication links table**: Should non-temporal implications (==>) go in `:implication-links`?
   - **Decision**: Yes, following C ONA structure

2. **Table capacity**: Should we enforce TABLE_SIZE=120 limit per table?
   - **Decision**: No, use unbounded maps for simplicity (can add later)

3. **Query handling**: Should queries search all tables or just procedural?
   - **Decision**: All tables (queries need declarative implications too)

4. **Debug output**: Keep verbose operation table logging?
   - **Decision**: Yes, controlled by volume=100

---

## Post-Implementation Analysis

After completing implementation, answer these questions:

1. **What was hardest?** (e.g., tracking down all access points)
2. **What unexpected issues occurred?** (e.g., dependency on operation registry)
3. **Performance impact?** (e.g., faster, slower, same)
4. **Behavior changes?** (e.g., different execution counts, new patterns)
5. **Architecture insights?** (e.g., why C ONA designed this way)

Document in: `OPERATION_TABLES_LESSONS_LEARNED.md`

---

## Next Steps After This Implementation

1. **Verify execution correctness**: Create focused tests showing ^right executes
2. **Investigate double execution**: Why operations execute 2x per goal
3. **Tune execution thresholds**: Align with C ONA if desired
4. **Profile performance**: Measure query time before/after
5. **Expand test suite**: More complex scenarios

---

**This plan is ready to execute after conversation compact.**
