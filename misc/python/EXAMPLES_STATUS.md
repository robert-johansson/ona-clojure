# Python Examples Status

## ✅ Working Examples (Native Binary)

These examples work with the GraalVM native binary:

### 1. **test_working.py** ✅ RECOMMENDED
Complete test suite demonstrating all features.

```bash
python3 test_working.py
```

**Tests:**
- Import and spawn (8ms!)
- Reset
- Add beliefs
- Execute cycles
- Queries
- Statistics

**Output:** All tests pass ✅

---

### 2. **NAR_simple.py** ✅
Minimal example showing subprocess communication works.

```bash
python3 NAR_simple.py
```

**Shows:** Commands are sent and process exits cleanly.

---

### 3. **basic_example_native.py** ✅ BEST DEMO
Demonstrates goal-driven reasoning and operation execution.

```bash
python3 basic_example_native.py
```

**Demonstrates:**
- Operation registration
- Temporal sequence learning: A → ^op → B
- Implication formation: `<(A &/ ^op) =/> B>`
- Goal-driven decision making: Given B!, system executes ^op

**Output:**
```
✓ SUCCESS! NAR executed:
  ^decide: (A &/ ^op) desire=0.75
```

---

## ❌ Broken Examples (Old JVM Version)

These examples use `NAR.py` which doesn't work due to JVM subprocess buffering issues:

- **basic_example.py** - Use `basic_example_native.py` instead
- **simple_example.py** - Use `test_working.py` instead
- **pong_example.py** - Could be converted to native version

---

## 🔧 Library Files

### Use These:

- **NAR_working.py** ✅ - Main interface (threading-based, robust)
  - Import: `from NAR_working import NAR, AddInput, Reset, ExecuteCycles, GetStats`

### Experimental/Reference:

- **NAR_simple.py** - Minimal send-only example
- **NAR_native.py** - select()-based (macOS issues)
- **NAR_native_threaded.py** - Early threading attempt
- **NAR.py** - Old JVM version (doesn't work)

---

## Quick Start

### Option 1: Simple API
```python
from NAR_working import AddInput, Reset, ExecuteCycles, GetStats

Reset()
AddInput("<bird --> animal>. {1.0 0.9}")
ExecuteCycles(10)
result = AddInput("<bird --> animal>?")
print(result['answers'])
```

### Option 2: NAR Instance
```python
from NAR_working import NAR

nar = NAR()
nar.reset()
nar.add_input("<bird --> animal>. {1.0 0.9}")
nar.cycles(10)
stats = nar.stats()
nar.terminate()
```

---

## Performance

| Version | Startup | Status |
|---------|---------|--------|
| **Native Binary** | **8ms** ⚡ | ✅ Works perfectly |
| JVM (NAR.py) | 1500ms | ❌ Subprocess hangs |

**Speedup: 188x faster!**

---

## Converting Old Examples

To convert an old example to use native binary:

1. Change import:
   ```python
   # OLD (doesn't work)
   from NAR import AddInput, Reset

   # NEW (works!)
   from NAR_working import AddInput, Reset
   ```

2. Remove `Exit()` calls (automatic cleanup)

3. Test it!

---

## Recommended Flow

1. **Start here:** Run `test_working.py` to verify everything works
2. **Learn the API:** Read `basic_example_native.py`
3. **Build your app:** Use `NAR_working.py` as the interface
4. **See docs:** Read `README.md` for full API reference
