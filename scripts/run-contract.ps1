$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    mvn package
    $sourceRevision = (git rev-parse HEAD).Trim()
    java "-Dreproduction.source.revision=$sourceRevision" -jar 'reproduction-app\target\reproduction-app-0.1.0-SNAPSHOT.jar' contract
}
finally {
    Pop-Location
}
