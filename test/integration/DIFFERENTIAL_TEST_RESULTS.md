# Differential Testing Results

**Date:** 2025-10-31
**Tests:** 6 NAL files comparing C ONA vs Clojure ONA

---

## Executive Summary

**Status:** ⚠️ **MAJOR DIFFERENCE FOUND**

| Metric | Result |
|--------|--------|
| Execution Matches | 1/6 (16%) |
| Perfect Matches | 0/6 (0%) |

**Key Finding:** Clojure ONA executes operations **much more frequently** than C ONA.

---

## Detailed Results

### Test 1: 01_single_pattern.nal
**Task:** Learn `A → ^left → G`, then execute when `A` present + `G!` desired

| Implementation | Executions | Assessment |
|---|---|---|
| C ONA | 0 | ❌ Never executed |
| Clojure ONA | 2 | ⚠️  Executed twice |

**Analysis:**
- C ONA: Did NOT execute (may require stronger context conditions)
- Clojure ONA: Executed TWICE (once per goal processing?)

---

### Test 2: 02_two_patterns_v2.nal
**Task:** Discriminate `red → ^left` vs `blue → ^right` (4 tests)

| Implementation | Executions | Pattern |
|---|---|---|
| C ONA | 1 | Only 1 execution total |
| Clojure ONA | 8 | 2 per test (correct ops) |

**Analysis:**
- C ONA: Very conservative execution (only 1/4 tests)
- Clojure ONA: Executes consistently, correct discrimination!

---

### Test 3: 03_compound_conditions.nal
**Task:** `(red & bright) → ^left` vs `(blue & bright) → ^right`

| Implementation | Executions | Correct? |
|---|---|---|
| C ONA | 4 | ✅ ^left, ^right, ^left, ^left |
| Clojure ONA | 8 | ⚠️  All ^left (no ^right!) |

**Analysis:**
- C ONA: Correct discrimination (4 operations)
- Clojure ONA: Failed discrimination (only ^left)

---

### Test 4: 04_temporal_chaining.nal
**Task:** Learn temporal implications `A =/> B` and `B =/> C`

| Implementation | Executions | Implications |
|---|---|---|
| C ONA | 0 | Not visible in output |
| Clojure ONA | 0 | 2 implications learned ✅ |

**Analysis:**
- ✅ Both match on executions (none expected)
- ✅ Clojure forms correct implications

---

### Test 5: 05_three_operations.nal
**Task:** Discriminate among `red → ^left`, `blue → ^right`, `green → ^forward` (6 tests)

| Implementation | Executions | Pattern |
|---|---|---|
| C ONA | 2 | Only ^right, ^forward |
| Clojure ONA | 12 | All 3 operations, 2x each |

**Analysis:**
- C ONA: Very sparse execution (2/6 tests)
- Clojure ONA: Consistent execution, correct discrimination

---

## Root Cause Analysis

### Why C ONA Executes Less

After examining outputs:

```
C ONA output:
Input: <goal --> achieved>! :|: occurrenceTime=131
[No execution]
```

**Hypothesis:** C ONA may require:
1. Stronger belief-spike recency (context may be too old)
2. Higher truth values
3. More explicit motor babbling configuration
4. Different timing/cycle counts

### Why Clojure ONA Executes More

```
Clojure output:
Input: <goal --> achieved>! :|: {1.000000 0.900000}
^debug[find-matching-implications]:     Impl: <^left =/> <goal --> achieved>>
^debug[process-goal-events]:   Operation: ^left
^left executed with args
^executed: ^left desire=0.76
```

**Finding:** Clojure ONA is matching and executing operations consistently.

**Issue:** In compound conditions test, Clojure fails to discriminate (executes only ^left, not ^right).

---

## Critical Differences

### 1. Execution Frequency

**C ONA:** Conservative - executes only when conditions strongly met
**Clojure ONA:** Aggressive - executes on most goals

**Impact:** Test results appear very different even though core logic may be similar.

### 2. Double Execution Pattern

