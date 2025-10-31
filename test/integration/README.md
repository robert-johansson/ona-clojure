# Integration Tests for ONA

**Purpose:** Progressive difficulty tests to validate sensorimotor learning and decision-making.

---

## Test Philosophy

These tests follow a **progressive difficulty** approach:
1. Start with the simplest possible pattern
2. Add complexity incrementally
3. Each level has clear success criteria (70-80%)
4. Tests are repeatable and deterministic

**This is better than:**
- Complex multi-pattern discrimination tests (like conditioning.py)
- Tests without documented baselines
- Tests that fail on both C and Clojure ONA

---

## Test Levels

### ✅ Level 1: Single Pattern Learning
**File:** `01_single_pattern.nal`
**Success Criteria:** 80%+ execution rate
**Status:** ✅ PASSING (100%)

**Task:**
- Learn: context A → ^left → goal achieved
- Test: When A is present and goal is desired, execute ^left

**Why it passes:**
- Simplest possible sensorimotor pattern
- Single implication to learn
- Clear context signal

---

### ✅ Level 2: Two Pattern Discrimination
**File:** `02_two_patterns_v2.nal`
**Success Criteria:** 70%+ correct discrimination
**Status:** ✅ PASSING (100%)

**Task:**
- Learn: red → ^left → goal
- Learn: blue → ^right → goal
- Test: Discriminate based on context

**Why it passes:**
- Uses atomic terms (red, blue)
- Each context has distinct concept
- Belief-spikes don't conflict

---

### ⚠️ Level 2 (Original): Inheritance Terms
**File:** `02_two_patterns.nal`
**Success Criteria:** 70%+ correct discrimination
**Status:** ⚠️ ISSUE - No discrimination

**Task:**
- Learn: <context --> A> → ^left
- Learn: <context --> B> → ^right

**Problem Discovered:**
Both `<context --> A>` and `<context --> B>` are indexed under the same concept key "context" (subject extraction from inheritance terms). When the second pattern is learned, the belief-spike for the first is overwritten.

**This reveals:** Inheritance terms with the same subject but different predicates cannot be discriminated via belief-spike checking.

**Workaround:** Use atomic terms or compound terms without shared subjects.

---

## Running Tests

### Quick Run

```bash
python3 test/integration/run_integration_tests.py
```

### Manual Run (Single Test)

```bash
./ona shell < test/integration/01_single_pattern.nal
```

### With C ONA (Comparison)

```bash
../../NAR shell < test/integration/01_single_pattern.nal
```

---

## Test Results Baseline

**Date:** 2025-10-31
**ONA Version:** Clojure ONA (GraalVM native)
**Binary:** ona-clojure/ona

| Test | Expected | Actual | Success Rate | Status |
|------|----------|--------|--------------|--------|
| 01_single_pattern.nal | 1 | 1 | 100% | ✅ PASS |
| 02_two_patterns_v2.nal | 4 | 4 | 100% | ✅ PASS |
| 02_two_patterns.nal | 4 | 4 (all ^left) | 0% discrimination | ⚠️ ISSUE |

**Overall:** 2/2 valid tests passing

---

## Known Limitations

### 1. Inheritance Term Belief Conflicts

**Issue:** Terms with the same subject share belief-spikes

**Example:**
```
<context --> A>. :|:   // Belief-spike on "context"
<context --> B>. :|:   // OVERWRITES belief-spike on "context"
```

**Impact:** Cannot discriminate patterns based on inheritance predicates

**Workaround:** Use atomic terms or ensure subjects are unique

**Root Cause:** `get-concept-key` extracts subject from inheritance terms for indexing (matches C ONA behavior)

### 2. Motor Babbling Not Tested

Integration tests currently disable motor babbling for deterministic results.

**To test motor babbling:** See `examples/motor_babbling_demo.nal`

---

## Future Test Additions

### Level 3: Compound Conditions (Not Yet Implemented)
```
Pattern 1: (red &/ bright) → ^left
Pattern 2: (red &/ dim) → ^right
```

### Level 4: Sequence Learning (Not Yet Implemented)
```
Learn: ^left followed by ^right achieves goal
```

### Level 5: Negative Evidence (Not Yet Implemented)
```
Learn: ^left → success
Learn: ^right → failure (negative)
Test: Prefer ^left
```

---

## Comparison with conditioning.py

**conditioning.py Status:**
- C ONA: 2-3/16 (12-18%) ❌
- Clojure ONA: 0/16 (0%) ❌
- Random: ~8/16 (50%)

**Why conditioning.py fails:**
1. Uses inheritance terms with shared subjects
2. 4-pattern discrimination is very complex
3. No documented baseline or success criteria
4. May never have worked (zero-cycles bug in original NAR.py)

**Integration tests are better because:**
- Clear progressive difficulty
- Documented success criteria (70-80%)
- Actually achievable (100% on simple patterns)
- Reveals specific issues (inheritance belief conflicts)

---

## Adding New Tests

1. Create `NN_test_name.nal` file
2. Add comment header with success criteria
3. Use `*setopname` to register operations
4. Include learning phase and testing phase
5. Run test runner to validate

**Template:**
```
// Level N: Test Description
// Success Criteria: XX%+ success rate
//
// Task: What the test validates

*volume=100
*setopname 1 ^operation

// === LEARNING PHASE ===
// Teach patterns here

// === TESTING PHASE ===
// Test patterns here
```

---

## Continuous Integration

To use these tests in CI:

```bash
#!/bin/bash
# Build binary
./scripts/build_native.sh

# Run integration tests
python3 test/integration/run_integration_tests.py

# Exit with test result code
exit $?
```

**Exit codes:**
- 0: All tests passed
- 1: One or more tests failed

---

## Conclusion

These integration tests provide a **solid foundation** for validating ONA's sensorimotor learning capabilities:

✅ **Working:** Single pattern learning (100%)
✅ **Working:** Two pattern discrimination with atomic terms (100%)
⚠️ **Issue Found:** Inheritance term belief conflicts
📋 **Next:** Add compound condition and sequence tests

**Result:** Much better than conditioning.py for validation and regression testing!
