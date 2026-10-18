/**
 * PathWise Main Application Orchestrator.
 * Connects the holographic UI, Leaflet map engine, tracking service, and cognitive arena.
 */

import { db } from './db.js';
import { tracker, formatDistance, formatDuration } from './tracker.js';
import { HolographicMapController } from './map.js';
import { reverseGeocode, searchPlaces } from './geocoding.js';
import { learnArena } from './learn.js';
import { sound } from './audio.js';

// Application State
let activeTab = 'home';
let mainMap = null;
let replayMap = null;
let activeReplayJourney = null;
let replayInterval = null;
let replayStepIndex = 0;
let isReplayPlaying = false;
let replayPlaybackSpeed = 1;
let currentClickCoords = null;

// Clock Loop
function startClock() {
  const clock = document.getElementById('liveClockDisplay');
  setInterval(() => {
    if (clock) {
      const now = new Date();
      clock.textContent = now.toTimeString().split(' ')[0] + ' UTC';
    }
  }, 1000);
}

// Tab Switching
function switchTab(tabName) {
  activeTab = tabName;
  sound.playTap();

  // Update Navigation Elements
  document.querySelectorAll('.nav-tab').forEach(btn => {
    btn.classList.toggle('active', btn.getAttribute('data-tab') === tabName);
  });
  document.querySelectorAll('.mob-tab').forEach(btn => {
    btn.classList.toggle('active', btn.getAttribute('data-tab') === tabName);
  });

  // Switch Viewport Sections
  document.querySelectorAll('.screen-view').forEach(view => {
    view.classList.remove('active');
  });

  const targetView = document.getElementById(`view${tabName.charAt(0).toUpperCase() + tabName.slice(1)}`);
  if (targetView) targetView.classList.add('active');

  // Trigger Leaflet resize if entering Map
  if (tabName === 'map') {
    setTimeout(() => {
      mainMap?.map?.invalidateSize();
    }, 150);
  } else if (tabName === 'journeys') {
    renderJourneysLibrary();
  } else if (tabName === 'learn') {
    learnArena.init('arenaGameBoard');
  } else if (tabName === 'home') {
    renderHomeDashboard();
  }
}

// Render Home Dashboard Metrics & Recent Journeys
async function renderHomeDashboard() {
  const journeys = await db.getAllJourneys();
  const landmarks = await db.getAllLandmarks();

  const totalDist = journeys.reduce((sum, j) => sum + (j.recordedDistanceMeters || 0), 0);

  const jCountEl = document.getElementById('statJourneysCount');
  const dDistEl = document.getElementById('statTotalDistance');
  const lCountEl = document.getElementById('statLandmarksCount');
  const rScoreEl = document.getElementById('statRecallScore');

  if (jCountEl) jCountEl.textContent = journeys.length;
  if (dDistEl) dDistEl.textContent = formatDistance(totalDist);
  if (lCountEl) lCountEl.textContent = landmarks.length;
  if (rScoreEl) rScoreEl.textContent = `${learnArena.score} XP`;

  // Render Recent Routes
  const listEl = document.getElementById('homeRecentJourneysList');
  if (listEl) {
    if (journeys.length === 0) {
      listEl.innerHTML = `
        <div class="hud-bracket-panel" style="padding: 20px; text-align:center; color:#94A3B8;">
          No journeys logged yet. Tap <strong>START JOURNEY</strong> or <strong>SIMULATE WALK</strong> above.
        </div>
      `;
    } else {
      listEl.innerHTML = journeys.slice(0, 3).map(j => `
        <div class="journey-card-item hud-bracket-panel" data-id="${j.id}">
          <div class="journey-info-col">
            <h4>${j.title} ${j.isDemo ? '<span class="badge-demo">DEMO</span>' : ''}</h4>
            <div class="journey-meta-row">
              <span>${formatDistance(j.recordedDistanceMeters)}</span>
              <span>•</span>
              <span>${formatDuration(j.durationSeconds)}</span>
              <span>•</span>
              <span>${j.pointCount} points</span>
            </div>
          </div>
          <button class="btn-replay-pill">
            <span>REPLAY</span>
            <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          </button>
        </div>
      `).join('');

      listEl.querySelectorAll('.journey-card-item').forEach(card => {
        card.addEventListener('click', () => {
          const id = card.getAttribute('data-id');
          openReplayTheater(id);
        });
      });
    }
  }
}

