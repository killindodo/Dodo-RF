/*
  ============================================================================
  Dodo-RF — Multi-Band Sub-GHz Transceiver Dashboard & Replicator
  ============================================================================
  Target board : ESP32 Dev Module (30-pin)
  CC1101 Module: SPI Connections
    - VCC  -> ESP32 3V3
    - GND  -> ESP32 GND
    - MOSI -> ESP32 D23
    - MISO -> ESP32 D19
    - SCK  -> ESP32 D18
    - CSN  -> ESP32 D5
    - GDO0 -> ESP32 D2 (Interrupt/Data)
  
  REQUIRED LIBRARIES (install via Library Manager):
    1. "ArduinoJson" by Benoit Blanchon
    2. "SmartRC-CC1101-Driver-Lib" by LSAT / LittleRookies
    3. "rc-switch" by sui77
  ============================================================================
*/

#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <ELECHOUSE_CC1101_SRC_DRV.h>
#include <RCSwitch.h>

// ---------------------------------------------------------------------------
// PIN DEFINITIONS
// ---------------------------------------------------------------------------
#define CC1101_CSN   5
#define CC1101_GDO0  2
#define CC1101_SCK   18
#define CC1101_MISO  19
#define CC1101_MOSI  23

// ---------------------------------------------------------------------------
// CONFIG
// ---------------------------------------------------------------------------
const char* AP_SSID = "Dodo-RF-Replicator";
const char* AP_PASS = "esp32rf2026";
IPAddress AP_IP(192, 168, 4, 1);
IPAddress AP_GATEWAY(192, 168, 4, 1);
IPAddress AP_SUBNET(255, 255, 255, 0);

WebServer server(80);
Preferences prefs;
RCSwitch mySwitch = RCSwitch();

const char* PREF_NAMESPACE = "dodo_store";

// ---------------------------------------------------------------------------
// RUNTIME STATE
// ---------------------------------------------------------------------------
String currentState = "Listening";
float currentFreq = 433.92;

struct CapturedSignal {
  String rawCode;
  float frequency;
  unsigned long timestamp;
  unsigned int bitLength;
  unsigned int protocol;
  unsigned int pulseLength;
};
#define MAX_PENDING 10
CapturedSignal pendingQueue[MAX_PENDING];
int pendingCount = 0;

