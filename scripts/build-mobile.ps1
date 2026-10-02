param([switch]$Release, [string]$GradlePath)
$ErrorActionPreference = 'Stop'
$quotaRoot = Split-Path -Parent $PSScriptRoot
if ($Release) {
    foreach ($quotaVariable in @('ANDROID_KEYSTORE_FILE','ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($quotaVariable,'Process'))) {
            throw "Release 签名尚未配置：$quotaVariable"
        }
    }
}
$quotaGradle = if ($GradlePath) { $GradlePath } else { Join-Path $quotaRoot 'gradlew.bat' }
if (-not (Test-Path -LiteralPath $quotaGradle)) { throw '找不到 Gradle；请配置 GradlePath 或使用项目 wrapper' }
$quotaTasks = if ($Release) { @('testDebugUnitTest','lintDebug','assembleRelease') } else { @('testDebugUnitTest','lintDebug','assembleDebug') }
$quotaArguments = @('--no-daemon','--console=plain','-Dorg.gradle.internal.http.connectionTimeout=15000','-Dorg.gradle.internal.http.socketTimeout=30000')
if ($env:OS -eq 'Windows_NT') {
    $quotaArguments += '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
}
Push-Location $quotaRoot
try {
    & $quotaGradle @quotaTasks @quotaArguments
    if ($LASTEXITCODE -ne 0) { throw "构建失败：退出码 $LASTEXITCODE" }
} finally { Pop-Location }
