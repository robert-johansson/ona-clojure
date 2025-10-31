# Discrimination Learning Experiment - Findings

## Experimental Goal

Test if ONA can learn context-dependent action selection:
- **Context A** + goal → execute `^left`
- **Context B** + goal → execute `^right`

This tests the system's ability to maintain multiple competing implications to the same goal and select the appropriate one based on current context.

## Experimental Design

### Training Phase
Demonstrated temporal sequences:
```
Trial type 1: A → ^left → G
Trial type 2: B → ^right → G
```

Expected learning:
- `<(A &/ ^left) =/> G>` with truth value ~0.9
- `<(B &/ ^right) =/> G>` with truth value ~0.9

### Testing Phase
Present context + goal and observe action selection:
```
Test 1: A + G! → expect ^left
Test 2: B + G! → expect ^right
```

## Results

### What Was Successfully Learned

✅ **Both implications were learned** - confirmed by execution output:
- System can execute `^decide: (A &/ ^left) desire=0.75`
- System can execute `^decide: (B &/ ^right) desire=0.75`

### Observed Limitation

❌ **Context discrimination failed** - the system consistently chose ONE implication regardless of context:

**Test results:**
```
Context A (expected ^left): 0/3 or 3/3 (depending on which implication dominated)
Context B (expected ^right): 3/3 or 0/3 (opposite pattern)
Overall: 50% correct (chance level for binary choice)
```

**Pattern:** Whichever implication was trained LAST dominated all decisions, suggesting recency bias without proper precondition checking.

## Analysis

### What the Decision Mechanism Does

When presented with goal `G!`, the system:
1. ✅ Finds implications leading to G
2. ✅ Evaluates their expected outcomes
3. ❌ **Does NOT properly check** if precondition context (A vs B) is currently true
4. ❌ Selects based on priority/recency rather than context match

### Evidence

Output shows `^decide: (B &/ ^right) desire=0.75` even when context A is present with high confidence:
```
Input: A. :|: {1.0 0.95}  # Context A explicitly asserted
...
Input: G! :|:
Output: ^decide: (B &/ ^right) desire=0.75  # Wrong context!
```

## Attempted Solutions

Tried multiple approaches, all with same results:

1. **Higher context confidence** - Set context beliefs to {1.0 0.95}
   - Result: No improvement

2. **More inference cycles** - Increased cycles from 3 → 15 after context
   - Result: No improvement

3. **Context clearing** - Explicitly set opposite context to {0.0 0.90}
   - Result: No improvement

4. **Interleaved training** - Alternated A and B trials instead of blocking
   - Result: No improvement

5. **Different sensor names** - Used `sensor_left`/`sensor_right` instead of A/B
   - Result: No improvement

## Interpretation

This appears to be a **current limitation in ONA's decision-making mechanism**:

### From ONA Architecture (CLAUDE.md)

> Decision_Suggest(concept, goal, currentTime):
> 1. Examines concept->precondition_beliefs[opID] table
> 2. For each operation implication <A =/> goal>:
>    - **Checks if precondition A's belief is recent/strong**
>    - Applies deductive inference

**The issue:** The "check if precondition is recent/strong" step doesn't appear to properly discriminate between competing preconditions when multiple implications target the same goal.

## What DOES Work

✅ **Single sensorimotor sequence learning** works perfectly:

```python
# Training: sensor → action → outcome
light_on → ^press_button → reward

# Testing: sensor + goal
light_on + reward! → executes ^press_button ✓
```

See `simple_learning_demo.py` for working example.

## Research Value

### For ONA Development

This experiment identifies a specific area for potential improvement:
- **Enhanced precondition evaluation** in decision-making
- **Context-sensitive action selection** for competing implications
- Better handling of **multiple paths to same goal**

### For Users

**Current best practice:**
- Design tasks with single clear sensorimotor chains
- Avoid multiple competing implications to identical goals
- Use distinct goals for different contexts if needed

## Comparison to Classical AI

**What this would be in classical RL:**
- Discrimination learning / contextual bandits
- State-dependent policy: π(a|s) where s ∈ {A, B}
- Standard task for any RL system

**Current ONA status:**
- ✅ Learns individual associations quickly (sample efficient)
- ✅ Executes goal-driven actions
- ⚠️ Needs improvement for context-dependent selection

## Files

- `discrimination_learning.py` - Main experiment (A/B contexts)
- `discrimination_learning_v2.py` - Alternative with sensor contexts
- `simple_learning_demo.py` - Working example of what DOES work

## Conclusion

ONA successfully learns temporal implications and performs goal-driven reasoning, but the current decision mechanism needs enhancement for proper context discrimination when multiple implications compete for the same goal.

This is a valuable finding that can guide future development of the system's decision-making capabilities.
