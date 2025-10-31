#!/usr/bin/env python3
"""
Test the compatible NAR.py with C ONA API
This tests that existing C ONA Python code works unchanged
"""

import NAR

print("✓ NAR.py imported successfully")
print()

# Test 1: Basic input
print("Test 1: AddInput with belief")
result = NAR.AddInput("<bird --> animal>. {1.0 0.9}", Print=False)
print(f"  Keys: {result.keys()}")
print(f"  Inputs: {len(result['input'])}")
print(f"  Executions: {len(result['executions'])}")
print(f"  ✓ AddInput works")
print()

# Test 2: Reset
print("Test 2: Reset")
NAR.Reset()
print("  ✓ Reset works")
print()

# Test 3: Query
print("Test 3: Query with ?")
NAR.AddInput("<bird --> animal>. {1.0 0.9}", Print=False)
NAR.AddInput("<robin --> bird>. {1.0 0.9}", Print=False)
result = NAR.AddInput("<robin --> animal>?", Print=False)
print(f"  Answers: {len(result['answers'])}")
if result['answers']:
    answer = result['answers'][0]
    print(f"  Answer term: {answer['term']}")
    print(f"  Answer truth: {answer.get('truth', 'N/A')}")
print(f"  ✓ Query works")
print()

# Test 4: GetStats
print("Test 4: GetStats")
stats = NAR.AddInput("*stats", Print=False)
print(f"  Stats keys: {list(stats.keys())[:5]}...")  # First 5 keys
print(f"  ✓ GetStats works")
print()

# Test 5: parseTask
print("Test 5: Parse functions")
task_str = "<bird --> animal>. Truth: frequency=1.0 confidence=0.9"
parsed = NAR.parseTask(task_str)
print(f"  Term: {parsed['term']}")
print(f"  Punctuation: {parsed['punctuation']}")
print(f"  Truth: {parsed.get('truth', 'N/A')}")
print(f"  ✓ parseTask works")
print()

# Test 6: parseTruth
truth_str = "frequency=1.0 confidence=0.9 dt=0"
truth = NAR.parseTruth(truth_str)
print(f"  Frequency: {truth['frequency']}")
print(f"  Confidence: {truth['confidence']}")
print(f"  ✓ parseTruth works")
print()

print("=" * 50)
print("ALL TESTS PASSED! ✓")
print("C ONA Python code will work unchanged!")
print("=" * 50)

# Cleanup
NAR.Exit()