// Render Journeys Library
async function renderJourneysLibrary() {
  const journeys = await db.getAllJourneys();
  const grid = document.getElementById('journeysGridList');
  if (!grid) return;

  if (journeys.length === 0) {
    grid.innerHTML = `
      <div class="hud-bracket-panel" style="grid-column: 1 / -1; padding: 40px; text-align:center;">
        <h3 class="text-cyan" style="font-family:'Outfit';">Journey Library Empty</h3>
        <p style="color:#94A3B8; margin-top:8px;">Record a GPS trip or tap <strong>+ Seed Demo Routes</strong> above.</p>
      </div>
    `;
    return;
  }

  grid.innerHTML = journeys.map(j => `
    <div class="journey-full-card hud-bracket-panel">
      <div class="jcard-top">
        <div>
          <h3 class="jcard-title">${j.title}</h3>
          <span style="font-family:'JetBrains Mono'; font-size:0.75rem; color:#64748B;">
            ${new Date(j.startTime).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}
          </span>
        </div>
        ${j.isDemo ? '<span class="badge-demo">DEMO</span>' : ''}
      </div>

      <div class="jcard-stats-grid">
        <div>
          <span style="font-size:0.65rem; color:#64748B;">DISTANCE</span>
          <div style="font-size:1.05rem; font-weight:bold; color:#00D2D3;">${formatDistance(j.recordedDistanceMeters)}</div>
        </div>
        <div>
          <span style="font-size:0.65rem; color:#64748B;">DURATION</span>
          <div style="font-size:1.05rem; font-weight:bold; color:#00F5FF;">${formatDuration(j.durationSeconds)}</div>
        </div>
        <div>
          <span style="font-size:0.65rem; color:#64748B;">COORDINATES</span>
          <div style="font-size:1.05rem; font-weight:bold; color:#FFB800;">${j.pointCount} pts</div>
        </div>
      </div>

      <div class="jcard-actions">
        <button class="btn-danger-hud btn-sm btn-delete-j" data-id="${j.id}">Delete</button>
        <button class="btn-hud-primary btn-sm btn-open-replay" data-id="${j.id}">
          <span>Replay Path</span>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
        </button>
      </div>
    </div>
  `).join('');

  grid.querySelectorAll('.btn-open-replay').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      openReplayTheater(btn.getAttribute('data-id'));
    });
  });

  grid.querySelectorAll('.btn-delete-j').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const id = btn.getAttribute('data-id');
      if (confirm('Delete this recorded route from local storage?')) {
        await db.deleteJourney(id);
        renderJourneysLibrary();
      }
    });
  });
}

// Open Replay Theater
async function openReplayTheater(journeyId) {
  switchTab('journeys');
  const journey = await db.getJourneyById(journeyId);
  if (!journey || !journey.points || journey.points.length < 2) {
    alert('This journey has insufficient coordinate breadcrumbs for replay.');
    return;
  }

  activeReplayJourney = journey;
  const theater = document.getElementById('replayTheater');
  if (theater) theater.style.display = 'flex';

  document.getElementById('replayTitle').textContent = journey.title;
  document.getElementById('replayMeta').textContent = `${formatDistance(journey.recordedDistanceMeters)} • ${formatDuration(journey.durationSeconds)} • ${journey.points.length} points`;
  document.getElementById('replayTotalTime').textContent = formatDuration(journey.durationSeconds);

  // Init Replay Map if not already created
  if (!replayMap) {
    replayMap = new HolographicMapController('replayLeafletMap');
    replayMap.init([journey.points[0].latitude, journey.points[0].longitude], 15);
  } else {
    replayMap.map?.invalidateSize();
  }

  replayMap.setRoutePoints(journey.points);
  replayMap.fitBounds(journey.points);

  replayStepIndex = journey.points.length;
  updateReplaySlider(100);
  stopReplayAnimation();
}

