
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

const authScreen = $("#authScreen");
const appShell = $("#appShell");
const loginPanel = $("#loginPanel");
const signupPanel = $("#signupPanel");

let currentUser = null;
let currentRoom = "All";
let devices = [];
let activity = [];

let safetyState = {
    safetyWarning: false,
    currentPower: 0,
    safetyLimit: 1500
  };
  

const demo = {
  musicPlaying: false,
  liked: false,
  track: 0,
  brightness: 80,
  temperature: 23,
  acMode: "Cooling",
  vacuumRunning: false,
  vacuumDocked: false,
  cameraOn: true,
  wifiOn: true,
  energyPeriod: "Today"
};

const tracks = [
  "Relax at home",
  "Morning atmosphere",
  "Evening calm"
];

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    }
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(
      data.message || data.error || `Request failed (${response.status})`
    );
  }

  return data;
}

function setMessage(selector, message) {
  const element = $(selector);
  if (element) element.textContent = message;
}

function showLogin() {
  if (authScreen) authScreen.hidden = false;
  if (appShell) appShell.hidden = true;
  if (loginPanel) loginPanel.hidden = false;
  if (signupPanel) signupPanel.hidden = true;

  setMessage("#loginMessage", "");
  setMessage("#signupMessage", "");
}

function showSignup() {
  if (loginPanel) loginPanel.hidden = true;
  if (signupPanel) signupPanel.hidden = false;

  setMessage("#loginMessage", "");
  setMessage("#signupMessage", "");
}

function showDashboard(user) {
  currentUser = user;
  authScreen.hidden = true;
  appShell.hidden = false;

  const userName = $("#userName");
  if (userName) {
    userName.textContent = user.name || user.email || "Home Owner";
  }
}

$("#showSignup")?.addEventListener("click", showSignup);
$("#showLogin")?.addEventListener("click", showLogin);

// ---------------- AUTHENTICATION ----------------

$("#signupForm")?.addEventListener("submit", async (event) => {
  event.preventDefault();

  const button = event.submitter || event.currentTarget.querySelector(
    'button[type="submit"]'
  );

  setMessage("#signupMessage", "");
  if (button) button.disabled = true;

  try {
    const name = $("#signupName").value.trim();
    const email = $("#signupEmail").value.trim();
    const password = $("#signupPassword").value;

    await api("/api/signup", {
      method: "POST",
      body: JSON.stringify({ name, email, password })
    });

    setMessage("#signupMessage", "Account created. Please log in.");
    $("#signupForm").reset();

    loginPanel.hidden = false;
    signupPanel.hidden = true;
    $("#loginEmail").value = email;
  } catch (error) {
    setMessage("#signupMessage", error.message);
  } finally {
    if (button) button.disabled = false;
  }
});

