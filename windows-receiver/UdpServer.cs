using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Text;

namespace BTMouseReceiver;

internal sealed class ClientStatusEventArgs : EventArgs
{
    public string? PhoneName { get; init; }
    public string? PhoneIp { get; init; }
    public bool IsConnected { get; init; }
    public long PacketsReceived { get; init; }
}

internal sealed class UdpServer : IDisposable
{
    public const int ControlPort = 51920;
    public const int DiscoveryPort = 51921;

    private const byte Magic0 = 0x42; // 'B'
    private const byte Magic1 = 0x54; // 'T'

    private const byte PktHello = 0x01;
    private const byte PktHelloAck = 0x02;
    private const byte PktPing = 0x03;
    private const byte PktPong = 0x04;

    private const byte PktMouseMove = 0x10;
    private const byte PktMouseButton = 0x11;
    private const byte PktPinchZoom = 0x12;
    private const byte PktKeyPress = 0x20;
    private const byte PktUnicodeText = 0x21;
    private const byte PktConsumerKey = 0x30;

    private readonly CancellationTokenSource _cts = new();
    private UdpClient? _controlClient;
    private UdpClient? _discoveryClient;

    private readonly ConcurrentDictionary<string, long> _recentSeqTimestamps = new();
    private readonly object _stateLock = new();
    private string? _connectedPhoneName;
    private string? _connectedPhoneIp;
    private DateTime _lastActivityUtc = DateTime.MinValue;
    private long _packetCount;

    public event EventHandler<ClientStatusEventArgs>? StatusChanged;
    public event EventHandler<string>? ServerError;

    public void Start()
    {
        try
        {
            _controlClient = CreateUdpListener(ControlPort);
            _discoveryClient = CreateUdpListener(DiscoveryPort);

            Task.Run(() => RunControlLoopAsync(_controlClient, _cts.Token));
            Task.Run(() => RunDiscoveryLoopAsync(_discoveryClient, _cts.Token));
            Task.Run(() => RunWatchdogLoopAsync(_cts.Token));
        }
        catch (Exception ex)
        {
            ServerError?.Invoke(this, $"Could not bind UDP ports {ControlPort}/{DiscoveryPort}: {ex.Message}");
        }
    }

    private static UdpClient CreateUdpListener(int port)
    {
        var client = new UdpClient(new IPEndPoint(IPAddress.Any, port));
        // Disable SIO_UDP_CONNRESET (-1744830452) on Windows so ICMP unreachable does not terminate ReceiveAsync
        try
        {
            client.Client.IOControl(-1744830452, new byte[] { 0 }, null);
        }
        catch
        {
            // Ignore on platforms/sockets that don't support IOControl
        }
        return client;
    }

    private async Task RunDiscoveryLoopAsync(UdpClient client, CancellationToken ct)
    {
        byte[] machineNameBytes = Encoding.UTF8.GetBytes(Environment.MachineName);
        while (!ct.IsCancellationRequested)
        {
            try
            {
                UdpReceiveResult result = await client.ReceiveAsync(ct);
                string message = Encoding.UTF8.GetString(result.Buffer).Trim();
                if (message.StartsWith("BTMOUSE_DISCOVER_V1", StringComparison.Ordinal))
                {
                    string replyText = $"BTMOUSE_HOST_V1|{Environment.MachineName}|{ControlPort}|{result.RemoteEndPoint.Address}";
                    byte[] replyBytes = Encoding.UTF8.GetBytes(replyText);
                    await client.SendAsync(replyBytes, replyBytes.Length, result.RemoteEndPoint);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }
            catch
            {
                await Task.Delay(50, ct);
            }
        }
        _ = machineNameBytes;
    }

    private async Task RunControlLoopAsync(UdpClient client, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                UdpReceiveResult result = await client.ReceiveAsync(ct);
                byte[] data = result.Buffer;
                if (data.Length < 3 || data[0] != Magic0 || data[1] != Magic1)
                {
                    continue;
                }

                IPEndPoint sender = result.RemoteEndPoint;
                string senderIp = sender.Address.ToString();
                byte pktType = data[2];

                lock (_stateLock)
                {
                    _lastActivityUtc = DateTime.UtcNow;
                    _connectedPhoneIp = senderIp;
                    _packetCount++;
                }

                switch (pktType)
                {
                    case PktHello:
                        HandleHello(client, sender, senderIp, data);
                        break;

                    case PktPing when data.Length >= 4:
                        byte pingSeq = data[3];
                        byte[] pong = { Magic0, Magic1, PktPong, pingSeq };
                        _ = client.SendAsync(pong, pong.Length, sender);
                        RaiseStatusChanged();
                        break;

                    case PktMouseMove when data.Length >= 7:
                        InputInjector.MoveAndScroll(
                            dx: unchecked((sbyte)data[3]),
                            dy: unchecked((sbyte)data[4]),
                            wheelV: unchecked((sbyte)data[5]),
                            wheelH: unchecked((sbyte)data[6])
                        );
                        break;

                    case PktMouseButton when data.Length >= 6:
                        if (!IsDuplicatePacket(senderIp, pktType, data[3]))
                        {
                            InputInjector.MouseButton(data[4], data[5]);
                        }
                        break;

                    case PktPinchZoom when data.Length >= 4:
                        InputInjector.PinchZoom(unchecked((sbyte)data[3]));
                        break;

                    case PktKeyPress when data.Length >= 6:
                        if (!IsDuplicatePacket(senderIp, pktType, data[3]))
                        {
                            InputInjector.SendHidKeyPress(data[4], data[5]);
                        }
                        break;

                    case PktUnicodeText when data.Length >= 6:
                        if (!IsDuplicatePacket(senderIp, pktType, data[3]))
                        {
                            int len = (data[4] << 8) | data[5];
                            if (len > 0 && data.Length >= 6 + len)
                            {
                                string text = Encoding.UTF8.GetString(data, 6, len);
                                InputInjector.SendUnicodeText(text);
                            }
                        }
                        break;

                    case PktConsumerKey when data.Length >= 6:
                        if (!IsDuplicatePacket(senderIp, pktType, data[3]))
                        {
                            ushort usage = (ushort)(data[4] | (data[5] << 8));
                            InputInjector.SendConsumerUsage(usage);
                        }
                        break;
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }
            catch
            {
                await Task.Delay(25, ct);
            }
        }
    }

