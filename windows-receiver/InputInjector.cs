using System.Diagnostics;
using System.Runtime.InteropServices;

namespace BTMouseReceiver;

internal static class InputInjector
{
    private const uint InputMouse = 0;
    private const uint InputKeyboard = 1;

    private const uint MouseEventMove = 0x0001;
    private const uint MouseEventLeftDown = 0x0002;
    private const uint MouseEventLeftUp = 0x0004;
    private const uint MouseEventRightDown = 0x0008;
    private const uint MouseEventRightUp = 0x0010;
    private const uint MouseEventMiddleDown = 0x0020;
    private const uint MouseEventMiddleUp = 0x0040;
    private const uint MouseEventXDown = 0x0080;
    private const uint MouseEventXUp = 0x0100;
    private const uint MouseEventWheel = 0x0800;
    private const uint MouseEventHWheel = 0x01000;

    private const int WheelDelta = 120;
    private const int XButton1 = 0x0001;
    private const int XButton2 = 0x0002;

    private const uint KeyEventExtendedKey = 0x0001;
    private const uint KeyEventKeyUp = 0x0002;
    private const uint KeyEventUnicode = 0x0004;

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public int mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HARDWAREINPUT
    {
        public uint uMsg;
        public ushort wParamL;
        public ushort wParamH;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public InputUnion U;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, [In] INPUT[] pInputs, int cbSize);

    private static readonly int InputSize = Marshal.SizeOf<INPUT>();

    private static readonly object SmoothLock = new();
    private static readonly AutoResetEvent SmoothSignal = new(false);
    private static double _remX;
    private static double _remY;

    static InputInjector()
    {
        var smootherThread = new Thread(RunSmoothLoop)
        {
            IsBackground = true,
            Name = "MouseMotionSmoother",
            Priority = ThreadPriority.AboveNormal,
        };
        smootherThread.Start();
    }

    private static void RunSmoothLoop()
    {
        long freq = Stopwatch.Frequency;
        long stepTicks = (long)(freq * 0.0025); // ~400Hz smooth sub-step cadence
        while (true)
        {
            SmoothSignal.WaitOne(15);
            while (true)
            {
                long startTick = Stopwatch.GetTimestamp();
                int emitX = 0;
                int emitY = 0;
                bool keepRunning;

                lock (SmoothLock)
                {
                    if (Math.Abs(_remX) < 0.15 && Math.Abs(_remY) < 0.15)
                    {
                        _remX = 0;
                        _remY = 0;
                        keepRunning = false;
                    }
                    else
                    {
                        double stepX = Math.Abs(_remX) <= 1.25 ? _remX : _remX * 0.45;
                        double stepY = Math.Abs(_remY) <= 1.25 ? _remY : _remY * 0.45;
                        emitX = (int)Math.Round(stepX, MidpointRounding.AwayFromZero);
                        emitY = (int)Math.Round(stepY, MidpointRounding.AwayFromZero);

                        if (emitX == 0 && Math.Abs(_remX) >= 0.5) emitX = Math.Sign(_remX);
                        if (emitY == 0 && Math.Abs(_remY) >= 0.5) emitY = Math.Sign(_remY);

                        _remX -= emitX;
                        _remY -= emitY;
                        keepRunning = Math.Abs(_remX) >= 0.15 || Math.Abs(_remY) >= 0.15;
                    }
                }

                if (emitX != 0 || emitY != 0)
                {
                    INPUT[] moveInput = { MakeMouseInput(emitX, emitY, 0, MouseEventMove) };
                    SendBatch(moveInput);
                }

                if (!keepRunning) break;

                long targetTick = startTick + stepTicks;
                while (Stopwatch.GetTimestamp() < targetTick)
                {
                    Thread.Sleep(1);
                }
            }
        }
    }

