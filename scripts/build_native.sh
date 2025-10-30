#!/bin/bash
set -e

echo "================================================"
echo "Building ONA Clojure Native Binary with GraalVM"
echo "================================================"

# Check if GraalVM native-image is available
if ! command -v native-image &> /dev/null; then
    echo "Error: native-image not found. Please install GraalVM and native-image."
    echo ""
    echo "Installation:"
    echo "  1. Download GraalVM from https://www.graalvm.org/downloads/"
    echo "  2. Set JAVA_HOME to GraalVM directory"
    echo "  3. Install native-image: gu install native-image"
    exit 1
fi

echo ""
echo "Step 1: Checking GraalVM version..."
java -version
echo ""

echo "Step 2: Cleaning previous builds..."
rm -rf target classes ona ona.jar
mkdir -p target classes

echo ""
echo "Step 3: Compiling Clojure code to classes..."
clojure -M:native -e "(compile 'ona.shell)" || {
    echo "Compilation failed. Trying AOT compilation..."
    clojure -X:deps prep
    clojure -M -e "
    (binding [*compile-path* \"classes\"]
      (compile 'ona.core)
      (compile 'ona.term)
      (compile 'ona.truth)
      (compile 'ona.event)
      (compile 'ona.implication)
      (compile 'ona.inference)
      (compile 'ona.cycle)
      (compile 'ona.decision)
      (compile 'ona.operation)
      (compile 'ona.prediction)
      (compile 'ona.query)
      (compile 'ona.config)
      (compile 'ona.shell))
    "
}

echo ""
echo "Step 4: Creating uberjar..."
clojure -X:deps prep
clojure -T:build uber || {
    echo "Uberjar build failed. Trying manual jar creation..."
    # Fallback: manual jar creation
    cd classes
    jar cvfe ../ona.jar ona.shell *
    cd ..
    jar uf ona.jar -C resources .
}

echo ""
echo "Step 5: Building native image..."
echo "This may take several minutes..."

native-image \
    --no-fallback \
    --initialize-at-build-time \
    --report-unsupported-elements-at-runtime \
    -H:+ReportExceptionStackTraces \
    -H:ConfigurationFileDirectories=resources/META-INF/native-image/ona-clojure \
    -H:Name=ona \
    -H:+PrintClassInitialization \
    -H:Log=registerResource: \
    --verbose \
    --enable-url-protocols=http,https \
    --allow-incomplete-classpath \
    -jar ona.jar \
    ona

echo ""
echo "================================================"
echo "Build complete!"
echo "================================================"
echo ""
echo "Binary location: ./ona"
echo "Binary size: $(du -h ona | cut -f1)"
echo ""
echo "Test it:"
echo "  ./ona shell"
echo ""
