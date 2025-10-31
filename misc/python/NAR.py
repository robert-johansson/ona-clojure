"""
Python interface for Clojure ONA (OpenNARS for Applications)

Similar to C ONA's NAR.py but simpler - Clojure ONA outputs JSON!

Usage:
    from NAR import AddInput, Reset, GetStats

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

def spawnNAR():
    """Spawn Clojure ONA shell process"""
    return subprocess.Popen(
        ["clj", "-M:ona", "shell"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
        cwd=str(CLOJURE_ONA_DIR),
        bufsize=1  # Line buffered
    )

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
        "occurrence-time": 123 | "eternal"
    }
    """
    if not isinstance(task_obj, dict):
        return None

    result = {
        "term": task_obj.get("term", ""),
        "occurrenceTime": str(task_obj.get("occurrence-time", "eternal"))
    }

    # Determine punctuation from type
    event_type = task_obj.get("type", "belief")
    if event_type == "goal":
        result["punctuation"] = "!"
    elif event_type == "question":
        result["punctuation"] = "?"
    else:
        result["punctuation"] = "."

    # Parse truth if present
    if "truth" in task_obj:
        result["truth"] = parseTruth(task_obj["truth"])

    # Priority if present
    if "priority" in task_obj:
        result["Priority"] = str(task_obj["priority"])

    return result

def parseExecution(exec_str):
    """Parse execution string

    Clojure ONA outputs: ^executed: ^op desire=0.70
    or when parsed from JSON: {"operation": "^op", "desire": 0.70}
    """
    if isinstance(exec_str, dict):
        return {
            "operator": exec_str.get("operation", ""),
            "arguments": exec_str.get("arguments", []),
            "desire": exec_str.get("desire", 0.0)
        }

    # Parse from text format
    if "^executed:" in exec_str:
        parts = exec_str.split("^executed:")[1].strip().split()
        operator = parts[0] if parts else ""
        desire = 0.0
        if "desire=" in exec_str:
            desire = float(exec_str.split("desire=")[1].split()[0])
        return {
            "operator": operator,
            "arguments": [],
            "desire": desire
        }

    return {"operator": "", "arguments": [], "desire": 0.0}

def parseReason(lines):
    """Parse decision reasoning from output lines

    Looks for lines like:
    ^decide: <A =/> B> desire=0.70
    """
    for line in lines:
        if "^decide:" in line:
            parts = line.split("^decide:")[1].strip()
            desire = 0.0
            if "desire=" in parts:
                desire = float(parts.split("desire=")[1].split()[0])

            implication = parts.split("desire=")[0].strip()
            return {
                "desire": desire,
                "hypothesis": {"term": implication, "punctuation": "."},
                "precondition": None  # Would need to parse implication
            }

    return None

def GetRawOutput(usedNAR, send_zero=True):
    """Get raw output lines from NAR

    Args:
        send_zero: If True, sends '0' to execute 0 cycles and flush output.
                   If False, just reads available output without triggering new execution.
    """
    lines = []
    requestOutputArgs = False

    if send_zero:
        usedNAR.stdin.write("0\n")
        usedNAR.stdin.flush()

    # Read lines until we see a "done" message or timeout
    while True:
        line = usedNAR.stdout.readline()
        if not line:
            break
        line = line.strip()

        # Stop when we see the "done" message
        if "done with" in line and "additional inference steps" in line:
            break

        if line:
            lines.append(line)

        if "operation result product expected" in line.lower():
            requestOutputArgs = True
            break

    return lines, requestOutputArgs

