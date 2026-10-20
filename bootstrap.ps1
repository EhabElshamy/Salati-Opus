$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$tmp = Join-Path $env:TEMP ("salati-" + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
  $zip = Join-Path $tmp 'gradle.zip'
  Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -OutFile $zip
  $expected = (Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256').Content.Trim()
  if ((Get-FileHash $zip -Algorithm SHA256).Hash.ToLower() -ne $expected.ToLower()) { throw 'Gradle checksum mismatch' }
  Expand-Archive $zip -DestinationPath $tmp
  & "$tmp\gradle-8.11.1\bin\gradle.bat" wrapper --gradle-version 8.11.1
  if ($LASTEXITCODE -ne 0) { throw 'Wrapper generation failed. Check JDK 17 and the SDK.' }
} finally { Remove-Item $tmp -Recurse -Force }
Write-Host 'Wrapper created. Open this folder in Android Studio.'
