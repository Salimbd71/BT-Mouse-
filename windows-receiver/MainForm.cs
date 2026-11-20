using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using Microsoft.Win32;

namespace BTMouseReceiver;

internal sealed class MainForm : Form
{
    private const string RunRegistryKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string AppRunValueName = "BTMouseReceiver";

    private readonly UdpServer _server = new();
    private readonly NotifyIcon _trayIcon;
    private readonly Icon _appIcon;
    private readonly EventWaitHandle _showWindowEvent;

    private readonly Label _statusDotLabel = new();
    private readonly Label _statusTitleLabel = new();
    private readonly Label _statusSubLabel = new();
    private readonly Label _ipValueLabel = new();
    private readonly Label _pcNameValueLabel = new();
    private readonly Label _packetCountLabel = new();
    private readonly CheckBox _startupCheckBox = new();
    private readonly Button _firewallButton = new();
    private readonly Button _refreshIpButton = new();
    private readonly Button _minimizeTrayButton = new();
    private readonly Button _exitButton = new();

    private bool _allowClose;

    public MainForm(bool startMinimized, EventWaitHandle showWindowEvent)
    {
        _showWindowEvent = showWindowEvent;
        _appIcon = CreateBrandIcon();

        Text = "BT mouse Wi-Fi Receiver";
        Icon = _appIcon;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(460, 410);
        BackColor = Color.FromArgb(12, 21, 28);
        ForeColor = Color.FromArgb(236, 244, 250);
        Font = new Font("Segoe UI", 9.5f, FontStyle.Regular);

        BuildUi();

        var trayMenu = new ContextMenuStrip();
        trayMenu.Items.Add("Open BT mouse Receiver", null, (_, _) => RestoreWindow());
        trayMenu.Items.Add(new ToolStripSeparator());
        trayMenu.Items.Add("Exit", null, (_, _) => ExitApplication());

        _trayIcon = new NotifyIcon
        {
            Icon = _appIcon,
            Text = "BT mouse Wi-Fi Receiver (Port 51920)",
            ContextMenuStrip = trayMenu,
            Visible = true,
        };
        _trayIcon.DoubleClick += (_, _) => RestoreWindow();

        _server.StatusChanged += OnServerStatusChanged;
        _server.ServerError += OnServerError;
        _server.Start();

        RefreshNetworkInfo();
        _startupCheckBox.Checked = IsStartupEnabled();
        _startupCheckBox.CheckedChanged += OnStartupCheckedChanged;

        StartShowSignalListener();

        if (startMinimized)
        {
            WindowState = FormWindowState.Minimized;
            ShowInTaskbar = false;
            Visible = false;
        }
    }

