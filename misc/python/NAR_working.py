"""
Working Python interface for native ONA binary

Uses threading to read output asynchronously while sending commands.
This is much faster and more reliable than the JVM version!
"""

import subprocess
import threading
import queue
import time
import signal
import atexit
from pathlib import Path

CLOJURE_ONA_DIR = Path(__file__).parent.parent.parent.absolute()
NATIVE_BINARY = CLOJURE_ONA_DIR / "ona"

class NAR:
    """NAR process wrapper"""

    def __init__(self):
        if not NATIVE_BINARY.exists():
            raise FileNotFoundError(
                f"Native binary not found: {NATIVE_BINARY}\n"
                "Run: ./scripts/build_native.sh"
            )

        self.proc = subprocess.Popen(
            [str(NATIVE_BINARY), "shell"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            universal_newlines=True,
            cwd=str(CLOJURE_ONA_DIR),
            bufsize=1
        )

        # Queue for output lines
        self.output_queue = queue.Queue()

        # Start output reader thread
        self.reader_thread = threading.Thread(
            target=self._read_output,
            daemon=True
        )
        self.reader_thread.start()

        # Consume welcome message
        time.sleep(0.05)
        self._drain_queue(timeout=0.1)

    def _read_output(self):
        """Background thread to read output"""
        try:
            for line in iter(self.proc.stdout.readline, ''):
                if line:
                    self.output_queue.put(line.rstrip())
        except:
            pass

    def _drain_queue(self, timeout=0.5):
        """Drain output queue"""
        lines = []
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                line = self.output_queue.get(timeout=0.05)
                lines.append(line)
                if line == "//*done":
                    break
            except queue.Empty:
                if lines:  # Got some output, done
                    break
        return lines

    def send(self, command, wait=0.3):
        """Send command and collect output"""
        self.proc.stdin.write(command + "\n")
        self.proc.stdin.flush()
        return self._drain_queue(timeout=wait)

    def add_input(self, narsese):
        """Add Narsese input"""
        output = self.send(narsese)
        return {
            "output": output,
            "executions": [l for l in output if l.startswith("^")],
            "derivations": [l[9:] for l in output if l.startswith("Derived:")],
            "answers": [l[8:] for l in output if l.startswith("Answer:")]
        }

    def reset(self):
        """Reset system"""
        return self.add_input("*reset")

    def cycles(self, n):
        """Execute n cycles"""
        return self.add_input(str(n))

    def stats(self):
        """Get statistics"""
        result = self.add_input("*stats")
        stats = {}
        for line in result["output"]:
            if ":" in line and not line.startswith("//"):
                parts = line.split(":", 1)
                if len(parts) == 2:
                    key = parts[0].strip()
                    value = parts[1].strip()
                    try:
                        stats[key] = int(value)
                    except ValueError:
                        try:
                            stats[key] = float(value)
                        except ValueError:
                            stats[key] = value
        return stats

    def terminate(self):
        """Terminate process"""
        try:
            self.proc.stdin.write("quit\n")
            self.proc.stdin.flush()
            self.proc.wait(timeout=1)
        except:
            self.proc.terminate()

# Global instance
_nar_instance = None

def get_nar():
    """Get or create global NAR instance"""
    global _nar_instance
    if _nar_instance is None:
        _nar_instance = NAR()
    return _nar_instance

# Convenience functions
def AddInput(narsese):
    return get_nar().add_input(narsese)

def Reset():
    return get_nar().reset()

def ExecuteCycles(n):
    return get_nar().cycles(n)

def GetStats():
    return get_nar().stats()

# Cleanup
@atexit.register
def cleanup():
    if _nar_instance:
        _nar_instance.terminate()
