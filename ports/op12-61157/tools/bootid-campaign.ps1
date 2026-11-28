param(
  [string]$Serial = "8d8e12ea",
  [string]$Adb = "adb",
  [string]$Core = ".\ic-op12e",
  [int]$Runs = 10
)
$results = @()
$success = 0
for ($i = 1; $i -le $Runs; $i++) {
  $started = $false
  for ($attempt = 1; $attempt -le 3 -and -not $started; $attempt++) {
    & $Adb -s $Serial reboot 2>$null
    Start-Sleep 6
    $ok = $false
    for ($w = 0; $w -lt 60; $w++) {
      if ((& $Adb devices) -match "$Serial\s+device") { $ok = $true; break }
      Start-Sleep 3
    }
    if (-not $ok) { continue }
    for ($w = 0; $w -lt 30; $w++) {
      if ((& $Adb -s $Serial shell getprop sys.boot_completed) -match "1") { break }
      Start-Sleep 3
    }
    Start-Sleep 30
    $bid1 = & $Adb -s $Serial shell cat /proc/sys/kernel/random/boot_id
    & $Adb -s $Serial push $Core /data/local/tmp/ic-op12e 2>$null | Out-Null
    & $Adb -s $Serial shell "chmod 755 /data/local/tmp/ic-op12e; rm -f /data/local/tmp/ic-bid.log; cd /data/local/tmp; PAYLOAD_CMD='sh /data/local/tmp/ksu_stage.sh' setsid ./ic-op12e ksu >/data/local/tmp/ic-bid.log 2>&1 </dev/null & echo STARTED" 2>$null | Out-Null
    Start-Sleep 6
    $bid2 = & $Adb -s $Serial shell cat /proc/sys/kernel/random/boot_id
    $has = & $Adb -s $Serial shell "test -s /data/local/tmp/ic-bid.log && echo YES || echo NO"
    if (($bid1 -eq $bid2) -and ($has -match "YES")) { $started = $true }
  }
  if (-not $started) {
    $results += [pscustomobject]@{ run = $i; result = "UNSTABLE" }
    continue
  }
  Start-Sleep 20
  $g = & $Adb -s $Serial shell "grep -q KSU_LOADED /data/local/tmp/ic-bid.log && echo YES || echo NO"
  if ($g -match "YES") { $success++; $results += [pscustomobject]@{ run = $i; result = "SUCCESS" } }
  else { $results += [pscustomobject]@{ run = $i; result = "FAIL" } }
}
$results | Format-Table
"success=$success/$Runs"
