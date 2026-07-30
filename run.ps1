$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

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
