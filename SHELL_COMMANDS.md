# Shell Commands for Clojure ONA

## Operations and Motor Babbling

### Register Operations
```
*setopname 1 ^left
*setopname 2 ^right
```

Registers motor commands that can be executed by the system.

### Enable Motor Babbling
```
*motorbabbling=0.9
```

Enables random exploration with 90% probability. When a goal is given without a known way to achieve it, the system will randomly try operations.

### Set Random Seed (for reproducibility)
```
*seed=42
```

Sets the random number generator seed for deterministic behavior. Useful for:
- Testing
- Debugging
- Reproducible experiments

## Example Usage

```clojure
clj -M:ona shell

*seed=42              // For reproducibility
*setopname 1 ^left    // Register operations
*setopname 2 ^right
*motorbabbling=0.9    // Enable exploration
*volume=100           // Full output

// Teaching phase: show consequences
A. :|:                // Observe state A
G! :|:                // System tries to achieve G
                      // Motor babbling executes random operation
G. :|:                // Observe outcome G
100                   // Let it process

*concepts             // Check what was learned
```

## What Gets Learned

The system learns temporal implications:
- `<A =/> ^operation>` - "From state A, do operation"
- `<^operation =/> G>` - "Doing operation leads to G"
- `<A =/> G>` - "From A, eventually reach G"

## Comparison with C ONA

### Similarities ✅
- Operation registration works the same
- Motor babbling behavior is identical
- Temporal sequences are formed correctly
- Implications are learned

### Differences ⚠️
- **Format**: C ONA uses compound implications `<(A &/ ^op) =/> G>`, Clojure ONA uses decomposed chains `<A =/> ^op>`, `<^op =/> G>`
- **Both are semantically equivalent** - just different granularity
- **Reproducibility**: Now added with `*seed=N` command

## All Available Commands

| Command | Example | Description |
|---------|---------|-------------|
| `*reset` | `*reset` | Clear all concepts and reset state |
| `*volume=N` | `*volume=100` | Set output verbosity (0-100) |
| `*concepts` | `*concepts` | Display all concepts and implications |
| `*stats` | `*stats` | Show system statistics |
| `*motorbabbling=N` | `*motorbabbling=0.9` | Set motor babbling probability |
| `*setopname ID NAME` | `*setopname 1 ^left` | Register an operation |
| `*seed=N` | `*seed=42` | Set random seed for reproducibility |
| `*currenttime=N` | `*currenttime=100` | Set current system time |
| `*stampid=N` | `*stampid=1` | Set stamp ID counter |