// ---------------------------------------------------------------------------
// EMBEDDED DASHBOARD (PROGMEM)
// ---------------------------------------------------------------------------
const char INDEX_HTML[] PROGMEM = R"HTML_CONTENT(
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<title>Dodo-RF Dashboard</title>
<style>
  :root {
    --bg: #000000;
    --panel-bg: #0a0a0a;
    --border: #113322;
    --green: #00FF66;
    --cyan: #00E5FF;
    --red: #FF2A6D;
    --text: #00FF66;
  }

  body.theme-cyberpunk {
    --bg: #05020a;
    --panel-bg: #12091f;
    --border: #44125e;
    --green: #00f0ff;
    --cyan: #ff007f;
    --red: #ff0055;
    --text: #00f0ff;
  }

  body.theme-monochrome {
    --bg: #0d0d0d;
    --panel-bg: #1a1a1a;
    --border: #333333;
    --green: #ffffff;
    --cyan: #cccccc;
    --red: #ff4444;
    --text: #ffffff;
  }

  body.theme-light {
    --bg: #f4f6f8;
    --panel-bg: #ffffff;
    --border: #d0d7de;
    --green: #0969da;
    --cyan: #1f2328;
    --red: #cf222e;
    --text: #1f2328;
  }

  * { box-sizing: border-box; }
  body {
    margin: 0; background: var(--bg); color: var(--text);
    font-family: 'Courier New', Courier, monospace; padding: 12px;
    max-width: 480px; margin: 0 auto; transition: background 0.3s, color 0.3s;
  }
  h1, h2 { text-transform: uppercase; letter-spacing: 1px; }
  .header {
    display: flex; justify-content: space-between; align-items: center;
    border-bottom: 1px solid var(--border); padding-bottom: 8px; margin-bottom: 12px;
  }
  .header h1 { font-size: 18px; color: var(--cyan); text-shadow: 0 0 4px var(--cyan); margin: 0; }
  .badge {
    padding: 4px 10px; border-radius: 4px; font-size: 12px; font-weight: bold;
    border: 1px solid currentColor; text-shadow: 0 0 4px currentColor;
  }
  .badge.idle { color: #888; }
  .badge.listening { color: var(--cyan); }
  .badge.transmitting { color: var(--red); animation: pulse 0.6s infinite alternate; }
  @keyframes pulse { from { opacity: 1; } to { opacity: 0.4; } }

  .panel {
    background: var(--panel-bg); border: 1px solid var(--border);
    border-radius: 6px; padding: 10px; margin-bottom: 14px;
    transition: background 0.3s, border-color 0.3s;
  }
  .panel h2 { font-size: 13px; color: var(--cyan); margin: 0 0 8px 0; }

  select {
    width: 100%; background: var(--bg); border: 1px solid var(--border); color: var(--cyan);
    padding: 8px; font-family: inherit; font-size: 12px; border-radius: 4px; margin-bottom: 8px;
    outline: none; transition: background 0.3s, color 0.3s;
  }
  select:focus { border-color: var(--cyan); }

  .console {
    background: var(--bg); border: 1px solid var(--border); border-radius: 4px;
    height: 160px; overflow-y: auto; padding: 8px; font-size: 12px;
  }
  .console-line {
    padding: 4px 6px; margin-bottom: 3px; cursor: pointer; border-radius: 3px;
    color: var(--green); text-shadow: 0 0 2px var(--green);
    border-left: 2px solid transparent;
  }
  .console-line:hover { background: rgba(0, 255, 102, 0.1); }
  .console-line.selected {
    border-left: 2px solid var(--cyan); background: rgba(0, 229, 255, 0.15);
    color: var(--cyan); text-shadow: 0 0 4px var(--cyan);
  }
  .console-empty { color: #666; font-size: 11px; }

  input[type=text] {
    width: 100%; background: var(--bg); border: 1px solid var(--border); color: var(--text);
    padding: 8px; font-family: inherit; font-size: 13px; border-radius: 4px; margin-bottom: 8px;
  }
  input[type=text]:focus { outline: none; border-color: var(--cyan); }

  .staged-code {
    font-size: 12px; color: var(--cyan); background: var(--bg); border: 1px dashed var(--border);
    padding: 6px; border-radius: 4px; margin-bottom: 8px; min-height: 18px; word-break: break-all;
  }

  button {
    font-family: inherit; font-size: 13px; padding: 8px 12px; border-radius: 4px;
    border: 1px solid currentColor; background: transparent; cursor: pointer;
    text-transform: uppercase; letter-spacing: 0.5px;
  }
  button.primary { color: var(--green); }
  button.primary:disabled { color: #555; border-color: #444; cursor: not-allowed; }
  button.small { font-size: 11px; padding: 5px 8px; }
  button:not(:disabled):hover { box-shadow: 0 0 6px currentColor; }

  .file-import { display: flex; align-items: center; gap: 8px; margin-top: 4px; flex-wrap: wrap; }
  .file-import label {
    border: 1px solid var(--cyan); color: var(--cyan); padding: 6px 10px; border-radius: 4px;
    font-size: 12px; cursor: pointer;
  }
  .file-import input[type=file] { display: none; }
  .import-status { font-size: 11px; color: #888; }

  table { width: 100%; border-collapse: collapse; font-size: 12px; }
  th, td { text-align: left; padding: 6px 4px; border-bottom: 1px solid var(--border); }
  th { color: var(--cyan); text-transform: uppercase; font-size: 10px; }
  td.code { color: var(--green); word-break: break-all; }
  .empty-row td { color: #777; text-align: center; padding: 12px; }
</style>
</head>
<body>

<div class="header">
  <h1>&gt; Dodo-RF</h1>
  <span id="statusBadge" class="badge idle">IDLE</span>
</div>

<div class="panel">
  <h2>UI Theme Selector</h2>
  <select id="themeSelect">
    <option value="default" selected>Matrix Green (Dark)</option>
    <option value="theme-cyberpunk">Cyberpunk Neon</option>
    <option value="theme-monochrome">Monochrome White</option>
    <option value="theme-light">Light Mode</option>
  </select>
</div>

<div class="panel">
  <h2>Frequency Band Selector</h2>
  <select id="freqSelect">
    <option value="315.00">315.00 MHz (Common in US/Asia for car fobs and home automation)</option>
    <option value="433.92" selected>433.92 MHz (Global default for standard remotes)</option>
    <option value="868.30">868.30 MHz (Common in Europe for alarms/gates)</option>
    <option value="915.00">915.00 MHz (Common in the Americas)</option>
  </select>
</div>

<div class="panel">
  <h2>Import Backup</h2>
  <div class="file-import">
    <label for="fileInput">Choose File</label>
    <input type="file" id="fileInput" accept="application/json">
    <span class="import-status" id="importStatus">No file selected</span>
  </div>
</div>

<div class="panel">
  <h2>Live Signal Console</h2>
  <div class="console" id="console">
    <div class="console-empty">Waiting for RF activity...</div>
  </div>
</div>

<div class="panel">
  <h2>Save Captured Signal</h2>
  <input type="text" id="signalName" placeholder="Enter Signal Name (e.g., Garage Door)">
  <div class="staged-code" id="stagedCode">No signal staged</div>
  <button class="primary" id="saveBtn" disabled>Save Signal</button>
</div>

<div class="panel">
  <h2>Saved Signals</h2>
  <table>
    <thead><tr><th>Name</th><th>Freq</th><th>Raw Code</th><th></th></tr></thead>
    <tbody id="signalsBody">
      <tr class="empty-row"><td colspan="4">No saved signals</td></tr>
    </tbody>
  </table>
</div>

<script>
(function () {
  let stagedCode = null;

  const statusBadge  = document.getElementById('statusBadge');
  const themeSelect   = document.getElementById('themeSelect');
  const freqSelect    = document.getElementById('freqSelect');
  const consoleEl     = document.getElementById('console');
  const stagedCodeEl  = document.getElementById('stagedCode');
  const nameInput     = document.getElementById('signalName');
  const saveBtn        = document.getElementById('saveBtn');
  const signalsBody   = document.getElementById('signalsBody');
  const fileInput      = document.getElementById('fileInput');
  const importStatus  = document.getElementById('importStatus');

  // Theme Switching Logic
  const savedTheme = localStorage.getItem('dodo_rf_theme') || 'default';
  themeSelect.value = savedTheme;
  if (savedTheme !== 'default') {
    document.body.className = savedTheme;
  }

  themeSelect.addEventListener('change', () => {
    const theme = themeSelect.value;
    document.body.className = theme === 'default' ? '' : theme;
    localStorage.setItem('dodo_rf_theme', theme);
  });

  function setBadge(state) {
    statusBadge.textContent = state.toUpperCase();
    statusBadge.className = 'badge ' + state.toLowerCase();
  }

  freqSelect.addEventListener('change', async () => {
    const selectedFreq = freqSelect.value;
    try {
      await fetch('/api/set_freq', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ freq: parseFloat(selectedFreq) })
      });
    } catch (e) {
      console.error('Failed to change frequency', e);
    }
  });

  function addConsoleLine(entry) {
    const placeholder = consoleEl.querySelector('.console-empty');
    if (placeholder) placeholder.remove();

    const line = document.createElement('div');
    line.className = 'console-line';
    line.dataset.code = entry.code;
    const time = new Date(entry.timestamp || Date.now()).toLocaleTimeString();
    line.textContent = '[' + time + '] ' + entry.frequency + 'MHz  ' + entry.code;

    line.addEventListener('click', () => {
      document.querySelectorAll('.console-line.selected').forEach(el => el.classList.remove('selected'));
      line.classList.add('selected');
      stageSignal(entry.code);
    });

    consoleEl.appendChild(line);
    consoleEl.scrollTop = consoleEl.scrollHeight;
  }

  function stageSignal(code) {
    stagedCode = code;
    stagedCodeEl.textContent = code;
    updateSaveBtn();
  }

  function updateSaveBtn() {
    saveBtn.disabled = !(stagedCode && nameInput.value.trim().length > 0);
  }
  nameInput.addEventListener('input', updateSaveBtn);

  saveBtn.addEventListener('click', async () => {
    if (!stagedCode || !nameInput.value.trim()) return;
    try {
      const res = await fetch('/api/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: nameInput.value.trim(),
          code: stagedCode,
          freq: parseFloat(freqSelect.value)
        })
      });
      if (res.ok) {
        nameInput.value = '';
        stagedCode = null;
        stagedCodeEl.textContent = 'No signal staged';
        updateSaveBtn();
        document.querySelectorAll('.console-line.selected').forEach(el => el.classList.remove('selected'));
        loadSignals();
      }
    } catch (e) {
      console.error('Save failed', e);
    }
  });

  function renderSignals(list) {
    signalsBody.innerHTML = '';
    if (!list || !Array.isArray(list) || list.length === 0) {
      signalsBody.innerHTML = '<tr class="empty-row"><td colspan="4">No saved signals</td></tr>';
      return;
    }
    list.forEach(sig => {
      const tr = document.createElement('tr');
      const freqDisplay = sig.freq ? sig.freq + 'M' : '433.92M';
      tr.innerHTML =
        '<td>' + escapeHtml(sig.name) + '</td>' +
        '<td>' + escapeHtml(freqDisplay) + '</td>' +
        '<td class="code">' + escapeHtml(sig.code) + '</td>' +
        '<td><button class="small primary replay-btn" data-id="' + sig.id + '">Replay</button></td>';
      signalsBody.appendChild(tr);
    });

    document.querySelectorAll('.replay-btn').forEach(btn => {
      btn.addEventListener('click', () => replaySignal(btn.dataset.id, btn));
    });
  }

  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str || '';
    return div.innerHTML;
  }

  async function loadSignals() {
    try {
      const res = await fetch('/api/signals');
      const data = await res.json();
      renderSignals(data);
    } catch (e) {
      console.error('Failed to load signals', e);
    }
  }

  async function replaySignal(id, btn) {
    btn.disabled = true;
    try {
      await fetch('/api/replay', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: parseInt(id, 10) })
      });
    } catch (e) {
      console.error('Replay failed', e);
    } finally {
      btn.disabled = false;
    }
  }

  fileInput.addEventListener('change', async () => {
    const file = fileInput.files[0];
    if (!file) return;
    importStatus.textContent = 'Reading ' + file.name + '...';
    try {
      const text = await file.text();
      const parsed = JSON.parse(text);
      const res = await fetch('/api/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(parsed)
      });
      const result = await res.json();
      if (res.ok) {
        importStatus.textContent = 'Imported ' + result.count + ' signal(s)';
        loadSignals();
      } else {
        importStatus.textContent = 'Import failed: ' + (result.error || 'unknown error');
      }
    } catch (e) {
      importStatus.textContent = 'Invalid file: ' + e.message;
    }
    fileInput.value = '';
  });

  async function pollStatus() {
    try {
      const res = await fetch('/api/status');
      const data = await res.json();
      setBadge(data.state || 'Idle');
      if (data.freq) {
        freqSelect.value = data.freq.toFixed(2);
      }
      if (data.new_signals && data.new_signals.length) {
        data.new_signals.forEach(addConsoleLine);
      }
    } catch (e) {
      statusBadge.textContent = 'OFFLINE';
      statusBadge.className = 'badge idle';
    }
  }

  loadSignals();
  pollStatus();
  setInterval(pollStatus, 1000);
})();
</script>

