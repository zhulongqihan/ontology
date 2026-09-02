param(
    [int]$Port = 8787,
    [string]$StatePath = "data\flexible-engine.db",
    [switch]$NoLegacy
)

$ErrorActionPreference = "Stop"
mvn.cmd package -DskipTests
$arguments = @('admin', $Port, $StatePath)
if ($NoLegacy) {
    $arguments += '--no-legacy'
}
java -jar "reproduction-app\target\reproduction-app-0.1.0-SNAPSHOT.jar" $arguments
