#!/usr/bin/env python3
"""
Basic example using native ONA binary

Demonstrates:
1. Register an operation
2. Observe: A happens, then execute ^op, then B happens
3. NAR learns: <(A &/ ^op) =/> B>
4. Give goal B, NAR should execute ^op when it sees A

WORKS WITH NATIVE BINARY ONLY (188x faster startup than JVM!)
"""

from NAR_working import NAR

def main():
    print("Basic ONA Native Binary Example")
    print("=" * 60)

    # Create NAR instance
    nar = NAR()

    # 1. Register operation
    print("\n1. Register operation ^op")
    nar.add_input("*setopname 1 ^op")

    # 2. Training: Show NAR that A -> ^op -> B sequence
    print("\n2. Training: A :|:, ^op :|:, B :|:")
    print("   Teaching NAR: when you see A, if you do ^op, you get B")

    for trial in range(3):
        print(f"\n   Training trial {trial + 1}:")
        nar.add_input("A. :|:")
        nar.cycles(5)
        nar.add_input("^op. :|:")
        nar.cycles(5)
        nar.add_input("B. :|:")
        nar.cycles(10)
        print(f"   Trial {trial + 1} complete")

    # 3. Check what was learned
    print("\n3. Query: What leads to B?")
    result = nar.add_input("B?")
    print(f"   Answer: {result['answers']}")
    print("   NAR should have learned: <(A &/ ^op) =/> B>")

    # 4. Test: Give goal B when A is present
    print("\n4. Test: Give A and goal B!")
    print("   NAR should execute ^op to achieve B")

    nar.add_input("A. :|:")
    nar.cycles(5)

    result = nar.add_input("B! :|:")
    nar.cycles(10)

    # Check if operation was executed
    if result.get("executions"):
        print("\n   ✓ SUCCESS! NAR executed:")
        for exe in result["executions"]:
            print(f"     {exe}")
    else:
        print("\n   ✗ No operations executed")
        print("   (NAR may need more training or inference steps)")

    # Show stats
    print("\n5. Final statistics:")
    stats = nar.stats()
    print(f"   Current time: {stats.get('currentTime')}")
    print(f"   Total concepts: {stats.get('total concepts')}")

    print("\n" + "=" * 60)
    print("Example complete!")

    nar.terminate()

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nInterrupted")
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