def GetOutput(usedNAR):
    """Get parsed output from NAR

    Returns dict with:
        - input: Input events
        - derivations: Derived/revised events
        - answers: Query answers
        - executions: Operations executed
        - reason: Decision reasoning
        - selections: Selected events
        - raw: Raw text output
    """
    lines, requestOutputArgs = GetRawOutput(usedNAR)

    executions = []
    inputs = []
    derivations = []
    answers = []
    selections = []

    for line in lines:
        # Try to parse as JSON first
        if line.startswith('{') or line.startswith('['):
            try:
                obj = json.loads(line)
                # Handle different JSON output types
                if isinstance(obj, dict):
                    if "operation" in obj:
                        executions.append(parseExecution(obj))
                    elif "term" in obj:
                        parsed = parseTask(obj)
                        if parsed:
                            derivations.append(parsed)
            except json.JSONDecodeError:
                pass

        # Parse text output
        if line.startswith('^executed:'):
            executions.append(parseExecution(line))
        elif line.startswith('Input:'):
            task_str = line.split('Input:')[1].strip()
            # Would need to parse Narsese here
            inputs.append({"term": task_str, "punctuation": "."})
        elif line.startswith('Derived:') or line.startswith('Revised:'):
            task_str = line.split(':')[1].strip()
            derivations.append({"term": task_str, "punctuation": "."})
        elif line.startswith('Answer:'):
            task_str = line.split('Answer:')[1].strip()
            answers.append({"term": task_str, "punctuation": "."})
        elif line.startswith('Selected:'):
            task_str = line.split('Selected:')[1].strip()
            selections.append({"term": task_str, "punctuation": "."})

    reason = parseReason(lines)

    return {
        "input": inputs,
        "derivations": derivations,
        "answers": answers,
        "executions": executions,
        "reason": reason,
        "selections": selections,
        "raw": "\n".join(lines),
        "requestOutputArgs": requestOutputArgs
    }

def GetStats(usedNAR):
    """Get NAR statistics

    Sends *stats command and parses output
    """
    usedNAR.stdin.write("*stats\n")
    usedNAR.stdin.flush()

    lines, _ = GetRawOutput(usedNAR)
    stats = {}

    for line in lines:
        if ':' in line and not line.startswith('//'):
            try:
                left = line.split(':')[0].strip().replace(' ', '_')
                right = float(line.split(':')[1].strip())
                stats[left] = right
            except (ValueError, IndexError):
                pass

    return stats

def AddInput(narsese, Print=True, usedNAR=None):
    """Add Narsese input to NAR

    Args:
        narsese: Narsese string (e.g., "bird. :|:" or "^left!")
        Print: Whether to print raw output
        usedNAR: NAR process to use (default: global NARproc)

    Returns:
        Parsed output dict (or stats dict if narsese == "*stats")
    """
    if usedNAR is None:
        usedNAR = NARproc

    usedNAR.stdin.write(narsese + '\n')
    usedNAR.stdin.flush()

    ReturnStats = narsese == "*stats"
    if ReturnStats:
        stats = GetStats(usedNAR)
        if Print:
            for key, value in stats.items():
                print(f"{key}: {value}")
        return stats

    ret = GetOutput(usedNAR)
    if Print:
        print(ret["raw"])
        sys.stdout.flush()

    return ret

def Exit(usedNAR=None):
    """Exit NAR process"""
    if usedNAR is None:
        usedNAR = NARproc
    try:
        usedNAR.stdin.write("quit\n")
        usedNAR.stdin.flush()
    except:
        pass
    finally:
        terminateNAR(usedNAR)

def Reset(usedNAR=None):
    """Reset NAR memory"""
    if usedNAR is None:
        usedNAR = NARproc
    AddInput("*reset", Print=False, usedNAR=usedNAR)

def PrintedTask(task):
    """Convert task dict back to Narsese string"""
    st = task["term"] + task["punctuation"]

    if task.get("occurrenceTime", "eternal") != "eternal":
        if task["occurrenceTime"] == "now":
            st += " :|:"
        elif task["occurrenceTime"].isdigit():
            st += f" :|: occurrenceTime={task['occurrenceTime']}"

    if "Priority" in task:
        st += f" Priority={task['Priority']}"

    if "truth" in task:
        st += f" Truth: frequency={task['truth']['frequency']} confidence={task['truth']['confidence']}"

    return st

def Shell():
    """Interactive shell - read from stdin and send to NAR"""
    while True:
        try:
            inp = input().rstrip("\n")
        except (EOFError, KeyboardInterrupt):
            Exit()
            sys.exit(0)
        AddInput(inp)

# Set volume to 100 on startup
AddInput("*volume=100", Print=False)

if __name__ == "__main__":
    Shell()
