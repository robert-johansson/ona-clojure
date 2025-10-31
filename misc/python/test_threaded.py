#!/usr/bin/env python3
"""Test threaded native interface"""

import time

print("Testing threaded native interface...")
print("=" * 60)

start = time.time()
import NAR_native_threaded as NAR
print(f"✓ Imported and spawned in {time.time() - start:.3f}s\n")

# Test 1
print("1. Reset")
result = NAR.Reset()
print(f"   Output: {result['output']}\n")

# Test 2
print("2. Add belief")
result = NAR.AddInput("<bird --> animal>. {1.0 0.9}")
print(f"   Output: {result['output']}\n")

# Test 3
print("3. Execute 5 cycles")
result = NAR.ExecuteCycles(5)
print(f"   Output: {result['output']}\n")

# Test 4
print("4. Query")
result = NAR.AddInput("<bird --> animal>?")
print(f"   Answers: {result['answers']}")
print(f"   Output: {result['output']}\n")

# Test 5
print("5. Stats")
stats = NAR.GetStats()
print(f"   Current time: {stats.get('currentTime', 'N/A')}")
print(f"   Total concepts: {stats.get('total concepts', 'N/A')}\n")

NAR.terminateNAR()

print("=" * 60)
print("✓ ALL TESTS PASSED!")
print("=" * 60)
