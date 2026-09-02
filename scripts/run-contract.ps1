$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    mvn package
    java -jar 'reproduction-app\target\reproduction-app-0.1.0-SNAPSHOT.jar' contract
}
finally {
    Pop-Location
}