function updateReplaySlider(pct) {
  const slider = document.getElementById('replayTimelineSlider');
  if (slider) slider.value = pct;

  if (activeReplayJourney && activeReplayJourney.points) {
    const totalPts = activeReplayJourney.points.length;
    const targetIdx = Math.round((pct / 100) * (totalPts - 1));
    replayStepIndex = targetIdx;

    const visible = activeReplayJourney.points.slice(0, targetIdx + 1);
    replayMap.setRoutePoints(visible);

    if (visible.length > 0) {
      const cur = visible[visible.length - 1];
      replayMap.updateUserLocation(cur.latitude, cur.longitude);
      const curSec = Math.round((pct / 100) * activeReplayJourney.durationSeconds);
      document.getElementById('replayCurrentTime').textContent = formatDuration(curSec);
      document.getElementById('replayPointIndex').textContent = `Point ${targetIdx + 1} / ${totalPts}`;
    }
  }
}

function startReplayAnimation() {
  if (!activeReplayJourney) return;
  isReplayPlaying = true;
  document.getElementById('replayPlayIcon').style.display = 'none';
  document.getElementById('replayPauseIcon').style.display = 'block';

  if (replayStepIndex >= activeReplayJourney.points.length - 1) {
    replayStepIndex = 0;
  }

  clearInterval(replayInterval);
  const intervalMs = Math.round(300 / replayPlaybackSpeed);

  replayInterval = setInterval(() => {
    if (!isReplayPlaying || !activeReplayJourney) return;

    if (replayStepIndex < activeReplayJourney.points.length) {
      const pct = (replayStepIndex / (activeReplayJourney.points.length - 1)) * 100;
      updateReplaySlider(pct);
      replayStepIndex++;
    } else {
      stopReplayAnimation();
    }
  }, intervalMs);
}

function stopReplayAnimation() {
  isReplayPlaying = false;
  clearInterval(replayInterval);
  document.getElementById('replayPlayIcon').style.display = 'block';
  document.getElementById('replayPauseIcon').style.display = 'none';
}

// Telemetry Listener Callback
function setupTelemetrySubscriber() {
  tracker.subscribe(async (state) => {
    const { journey, points, lastLocation } = state;
    const statusText = document.getElementById('globalStatusText');
    const dockDur = document.getElementById('dockDuration');
    const dockDist = document.getElementById('dockDistance');
    const dockSpeed = document.getElementById('dockSpeed');
    const dockPts = document.getElementById('dockPoints');

    const btnStart = document.getElementById('btnDockStart');
    const btnSim = document.getElementById('btnDockSimulate');
    const btnPause = document.getElementById('btnDockPause');
    const btnResume = document.getElementById('btnDockResume');
    const btnStop = document.getElementById('btnDockStop');

    const homeBanner = document.getElementById('activeMissionBanner');

    if (state.state === 'RECORDING') {
      if (statusText) statusText.textContent = 'RECORDING';
      if (homeBanner) {
        homeBanner.style.display = 'flex';
        document.getElementById('bannerDist').textContent = formatDistance(journey.recordedDistanceMeters);
        document.getElementById('bannerDur').textContent = formatDuration(journey.durationSeconds);
        document.getElementById('bannerPts').textContent = `${journey.pointCount} pts`;
      }
      btnStart.style.display = 'none';
      btnSim.style.display = 'none';
      btnPause.style.display = 'inline-flex';
      btnResume.style.display = 'none';
      btnStop.style.display = 'inline-flex';

      if (dockDur) dockDur.textContent = formatDuration(journey.durationSeconds);
      if (dockDist) dockDist.textContent = formatDistance(journey.recordedDistanceMeters);
      if (dockSpeed) dockSpeed.textContent = lastLocation?.speed ? `${(lastLocation.speed * 3.6).toFixed(1)} km/h` : '4.8 km/h';
      if (dockPts) dockPts.textContent = journey.pointCount;

      if (lastLocation && mainMap) {
        mainMap.updateUserLocation(lastLocation.latitude, lastLocation.longitude);
        mainMap.setRoutePoints(points);
      }
    } else if (state.state === 'PAUSED') {
      if (statusText) statusText.textContent = 'PAUSED';
      btnPause.style.display = 'none';
      btnResume.style.display = 'inline-flex';
    } else {
      // IDLE
      if (statusText) statusText.textContent = 'STANDBY';
      if (homeBanner) homeBanner.style.display = 'none';
      btnStart.style.display = 'inline-flex';
      btnSim.style.display = 'inline-flex';
      btnPause.style.display = 'none';
      btnResume.style.display = 'none';
      btnStop.style.display = 'none';

      if (dockDur) dockDur.textContent = '00:00:00';
      if (dockDist) dockDist.textContent = '0.00 km';
      if (dockSpeed) dockSpeed.textContent = '0.0 km/h';
      if (dockPts) dockPts.textContent = '0';
    }

    // Reverse Geocode Street Name for current fix
    if (lastLocation) {
      const street = await reverseGeocode(lastLocation.latitude, lastLocation.longitude);
      if (street) {
        const badge = document.getElementById('currentStreetNameText');
        if (badge) badge.textContent = street;
      }
    }
  });
}

