using System.Buffers.Binary;
using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Text;

namespace BTMouseReceiver;

internal sealed record OfferedFile(
    string Id,
    string FileName,
    long FileSize,
    string FullPath
);

internal sealed class FileTransferProgressEventArgs : EventArgs
{
    public string Direction { get; init; } = "";
    public string FileName { get; init; } = "";
    public long BytesTransferred { get; init; }
    public long TotalBytes { get; init; }
    public bool Completed { get; init; }
    public bool Cancelled { get; init; }
}

internal sealed class FileTransferServer : IDisposable
{
    public const int FilePort = 51922;

    private const byte OpUploadToPc = 0x01;
    private const byte OpListOffers = 0x02;
    private const byte OpDownloadFromPc = 0x03;
    private const byte OpClearOffer = 0x04;
    private const byte OpGetClipboard = 0x05;
    private const byte OpSetClipboard = 0x06;
    private const byte OpSystemAction = 0x07;

    private const byte StatusAck = 0x06;
    private const byte StatusErr = 0x15;

    private readonly CancellationTokenSource _cts = new();
    private TcpListener? _listener;
    private readonly ConcurrentDictionary<string, OfferedFile> _offeredFiles = new();

    public string ReceiveDirectory { get; }

    public Func<Task<string>>? GetClipboardTextAsync { get; set; }
    public Func<string, Task>? SetClipboardTextAsync { get; set; }
    public Func<byte, Task<bool>>? ExecuteSystemActionAsync { get; set; }

    public event EventHandler<FileTransferProgressEventArgs>? TransferProgress;
    public event EventHandler<int>? OffersChanged;
    public event EventHandler<string>? ServerError;

