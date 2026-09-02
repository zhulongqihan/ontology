param(
    [int]$Port = 8787,
    [string]$StatePath = "data\engine-state.json"
)

$ErrorActionPreference = "Stop"
mvn.cmd package -DskipTests
java -jar "reproduction-app\target\reproduction-app-0.1.0-SNAPSHOT.jar" admin $Port $StatePath
