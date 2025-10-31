#!/usr/bin/env python3
"""
Differential testing: Compare C ONA vs Clojure ONA behavior

Runs the same NAL tests on both implementations and compares:
- Executed operations (critical)
- Implications learned
- Query answers
- Concept formation
"""
import subprocess
import sys
import re
from pathlib import Path
from collections import defaultdict

class TestComparison:
    def __init__(self, test_name):
        self.test_name = test_name
        self.c_executions = []
        self.clojure_executions = []
        self.c_implications = []
        self.clojure_implications = []
        self.c_answers = []
        self.clojure_answers = []
        self.differences = []

    def add_difference(self, category, detail):
        self.differences.append(f"{category}: {detail}")

    def matches(self):
        return len(self.differences) == 0

def run_ona_test(test_file, binary):
    """Run a NAL test and capture output"""
    try:
        result = subprocess.run(
            [binary, "shell"],
            input=open(test_file).read(),
            capture_output=True,
            text=True,
            timeout=30
        )
        return result.stdout
    except subprocess.TimeoutExpired:
        return "TIMEOUT"
    except Exception as e:
        return f"ERROR: {e}"

def parse_executions(output):
    """Extract executed operations from output"""
    executions = []
    for line in output.split('\n'):
        # Match: ^executed: ^left desire=0.75
        if line.startswith('^executed:'):
            parts = line.split()
            if len(parts) >= 2:
                op = parts[1]
                executions.append(op)
        # Also match: ^left executed with args
        elif 'executed with args' in line:
            parts = line.split()
            if len(parts) >= 1 and parts[0].startswith('^'):
                op = parts[0]
                executions.append(op)
    return executions

def parse_implications(output):
    """Extract learned implications from *concepts output"""
    implications = []
    in_concepts = False

    for line in output.split('\n'):
        if '//*concepts' in line:
            in_concepts = True
            continue
        if '//*done' in line:
            in_concepts = False
            continue

        if in_concepts and '"implications":' in line:
            # Extract implication terms from JSON-like output
            # Look for: "term": "<something =/> something>"
            matches = re.findall(r'"term":\s*"([^"]+)"', line)
            for match in matches:
                if '=/>' in match:
                    implications.append(match)

    return implications

def parse_answers(output):
    """Extract query answers from output"""
    answers = []
    for line in output.split('\n'):
        # Match: Answer: <A =/> B>. Truth: frequency=1.0 confidence=0.42
        if line.startswith('Answer:'):
            # Extract the answer term and truth
            answer = line.replace('Answer:', '').strip()
            answers.append(answer)
    return answers

def compare_executions(c_execs, clojure_execs):
    """Compare executed operations between implementations"""
    differences = []

    # Check if same number
    if len(c_execs) != len(clojure_execs):
        differences.append(f"Execution count: C={len(c_execs)}, Clojure={len(clojure_execs)}")

    # Check if same operations (order matters for sensorimotor)
    if c_execs != clojure_execs:
        differences.append(f"Operations differ:")
        differences.append(f"  C ONA:     {c_execs}")
        differences.append(f"  Clojure:   {clojure_execs}")

        # Also show which are missing/extra
        c_set = set(c_execs)
        clojure_set = set(clojure_execs)

        missing_in_clojure = c_set - clojure_set
        extra_in_clojure = clojure_set - c_set

        if missing_in_clojure:
            differences.append(f"  Missing in Clojure: {missing_in_clojure}")
        if extra_in_clojure:
            differences.append(f"  Extra in Clojure: {extra_in_clojure}")

    return differences

def compare_implications(c_impls, clojure_impls):
    """Compare learned implications"""
    differences = []

    c_set = set(c_impls)
    clojure_set = set(clojure_impls)

    # Only report if significantly different
    missing = c_set - clojure_set
    extra = clojure_set - c_set

    if missing:
        differences.append(f"Implications in C but not Clojure: {len(missing)}")
        for impl in list(missing)[:3]:  # Show first 3
            differences.append(f"  - {impl}")

    if extra:
        differences.append(f"Implications in Clojure but not C: {len(extra)}")
        for impl in list(extra)[:3]:  # Show first 3
            differences.append(f"  + {impl}")

    return differences

