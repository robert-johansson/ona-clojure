"""
Simplest possible Python interface for native ONA - uses fixed timeouts
"""

import subprocess
import time
from pathlib import Path

# Find native binary
CLOJURE_ONA_DIR = Path(__file__).parent.parent.parent.absolute()
NATIVE_BINARY = CLOJURE_ONA_DIR / "ona"

# Spawn process
proc = subprocess.Popen(
    [str(NATIVE_BINARY), "shell"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,  # Merge stderr into stdout
    universal_newlines=True,
    cwd=str(CLOJURE_ONA_DIR)
)

# Wait for welcome message
time.sleep(0.1)

def send_and_wait(command, wait_time=0.2):
    """Send command and wait for output"""
    proc.stdin.write(command + "\n")
    proc.stdin.flush()
    time.sleep(wait_time)
    return command

# Test it
print("Testing simple interface...")
print(f"Binary: {NATIVE_BINARY}")

send_and_wait("*reset")
print("✓ Reset sent")

send_and_wait("<bird --> animal>. {1.0 0.9}")
print("✓ Belief sent")

send_and_wait("10")
print("✓ Cycles sent")

send_and_wait("<bird --> animal>?")
print("✓ Query sent")

send_and_wait("*stats")
print("✓ Stats sent")

send_and_wait("quit")
print("✓ Quit sent")

proc.wait(timeout=2)
print(f"✓ Process exited: {proc.returncode}")
