param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [int]$Threshold = 30,
    [double]$FillRatio = 0.92
)

Add-Type -AssemblyName System.Drawing

$bmp = [System.Drawing.Bitmap]::FromFile($InputPath)
$w = $bmp.Width
$h = $bmp.Height

function IsEmpty([System.Drawing.Color]$c) {
    return ($c.A -lt 10) -or ($c.R -le $Threshold -and $c.G -le $Threshold -and $c.B -le $Threshold)
}

$minX = $w
$minY = $h
$maxX = 0
$maxY = 0

for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        if (-not (IsEmpty ($bmp.GetPixel($x, $y)))) {
            if ($x -lt $minX) { $minX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}

$cropW = $maxX - $minX + 1
$cropH = $maxY - $minY + 1
$side = [Math]::Max($cropW, $cropH)
$targetSide = [int]($side / $FillRatio)
$out = New-Object System.Drawing.Bitmap $targetSide, $targetSide, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

$scale = [Math]::Min($targetSide / $cropW, $targetSide / $cropH)
$drawW = [int]($cropW * $scale)
$drawH = [int]($cropH * $scale)
$offsetX = [int](($targetSide - $drawW) / 2)
$offsetY = [int](($targetSide - $drawH) / 2)

$g = [System.Drawing.Graphics]::FromImage($out)
try {
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($bmp, $offsetX, $offsetY, $drawW, $drawH)
} finally {
    $g.Dispose()
}

$out.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$out.Dispose()
Write-Host "Saved $OutputPath (${targetSide}x${targetSide})"
