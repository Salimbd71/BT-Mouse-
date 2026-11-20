#define MyAppName "BT mouse Wi-Fi Receiver"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "BT mouse"
#define MyAppExeName "BTMouseReceiver.exe"

[Setup]
AppId={{8E9A71A4-2D4B-4F31-9B7C-4A2E8F5B1201}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\BT mouse Receiver
DefaultGroupName=BT mouse Receiver
DisableProgramGroupPage=yes
OutputDir=installer-out
OutputBaseFilename=BTMouseReceiver-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "autostart"; Description: "Start BT mouse Receiver automatically with Windows (minimized to tray)"; GroupDescription: "Startup Options"

[Files]
Source: "publish\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "BTMouseReceiver"; ValueData: """{app}\{#MyAppExeName}"" --minimized"; Flags: uninsdeletevalue; Tasks: autostart

[Run]
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""BT mouse Wi-Fi Receiver"""; Flags: runhidden waituntilterminated
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""BT mouse Wi-Fi Receiver"" dir=in action=allow protocol=UDP localport=51920,51921"; Flags: runhidden waituntilterminated
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "taskkill"; Parameters: "/IM {#MyAppExeName} /F"; Flags: runhidden; RunOnceId: "KillReceiver"
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""BT mouse Wi-Fi Receiver"""; Flags: runhidden waituntilterminated; RunOnceId: "DelFirewallRule"