$("#loginForm")?.addEventListener("submit", async (event) => {
  event.preventDefault();

  const button = event.submitter || event.currentTarget.querySelector(
    'button[type="submit"]'
  );

  setMessage("#loginMessage", "");
  if (button) button.disabled = true;

  try {
    const email = $("#loginEmail").value.trim();
    const password = $("#loginPassword").value;

    const data = await api("/api/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    });

    await openDashboard(data.user || data);
  } catch (error) {
    setMessage("#loginMessage", error.message);
  } finally {
    if (button) button.disabled = false;
  }
});

// ---------------- DASHBOARD STATE ----------------

async function refreshState() {
  const data = await api("/api/state");

  devices = Array.isArray(data.devices) ? data.devices : [];
  activity = Array.isArray(data.history) ? data.history : [];

  renderDevices();
  renderSummary();
  renderActivity();
}

async function refreshState() {
    const data = await api("/api/state");
  
    devices = Array.isArray(data.devices) ? data.devices : [];
    activity = Array.isArray(data.history) ? data.history : [];
  
    safetyState = {
      safetyWarning: Boolean(data.safetyWarning),
      currentPower: Number(data.currentPower ?? 0),
      safetyLimit: Number(data.safetyLimit ?? 1500)
    };
  
    renderDevices();
    renderSummary();
    renderActivity();
  }
  

async function openDashboard(user) {
  showDashboard(user);

  try {
    await refreshState();
    renderRoomFilters();
    updateMusicUI();
    updateBrightnessUI();
    updateACUI();
    updateVacuumUI();
    updateCameraUI();
    updateWifiUI();
    updateEnergyPeriodUI();
  } catch (error) {
    console.error("Could not load home state:", error);
    alert("Login successful, but home state could not load: " + error.message);
  }
}

function isDeviceOn(device) {
  return Boolean(device.on ?? device.isOn ?? device.state === "ON");
}

function findDevice(card) {
  if (!card) return null;

  return devices.find((item) =>
    item.id === card.dataset.id ||
    item.name === card.dataset.name
  );
}

function renderDevices() {
  $$(".appliance-card").forEach((card) => {
    const device = findDevice(card);

    if (!device) {
      card.hidden = true;
      return;
    }

    card.hidden =
      currentRoom !== "All" && card.dataset.room !== currentRoom;

    const isOn = isDeviceOn(device);
    const status = card.querySelector(".device-status");
    const toggle = card.querySelector(".toggle-btn");
    const indicator = card.querySelector(".power-indicator");

    if (status) status.textContent = isOn ? "ON" : "OFF";
    if (toggle) toggle.textContent = isOn ? "Turn off" : "Turn on";
    if (indicator) {
      indicator.classList.toggle("is-off", !isOn);
      indicator.setAttribute("aria-label", isOn ? "Device on" : "Device off");
    }

    card.classList.toggle("is-on", isOn);
  });
}


function renderSummary() {
    const activeDevices = devices.filter(isDeviceOn);
  
    const watts = activeDevices.reduce((total, device) => {
      return total + Number(device.power ?? device.watts ?? 0);
    }, 0);
  
    const limit = Math.max(1, Number(safetyState?.safetyLimit) || 1500);
    const energy = watts * 6 / 1000;
  
    if ($("#currentPower")) {
      $("#currentPower").textContent = `${watts} W`;
    }
  
    if ($("#dailyEnergy")) {
      $("#dailyEnergy").textContent = `${energy.toFixed(2)} kWh`;
    }
  
    if ($("#dailyBill")) {
      $("#dailyBill").textContent =
        `Estimated bill: ₹${(energy * 8).toFixed(2)}/day`;
    }
  
    if ($("#runningCount")) {
      $("#runningCount").textContent =
        `${activeDevices.length} / ${devices.length}`;
    }
  
    if ($("#meterValue")) {
      $("#meterValue").textContent = `${watts} W`;
    }
  
    const fill = $("#energyMeterFill");
  
    if (fill) {
      fill.style.width = `${Math.min((watts / limit) * 100, 100)}%`;
      fill.style.background = watts > limit ? "#ff5c5c" : "";
    }
  
    const maxLabel = $(".meter-labels span:last-child");
  
    if (maxLabel) {
      maxLabel.textContent = `${limit} W`;
    }
  
    if ($("#miniEnergy")) {
      $("#miniEnergy").textContent = `${watts} W`;
    }
  
    let warning = $("#safetyWarningMessage");
    const meterLabels = $(".meter-labels");
  
    if (meterLabels && !warning) {
      warning = document.createElement("p");
      warning.id = "safetyWarningMessage";
      warning.setAttribute("role", "status");
      warning.style.marginTop = "12px";
      warning.style.fontWeight = "600";
      meterLabels.insertAdjacentElement("afterend", warning);
    }
  
    if (warning) {
      if (safetyState?.safetyWarning || watts > limit) {
        warning.textContent =
          `⚠ Safety warning: ${watts} W exceeds the ${limit} W demo limit.`;
        warning.style.color = "#ff7777";
      } else {
        warning.textContent =
          `✓ Power load is within the ${limit} W demo limit.`;
        warning.style.color = "#42d9a0";
      }
    }
  }
  

function renderActivity() {
  const container = $("#activityLog");
  if (!container) return;

  container.replaceChildren();

  if (!activity.length) {
    const empty = document.createElement("p");
    empty.className = "empty-state";
    empty.textContent = "No activity yet. Switch on an appliance to begin.";
    container.append(empty);
    return;
  }

  activity.slice(0, 10).forEach((entry) => {
    const item = document.createElement("p");
    item.className = "activity-entry";
    item.textContent = entry.message ||
      `${entry.device || entry.name || "Appliance"}: ${entry.action || "Updated"}`;
    container.append(item);
  });
}

// ---------------- ROOM NAVIGATION ----------------

function selectRoom(room) {
  currentRoom = room;

  $$(".room-link").forEach((button) => {
    button.classList.toggle("active", button.dataset.room === room);
  });

  $$(".top-room").forEach((button) => {
    button.classList.toggle("active", button.dataset.room === room);
  });

  renderDevices();

  $(".sidebar")?.classList.remove("mobile-open");
}

$$(".room-link, .top-room").forEach((button) => {
  button.addEventListener("click", () => {
    selectRoom(button.dataset.room);
  });
});

function renderRoomFilters() {
  selectRoom(currentRoom);
}

$("#mobileMenu")?.addEventListener("click", () => {
  $(".sidebar")?.classList.toggle("mobile-open");
});

// ---------------- APPLIANCE ON/OFF ----------------

$("#applianceGrid")?.addEventListener("click", async (event) => {
  const button = event.target.closest(".toggle-btn");
  if (!button) return;

  const card = button.closest(".appliance-card");
  const device = findDevice(card);
  if (!device) return;

  button.disabled = true;

  try {
    await api("/api/toggle", {
      method: "POST",
      body: JSON.stringify({ id: device.id })
    });

    await refreshState();
  } catch (error) {
    alert("Could not update appliance: " + error.message);
  } finally {
    button.disabled = false;
  }
});

$("#turnAllOff")?.addEventListener("click", async () => {
  const button = $("#turnAllOff");
  if (button) button.disabled = true;

  try {
    await api("/api/all-off", {
      method: "POST",
      body: JSON.stringify({})
    });

    await refreshState();
  } catch (error) {
    alert("Could not switch appliances off: " + error.message);
  } finally {
    if (button) button.disabled = false;
  }
});

// ---------------- BRIGHTNESS CONTROL ----------------

const brightnessSlider = $(".brightness-row input[type='range']");

if (brightnessSlider) {
  demo.brightness = Number(brightnessSlider.value);

  brightnessSlider.addEventListener("input", () => {
    demo.brightness = Number(brightnessSlider.value);
    updateBrightnessUI();
  });
}

function updateBrightnessUI() {
  const value = Math.max(0, Math.min(100, demo.brightness));
  const dial = $(".brightness-dial strong");
  const lamp = $(".lamp-glow");
  const shade = $(".lamp-shade");

  if (dial) dial.textContent = `${value}%`;

  if (lamp) {
    lamp.style.opacity = String(value / 100);
  }

  if (shade) {
    shade.style.filter = `brightness(${0.45 + value / 180})`;
  }

  if (brightnessSlider) {
    brightnessSlider.setAttribute("aria-valuetext", `${value}%`);
  }

  const lightingCard = $(".lighting-panel");
  lightingCard?.classList.toggle("light-dimmed", value === 0);
}

// ---------------- MUSIC CONTROLS ----------------

function updateMusicUI() {
  const title = $(".music-info h3");
  const playButton = $(".play-button");
  const heart = $(".music-heart");
  const progress = $(".music-progress span");
  const disc = $(".music-disc");
  const controls = $$(".music-controls span");

  if (title) title.textContent = tracks[demo.track];
  if (playButton) {
    playButton.textContent = demo.musicPlaying ? "Ⅱ" : "▶";
    playButton.setAttribute(
      "aria-label",
      demo.musicPlaying ? "Pause music" : "Play music"
    );
    playButton.title = demo.musicPlaying ? "Pause" : "Play";
  }

  if (heart) {
    heart.textContent = demo.liked ? "♥" : "♡";
    heart.style.color = demo.liked ? "#ff6688" : "";
    heart.setAttribute("aria-label", demo.liked ? "Unlike track" : "Like track");
  }

  if (progress) {
    progress.style.width = demo.musicPlaying ? "65%" : "35%";
  }

  if (disc) {
    disc.classList.toggle("playing", demo.musicPlaying);
  }

  controls.forEach((control) => {
    control.style.cursor = "pointer";
    control.setAttribute("role", "button");
    control.setAttribute("tabindex", "0");
  });
}

function changeTrack(direction) {
  demo.track = (demo.track + direction + tracks.length) % tracks.length;
  updateMusicUI();
}

const musicControls = $$(".music-controls span");

if (musicControls[0]) {
  musicControls[0].addEventListener("click", () => changeTrack(-1));
}

if (musicControls[1]) {
  musicControls[1].addEventListener("click", () => {
    demo.musicPlaying = !demo.musicPlaying;
    updateMusicUI();
  });
}

if (musicControls[2]) {
  musicControls[2].addEventListener("click", () => changeTrack(1));
}

$(".music-heart")?.addEventListener("click", () => {
  demo.liked = !demo.liked;
  updateMusicUI();
});

$(".music-heart")?.addEventListener("keydown", (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    demo.liked = !demo.liked;
    updateMusicUI();
  }
});

