"""
Python interface for Clojure ONA (OpenNARS for Applications) - Native Binary Version

Uses the GraalVM native binary instead of JVM for faster startup and simpler I/O.

Usage:
    from NAR_native import AddInput, Reset, GetStats

    # Add input and get results
    result = AddInput("bird.")
    print(result["executions"])  # Operations executed
    print(result["derivations"])  # Derived events
    print(result["answers"])      # Query answers

    # Reset system
    Reset()

    # Get statistics
    stats = GetStats()
"""

import os
import sys
import json
import signal
import subprocess
from pathlib import Path

# Find the Clojure ONA directory
CLOJURE_ONA_DIR = Path(__file__).parent.parent.parent.absolute()
NATIVE_BINARY = CLOJURE_ONA_DIR / "ona"

def spawnNAR():
    """Spawn native ONA shell process"""
    if not NATIVE_BINARY.exists():
        raise FileNotFoundError(
            f"Native binary not found at {NATIVE_BINARY}\n"
            "Run: ./scripts/build_native.sh"
        )

    proc = subprocess.Popen(
        [str(NATIVE_BINARY), "shell"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
        cwd=str(CLOJURE_ONA_DIR),
        bufsize=0  # Unbuffered for native binary
    )

    # Consume welcome message
    import select
    import time
    time.sleep(0.05)  # Give it a moment to print welcome
    while True:
        ready, _, _ = select.select([proc.stdout], [], [], 0.01)
        if not ready:
            break
        line = proc.stdout.readline()
        if not line:
            break

    return proc

NARproc = spawnNAR()

def getNAR():
    """Get current NAR process"""
    return NARproc

def setNAR(proc):
    """Set NAR process"""
    global NARproc
    NARproc = proc

def terminateNAR(usedNAR=None):
    """Terminate NAR process"""
    if usedNAR is None:
        usedNAR = NARproc
    try:
        os.killpg(os.getpgid(usedNAR.pid), signal.SIGTERM)
    except:
        usedNAR.terminate()

def parseTruth(truth_obj):
    """Parse truth value from JSON object"""
    if isinstance(truth_obj, dict):
        return {
            "frequency": truth_obj.get("frequency", 1.0),
            "confidence": truth_obj.get("confidence", 0.9)
        }
    return None

def parseTask(task_obj):
    """Parse task/event from JSON object

    Clojure ONA outputs events as:
    {
        "term": "A",
        "type": "belief" | "goal",
        "truth": {"frequency": 1.0, "confidence": 0.9},
        "occurrenceTime": 123 | "eternal"
    }
    """
    if not isinstance(task_obj, dict):
        return None

    return {
        "term": task_obj.get("term", ""),
        "type": task_obj.get("type", "belief"),
        "truth": parseTruth(task_obj.get("truth")),
        "occurrenceTime": task_obj.get("occurrenceTime")
    }

def GetRawOutput(nar=None):
    """Read lines from NAR until 'done' marker"""
    if nar is None:
        nar = NARproc

    lines = []
    # Use select to avoid blocking on empty output
    import select

    while True:
        # Check if data is available with timeout
        ready, _, _ = select.select([nar.stdout], [], [], 0.1)
        if not ready:
            # No more output available
            break

        line = nar.stdout.readline()
        if not line:  # EOF
            break
        line = line.strip()
        if line == "//*done":
            break
        if line:  # Skip empty lines
            lines.append(line)

    return lines

def AddInput(narsese, nar=None):
    """Add Narsese input and get results

    Returns dict with:
        - executions: List of operation executions
        - derivations: List of derived events
        - answers: List of query answers
        - output: Raw output lines
    """
    if nar is None:
        nar = NARproc

    # Send input
    nar.stdin.write(narsese + "\n")
    nar.stdin.flush()

    # Read output
    output = GetRawOutput(nar)

    # Parse structured output
    result = {
        "executions": [],
        "derivations": [],
        "answers": [],
        "output": output
    }

    for line in output:
        # Parse operation executions: ^op executed
        if line.startswith("^"):
            result["executions"].append(line)

        # Parse derived events: Derived: ...
        elif line.startswith("Derived:"):
            result["derivations"].append(line[8:].strip())

        # Parse answers: Answer: ...
        elif line.startswith("Answer:"):
            result["answers"].append(line[7:].strip())

    return result

def Reset(nar=None):
    """Reset the system"""
    return AddInput("*reset", nar)

def GetStats(nar=None):
    """Get system statistics"""
    result = AddInput("*stats", nar)

    # Parse stats from output
    stats = {}
    for line in result["output"]:
        if ":" in line and not line.startswith("//"):
            parts = line.split(":", 1)
            if len(parts) == 2:
                key = parts[0].strip()
                value = parts[1].strip()
                # Try to parse as number
                try:
                    stats[key] = int(value)
                except ValueError:
                    try:
                        stats[key] = float(value)
                    except ValueError:
                        stats[key] = value

    return stats

def ExecuteCycles(n, nar=None):
    """Execute n reasoning cycles"""
    return AddInput(str(n), nar)

# Cleanup on exit
import atexit
atexit.register(lambda: terminateNAR(NARproc))