</body>
</html>
)HTML_CONTENT";

// ---------------------------------------------------------------------------
// PREFERENCES HELPERS
// ---------------------------------------------------------------------------
String loadSignalsJson() {
  prefs.begin(PREF_NAMESPACE, true);
  String json = prefs.getString("signals", "[]");
  prefs.end();
  return json;
}

void saveSignalsJson(const String &json) {
  prefs.begin(PREF_NAMESPACE, false);
  prefs.putString("signals", json);
  prefs.end();
}

int getNextId() {
  prefs.begin(PREF_NAMESPACE, true);
  int id = prefs.getInt("next_id", 1);
  prefs.end();
  return id;
}

void setNextId(int id) {
  prefs.begin(PREF_NAMESPACE, false);
  prefs.putInt("next_id", id);
  prefs.end();
}

// ---------------------------------------------------------------------------
// ROUTE HANDLERS
// ---------------------------------------------------------------------------
void handleRoot() {
  server.send_P(200, "text/html", INDEX_HTML);
}

void handleStatus() {
  JsonDocument doc;
  doc["state"] = currentState;
  doc["freq"]  = currentFreq;

  JsonArray newSignals = doc["new_signals"].to<JsonArray>();
  for (int i = 0; i < pendingCount; i++) {
    JsonObject o = newSignals.add<JsonObject>();
    o["code"]      = pendingQueue[i].rawCode;
    o["frequency"] = pendingQueue[i].frequency;
    o["timestamp"] = pendingQueue[i].timestamp;
  }
  pendingCount = 0;

  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handleSetFreq() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"missing body\"}");
    return;
  }

  JsonDocument inDoc;
  DeserializationError err = deserializeJson(inDoc, server.arg("plain"));
  if (err || !inDoc["freq"].is<float>()) {
    server.send(400, "application/json", "{\"error\":\"invalid frequency\"}");
    return;
  }

  float newFreq = inDoc["freq"].as<float>();
  currentFreq = newFreq;

  ELECHOUSE_cc1101.setMHZ(currentFreq);
  ELECHOUSE_cc1101.SetRx();

  Serial.printf("[CC1101] Tuned to: %.2f MHz\n", currentFreq);
  server.send(200, "application/json", "{\"status\":\"frequency set\"}");
}

