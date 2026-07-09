param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [int]$Threshold = 35
)

Add-Type -AssemblyName System.Drawing

$bmp = [System.Drawing.Bitmap]::FromFile($InputPath)
$w = $bmp.Width
$h = $bmp.Height
$remove = New-Object bool[] ($w * $h)
$queue = [System.Collections.Generic.Queue[object]]::new()

function Test-Dark([System.Drawing.Color]$c) {
    return ($c.R -le $Threshold -and $c.G -le $Threshold -and $c.B -le $Threshold)
}

function Add-Seed([int]$x, [int]$y) {
    if ($x -lt 0 -or $y -lt 0 -or $x -ge $w -or $y -ge $h) { return }
    $idx = ($y * $w) + $x
    if ($remove[$idx]) { return }
    $c = $bmp.GetPixel($x, $y)
    if (Test-Dark $c) {
        $remove[$idx] = $true
        $null = $queue.Enqueue([int[]]@($x, $y))
    }
}

for ($x = 0; $x -lt $w; $x++) {
    Add-Seed $x 0
    Add-Seed $x ($h - 1)
}
for ($y = 0; $y -lt $h; $y++) {
    Add-Seed 0 $y
    Add-Seed ($w - 1) $y
}

while ($queue.Count -gt 0) {
    $p = $queue.Dequeue()
    Add-Seed ($p[0] - 1) $p[1]
    Add-Seed ($p[0] + 1) $p[1]
    Add-Seed $p[0] ($p[1] - 1)
    Add-Seed $p[0] ($p[1] + 1)
}

$out = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $idx = ($y * $w) + $x
        $c = $bmp.GetPixel($x, $y)
        if ($remove[$idx]) {
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, $c.R, $c.G, $c.B))
        } else {
            $out.SetPixel($x, $y, $c)
        }
    }
}

$out.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$out.Dispose()

Write-Host "Saved $OutputPath"
