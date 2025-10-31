#!/usr/bin/env python3
"""
Simple Procedural Learning Demo

Demonstrates what DOES work well in ONA:
- Learning a single sensorimotor sequence
- Reliably executing learned operations to achieve goals

This is based on basic_example_native.py but with clearer experimental structure.
"""

from NAR_working import NAR

def main():
    print("=" * 70)
    print("Simple Procedural Learning Demo")
    print("=" * 70)
    print("\nDemonstrating: sensor → ^action → goal")
    print("Then testing: sensor + goal! → system executes ^action")

    nar = NAR()

    # Register operation
    print("\n1. Register operation ^press_button")
    nar.add_input("*setopname 1 ^press_button")
    print("   ✓ Operation registered")

    # Training: Observe the sequence multiple times
    print("\n2. Training: Show that pressing button when light is on causes reward")
    print("   Sequence: light_on → ^press_button → reward")

    num_trials = 5
    for trial in range(num_trials):
        print(f"\n   Training trial {trial + 1}/{num_trials}")

        # Sensor input
        nar.add_input("light_on. :|:")
        nar.cycles(5)

        # Action (demonstrating)
        nar.add_input("^press_button. :|:")
        nar.cycles(5)

        # Outcome
        nar.add_input("reward. :|:")
        nar.cycles(10)

        print(f"      Sequence demonstrated: light_on → ^press_button → reward")

    print(f"\n   Training complete! ({num_trials} demonstrations)")

    # Query what was learned
    print("\n3. Query: What leads to reward?")
    result = nar.add_input("reward?")
    if result.get("answers"):
        print(f"   System knows: {result['answers']}")
    else:
        print("   (Implications stored internally, not directly queryable)")

    # Test: See if system can autonomously execute the operation
    print("\n4. Test: Present sensor + goal, does NAR execute action?")
    print("   Presenting: light_on + reward!")

    nar.cycles(5)  # Clear working memory a bit

    # Present sensor
    nar.add_input("light_on. :|:")
    nar.cycles(5)

    # Present goal
    result = nar.add_input("reward! :|:")
    nar.cycles(10)

    # Check execution
    executed = result.get("executions", [])

    print(f"\n   Executions: {executed}")

    if any("^press_button" in exe or "press_button" in exe for exe in executed):
        print("\n   ✓✓✓ SUCCESS!")
        print("   The system autonomously decided to press the button!")
        print("   It learned: <(light_on &/ ^press_button) =/> reward>")
    else:
        print("\n   ✗ System did not execute the expected action")
        print("   (May need more training or inference cycles)")

    # Show statistics
    print("\n5. Statistics")
    stats = nar.stats()
    print(f"   System time: {stats.get('currentTime')}")
    print(f"   Total concepts: {stats.get('total concepts')}")

    print("\n" + "=" * 70)
    print("Demo complete!")
    print("\nWhat this demonstrates:")
    print("- ONA learns temporal implications from observed sequences")
    print("- It can execute operations to achieve goals (procedural reasoning)")
    print("- Learning happens in just a few trials (sample efficient)")
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