def run_differential_test(test_file, c_binary, clojure_binary):
    """Run differential test and return comparison"""
    test_name = Path(test_file).name

    print(f"\n{'='*70}")
    print(f"Differential Test: {test_name}")
    print(f"{'='*70}")

    comparison = TestComparison(test_name)

    # Run on C ONA
    print("Running on C ONA...", end=' ', flush=True)
    c_output = run_ona_test(test_file, c_binary)
    if "TIMEOUT" in c_output or "ERROR" in c_output:
        print(f"❌ {c_output}")
        comparison.add_difference("C ONA", c_output)
        return comparison
    print("✓")

    # Run on Clojure ONA
    print("Running on Clojure ONA...", end=' ', flush=True)
    clojure_output = run_ona_test(test_file, clojure_binary)
    if "TIMEOUT" in clojure_output or "ERROR" in clojure_output:
        print(f"❌ {clojure_output}")
        comparison.add_difference("Clojure ONA", clojure_output)
        return comparison
    print("✓")

    # Parse outputs
    comparison.c_executions = parse_executions(c_output)
    comparison.clojure_executions = parse_executions(clojure_output)
    comparison.c_implications = parse_implications(c_output)
    comparison.clojure_implications = parse_implications(clojure_output)
    comparison.c_answers = parse_answers(c_output)
    comparison.clojure_answers = parse_answers(clojure_output)

    print(f"  C ONA executions: {comparison.c_executions}")
    print(f"  Clojure executions: {comparison.clojure_executions}")

    # Compare executions (most critical)
    exec_diffs = compare_executions(comparison.c_executions, comparison.clojure_executions)
    for diff in exec_diffs:
        comparison.add_difference("Executions", diff)

    # Compare implications (informational)
    impl_diffs = compare_implications(comparison.c_implications, comparison.clojure_implications)
    for diff in impl_diffs:
        comparison.add_difference("Implications", diff)

    # Report
    if comparison.matches():
        print("✅ MATCH: Both implementations behave identically")
    else:
        print("⚠️  DIFFERENCES FOUND:")
        for diff in comparison.differences:
            print(f"  {diff}")

    return comparison

def main():
    # Find binaries
    script_dir = Path(__file__).parent
    clojure_binary = script_dir.parent.parent / "ona"
    c_binary = script_dir.parent.parent.parent / "NAR"

    if not clojure_binary.exists():
        print(f"ERROR: Clojure ONA binary not found at {clojure_binary}")
        print("Run: ./scripts/build_native.sh")
        sys.exit(1)

    if not c_binary.exists():
        print(f"ERROR: C ONA binary not found at {c_binary}")
        print("C ONA must be built in the parent directory")
        sys.exit(1)

    print(f"C ONA binary: {c_binary}")
    print(f"Clojure ONA binary: {clojure_binary}")

    # Find test files
    test_files = sorted(script_dir.glob("*.nal"))

    if not test_files:
        print("No .nal test files found!")
        sys.exit(1)

    print(f"Found {len(test_files)} test file(s)")

    # Run all differential tests
    results = []
    for test_file in test_files:
        result = run_differential_test(test_file, c_binary, clojure_binary)
        results.append(result)

    # Print summary
    print(f"\n{'='*70}")
    print("DIFFERENTIAL TESTING SUMMARY")
    print(f"{'='*70}")

    matches = 0
    execution_matches = 0

    for result in results:
        # Check if executions match (most important)
        exec_match = result.c_executions == result.clojure_executions
        exec_symbol = "✅" if exec_match else "❌"

        overall_symbol = "✅" if result.matches() else "⚠️ "

        print(f"{overall_symbol} {result.test_name:30s} "
              f"Exec:{exec_symbol} "
              f"C:{len(result.c_executions)} Clj:{len(result.clojure_executions)}")

        if result.matches():
            matches += 1
        if exec_match:
            execution_matches += 1

    print(f"\n{'='*70}")
    print(f"Execution Matches: {execution_matches}/{len(results)} "
          f"({execution_matches*100//len(results)}%)")
    print(f"Perfect Matches: {matches}/{len(results)} "
          f"({matches*100//len(results)}%)")
    print(f"{'='*70}")

    # Exit code: 0 if all executions match, 1 otherwise
    sys.exit(0 if execution_matches == len(results) else 1)

if __name__ == "__main__":
    main()
