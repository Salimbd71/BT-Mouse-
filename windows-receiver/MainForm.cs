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
    private readonly FileTransferServer _fileServer = new();
    private readonly NotifyIcon _trayIcon;
    private readonly Icon _appIcon;
    private readonly EventWaitHandle _showWindowEvent;

    private readonly Label _statusDotLabel = new();
    private readonly Label _statusTitleLabel = new();
    private readonly Label _statusSubLabel = new();
    private readonly Label _ipValueLabel = new();
    private readonly Label _pcNameValueLabel = new();
    private readonly Label _packetCountLabel = new();
    private readonly Label _transferStatusLabel = new();
    private readonly Button _sendFilesButton = new();
    private readonly Button _openFolderButton = new();
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
        AllowDrop = true;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(460, 505);
        BackColor = Color.FromArgb(12, 21, 28);
        ForeColor = Color.FromArgb(236, 244, 250);
        Font = new Font("Segoe UI", 9.5f, FontStyle.Regular);

        DragEnter += OnFormDragEnter;
        DragDrop += OnFormDragDrop;

        BuildUi();

        var trayMenu = new ContextMenuStrip();
        trayMenu.Items.Add("Open BT mouse Receiver", null, (_, _) => RestoreWindow());
        trayMenu.Items.Add("Send File(s) to Phone...", null, (_, _) => PickAndOfferFilesToPhone());
        trayMenu.Items.Add("Open Received Files Folder", null, (_, _) => OpenReceiveFolder());
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

        _fileServer.TransferProgress += OnFileTransferProgress;
        _fileServer.OffersChanged += OnFileOffersChanged;
        _fileServer.ServerError += OnServerError;
        _fileServer.Start();

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

        var fileCard = new Panel
        {
            Location = new Point(20, 316),
            Size = new Size(420, 86),
            BackColor = Color.FromArgb(18, 32, 43),
        };

        var fileHeader = new Label
        {
            Text = "Wi-Fi File Transfer (TCP 51922)  •  Drag & drop files anywhere",
            Font = new Font("Segoe UI", 8.8f, FontStyle.Bold),
            ForeColor = Color.FromArgb(0, 212, 255),
            Location = new Point(14, 10),
            AutoSize = true,
        };

        _transferStatusLabel.Text = "Ready • Saves phone files to Downloads\\BT mouse Files";
        _transferStatusLabel.Font = new Font("Segoe UI", 8.5f);
        _transferStatusLabel.ForeColor = Color.FromArgb(156, 180, 198);
        _transferStatusLabel.Location = new Point(14, 30);
        _transferStatusLabel.Size = new Size(392, 18);

        _sendFilesButton.Text = "Send File to Phone";
        StyleButton(_sendFilesButton, Color.FromArgb(0, 150, 199), Color.White);
        _sendFilesButton.Location = new Point(14, 50);
        _sendFilesButton.Size = new Size(192, 28);
        _sendFilesButton.Click += (_, _) => PickAndOfferFilesToPhone();

        _openFolderButton.Text = "Open Received Folder";
        StyleButton(_openFolderButton, Color.FromArgb(29, 49, 64), Color.FromArgb(88, 232, 198));
        _openFolderButton.Location = new Point(214, 50);
        _openFolderButton.Size = new Size(192, 28);
        _openFolderButton.Click += (_, _) => OpenReceiveFolder();

        fileCard.Controls.AddRange(new Control[]
        {
            fileHeader, _transferStatusLabel, _sendFilesButton, _openFolderButton,
        });
        Controls.Add(fileCard);

        _startupCheckBox.Text = "Start minimized when Windows turns on";
        _startupCheckBox.ForeColor = Color.FromArgb(214, 229, 240);
        _startupCheckBox.Location = new Point(22, 412);
        _startupCheckBox.AutoSize = true;
        Controls.Add(_startupCheckBox);

        _refreshIpButton.Text = "Refresh IP";
        StyleButton(_refreshIpButton, Color.FromArgb(29, 49, 64), Color.White);
        _refreshIpButton.Location = new Point(20, 448);
        _refreshIpButton.Size = new Size(94, 36);
        _refreshIpButton.Click += (_, _) => RefreshNetworkInfo();

        _firewallButton.Text = "Allow Firewall";
        StyleButton(_firewallButton, Color.FromArgb(29, 49, 64), Color.FromArgb(88, 232, 198));
        _firewallButton.Location = new Point(122, 448);
        _firewallButton.Size = new Size(110, 36);
        _firewallButton.Click += (_, _) => AddFirewallRulesElevated();

        _minimizeTrayButton.Text = "Hide to Tray";
        StyleButton(_minimizeTrayButton, Color.FromArgb(0, 150, 199), Color.White);
        _minimizeTrayButton.Location = new Point(240, 448);
        _minimizeTrayButton.Size = new Size(110, 36);
        _minimizeTrayButton.Click += (_, _) => HideToTray();

        _exitButton.Text = "Exit";
        StyleButton(_exitButton, Color.FromArgb(95, 36, 44), Color.White);
        _exitButton.Location = new Point(358, 448);
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

    private void PickAndOfferFilesToPhone()
    {
        using var dlg = new OpenFileDialog
        {
            Title = "Select file(s) to send to Android Phone",
            Multiselect = true,
            Filter = "All Files (*.*)|*.*",
        };
        if (dlg.ShowDialog(this) == DialogResult.OK && dlg.FileNames.Length > 0)
        {
            OfferFilesToPhone(dlg.FileNames);
        }
    }

    private void OnFormDragEnter(object? sender, DragEventArgs e)
    {
        if (e.Data?.GetDataPresent(DataFormats.FileDrop) == true)
        {
            e.Effect = DragDropEffects.Copy;
        }
    }

    private void OnFormDragDrop(object? sender, DragEventArgs e)
    {
        if (e.Data?.GetData(DataFormats.FileDrop) is string[] files && files.Length > 0)
        {
            OfferFilesToPhone(files);
        }
    }

    private void OfferFilesToPhone(IEnumerable<string> paths)
    {
        int totalPending = _fileServer.AddOfferedFiles(paths);
        _server.NotifyFileOffer(totalPending);
        _transferStatusLabel.Text = totalPending > 0
            ? $"Queued {totalPending} file(s) for phone • Open 'Files' on phone"
            : "No valid files selected (folders not supported directly)";
    }

    private void OpenReceiveFolder()
    {
        try
        {
            Directory.CreateDirectory(_fileServer.ReceiveDirectory);
            Process.Start(new ProcessStartInfo
            {
                FileName = _fileServer.ReceiveDirectory,
                UseShellExecute = true,
            });
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, $"Could not open folder: {ex.Message}", "BT mouse Receiver");
        }
    }

    private void OnFileOffersChanged(object? sender, int remainingCount)
    {
        if (IsDisposed || !IsHandleCreated) return;
        BeginInvoke(() =>
        {
            if (remainingCount == 0)
            {
                _transferStatusLabel.Text = "Ready • Saves phone files to Downloads\\BT mouse Files";
            }
            else
            {
                _transferStatusLabel.Text = $"Queued {remainingCount} file(s) waiting for phone download";
            }
        });
    }

    private void OnFileTransferProgress(object? sender, FileTransferProgressEventArgs e)
    {
        if (IsDisposed || !IsHandleCreated) return;
        BeginInvoke(() =>
        {
            if (e.Cancelled)
            {
                _transferStatusLabel.Text = "Transfer cancelled • Ready";
                return;
            }
            int pct = e.TotalBytes > 0 ? (int)((e.BytesTransferred * 100L) / e.TotalBytes) : 100;
            _transferStatusLabel.Text = e.Completed
                ? $"{e.Direction}: {e.FileName} (100%)"
                : $"{e.Direction}: {e.FileName} ({pct}%)";
        });
    }

    private void AddFirewallRulesElevated()
    {
        try
        {
            string psArgs =
                "netsh advfirewall firewall delete rule name=\"BT mouse Wi-Fi Receiver\" >nul 2>&1 & " +
                "netsh advfirewall firewall add rule name=\"BT mouse Wi-Fi Receiver\" dir=in action=allow protocol=UDP localport=51920,51921 & " +
                "netsh advfirewall firewall delete rule name=\"BT mouse Wi-Fi File Transfer\" >nul 2>&1 & " +
                "netsh advfirewall firewall add rule name=\"BT mouse Wi-Fi File Transfer\" dir=in action=allow protocol=TCP localport=51922";
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
                "Windows Firewall allow rules added for UDP 51920-51921 and TCP 51922.",
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
            _fileServer.Dispose();
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
