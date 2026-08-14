# 🦤 Dodo-RF: Multi-Band Sub-GHz Transceiver Dashboard & Replicator

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform: ESP32](https://img.shields.io/badge/Platform-ESP32-blue.svg)](https://www.espressif.com/)
[![Radio: CC1101](https://img.shields.io/badge/Radio-CC1101-red.svg)](https://www.ti.com/product/CC1101)

**Dodo-RF** is a fully standalone, open-source Sub-GHz RF analysis, capture, and replay platform built using an **ESP32** microcontroller and a **CC1101** transceiver module. 

Featuring an embedded, cyberpunk-styled Web UI served directly from internal flash memory (`PROGMEM`), Dodo-RF creates its own Wi-Fi Access Point so you can scan, stage, store, and retransmit fixed-code RF signals from any browser or smartphone without external internet or software dependencies.

---

## 📑 Table of Contents

- [Features](#-features)
- [Hardware & Pinout](#-hardware--pinout)
- [Power Configurations](#-power-configurations)
- [Software Dependencies](#-software-dependencies)
- [Installation & Flashing](#-installation--flashing)
- [Web Dashboard & Theming](#-web-dashboard--theming)
- [Usage Workflow](#-usage-workflow)
- [Protocol Compatibility](#-protocol-compatibility)
- [Disclaimer](#-disclaimer)
- [License](#-license)

---

## 🌟 Features

* **Multi-Band Support:** On-the-fly digital frequency switching across primary ISM bands:
  * `315.00 MHz` — US/Asia wireless devices and consumer automation
  * `433.92 MHz` — Global standard for consumer RF sockets, relays, and switches
  * `868.30 MHz` — European security, telemetry, and gate control systems
  * `915.00 MHz` — Americas industrial ISM band telemetry and sensors
* **Embedded Responsive Web Dashboard:** Hosted entirely on the ESP32 with zero external assets, CDNs, or apps needed.
* **Dynamic Theme Switcher:** 4 built-in CSS themes (Matrix Green, Cyberpunk Neon, Monochrome White, Light Mode) with persistent local storage.
* **Standalone SoftAP:** Runs an isolated local Wi-Fi Access Point (`Dodo-RF-Replicator`) for field operations.
* **Persistent Flash Storage:** Retains saved signals across power cycles using non-volatile flash storage (`Preferences.h`).
* **Database Backup & Import:** Export and import signal configurations in standard JSON format.
* **Real-Time Interrupt Processing:** Hardware interrupt-driven pulse demodulation and capture using `rc-switch` via the `GDO0` data line.

---

## 🛠️ Hardware & Pinout

### Required Components
* **ESP32 Dev Module** (30-pin)
* **CC1101 Sub-GHz Transceiver Module** (with Sub-GHz spring or whip antenna)
* **Jumper Wires**
* **Power Source:** Android smartphone with USB-OTG cable or external 3.3V/5V breadboard power module

### SPI Wiring Scheme

| CC1101 Pin | ESP32 Pin | Function | Notes |
| :--- | :--- | :--- | :--- |
| **VCC** | **3V3** | Power Rail | **Do NOT connect to 5V!** |
| **GND** | **GND** | Ground | Common system ground |
| **CSN** | **GPIO 5 (D5)** | Chip Select | Hardware SPI CS |
| **SCK** | **GPIO 18 (D18)**| SPI Clock | Hardware SPI SCK |
| **MISO** | **GPIO 19 (D19)**| Master In | Hardware SPI MISO |
| **MOSI** | **GPIO 23 (D23)**| Master Out | Hardware SPI MOSI |
| **GDO0** | **GPIO 2 (D2)** | Interrupt / Data | Real-time signal capture/transmit line |
| **GDO2** | *N/C* | Unused | Leave unconnected |

---

## ⚡ Power Configurations

* **Option A: Direct USB-OTG (Ultra-Portable):** Connect the ESP32 directly to a smartphone using a USB-OTG adapter. The phone powers the ESP32, and the ESP32’s onboard regulator delivers a stable **3.3V** supply to the CC1101 transceiver module via the `3V3` pin.
* **Option B: External Power (Benchtop):** Use an MB102 breadboard power supply to feed regulated **5V to ESP32 VIN** and **3.3V to the CC1101 VCC** with a unified common ground.

---

## 💻 Software Dependencies

Install the following libraries using the **Arduino Library Manager** (`Sketch > Include Library > Manage Libraries...`):

1. **`SmartRC-CC1101-Driver-Lib`** (by LSAT / LittleRookies)
2. **`rc-switch`** (by sui77)
3. **`ArduinoJson`** (by Benoit Blanchon)

---

## 🚀 Installation & Flashing

1. Clone or download this repository:
   ```bash
   git clone [https://github.com/your-username/Dodo-RF.git](https://github.com/your-username/Dodo-RF.git)
   Open Dodo_RF.ino in the Arduino IDE.

Under Tools > Board, select ESP32 Dev Module.

Select the serial port connected to your ESP32 (e.g., /dev/ttyUSB0 or COM3).

Set the Upload Speed to 921600 (or 115200 if flashing encounters timeouts).

Click Upload (Ctrl + U / Cmd + U).

🎨 Web Dashboard & Theming
The user interface is optimized for mobile touchscreens and features a real-time console and live signal stager:

Matrix Green (Default): Dark hacker aesthetic with glowing green indicators.

Cyberpunk Neon: High-contrast neon magenta and electric cyan highlights.

Monochrome White: Clean, crisp white-on-black interface for daylight readability.

Light Mode: High-contrast daylight theme with clean borders and dark typography.

📱 Usage Workflow
Power On: Power the device via USB-OTG or external 5V/3.3V source.

Connect to Wi-Fi:

SSID: Dodo-RF-Replicator

Password: esp32rf2026

Open Dashboard: Navigate to http://192.168.4.1 in your mobile or desktop browser.

Tune Frequency: Select your target RF band from the dropdown (e.g., 433.92 MHz).

Capture Signals:

Transmit a signal using a compatible fixed-code remote near the antenna.

Incoming packets will appear in real time in the Live Signal Console.

Save & Transmit:

Tap any raw packet in the console to stage it.

Enter a descriptive name (e.g., Desk Lamp) and click Save Signal.

Click Replay next to any saved entry to retransmit over the air.
🔍 Protocol CompatibilitySignal CategorySupportedExamplesNotesFixed-Code ASK / OOK✅ YesEV1527, PT2262, PT2260, HT6P20B, SC5262Standard wireless switches, sockets, doorbells, relay boardsRolling / Encrypted Code❌ NoKeeLoq, Hitag, AES Car Remotes, Secure Access GatesCryptographically signed rolling codes prevent replay by design

⚠️ Disclaimer
This project is created strictly for educational, prototyping, and authorized research purposes. Always ensure you have explicit authorization before capturing or transmitting radio signals on any target device or network.

📜 License
This project is open source under the MIT License.
