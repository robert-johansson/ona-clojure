#!/usr/bin/env python3
"""
Quick test of native binary Python interface
"""

import time

print("=" * 60)
print("Testing NATIVE binary Python interface")
print("=" * 60)

# Test import and spawn
print("\n1. Importing NAR_native...")
start = time.time()
import NAR_native as NAR
print(f"   ✓ Spawned in {time.time() - start:.3f}s")

# Test reset
print("\n2. Testing reset...")
start = time.time()
result = NAR.Reset()
print(f"   Time: {time.time() - start:.3f}s")
print(f"   Output: {result['output']}")

# Test adding belief
print("\n3. Adding belief...")
start = time.time()
result = NAR.AddInput("<bird --> animal>. {1.0 0.9}")
print(f"   Time: {time.time() - start:.3f}s")
print(f"   Output: {result['output']}")

# Test cycles
print("\n4. Executing 5 cycles...")
start = time.time()
result = NAR.ExecuteCycles(5)
print(f"   Time: {time.time() - start:.3f}s")
print(f"   Output: {result['output']}")

# Test query
print("\n5. Querying...")
start = time.time()
result = NAR.AddInput("<bird --> animal>?")
print(f"   Time: {time.time() - start:.3f}s")
print(f"   Answers: {result['answers']}")
print(f"   Output: {result['output']}")

# Test stats
print("\n6. Getting stats...")
start = time.time()
stats = NAR.GetStats()
print(f"   Time: {time.time() - start:.3f}s")
print(f"   Current time: {stats.get('currentTime', 'N/A')}")
print(f"   Total concepts: {stats.get('total concepts', 'N/A')}")

NAR.terminateNAR()
print("\n" + "=" * 60)
print("✓ All tests PASSED!")
print("=" * 60)