    private void HandleHello(UdpClient client, IPEndPoint sender, string senderIp, byte[] data)
    {
        int nameLen = data.Length >= 4 ? data[3] : 0;
        string phoneName = nameLen > 0 && data.Length >= 4 + nameLen
            ? Encoding.UTF8.GetString(data, 4, nameLen)
            : "Android Phone";

        lock (_stateLock)
        {
            _connectedPhoneName = phoneName;
            _connectedPhoneIp = senderIp;
            _lastActivityUtc = DateTime.UtcNow;
        }

        byte[] hostBytes = Encoding.UTF8.GetBytes(Environment.MachineName);
        int copyLen = Math.Min(hostBytes.Length, 48);
        byte[] ack = new byte[4 + copyLen];
        ack[0] = Magic0;
        ack[1] = Magic1;
        ack[2] = PktHelloAck;
        ack[3] = (byte)copyLen;
        Array.Copy(hostBytes, 0, ack, 4, copyLen);

        _ = client.SendAsync(ack, ack.Length, sender);
        RaiseStatusChanged();
    }

    private async Task RunWatchdogLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                await Task.Delay(3000, ct);
                bool expired = false;
                lock (_stateLock)
                {
                    if (_connectedPhoneIp != null && (DateTime.UtcNow - _lastActivityUtc).TotalSeconds > 12)
                    {
                        _connectedPhoneIp = null;
                        _connectedPhoneName = null;
                        expired = true;
                    }
                }
                if (expired)
                {
                    RaiseStatusChanged();
                }

                // Prune old sequence deduplication keys older than 1s
                long cutoff = Environment.TickCount64 - 1000;
                foreach (var kv in _recentSeqTimestamps)
                {
                    if (kv.Value < cutoff)
                    {
                        _recentSeqTimestamps.TryRemove(kv.Key, out _);
                    }
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private bool IsDuplicatePacket(string senderIp, byte pktType, byte seq)
    {
        long now = Environment.TickCount64;
        string key = $"{senderIp}:{pktType}:{seq}";
        if (_recentSeqTimestamps.TryGetValue(key, out long prev) && (now - prev) < 250)
        {
            return true;
        }
        _recentSeqTimestamps[key] = now;
        return false;
    }

    private void RaiseStatusChanged()
    {
        string? name;
        string? ip;
        long count;
        lock (_stateLock)
        {
            name = _connectedPhoneName;
            ip = _connectedPhoneIp;
            count = _packetCount;
        }

        StatusChanged?.Invoke(this, new ClientStatusEventArgs
        {
            PhoneName = name,
            PhoneIp = ip,
            IsConnected = ip != null,
            PacketsReceived = count,
        });
    }

    public void Dispose()
    {
        _cts.Cancel();
        _controlClient?.Dispose();
        _discoveryClient?.Dispose();
        _cts.Dispose();
    }
}
