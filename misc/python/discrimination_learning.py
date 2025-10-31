#!/usr/bin/env python3
"""
Discrimination Learning Experiment

Demonstrates context-dependent action selection through reinforcement learning.

Training:
- Context A + G! → ^left → G (reinforcement) → learns <(A &/ ^left) =/> G>
- Context B + G! → ^right → G (reinforcement) → learns <(B &/ ^right) =/> G>

Testing:
- Context A + G! → system should choose ^left (not random)
- Context B + G! → system should choose ^right

This shows the system learns discriminative stimulus-response-outcome associations.
"""

from NAR_working import NAR
import random

def main():
    print("=" * 70)
    print("Discrimination Learning Experiment")
    print("=" * 70)
    print("\nTraining NAR to select different actions based on context:")
    print("  A + G! → ^left → G")
    print("  B + G! → ^right → G")

    # Create NAR instance
    nar = NAR()

    # Register operations
    print("\n1. Registering operations")
    nar.add_input("*setopname 1 ^left")
    nar.add_input("*setopname 2 ^right")
    print("   ✓ ^left and ^right registered")

    # Training phase - interleaved learning
    print("\n2. Training Phase (Interleaved)")
    print("   Demonstrating sequences in alternating order:")
    print("   A → ^left → G  (teaching <(A &/ ^left) =/> G>)")
    print("   B → ^right → G (teaching <(B &/ ^right) =/> G>)")

    num_training_rounds = 5

    for round_num in range(num_training_rounds):
        # Train A → ^left → G
        nar.add_input("A. :|:")
        nar.cycles(5)
        nar.add_input("^left. :|:")
        nar.cycles(5)
        nar.add_input("G. :|:")
        nar.cycles(10)

        # Consolidation period
        nar.cycles(5)

        # Train B → ^right → G
        nar.add_input("B. :|:")
        nar.cycles(5)
        nar.add_input("^right. :|:")
        nar.cycles(5)
        nar.add_input("G. :|:")
        nar.cycles(10)

        # Consolidation period
        nar.cycles(5)

        print(f"   Round {round_num + 1}/{num_training_rounds}: A→^left and B→^right")

    print(f"\n   Training complete! ({num_training_rounds * 2} total demonstrations)")

    # Query what was learned
    print("\n3. Query learned implications")
    result = nar.add_input("G?")
    if result.get("answers"):
        print(f"   Query G?: {result['answers']}")

    # Check concepts to see implications
    print("\n   Checking system state...")
    stats = nar.stats()
    print(f"   Total concepts: {stats.get('total concepts')}")

    # Try querying the specific implications
    result_a = nar.add_input("<<(A &/ ^left) =/> G>>?")
    result_b = nar.add_input("<<(B &/ ^right) =/> G>>?")
    if result_a.get("answers"):
        print(f"   A→left→G implication: {result_a['answers']}")
    if result_b.get("answers"):
        print(f"   B→right→G implication: {result_b['answers']}")

    # Test phase
    print("\n4. Testing Phase (deterministic action selection)")
    print("   Testing if NAR has learned context-dependent actions...")

    test_results = {"A": [], "B": []}

    for test_trial in range(6):
        context = "A" if test_trial % 2 == 0 else "B"
        expected_op = "^left" if context == "A" else "^right"

        print(f"\n   Test {test_trial + 1}: Context={context}, Expected={expected_op}")

        # Clear previous context (present opposite context as false to reset)
        opposite = "B" if context == "A" else "A"
        nar.add_input(f"{opposite}. :|: {{0.0 0.90}}")
        nar.cycles(5)

        # Present current context with high confidence
        nar.add_input(f"{context}. :|: {{1.0 0.95}}")
        nar.cycles(10)  # More time for context to register

        # Present goal and collect ALL output for debugging
        result = nar.add_input("G! :|:")
        nar.cycles(15)  # More inference time

        # Check what was executed
        executed = result.get("executions", [])

        # Show raw output for first few tests
        if test_trial < 2:
            print(f"      Raw output: {result.get('output', [])[:5]}")

        if executed:
            print(f"      Executed: {executed}")
            # Check if correct operation was chosen
            if any(expected_op in exe for exe in executed):
                print(f"      ✓ SUCCESS! Chose correct action {expected_op}")
                test_results[context].append(True)
            else:
                print(f"      ✗ Wrong action (expected {expected_op})")
                test_results[context].append(False)
        else:
            print(f"      · No action executed (may need more training)")
            test_results[context].append(False)

        # Small delay between tests
        nar.cycles(5)

    # Summary
    print("\n" + "=" * 70)
    print("RESULTS")
    print("=" * 70)

    a_correct = sum(test_results["A"])
    b_correct = sum(test_results["B"])
    total_tests = len(test_results["A"]) + len(test_results["B"])
    total_correct = a_correct + b_correct

    print(f"\nContext A (should choose ^left): {a_correct}/{len(test_results['A'])} correct")
    print(f"Context B (should choose ^right): {b_correct}/{len(test_results['B'])} correct")
    print(f"\nOverall: {total_correct}/{total_tests} correct ({100*total_correct/total_tests:.1f}%)")

    if total_correct >= total_tests * 0.8:
        print("\n✓✓✓ EXCELLENT! System learned discrimination!")
    elif total_correct >= total_tests * 0.5:
        print("\n✓ GOOD! System shows learning (may need more training)")
    else:
        print("\n· Partial learning (needs more training trials)")

    # Show final statistics
    print("\n5. Final System Statistics")
    stats = nar.stats()
    print(f"   Current time: {stats.get('currentTime')}")
    print(f"   Total concepts: {stats.get('total concepts')}")

    print("\n" + "=" * 70)
    print("Experiment complete!")
    print("=" * 70)

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
