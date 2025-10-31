"""
Python interface for Clojure ONA (OpenNARS for Applications) - Native Binary Version with Threading

Uses threading to avoid blocking on I/O.
"""

import os
import sys
import signal
import subprocess
import threading
import queue
import time
from pathlib import Path

# Find the Clojure ONA directory
CLOJURE_ONA_DIR = Path(__file__).parent.parent.parent.absolute()
NATIVE_BINARY = CLOJURE_ONA_DIR / "ona"

def reader_thread(pipe, output_queue):
    """Thread to read from pipe and put lines in queue"""
    try:
        for line in iter(pipe.readline, ''):
            if line:
                output_queue.put(line.rstrip())
    except:
        pass

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
        bufsize=1  # Line buffered
    )

    # Create queue for output
    output_queue = queue.Queue()

    # Start reader thread
    reader = threading.Thread(target=reader_thread, args=(proc.stdout, output_queue), daemon=True)
    reader.start()

    proc.output_queue = output_queue

    # Consume welcome message (wait max 0.5s)
    timeout = time.time() + 0.5
    while time.time() < timeout:
        try:
            line = output_queue.get(timeout=0.05)
        except queue.Empty:
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

def GetRawOutput(nar=None, timeout_sec=2.0):
    """Read lines from NAR until 'done' marker or timeout"""
    if nar is None:
        nar = NARproc

    lines = []
    timeout = time.time() + timeout_sec

    while time.time() < timeout:
        try:
            line = nar.output_queue.get(timeout=0.05)
            if line == "//*done":
                break
            if line:  # Skip empty lines
                lines.append(line)
        except queue.Empty:
            # No more output available, check if we should continue waiting
            if lines:  # If we got some output, stop waiting
                break
            continue

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
