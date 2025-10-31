#!/bin/bash
# Test native binary against golden C ONA output

set -e

# Configuration
TEST_DIR="test/ona/differential/fixtures"
GOLDEN_DIR="test/ona/differential/golden"
NATIVE_OUTPUT_DIR="test/ona/differential/native"

# Create output directory
mkdir -p "$NATIVE_OUTPUT_DIR"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Check if native binary exists
if [ ! -f "./ona" ]; then
    echo -e "${RED}ERROR${NC}: Native binary ./ona not found"
    echo "Run ./scripts/build_native.sh first"
    exit 1
fi

# Test a single file
test_file() {
    local nal_file=$1
    local basename=$(basename "$nal_file" .nal)
    local golden_file="$GOLDEN_DIR/${basename}_golden.txt"
    local native_file="$NATIVE_OUTPUT_DIR/${basename}_native.txt"

    echo "Testing: $basename"

    # Check if golden file exists
    if [ ! -f "$golden_file" ]; then
        echo -e "  ${YELLOW}SKIP${NC}: No golden output found"
        return 1
    fi

    # Run native ONA
    echo "  Running native ONA..."
    cat "$nal_file" | ./ona shell > "$native_file" 2>&1 || {
        echo -e "  ${RED}ERROR${NC}: Native ONA failed"
        return 1
    }

    # Compare outputs
    echo "  Comparing outputs..."

    # Compare full outputs (ignoring timing differences)
    if diff -u "$golden_file" "$native_file" > /dev/null 2>&1; then
        echo -e "  ${GREEN}PASS${NC}: Outputs match!"
        return 0
    else
        echo -e "  ${RED}FAIL${NC}: Outputs differ"
        diff -u "$golden_file" "$native_file" | head -50
        return 1
    fi
}

# Main test loop
echo "Differential Testing - Native Binary"
echo "====================================="
echo ""

passed=0
failed=0
skipped=0

for nal_file in "$TEST_DIR"/*.nal; do
    if test_file "$nal_file"; then
        ((passed++))
    else
        if [ $? -eq 1 ]; then
            ((skipped++))
        else
            ((failed++))
        fi
    fi
    echo ""
done

# Summary
echo "====================================="
echo "Summary:"
echo -e "  ${GREEN}PASSED${NC}: $passed"
echo -e "  ${RED}FAILED${NC}: $failed"
echo -e "  ${YELLOW}SKIPPED${NC}: $skipped"

if [ $failed -gt 0 ]; then
    exit 1
fi
