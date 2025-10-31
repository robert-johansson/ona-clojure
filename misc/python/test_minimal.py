#!/usr/bin/env python3
"""Minimal test of NAR Python interface"""

import sys
sys.path.insert(0, "/Users/robert/claude/ONA/OpenNARS-for-Applications/ona-clojure/misc/python")

from NAR import AddInput, Exit

print("Test 1: Simple input")
result = AddInput("A. :|:")
print(f"Result: {len(result['raw'])} chars")

print("\nTest 2: Execute cycles")
result = AddInput("5")
print(f"Result: {len(result['raw'])} chars")

print("\nTest 3: Query")
result = AddInput("A?")
print(f"Result: {len(result['raw'])} chars")
print(f"Answers: {result.get('answers', [])}")

print("\nDone!")
Exit()
