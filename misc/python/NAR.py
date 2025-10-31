"""
Drop-in compatible NAR.py for Clojure ONA native binary

This is API-compatible with the C ONA NAR.py, so all existing Python code
written for C ONA will work unchanged with the GraalVM native Clojure binary.

Uses threading to avoid subprocess buffering issues.
"""
import os
import sys
import ast
import signal
import subprocess
import threading
import queue
import time
from pathlib import Path

# Find native binary
CLOJURE_ONA_DIR = Path(__file__).parent.parent.parent.absolute()
NATIVE_BINARY = CLOJURE_ONA_DIR / "ona"

def spawnNAR():
    """Spawn ONA native binary subprocess with threaded output reading"""
    if not NATIVE_BINARY.exists():
        raise FileNotFoundError(
            f"Native binary not found: {NATIVE_BINARY}\n"
            "Run: ./scripts/build_native.sh from {CLOJURE_ONA_DIR}"
        )

    proc = subprocess.Popen(
        [str(NATIVE_BINARY), "shell"],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        universal_newlines=True,
        bufsize=1,
        cwd=str(CLOJURE_ONA_DIR)
    )

    # Create output queue and reader thread
    proc.output_queue = queue.Queue()

    def read_output():
        try:
            for line in iter(proc.stdout.readline, ''):
                if line:
                    proc.output_queue.put(line.rstrip())
        except:
            pass

    proc.reader_thread = threading.Thread(target=read_output, daemon=True)
    proc.reader_thread.start()

    # Consume welcome message
    time.sleep(0.05)
    while True:
        try:
            proc.output_queue.get(timeout=0.05)
        except queue.Empty:
            break

    return proc

NARproc = spawnNAR()

def getNAR():
    return NARproc

def setNAR(proc):
    global NARproc
    NARproc = proc

def terminateNAR(usedNAR=None):
    if usedNAR is None:
        usedNAR = NARproc
    try:
        usedNAR.stdin.write("quit\n")
        usedNAR.stdin.flush()
        usedNAR.wait(timeout=1)
    except:
        usedNAR.terminate()

def parseTruth(T):
    return {"frequency": T.split("frequency=")[1].split(" confidence")[0].replace(",",""), "confidence": T.split(" confidence=")[1].split(" dt=")[0].split(" occurrenceTime=")[0]}

def parseTask(s):
    M = {"occurrenceTime" : "eternal"}
    if " :|:" in s:
        M["occurrenceTime"] = "now"
        s = s.replace(" :|:","")
        if "occurrenceTime" in s:
            M["occurrenceTime"] = s.split("occurrenceTime=")[1].split(" ")[0]
    if "Stamp" in s:
        M["Stamp"] = ast.literal_eval(s.split("Stamp=")[1].split("]")[0]+"]")
    sentence = s.split(" occurrenceTime=")[0] if " occurrenceTime=" in s else s.split(" Stamp=")[0].split(" Priority=")[0].split(" creationTime=")[0]
    M["punctuation"] = sentence[-4] if ":|:" in sentence else sentence[-1]
    M["term"] = sentence.split(" creationTime")[0].split(" occurrenceTime")[0].split(" Truth")[0].split(" Stamp=")[0][:-1]
    if "Truth" in s:
        M["truth"] = parseTruth(s.split("Truth: ")[1])
    if "Priority" in s:
        M["Priority"] = s.split("Priority=")[1].split(" ")[0]
    return M

def parseReason(sraw):
    if "implication: " not in sraw:
        return None
    Implication = parseTask(sraw.split("implication: ")[-1].split("precondition: ")[0]) #last reason only (others couldn't be associated currently)
    Precondition = parseTask(sraw.split("precondition: ")[-1].split("\n")[0])
    Implication["occurrenceTime"] = "eternal"
    Precondition["punctuation"] = Implication["punctuation"] = "."
    Reason = {}
    Reason["desire"] = sraw.split("decision expectation=")[-1].split(" ")[0]
    Reason["hypothesis"] = Implication
    Reason["precondition"] = Precondition
    return Reason

