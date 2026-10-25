param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$Serial = 'emulator-5554',
    [string]$Evidence = 'docs/evidence/storage-2026-10-25'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repo
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "ADB not found: $adb" }
if (-not $Serial.StartsWith('emulator-')) { throw 'Use a test emulator for this scenario.' }
$env:JAVA_HOME = $Jdk
New-Item -ItemType Directory -Force -Path $Evidence | Out-Null

function Invoke-Adb {
    $result = & $adb -s $Serial @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    $result
}

function Invoke-Test([string]$Class, [string]$Log, [string]$Phase = '', [string]$CachePhase = '') {
    $arguments = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', "vn.edu.usth.covidstats.$Class")
    if ($Phase) { $arguments += @('-e', 'storagePhase', $Phase) }
    if ($CachePhase) { $arguments += @('-e', 'm3CachePhase', $CachePhase) }
    $arguments += 'vn.edu.usth.covidstats.test/androidx.test.runner.AndroidJUnitRunner'
    $output = Invoke-Adb @arguments
    $output | Set-Content -Encoding utf8 -Path (Join-Path $Evidence $Log)
    if (($output -join "`n") -notmatch 'OK \(\d+ tests?\)' -or
        ($output -join "`n") -match 'FAILURES|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Test failed: $Class. See $Log"
    }
    Write-Output "$Class : PASS"
}

& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline *> (Join-Path $Evidence 'build-test.txt')
if ($LASTEXITCODE -ne 0) { throw 'Gradle failed; see build-test.txt.' }
Invoke-Adb install -r 'app/build/outputs/apk/debug/app-debug.apk'
Invoke-Adb install -r 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$wifi = (Invoke-Adb shell settings get global wifi_on | Out-String).Trim()
$mobile = (Invoke-Adb shell settings get global mobile_data | Out-String).Trim()
try {
    Invoke-Adb shell svc wifi enable
    Invoke-Adb shell svc data enable
    Invoke-Test 'StorageContractTest' 'storage-contract.txt'
    Invoke-Test 'M3RepositoryTest' 'repository.txt'
    Invoke-Test 'M4OfflineFileCacheTest' 'offline-file-cache.txt'
    Invoke-Test 'WatchlistIntegrationTest#seedOnline' 'seed-online.txt' 'seedOnline'
    Invoke-Adb shell am force-stop vn.edu.usth.covidstats
    Invoke-Adb shell svc wifi disable
    Invoke-Adb shell svc data disable
    Start-Sleep -Seconds 2
    Invoke-Test 'WatchlistIntegrationTest#readOfflineAndSwipe' 'read-offline-swipe.txt' 'readOfflineAndSwipe'
    Invoke-Test 'M3PersistentCacheTest' 'persistent-cache.txt' '' 'read'
    Invoke-Adb shell svc wifi enable
    Invoke-Adb shell svc data enable
    Start-Sleep -Seconds 3
    Invoke-Test 'WatchlistIntegrationTest#recoverOnline' 'recover-online.txt' 'recoverOnline'
    Invoke-Test 'WatchlistIntegrationTest#pullRefreshAndVietnamese' 'pull-refresh-vi.txt' 'pullRefreshAndVietnamese'
} finally {
    try {
        Invoke-Test 'WatchlistIntegrationTest#restoreOriginal' 'restore-original.txt' 'restoreOriginal'
    } finally {
        Invoke-Adb shell svc wifi $(if ($wifi -eq '1') { 'enable' } else { 'disable' })
        Invoke-Adb shell svc data $(if ($mobile -eq '1') { 'enable' } else { 'disable' })
        Invoke-Adb logcat -d -s StorageEvidence M3CacheProof M3Data |
            Set-Content -Encoding utf8 -Path (Join-Path $Evidence 'device-proof.txt')
    }
}
foreach ($name in @('01-empty', '02-detail-stars-in-real-tab', '03-after-force-stop-offline',
                    '04-swiped-vn-refreshed-offline', '05-back-online', '06-watchlist-vi')) {
    Invoke-Adb pull "/sdcard/Android/data/vn.edu.usth.covidstats/files/storage-evidence/$name.png" (Join-Path $Evidence "$name.png")
}
Copy-Item -LiteralPath 'app/build/reports/lint-results-debug.txt' -Destination (Join-Path $Evidence 'lint.txt')
Write-Output 'Storage verification complete. Original watchlist and network settings restored.'
