#!/usr/bin/env bash
set -euo pipefail

echo "[1/3] git submodule init/update"
git submodule update --init --recursive

echo "[2/3] uv sync"
uv sync

echo "[3/3] quick check"
uv run vibe --help

echo "done"
