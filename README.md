# ZTE Modem Configurator

Android app that configures ZTE USB modems and establishes internet connections via USB.

## Features

### Modem Configuration
- Detects available CDC drivers on Android device (`cdc_ether`, `cdc_ncm`, `cdc_acm`, etc.)
- Automatic or manual mode selection
- Sends appropriate AT commands to configure the modem
- Works without root access using USB Host API

### Internet Connection (New in v2.0)
- **USB Serial Mode** for diagnostics and AT command terminal
- **Automatic Connection** via AT commands (APN configuration, dialing)
- **VPN Service** routes all traffic through modem
- **Background Service** maintains connection
- **Real-time status** and signal monitoring

### ADB Control
- Remote control via ADB broadcasts
- Scriptable configuration and connection
- Diagnostic data export

## Supported Modems

- ZTE MF831 and other ZTE USB modems (VID: 0x19d2)

## How It Works

1. App scans `/sys/bus/usb/drivers/` to detect available CDC drivers
2. Finds connected ZTE modem via USB Host API
3. Selects appropriate AT command based on available driver:
   - `AT+ZCDRUN=E` for CDC-Ether mode
   - `AT+ZCDRUN=9` for CDC-NCM mode  
   - `AT+ZCDRUN=F` for CDC-ACM mode
4. Sends configuration via USB control/bulk transfer
5. User unplugs and replugs modem to activate new mode

## Building

```bash
./gradlew assembleRelease
```

Requires:
- Android SDK 33
- Gradle 7.5+
- Java 17

## Usage

### Quick Configure (One-Time Setup)

1. Install APK on Android device
2. Connect ZTE modem via USB OTG
3. Launch app and grant USB permission
4. Select mode (Auto-Detect recommended)
5. Tap "CONFIGURE MODEM"
6. Unplug and replug modem

### Connect to Internet

1. Select "USB Serial (Diagnostic Mode)"
2. Tap "CONFIGURE MODEM"
3. Wait 2 seconds for serial connection
4. Enter your carrier's APN (e.g., `internet`, `fast.t-mobile.com`)
5. Tap "CONNECT TO INTERNET"
6. Grant VPN permission when prompted
7. Wait for "Connected" status
8. Browse internet through USB modem!

### AT Command Terminal

When in USB Serial mode:
- Type AT commands in the terminal
- Examples:
  - `AT+CSQ` - Signal strength
  - `AT+CGMI` - Manufacturer info
  - `AT+COPS?` - Current carrier
  - `AT+CGDCONT?` - APN settings

## ADB Control

Control the app remotely via ADB:

```bash
# Detect system
adb shell am broadcast -a com.zte.modem.DETECT

# Configure modem (auto-detect mode)
adb shell am broadcast -a com.zte.modem.CONFIGURE

# Configure specific mode
adb shell am broadcast -a com.zte.modem.CONFIGURE --es mode "cdc_ether"

# Connect to internet
adb shell am broadcast -a com.zte.modem.CONNECT --es apn "internet"

# Disconnect
adb shell am broadcast -a com.zte.modem.DISCONNECT

# Get diagnostics
adb shell am broadcast -a com.zte.modem.GET_STATUS

# Get full log
adb shell am broadcast -a com.zte.modem.GET_LOG
```

Supported modes: `auto`, `cdc_acm`, `cdc_ncm`, `cdc_ether`, `rndis`, `serial`

## License

MIT
