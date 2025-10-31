#!/usr/bin/env python3
"""
Simple example demonstrating Clojure ONA Python interface

This example shows:
1. Registering operations
2. Adding sensory observations
3. Giving goals
4. Learning temporal implications
5. Decision making

Scenario: NAR learns that executing ^toggle leads to seeing 'on'
"""

import sys
import time
from NAR import AddInput, Reset, GetStats, Exit

def print_section(title):
    """Print section header"""
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}\n")

def main():
    print_section("Clojure ONA Python Interface Example")

    # 1. Setup - register operation
    print_section("Step 1: Register Operation")
    print("Registering operation: ^toggle")
    AddInput("*setopname 1 ^toggle")

    # 2. Show initial state
    print_section("Step 2: Initial Observation")
    print("Observe: off")
    AddInput("off. :|:")
    AddInput("5")  # 5 inference steps

    # 3. Execute operation and observe outcome
    print_section("Step 3: Execute Operation and Observe Outcome")
    print("Execute: ^toggle")
    AddInput("^toggle. :|:")
    AddInput("5")

    print("\nObserve result: on")
    AddInput("on. :|:")
    AddInput("10")  # More steps to form sequence and implication

    # 4. Show what was learned
    print_section("Step 4: What Did NAR Learn?")
    print("Query: What leads to 'on'?")
    result = AddInput("on?")
    print("\nLearned implications should include: <^toggle =/> on>")

    # 5. Give a goal - NAR should execute ^toggle to achieve it
    print_section("Step 5: Give Goal - NAR Should Act")
    print("Goal: on!")
    result = AddInput("on! :|:")
    AddInput("10")  # Steps for decision making

    if result.get("executions"):
        print("\n✓ NAR executed operations:")
        for exe in result["executions"]:
            print(f"  - {exe['operator']} (desire={exe.get('desire', 0):.2f})")
    else:
        print("\n✗ No operations executed (may need more training)")

    # 6. Try with motor babbling
    print_section("Step 6: Try with Motor Babbling")
    Reset()
    print("Reset NAR and enable motor babbling")
    AddInput("*setopname 1 ^left")
    AddInput("*setopname 2 ^right")
    AddInput("*motorbabbling=0.3")  # 30% chance
    AddInput("*seed=42")  # Reproducible

    print("\nRun 10 cycles - should see random exploration:")
    for i in range(10):
        result = AddInput(f"5")  # 5 steps per cycle
        if result.get("executions"):
            for exe in result["executions"]:
                print(f"  Cycle {i+1}: Babbling -> {exe['operator']}")

    # 7. Show statistics
    print_section("Step 7: System Statistics")
    stats = AddInput("*stats")
    print("\nKey statistics:")
    for key in ["concepts", "beliefs", "goals", "operations"]:
        if key in stats:
            print(f"  {key}: {stats[key]}")

    print_section("Example Complete!")
    print("Python interface working correctly!")

    # Cleanup
    Exit()

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
        Exit()
        sys.exit(0)
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
        Exit()
        sys.exit(1)
