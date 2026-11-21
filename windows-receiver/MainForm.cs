using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using Microsoft.Win32;
using QRCoder;

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
    private readonly PictureBox _qrPictureBox = new();
    private readonly Label _qrHintLabel = new();
    private readonly Label _transferStatusLabel = new();
    private readonly Panel _progressTrackPanel = new();
    private readonly Panel _progressFillPanel = new();
    private readonly Label _progressPercentLabel = new();
    private readonly Button _sendFilesButton = new();
    private readonly Button _openFolderButton = new();
    private readonly CheckBox _startupCheckBox = new();
    private readonly Button _firewallButton = new();
    private readonly Button _refreshIpButton = new();
    private readonly Button _minimizeTrayButton = new();
    private readonly Button _exitButton = new();

    private bool _allowClose;
    private string _latestQrPayload = string.Empty;

    public MainForm(bool startMinimized, EventWaitHandle showWindowEvent)
    {
        _showWindowEvent = showWindowEvent;
        _appIcon = CreateBrandIcon();

        Text = "BT/WiFi Mouse Receiver";
        Icon = _appIcon;
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        AllowDrop = true;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(460, 536);
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

        _fileServer.GetClipboardTextAsync = () => RunOnUiThreadAsync(() =>
            Clipboard.ContainsText() ? Clipboard.GetText() : string.Empty);
        _fileServer.SetClipboardTextAsync = text => RunOnUiThreadAsync(() =>
        {
            if (string.IsNullOrEmpty(text))
            {
                Clipboard.Clear();
            }
            else
            {
                Clipboard.SetText(text);
            }
            _transferStatusLabel.Text = "Clipboard synced from phone";
            return true;
        });
        _fileServer.ExecuteSystemActionAsync = actionCode => Task.Run(() =>
        {
            bool ok = PowerControlHelper.Execute(
                actionCode,
                _fileServer.ReceiveDirectory,
                action => RunOnUiThreadAsync(() => { action(); return true; }));
            if (ok && !IsDisposed && IsHandleCreated)
            {
                BeginInvoke(() =>
                {
                    _transferStatusLabel.Text = actionCode switch
                    {
                        PowerControlHelper.ActionScreenshot => "Screenshot saved to Downloads\\BT mouse Files & copied",
                        PowerControlHelper.ActionBrightnessUp => "Screen brightness increased",
                        PowerControlHelper.ActionBrightnessDown => "Screen brightness decreased",
                        PowerControlHelper.ActionLock => "Workstation locked",
                        _ => _transferStatusLabel.Text,
                    };
                });
            }
            return ok;
        });
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
        _pcNameValueLabel.Location = new Point(112, 14);
        _pcNameValueLabel.Size = new Size(192, 20);

        var ipKeyLabel = new Label
        {
            Text = "Wi-Fi IPv4:",
            ForeColor = Color.FromArgb(148, 171, 189),
            Location = new Point(16, 44),
            AutoSize = true,
        };
        _ipValueLabel.Font = new Font("Consolas", 9.5f, FontStyle.Bold);
        _ipValueLabel.ForeColor = Color.FromArgb(88, 232, 198);
        _ipValueLabel.Location = new Point(112, 43);
        _ipValueLabel.Size = new Size(192, 34);

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
        _packetCountLabel.Location = new Point(112, 84);
        _packetCountLabel.Size = new Size(192, 20);

        _qrPictureBox.Location = new Point(316, 8);
        _qrPictureBox.Size = new Size(88, 88);
        _qrPictureBox.BackColor = Color.White;
        _qrPictureBox.SizeMode = PictureBoxSizeMode.Zoom;
        _qrPictureBox.Cursor = Cursors.Hand;
        _qrPictureBox.Click += (_, _) => ShowEnlargedQrDialog();

        _qrHintLabel.Text = "Scan QR";
        _qrHintLabel.Font = new Font("Segoe UI", 7.5f, FontStyle.Bold);
        _qrHintLabel.ForeColor = Color.FromArgb(0, 212, 255);
        _qrHintLabel.Location = new Point(316, 98);
        _qrHintLabel.Size = new Size(88, 14);
        _qrHintLabel.TextAlign = ContentAlignment.MiddleCenter;
        _qrHintLabel.Cursor = Cursors.Hand;
        _qrHintLabel.Click += (_, _) => ShowEnlargedQrDialog();

        infoCard.Controls.AddRange(new Control[]
        {
            pcKeyLabel, _pcNameValueLabel,
            ipKeyLabel, _ipValueLabel,
            pktKeyLabel, _packetCountLabel,
            _qrPictureBox, _qrHintLabel,
        });
        Controls.Add(infoCard);

        var fileCard = new Panel
        {
            Location = new Point(20, 316),
            Size = new Size(420, 114),
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

        _progressTrackPanel.Location = new Point(14, 52);
        _progressTrackPanel.Size = new Size(340, 12);
        _progressTrackPanel.BackColor = Color.FromArgb(11, 20, 27);

        _progressFillPanel.Location = new Point(0, 0);
        _progressFillPanel.Size = new Size(0, 12);
        _progressFillPanel.BackColor = Color.FromArgb(0, 212, 255);
        _progressTrackPanel.Controls.Add(_progressFillPanel);

        _progressPercentLabel.Text = "0%";
        _progressPercentLabel.Font = new Font("Consolas", 8.5f, FontStyle.Bold);
        _progressPercentLabel.ForeColor = Color.FromArgb(156, 180, 198);
        _progressPercentLabel.Location = new Point(360, 50);
        _progressPercentLabel.Size = new Size(46, 16);
        _progressPercentLabel.TextAlign = ContentAlignment.MiddleRight;

        _sendFilesButton.Text = "Send File to Phone";
        StyleButton(_sendFilesButton, Color.FromArgb(0, 150, 199), Color.White);
        _sendFilesButton.Location = new Point(14, 74);
        _sendFilesButton.Size = new Size(192, 30);
        _sendFilesButton.Click += (_, _) => PickAndOfferFilesToPhone();

        _openFolderButton.Text = "Open Received Folder";
        StyleButton(_openFolderButton, Color.FromArgb(29, 49, 64), Color.FromArgb(88, 232, 198));
        _openFolderButton.Location = new Point(214, 74);
        _openFolderButton.Size = new Size(192, 30);
        _openFolderButton.Click += (_, _) => OpenReceiveFolder();

        fileCard.Controls.AddRange(new Control[]
        {
            fileHeader, _transferStatusLabel, _progressTrackPanel, _progressPercentLabel, _sendFilesButton, _openFolderButton,
        });
        Controls.Add(fileCard);

        _startupCheckBox.Text = "Start minimized when Windows turns on";
        _startupCheckBox.ForeColor = Color.FromArgb(214, 229, 240);
        _startupCheckBox.Location = new Point(22, 442);
        _startupCheckBox.AutoSize = true;
        Controls.Add(_startupCheckBox);

        _refreshIpButton.Text = "Refresh IP";
        StyleButton(_refreshIpButton, Color.FromArgb(29, 49, 64), Color.White);
        _refreshIpButton.Location = new Point(20, 478);
        _refreshIpButton.Size = new Size(94, 36);
        _refreshIpButton.Click += (_, _) => RefreshNetworkInfo();

        _firewallButton.Text = "Allow Firewall";
        StyleButton(_firewallButton, Color.FromArgb(29, 49, 64), Color.FromArgb(88, 232, 198));
        _firewallButton.Location = new Point(122, 478);
        _firewallButton.Size = new Size(110, 36);
        _firewallButton.Click += (_, _) => AddFirewallRulesElevated();

        _minimizeTrayButton.Text = "Hide to Tray";
        StyleButton(_minimizeTrayButton, Color.FromArgb(0, 150, 199), Color.White);
        _minimizeTrayButton.Location = new Point(240, 478);
        _minimizeTrayButton.Size = new Size(110, 36);
        _minimizeTrayButton.Click += (_, _) => HideToTray();

        _exitButton.Text = "Exit";
        StyleButton(_exitButton, Color.FromArgb(95, 36, 44), Color.White);
        _exitButton.Location = new Point(358, 478);
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

        var uniqueIps = ips.Distinct().ToList();
        _ipValueLabel.Text = uniqueIps.Count > 0
            ? string.Join("  /  ", uniqueIps)
            : "No active Wi-Fi/LAN IPv4 found";

        UpdateQrCodePreview(uniqueIps);
    }

    private void UpdateQrCodePreview(List<string> uniqueIps)
    {
        if (uniqueIps.Count == 0)
        {
            _latestQrPayload = string.Empty;
            var old = _qrPictureBox.Image;
            _qrPictureBox.Image = null;
            old?.Dispose();
            return;
        }

        string payload = $"BTMOUSE_QR_V1|{string.Join(",", uniqueIps)}|{UdpServer.ControlPort}|{Environment.MachineName}";
        _latestQrPayload = payload;
        var prev = _qrPictureBox.Image;
        _qrPictureBox.Image = RenderQrBitmap(payload, pixelsPerModule: 4);
        prev?.Dispose();
    }

    private void ShowEnlargedQrDialog()
    {
        if (string.IsNullOrWhiteSpace(_latestQrPayload))
        {
            RefreshNetworkInfo();
            if (string.IsNullOrWhiteSpace(_latestQrPayload))
            {
                MessageBox.Show(this, "No active Wi-Fi or LAN IPv4 address found yet.", "QR Connect", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
        }

        using var popup = new Form
        {
            Text = "Scan QR from BT/WiFi Mouse Phone App",
            FormBorderStyle = FormBorderStyle.FixedDialog,
            MaximizeBox = false,
            MinimizeBox = false,
            StartPosition = FormStartPosition.CenterParent,
            ClientSize = new Size(320, 360),
            BackColor = Color.FromArgb(12, 21, 28),
            ForeColor = Color.FromArgb(236, 244, 250),
        };

        using var bigQr = RenderQrBitmap(_latestQrPayload, pixelsPerModule: 8);
        var pic = new PictureBox
        {
            Image = bigQr,
            Location = new Point(25, 20),
            Size = new Size(270, 270),
            SizeMode = PictureBoxSizeMode.Zoom,
            BackColor = Color.White,
        };
        var lbl = new Label
        {
            Text = $"{Environment.MachineName}  •  {_ipValueLabel.Text}",
            Location = new Point(16, 304),
            Size = new Size(288, 38),
            TextAlign = ContentAlignment.MiddleCenter,
            Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
            ForeColor = Color.FromArgb(88, 232, 198),
        };
        popup.Controls.Add(pic);
        popup.Controls.Add(lbl);
        popup.ShowDialog(this);
    }

    private static Bitmap RenderQrBitmap(string payload, int pixelsPerModule)
    {
        using var gen = new QRCodeGenerator();
        using QRCodeData data = gen.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
        var matrix = data.ModuleMatrix;
        int size = matrix.Count;
        int quiet = 2;
        int imgPx = (size + quiet * 2) * pixelsPerModule;
        var bmp = new Bitmap(imgPx, imgPx);
        using Graphics g = Graphics.FromImage(bmp);
        g.Clear(Color.White);
        using var darkBrush = new SolidBrush(Color.FromArgb(12, 21, 28));
        for (int y = 0; y < size; y++)
        {
            var row = matrix[y];
            for (int x = 0; x < row.Count; x++)
            {
                if (row[x])
                {
                    g.FillRectangle(
                        darkBrush,
                        (x + quiet) * pixelsPerModule,
                        (y + quiet) * pixelsPerModule,
                        pixelsPerModule,
                        pixelsPerModule);
                }
            }
        }
        return bmp;
    }

    private Task<T> RunOnUiThreadAsync<T>(Func<T> func)
    {
        var tcs = new TaskCompletionSource<T>(TaskCreationOptions.RunContinuationsAsynchronously);
        if (IsDisposed || !IsHandleCreated)
        {
            tcs.TrySetCanceled();
            return tcs.Task;
        }
        BeginInvoke(() =>
        {
            try
            {
                tcs.TrySetResult(func());
            }
            catch (Exception ex)
            {
                tcs.TrySetException(ex);
            }
        });
        return tcs.Task;
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
                _progressFillPanel.Width = 0;
                _progressFillPanel.BackColor = Color.FromArgb(248, 113, 113);
                _progressPercentLabel.Text = "0%";
                _progressPercentLabel.ForeColor = Color.FromArgb(248, 113, 113);
                return;
            }
            int pct = e.TotalBytes > 0
                ? Math.Clamp((int)((e.BytesTransferred * 100L) / e.TotalBytes), 0, 100)
                : 100;
            int fillWidth = (int)Math.Round(_progressTrackPanel.Width * (pct / 100.0));
            _progressFillPanel.Width = Math.Clamp(fillWidth, 0, _progressTrackPanel.Width);
            _progressFillPanel.BackColor = e.Completed
                ? Color.FromArgb(52, 211, 153)
                : Color.FromArgb(0, 212, 255);
            _progressPercentLabel.Text = $"{pct}%";
            _progressPercentLabel.ForeColor = e.Completed
                ? Color.FromArgb(52, 211, 153)
                : Color.FromArgb(0, 212, 255);

            string sizeProgress = $"{FormatBytes(e.BytesTransferred)} / {FormatBytes(e.TotalBytes)}";
            _transferStatusLabel.Text = e.Completed
                ? $"{e.Direction}: {e.FileName} ({sizeProgress})"
                : $"{e.Direction}: {e.FileName} ({sizeProgress})";
        });
    }

    private static string FormatBytes(long bytes)
    {
        if (bytes < 1024L) return $"{bytes} B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return $"{kb:F1} KB";
        double mb = kb / 1024.0;
        if (mb < 1024.0) return $"{mb:F1} MB";
        return $"{mb / 1024.0:F2} GB";
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
