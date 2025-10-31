#!/usr/bin/env python3
"""
Pong-like example using Clojure ONA Python interface

A simple game where NAR controls a paddle to hit a ball.
Demonstrates:
- Sensorimotor learning
- Real-time decision making
- Temporal implication learning
- Goal-directed behavior

The ball moves left/right, and NAR learns to move the paddle to intercept it.
"""

import sys
import time
import random
from NAR import AddInput, Reset, Exit

class PongGame:
    """Simple pong game state"""

    def __init__(self):
        self.ball_pos = 5      # Ball position (0-10)
        self.ball_dir = 1      # Ball direction: 1=right, -1=left
        self.paddle_pos = 5    # Paddle position (0-10)
        self.score = 0
        self.misses = 0

    def reset(self):
        """Reset game state"""
        self.ball_pos = 5
        self.ball_dir = random.choice([-1, 1])
        self.paddle_pos = 5

    def update_ball(self):
        """Update ball position"""
        self.ball_pos += self.ball_dir

        # Bounce off walls
        if self.ball_pos <= 0 or self.ball_pos >= 10:
            self.ball_dir *= -1
            self.ball_pos = max(0, min(10, self.ball_pos))

    def execute_action(self, action):
        """Execute paddle action"""
        if action == "^left":
            self.paddle_pos = max(0, self.paddle_pos - 1)
            return True
        elif action == "^right":
            self.paddle_pos = min(10, self.paddle_pos + 1)
            return True
        return False

    def check_hit(self):
        """Check if paddle hit ball"""
        distance = abs(self.ball_pos - self.paddle_pos)
        if distance <= 1:  # Hit within 1 unit
            self.score += 1
            return True
        return False

    def get_state(self):
        """Get current sensory state as Narsese"""
        states = []

        # Ball position
        if self.ball_pos < 4:
            states.append("ball_left")
        elif self.ball_pos > 6:
            states.append("ball_right")
        else:
            states.append("ball_center")

        # Ball direction
        if self.ball_dir > 0:
            states.append("ball_moving_right")
        else:
            states.append("ball_moving_left")

        # Paddle position
        if self.paddle_pos < 4:
            states.append("paddle_left")
        elif self.paddle_pos > 6:
            states.append("paddle_right")
        else:
            states.append("paddle_center")

        return states

    def print_state(self):
        """Print visual representation"""
        line = ['-'] * 11
        line[self.ball_pos] = 'O'
        line[self.paddle_pos] = '|'

        # If ball and paddle overlap, show hit
        if self.ball_pos == self.paddle_pos:
            line[self.ball_pos] = 'X'

        print(''.join(line) + f"  Score: {self.score}  Misses: {self.misses}")


def run_game(num_steps=50, enable_babbling=True, verbose=True):
    """Run pong game with NAR learning

    Args:
        num_steps: Number of game steps to run
        enable_babbling: Whether to enable motor babbling for exploration
        verbose: Whether to print detailed output
    """
    game = PongGame()

    print("=" * 70)
    print("  PONG GAME - NAR Learning to Hit Ball")
    print("=" * 70)

    # Setup
    print("\n[Setup]")
    AddInput("*reset", Print=False)
    AddInput("*setopname 1 ^left", Print=verbose)
    AddInput("*setopname 2 ^right", Print=verbose)

    if enable_babbling:
        AddInput("*motorbabbling=0.2", Print=verbose)  # 20% exploration
        AddInput("*seed=42", Print=verbose)  # Reproducible

    print(f"\nRunning {num_steps} steps...")
    print("Legend: O=ball, |=paddle, X=hit\n")

    for step in range(num_steps):
        if verbose:
            print(f"\n--- Step {step + 1} ---")

        # Get current state
        states = game.get_state()

        # Send sensory observations to NAR
        for state in states:
            AddInput(f"{state}. :|:", Print=False)

        # Give goal: want to hit ball
        result = AddInput("hit! :|:", Print=False)

        # Check if NAR executed an action
        action_taken = None
        if result.get("executions"):
            for exe in result["executions"]:
                action_taken = exe["operator"]
                if verbose:
                    print(f"NAR action: {action_taken} (desire={exe.get('desire', 0):.2f})")
                game.execute_action(action_taken)
                break

        # Update game physics
        game.update_ball()

        # Check if hit
        hit = game.check_hit()
        if hit:
            # Positive feedback
            AddInput("hit. :|:", Print=False)
            if verbose:
                print("✓ HIT!")
        else:
            # Check if missed (ball passed paddle)
            if abs(game.ball_pos - game.paddle_pos) > 3:
                game.misses += 1
                if verbose:
                    print("✗ Miss")

        # Print visual state
        if verbose or step % 5 == 0:
            game.print_state()

        # Let NAR process
        AddInput("3", Print=False)  # 3 inference steps

        # Small delay for visibility
        if verbose:
            time.sleep(0.1)

    # Final statistics
    print("\n" + "=" * 70)
    print("  GAME OVER")
    print("=" * 70)
    print(f"\nFinal Score: {game.score}")
    print(f"Misses: {game.misses}")
    print(f"Hit Rate: {100 * game.score / num_steps:.1f}%")

    # Show what NAR learned
    print("\n[What NAR Learned]")
    print("\nQuerying: What leads to 'hit'?")
    result = AddInput("hit?")

    if result.get("answers"):
        print("\nAnswers:")
        for ans in result["answers"]:
            print(f"  - {ans['term']}")
    else:
        print("No specific answer (check implications in concepts)")

    return game.score, game.misses


def main():
    """Main entry point"""
    print("\nClojure ONA Pong Example")
    print("========================\n")

    try:
        # Run game
        score, misses = run_game(
            num_steps=30,
            enable_babbling=True,
            verbose=False  # Set to True for detailed output
        )

        print("\nExample complete!")

    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()
    finally:
        Exit()


if __name__ == "__main__":
    main()
