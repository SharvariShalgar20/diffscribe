<#
Scores the backend's output against the saved sample diffs.

Usage (backend and Ollama must be running):
  powershell -ExecutionPolicy Bypass -File .\eval\run-eval.ps1 -Label qwen-1.5b
  powershell -ExecutionPolicy Bypass -File .\eval\run-eval.ps1 -Label qwen-1.5b -Runs 3

Sample files are named NN-<type>-<slug>.diff; <type> is the expected change type.
Only calls /api/generate-pr-description: no GitHub calls, no side effects.
Written for Windows PowerShell 5.1 (the `powershell` command).
#>
param(
    [string]$Label = "unlabeled",
    [string]$BackendUrl = "http://localhost:8080",
    [int]$Runs = 1
)

# ConvertTo-Json in Windows PowerShell 5.1 can turn a string into {"value": ...},
# which the backend rejects with a 400. Build the JSON with .NET's serializer.
Add-Type -AssemblyName System.Web.Extensions
$serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$serializer.MaxJsonLength = [int]::MaxValue

$samplesDir = Join-Path $PSScriptRoot "samples"
$resultsDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null

$requiredHeadings = @('## What changed', '## Why', '## How to test')
$rows = @()
$bodyShown = $false

foreach ($file in Get-ChildItem -Path $samplesDir -Filter *.diff | Sort-Object Name) {
    if ($file.Name -match '^\d+-(feat|fix|refactor|docs|test|chore)-') {
        $expected = $Matches[1]
    } else {
        Write-Warning "Skipping $($file.Name): name must look like 01-feat-something.diff"
        continue
    }

    $diff = [string][System.IO.File]::ReadAllText($file.FullName)
    $json = $serializer.Serialize(@{ diffChunks = @($diff) })
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)

    if (-not $bodyShown) {
        Write-Host "Request body starts: $($json.Substring(0, [Math]::Min(45, $json.Length)))"
        Write-Host "  (should look like {""diffChunks"":[""diff --git ...  with no {""value"" in it)"
        $bodyShown = $true
    }

    for ($run = 1; $run -le $Runs; $run++) {
        Write-Host "[$($file.Name)] run $run/$Runs ..." -NoNewline
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $response = $null
        $failure = $null
        try {
            $response = Invoke-RestMethod -Uri "$BackendUrl/api/generate-pr-description" -Method Post `
                -Body $bytes -ContentType "application/json; charset=utf-8" -TimeoutSec 600
        } catch {
            $failure = $_.Exception.Message
            if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
                $failure += " | " + $_.ErrorDetails.Message
            }
        }
        $sw.Stop()

        $typeOk = $false
        $titleOk = $false
        $headingsOk = $false
        $got = 'ERROR'
        $title = ''
        if ($response) {
            $type = [string]$response.type
            $title = [string]$response.title
            $description = [string]$response.description
            $got = $type

            $typeOk = ($type -eq $expected)
            $titleOk = ($title -match '^(feat|fix|refactor|docs|test|chore)(\(.+\))?: .+') -and
                       ($title.Length -le 72) -and
                       ($title.StartsWith($type))

            $present = @($requiredHeadings | Where-Object {
                $description -match "(?m)^$([regex]::Escape($_))\s*$"
            })
            $headingCount = ([regex]::Matches($description, '(?m)^##\s')).Count
            $headingsOk = ($present.Count -eq $requiredHeadings.Count) -and
                          ($headingCount -eq $requiredHeadings.Count)
        }

        $seconds = [math]::Round($sw.Elapsed.TotalSeconds, 1)
        $rows += [pscustomobject]@{
            Sample   = $file.Name
            Run      = $run
            Expected = $expected
            Got      = $got
            Type     = $typeOk
            Title    = $titleOk
            Headings = $headingsOk
            Seconds  = $seconds
            TitleText = $title
            Note     = $failure
        }
        Write-Host " done (${seconds}s)"
    }
}

if ($rows.Count -eq 0) {
    Write-Warning "No samples ran."
    exit 1
}

$rows | Format-Table Sample, Run, Expected, Got, Type, Title, Headings, Seconds -AutoSize

$firstError = $rows | Where-Object { $_.Note } | Select-Object -First 1
if ($firstError) {
    Write-Host "First error: $($firstError.Note)" -ForegroundColor Yellow
    Write-Host "Scores below are meaningless until errors are fixed." -ForegroundColor Yellow
}

$n = $rows.Count
function Pct($count) { [math]::Round(100 * $count / $n) }
$typeHits = @($rows | Where-Object { $_.Type }).Count
$titleHits = @($rows | Where-Object { $_.Title }).Count
$headingHits = @($rows | Where-Object { $_.Headings }).Count
$errorCount = @($rows | Where-Object { $_.Note }).Count

Write-Host ""
Write-Host "Model label  : $Label"
Write-Host "Results      : $n"
Write-Host "Type correct : $typeHits/$n ($(Pct $typeHits)%)"
Write-Host "Title format : $titleHits/$n ($(Pct $titleHits)%)"
Write-Host "3 headings   : $headingHits/$n ($(Pct $headingHits)%)"
Write-Host "Errors       : $errorCount"

$safeLabel = $Label -replace '[^\w\.-]', '-'
$csv = Join-Path $resultsDir ("{0}-{1}.csv" -f $safeLabel, (Get-Date -Format "yyyyMMdd-HHmmss"))
$rows | Export-Csv -Path $csv -NoTypeInformation
Write-Host "Saved: $csv"