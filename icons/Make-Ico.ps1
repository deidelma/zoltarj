param(
    [Parameter(Mandatory = $true)]
    [string]$InputPng,

    [string]$OutputIco
)

if (-not $OutputIco) {
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($InputPng)
    $OutputIco = "$baseName.ico"
}

# Ensure magick is available (ImageMagick)
$magick = "magick"
try {
    & $magick -version | Out-Null
} catch {
    Write-Error "ImageMagick ('magick') not found in PATH."
    exit 1
}

# Create multi-size ICO
& $magick $InputPng -define icon:auto-resize=256,128,64,48,32,24,16 $OutputIco

Write-Host "Created $OutputIco"
