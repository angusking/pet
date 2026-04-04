param(
  [string]$RegistryHost = "crpi-hj866eohic2eueoi.cn-beijing.personal.cr.aliyuncs.com",
  [string]$RegistryNamespace = "gj_pet",
  [string]$RepositoryPrefix = "pet"
)

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

function Read-MenuChoice {
  param(
    [Parameter(Mandatory = $true)][string]$Prompt,
    [Parameter(Mandatory = $true)][string[]]$AllowedValues
  )
  $value = (Read-Host $Prompt).Trim()
  if ($AllowedValues -notcontains $value) {
    Fail "Invalid option: $value"
  }
  return $value
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
$choice = Read-MenuChoice -Prompt "Enter option [1/2/3/4]" -AllowedValues @("1", "2", "3", "4")

switch ($choice) {
  "1" { $targets = @("aiservice") }
  "2" { $targets = @("backend") }
  "3" { $targets = @("frontend") }
  "4" { $targets = @("aiservice", "backend", "frontend") }
  default { Fail "Invalid option: $choice" }
}

$version = (Read-Host "Enter version tag (example: v0.2.0 or 20260404-01)").Trim()
if ([string]::IsNullOrWhiteSpace($version)) {
  Fail "Version tag cannot be empty."
}

Write-Host ""
Write-Host "Select artifact action after build:" -ForegroundColor Yellow
Write-Host "  1) export tar only"
Write-Host "  2) push to Aliyun registry only"
Write-Host "  3) both"
$artifactChoice = Read-MenuChoice -Prompt "Enter option [1/2/3]" -AllowedValues @("1", "2", "3")

$shouldExport = $artifactChoice -in @("1", "3")
$shouldPush = $artifactChoice -in @("2", "3")

if ($shouldPush) {
  Write-Host ""
  Write-Host "Aliyun Container Registry config:" -ForegroundColor Yellow
  Write-Host "  host      : $RegistryHost"
  Write-Host "  namespace : $RegistryNamespace"
  Write-Host "  repo rule : $RepositoryPrefix-<component>"
  Write-Host "  example   : $RegistryHost/$RegistryNamespace/$RepositoryPrefix-aiservice:$version"
  Write-Host "Note: if the target repository path does not exist yet, Aliyun usually creates it on first push." -ForegroundColor DarkYellow

  $customRegistryHost = (Read-Host "Registry host (Press Enter to keep default)").Trim()
  if (-not [string]::IsNullOrWhiteSpace($customRegistryHost)) {
    $RegistryHost = $customRegistryHost
  }

  $customRegistryNamespace = (Read-Host "Registry namespace (Press Enter to keep default)").Trim()
  if (-not [string]::IsNullOrWhiteSpace($customRegistryNamespace)) {
    $RegistryNamespace = $customRegistryNamespace
  }

  $customRepositoryPrefix = (Read-Host "Repository prefix (Press Enter to keep default)").Trim()
  if (-not [string]::IsNullOrWhiteSpace($customRepositoryPrefix)) {
    $RepositoryPrefix = $customRepositoryPrefix
  }
}

if ($shouldExport) {
  New-Item -ItemType Directory -Force -Path $exportDir | Out-Null
}

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
  $tarName = "$imageBase-$version.tar"
  $tarPath = Join-Path $exportDir $tarName
  $remoteRepository = "$RegistryHost/$RegistryNamespace/$RepositoryPrefix-$target"
  $remoteVersionTag = "$remoteRepository`:$version"

  Info "Building $target image..."
  Push-Location $contextDir
  try {
    Invoke-DockerCommand -Args @("build", "-t", $versionTag, ".")
  } finally {
    Pop-Location
  }

  if ($shouldExport) {
    Info "Exporting image to $tarPath"
    Invoke-DockerCommand -Args @("save", "-o", $tarPath, $versionTag)
  }

  if ($shouldPush) {
    Info "Tagging Aliyun image: $remoteVersionTag"
    Invoke-DockerCommand -Args @("tag", $versionTag, $remoteVersionTag)

    Info "Pushing Aliyun image: $remoteVersionTag"
    Invoke-DockerCommand -Args @("push", $remoteVersionTag)
  }

  Success "$target done: $versionTag"
  if ($shouldExport) {
    Write-Host "      tar: $tarPath"
  }
  if ($shouldPush) {
    Write-Host "      aliyun: $remoteVersionTag"
  }
}

Write-Host ""
Success "All selected targets completed."
if ($shouldExport) {
  Write-Host "Export directory: $exportDir"
}
[void](Read-Host "Press Enter to exit")