void handleGetSignals() {
  String json = loadSignalsJson();
  server.send(200, "application/json", json);
}

void handleSave() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"missing body\"}");
    return;
  }

  JsonDocument inDoc;
  DeserializationError err = deserializeJson(inDoc, server.arg("plain"));
  if (err || !inDoc["name"].is<String>() || !inDoc["code"].is<String>()) {
    server.send(400, "application/json", "{\"error\":\"invalid payload\"}");
    return;
  }
  String name = inDoc["name"].as<String>();
  String code = inDoc["code"].as<String>();
  float freq  = inDoc["freq"].is<float>() ? inDoc["freq"].as<float>() : currentFreq;

  JsonDocument doc;
  deserializeJson(doc, loadSignalsJson());
  JsonArray arr = doc.as<JsonArray>();
  if (arr.isNull()) arr = doc.to<JsonArray>();

  int newId = getNextId();
  JsonObject entry = arr.add<JsonObject>();
  entry["id"]   = newId;
  entry["name"] = name;
  entry["code"] = code;
  entry["freq"] = freq;

  String out;
  serializeJson(doc, out);
  saveSignalsJson(out);
  setNextId(newId + 1);

  server.send(200, "application/json", "{\"status\":\"saved\",\"id\":" + String(newId) + "}");
}