def parseExecution(e):
    if "args " not in e:
        return {"operator" : e.split(" ")[0], "arguments" : []}
    opname = e.split(" ")[0]
    return {"operator": opname, "arguments": e.split("args ")[1].split("{SELF} * ")[1][:-1], 'metta': '(^ ' + opname[1:] + ')'}

def GetRawOutput(usedNAR):
    """Get raw output from NAR using threaded queue reading"""
    usedNAR.stdin.write("0\n")
    usedNAR.stdin.flush()

    lines = []
    requestOutputArgs = False
    deadline = time.time() + 0.5

    while time.time() < deadline:
        try:
            line = usedNAR.output_queue.get(timeout=0.05)
            if line == "done with 0 additional inference steps.":
                break
            lines.append(line)
            if line == "//Operation result product expected:":
                requestOutputArgs = True
                break
            if line == "//*done":
                break
        except queue.Empty:
            if lines:  # Got some output, done
                break

    return lines, requestOutputArgs

def GetOutput(usedNAR):
    lines, requestOutputArgs = GetRawOutput(usedNAR)
    executions = [parseExecution(l) for l in lines if l.startswith('^')]
    inputs = [parseTask(l.split("Input: ")[1]) for l in lines if l.startswith('Input:')]
    derivations = [parseTask(l.split("Derived: " if l.startswith('Derived:') else "Revised: ")[1]) for l in lines if l.startswith('Derived:') or l.startswith('Revised:')]
    answers = [parseTask(l.split("Answer: ")[1]) for l in lines if l.startswith('Answer:')]
    selections = [parseTask(l.split("Selected: ")[1]) for l in lines if l.startswith('Selected:')]
    reason = parseReason("\n".join(lines))
    return {"input": inputs, "derivations": derivations, "answers": answers, "executions": executions, "reason": reason, "selections": selections, "raw": "\n".join(lines), "requestOutputArgs" : requestOutputArgs}

def GetStats(usedNAR):
    Stats = {}
    lines, _ = GetRawOutput(usedNAR)
    for l in lines:
        if ":" in l and not l.startswith("//"):
            parts = l.split(":", 1)
            if len(parts) == 2:
                leftside = parts[0].replace(" ", "_").strip()
                rightside_str = parts[1].strip()
                try:
                    Stats[leftside] = float(rightside_str)
                except ValueError:
                    Stats[leftside] = rightside_str
    return Stats

def AddInput(narsese, Print=True, usedNAR=None):
    if usedNAR is None:
        usedNAR = NARproc
    usedNAR.stdin.write(narsese + '\n')
    usedNAR.stdin.flush()
    ReturnStats = narsese == "*stats"
    if ReturnStats:
        result = GetStats(usedNAR)
        if Print:
            lines, _ = GetRawOutput(usedNAR)
            print("\n".join(lines))
        return result
    ret = GetOutput(usedNAR)
    if Print:
        print(ret["raw"])
        sys.stdout.flush()
    return ret

def Exit(usedNAR=None):
    if usedNAR is None:
        usedNAR = NARproc
    try:
        usedNAR.stdin.write("quit\n")
        usedNAR.stdin.flush()
    except:
        pass

def Reset(usedNAR=None):
    if usedNAR is None:
        usedNAR = NARproc
    AddInput("*reset", usedNAR=usedNAR)

# Set default volume
AddInput("*volume=100")

def PrintedTask(task):
    st = task["term"] + task["punctuation"]
    st += (" :|: occurrenceTime="+task["occurrenceTime"] if task["occurrenceTime"].isdigit() else "")
    if "Priority" in task: st += " Priority=" + str(task["Priority"])
    if "truth" in task: st += " Truth: frequency="+task["truth"]["frequency"] + " confidence="+task["truth"]["confidence"]
    return st

def Shell():
    while True:
        try:
            inp = input().rstrip("\n")
        except:
            Exit()
            exit(0)
        AddInput(inp)

if __name__ == "__main__":
    Shell()
