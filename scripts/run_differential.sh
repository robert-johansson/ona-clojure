#!/bin/bash
# Run differential test comparing Clojure ONA output to golden C ONA output

set -e

# Configuration
TEST_DIR="test/ona/differential/fixtures"
GOLDEN_DIR="test/ona/differential/golden"
CLOJURE_OUTPUT_DIR="test/ona/differential/clojure"

# Create output directory
mkdir -p "$CLOJURE_OUTPUT_DIR"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test a single file
test_file() {
    local nal_file=$1
    local basename=$(basename "$nal_file" .nal)
    local golden_file="$GOLDEN_DIR/${basename}_golden.txt"
    local clojure_file="$CLOJURE_OUTPUT_DIR/${basename}_clojure.txt"

    echo "Testing: $basename"

    # Check if golden file exists
    if [ ! -f "$golden_file" ]; then
        echo -e "  ${YELLOW}SKIP${NC}: No golden output found. Run generate_golden.sh first."
        return 1
    fi

    # Run Clojure ONA
    echo "  Running Clojure ONA..."
    cat "$nal_file" | clj -M:ona shell > "$clojure_file" 2>&1 || {
        echo -e "  ${RED}ERROR${NC}: Clojure ONA failed"
        return 1
    }

    # Extract structured outputs for comparison
    echo "  Comparing outputs..."

    # Compare *concepts output
    local golden_concepts=$(grep -A 1000 "^//*concepts" "$golden_file" | grep -B 1000 "^//*done" || true)
    local clojure_concepts=$(grep -A 1000 "^//*concepts" "$clojure_file" | grep -B 1000 "^//*done" || true)

    # Compare *stats output
    local golden_stats=$(grep -A 100 "^//*stats" "$golden_file" | grep -B 100 "^//*done" || true)
    local clojure_stats=$(grep -A 100 "^//*stats" "$clojure_file" | grep -B 100 "^//*done" || true)

    # Count differences
    local concepts_diff=$(diff <(echo "$golden_concepts") <(echo "$clojure_concepts") | wc -l)
    local stats_diff=$(diff <(echo "$golden_stats") <(echo "$clojure_stats") | wc -l)

    # Report results
    if [ "$concepts_diff" -eq 0 ] && [ "$stats_diff" -eq 0 ]; then
        echo -e "  ${GREEN}PASS${NC}: Outputs match!"
        return 0
    else
        echo -e "  ${RED}FAIL${NC}: Outputs differ"
        echo "    Concepts diff lines: $concepts_diff"
        echo "    Stats diff lines: $stats_diff"
        echo "    See detailed diff: diff $golden_file $clojure_file"
        return 1
    fi
}

# Main script
echo "Differential Testing"
echo "===================="
echo ""

# Run all tests or specific file
if [ $# -eq 0 ]; then
    # No arguments - test all .nal files
    pass_count=0
    fail_count=0
    skip_count=0

    for nal_file in "$TEST_DIR"/*.nal; do
        if [ -f "$nal_file" ]; then
            if test_file "$nal_file"; then
                ((pass_count++))
            else
                if [ -f "$GOLDEN_DIR/$(basename "$nal_file" .nal)_golden.txt" ]; then
                    ((fail_count++))
                else
                    ((skip_count++))
                fi
            fi
            echo ""
        fi
    done

    # Summary
    echo "===================="
    echo "Summary:"
    echo -e "  ${GREEN}PASSED${NC}: $pass_count"
    echo -e "  ${RED}FAILED${NC}: $fail_count"
    echo -e "  ${YELLOW}SKIPPED${NC}: $skip_count"

    if [ $fail_count -eq 0 ]; then
        exit 0
    else
        exit 1
    fi
else
    # Test specific file
    test_file "$1"
fi
