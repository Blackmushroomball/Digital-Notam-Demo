$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $root 'target\classes'
Set-Location $root
New-Item -ItemType Directory -Force $out | Out-Null
$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object FullName
javac --release 21 -encoding UTF-8 -cp (Join-Path $root 'lib\*') -d $out $sources
java -cp "$out;$root\lib\*" com.example.digitalnotam.DigitalNotamApplication
