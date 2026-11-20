namespace BTMouseReceiver;

internal static class Program
{
    private const string MutexName = @"Global\BTMouseReceiver_SingleInstance_Mutex";
    private const string ShowEventName = @"Global\BTMouseReceiver_ShowWindow_Event";

    [STAThread]
    private static void Main(string[] args)
    {
        Application.SetHighDpiMode(HighDpiMode.PerMonitorV2);
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);

        using var showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);
        using var mutex = new Mutex(initiallyOwned: true, name: MutexName, createdNew: out bool createdNew);

        if (!createdNew)
        {
            // Signal the already-running instance to show its window
            showEvent.Set();
            return;
        }

        bool startMinimized = args.Any(a => string.Equals(a, "--minimized", StringComparison.OrdinalIgnoreCase));
        Application.Run(new MainForm(startMinimized, showEvent));
    }
}
