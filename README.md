# ONA-Clojure: OpenNARS for Applications (Clojure Implementation)

A Clojure re-implementation of OpenNARS for Applications (ONA), focusing on the NAL 6-8 sensorimotor base with differential testing against the C reference implementation.

## Project Status

**Phase 1: Foundation & Shell Interface** (In Progress)

- ✅ Project structure and dependencies
- ✅ Basic data structures (Truth, Term, Concept)
- ✅ Shell REPL interface with core commands
- ✅ Differential testing infrastructure
- 🚧 NAL 7 temporal reasoning (next)
- ⏳ NAL 8 procedural reasoning
- ⏳ Full cycle integration

## Prerequisites

- **Clojure 1.11+** and Clojure CLI tools
- **C ONA** (parent directory): For generating golden test outputs
- Java 11+ (for running Clojure)

## Quick Start

### 1. Install Dependencies

```bash
clj -P  # Download dependencies
```

### 2. Run the Shell

```bash
clj -M:ona shell
```

You should see:
```
OpenNARS for Applications (Clojure)
Type Narsese or commands. Use 'quit' to exit.
```

### 3. Try Some Commands

```narsese
*volume=100
<bird --> animal>. {1.0 0.9}
*concepts
*stats
quit
```

## Differential Testing

The project includes a differential testing framework that compares Clojure output against the C ONA reference implementation.

### Generate Golden Outputs

First, generate reference outputs from C ONA:

```bash
cd ona-clojure
./scripts/generate_golden.sh
```

This creates golden outputs in `test/ona/differential/golden/`.

### Run Differential Tests

Compare Clojure output to golden outputs:

```bash
# Test all files
./scripts/run_differential.sh

# Test specific file
./scripts/run_differential.sh test/ona/differential/fixtures/01-temporal-basic.nal
```

### Add New Tests

1. Create a `.nal` file in `test/ona/differential/fixtures/`
2. Run `./scripts/generate_golden.sh` to create golden output
3. Run `./scripts/run_differential.sh` to test

Example test file structure:
```narsese
*reset
*volume=100
*motorbabbling=false
*currenttime=1

// Your test scenario
<a --> x>. :|:
10
*concepts
*stats
```

## Shell Commands

### Introspection Commands

- `*concepts` - Dump all concepts with beliefs and implications
- `*stats` - Print system statistics
- `*cycling_belief_events` - Show belief event queue (TODO)
- `*cycling_goal_events` - Show goal event queue (TODO)

### Configuration Commands

- `*volume=N` - Set output verbosity (0-100)
- `*reset` - Clear all memory
- `*currenttime=N` - Set system time
- `*stampid=N` - Set stamp ID base
- `*motorbabbling=false/true` - Enable/disable motor babbling

### Input

- `<term>. {f c}` - Belief with truth value
- `<term>. :|:` - Temporal belief
- `<term>!` - Goal
- `<term>?` - Question
- `N` - Execute N cycles
- `// comment` - Comment (echoed)

## Project Structure

```
ona-clojure/
├── deps.edn                 # Dependencies
├── src/ona/
│   ├── core.clj            # NAR state and concepts
│   ├── shell.clj           # REPL interface
│   ├── term.clj            # Term representation
│   └── truth.clj           # Truth value functions
├── test/ona/
│   └── differential/
│       ├── fixtures/       # Test .nal files
│       ├── golden/         # C ONA reference outputs
│       └── clojure/        # Clojure outputs
└── scripts/
    ├── generate_golden.sh  # Generate reference outputs
    └── run_differential.sh # Run differential tests
```

## Development Workflow

### 1. TDD with Differential Tests

For each new feature:

1. Write a `.nal` test file with the desired behavior
2. Generate golden output: `./scripts/generate_golden.sh`
3. Implement the feature in Clojure
4. Test: `./scripts/run_differential.sh`
5. Iterate until outputs match

### 2. REPL-Driven Development

```bash
clj -M:repl
```

```clojure
(require '[ona.shell :as shell])
(require '[ona.core :as core])
(require '[ona.truth :as truth])

;; Test truth functions
(truth/revision (truth/make-truth 0.9 0.8)
                (truth/make-truth 0.8 0.7))

;; Test concept creation
(def state (core/init-state))
(def term (ona.term/make-term "<bird --> animal>"))
(core/get-concept state term)
```

### 3. Running Tests

```bash
clj -M:test  # Run all tests (when unit tests added)
```

## Implementation Strategy

### Phase 1: Foundation (Weeks 1-2) ✅ In Progress

- [x] Shell REPL with core commands
- [x] Basic data structures
- [x] Concept storage
- [x] Differential testing framework
- [ ] Narsese parser (full)
- [ ] Truth value calculations (all 30+ functions)

### Phase 2: NAL 7 - Temporal (Weeks 3-6)

- [ ] Temporal projection
- [ ] Sequence formation
- [ ] Implication learning
- [ ] Forward prediction
- [ ] Backward chaining

### Phase 3: NAL 8 - Procedural (Weeks 7-10)

- [ ] Decision making
- [ ] Operation execution
- [ ] Anticipation
- [ ] Motor babbling

### Phase 4: Integration (Weeks 11-12)

- [ ] Full cycle implementation
- [ ] Priority queues
- [ ] Threshold adaptation
- [ ] Complete test suite

## Documentation

Comprehensive research documentation is available in `../research/`:

- `00-overview.md` - Architecture overview
- `01-data-structures/` - Data structure specs
- `02-algorithms/` - NAL 6-8 algorithms
- `03-control-flow/` - Cycle phases
- `08-theory/` - Design rationale
- `09-validation/` - Benchmarks
- `10-tuning/` - Hyperparameters

## Performance

Using `org.clojure/data.priority-map` for efficient priority queues from the start.

Optimization priorities:
1. **Correctness first** - Match C ONA behavior exactly
2. **Profiling** - Identify hot paths
3. **Optimization** - Targeted performance improvements

## Contributing

This is a research/educational project. The goal is to:

1. Validate understanding of ONA through re-implementation
2. Enable functional programming approach to NARS
3. Provide clear, documented codebase for learning

## License

Same as OpenNARS-for-Applications (MIT)

## Acknowledgments

- Original ONA by Patrick Hammer, Tony Lofthouse, and Pei Wang
- Research documentation based on ONA C codebase and Hammer et al. (2022)
- Differential testing approach inspired by compiler testing methodologies
