/**
 * PathWise Main Application Orchestrator.
 * Connects the holographic UI, Leaflet map engine, tracking service, and cognitive arena.
 */

import { db } from './db.js';
import { tracker, formatDistance, formatDuration } from './tracker.js';
import { HolographicMapController } from './map.js';
import {
  reverseGeocode,
  searchPlaces,
  getAreaDetails,
  fetchNearbyLandmarks,
  calculateBearing,
  bearingToCardinal,
  calculateDistance
} from './geocoding.js';
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

// Live In-Journey Spatial Awareness & Learning State
let currentAreaDetails = null;
let previousSuburb = null;
let currentHeading = 0;
let discoveredSectors = new Set();
let sessionMasteryXP = 0;
let liveNearbyLandmarks = [];
let tripOriginStreet = null;
let lastNearbyScanCoords = null;
let currentApproachingLandmark = null;
let activeDockTab = 'telemetry';

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

  // Update Spatial Mental Mastery Rank Card
  const xp = learnArena.score || 0;
  const masteryEl = document.getElementById('homeMasteryXP');
  if (masteryEl) masteryEl.textContent = `${xp} XP`;
  const rankEl = document.getElementById('homeRankTitle');
  const xpFill = document.getElementById('homeXpFill');
  const xpNeeded = document.getElementById('homeXpNeeded');
  if (xp < 100) {
    if (rankEl) rankEl.textContent = 'NOVICE RECONNAISSANCE';
    if (xpFill) xpFill.style.width = `${Math.max(12, (xp / 100) * 100)}%`;
    if (xpNeeded) xpNeeded.textContent = `${100 - xp} XP to Urban Explorer Grade II`;
  } else if (xp < 300) {
    if (rankEl) rankEl.textContent = 'URBAN EXPLORER GRADE II';
    if (xpFill) xpFill.style.width = `${Math.min(100, ((xp - 100) / 200) * 100)}%`;
    if (xpNeeded) xpNeeded.textContent = `${300 - xp} XP to City Cartographer Grade III`;
  } else {
    if (rankEl) rankEl.textContent = 'MASTER CITY CARTOGRAPHER';
    if (xpFill) xpFill.style.width = '100%';
    if (xpNeeded) xpNeeded.textContent = 'Maximum Spatial Mastery Level Achieved';
  }

  // Render Recent Routes
  const listEl = document.getElementById('homeRecentJourneysList');
  if (listEl) {
    if (journeys.length === 0) {
      listEl.innerHTML = `
        <div class="hud-bracket-panel" style="padding: 20px; text-align:center; color:#94A3B8;">
          No journeys logged yet. Tap <strong>START MISSION</strong> or <strong>SIMULATE WALK</strong> above.
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

// Dock Tab Switching (Controls, Radar, Analysis & Quiz)
function switchDockTab(tabKey) {
  activeDockTab = tabKey;
  sound.playTap();
  document.querySelectorAll('.dock-tab-btn').forEach(btn => {
    btn.classList.toggle('active', btn.getAttribute('data-dock-tab') === tabKey);
  });
  const pTel = document.getElementById('dockPanelTelemetry');
  const pRad = document.getElementById('dockPanelRadar');
  const pLrn = document.getElementById('dockPanelLearning');
  if (pTel) pTel.style.display = tabKey === 'telemetry' ? 'block' : 'none';
  if (pRad) pRad.style.display = tabKey === 'radar' ? 'block' : 'none';
  if (pLrn) pLrn.style.display = tabKey === 'learning' ? 'block' : 'none';
}

// Render Nearby Landmarks in Dock Radar
function renderRadarLandmarksList(places) {
  const container = document.getElementById('radarLandmarksList');
  if (!container) return;

  if (!places || places.length === 0) {
    container.innerHTML = `
      <div class="radar-empty-state">No prominent landmarks detected within 1.2km radius.</div>
    `;
    return;
  }

  container.innerHTML = places.map(p => `
    <div class="radar-place-item" data-id="${p.id}">
      <div class="radar-place-info">
        <span class="radar-place-name">${p.name}</span>
        <div class="radar-place-meta">
          <span class="radar-place-category">${p.category}</span>
          <span>•</span>
          <span class="radar-place-dist">${p.distanceMeters}m ${p.cardinalDirection}</span>
        </div>
      </div>
      <button class="btn-pin-mini" data-lat="${p.latitude}" data-lon="${p.longitude}" data-name="${encodeURIComponent(p.name)}" data-cat="${encodeURIComponent(p.category)}">
        + Pin
      </button>
    </div>
  `).join('');

  container.querySelectorAll('.btn-pin-mini').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const lat = parseFloat(btn.getAttribute('data-lat'));
      const lon = parseFloat(btn.getAttribute('data-lon'));
      const name = decodeURIComponent(btn.getAttribute('data-name'));
      const cat = decodeURIComponent(btn.getAttribute('data-cat'));
      await pinNearbyLandmark({ name, category: cat, latitude: lat, longitude: lon });
      btn.textContent = '✓ Pinned';
      btn.style.borderColor = '#10B981';
      btn.style.color = '#10B981';
    });
  });
}

// 1-Tap Save Landmark to Mental Map
async function pinNearbyLandmark(place) {
  await db.saveLandmark({
    id: 'lm-' + Date.now(),
    name: place.name,
    category: place.category || 'Landmark',
    notes: 'Discovered during active journey',
    latitude: place.latitude,
    longitude: place.longitude,
    createdAt: Date.now()
  });

  const updated = await db.getAllLandmarks();
  mainMap.setLandmarks(updated);
  sound.playSuccess();
  learnArena.addScore(15);
  sessionMasteryXP += 15;
  const sessEl = document.getElementById('analysisSessionXP');
  if (sessEl) sessEl.textContent = `+${sessionMasteryXP} XP`;
}

// In-Journey Micro-Recall Quiz Logic
function launchInJourneyQuiz() {
  sound.playTap();
  const modal = document.getElementById('inJourneyQuizModal');
  const qEl = document.getElementById('inJourneyQuizQuestion');
  const optsEl = document.getElementById('inJourneyQuizOptions');
  const fbEl = document.getElementById('inJourneyQuizFeedback');
  if (!modal || !qEl || !optsEl || !fbEl) return;

  fbEl.style.display = 'none';
  fbEl.textContent = '';
  modal.style.display = 'flex';

  // Generate 1 of 3 dynamic questions based on current active journey
  const questionType = Math.floor(Math.random() * 3);
  let questionText = '';
  let options = [];
  let correctIndex = 0;

  if (questionType === 0 && tripOriginStreet) {
    questionText = 'Where did you start this current journey?';
    const distractors = ['MG Road Promenade', 'Residency Road', 'Old Airport Road', '100 Feet Corridor', 'Brigade Boulevard'].filter(s => s !== tripOriginStreet);
    options = [tripOriginStreet, distractors[0], distractors[1], distractors[2]].sort(() => Math.random() - 0.5);
    correctIndex = options.indexOf(tripOriginStreet);
  } else if (questionType === 1 && liveNearbyLandmarks.length > 0) {
    const target = liveNearbyLandmarks[0];
    questionText = `Which landmark is closest to your current position (~${target.distanceMeters}m ${target.cardinalDirection})?`;
    const distractors = ['Central Library Tower', 'Botanical Conservatory', 'Grand Trinity Cathedral', 'City Art Pavilion'].filter(s => s !== target.name);
    options = [target.name, distractors[0], distractors[1], distractors[2]].sort(() => Math.random() - 0.5);
    correctIndex = options.indexOf(target.name);
  } else {
    const card = bearingToCardinal(currentHeading);
    questionText = `What general cardinal direction is your route heading right now?`;
    const cardinals = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
    const distractors = cardinals.filter(c => c !== card);
    options = [card, distractors[0], distractors[1], distractors[2]].sort(() => Math.random() - 0.5);
    correctIndex = options.indexOf(card);
  }

  qEl.textContent = questionText;
  optsEl.innerHTML = options.map((opt, i) => `
    <button class="btn-quiz-opt" data-index="${i}">
      <span>${opt}</span>
      <span style="font-family:'JetBrains Mono'; font-size:0.75rem; color:#64748B;">0${i + 1}</span>
    </button>
  `).join('');

  optsEl.querySelectorAll('.btn-quiz-opt').forEach(btn => {
    btn.addEventListener('click', () => {
      const selected = parseInt(btn.getAttribute('data-index'), 10);
      const isCorrect = selected === correctIndex;

      optsEl.querySelectorAll('.btn-quiz-opt').forEach((b, idx) => {
        b.disabled = true;
        if (idx === correctIndex) b.classList.add('correct');
        else if (idx === selected) b.classList.add('wrong');
      });

      if (isCorrect) {
        sound.playSuccess();
        learnArena.addScore(20);
        sessionMasteryXP += 20;
        const sessEl = document.getElementById('analysisSessionXP');
        if (sessEl) sessEl.textContent = `+${sessionMasteryXP} XP`;

        fbEl.style.display = 'block';
        fbEl.style.background = 'rgba(16, 185, 129, 0.2)';
        fbEl.style.border = '1px solid #10B981';
        fbEl.style.color = '#10B981';
        fbEl.textContent = '★ SPOT ON! +20 XP awarded to Spatial Recall Mastery!';
      } else {
        sound.playBeep(330, 0.2, 'sawtooth');
        fbEl.style.display = 'block';
        fbEl.style.background = 'rgba(239, 68, 68, 0.2)';
        fbEl.style.border = '1px solid #EF4444';
        fbEl.style.color = '#EF4444';
        fbEl.textContent = 'Incorrect orientation. The correct answer was highlighted in green.';
      }

      setTimeout(() => {
        modal.style.display = 'none';
      }, 2500);
    });
  });
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

      const distStr = formatDistance(journey.recordedDistanceMeters);
      const durStr = formatDuration(journey.durationSeconds);
      const speedKmH = lastLocation?.speed ? (lastLocation.speed * 3.6).toFixed(1) : '4.8';

      if (dockDur) dockDur.textContent = durStr;
      if (dockDist) dockDist.textContent = distStr;
      if (dockSpeed) dockSpeed.textContent = `${speedKmH} km/h`;
      if (dockPts) dockPts.textContent = journey.pointCount;

      // Update Collapsed Mini HUD Pill
      const miniStatusText = document.getElementById('miniStatusText');
      const miniPulseDot = document.getElementById('miniPulseDot');
      const miniDur = document.getElementById('miniDur');
      const miniDist = document.getElementById('miniDist');
      const btnMiniAction = document.getElementById('btnMiniAction');

      if (miniStatusText) miniStatusText.textContent = 'RECORDING';
      if (miniPulseDot) miniPulseDot.style.background = '#FF4757';
      if (miniDur) miniDur.textContent = durStr;
      if (miniDist) miniDist.textContent = distStr;
      if (btnMiniAction) {
        btnMiniAction.textContent = 'STOP & SAVE';
        btnMiniAction.className = 'btn-xs btn-dock-action btn-stop';
      }

      // Update Map Route & Position
      if (lastLocation && mainMap) {
        mainMap.updateUserLocation(lastLocation.latitude, lastLocation.longitude);
        mainMap.setRoutePoints(points);
      }

      // Calculate Heading from trajectory
      if (points.length >= 2) {
        const p1 = points[points.length - 2];
        const p2 = points[points.length - 1];
        currentHeading = calculateBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
      }

      // In-Journey Live Pace & Rhythm Analysis
      const paceEl = document.getElementById('analysisPace');
      const rhythmEl = document.getElementById('analysisRhythm');
      const sectorsEl = document.getElementById('analysisSectorsCount');
      const sessionXpEl = document.getElementById('analysisSessionXP');

      const speedVal = lastLocation?.speed || 1.35;
      if (speedVal > 0.4) {
        const paceSec = 1000 / speedVal;
        const pm = Math.floor(paceSec / 60);
        const ps = Math.floor(paceSec % 60);
        if (paceEl) paceEl.textContent = `${pm}'${ps.toString().padStart(2, '0')}"/km`;
      } else {
        if (paceEl) paceEl.textContent = `--'--"/km`;
      }

      if (rhythmEl) {
        if (speedVal < 0.5) rhythmEl.textContent = 'Stationary';
        else if (speedVal < 2.0) rhythmEl.textContent = 'Walking Pace';
        else if (speedVal < 4.5) rhythmEl.textContent = 'Jogging / Bike';
        else rhythmEl.textContent = 'Vehicular Transit';
      }

      if (sectorsEl) sectorsEl.textContent = `${discoveredSectors.size}`;
      if (sessionXpEl) sessionXpEl.textContent = `+${sessionMasteryXP} XP`;

      // Live Spatial Bar Updates
      const activeSpeedEl = document.getElementById('hudActiveSpeed');
      const activeHeadingEl = document.getElementById('hudActiveHeading');
      const headingIcon = document.getElementById('hudHeadingIcon');

      if (activeSpeedEl) activeSpeedEl.textContent = `${speedKmH} km/h`;
      if (activeHeadingEl) activeHeadingEl.textContent = `${bearingToCardinal(currentHeading)} (${currentHeading}°)`;
      if (headingIcon) headingIcon.style.transform = `rotate(${currentHeading}deg)`;

      // Reverse geocode details & sector awareness
      if (lastLocation) {
        const details = await getAreaDetails(lastLocation.latitude, lastLocation.longitude);
        if (details) {
          currentAreaDetails = details;
          if (!tripOriginStreet && details.street) {
            tripOriginStreet = details.street;
          }

          const streetEl = document.getElementById('hudActiveStreet');
          const suburbEl = document.getElementById('hudActiveSuburb');
          if (streetEl) streetEl.textContent = details.formattedHeadline;
          if (suburbEl) suburbEl.textContent = details.formattedSubtitle;

          // Sector Traversal Detection
          if (details.suburb && previousSuburb && details.suburb.toLowerCase() !== previousSuburb.toLowerCase()) {
            discoveredSectors.add(details.suburb);
            const toast = document.getElementById('hudSectorToast');
            const nameEl = document.getElementById('hudSectorName');
            if (toast && nameEl) {
              nameEl.textContent = details.suburb;
              toast.style.display = 'flex';
              sound.playSectorChime();
              sound.speak(`Now traversing ${details.suburb}`);
              learnArena.addScore(25);
              sessionMasteryXP += 25;
              if (sessionXpEl) sessionXpEl.textContent = `+${sessionMasteryXP} XP`;
              setTimeout(() => { toast.style.display = 'none'; }, 4500);
            }
          }
          if (details.suburb) previousSuburb = details.suburb;
        }

        // Periodic Nearby Landmarks Radar Scan (every ~80m or initial)
        if (!lastNearbyScanCoords || calculateDistance(lastNearbyScanCoords.lat, lastNearbyScanCoords.lon, lastLocation.latitude, lastLocation.longitude) > 80) {
          lastNearbyScanCoords = { lat: lastLocation.latitude, lon: lastLocation.longitude };
          fetchNearbyLandmarks(lastLocation.latitude, lastLocation.longitude).then(places => {
            liveNearbyLandmarks = places;
            const badge = document.getElementById('radarCountBadge');
            if (badge) badge.textContent = places.length;
            renderRadarLandmarksList(places);

            // Approaching Landmark Proximity Alert (within 150m)
            const closest = places[0];
            const proxBanner = document.getElementById('hudProximityBanner');
            const proxTitle = document.getElementById('hudProximityTitle');
            const proxDist = document.getElementById('hudProximityDist');

            if (closest && closest.distanceMeters <= 150) {
              currentApproachingLandmark = closest;
              if (proxBanner && proxTitle && proxDist) {
                proxTitle.textContent = `${closest.name} (${closest.category})`;
                proxDist.textContent = `${closest.distanceMeters}m Ahead (${closest.cardinalDirection})`;
                proxBanner.style.display = 'flex';
              }
              sound.speak(`Approaching ${closest.name}, ${closest.distanceMeters} meters ahead`);
            } else if (proxBanner) {
              proxBanner.style.display = 'none';
              currentApproachingLandmark = null;
            }
          });
        }
      }
    } else if (state.state === 'PAUSED') {
      if (statusText) statusText.textContent = 'PAUSED';
      btnPause.style.display = 'none';
      btnResume.style.display = 'inline-flex';

      const miniStatusText = document.getElementById('miniStatusText');
      const miniPulseDot = document.getElementById('miniPulseDot');
      const btnMiniAction = document.getElementById('btnMiniAction');
      if (miniStatusText) miniStatusText.textContent = 'PAUSED';
      if (miniPulseDot) miniPulseDot.style.background = '#FFB800';
      if (btnMiniAction) {
        btnMiniAction.textContent = 'RESUME';
        btnMiniAction.className = 'btn-xs btn-dock-action btn-resume';
      }
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

      const miniStatusText = document.getElementById('miniStatusText');
      const miniPulseDot = document.getElementById('miniPulseDot');
      const miniDur = document.getElementById('miniDur');
      const miniDist = document.getElementById('miniDist');
      const btnMiniAction = document.getElementById('btnMiniAction');

      if (miniStatusText) miniStatusText.textContent = 'STANDBY';
      if (miniPulseDot) miniPulseDot.style.background = '#00F5FF';
      if (miniDur) miniDur.textContent = '00:00:00';
      if (miniDist) miniDist.textContent = '0.00 km';
      if (btnMiniAction) {
        btnMiniAction.textContent = 'START GPS';
        btnMiniAction.className = 'btn-xs btn-dock-action btn-start';
      }

      const proxBanner = document.getElementById('hudProximityBanner');
      if (proxBanner) proxBanner.style.display = 'none';
      currentApproachingLandmark = null;
    }
  });
}