    public FileTransferServer()
    {
        string userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        string downloads = string.IsNullOrWhiteSpace(userProfile)
            ? Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments)
            : Path.Combine(userProfile, "Downloads");
        ReceiveDirectory = Path.Combine(downloads, "BT mouse Files");
    }

    public int PendingOfferCount => _offeredFiles.Count;

    public void Start()
    {
        try
        {
            Directory.CreateDirectory(ReceiveDirectory);
            _listener = new TcpListener(IPAddress.Any, FilePort);
            _listener.Start();
            Task.Run(() => AcceptLoopAsync(_listener, _cts.Token));
        }
        catch (Exception ex)
        {
            ServerError?.Invoke(this, $"File transfer TCP port {FilePort} error: {ex.Message}");
        }
    }

    public int AddOfferedFiles(IEnumerable<string> filePaths)
    {
        int added = 0;
        foreach (string path in filePaths)
        {
            try
            {
                if (!File.Exists(path)) continue;
                var info = new FileInfo(path);
                string id = Guid.NewGuid().ToString("N")[..12];
                var offer = new OfferedFile(id, info.Name, info.Length, info.FullName);
                _offeredFiles[id] = offer;
                added++;
            }
            catch
            {
                // Ignore inaccessible file
            }
        }
        if (added > 0)
        {
            OffersChanged?.Invoke(this, _offeredFiles.Count);
        }
        return _offeredFiles.Count;
    }

    public void ClearAllOffers()
    {
        _offeredFiles.Clear();
        OffersChanged?.Invoke(this, 0);
    }

    private async Task AcceptLoopAsync(TcpListener listener, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                TcpClient client = await listener.AcceptTcpClientAsync(ct);
                _ = Task.Run(() => HandleClientAsync(client, ct), ct);
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
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken ct)
    {
        using (client)
        {
            client.NoDelay = true;
            client.SendBufferSize = 65536;
            client.ReceiveBufferSize = 65536;
            await using NetworkStream stream = client.GetStream();

            byte[] opBuf = new byte[1];
            if (!await ReadExactAsync(stream, opBuf, 1, ct)) return;

            switch (opBuf[0])
            {
                case OpUploadToPc:
                    await HandleUploadToPcAsync(stream, ct);
                    break;
                case OpListOffers:
                    await HandleListOffersAsync(stream, ct);
                    break;
                case OpDownloadFromPc:
                    await HandleDownloadFromPcAsync(stream, ct);
                    break;
                case OpClearOffer:
                    await HandleClearOfferAsync(stream, ct);
                    break;
                case OpGetClipboard:
                    await HandleGetClipboardAsync(stream, ct);
                    break;
                case OpSetClipboard:
                    await HandleSetClipboardAsync(stream, ct);
                    break;
                case OpSystemAction:
                    await HandleSystemActionAsync(stream, ct);
                    break;
            }
        }
    }

    private async Task HandleUploadToPcAsync(NetworkStream stream, CancellationToken ct)
    {
        string? tempPath = null;
        try
        {
            byte[] lenBuf = new byte[2];
            if (!await ReadExactAsync(stream, lenBuf, 2, ct)) return;
            ushort nameLen = BinaryPrimitives.ReadUInt16BigEndian(lenBuf);
            if (nameLen == 0 || nameLen > 4096)
            {
                await stream.WriteAsync(new[] { StatusErr }, ct);
                return;
            }

            byte[] nameBytes = new byte[nameLen];
            if (!await ReadExactAsync(stream, nameBytes, nameLen, ct)) return;
            string rawName = Encoding.UTF8.GetString(nameBytes);

            byte[] sizeBuf = new byte[8];
            if (!await ReadExactAsync(stream, sizeBuf, 8, ct)) return;
            long fileSize = BinaryPrimitives.ReadInt64BigEndian(sizeBuf);
            if (fileSize < 0)
            {
                await stream.WriteAsync(new[] { StatusErr }, ct);
                return;
            }

            Directory.CreateDirectory(ReceiveDirectory);
            string safeName = SanitizeFileName(rawName);
            string finalPath = GetUniqueFilePath(ReceiveDirectory, safeName);
            tempPath = finalPath + ".part";

            byte[] buffer = new byte[65536];
            long received = 0;
            long lastNotify = 0;
            string displayFileName = Path.GetFileName(finalPath);

            TransferProgress?.Invoke(this, new FileTransferProgressEventArgs
            {
                Direction = "Received from Phone",
                FileName = displayFileName,
                BytesTransferred = 0,
                TotalBytes = fileSize,
                Completed = false,
            });

            await using (var fs = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None, 65536, useAsync: true))
            {
                while (received < fileSize)
                {
                    int toRead = (int)Math.Min(buffer.Length, fileSize - received);
                    int n = await stream.ReadAsync(buffer.AsMemory(0, toRead), ct);
                    if (n <= 0)
                    {
                        throw new EndOfStreamException("Upload disconnected early");
                    }
                    await fs.WriteAsync(buffer.AsMemory(0, n), ct);
                    received += n;

                    if (received - lastNotify >= 65536 || received == fileSize)
                    {
                        lastNotify = received;
                        TransferProgress?.Invoke(this, new FileTransferProgressEventArgs
                        {
                            Direction = "Received from Phone",
                            FileName = displayFileName,
                            BytesTransferred = received,
                            TotalBytes = fileSize,
                            Completed = false,
                        });
                    }
                }
                await fs.FlushAsync(ct);
            }

            File.Move(tempPath, finalPath, overwrite: false);
            tempPath = null;

            await stream.WriteAsync(new[] { StatusAck }, ct);
            await stream.FlushAsync(ct);

            TransferProgress?.Invoke(this, new FileTransferProgressEventArgs
            {
                Direction = "Received from Phone",
                FileName = Path.GetFileName(finalPath),
                BytesTransferred = fileSize,
                TotalBytes = fileSize,
                Completed = true,
            });
        }
        catch
        {
            if (tempPath != null && File.Exists(tempPath))
            {
                try { File.Delete(tempPath); } catch { }
            }
            TransferProgress?.Invoke(this, new FileTransferProgressEventArgs
            {
                Direction = "Received from Phone",
                FileName = "",
                BytesTransferred = 0,
                TotalBytes = 0,
                Completed = false,
                Cancelled = true,
            });
            try { await stream.WriteAsync(new[] { StatusErr }, ct); } catch { }
        }
    }

    private async Task HandleListOffersAsync(NetworkStream stream, CancellationToken ct)
    {
        var valid = new List<OfferedFile>();
        foreach (var kv in _offeredFiles)
        {
            if (File.Exists(kv.Value.FullPath))
            {
                var info = new FileInfo(kv.Value.FullPath);
                valid.Add(kv.Value with { FileSize = info.Length });
            }
            else
            {
                _offeredFiles.TryRemove(kv.Key, out _);
            }
        }

        using var ms = new MemoryStream();
        byte[] intBuf = new byte[4];
        BinaryPrimitives.WriteInt32BigEndian(intBuf, valid.Count);
        ms.Write(intBuf, 0, 4);

        byte[] u16 = new byte[2];
        byte[] i64 = new byte[8];

        foreach (OfferedFile item in valid)
        {
            byte[] idBytes = Encoding.UTF8.GetBytes(item.Id);
            byte[] nameBytes = Encoding.UTF8.GetBytes(item.FileName);

            BinaryPrimitives.WriteUInt16BigEndian(u16, (ushort)idBytes.Length);
            ms.Write(u16, 0, 2);
            ms.Write(idBytes, 0, idBytes.Length);

            BinaryPrimitives.WriteUInt16BigEndian(u16, (ushort)nameBytes.Length);
            ms.Write(u16, 0, 2);
            ms.Write(nameBytes, 0, nameBytes.Length);

            BinaryPrimitives.WriteInt64BigEndian(i64, item.FileSize);
            ms.Write(i64, 0, 8);
        }

        byte[] payload = ms.ToArray();
        await stream.WriteAsync(payload, ct);
        await stream.FlushAsync(ct);
    }

    private async Task HandleDownloadFromPcAsync(NetworkStream stream, CancellationToken ct)
    {
        byte[] lenBuf = new byte[2];
        if (!await ReadExactAsync(stream, lenBuf, 2, ct)) return;
        ushort idLen = BinaryPrimitives.ReadUInt16BigEndian(lenBuf);
        if (idLen == 0 || idLen > 256) return;

        byte[] idBytes = new byte[idLen];
        if (!await ReadExactAsync(stream, idBytes, idLen, ct)) return;
        string id = Encoding.UTF8.GetString(idBytes);

        if (!_offeredFiles.TryGetValue(id, out OfferedFile? offer) || !File.Exists(offer.FullPath))
        {
            byte[] notFound = new byte[9];
            notFound[0] = 0x00;
            await stream.WriteAsync(notFound, ct);
            await stream.FlushAsync(ct);
            return;
        }

        var info = new FileInfo(offer.FullPath);
        long size = info.Length;
        byte[] header = new byte[9];
        header[0] = 0x01;
        BinaryPrimitives.WriteInt64BigEndian(header.AsSpan(1, 8), size);
        await stream.WriteAsync(header, ct);

        byte[] buffer = new byte[65536];
        long sent = 0;
        long lastNotify = 0;

        TransferProgress?.Invoke(this, new FileTransferProgressEventArgs
        {
            Direction = "Sent to Phone",
            FileName = offer.FileName,
            BytesTransferred = 0,
            TotalBytes = size,
            Completed = size == 0,
        });

        await using var fs = new FileStream(offer.FullPath, FileMode.Open, FileAccess.Read, FileShare.Read, 65536, useAsync: true);
        int read;
        while ((read = await fs.ReadAsync(buffer, ct)) > 0)
        {
            await stream.WriteAsync(buffer.AsMemory(0, read), ct);
            sent += read;
            if (sent - lastNotify >= 65536 || sent == size)
            {
                lastNotify = sent;
                TransferProgress?.Invoke(this, new FileTransferProgressEventArgs
                {
                    Direction = "Sent to Phone",
                    FileName = offer.FileName,
                    BytesTransferred = sent,
                    TotalBytes = size,
                    Completed = sent == size,
                });
            }
        }
        await stream.FlushAsync(ct);
    }

    private async Task HandleClearOfferAsync(NetworkStream stream, CancellationToken ct)
    {
        byte[] lenBuf = new byte[2];
        if (!await ReadExactAsync(stream, lenBuf, 2, ct)) return;
        ushort idLen = BinaryPrimitives.ReadUInt16BigEndian(lenBuf);
        if (idLen == 0 || idLen > 256) return;

        byte[] idBytes = new byte[idLen];
        if (!await ReadExactAsync(stream, idBytes, idLen, ct)) return;
        string id = Encoding.UTF8.GetString(idBytes);

        _offeredFiles.TryRemove(id, out _);
        OffersChanged?.Invoke(this, _offeredFiles.Count);

        await stream.WriteAsync(new[] { StatusAck }, ct);
        await stream.FlushAsync(ct);
    }

    private async Task HandleGetClipboardAsync(NetworkStream stream, CancellationToken ct)
    {
        try
        {
            string text = GetClipboardTextAsync != null
                ? await GetClipboardTextAsync()
                : string.Empty;
            byte[] utf8 = Encoding.UTF8.GetBytes(text);
            byte[] packet = new byte[1 + 4 + utf8.Length];
            packet[0] = StatusAck;
            BinaryPrimitives.WriteInt32BigEndian(packet.AsSpan(1, 4), utf8.Length);
            Buffer.BlockCopy(utf8, 0, packet, 5, utf8.Length);
            await stream.WriteAsync(packet, ct);
            await stream.FlushAsync(ct);
        }
        catch
        {
            try { await stream.WriteAsync(new[] { StatusErr }, ct); } catch { }
        }
    }

    private async Task HandleSetClipboardAsync(NetworkStream stream, CancellationToken ct)
    {
        try
        {
            byte[] lenBuf = new byte[4];
            if (!await ReadExactAsync(stream, lenBuf, 4, ct)) return;
            int len = BinaryPrimitives.ReadInt32BigEndian(lenBuf);
            if (len < 0 || len > 1_048_576)
            {
                await stream.WriteAsync(new[] { StatusErr }, ct);
                return;
            }

            string text = string.Empty;
            if (len > 0)
            {
                byte[] textBytes = new byte[len];
                if (!await ReadExactAsync(stream, textBytes, len, ct)) return;
                text = Encoding.UTF8.GetString(textBytes);
            }

            if (SetClipboardTextAsync != null)
            {
                await SetClipboardTextAsync(text);
            }

            await stream.WriteAsync(new[] { StatusAck }, ct);
            await stream.FlushAsync(ct);
        }
        catch
        {
            try { await stream.WriteAsync(new[] { StatusErr }, ct); } catch { }
        }
    }

    private async Task HandleSystemActionAsync(NetworkStream stream, CancellationToken ct)
    {
        try
        {
            byte[] actionBuf = new byte[1];
            if (!await ReadExactAsync(stream, actionBuf, 1, ct)) return;
            bool ok = ExecuteSystemActionAsync != null && await ExecuteSystemActionAsync(actionBuf[0]);
            await stream.WriteAsync(new[] { ok ? StatusAck : StatusErr }, ct);
            await stream.FlushAsync(ct);
        }
        catch
        {
            try { await stream.WriteAsync(new[] { StatusErr }, ct); } catch { }
        }
    }

    private static async Task<bool> ReadExactAsync(NetworkStream stream, byte[] buffer, int count, CancellationToken ct)
    {
        int offset = 0;
        while (offset < count)
        {
            int n = await stream.ReadAsync(buffer.AsMemory(offset, count - offset), ct);
            if (n <= 0) return false;
            offset += n;
        }
        return true;
    }

    private static string SanitizeFileName(string rawName)
    {
        string name = Path.GetFileName(rawName).Trim();
        foreach (char invalid in Path.GetInvalidFileNameChars())
        {
            name = name.Replace(invalid, '_');
        }
        return string.IsNullOrWhiteSpace(name) ? "received_file" : name;
    }

    private static string GetUniqueFilePath(string directory, string fileName)
    {
        string candidate = Path.Combine(directory, fileName);
        if (!File.Exists(candidate)) return candidate;

        string baseName = Path.GetFileNameWithoutExtension(fileName);
        string ext = Path.GetExtension(fileName);
        int index = 1;
        while (File.Exists(candidate))
        {
            candidate = Path.Combine(directory, $"{baseName} ({index}){ext}");
            index++;
        }
        return candidate;
    }

    public void Dispose()
    {
        _cts.Cancel();
        _listener?.Stop();
        _cts.Dispose();
    }
}