    public static void MoveAndScroll(sbyte dx, sbyte dy, sbyte wheelV, sbyte wheelH)
    {
        if (dx != 0 || dy != 0)
        {
            lock (SmoothLock)
            {
                _remX = Math.Clamp(_remX + dx, -600, 600);
                _remY = Math.Clamp(_remY + dy, -600, 600);
            }
            SmoothSignal.Set();
        }

        if (wheelV != 0 || wheelH != 0)
        {
            var list = new List<INPUT>(2);
            if (wheelV != 0)
            {
                list.Add(MakeMouseInput(0, 0, wheelV * WheelDelta, MouseEventWheel));
            }
            if (wheelH != 0)
            {
                list.Add(MakeMouseInput(0, 0, wheelH * WheelDelta, MouseEventHWheel));
            }
            SendBatch(list.ToArray());
        }
    }

    public static void MouseButton(byte buttonMask, byte action)
    {
        // action: 0 = up, 1 = down, 2 = click
        var list = new List<INPUT>(4);
        bool doDown = action is 1 or 2;
        bool doUp = action is 0 or 2;

        if ((buttonMask & 0x01) != 0)
        {
            if (doDown) list.Add(MakeMouseInput(0, 0, 0, MouseEventLeftDown));
            if (doUp) list.Add(MakeMouseInput(0, 0, 0, MouseEventLeftUp));
        }
        if ((buttonMask & 0x02) != 0)
        {
            if (doDown) list.Add(MakeMouseInput(0, 0, 0, MouseEventRightDown));
            if (doUp) list.Add(MakeMouseInput(0, 0, 0, MouseEventRightUp));
        }
        if ((buttonMask & 0x04) != 0)
        {
            if (doDown) list.Add(MakeMouseInput(0, 0, 0, MouseEventMiddleDown));
            if (doUp) list.Add(MakeMouseInput(0, 0, 0, MouseEventMiddleUp));
        }
        if ((buttonMask & 0x08) != 0)
        {
            if (doDown) list.Add(MakeMouseInput(0, 0, XButton1, MouseEventXDown));
            if (doUp) list.Add(MakeMouseInput(0, 0, XButton1, MouseEventXUp));
        }
        if ((buttonMask & 0x10) != 0)
        {
            if (doDown) list.Add(MakeMouseInput(0, 0, XButton2, MouseEventXDown));
            if (doUp) list.Add(MakeMouseInput(0, 0, XButton2, MouseEventXUp));
        }

        if (list.Count > 0)
        {
            SendBatch(list.ToArray());
        }
    }

    public static void PinchZoom(sbyte wheelStep)
    {
        if (wheelStep == 0) return;
        INPUT[] inputs =
        {
            MakeKeyInput(HidToWinKeyMap.VkLControl, keyUp: false),
            MakeMouseInput(0, 0, wheelStep * WheelDelta, MouseEventWheel),
            MakeKeyInput(HidToWinKeyMap.VkLControl, keyUp: true),
        };
        SendBatch(inputs);
    }

    public static void SendHidKeyPress(byte modifiers, byte hidKeyCode)
    {
        ushort vk = HidToWinKeyMap.FromHidUsage(hidKeyCode);
        if (vk == 0 && modifiers == 0) return;

        var modKeys = CollectModifierVks(modifiers);
        var list = new List<INPUT>(modKeys.Count * 2 + 2);

        foreach (ushort modVk in modKeys)
        {
            list.Add(MakeKeyInput(modVk, keyUp: false));
        }

        if (vk != 0)
        {
            list.Add(MakeKeyInput(vk, keyUp: false));
            list.Add(MakeKeyInput(vk, keyUp: true));
        }

        for (int i = modKeys.Count - 1; i >= 0; i--)
        {
            list.Add(MakeKeyInput(modKeys[i], keyUp: true));
        }

        if (list.Count > 0)
        {
            SendBatch(list.ToArray());
        }
    }