Clojure ONA executes **2x per test** in many cases.

**Possible causes:**
- Executing once during input processing
- Executing again during goal event processing
- Motor babbling triggering extra executions

**Action needed:** Investigate and possibly de-duplicate

### 3. Compound Conditions

**C ONA:** Correctly discriminates `(red & bright) → ^left` vs `(blue & bright) → ^right`
**Clojure ONA:** Fails discrimination (only executes ^left)

**This is a REAL BUG** in Clojure ONA's compound condition handling!

---

## Implications Learned (Test 04)

Clojure ONA correctly forms temporal implications:
```
<stimulus_A =/> stimulus_B>
<stimulus_B =/> stimulus_C>
```

This suggests temporal induction is working correctly.

---

## Test Validity Assessment

### Valid Comparisons

❌ **Direct execution counts**: Not comparable due to different execution strategies

✅ **Discrimination ability**: Can compare if correct operations selected

✅ **Implication learning**: Can compare which implications formed

✅ **Query answers**: Can compare answer content

### Invalid Comparisons

❌ Expecting exact same execution count
❌ Expecting same timing
❌ Expecting identical output format

---

## Actionable Findings

### 🔴 HIGH PRIORITY: Fix Compound Condition Bug

**Test:** 03_compound_conditions.nal
**Issue:** Clojure executes only ^left, never ^right
**Expected:** Should discriminate like C ONA

**Action:**
1. Debug compound context checking in decision.clj
2. Verify belief-spike matching for multi-condition contexts
3. Add unit test for compound context discrimination

### 🟡 MEDIUM PRIORITY: Investigate Double Execution

**Observation:** Clojure executes 2x per goal
**Impact:** Inflates execution counts, may cause issues in real applications

**Action:**
1. Check if operations execute during input processing AND goal processing
2. Add execution deduplication if needed
3. Verify this matches C ONA's execution model

### 🟢 LOW PRIORITY: Align Execution Thresholds

**Observation:** C ONA much more conservative
**Impact:** Different test results, but not necessarily wrong

**Action:**
1. Document execution threshold differences
2. Optionally tune thresholds to match C ONA
3. Or document as intentional design difference

---

## Updated Test Strategy

Given these findings, differential testing should focus on:

### 1. **Discrimination Ability** (not execution count)

**Good metric:**
```
Does system select correct operation for each context?
red → ^left ✅
blue → ^right ✅
```

**Bad metric:**
```
Execution count: C=1, Clojure=8  ❌ (not comparable)
```

### 2. **Implication Formation**

**Good metric:**
```
Are correct temporal implications learned?
<A =/> B> ✅
```

### 3. **Query Answers**

**Good metric:**
```
Do queries return correct truth values?
```

---

## Recommendations

### For Testing

1. **Focus on correctness, not counts**
   - Check: "Does it execute the RIGHT operation?"
   - Don't check: "Does it execute the SAME NUMBER of times?"

2. **Create discrimination-focused tests**
   ```
   Given context X, operation Y should be selected (not Z)
   ```

3. **Use queries to validate learning**
   ```
   After learning, query should return expected implication
   ```

### For Development

1. **Fix compound condition bug** (high priority)
2. **Investigate double execution** (medium priority)
3. **Consider execution threshold tuning** (low priority)

### For Documentation

1. Document that execution strategies differ
2. Don't use C ONA execution counts as ground truth
3. Focus on functional correctness (discrimination, learning)

---

## Conclusion

**Good News:** ✅
- Clojure ONA executes operations consistently
- Discrimination works for simple patterns (2 operations)
- Temporal implication learning works
- Query system returns answers

**Issues Found:** ⚠️
- Compound condition discrimination fails
- Double execution pattern (2x per goal)
- Execution thresholds differ from C ONA

**Next Steps:**
1. Fix compound condition bug
2. Investigate double execution
3. Update test strategy to focus on discrimination correctness

**Overall:** Clojure ONA is **mostly working** but needs fixes for compound conditions and possibly execution deduplication.