void handleReplay() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"missing body\"}");
    return;
  }

  JsonDocument inDoc;
  DeserializationError err = deserializeJson(inDoc, server.arg("plain"));
  if (err || !inDoc["id"].is<int>()) {
    server.send(400, "application/json", "{\"error\":\"invalid payload\"}");
    return;
  }
  int id = inDoc["id"].as<int>();

  JsonDocument doc;
  deserializeJson(doc, loadSignalsJson());
  JsonArray arr = doc.as<JsonArray>();

  for (JsonObject o : arr) {
    if (o["id"].as<int>() == id) {
      String codeStr   = o["code"].as<String>();
      String name      = o["name"].as<String>();
      float targetFreq = o["freq"].is<float>() ? o["freq"].as<float>() : currentFreq;

      currentState = "Transmitting";

      ELECHOUSE_cc1101.setMHZ(targetFreq);
      
      unsigned long value = strtoul(codeStr.c_str(), NULL, 0);

      ELECHOUSE_cc1101.SetTx();
      mySwitch.enableTransmit(CC1101_GDO0);
      
      mySwitch.send(value, 24);
      
      mySwitch.disableTransmit();

      ELECHOUSE_cc1101.setMHZ(currentFreq);
      ELECHOUSE_cc1101.SetRx();
      mySwitch.enableReceive(CC1101_GDO0);

      currentState = "Listening";

      server.send(200, "application/json", "{\"status\":\"transmitted\",\"id\":" + String(id) + "}");
      return;
    }
  }
  server.send(404, "application/json", "{\"error\":\"signal not found\"}");
}

