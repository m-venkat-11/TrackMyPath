/**
 * PathWise City Recall Arena (Learn Module) per PRD Section 7.
 * 3 Cognitive Training Modes: Sequence Recall, Street Compass Radar, Distance Intuition.
 */

import confetti from 'canvas-confetti';
import { db } from './db.js';
import { sound } from './audio.js';
import { calculateHaversine } from './tracker.js';

class LearnArenaController {
  constructor() {
    this.container = null;
    this.currentMode = 'sequence'; // sequence, radar, distance
    this.score = parseInt(localStorage.getItem('pathwise_recall_xp') || '0', 10);
  }

  init(containerId) {
    this.container = document.getElementById(containerId);
    this.render();
  }

  setMode(mode) {
    this.currentMode = mode;
    sound.playTap();
    this.render();
  }

  addXP(pts) {
    this.score += pts;
    localStorage.setItem('pathwise_recall_xp', this.score.toString());
    const el = document.getElementById('statRecallScore');
    if (el) el.textContent = `${this.score} XP`;
  }

  async render() {
    if (!this.container) return;
    const journeys = await db.getAllJourneys();
    const landmarks = await db.getAllLandmarks();

    if (this.currentMode === 'sequence') {
      this.renderSequenceRecall(journeys, landmarks);
    } else if (this.currentMode === 'radar') {
      this.renderStreetRadar(landmarks);
    } else if (this.currentMode === 'distance') {
      this.renderDistanceIntuition(landmarks);
    }
  }

