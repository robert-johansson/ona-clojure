#!/usr/bin/env python3
"""
Test comparing native binary vs JVM for Python interface
"""

import time
import sys

def test_native():
    """Test native binary version"""
    print("=" * 60)
    print("Testing NATIVE binary version")
    print("=" * 60)

    start = time.time()
    import NAR_native as NAR
    init_time = time.time() - start
    print(f"✓ Import and spawn: {init_time:.3f}s")

    # Test 1: Reset
    print("\nTest 1: Reset")
    start = time.time()
    result = NAR.Reset()
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Output lines: {len(result['output'])}")

    # Test 2: Add belief
    print("\nTest 2: Add belief")
    start = time.time()
    result = NAR.AddInput("<bird --> animal>. {1.0 0.9}")
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Output lines: {len(result['output'])}")

    # Test 3: Execute cycles
    print("\nTest 3: Execute 10 cycles")
    start = time.time()
    result = NAR.ExecuteCycles(10)
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Output: {result['output']}")

    # Test 4: Query
    print("\nTest 4: Query")
    start = time.time()
    result = NAR.AddInput("<bird --> animal>?")
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Answers: {result['answers']}")

    # Test 5: Stats
    print("\nTest 5: Get stats")
    start = time.time()
    stats = NAR.GetStats()
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Stats keys: {list(stats.keys())}")
    print(f"  Current time: {stats.get('currentTime', 'N/A')}")

    NAR.terminateNAR()
    print("\n✓ All tests completed successfully!")
    return True

def test_jvm():
    """Test JVM version"""
    print("\n" + "=" * 60)
    print("Testing JVM version")
    print("=" * 60)

    start = time.time()
    import NAR
    init_time = time.time() - start
    print(f"✓ Import and spawn: {init_time:.3f}s")

    # Test 1: Reset
    print("\nTest 1: Reset")
    start = time.time()
    result = NAR.Reset()
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Output lines: {len(result['output'])}")

    # Test 2: Add belief
    print("\nTest 2: Add belief")
    start = time.time()
    result = NAR.AddInput("<bird --> animal>. {1.0 0.9}")
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Output lines: {len(result['output'])}")

    # Test 3: Execute cycles
    print("\nTest 3: Execute 10 cycles")
    start = time.time()
    result = NAR.ExecuteCycles(10)
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Output: {result['output']}")

    # Test 4: Query
    print("\nTest 4: Query")
    start = time.time()
    result = NAR.AddInput("<bird --> animal>?")
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Answers: {result['answers']}")

    # Test 5: Stats
    print("\nTest 5: Get stats")
    start = time.time()
    stats = NAR.GetStats()
    print(f"  Time: {time.time() - start:.3f}s")
    print(f"  Stats keys: {list(stats.keys())}")
    print(f"  Current time: {stats.get('currentTime', 'N/A')}")

    NAR.terminateNAR()
    print("\n✓ All tests completed successfully!")
    return True

if __name__ == "__main__":
    # Test native version first
    try:
        native_success = test_native()
    except Exception as e:
        print(f"\n✗ Native version failed: {e}")
        import traceback
        traceback.print_exc()
        native_success = False

    # Test JVM version
    if "--jvm" in sys.argv:
        try:
            jvm_success = test_jvm()
        except Exception as e:
            print(f"\n✗ JVM version failed: {e}")
            import traceback
            traceback.print_exc()
            jvm_success = False

    # Summary
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Native: {'✓ PASS' if native_success else '✗ FAIL'}")
    if "--jvm" in sys.argv:
        print(f"JVM:    {'✓ PASS' if jvm_success else '✗ FAIL'}")
