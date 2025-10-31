#!/usr/bin/env python3
"""
Integration test runner for ONA

Runs NAL test files and validates execution success.
"""
import subprocess
import sys
import os
from pathlib import Path

# Add parent misc/python to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "misc" / "python"))

class TestResult:
    def __init__(self, name, expected_ops, found_ops, success_rate):
        self.name = name
        self.expected_ops = expected_ops
        self.found_ops = found_ops
        self.success_rate = success_rate
        self.passed = success_rate >= expected_ops["threshold"]

def parse_nal_expectations(nal_file):
    """Parse NAL file to extract expected operations and success threshold"""
    with open(nal_file) as f:
        content = f.read()

    # Extract from comments
    threshold = 0.8  # default
    if "Success Criteria:" in content:
        line = [l for l in content.split('\n') if "Success Criteria:" in l][0]
        if "80%" in line:
            threshold = 0.8
        elif "70%" in line:
            threshold = 0.7
        elif "60%" in line:
            threshold = 0.6

    # Count setopname commands to know which operations are registered
    operations = []
    for line in content.split('\n'):
        if '*setopname' in line:
            parts = line.split()
            if len(parts) >= 3:
                operations.append(parts[2])

    # Count goals (!) in testing phase
    testing_phase = False
    goal_count = 0
    for line in content.split('\n'):
        if "TESTING PHASE" in line:
            testing_phase = True
        if testing_phase and "!" in line and ":|:" in line:
            goal_count += 1

    return {
        "threshold": threshold,
        "operations": operations,
        "expected_executions": goal_count
    }

def run_nal_test(nal_file, ona_binary):
    """Run a NAL test file and capture executed operations"""
    result = subprocess.run(
        [ona_binary, "shell"],
        input=open(nal_file).read(),
        capture_output=True,
        text=True,
        timeout=30
    )

    # Parse output for executed operations
    executed_ops = []
    for line in result.stdout.split('\n'):
        if line.startswith('^executed:'):
            parts = line.split()
            if len(parts) >= 2:
                op = parts[1]
                executed_ops.append(op)

    return executed_ops

def run_test_file(nal_file, ona_binary):
    """Run a single test file and return results"""
    print(f"\n{'='*70}")
    print(f"Running: {Path(nal_file).name}")
    print(f"{'='*70}")

    # Parse expectations
    expectations = parse_nal_expectations(nal_file)
    print(f"Expected operations: {expectations['operations']}")
    print(f"Expected executions: {expectations['expected_executions']}")
    print(f"Success threshold: {expectations['threshold']*100}%")

    # Run test
    try:
        executed_ops = run_nal_test(nal_file, ona_binary)
        print(f"\nExecuted operations: {executed_ops}")

        # Calculate success rate
        if expectations['expected_executions'] > 0:
            success_rate = len(executed_ops) / expectations['expected_executions']
        else:
            success_rate = 1.0 if not executed_ops else 0.0

        print(f"Success rate: {success_rate*100:.1f}%")

        # Determine pass/fail
        passed = success_rate >= expectations['threshold']
        if passed:
            print(f"✅ PASSED")
        else:
            print(f"❌ FAILED (needed {expectations['threshold']*100}%)")

        return TestResult(
            Path(nal_file).name,
            expectations,
            executed_ops,
            success_rate
        )

    except subprocess.TimeoutExpired:
        print("❌ TIMEOUT")
        return TestResult(Path(nal_file).name, expectations, [], 0.0)
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return TestResult(Path(nal_file).name, expectations, [], 0.0)

def main():
    # Find ONA binary
    script_dir = Path(__file__).parent
    ona_binary = script_dir.parent.parent / "ona"

    if not ona_binary.exists():
        print(f"ERROR: ONA binary not found at {ona_binary}")
        print("Run: ./scripts/build_native.sh")
        sys.exit(1)

    print(f"Using ONA binary: {ona_binary}")

    # Find all test files
    test_files = sorted(script_dir.glob("*.nal"))

    if not test_files:
        print("No .nal test files found!")
        sys.exit(1)

    print(f"Found {len(test_files)} test file(s)")

    # Run all tests
    results = []
    for test_file in test_files:
        result = run_test_file(test_file, ona_binary)
        results.append(result)

    # Print summary
    print(f"\n{'='*70}")
    print("SUMMARY")
    print(f"{'='*70}")

    for result in results:
        status = "✅ PASS" if result.passed else "❌ FAIL"
        print(f"{status} {result.name:30s} {result.success_rate*100:5.1f}% "
              f"({len(result.found_ops)}/{result.expected_ops['expected_executions']})")

    # Overall result
    passed_count = sum(1 for r in results if r.passed)
    print(f"\n{'='*70}")
    print(f"Passed: {passed_count}/{len(results)}")
    print(f"{'='*70}")

    # Exit with appropriate code
    sys.exit(0 if passed_count == len(results) else 1)

if __name__ == "__main__":
    main()
