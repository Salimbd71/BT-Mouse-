namespace BTMouseReceiver;

internal static class HidToWinKeyMap
{
    public const byte ModLeftCtrl = 0x01;
    public const byte ModLeftShift = 0x02;
    public const byte ModLeftAlt = 0x04;
    public const byte ModLeftGui = 0x08;
    public const byte ModRightCtrl = 0x10;
    public const byte ModRightShift = 0x20;
    public const byte ModRightAlt = 0x40;
    public const byte ModRightGui = 0x80;

    public const ushort VkBack = 0x08;
    public const ushort VkTab = 0x09;
    public const ushort VkReturn = 0x0D;
    public const ushort VkEscape = 0x1B;
    public const ushort VkSpace = 0x20;
    public const ushort VkPrior = 0x21;    // Page Up
    public const ushort VkNext = 0x22;     // Page Down
    public const ushort VkEnd = 0x23;
    public const ushort VkHome = 0x24;
    public const ushort VkLeft = 0x25;
    public const ushort VkUp = 0x26;
    public const ushort VkRight = 0x27;
    public const ushort VkDown = 0x28;
    public const ushort VkSnapshot = 0x2C; // Print Screen
    public const ushort VkInsert = 0x2D;
    public const ushort VkDelete = 0x2E;

    public const ushort VkLWin = 0x5B;
    public const ushort VkRWin = 0x5C;
    public const ushort VkLShift = 0xA0;
    public const ushort VkRShift = 0xA1;
    public const ushort VkLControl = 0xA2;
    public const ushort VkRControl = 0xA3;
    public const ushort VkLMenu = 0xA4;
    public const ushort VkRMenu = 0xA5;

    public const ushort VkBrowserBack = 0xA6;
    public const ushort VkBrowserForward = 0xA7;
    public const ushort VkBrowserHome = 0xAC;
    public const ushort VkBrowserSearch = 0xAA;
    public const ushort VkVolumeMute = 0xAD;
    public const ushort VkVolumeDown = 0xAE;
    public const ushort VkVolumeUp = 0xAF;
    public const ushort VkMediaNextTrack = 0xB0;
    public const ushort VkMediaPrevTrack = 0xB1;
    public const ushort VkMediaStop = 0xB2;
    public const ushort VkMediaPlayPause = 0xB3;

    public const ushort VkOem1 = 0xBA;      // ;:
    public const ushort VkOemPlus = 0xBB;   // =+
    public const ushort VkOemComma = 0xBC;  // ,<
    public const ushort VkOemMinus = 0xBD;  // -_
    public const ushort VkOemPeriod = 0xBE; // .>
    public const ushort VkOem2 = 0xBF;      // /?
    public const ushort VkOem3 = 0xC0;      // `~
    public const ushort VkOem4 = 0xDB;      // [{
    public const ushort VkOem5 = 0xDC;      // \|
    public const ushort VkOem6 = 0xDD;      // ]}
    public const ushort VkOem7 = 0xDE;      // '"

    public static ushort FromHidUsage(byte hidKey)
    {
        // A-Z (0x04..0x1D) -> VK_A..VK_Z (0x41..0x5A)
        if (hidKey is >= 0x04 and <= 0x1D)
        {
            return (ushort)('A' + (hidKey - 0x04));
        }

        // 1-9 (0x1E..0x26) -> '1'..'9' (0x31..0x39)
        if (hidKey is >= 0x1E and <= 0x26)
        {
            return (ushort)('1' + (hidKey - 0x1E));
        }

        // F1-F12 (0x3A..0x45) -> 0x70..0x7B
        if (hidKey is >= 0x3A and <= 0x45)
        {
            return (ushort)(0x70 + (hidKey - 0x3A));
        }

        return hidKey switch
        {
            0x27 => '0',
            0x28 => VkReturn,
            0x29 => VkEscape,
            0x2A => VkBack,
            0x2B => VkTab,
            0x2C => VkSpace,
            0x2D => VkOemMinus,
            0x2E => VkOemPlus,
            0x2F => VkOem4,
            0x30 => VkOem6,
            0x31 => VkOem5,
            0x33 => VkOem1,
            0x34 => VkOem7,
            0x35 => VkOem3,
            0x36 => VkOemComma,
            0x37 => VkOemPeriod,
            0x38 => VkOem2,
            0x39 => 0x14, // VK_CAPITAL (Caps Lock)
            0x46 => VkSnapshot,
            0x49 => VkInsert,
            0x4A => VkHome,
            0x4B => VkPrior,
            0x4C => VkDelete,
            0x4D => VkEnd,
            0x4E => VkNext,
            0x4F => VkRight,
            0x50 => VkLeft,
            0x51 => VkDown,
            0x52 => VkUp,
            _ => 0,
        };
    }

    public static ushort FromConsumerUsage(ushort usage) => usage switch
    {
        0x00B5 => VkMediaNextTrack,
        0x00B6 => VkMediaPrevTrack,
        0x00B7 => VkMediaStop,
        0x00CD => VkMediaPlayPause,
        0x00E2 => VkVolumeMute,
        0x00E9 => VkVolumeUp,
        0x00EA => VkVolumeDown,
        0x0221 => VkBrowserSearch,
        0x0223 => VkBrowserHome,
        0x0224 => VkBrowserBack,
        0x0040 => VkEscape, // Menu fallback
        _ => 0,
    };

    public static bool IsExtendedKey(ushort vk) => vk is
        VkRControl or VkRMenu or VkLWin or VkRWin or
        VkInsert or VkDelete or VkHome or VkEnd or VkPrior or VkNext or
        VkLeft or VkUp or VkRight or VkDown or VkSnapshot;
}
