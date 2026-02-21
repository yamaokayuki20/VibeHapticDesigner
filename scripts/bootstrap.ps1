param(
    [switch]$NoSync
)

$ErrorActionPreference = "Stop"

Write-Host "[1/3] git submodule init/update"
git submodule update --init --recursive

if (-not $NoSync) {
    Write-Host "[2/3] uv sync"
    uv sync
}

Write-Host "[3/3] quick check"
uv run vibe --help

Write-Host "done"
