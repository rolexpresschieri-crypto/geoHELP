param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [int]$Threshold = 30
)

Add-Type -AssemblyName System.Drawing

$bmp = [System.Drawing.Bitmap]::FromFile($InputPath)
$w = $bmp.Width
$h = $bmp.Height

function IsBorder([System.Drawing.Color]$c) {
    return ($c.A -lt 10) -or ($c.R -le $Threshold -and $c.G -le $Threshold -and $c.B -le $Threshold)
}

$minX = $w
$minY = $h
$maxX = 0
$maxY = 0

for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        if (-not (IsBorder ($bmp.GetPixel($x, $y)))) {
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
$out = New-Object System.Drawing.Bitmap $side, $side, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

$offsetX = [int](($side - $cropW) / 2)
$offsetY = [int](($side - $cropH) / 2)

$g = [System.Drawing.Graphics]::FromImage($out)
try {
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($bmp, $offsetX, $offsetY, [System.Drawing.Rectangle]::new($minX, $minY, $cropW, $cropH), [System.Drawing.GraphicsUnit]::Pixel)
} finally {
    $g.Dispose()
}

$out.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$out.Dispose()
Write-Host "Trimmed $InputPath -> $OutputPath (${side}x${side})"