    private void BuildUi()
    {
        var headerPanel = new Panel
        {
            Location = new Point(20, 18),
            Size = new Size(420, 74),
            BackColor = Color.FromArgb(18, 32, 43),
        };

        var titleLabel = new Label
        {
            Text = "BT mouse  •  Windows Wi-Fi Receiver",
            Font = new Font("Segoe UI", 12.5f, FontStyle.Bold),
            ForeColor = Color.FromArgb(0, 212, 255),
            Location = new Point(16, 12),
            AutoSize = true,
        };

        var subtitleLabel = new Label
        {
            Text = $"Listening on UDP Port {UdpServer.ControlPort} (Auto-Discovery {UdpServer.DiscoveryPort})",
            Font = new Font("Segoe UI", 9f),
            ForeColor = Color.FromArgb(156, 180, 198),
            Location = new Point(16, 40),
            AutoSize = true,
        };

        headerPanel.Controls.Add(titleLabel);
        headerPanel.Controls.Add(subtitleLabel);
        Controls.Add(headerPanel);

        var statusCard = new Panel
        {
            Location = new Point(20, 104),
            Size = new Size(420, 72),
            BackColor = Color.FromArgb(18, 32, 43),
        };

        _statusDotLabel.Text = "●";
        _statusDotLabel.Font = new Font("Segoe UI", 14f, FontStyle.Bold);
        _statusDotLabel.ForeColor = Color.FromArgb(255, 193, 7);
        _statusDotLabel.Location = new Point(14, 14);
        _statusDotLabel.AutoSize = true;

        _statusTitleLabel.Text = "Waiting for Android phone on Wi-Fi...";
        _statusTitleLabel.Font = new Font("Segoe UI", 10.5f, FontStyle.Bold);
        _statusTitleLabel.ForeColor = Color.FromArgb(236, 244, 250);
        _statusTitleLabel.Location = new Point(42, 16);
        _statusTitleLabel.Size = new Size(362, 22);

        _statusSubLabel.Text = "Open BT mouse app → tap Wi-Fi → select this PC";
        _statusSubLabel.Font = new Font("Segoe UI", 8.8f);
        _statusSubLabel.ForeColor = Color.FromArgb(156, 180, 198);
        _statusSubLabel.Location = new Point(42, 42);
        _statusSubLabel.Size = new Size(362, 20);

        statusCard.Controls.Add(_statusDotLabel);
        statusCard.Controls.Add(_statusTitleLabel);
        statusCard.Controls.Add(_statusSubLabel);
        Controls.Add(statusCard);

        var infoCard = new Panel
        {
            Location = new Point(20, 188),
            Size = new Size(420, 118),
            BackColor = Color.FromArgb(18, 32, 43),
        };

        var pcKeyLabel = new Label
        {
            Text = "PC Name:",
            ForeColor = Color.FromArgb(148, 171, 189),
            Location = new Point(16, 14),
            AutoSize = true,
        };
        _pcNameValueLabel.Text = Environment.MachineName;
        _pcNameValueLabel.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
        _pcNameValueLabel.ForeColor = Color.White;
        _pcNameValueLabel.Location = new Point(130, 14);
        _pcNameValueLabel.Size = new Size(274, 20);

        var ipKeyLabel = new Label
        {
            Text = "Wi-Fi IPv4:",
            ForeColor = Color.FromArgb(148, 171, 189),
            Location = new Point(16, 44),
            AutoSize = true,
        };
        _ipValueLabel.Font = new Font("Consolas", 10f, FontStyle.Bold);
        _ipValueLabel.ForeColor = Color.FromArgb(88, 232, 198);
        _ipValueLabel.Location = new Point(130, 43);
        _ipValueLabel.Size = new Size(274, 34);

        var pktKeyLabel = new Label
        {
            Text = "Packets:",
            ForeColor = Color.FromArgb(148, 171, 189),
            Location = new Point(16, 84),
            AutoSize = true,
        };
        _packetCountLabel.Text = "0";
        _packetCountLabel.Font = new Font("Consolas", 9.5f);
        _packetCountLabel.ForeColor = Color.FromArgb(200, 220, 232);
        _packetCountLabel.Location = new Point(130, 84);
        _packetCountLabel.Size = new Size(274, 20);

        infoCard.Controls.AddRange(new Control[]
        {
            pcKeyLabel, _pcNameValueLabel,
            ipKeyLabel, _ipValueLabel,
            pktKeyLabel, _packetCountLabel,
        });
        Controls.Add(infoCard);

        _startupCheckBox.Text = "Start minimized when Windows turns on";
        _startupCheckBox.ForeColor = Color.FromArgb(214, 229, 240);
        _startupCheckBox.Location = new Point(22, 318);
        _startupCheckBox.AutoSize = true;
        Controls.Add(_startupCheckBox);

        _refreshIpButton.Text = "Refresh IP";
        StyleButton(_refreshIpButton, Color.FromArgb(29, 49, 64), Color.White);
        _refreshIpButton.Location = new Point(20, 354);
        _refreshIpButton.Size = new Size(94, 36);
        _refreshIpButton.Click += (_, _) => RefreshNetworkInfo();

        _firewallButton.Text = "Allow Firewall";
        StyleButton(_firewallButton, Color.FromArgb(29, 49, 64), Color.FromArgb(88, 232, 198));
        _firewallButton.Location = new Point(122, 354);
        _firewallButton.Size = new Size(110, 36);
        _firewallButton.Click += (_, _) => AddFirewallRulesElevated();

        _minimizeTrayButton.Text = "Hide to Tray";
        StyleButton(_minimizeTrayButton, Color.FromArgb(0, 150, 199), Color.White);
        _minimizeTrayButton.Location = new Point(240, 354);
        _minimizeTrayButton.Size = new Size(110, 36);
        _minimizeTrayButton.Click += (_, _) => HideToTray();

        _exitButton.Text = "Exit";
        StyleButton(_exitButton, Color.FromArgb(95, 36, 44), Color.White);
        _exitButton.Location = new Point(358, 354);
        _exitButton.Size = new Size(82, 36);
        _exitButton.Click += (_, _) => ExitApplication();

        Controls.AddRange(new Control[]
        {
            _refreshIpButton, _firewallButton, _minimizeTrayButton, _exitButton
        });
    }

    private static void StyleButton(Button btn, Color backColor, Color foreColor)
    {
        btn.FlatStyle = FlatStyle.Flat;
        btn.FlatAppearance.BorderSize = 0;
        btn.BackColor = backColor;
        btn.ForeColor = foreColor;
        btn.Font = new Font("Segoe UI", 9f, FontStyle.Bold);
        btn.Cursor = Cursors.Hand;
    }

    private void RefreshNetworkInfo()
    {
        var ips = new List<string>();
        foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up) continue;
            if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            if (ni.Description.Contains("Virtual", StringComparison.OrdinalIgnoreCase)) continue;

