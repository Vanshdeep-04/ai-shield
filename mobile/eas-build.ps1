# Runs an EAS command for AI Shield safely.
#
# Why: this project folder sits inside a larger git repository (the user's home folder). By default
# EAS packages the project from the git root, which could upload unrelated files. EAS_NO_VCS=1 makes
# EAS package only this mobile folder (filtered by .easignore), and a project-local temp folder keeps
# EAS from scanning the system temp directory.
#
# Usage (from the mobile folder):
#   .\eas-build.ps1                                  # development build for Android
#   .\eas-build.ps1 build --profile preview --platform android
#   .\eas-build.ps1 credentials -p android           # view keystore / SHA-1 fingerprint

$ErrorActionPreference = 'Stop'
$mobile = $PSScriptRoot
$tools = Join-Path (Split-Path $mobile -Parent) '.tools'
$tmp = Join-Path $tools 'tmp'
New-Item -ItemType Directory -Force $tmp | Out-Null

$env:EAS_NO_VCS = '1'
$env:EAS_PROJECT_ROOT = $mobile
$env:TMP = $tmp
$env:TEMP = $tmp
$env:npm_config_cache = Join-Path $tools 'npm-cache'

$easArgs = if ($args.Count -gt 0) { $args } else { @('build', '--profile', 'development', '--platform', 'android') }

Push-Location $mobile
try {
    npx eas-cli@latest @easArgs
} finally {
    Pop-Location
}
