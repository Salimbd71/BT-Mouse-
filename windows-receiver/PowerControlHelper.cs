using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

namespace BTMouseReceiver;

internal static class PowerControlHelper
{
    public const byte ActionLock = 0x01;
    public const byte ActionSleep = 0x02;
    public const byte ActionRestart = 0x03;
    public const byte ActionShutdown = 0x04;
    public const byte ActionBrightnessUp = 0x05;
    public const byte ActionBrightnessDown = 0x06;
    public const byte ActionScreenshot = 0x07;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool LockWorkStation();

    [DllImport("powrprof.dll", SetLastError = true)]
    private static extern bool SetSuspendState(bool hibernate, bool forceCritical, bool disableWakeEvent);

    public static bool Execute(byte actionCode, string receiveDirectory, Func<Action, Task> runOnUiThreadAsync)
    {
        switch (actionCode)
        {
            case ActionLock:
                return LockWorkStation();

            case ActionSleep:
                return SetSuspendState(hibernate: false, forceCritical: false, disableWakeEvent: false);

            case ActionRestart:
                Process.Start(new ProcessStartInfo("shutdown", "/r /t 4")
                {
                    CreateNoWindow = true,
                    UseShellExecute = false,
                });
                return true;

            case ActionShutdown:
                Process.Start(new ProcessStartInfo("shutdown", "/s /t 4")
                {
                    CreateNoWindow = true,
                    UseShellExecute = false,
                });
                return true;

            case ActionBrightnessUp:
                AdjustBrightness(+15);
                return true;

            case ActionBrightnessDown:
                AdjustBrightness(-15);
                return true;

            case ActionScreenshot:
                return CaptureScreenshot(receiveDirectory, runOnUiThreadAsync);

            default:
                return false;
        }
    }

    private static void AdjustBrightness(int deltaPercent)
    {
        _ = Task.Run(() =>
        {
            try
            {
                string ps =
                    "$b = (Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightness -ErrorAction SilentlyContinue | Select-Object -First 1).CurrentBrightness; " +
                    "if ($null -ne $b) { " +
                    $"$next = [Math]::Max(5, [Math]::Min(100, [int]$b + ({deltaPercent}))); " +
                    "(Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightnessMethods -ErrorAction SilentlyContinue | Select-Object -First 1).WmiSetBrightness(1, $next) " +
                    "}";
                var psi = new ProcessStartInfo("powershell.exe", $"-NoProfile -NonInteractive -WindowStyle Hidden -Command \"{ps}\"")
                {
                    CreateNoWindow = true,
                    UseShellExecute = false,
                };
                Process.Start(psi);
            }
            catch
            {
                // External monitors without DDC/WMI brightness support ignore cleanly
            }
        });
    }

    private static bool CaptureScreenshot(string receiveDirectory, Func<Action, Task> runOnUiThreadAsync)
    {
        try
        {
            Rectangle bounds = SystemInformation.VirtualScreen;
            using var bitmap = new Bitmap(bounds.Width, bounds.Height, PixelFormat.Format32bppArgb);
            using (Graphics g = Graphics.FromImage(bitmap))
            {
                g.CopyFromScreen(bounds.Left, bounds.Top, 0, 0, bounds.Size, CopyPixelOperation.SourceCopy);
            }

            Directory.CreateDirectory(receiveDirectory);
            string filePath = Path.Combine(
                receiveDirectory,
                $"Screenshot_{DateTime.Now:yyyyMMdd_HHmmss}.png");
            bitmap.Save(filePath, ImageFormat.Png);

            var cloneForClipboard = (Bitmap)bitmap.Clone();
            _ = runOnUiThreadAsync(() =>
            {
                try
                {
                    Clipboard.SetImage(cloneForClipboard);
                }
                finally
                {
                    cloneForClipboard.Dispose();
                }
            });
            return true;
        }
        catch
        {
            return false;
        }
    }
}