void handleImport() {
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"error\":\"missing body\"}");
    return;
  }

  JsonDocument inDoc;
  DeserializationError err = deserializeJson(inDoc, server.arg("plain"));
  if (err || !inDoc.is<JsonArray>()) {
    server.send(400, "application/json", "{\"error\":\"expected a JSON array of signals\"}");
    return;
  }
  JsonArray incoming = inDoc.as<JsonArray>();

  JsonDocument doc;
  deserializeJson(doc, loadSignalsJson());
  JsonArray arr = doc.as<JsonArray>();
  if (arr.isNull()) arr = doc.to<JsonArray>();

  int nextId = getNextId();
  int imported = 0;

  for (JsonObject o : incoming) {
    if (!o["name"].is<String>() || !o["code"].is<String>()) continue;
    JsonObject entry = arr.add<JsonObject>();
    entry["id"]   = nextId++;
    entry["name"] = o["name"].as<String>();
    entry["code"] = o["code"].as<String>();
    entry["freq"] = o["freq"].is<float>() ? o["freq"].as<float>() : 433.92;
    imported++;
  }

  String out;
  serializeJson(doc, out);
  saveSignalsJson(out);
  setNextId(nextId);

  server.send(200, "application/json", "{\"status\":\"imported\",\"count\":" + String(imported) + "}");
}

void handleNotFound() {
  server.send(404, "text/plain", "Not found");
}

// ---------------------------------------------------------------------------
// SETUP / LOOP
// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n[BOOT] Dodo-RF Starting...");

  ELECHOUSE_cc1101.setSpiPin(CC1101_SCK, CC1101_MISO, CC1101_MOSI, CC1101_CSN);
  ELECHOUSE_cc1101.Init();
  ELECHOUSE_cc1101.setMHZ(currentFreq);
  ELECHOUSE_cc1101.SetRx();

  mySwitch.enableReceive(CC1101_GDO0);

  Serial.printf("[CC1101] Dodo-RF listening on %.2f MHz\n", currentFreq);

  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(AP_IP, AP_GATEWAY, AP_SUBNET);
  WiFi.softAP(AP_SSID, AP_PASS);
  Serial.print("[WIFI] AP SSID: ");
  Serial.println(AP_SSID);
  Serial.print("[WIFI] AP IP:   ");
  Serial.println(WiFi.softAPIP());

  server.on("/", HTTP_GET, handleRoot);
  server.on("/api/status", HTTP_GET, handleStatus);
  server.on("/api/set_freq", HTTP_POST, handleSetFreq);
  server.on("/api/signals", HTTP_GET, handleGetSignals);
  server.on("/api/save", HTTP_POST, handleSave);
  server.on("/api/replay", HTTP_POST, handleReplay);
  server.on("/api/import", HTTP_POST, handleImport);
  server.onNotFound(handleNotFound);

  server.begin();
  Serial.println("[HTTP] Dodo-RF Web Server live on port 80");
}

void loop() {
  server.handleClient();

  if (mySwitch.available()) {
    unsigned long receivedValue = mySwitch.getReceivedValue();

    if (receivedValue != 0 && pendingCount < MAX_PENDING) {
      char hexBuffer[16];
      snprintf(hexBuffer, sizeof(hexBuffer), "0x%X", (unsigned int)receivedValue);

      CapturedSignal sig;
      sig.rawCode     = String(hexBuffer);
      sig.frequency   = currentFreq;
      sig.timestamp   = millis();
      sig.bitLength   = mySwitch.getReceivedBitlength();
      sig.protocol    = mySwitch.getReceivedProtocol();
      sig.pulseLength = mySwitch.getReceivedDelay();

      pendingQueue[pendingCount++] = sig;

      Serial.printf("[Dodo-RF RX] Freq: %.2f MHz | Code: %s | Bits: %d\n",
                    currentFreq, sig.rawCode.c_str(), sig.bitLength);
    }

    mySwitch.resetAvailable();
  }
}