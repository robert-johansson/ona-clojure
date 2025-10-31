#!/usr/bin/env python3
"""
Discrimination Learning Experiment V2

Different approach: Use different goals for each context to avoid competition.

Training:
- Context sensor_left + goal_left! → ^left → reward
- Context sensor_right + goal_right! → ^right → reward

This tests if the system can learn two independent stimulus-response chains.
"""

from NAR_working import NAR

def main():
    print("=" * 70)
    print("Discrimination Learning Experiment V2")
    print("=" * 70)
    print("\nTraining NAR with distinct goals for each context:")
    print("  sensor_left + goal_left! → ^left → reward")
    print("  sensor_right + goal_right! → ^right → reward")

    # Create NAR instance
    nar = NAR()

    # Register operations
    print("\n1. Registering operations")
    nar.add_input("*setopname 1 ^left")
    nar.add_input("*setopname 2 ^right")
    print("   ✓ ^left and ^right registered")

    # Training phase
    print("\n2. Training Phase (5 rounds each)")

    # Train sensor_left → ^left → reward → goal_left
    print("\n   Training: sensor_left → ^left → reward")
    for trial in range(5):
        nar.add_input("sensor_left. :|:")
        nar.cycles(5)
        nar.add_input("^left. :|:")
        nar.cycles(5)
        nar.add_input("reward. :|:")
        nar.cycles(10)
        print(f"      Trial {trial + 1}/5")

    print("\n   Training: sensor_right → ^right → reward")
    for trial in range(5):
        nar.add_input("sensor_right. :|:")
        nar.cycles(5)
        nar.add_input("^right. :|:")
        nar.cycles(5)
        nar.add_input("reward. :|:")
        nar.cycles(10)
        print(f"      Trial {trial + 1}/5")

    print(f"\n   Training complete! (10 total demonstrations)")

    # Test phase
    print("\n3. Testing Phase")
    print("   Testing if NAR executes correct actions for each sensor...")

    test_results = {"left": [], "right": []}

    for test_trial in range(6):
        side = "left" if test_trial % 2 == 0 else "right"
        expected_op = f"^{side}"

        print(f"\n   Test {test_trial + 1}: sensor_{side}, expect {expected_op}")

        # Present sensor
        nar.add_input(f"sensor_{side}. :|:")
        nar.cycles(10)

        # Present goal for reward
        result = nar.add_input("reward! :|:")
        nar.cycles(15)

        # Check execution
        executed = result.get("executions", [])

        if executed:
            print(f"      Executed: {executed}")
            if any(expected_op in exe for exe in executed):
                print(f"      ✓ SUCCESS! Chose {expected_op}")
                test_results[side].append(True)
            else:
                print(f"      ✗ Wrong action (expected {expected_op})")
                test_results[side].append(False)
        else:
            print(f"      · No action")
            test_results[side].append(False)

        nar.cycles(5)

    # Summary
    print("\n" + "=" * 70)
    print("RESULTS")
    print("=" * 70)

    left_correct = sum(test_results["left"])
    right_correct = sum(test_results["right"])
    total = len(test_results["left"]) + len(test_results["right"])
    correct = left_correct + right_correct

    print(f"\nsensor_left → ^left:  {left_correct}/{len(test_results['left'])} correct")
    print(f"sensor_right → ^right: {right_correct}/{len(test_results['right'])} correct")
    print(f"\nOverall: {correct}/{total} correct ({100*correct/total:.1f}%)")

    if correct >= total * 0.8:
        print("\n✓✓✓ EXCELLENT! System learned discrimination!")
    elif correct >= total * 0.5:
        print("\n✓ GOOD! System shows learning")
    else:
        print("\n· Needs more training")

    stats = nar.stats()
    print(f"\nSystem time: {stats.get('currentTime')}, Concepts: {stats.get('total concepts')}")
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