    public static void SendUnicodeText(string text)
    {
        if (string.IsNullOrEmpty(text)) return;

        var list = new List<INPUT>(text.Length * 2);
        for (int i = 0; i < text.Length; i++)
        {
            char ch = text[i];
            if (ch == '\r') continue;
            if (ch == '\n')
            {
                list.Add(MakeKeyInput(HidToWinKeyMap.VkReturn, keyUp: false));
                list.Add(MakeKeyInput(HidToWinKeyMap.VkReturn, keyUp: true));
                continue;
            }
            if (ch == '\t')
            {
                list.Add(MakeKeyInput(HidToWinKeyMap.VkTab, keyUp: false));
                list.Add(MakeKeyInput(HidToWinKeyMap.VkTab, keyUp: true));
                continue;
            }

            list.Add(MakeUnicodeInput(ch, keyUp: false));
            list.Add(MakeUnicodeInput(ch, keyUp: true));
        }

        if (list.Count > 0)
        {
            SendBatch(list.ToArray());
        }
    }

    public static void SendConsumerUsage(ushort usage)
    {
        ushort vk = HidToWinKeyMap.FromConsumerUsage(usage);
        if (vk == 0) return;

        INPUT[] inputs =
        {
            MakeKeyInput(vk, keyUp: false),
            MakeKeyInput(vk, keyUp: true),
        };
        SendBatch(inputs);
    }

    private static List<ushort> CollectModifierVks(byte modifiers)
    {
        var mods = new List<ushort>(4);
        if ((modifiers & HidToWinKeyMap.ModLeftCtrl) != 0) mods.Add(HidToWinKeyMap.VkLControl);
        if ((modifiers & HidToWinKeyMap.ModLeftShift) != 0) mods.Add(HidToWinKeyMap.VkLShift);
        if ((modifiers & HidToWinKeyMap.ModLeftAlt) != 0) mods.Add(HidToWinKeyMap.VkLMenu);
        if ((modifiers & HidToWinKeyMap.ModLeftGui) != 0) mods.Add(HidToWinKeyMap.VkLWin);
        if ((modifiers & HidToWinKeyMap.ModRightCtrl) != 0) mods.Add(HidToWinKeyMap.VkRControl);
        if ((modifiers & HidToWinKeyMap.ModRightShift) != 0) mods.Add(HidToWinKeyMap.VkRShift);
        if ((modifiers & HidToWinKeyMap.ModRightAlt) != 0) mods.Add(HidToWinKeyMap.VkRMenu);
        if ((modifiers & HidToWinKeyMap.ModRightGui) != 0) mods.Add(HidToWinKeyMap.VkRWin);
        return mods;
    }

    private static INPUT MakeMouseInput(int dx, int dy, int mouseData, uint flags) => new()
    {
        type = InputMouse,
        U = new InputUnion
        {
            mi = new MOUSEINPUT
            {
                dx = dx,
                dy = dy,
                mouseData = mouseData,
                dwFlags = flags,
                time = 0,
                dwExtraInfo = IntPtr.Zero,
            }
        }
    };

    private static INPUT MakeKeyInput(ushort vk, bool keyUp)
    {
        uint flags = 0;
        if (HidToWinKeyMap.IsExtendedKey(vk)) flags |= KeyEventExtendedKey;
        if (keyUp) flags |= KeyEventKeyUp;
        return new INPUT
        {
            type = InputKeyboard,
            U = new InputUnion
            {
                ki = new KEYBDINPUT
                {
                    wVk = vk,
                    wScan = 0,
                    dwFlags = flags,
                    time = 0,
                    dwExtraInfo = IntPtr.Zero,
                }
            }
        };
    }

    private static INPUT MakeUnicodeInput(char ch, bool keyUp) => new()
    {
        type = InputKeyboard,
        U = new InputUnion
        {
            ki = new KEYBDINPUT
            {
                wVk = 0,
                wScan = ch,
                dwFlags = keyUp ? (KeyEventUnicode | KeyEventKeyUp) : KeyEventUnicode,
                time = 0,
                dwExtraInfo = IntPtr.Zero,
            }
        }
    };

    private static void SendBatch(INPUT[] inputs)
    {
        SendInput((uint)inputs.Length, inputs, InputSize);
    }
}
