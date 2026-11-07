#!/bin/bash
# Regenerates res/raw/note0..35.wav (48 kHz, -3 dBFS headroom, faded tails)
# from the lossless files in "Processed audio files".
# Usage: ./copy-and-rename-audio-files.sh
set -e
cd "$(dirname "$0")"
python3 -I make-48k-wav.py app/src/main/res/raw