// ---------------- AIR CONDITIONER ----------------

$$(".ac-mode").forEach((button) => {
  button.addEventListener("click", () => {
    demo.acMode = button.dataset.mode || "Cooling";
    updateACUI();
  });
});

function updateACUI() {
  $$(".ac-mode").forEach((button) => {
    button.classList.toggle("active", button.dataset.mode === demo.acMode);
  });

  const label = $(".temperature-inner span");
  const value = $("#temperatureValue");

  if (label) label.textContent = demo.acMode;
  if (value) value.textContent = `${demo.temperature}°C`;

  const dial = $(".temperature-dial");
  if (dial) {
    dial.title = "Click to increase temperature";
    dial.style.cursor = "pointer";
    dial.setAttribute("role", "button");
    dial.setAttribute("tabindex", "0");
    dial.setAttribute("aria-label", `Temperature ${demo.temperature} degrees Celsius`);
  }

  const knob = $(".dial-knob");
  if (knob) {
    knob.style.left = `${((demo.temperature - 10) / 20) * 100}%`;
  }
}

function changeTemperature(amount) {
  demo.temperature = Math.max(10, Math.min(30, demo.temperature + amount));
  updateACUI();
}

$(".temperature-dial")?.addEventListener("click", (event) => {
  if (event.target.closest(".dial-knob")) {
    changeTemperature(1);
    return;
  }

  const bounds = event.currentTarget.getBoundingClientRect();
  const position = (event.clientX - bounds.left) / bounds.width;
  demo.temperature = Math.round(10 + Math.max(0, Math.min(1, position)) * 20);
  updateACUI();
});

