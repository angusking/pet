param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($message) {
  Write-Host "[ERROR] $message" -ForegroundColor Red
  exit 1
}

function Info($message) {
  Write-Host "[INFO] $message" -ForegroundColor Cyan
}

function Success($message) {
  Write-Host "[OK] $message" -ForegroundColor Green
}

function Invoke-DockerCommand {
  param(
    [Parameter(Mandatory = $true)][string[]]$Args
  )
  & docker @Args
  if ($LASTEXITCODE -ne 0) {
    Fail ("docker command failed: docker " + ($Args -join " "))
  }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$aiServiceDir = Join-Path $repoRoot "AIService"
$frontendDir = Join-Path $repoRoot "frontend"
$backendDir = Join-Path $repoRoot "backend"
$exportRoot = Join-Path $PSScriptRoot "exports"
$dateFolder = Get-Date -Format "yyyy-MM-dd"
$exportDir = Join-Path $exportRoot $dateFolder

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Fail "docker not found. Please install Docker Desktop and ensure docker is in PATH."
}

Write-Host ""
Write-Host "Select build target:" -ForegroundColor Yellow
Write-Host "  1) aiservice"
Write-Host "  2) backend"
Write-Host "  3) frontend"
Write-Host "  4) all"
$choice = (Read-Host "Enter option [1/2/3/4]").Trim()

switch ($choice) {
  "1" { $targets = @("aiservice") }
  "2" { $targets = @("backend") }
  "3" { $targets = @("frontend") }
  "4" { $targets = @("aiservice", "backend", "frontend") }
  default { Fail "Invalid option: $choice" }
}

$version = (Read-Host "Enter version tag (example: v1.0.0 or 20260226-01)").Trim()
if ([string]::IsNullOrWhiteSpace($version)) {
  Fail "Version tag cannot be empty."
}

New-Item -ItemType Directory -Force -Path $exportDir | Out-Null

foreach ($target in $targets) {
  switch ($target) {
    "aiservice" {
      $contextDir = $aiServiceDir
      $imageBase = "pet-aiservice"
    }
    "backend" {
      $contextDir = $backendDir
      $imageBase = "pet-backend"
    }
    "frontend" {
      $contextDir = $frontendDir
      $imageBase = "pet-frontend"
    }
    default {
      Fail "Unsupported target: $target"
    }
  }

  if (-not (Test-Path $contextDir)) {
    Fail "Build context not found: $contextDir"
  }

  $versionTag = "$imageBase`:$version"
  $latestTag = "$imageBase`:latest"
  $tarName = "$imageBase-$version.tar"
  $tarPath = Join-Path $exportDir $tarName

  Info "Building $target image..."
  Push-Location $contextDir
  try {
    Invoke-DockerCommand -Args @("build", "-t", $versionTag, "-t", $latestTag, ".")
  } finally {
    Pop-Location
  }

  Info "Exporting image to $tarPath"
  Invoke-DockerCommand -Args @("save", "-o", $tarPath, $versionTag)

  Success "$target done: $versionTag"
  Write-Host "      tar: $tarPath"
}

Write-Host ""
Success "All selected targets completed."
Write-Host "Export directory: $exportDir"
[void](Read-Host "Press Enter to exit")
