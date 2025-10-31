# Python Interface for ONA Clojure (Native Binary)

Python interface for the GraalVM native-compiled ONA binary.

## Why Use the Native Binary?

The native binary is **~200x faster to start** than the JVM version:
- **Native**: 8ms startup
- **JVM**: 1500ms startup

The native binary also has **much more reliable I/O behavior** through subprocess pipes.

## Installation

1. Build the native binary:
   ```bash
   cd ../..
   ./scripts/build_native.sh
   ```

2. Create Python virtual environment (optional but recommended):
   ```bash
   python3 -m venv venv
   source venv/bin/activate
   ```

## Usage

### Simple Example

```python
from NAR_working import AddInput, Reset, ExecuteCycles, GetStats

# Reset the system
Reset()

# Add beliefs
AddInput("<bird --> animal>. {1.0 0.9}")
AddInput("<robin --> bird>. {1.0 0.9}")

# Execute reasoning cycles
ExecuteCycles(10)

# Query
result = AddInput("<robin --> animal>?")
print(result['answers'])  # Check for derived answer

# Get statistics
stats = GetStats()
print(f"Current time: {stats['currentTime']}")
print(f"Total concepts: {stats['total concepts']}")
```

### API Reference

#### `AddInput(narsese: str) -> dict`

Add Narsese input and get results.

**Returns:**
```python
{
    "output": ["list", "of", "output", "lines"],
    "executions": ["^operations", "executed"],
    "derivations": ["derived", "events"],
    "answers": ["query", "answers"]
}
```

#### `Reset() -> dict`

Reset the system to initial state.

#### `ExecuteCycles(n: int) -> dict`

Execute n reasoning cycles.

#### `GetStats() -> dict`

Get system statistics.

**Returns:**
```python
{
    "currentTime": 10,
    "total concepts": 5,
    "current belief events cnt": 2,
    ...
}
```

## Example: Pong Game

```python
from NAR_working import NAR

# Create NAR instance
nar = NAR()

# Register observation
nar.add_input("<ball --> [at_x_5]>. :|: {1.0 0.9}")

# Set goal
nar.add_input("<ball --> [hit]>! {1.0 0.9}")

# Learn implication
nar.add_input("<(&/, <ball --> [at_x_5]>, ^move_paddle_right) =/> <ball --> [hit]>>. {1.0 0.9}")

# Execute cycles - system should decide to move paddle
result = nar.cycles(10)
print(result['executions'])  # Should show ^move_paddle_right

# Clean up
nar.terminate()
```

## Files

- **NAR_working.py** - Main working interface (USE THIS)
- **test_working.py** - Test suite demonstrating all features
- **NAR_simple.py** - Minimal example showing subprocess communication
- **NAR_native.py** - Experimental select()-based version (macOS issues)
- **NAR_native_threaded.py** - Experimental threading version

## Troubleshooting

**Binary not found error:**
```
Run: ./scripts/build_native.sh
```

**Import errors:**
```bash
# Make sure you're in the misc/python directory
cd misc/python
source venv/bin/activate  # If using venv
python3 your_script.py
```

## Performance Comparison

| Version | Startup Time | I/O Reliability |
|---------|--------------|-----------------|
| **Native Binary** | **8ms** ⚡ | ✅ Excellent |
| JVM (clj -M:ona) | 1500ms | ⚠️ Buffering issues |

The native binary is the **recommended** way to use ONA from Python!
