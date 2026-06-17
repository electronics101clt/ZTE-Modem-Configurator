# ZTE Modem Configurator

Android app that automatically detects available CDC drivers and configures ZTE USB modems to use the compatible mode.

## Features

- Detects available CDC drivers on Android device (`cdc_ether`, `cdc_ncm`, `cdc_acm`, etc.)
- Automatically selects the best compatible mode
- Sends appropriate AT commands to configure the modem
- Works without root access using USB Host API

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

1. Install APK on Android device
2. Connect ZTE modem via USB OTG
3. Launch app and grant USB permission
4. Tap "DETECT SYSTEM & CONFIGURE MODEM"
5. Wait for success message
6. Unplug and replug modem

## License

MIT