// Initialize Application
document.addEventListener('DOMContentLoaded', async () => {
  startClock();
  setupTelemetrySubscriber();

  // Initialize DB and Seed Demo Data
  await db.seedDemoDataIfEmpty();

  // Initialize Main Holographic Map
  mainMap = new HolographicMapController('leafletMap');
  mainMap.init([12.9716, 77.5946], 15);

  const landmarks = await db.getAllLandmarks();
  mainMap.setLandmarks(landmarks);

  // Map Click to Open Landmark Dialog
  mainMap.onMapClickCallback = async (lat, lon) => {
    currentClickCoords = { lat, lon };
    sound.playTap();
    const modal = document.getElementById('landmarkModal');
    if (modal) modal.style.display = 'flex';
    const resolved = await reverseGeocode(lat, lon);
    const input = document.getElementById('landmarkNameInput');
    if (input) input.value = resolved || '';
    const coordLbl = document.getElementById('modalCoordLabel');
    if (coordLbl) coordLbl.textContent = `LAT: ${lat.toFixed(5)} • LON: ${lon.toFixed(5)}`;
  };

  // ── Event Handlers ──────────────────────────────────────────────────────────

  // Navigation Tabs
  document.querySelectorAll('.nav-tab, .mob-tab').forEach(btn => {
    btn.addEventListener('click', () => {
      const tab = btn.getAttribute('data-tab');
      switchTab(tab);
    });
  });

  document.getElementById('brandBtn')?.addEventListener('click', () => switchTab('home'));
  document.getElementById('btnViewAllJourneys')?.addEventListener('click', () => switchTab('journeys'));
  document.getElementById('btnJumpToActiveHUD')?.addEventListener('click', () => switchTab('map'));

  // Home Screen Buttons
  document.getElementById('btnHomeStartJourney')?.addEventListener('click', () => {
    switchTab('map');
    tracker.startRealGPS();
  });

  document.getElementById('btnHomeExploreMap')?.addEventListener('click', () => switchTab('map'));

  document.getElementById('btnHomeSimulateWalk')?.addEventListener('click', () => {
    switchTab('map');
    tracker.startVirtualSimulation();
  });

  // Dock Buttons
  document.getElementById('btnDockStart')?.addEventListener('click', () => tracker.startRealGPS());
  document.getElementById('btnDockSimulate')?.addEventListener('click', () => tracker.startVirtualSimulation());
  document.getElementById('btnDockPause')?.addEventListener('click', () => tracker.pause());
  document.getElementById('btnDockResume')?.addEventListener('click', () => tracker.resume());
  document.getElementById('btnDockStop')?.addEventListener('click', async () => {
    await tracker.stop();
    renderHomeDashboard();
  });

  document.getElementById('btnDockAddLandmark')?.addEventListener('click', () => {
    const center = mainMap.map?.getCenter() || { lat: 12.9716, lng: 77.5946 };
    mainMap.onMapClickCallback(center.lat, center.lng);
  });

  // Map Style & Public Layer Providers System
  const STYLE_NAMES = {
    'direct-osm': 'DIRECT OSM',
    'osm-hot': 'HUMANITARIAN',
    'esri-streets': 'ESRI STREET',
    'esri-satellite': 'SATELLITE',
    'esri-dark': 'ESRI DARK',
    'cyberpunk-osm': 'CYBERPUNK',
    'opentopo': 'TOPOGRAPHIC'
  };

  const btnMapStyle = document.getElementById('btnToggleMapStyle');
  const lblMapStyle = document.getElementById('lblMapStyle');
  const btnLayers = document.getElementById('btnOpenLayersMenu');
  const popoverLayers = document.getElementById('mapLayersPopover');
  const btnCloseLayers = document.getElementById('btnCloseLayersPopover');

  function updateMapStyleUI(styleKey) {
    if (lblMapStyle) {
      lblMapStyle.textContent = STYLE_NAMES[styleKey] || 'DIRECT OSM';
    }
    document.querySelectorAll('.layer-option-card').forEach(card => {
      card.classList.toggle('active', card.getAttribute('data-style') === styleKey);
    });
  }

  // Quick Cycle Toggle Button
  btnMapStyle?.addEventListener('click', () => {
    sound.playTap();
    const newStyle = mainMap.toggleNextMapStyle();
    updateMapStyleUI(newStyle);
  });

  // Layers Popover Menu Toggle
  btnLayers?.addEventListener('click', () => {
    sound.playTap();
    const isVisible = popoverLayers?.style.display === 'block';
    if (popoverLayers) {
      popoverLayers.style.display = isVisible ? 'none' : 'block';
    }
    btnLayers.classList.toggle('active', !isVisible);
  });

  btnCloseLayers?.addEventListener('click', () => {
    sound.playTap();
    if (popoverLayers) popoverLayers.style.display = 'none';
    btnLayers?.classList.remove('active');
  });

  // Layer Option Cards in Popover
  document.querySelectorAll('.layer-option-card').forEach(card => {
    card.addEventListener('click', () => {
      sound.playTap();
      const chosenStyle = card.getAttribute('data-style');
      if (chosenStyle) {
        mainMap.setMapStyle(chosenStyle);
        updateMapStyleUI(chosenStyle);
      }
    });
  });

  // 3D Tilt Toggle
  const btn3D = document.getElementById('btnToggle3D');
  btn3D?.addEventListener('click', () => {
    sound.playTap();
    const stage = document.getElementById('mapPerspectiveStage');
    const is3d = stage.classList.toggle('tactical-3d');
    btn3D.classList.toggle('active', is3d);
  });

  // Radar Scanline Toggle
  const btnRadar = document.getElementById('btnToggleRadar');
  btnRadar?.addEventListener('click', () => {
    sound.playTap();
    const disc = document.getElementById('radarScanDisc');
    const isActive = disc.classList.toggle('hidden');
    btnRadar.classList.toggle('active', !isActive);
  });

  // Laser Distance Ruler Toggle
  const btnMeasure = document.getElementById('btnToggleMeasure');
  const measureCard = document.getElementById('measureResultCard');

  btnMeasure?.addEventListener('click', () => {
    sound.playTap();
    const isActive = btnMeasure.classList.toggle('active');
    measureCard.style.display = isActive ? 'block' : 'none';

    mainMap.toggleMeasureMode(isActive, (res) => {
      document.getElementById('measureDistVal').textContent = formatDistance(res.distMeters);
      document.getElementById('measureBearingVal').textContent = `${res.bearingDeg}°`;
      document.getElementById('measureWalkVal').textContent = `${res.walkMinutes} mins`;
    });
  });

  document.getElementById('btnCloseMeasureCard')?.addEventListener('click', () => {
    btnMeasure.classList.remove('active');
    measureCard.style.display = 'none';
    mainMap.toggleMeasureMode(false);
  });

  // Recenter GPS
  document.getElementById('btnRecenterGPS')?.addEventListener('click', () => {
    sound.playTap();
    if (tracker.lastLocation) {
      mainMap.recenter(tracker.lastLocation.latitude, tracker.lastLocation.longitude);
    } else {
      mainMap.recenter(12.9716, 77.5946);
    }
  });

  // Place Search
  const searchInput = document.getElementById('mapSearchInput');
  const searchDropdown = document.getElementById('searchResultsDropdown');
  const searchClear = document.getElementById('btnSearchClear');
  let searchTimer = null;

  searchInput?.addEventListener('input', (e) => {
    const q = e.target.value;
    searchClear.style.display = q ? 'block' : 'none';
    clearTimeout(searchTimer);

    if (q.length < 2) {
      searchDropdown.style.display = 'none';
      return;
    }

    searchTimer = setTimeout(async () => {
      const places = await searchPlaces(q);
      if (places.length > 0) {
        searchDropdown.style.display = 'block';
        searchDropdown.innerHTML = places.map(p => `
          <div class="search-result-row" data-lat="${p.lat}" data-lon="${p.lon}">
            📍 ${p.name}
          </div>
        `).join('');

        searchDropdown.querySelectorAll('.search-result-row').forEach(row => {
          row.addEventListener('click', () => {
            const lat = parseFloat(row.getAttribute('data-lat'));
            const lon = parseFloat(row.getAttribute('data-lon'));
            mainMap.recenter(lat, lon, 17);
            searchDropdown.style.display = 'none';
            searchInput.value = '';
            searchClear.style.display = 'none';
          });
        });
      } else {
        searchDropdown.style.display = 'none';
      }
    }, 350);
  });

  searchClear?.addEventListener('click', () => {
    searchInput.value = '';
    searchDropdown.style.display = 'none';
    searchClear.style.display = 'none';
  });

  // Replay Controls
  document.getElementById('btnCloseReplayTheater')?.addEventListener('click', () => {
    stopReplayAnimation();
    document.getElementById('replayTheater').style.display = 'none';
  });

  document.getElementById('btnReplayPlayPause')?.addEventListener('click', () => {
    if (isReplayPlaying) {
      stopReplayAnimation();
    } else {
      startReplayAnimation();
    }
  });

  document.getElementById('replayTimelineSlider')?.addEventListener('input', (e) => {
    stopReplayAnimation();
    updateReplaySlider(parseFloat(e.target.value));
  });

  document.querySelectorAll('.btn-speed').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.btn-speed').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      replayPlaybackSpeed = parseFloat(btn.getAttribute('data-speed'));
      if (isReplayPlaying) {
        stopReplayAnimation();
        startReplayAnimation();
      }
    });
  });

  // Learn Mode Tabs
  document.querySelectorAll('.btn-mode-tab').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.btn-mode-tab').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      learnArena.setMode(btn.getAttribute('data-mode'));
    });
  });

  // Landmark Modal Form
  document.getElementById('formAddLandmark')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('landmarkNameInput').value;
    const cat = document.getElementById('landmarkCategoryInput').value;
    const notes = document.getElementById('landmarkNotesInput').value;

    if (currentClickCoords) {
      await db.saveLandmark({
        id: 'lm-' + Date.now(),
        name,
        category: cat,
        notes,
        latitude: currentClickCoords.lat,
        longitude: currentClickCoords.lon,
        createdAt: Date.now()
      });

      const updated = await db.getAllLandmarks();
      mainMap.setLandmarks(updated);
      sound.playSuccess();
    }

    document.getElementById('landmarkModal').style.display = 'none';
    renderHomeDashboard();
  });

  document.getElementById('btnCloseLandmarkModal')?.addEventListener('click', () => {
    document.getElementById('landmarkModal').style.display = 'none';
  });
  document.getElementById('btnCancelLandmark')?.addEventListener('click', () => {
    document.getElementById('landmarkModal').style.display = 'none';
  });

  // Settings Actions
  document.getElementById('settingScanlines')?.addEventListener('change', (e) => {
    const sl = document.getElementById('hudScanlines');
    if (sl) sl.classList.toggle('disabled', !e.target.checked);
  });

  document.getElementById('settingAudio')?.addEventListener('change', (e) => {
    sound.setEnabled(e.target.checked);
  });

  document.getElementById('btnClearDatabase')?.addEventListener('click', async () => {
    if (confirm('Are you sure you want to delete all saved journeys and landmarks?')) {
      await db.clearAll();
      await db.seedDemoDataIfEmpty();
      alert('Local database reset successfully.');
      renderHomeDashboard();
      renderJourneysLibrary();
    }
  });

  document.getElementById('btnSeedDemoJourneys')?.addEventListener('click', async () => {
    await db.seedDemoDataIfEmpty();
    renderJourneysLibrary();
    renderHomeDashboard();
    sound.playSuccess();
  });

  document.getElementById('btnExportDataJSON')?.addEventListener('click', async () => {
    await db.exportDataJSON();
    sound.playTap();
  });

  // Render initial home view
  renderHomeDashboard();
});
