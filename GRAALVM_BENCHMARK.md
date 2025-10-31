# GraalVM Native Image Benchmark Results

## Build Information

**Date**: October 31, 2025
**GraalVM Version**: GraalVM CE 22.0.2+9.1
**Platform**: macOS (darwin-aarch64)
**Build Time**: 41.9 seconds

## Binary Statistics

| Metric | Value |
|--------|-------|
| **Binary Size** | 44 MB |
| **Code Area** | 19.30 MB (43.78%) |
| **Image Heap** | 24.11 MB (54.69%) |
| **Other Data** | 690.70 KB (1.53%) |
| **Reachable Types** | 9,163 (86.8% of total) |
| **Reachable Methods** | 40,689 (59.5% of total) |
| **Peak Build Memory** | 2.13 GB |

## Performance Comparison: Native vs JVM

### Startup + Execution Time

**Test**: Process 30 reasoning cycles with 3 beliefs

| Runtime | Time | Speedup |
|---------|------|---------|
| **Native Binary** | **13 ms** | **72.8x faster** |
| JVM (Clojure) | 946 ms | baseline |

### Memory Usage

| Runtime | Memory | Reduction |
|---------|--------|-----------|
| **Native Binary** | **14 MB** | **17.6x less** |
| JVM (Clojure) | 249 MB | baseline |

### Output Verification

✅ **Outputs identical** - Native binary produces bit-for-bit identical results to JVM version

## Key Advantages

### 1. **Instant Startup**
- Native: 13ms total execution time
- JVM: 946ms (most spent on JVM startup and classloading)
- **Perfect for embedded systems and CLI tools**

### 2. **Low Memory Footprint**
- Native uses only 14MB RAM
- **Suitable for resource-constrained environments**
- Can run on IoT devices, microcontrollers with sufficient memory

### 3. **Predictable Performance**
- No JIT warmup required
- No garbage collection pauses during startup
- Consistent behavior across runs

### 4. **Standalone Distribution**
- Single 44MB binary (no JVM required)
- No dependency management
- Easy deployment

## Detailed Test Configuration

### Test Scenario
```narsese
*reset
*volume=0
*motorbabbling=false
bird.
10
animal.
10
flies. :|:
10
*volume=100
*stats
quit
```

### Hardware
- **System**: macOS (Apple Silicon M-series)
- **Available Memory**: 16 GB
- **CPU Cores**: 8 (all used during build)

### Build Configuration

**GraalVM Flags Used**:
```bash
--no-fallback                              # No fallback to JVM
--initialize-at-build-time                 # Initialize classes at build time
--report-unsupported-elements-at-runtime   # Report issues at runtime
-H:+ReportExceptionStackTraces             # Detailed error reporting
-H:Optimize=2                              # Maximum optimization level
-H:+PrintClassInitialization               # Debug class loading
--enable-url-protocols=http,https          # Network support
```

**Reflection Configuration**: All Clojure runtime classes pre-configured for reflection

## Build Process

The build process is fully automated via `scripts/build_native.sh`:

1. **Clean** - Remove previous builds
2. **AOT Compile** - Compile Clojure namespaces to bytecode
3. **Create Uberjar** - Package all dependencies (~6MB)
4. **Native Image** - GraalVM compilation (38 seconds)

### Build Performance

| Phase | Time | Memory |
|-------|------|--------|
| Analysis | 14.3s | 720 MB |
| Universe Building | 1.9s | 860 MB |
| Method Parsing | 1.6s | 940 MB |
| Inlining | 1.1s | 1.03 GB |
| Compilation | 13.8s | 1.11 GB |
| Layout | 1.7s | 1.32 GB |
| Image Creation | 3.2s | 620 MB |
| **Total** | **41.9s** | **2.13 GB peak** |

## Comparison with C ONA

| Metric | ONA-Clojure Native | C ONA | Notes |
|--------|-------------------|-------|-------|
| Binary Size | 44 MB | ~200 KB | Native includes full runtime |
| Startup Time | 13 ms | <1 ms | C still faster for cold start |
| Memory Usage | 14 MB | ~2-5 MB | Native includes heap image |
| Development Speed | High | Medium | Clojure = rapid development |
| Type Safety | Runtime | Compile-time | Trade-off |
| Code Size | ~10,500 LOC | ~20,000 LOC | Clojure more concise |

### When to Use Each

**ONA-Clojure Native**:
- Development and experimentation
- Integration with JVM ecosystem
- When 13ms startup is acceptable
- When 14MB memory overhead is acceptable
- When easy debugging/REPL workflow is valued

**C ONA**:
- Absolute minimum resource usage
- Sub-millisecond startup required
- Embedded systems with <10MB RAM
- Real-time hard constraints
- Maximum performance critical

## Lessons Learned

### 1. GraalVM Works Great with Clojure
Despite Clojure's dynamic nature, GraalVM successfully compiled the entire codebase without major issues. The key was:
- Proper `:gen-class` annotations
- Reflection configuration for Clojure runtime
- AOT compilation before native-image

### 2. Build Time is Reasonable
41.9 seconds for a complete native build is acceptable for:
- Development workflows (compile once, test many times)
- CI/CD pipelines
- Release builds

### 3. Size Trade-off is Worth It
The 44MB binary includes:
- Full Clojure runtime
- Complete ONA implementation
- All dependencies
- Pre-initialized heap with compiled code

This is still **smaller than most Electron apps** and provides true native performance.

### 4. Memory Reduction is Dramatic
Reducing memory from 249MB (JVM) to 14MB (native) enables:
- Running on Raspberry Pi and similar devices
- Deploying many instances on single machine
- Integration into larger systems without memory pressure

## Future Optimizations

### Potential Improvements

1. **Further Size Reduction**
   - Profile unused classes and exclude them
   - Use `--gc=G1` instead of Serial GC for better scalability
   - Strip debug symbols in production builds

2. **Even Faster Startup**
   - Pre-initialize more classes at build time
   - Optimize class initialization order
   - Use Profile-Guided Optimizations (PGO)

3. **Static Linking** (Linux only)
   - Build fully static binary with musl libc
   - Enables deployment on minimal containers

4. **Cross-Compilation**
   - Build Linux binary on macOS
   - Build Windows binary from Linux
   - Distribute multi-platform releases

## Conclusion

GraalVM native compilation of ONA-Clojure is a **complete success**:

✅ **72x faster startup** (13ms vs 946ms)
✅ **18x less memory** (14MB vs 249MB)
✅ **Identical behavior** to JVM version
✅ **Standalone distribution** (no JVM required)
✅ **Reasonable build time** (41.9 seconds)
✅ **Acceptable binary size** (44MB)

The native binary provides the **best of both worlds**:
- **Development**: Keep using Clojure's REPL, immutable data structures, and functional abstractions
- **Deployment**: Ship a fast, lightweight, standalone binary

This makes ONA-Clojure suitable for:
- IoT and embedded devices (with sufficient memory)
- CLI tools and automation scripts
- Microservices and serverless functions
- Desktop applications
- Any scenario where startup time matters

**Recommendation**: Use the native binary for all production deployments unless you specifically need JVM interoperability or dynamic code loading.