            foreach (UnicastIPAddressInformation ua in ni.GetIPProperties().UnicastAddresses)
            {
                if (ua.Address.AddressFamily == AddressFamily.InterNetwork &&
                    !ua.Address.ToString().StartsWith("169.254.", StringComparison.Ordinal))
                {
                    ips.Add(ua.Address.ToString());
                }
            }
        }

        _ipValueLabel.Text = ips.Count > 0
            ? string.Join("  /  ", ips.Distinct())
            : "No active Wi-Fi/LAN IPv4 found";
    }

    private void OnServerStatusChanged(object? sender, ClientStatusEventArgs e)
    {
        if (IsDisposed || !IsHandleCreated) return;
        BeginInvoke(() =>
        {
            _packetCountLabel.Text = e.PacketsReceived.ToString("N0");
            if (e.IsConnected)
            {
                _statusDotLabel.ForeColor = Color.FromArgb(52, 211, 153);
                _statusTitleLabel.Text = $"Connected: {e.PhoneName ?? "Android Phone"}";
                _statusSubLabel.Text = $"IP: {e.PhoneIp}  •  UDP active";
                _trayIcon.Text = $"BT mouse: Connected to {e.PhoneName ?? e.PhoneIp}";
            }
            else
            {
                _statusDotLabel.ForeColor = Color.FromArgb(255, 193, 7);
                _statusTitleLabel.Text = "Waiting for Android phone on Wi-Fi...";
                _statusSubLabel.Text = "Open BT mouse app → tap Wi-Fi → select this PC";
                _trayIcon.Text = "BT mouse Wi-Fi Receiver (Port 51920)";
            }
        });
    }

    private void OnServerError(object? sender, string errorMessage)
    {
        if (IsDisposed || !IsHandleCreated) return;
        BeginInvoke(() =>
        {
            _statusDotLabel.ForeColor = Color.FromArgb(248, 113, 113);
            _statusTitleLabel.Text = "Receiver error";
            _statusSubLabel.Text = errorMessage;
        });
    }

    private void AddFirewallRulesElevated()
    {
        try
        {
            string psArgs =
                "netsh advfirewall firewall delete rule name=\"BT mouse Wi-Fi Receiver\" >nul 2>&1 & " +
                "netsh advfirewall firewall add rule name=\"BT mouse Wi-Fi Receiver\" dir=in action=allow protocol=UDP localport=51920,51921";
            var psi = new ProcessStartInfo
            {
                FileName = "cmd.exe",
                Arguments = $"/c {psArgs}",
                Verb = "runas",
                UseShellExecute = true,
                WindowStyle = ProcessWindowStyle.Hidden,
            };
            Process.Start(psi);
            MessageBox.Show(
                this,
                "Windows Firewall allow rule added for UDP ports 51920 and 51921.",
                "BT mouse Receiver",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                this,
                $"Could not add firewall rule automatically: {ex.Message}",
                "BT mouse Receiver",
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);
        }
    }

    private static bool IsStartupEnabled()
    {
        using RegistryKey? key = Registry.CurrentUser.OpenSubKey(RunRegistryKeyPath, writable: false);
        return key?.GetValue(AppRunValueName) is string val && !string.IsNullOrWhiteSpace(val);
    }

    private void OnStartupCheckedChanged(object? sender, EventArgs e)
    {
        try
        {
            using RegistryKey? key = Registry.CurrentUser.OpenSubKey(RunRegistryKeyPath, writable: true);
            if (key == null) return;
            if (_startupCheckBox.Checked)
            {
                string exePath = Environment.ProcessPath ?? Application.ExecutablePath;
                key.SetValue(AppRunValueName, $"\"{exePath}\" --minimized");
            }
            else
            {
                key.DeleteValue(AppRunValueName, throwOnMissingValue: false);
            }
        }
        catch
        {
            // Ignore registry permission errors
        }
    }

    private void StartShowSignalListener()
    {
        Task.Run(() =>
        {
            while (!IsDisposed)
            {
                if (_showWindowEvent.WaitOne(500))
                {
                    if (!IsDisposed && IsHandleCreated)
                    {
                        BeginInvoke(RestoreWindow);
                    }
                }
            }
        });
    }

    private void RestoreWindow()
    {
        Visible = true;
        ShowInTaskbar = true;
        WindowState = FormWindowState.Normal;
        Activate();
    }

    private void HideToTray()
    {
        Visible = false;
        ShowInTaskbar = false;
    }

    private void ExitApplication()
    {
        _allowClose = true;
        _trayIcon.Visible = false;
        Close();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (!_allowClose && e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            HideToTray();
            return;
        }
        base.OnFormClosing(e);
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _server.Dispose();
            _trayIcon.Dispose();
            _appIcon.Dispose();
        }
        base.Dispose(disposing);
    }

    private static Icon CreateBrandIcon()
    {
        using var bmp = new Bitmap(64, 64);
        using Graphics g = Graphics.FromImage(bmp);
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.Clear(Color.FromArgb(12, 21, 28));

        using var borderPen = new Pen(Color.FromArgb(0, 212, 255), 3f);
        g.DrawEllipse(borderPen, 5, 5, 54, 54);

        Point[] arrow =
        {
            new Point(22, 16),
            new Point(22, 47),
            new Point(30, 39),
            new Point(36, 51),
            new Point(41, 48),
            new Point(35, 36),
            new Point(46, 36),
        };
        using var fillBrush = new SolidBrush(Color.FromArgb(0, 212, 255));
        g.FillPolygon(fillBrush, arrow);

        return Icon.FromHandle(bmp.GetHicon());
    }
}
