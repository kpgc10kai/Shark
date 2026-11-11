param(
    [ValidateSet("Probe", "Plant", "Both")]
    [string]$Mode = "Both",
    [string]$NdkRoot = $env:ANDROID_NDK_HOME
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$repo = Split-Path -Parent $root
$source = Join-Path $repo "exploit\src\t61269_plant.c"
$outDir = Join-Path $PSScriptRoot "bin"

if (-not $NdkRoot) {
    $candidate = Join-Path $PSScriptRoot "tools\android-ndk-r28c"
    if (Test-Path -LiteralPath $candidate) {
        $NdkRoot = $candidate
    }
}
if (-not $NdkRoot) {
    throw "NDK not found. Set ANDROID_NDK_HOME, or extract it to ports\v2505a\tools\android-ndk-r28c."
}

$cc = Join-Path $NdkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android31-clang.cmd"
if (-not (Test-Path -LiteralPath $cc)) {
    throw "NDK clang not found: $cc"
}

New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Build-One([string]$Name, [string[]]$ExtraFlags) {
    $target = Join-Path $outDir $Name
    $ccArgs = @("-static", "-O2", "-pthread", "-DV2505A") + $ExtraFlags +
        @("-o", $target, $source)
    & $cc @ccArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Compile failed: $Name"
    }
    Get-Item -LiteralPath $target | Select-Object FullName, Length
}

if ($Mode -in @("Probe", "Both")) {
    Build-One "v2505a_probe" @("-DPROBE_ONLY")
}
if ($Mode -in @("Plant", "Both")) {
    Build-One "v2505a_plant" @()
}

Write-Output "BUILD_V2505A_DONE mode=$Mode"
