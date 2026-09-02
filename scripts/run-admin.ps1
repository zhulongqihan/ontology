param(
    [int]$Port = 8787,
    [string]$StatePath = "data\flexible-engine.db"
)

$ErrorActionPreference = "Stop"
mvn.cmd package -DskipTests
java -jar "reproduction-app\target\reproduction-app-0.1.0-SNAPSHOT.jar" admin $Port $StatePath
