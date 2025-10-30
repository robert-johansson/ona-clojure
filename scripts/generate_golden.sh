#!/bin/bash
# Generate golden outputs from C ONA for differential testing

set -e

# Configuration
C_ONA_PATH="../NAR"
TEST_DIR="test/ona/differential/fixtures"
GOLDEN_DIR="test/ona/differential/golden"

# Create golden output directory
mkdir -p "$GOLDEN_DIR"

echo "Generating golden outputs from C ONA..."
echo "========================================="

# Process each .nal test file
for nal_file in "$TEST_DIR"/*.nal; do
    if [ -f "$nal_file" ]; then
        basename=$(basename "$nal_file" .nal)
        output_file="$GOLDEN_DIR/${basename}_golden.txt"

        echo "Processing: $basename"

        # Run C ONA and capture output
        cat "$nal_file" | "$C_ONA_PATH" shell > "$output_file" 2>&1

        echo "  -> Generated: $output_file"
    fi
done

echo ""
echo "Golden output generation complete!"
echo "Files in: $GOLDEN_DIR"
