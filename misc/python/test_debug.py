#!/usr/bin/env python3
"""Debug test to see exactly what's being sent/received"""

import subprocess
from pathlib import Path

CLOJURE_ONA_DIR = Path(__file__).parent.parent.parent.absolute()

print("Starting Clojure ONA...")
proc = subprocess.Popen(
    ["clj", "-M:ona", "shell"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    universal_newlines=True,
    cwd=str(CLOJURE_ONA_DIR),
    bufsize=1
)

# Read startup messages
print("Reading startup...")
while True:
    line = proc.stdout.readline()
    print(f"RECV: {line!r}")
    if "Use 'quit' to exit" in line:
        break

# Send A.
print("\n>>> Sending: A. :|:")
proc.stdin.write("A. :|:\n")
proc.stdin.flush()

# Read response
print("Reading response...")
for i in range(5):
    line = proc.stdout.readline()
    print(f"RECV [{i}]: {line!r}")
    if not line:
        print("  (no more output)")
        break

# Send 0
print("\n>>> Sending: 0")
proc.stdin.write("0\n")
proc.stdin.flush()

# Read response
print("Reading response...")
for i in range(5):
    line = proc.stdout.readline()
    print(f"RECV [{i}]: {line!r}")
    if "done with" in line:
        print("  (got done message!)")
        break
    if not line:
        print("  (no more output)")
        break

# Send quit
print("\n>>> Sending: quit")
proc.stdin.write("quit\n")
proc.stdin.flush()

proc.wait()
print("Done!")
