$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# Build the Vue sources before starting the server. Node/npm are expected to be
# selected by NVM in the shell that launches this script.
$node = Get-Command node -ErrorAction SilentlyContinue
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npm) {
    $npm = Get-Command npm -ErrorAction SilentlyContinue
}
if (-not $node -or -not $npm) {
    throw 'Node.js/npm is unavailable. Run nvm use <version> (Node 20.19+ or 22.12+) and start run.ps1 again.'
}

Push-Location (Join-Path $root 'frontend')
try {
    & $npm.Source run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

# The geometry module uses managed Maven dependencies (Jackson, JTS and
# GeographicLib). Prefer the checked-in wrapper so callers do not need a global
# Maven installation.
$wrapper = Join-Path $root 'mvnw.cmd'
if (Test-Path -LiteralPath $wrapper) {
    & $wrapper -q -DskipTests compile exec:java
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    & mvn -q -DskipTests compile exec:java
} else {
    throw 'Maven is required. Run mvnw.cmd after the Maven wrapper is generated, or install Maven.'
}
