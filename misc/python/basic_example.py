#!/usr/bin/env python3
"""
Basic example of Clojure ONA Python interface

Demonstrates the absolute basics:
1. Register an operation
2. Observe: A happens, then execute ^op, then B happens
3. NAR learns: <(A &/ ^op) =/> B>
4. Give goal B, NAR should execute ^op when it sees A
"""

import sys
import os

# Add parent directory to path to import NAR
sys.path.insert(0, os.path.dirname(__file__))

from NAR import AddInput, Reset, Exit

def main():
    print("Basic Clojure ONA Python Interface Example")
    print("=" * 60)

    # 1. Register operation
    print("\n1. Register operation ^op")
    AddInput("*setopname 1 ^op")

    # 2. Training: Show NAR that A -> ^op -> B sequence
    print("\n2. Training: A :|:, ^op :|:, B :|:")
    print("   Teaching NAR: when you see A, if you do ^op, you get B")

    for trial in range(3):
        print(f"\n   Training trial {trial + 1}:")
        AddInput("A. :|:")
        AddInput("5")  # Let NAR process
        AddInput("^op. :|:")
        AddInput("5")
        AddInput("B. :|:")
        AddInput("10")  # Let NAR form sequences and implications
        print(f"   Trial {trial + 1} complete")

    # 3. Check what was learned
    print("\n3. Query: What leads to B?")
    result = AddInput("B?")
    print("   NAR should have learned: <(A &/ ^op) =/> B>")

    # 4. Test: Give goal B when A is present
    print("\n4. Test: Give A and goal B!")
    print("   NAR should execute ^op to achieve B")

    AddInput("A. :|:")
    AddInput("5")

    result = AddInput("B! :|:")
    AddInput("10")

    # Check if operation was executed
    if result.get("executions"):
        print("\n   ✓ SUCCESS! NAR executed:")
        for exe in result["executions"]:
            print(f"     {exe['operator']} (desire={exe.get('desire', 0):.2f})")
    else:
        print("\n   ✗ No operations executed")
        print("   (NAR may need more training or inference steps)")

    print("\n" + "=" * 60)
    print("Example complete!")

    Exit()

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nInterrupted")
        Exit()
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
        Exit()
        sys.exit(1)
