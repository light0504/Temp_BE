param([switch]$Build)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$taskTools = Join-Path (Split-Path -Parent $PSScriptRoot) '.tools'
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $taskJdk = Get-ChildItem -LiteralPath $taskTools -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $taskJdk) { throw 'Install JDK 21 and Maven, or use docker compose up --build.' }
    $env:JAVA_HOME = $taskJdk.FullName
    $env:PATH = "$($taskJdk.FullName)\bin;$env:PATH"
}
if ($Build -or -not (Test-Path -LiteralPath 'target/fbus-api-1.0.0.jar')) {
    $taskMaven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($taskMaven) { & $taskMaven.Source -DskipTests package }
    else {
        $taskMavenPath = Join-Path $taskTools 'apache-maven-3.9.11/bin/mvn.cmd'
        if (-not (Test-Path -LiteralPath $taskMavenPath)) { throw 'Install Maven or use docker compose up --build.' }
        & $taskMavenPath "-Dmaven.repo.local=$(Join-Path $taskTools 'm2')" -DskipTests package
    }
    if ($LASTEXITCODE -ne 0) { throw 'Backend compilation failed.' }
}
& java -jar target/fbus-api-1.0.0.jar
