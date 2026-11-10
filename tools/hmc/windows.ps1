# Lists the monitors' work areas and the outer rectangle of every Minecraft window (DPI-aware pixels), to check
# how the director tiled windowed clients. Usage: powershell -NoProfile -File tools/hmc/windows.ps1
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class StagecraftWin {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[StagecraftWin]::SetProcessDPIAware() | Out-Null
foreach ($screen in [System.Windows.Forms.Screen]::AllScreens) {
    $w = $screen.WorkingArea
    "monitor {0}{1}: bounds {2} work area x={3} y={4} w={5} h={6}" -f $screen.DeviceName, $(if ($screen.Primary) { ' (primary)' } else { '' }), $screen.Bounds, $w.X, $w.Y, $w.Width, $w.Height
}
Get-Process -Name java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*Minecraft*' } | ForEach-Object {
    $rect = New-Object StagecraftWin+RECT
    [StagecraftWin]::GetWindowRect($_.MainWindowHandle, [ref]$rect) | Out-Null
    "window '{0}': x={1} y={2} w={3} h={4}" -f $_.MainWindowTitle, $rect.Left, $rect.Top, ($rect.Right - $rect.Left), ($rect.Bottom - $rect.Top)
}