$(".temperature-dial")?.addEventListener("keydown", (event) => {
  if (event.key === "ArrowUp" || event.key === "ArrowRight") {
    event.preventDefault();
    changeTemperature(1);
  } else if (event.key === "ArrowDown" || event.key === "ArrowLeft") {
    event.preventDefault();
    changeTemperature(-1);
  }
});

// ---------------- ROBOT VACUUM ----------------

const vacuumButtons = $$(".vacuum-actions .demo-action");

function updateVacuumUI() {
  const panel = $(".vacuum-panel");
  const robot = $(".vacuum-robot");
  const buttons = $$(".vacuum-actions .demo-action");

  if (panel) {
    panel.classList.toggle("vacuum-running", demo.vacuumRunning);
    panel.classList.toggle("vacuum-docked", demo.vacuumDocked);
  }

  if (robot) {
    robot.title = demo.vacuumDocked
      ? "Vacuum docked"
      : demo.vacuumRunning
        ? "Vacuum cleaning"
        : "Vacuum stopped";
  }

  if (buttons[0]) {
    buttons[0].textContent = demo.vacuumDocked ? "Undock" : "Go dock";
  }

  if (buttons[1]) {
    buttons[1].textContent = demo.vacuumRunning ? "Stop" : "Start";
  }

  const description = $(".vacuum-panel .muted-small");
  if (description) {
    description.textContent = demo.vacuumDocked
      ? "Robot is at the dock"
      : demo.vacuumRunning
        ? "Cleaning in progress"
        : "Smart cleaning concept";
  }
}

if (vacuumButtons[0]) {
  vacuumButtons[0].addEventListener("click", () => {
    demo.vacuumDocked = !demo.vacuumDocked;

    if (demo.vacuumDocked) {
      demo.vacuumRunning = false;
    }

    updateVacuumUI();
  });
}

if (vacuumButtons[1]) {
  vacuumButtons[1].addEventListener("click", () => {
    demo.vacuumRunning = !demo.vacuumRunning;

    if (demo.vacuumRunning) {
      demo.vacuumDocked = false;
    }

    updateVacuumUI();
  });
}

// ---------------- CAMERA DEMO ----------------