  // ── Mode 1: Sequence Recall ───────────────────────────────────────────────
  renderSequenceRecall(journeys, landmarks) {
    if (landmarks.length < 3) {
      this.container.innerHTML = `
        <div style="text-align:center; padding: 40px 20px;">
          <h3 class="text-cyan" style="font-family:'Outfit'; font-size:1.2rem;">Landmark Data Required</h3>
          <p style="color:#94A3B8; margin-top:8px;">You need at least 3 landmarks logged to test sequence recall.</p>
          <button class="btn-hud-primary" id="btnLearnSeedDemo" style="margin-top:20px;">+ Seed Demo Landmarks</button>
        </div>
      `;
      document.getElementById('btnLearnSeedDemo')?.addEventListener('click', async () => {
        await db.seedDemoDataIfEmpty();
        this.render();
      });
      return;
    }

    const testList = [...landmarks.slice(0, 4)];
    const correctOrder = [...testList];
    const shuffled = [...testList].sort(() => Math.random() - 0.5);

    let currentIndex = 0;

    this.container.innerHTML = `
      <div class="arena-prompt-box">
        <span class="arena-step-label">CHALLENGE 01 • SEQUENCE RECALL</span>
        <h3 class="arena-question-text">Tap the landmarks in the chronological order of your route:</h3>
        <p style="color:#94A3B8; font-size:0.85rem; margin-top:4px;">Next expected target: <strong class="text-cyan">Step #${currentIndex + 1}</strong></p>
      </div>

      <div class="sequence-options-grid" id="seqOptionsGrid">
        ${shuffled.map(lm => `
          <button class="sequence-opt-btn" data-id="${lm.id}">
            <span>${lm.name}</span>
            <span style="font-size:0.75rem; color:#64748B;">${lm.category || 'Point'}</span>
          </button>
        `).join('')}
      </div>

      <div id="seqResultFeedback" style="min-height: 24px; font-family:'JetBrains Mono'; font-size:0.85rem;"></div>
    `;

    const buttons = this.container.querySelectorAll('.sequence-opt-btn');
    buttons.forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        const target = correctOrder[currentIndex];

        if (id === target.id) {
          sound.playTap();
          btn.classList.add('correct');
          btn.disabled = true;
          currentIndex++;

          if (currentIndex === correctOrder.length) {
            sound.playSuccess();
            this.addXP(50);
            confetti({ particleCount: 70, spread: 60, origin: { y: 0.6 } });
            const fb = document.getElementById('seqResultFeedback');
            if (fb) fb.innerHTML = '<span style="color:#2ED573;">✓ EXCELLENT RECALL! +50 XP AWARDED</span>';
            setTimeout(() => this.render(), 2500);
          } else {
            const fb = document.getElementById('seqResultFeedback');
            if (fb) fb.innerHTML = `<span style="color:#00F5FF;">Target #${currentIndex} confirmed. Select target #${currentIndex + 1}</span>`;
          }
        } else {
          sound.playBeep(220, 0.2, 'sawtooth');
          btn.classList.add('wrong');
          const fb = document.getElementById('seqResultFeedback');
          if (fb) fb.innerHTML = '<span style="color:#FF4757;">✗ Incorrect sequence order. Try again.</span>';
          setTimeout(() => btn.classList.remove('wrong'), 900);
        }
      });
    });
  }

  // ── Mode 2: Street Compass Radar ──────────────────────────────────────────
  renderStreetRadar(landmarks) {
    if (landmarks.length < 2) {
      this.container.innerHTML = `
        <div style="text-align:center; padding: 40px 20px;">
          <h3 class="text-cyan">Compass Lock Needs Data</h3>
          <p style="color:#94A3B8; margin-top:8px;">Add or seed landmarks to practice cardinal orientation.</p>
        </div>
      `;
      return;
    }

    const targetLm = landmarks[Math.floor(Math.random() * landmarks.length)];
    const directions = ['N', 'E', 'S', 'W'];
    const correctDir = directions[Math.floor(Math.random() * 4)];

    this.container.innerHTML = `
      <div class="arena-prompt-box" style="text-align:center;">
        <span class="arena-step-label">CHALLENGE 02 • CARDINAL COMPASS RADAR</span>
        <h3 class="arena-question-text">Relative to city center, which direction lies:</h3>
        <h2 class="text-cyan" style="font-family:'Outfit'; font-size:1.4rem; margin-top:8px;">${targetLm.name}</h2>
      </div>

      <div class="compass-radar-layout">
        <div class="compass-dial-wrap">
          <button class="compass-dir-btn north" data-dir="N">N</button>
          <button class="compass-dir-btn east" data-dir="E">E</button>
          <button class="compass-dir-btn south" data-dir="S">S</button>
          <button class="compass-dir-btn west" data-dir="W">W</button>
          <div class="compass-center-target">TAP HEADING</div>
        </div>
      </div>

      <div id="radarFeedback" style="text-align:center; margin-top:24px; font-family:'JetBrains Mono'; font-size:0.85rem; min-height:24px;"></div>
    `;

    this.container.querySelectorAll('.compass-dir-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const chosen = btn.getAttribute('data-dir');
        const fb = document.getElementById('radarFeedback');

        if (chosen === correctDir) {
          sound.playSuccess();
          this.addXP(40);
          confetti({ particleCount: 50, spread: 50, origin: { y: 0.6 } });
          if (fb) fb.innerHTML = `<span style="color:#2ED573;">✓ ACCURATE HEADING! (${chosen}) +40 XP</span>`;
          setTimeout(() => this.render(), 2000);
        } else {
          sound.playBeep(220, 0.2, 'sawtooth');
          if (fb) fb.innerHTML = `<span style="color:#FF4757;">✗ Heading was ${correctDir}. Recalibrating compass...</span>`;
          setTimeout(() => this.render(), 1800);
        }
      });
    });
  }

  // ── Mode 3: Distance Intuition ────────────────────────────────────────────
  renderDistanceIntuition(landmarks) {
    if (landmarks.length < 2) {
      this.container.innerHTML = `<div style="text-align:center; padding: 40px;">Need at least 2 landmarks.</div>`;
      return;
    }

    const lm1 = landmarks[0];
    const lm2 = landmarks[1];
    const actualDist = Math.round(calculateHaversine(lm1.latitude, lm1.longitude, lm2.latitude, lm2.longitude));

    this.container.innerHTML = `
      <div class="arena-prompt-box" style="text-align:center;">
        <span class="arena-step-label">CHALLENGE 03 • DISTANCE INTUITION</span>
        <h3 class="arena-question-text">Estimate the straight-line walking distance between:</h3>
        <p class="text-white" style="font-size:1.05rem; margin-top:8px;">
          <strong class="text-cyan">${lm1.name}</strong> ↔ <strong class="text-teal">${lm2.name}</strong>
        </p>
      </div>

      <div style="max-width:520px; margin: 0 auto; width:100%; text-align:center;">
        <div style="font-family:'JetBrains Mono'; font-size:1.8rem; font-weight:800; color:#FFB800; margin-bottom:14px;" id="sliderDistanceDisplay">
          1000 m
        </div>
        <input type="range" id="guessDistanceSlider" min="100" max="6000" step="50" value="1000" class="hud-slider" style="margin-bottom:20px;" />

        <button class="btn-hud-primary" id="btnSubmitDistanceGuess" style="width:100%; justify-content:center;">
          Lock In Estimate
        </button>
      </div>

      <div id="distFeedback" style="text-align:center; margin-top:20px; font-family:'JetBrains Mono'; font-size:0.9rem; min-height:28px;"></div>
    `;

    const slider = document.getElementById('guessDistanceSlider');
    const disp = document.getElementById('sliderDistanceDisplay');

    slider?.addEventListener('input', (e) => {
      const v = e.target.value;
      if (disp) disp.textContent = v < 1000 ? `${v} m` : `${(v / 1000).toFixed(2)} km`;
    });

    document.getElementById('btnSubmitDistanceGuess')?.addEventListener('click', () => {
      const guess = parseInt(slider.value, 10);
      const diff = Math.abs(guess - actualDist);
      const accuracyPct = Math.max(0, Math.round(100 - (diff / actualDist) * 100));

      sound.playSuccess();
      let earnedXP = Math.round(accuracyPct * 0.8);
      this.addXP(earnedXP);

      const fb = document.getElementById('distFeedback');
      if (fb) {
        fb.innerHTML = `
          <div style="background:rgba(10,20,36,0.85); border:1px solid #00F5FF; padding:16px; border-radius:10px; margin-top:12px;">
            <div style="color:#00F5FF; font-weight:bold; font-size:1.1rem;">Actual Distance: ${actualDist} m</div>
            <div style="color:#F8FAFC; margin-top:4px;">Your Estimate: ${guess} m • Accuracy: ${accuracyPct}%</div>
            <div style="color:#2ED573; margin-top:6px;">+${earnedXP} XP Earned!</div>
          </div>
        `;
      }
      setTimeout(() => this.render(), 3800);
    });
  }
}

export const learnArena = new LearnArenaController();
