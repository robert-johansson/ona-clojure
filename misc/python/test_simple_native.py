#!/usr/bin/env python3
"""
Minimal test of native binary Python interface
"""

import subprocess
import time
from pathlib import Path

# Find native binary
CLOJURE_ONA_DIR = Path(__file__).parent.parent.parent.absolute()
NATIVE_BINARY = CLOJURE_ONA_DIR / "ona"

print(f"Native binary: {NATIVE_BINARY}")
print(f"Exists: {NATIVE_BINARY.exists()}")

print("\n" + "=" * 60)
print("Spawning native ONA shell...")
print("=" * 60)

start = time.time()
proc = subprocess.Popen(
    [str(NATIVE_BINARY), "shell"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    universal_newlines=True,
    cwd=str(CLOJURE_ONA_DIR),
    bufsize=0  # Unbuffered
)
print(f"Spawned in {time.time() - start:.3f}s")

# Read welcome message
print("\nReading welcome message...")
for i in range(5):
    line = proc.stdout.readline()
    print(f"  Line {i+1}: {line.rstrip()}")

# Send reset command
print("\nSending: *reset")
proc.stdin.write("*reset\n")
proc.stdin.flush()

# Read until //*done
print("\nReading response...")
timeout = time.time() + 2  # 2 second timeout
lines = []
while time.time() < timeout:
    line = proc.stdout.readline()
    if not line:
        print("  EOF reached")
        break
    line = line.strip()
    print(f"  Got: {line}")
    lines.append(line)
    if line == "//*done":
        print("  ✓ Found //*done marker")
        break

if time.time() >= timeout:
    print("  ✗ TIMEOUT waiting for //*done")

# Send quit
print("\nSending: quit")
proc.stdin.write("quit\n")
proc.stdin.flush()

# Read final output
for i in range(3):
    line = proc.stdout.readline()
    if line:
        print(f"  {line.rstrip()}")

proc.wait(timeout=2)
print(f"\n✓ Process exited with code: {proc.returncode}")