$(".camera-panel .power-indicator")?.addEventListener("click", () => {
  demo.cameraOn = !demo.cameraOn;
  updateCameraUI();
});

function updateCameraUI() {
  const panel = $(".camera-panel");
  const indicator = $(".camera-panel .power-indicator");
  const description = $(".camera-panel .muted-small");

  panel?.classList.toggle("demo-off", !demo.cameraOn);

  if (indicator) {
    indicator.title = demo.cameraOn ? "Turn camera off" : "Turn camera on";
    indicator.style.cursor = "pointer";
    indicator.setAttribute("role", "button");
    indicator.setAttribute("tabindex", "0");
    indicator.setAttribute("aria-label", indicator.title);
  }

  if (description) {
    description.textContent = demo.cameraOn
      ? "Home monitoring · Active"
      : "Camera is switched off";
  }
}

// ---------------- WI-FI DEMO ----------------

$(".wifi-card .power-indicator")?.addEventListener("click", () => {
  demo.wifiOn = !demo.wifiOn;
  updateWifiUI();
});

function updateWifiUI() {
  const card = $(".wifi-card");
  const indicator = $(".wifi-card .power-indicator");
  const label = $(".wifi-card .connected-label");

  card?.classList.toggle("demo-off", !demo.wifiOn);

  if (label) label.textContent = demo.wifiOn ? "Connected" : "Disconnected";

  if (indicator) {
    indicator.title = demo.wifiOn ? "Disconnect Wi-Fi" : "Connect Wi-Fi";
    indicator.style.cursor = "pointer";
    indicator.setAttribute("role", "button");
    indicator.setAttribute("tabindex", "0");
    indicator.setAttribute("aria-label", indicator.title);
  }
}

// Allow keyboard activation for demo power indicators.
[".camera-panel .power-indicator", ".wifi-card .power-indicator"].forEach(
  (selector) => {
    $(selector)?.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        event.currentTarget.click();
      }
    });
  }
);

// ---------------- ENERGY PERIOD FILTER ----------------

const energyPill = $(".electricity-card .mini-pill");
const energyPeriods = ["Today", "Week", "Month"];

function updateEnergyPeriodUI() {
  if (energyPill) {
    energyPill.textContent = `${demo.energyPeriod} ⌄`;
    energyPill.style.cursor = "pointer";
    energyPill.title = "Change energy period";
    energyPill.setAttribute("role", "button");
    energyPill.setAttribute("tabindex", "0");
  }

  const caption = $(".electricity-card .electricity-total span");
  if (caption) {
    caption.textContent = `Current load · ${demo.energyPeriod}`;
  }
}

function cycleEnergyPeriod() {
  const index = energyPeriods.indexOf(demo.energyPeriod);
  demo.energyPeriod = energyPeriods[(index + 1) % energyPeriods.length];
  updateEnergyPeriodUI();
}

energyPill?.addEventListener("click", cycleEnergyPeriod);
energyPill?.addEventListener("keydown", (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    cycleEnergyPeriod();
  }
});

// ---------------- PROFILE BUTTON ----------------

$("#profileCircle")?.addEventListener("click", () => {
  const name = currentUser?.name || currentUser?.email || "Home Owner";
  alert(`SmartTwin home profile\nSigned in as: ${name}`);
});

// ---------------- LOGOUT & SESSION ----------------

$("#logoutBtn")?.addEventListener("click", async () => {
  const button = $("#logoutBtn");
  if (button) button.disabled = true;

  try {
    await api("/api/logout", {
      method: "POST",
      body: JSON.stringify({})
    });
  } catch (error) {
    console.error("Logout request failed:", error);
  } finally {
    currentUser = null;
    devices = [];
    activity = [];

    $("#loginForm")?.reset();
    $("#signupForm")?.reset();

    showLogin();

    if (button) button.disabled = false;
  }
});

async function checkSession() {
  try {
    const data = await api("/api/me");

    if (data.authenticated && data.user) {
      await openDashboard(data.user);
    } else {
      showLogin();
    }
  } catch (error) {
    console.error("Session check failed:", error);
    showLogin();
  }
}

// ---------------- INITIALIZE ----------------

updateMusicUI();
updateBrightnessUI();
updateACUI();
updateVacuumUI();
updateCameraUI();
updateWifiUI();
updateEnergyPeriodUI();

checkSession();