// Initialize Application
document.addEventListener('DOMContentLoaded', async () => {
  startClock();
  setupTelemetrySubscriber();
  initHomeHoloCanvas();

  // Initialize DB and Seed Demo Data
  await db.seedDemoDataIfEmpty();

  let isSatelliteGpsLocked = false;
  let initialCenter = [17.7338, 83.3323]; // Default to user's real sector

  // Initialize Main Holographic Map
  mainMap = new HolographicMapController('leafletMap');
  mainMap.init(initialCenter, 15);

  const homeWarningBanner = document.getElementById('homeGpsWarningBanner');
  const mapWarningStrip = document.getElementById('hudMapGpsAlertStrip');
  const homeBadge = document.getElementById('homeGpsBadge');
  const homeLoc = document.getElementById('homeDetectedLocality');
  const homeCoords = document.getElementById('homeDetectedCoords');

  function showGpsAlert(isError, title, msg) {
    if (isSatelliteGpsLocked) return;
    if (homeWarningBanner) {
      homeWarningBanner.style.display = 'flex';
      const t = document.getElementById('homeGpsWarningTitle');
      const m = document.getElementById('homeGpsWarningMsg');
      if (t) t.textContent = title;
      if (m) m.textContent = msg;
    }
    if (mapWarningStrip) {
      mapWarningStrip.style.display = 'flex';
      const m = document.getElementById('hudMapGpsAlertText');
      if (m) m.textContent = msg;
    }
  }

  function hideGpsAlert() {
    if (homeWarningBanner) homeWarningBanner.style.display = 'none';
    if (mapWarningStrip) mapWarningStrip.style.display = 'none';
  }

  // Fast IP city detection as an interim fallback while GPS resolves
  try {
    fetch('https://ipapi.co/json/').then(r => r.json()).then(data => {
      if (!isSatelliteGpsLocked && data && data.latitude && data.longitude) {
        window.__userInitialCoords = { lat: data.latitude, lon: data.longitude };
        const cityLabel = (data.city ? `${data.city}, ${data.region || data.country_name}` : 'DETECTED SECTOR').toUpperCase();
        if (homeLoc) homeLoc.textContent = cityLabel;
        if (homeCoords) homeCoords.textContent = `${data.latitude.toFixed(4)}° N, ${data.longitude.toFixed(4)}° E`;
        if (homeBadge) homeBadge.textContent = 'IP ESTIMATE';

        showGpsAlert(
          false,
          `Approximate Network Location (${data.city || 'Regional'})`,
          `Showing ISP regional gateway (~${data.city || 'Cellular'}). Tap 'ALLOW LIVE GPS' to lock onto your real house/street.`
        );

        if (!tracker.lastLocation && mainMap) {
          mainMap.recenter(data.latitude, data.longitude, 14);
          mainMap.updateUserLocation(data.latitude, data.longitude);
        }
      }
    }).catch(() => {});
  } catch (_) {}

  // Function to apply precision GPS fix
  const onGpsSuccess = async (pos) => {
    isSatelliteGpsLocked = true;
    hideGpsAlert();

    const { latitude, longitude, accuracy } = pos.coords;
    window.__userInitialCoords = { lat: latitude, lon: longitude };
    tracker.lastLocation = {
      latitude,
      longitude,
      speed: pos.coords.speed || 0,
      accuracy: accuracy || 4.0
    };

    mainMap?.updateUserLocation(latitude, longitude);
    mainMap?.recenter(latitude, longitude, 17);

    if (homeBadge) homeBadge.textContent = `GPS LOCK (±${Math.round(accuracy)}m)`;

    const details = await getAreaDetails(latitude, longitude);
    if (details) {
      currentAreaDetails = details;
      if (homeLoc) homeLoc.textContent = details.formattedHeadline.toUpperCase();
      const streetEl = document.getElementById('hudActiveStreet');
      const suburbEl = document.getElementById('hudActiveSuburb');
      if (streetEl) streetEl.textContent = details.formattedHeadline;
      if (suburbEl) suburbEl.textContent = details.formattedSubtitle;
    }

    if (homeCoords) {
      homeCoords.textContent = `${latitude.toFixed(4)}° N, ${longitude.toFixed(4)}° E`;
    }
  };

  // Function for user-driven GPS permission request
  function requestActiveGpsFix() {
    if (!('geolocation' in navigator)) {
      alert('Geolocation is not supported by your browser.');
      return;
    }
    sound.playTap();
    if (homeBadge) homeBadge.textContent = 'ACQUIRING GPS...';
    
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        onGpsSuccess(pos);
        sound.playSuccess();
      },
      (err) => {
        console.warn('Direct GPS request error:', err.message);
        if (err.code === 1) { // PERMISSION_DENIED
          showGpsAlert(
            true,
            'Browser Location Permission Denied',
            'Please tap the lock icon 🔒 in your browser address bar and set "Location" to Allow, or choose your city below.'
          );
          openSectorPicker();
        } else {
          showGpsAlert(
            true,
            'Satellite Signal Search Timed Out',
            'Could not get immediate satellite lock. You can select your locality manually below.'
          );
        }
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
  }

  // Proactively attempt precision GPS location
  if ('geolocation' in navigator) {
    navigator.geolocation.getCurrentPosition(
      onGpsSuccess,
      (err) => {
        console.warn('Proactive high-accuracy GPS error:', err.message);
        if (err.code === 1) {
          showGpsAlert(
            true,
            'Location Access Blocked By Browser',
            'Showing estimated network location. Tap "ALLOW LIVE GPS" or the address bar 🔒 to enable real-time tracking.'
          );
        } else {
          navigator.geolocation.getCurrentPosition(
            onGpsSuccess,
            (err2) => {
              console.warn('Standard geolocation error:', err2.message);
              showGpsAlert(
                true,
                'Satellite GNSS Searching',
                'Device is searching for satellite fix. Tap "ALLOW LIVE GPS" to retry or pick your sector.'
              );
            },
            { enableHighAccuracy: false, timeout: 15000, maximumAge: 60000 }
          );
        }
      },
      { enableHighAccuracy: true, timeout: 5000, maximumAge: 10000 }
    );
  }

  // Listen to tracker.js custom events
  window.addEventListener('pathwise-gps-success', (e) => {
    if (e.detail) {
      onGpsSuccess({ coords: e.detail });
    }
  });

  window.addEventListener('pathwise-gps-error', (e) => {
    if (e.detail?.code === 1) {
      showGpsAlert(
        true,
        'GPS Tracking Permission Revoked',
        'Your browser blocked GPS tracking. Click the address bar lock icon 🔒 to allow location.'
      );
    }
  });

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

  const handleLaunchMission = () => {
    switchTab('map');
    const dock = document.getElementById('mapBottomDock');
    if (dock) dock.classList.remove('is-collapsed');
    const icon = document.getElementById('dockCollapseIcon');
    if (icon) icon.textContent = '▼';
    switchDockTab('telemetry');

    const seed = mainMap?.userLocation ? {
      latitude: mainMap.userLocation.lat,
      longitude: mainMap.userLocation.lng,
      altitude: 30,
      speed: 0,
      accuracy: 5.0,
      timestamp: Date.now()
    } : null;

    tracker.startRealGPS(seed);
  };

  const handleStartSimulation = () => {
    switchTab('map');
    const dock = document.getElementById('mapBottomDock');
    if (dock) dock.classList.remove('is-collapsed');
    const icon = document.getElementById('dockCollapseIcon');
    if (icon) icon.textContent = '▼';
    switchDockTab('telemetry');

    let baseLat = null;
    let baseLon = null;
    if (mainMap?.userLocation) {
      baseLat = mainMap.userLocation.lat;
      baseLon = mainMap.userLocation.lng;
    }
    tracker.startVirtualSimulation(baseLat, baseLon);
  };

  // Home Screen Buttons
  const btnHomeStart = document.getElementById('btnHomeStartJourney');
  if (btnHomeStart) {
    btnHomeStart.addEventListener('click', (e) => {
      e.preventDefault();
      handleLaunchMission();
    });
  }

  document.getElementById('btnHomeExploreMap')?.addEventListener('click', () => switchTab('map'));

  document.getElementById('btnHomeSimulateWalk')?.addEventListener('click', (e) => {
    e.preventDefault();
    handleStartSimulation();
  });

  document.getElementById('btnHomeLaunchArena')?.addEventListener('click', () => switchTab('learn'));
  document.getElementById('btnHomeLaunchCompass')?.addEventListener('click', () => {
    switchTab('learn');
    learnArena.currentMode = 'radar';
    learnArena.render();
  });

  // Dock Collapse Toggle Button
  document.getElementById('btnToggleDockCollapse')?.addEventListener('click', (e) => {
    e.stopPropagation();
    const dock = document.getElementById('mapBottomDock');
    if (!dock) return;
    const isNowCollapsed = dock.classList.toggle('is-collapsed');
    const icon = document.getElementById('dockCollapseIcon');
    if (icon) icon.textContent = isNowCollapsed ? '▲' : '▼';
    sound.playTap();
  });

  // Mini Action Button (Inside Collapsed Dock)
  document.getElementById('btnMiniAction')?.addEventListener('click', async (e) => {
    e.stopPropagation();
    if (tracker.state === 'RECORDING') {
      await tracker.stop();
      renderHomeDashboard();
    } else {
      handleLaunchMission();
    }
  });

  // Dock Segmented Tabs
  document.getElementById('dockTabTelemetry')?.addEventListener('click', () => switchDockTab('telemetry'));
  document.getElementById('dockTabRadar')?.addEventListener('click', () => switchDockTab('radar'));
  document.getElementById('dockTabLearning')?.addEventListener('click', () => switchDockTab('learning'));

  // Quick Pin Proximity Landmark
  document.getElementById('btnQuickPinProximity')?.addEventListener('click', async () => {
    if (currentApproachingLandmark) {
      await pinNearbyLandmark(currentApproachingLandmark);
      const btn = document.getElementById('btnQuickPinProximity');
      if (btn) {
        btn.textContent = '✓';
        btn.style.background = '#10B981';
      }
    }
  });

  // Voice Guidance Toggle
  const btnVoice = document.getElementById('btnToggleVoice');
  btnVoice?.addEventListener('click', () => {
    sound.setVoiceEnabled(!sound.voiceEnabled);
    btnVoice.classList.toggle('active', sound.voiceEnabled);
    if (sound.voiceEnabled) {
      sound.speak('Voice guidance active');
    }
  });

  // In-Journey Micro-Quiz Buttons
  document.getElementById('btnLaunchInJourneyQuiz')?.addEventListener('click', () => {
    launchInJourneyQuiz();
  });
  document.getElementById('btnCloseInJourneyQuiz')?.addEventListener('click', () => {
    const modal = document.getElementById('inJourneyQuizModal');
    if (modal) modal.style.display = 'none';
  });

  // Dock Action Buttons
  document.getElementById('btnDockStart')?.addEventListener('click', () => handleLaunchMission());
  document.getElementById('btnDockSimulate')?.addEventListener('click', () => handleStartSimulation());
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
    'google-streets': 'GOOGLE MAPS',
    'google-hybrid': 'GOOGLE HYBRID',
    'google-terrain': 'GOOGLE TERRAIN',
    'direct-osm': 'DIRECT OSM',
    'osm-hot': 'HUMANITARIAN',
    'esri-streets': 'ESRI STREET',
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
      lblMapStyle.textContent = STYLE_NAMES[styleKey] || 'GOOGLE MAPS';
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

  // ── Manual Sector Picker & Real GPS Grant Handlers ─────────────────────────
  function openSectorPicker() {
    sound.playTap();
    const modal = document.getElementById('sectorPickerModal');
    if (modal) modal.style.display = 'flex';
  }

  function applyManualSector(lat, lon, label) {
    window.__userInitialCoords = { lat, lon };
    tracker.lastLocation = {
      latitude: lat,
      longitude: lon,
      speed: 0,
      accuracy: 5.0
    };
    mainMap?.updateUserLocation(lat, lon);
    mainMap?.recenter(lat, lon, 16);
    hideGpsAlert();

    if (homeBadge) homeBadge.textContent = 'SECTOR LOCKED';
    if (homeLoc) homeLoc.textContent = label.toUpperCase();
    if (homeCoords) homeCoords.textContent = `${lat.toFixed(4)}° N, ${lon.toFixed(4)}° E`;

    getAreaDetails(lat, lon).then(details => {
      if (details) {
        currentAreaDetails = details;
        const streetEl = document.getElementById('hudActiveStreet');
        const suburbEl = document.getElementById('hudActiveSuburb');
        if (streetEl) streetEl.textContent = details.formattedHeadline;
        if (suburbEl) suburbEl.textContent = details.formattedSubtitle;
      }
    });

    const modal = document.getElementById('sectorPickerModal');
    if (modal) modal.style.display = 'none';
    sound.playSuccess();
  }

  document.getElementById('btnHomeGrantGps')?.addEventListener('click', requestActiveGpsFix);
  document.getElementById('btnMapGrantGps')?.addEventListener('click', requestActiveGpsFix);
  document.getElementById('btnHomeSetCity')?.addEventListener('click', openSectorPicker);
  document.getElementById('btnMapSetCity')?.addEventListener('click', openSectorPicker);
  document.getElementById('btnCloseSectorPicker')?.addEventListener('click', () => {
    const modal = document.getElementById('sectorPickerModal');
    if (modal) modal.style.display = 'none';
  });

  // Sector Quick Chips
  document.querySelectorAll('.sector-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('.sector-chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      const lat = parseFloat(chip.getAttribute('data-lat'));
      const lon = parseFloat(chip.getAttribute('data-lon'));
      const name = chip.getAttribute('data-name') || chip.textContent;
      applyManualSector(lat, lon, name);
    });
  });

  // Sector Custom Search Submit
  document.getElementById('btnSearchSectorSubmit')?.addEventListener('click', async () => {
    const q = document.getElementById('sectorSearchInput')?.value?.trim();
    if (!q) return;
    const places = await searchPlaces(q);
    if (places && places.length > 0) {
      applyManualSector(places[0].lat, places[0].lon, places[0].name);
    } else {
      alert(`Could not resolve location for "${q}". Try another landmark or city name.`);
    }
  });

  // Recenter GPS
  document.getElementById('btnRecenterGPS')?.addEventListener('click', () => {
    sound.playTap();
    if (tracker.lastLocation) {
      mainMap.recenter(tracker.lastLocation.latitude, tracker.lastLocation.longitude, 17);
      mainMap.updateUserLocation(tracker.lastLocation.latitude, tracker.lastLocation.longitude);
    } else {
      requestActiveGpsFix();
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
            const name = row.textContent.replace('📍', '').trim();
            applyManualSector(lat, lon, name);
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

// Ambient Holographic Particle & Constellation Canvas
function initHomeHoloCanvas() {
  const canvas = document.getElementById('homeHoloGridCanvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  function resize() {
    canvas.width = canvas.parentElement?.clientWidth || window.innerWidth;
    canvas.height = canvas.parentElement?.clientHeight || window.innerHeight;
  }
  resize();
  window.addEventListener('resize', resize);

  const nodeCount = 36;
  const nodes = [];
  for (let i = 0; i < nodeCount; i++) {
    nodes.push({
      x: Math.random() * (canvas.width || 800),
      y: Math.random() * (canvas.height || 600),
      vx: (Math.random() - 0.5) * 0.4,
      vy: (Math.random() - 0.5) * 0.4,
      radius: Math.random() * 1.8 + 1,
      color: Math.random() > 0.45 ? 'rgba(0, 245, 255, ' : 'rgba(139, 92, 246, '
    });
  }

  let radarAngle = 0;

  function render() {
    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    // Subtle radar sweep arc from center
    const cx = w / 2;
    const cy = h * 0.32;
    const maxR = Math.min(w, h) * 0.42;
    radarAngle += 0.012;

    const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, maxR);
    grad.addColorStop(0, 'rgba(0, 245, 255, 0.04)');
    grad.addColorStop(0.7, 'rgba(0, 245, 255, 0.01)');
    grad.addColorStop(1, 'transparent');
    ctx.fillStyle = grad;
    ctx.beginPath();
    ctx.arc(cx, cy, maxR, 0, Math.PI * 2);
    ctx.fill();

    // Radar beam line
    ctx.strokeStyle = 'rgba(0, 245, 255, 0.14)';
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + Math.cos(radarAngle) * maxR, cy + Math.sin(radarAngle) * maxR);
    ctx.stroke();

    // Draw connecting lines between nearby constellation nodes
    for (let i = 0; i < nodeCount; i++) {
      for (let j = i + 1; j < nodeCount; j++) {
        const dx = nodes[i].x - nodes[j].x;
        const dy = nodes[i].y - nodes[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 110) {
          ctx.strokeStyle = `rgba(0, 245, 255, ${0.14 * (1 - dist / 110)})`;
          ctx.lineWidth = 0.7;
          ctx.beginPath();
          ctx.moveTo(nodes[i].x, nodes[i].y);
          ctx.lineTo(nodes[j].x, nodes[j].y);
          ctx.stroke();
        }
      }
    }

    // Draw and update nodes
    for (const n of nodes) {
      n.x += n.vx;
      n.y += n.vy;
      if (n.x < 0 || n.x > w) n.vx *= -1;
      if (n.y < 0 || n.y > h) n.vy *= -1;

      ctx.fillStyle = `${n.color}0.7)`;
      ctx.beginPath();
      ctx.arc(n.x, n.y, n.radius, 0, Math.PI * 2);
      ctx.fill();
    }

    requestAnimationFrame(render);
  }

  requestAnimationFrame(render);
}

