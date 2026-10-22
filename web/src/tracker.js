/**
 * PathWise Journey Tracking Engine & Mission Simulator.
 * Provides live 1s duration telemetry, real GPS acquisition, and virtual mission walk.
 */

import { db } from './db.js';
import { sound } from './audio.js';

export function calculateHaversine(lat1, lon1, lat2, lon2) {
  const R = 6371e3; // Earth radius in meters
  const toRad = deg => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export function formatDistance(meters) {
  if (!meters || meters < 0) return '0 m';
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toFixed(2)} km`;
}

export function formatDuration(seconds) {
  if (!seconds || seconds < 0) return '00:00';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  const pad = n => n.toString().padStart(2, '0');
  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(s)}`;
  return `${pad(m)}:${pad(s)}`;
}

class TrackingManager {
  constructor() {
    this.state = 'IDLE'; // IDLE, RECORDING, PAUSED
    this.currentJourney = null;
    this.points = [];
    this.geoWatchId = null;
    this.simInterval = null;
    this.tickerInterval = null;
    this.listeners = new Set();
    this.lastLocation = null;
  }

  subscribe(listener) {
    this.listeners.add(listener);
    this.notify();
    return () => this.listeners.delete(listener);
  }

  notify() {
    const payload = {
      state: this.state,
      journey: this.currentJourney,
      points: this.points,
      lastLocation: this.lastLocation
    };
    this.listeners.forEach(fn => {
      try {
        fn(payload);
      } catch (err) {
        console.warn('Telemetry listener error:', err);
      }
    });
  }

  startTicker() {
    this.stopTicker();
    this.tickerInterval = setInterval(() => {
      if (this.state === 'RECORDING' && this.currentJourney) {
        this.currentJourney.durationSeconds = Math.max(1, Math.floor((Date.now() - this.currentJourney.startTime) / 1000));
        this.notify();
      }
    }, 1000);
  }

  stopTicker() {
    if (this.tickerInterval) {
      clearInterval(this.tickerInterval);
      this.tickerInterval = null;
    }
  }

  _clearWatches() {
    if (this.geoWatchId !== null) {
      try {
        navigator.geolocation?.clearWatch(this.geoWatchId);
      } catch (_) {}
      this.geoWatchId = null;
    }
    if (this.simInterval !== null) {
      clearInterval(this.simInterval);
      this.simInterval = null;
    }
    this.stopTicker();
  }

  startRealGPS(seedLocation = null) {
    this._clearWatches();

    // Safely snapshot & persist any previously active journey without blocking or stomping state
    if (this.currentJourney && this.points.length > 0) {
      const prev = {
        ...this.currentJourney,
        status: 'COMPLETED',
        endTime: Date.now(),
        durationSeconds: Math.max(1, Math.floor((Date.now() - this.currentJourney.startTime) / 1000)),
        points: [...this.points]
      };
      db.saveJourney(prev).catch(e => console.warn('Auto-save prior journey failed:', e));
    }

    sound.playStart();

    this.state = 'RECORDING';
    this.currentJourney = {
      id: 'journey-' + Date.now(),
      title: 'GPS Route: ' + new Date().toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }),
      startTime: Date.now(),
      endTime: null,
      durationSeconds: 0,
      recordedDistanceMeters: 0,
      pointCount: 0,
      status: 'RECORDING',
      isDemo: false
    };
    this.points = [];

    // Seed immediate starting position if known from previous GPS lock or user coordinates
    const initialFix = seedLocation || this.lastLocation || (window.__userInitialCoords ? {
      latitude: window.__userInitialCoords.lat,
      longitude: window.__userInitialCoords.lon,
      altitude: 30,
      speed: 0,
      accuracy: 5.0,
      timestamp: Date.now()
    } : null);

    if (initialFix && initialFix.latitude && initialFix.longitude) {
      this.processLocationFix({
        latitude: initialFix.latitude,
        longitude: initialFix.longitude,
        altitude: initialFix.altitude || 30,
        speed: initialFix.speed || 0,
        accuracy: initialFix.accuracy || 4.0,
        timestamp: Date.now()
      });
    }

    this.startTicker();

    if ('geolocation' in navigator) {
      const handlePos = (pos) => {
        if (this.state !== 'RECORDING') return;
        window.dispatchEvent(new CustomEvent('pathwise-gps-success', { detail: pos.coords }));
        this.processLocationFix({
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
          altitude: pos.coords.altitude || 30,
          speed: (pos.coords.speed !== null && pos.coords.speed !== undefined) ? pos.coords.speed : 0,
          accuracy: pos.coords.accuracy || 4.0,
          timestamp: pos.timestamp || Date.now()
        });
      };

      // Fast single-shot snapshot to warm up GPS hardware quickly
      try {
        navigator.geolocation.getCurrentPosition(
          handlePos,
          (err) => console.log('Fast GPS snapshot warmup:', err.message),
          { enableHighAccuracy: true, timeout: 6000, maximumAge: 5000 }
        );
      } catch (_) {}

      // Ongoing high-accuracy streaming watch
      try {
        this.geoWatchId = navigator.geolocation.watchPosition(
          handlePos,
          (err) => {
            console.warn('High-accuracy GPS watch error, falling back:', err.message);
            window.dispatchEvent(new CustomEvent('pathwise-gps-error', { detail: { code: err.code, message: err.message } }));
            if (this.state === 'RECORDING') {
              try {
                if (this.geoWatchId !== null) navigator.geolocation.clearWatch(this.geoWatchId);
              } catch (_) {}
              this.geoWatchId = navigator.geolocation.watchPosition(
                handlePos,
                (err2) => {
                  console.warn('Standard accuracy GPS error:', err2.message);
                  window.dispatchEvent(new CustomEvent('pathwise-gps-error', { detail: { code: err2.code, message: err2.message } }));
                },
                { enableHighAccuracy: false, maximumAge: 5000, timeout: 20000 }
              );
            }
          },
          { enableHighAccuracy: true, maximumAge: 2000, timeout: 12000 }
        );
      } catch (e) {
        console.error('WatchPosition failed to initialize:', e);
      }
    } else {
      window.dispatchEvent(new CustomEvent('pathwise-gps-error', { detail: { code: 2, message: 'Geolocation not supported in this browser' } }));
    }

    this.notify();
  }

  startVirtualSimulation(baseLat = null, baseLon = null) {
    this._clearWatches();

    if (this.currentJourney && this.points.length > 0) {
      const prev = {
        ...this.currentJourney,
        status: 'COMPLETED',
        endTime: Date.now(),
        durationSeconds: Math.max(1, Math.floor((Date.now() - this.currentJourney.startTime) / 1000)),
        points: [...this.points]
      };
      db.saveJourney(prev).catch(e => console.warn('Auto-save prior journey failed:', e));
    }

    sound.playStart();

    // Dynamically anchor to real acquired position
    if (!baseLat || !baseLon) {
      if (this.lastLocation) {
        baseLat = this.lastLocation.latitude;
        baseLon = this.lastLocation.longitude;
      } else if (window.__userInitialCoords) {
        baseLat = window.__userInitialCoords.lat;
        baseLon = window.__userInitialCoords.lon;
      } else {
        baseLat = 17.7338;
        baseLon = 83.3323;
      }
    }

    this.state = 'RECORDING';
    this.currentJourney = {
      id: 'sim-journey-' + Date.now(),
      title: 'Virtual Walk: Neighborhood Promenade',
      startTime: Date.now(),
      endTime: null,
      durationSeconds: 0,
      recordedDistanceMeters: 0,
      pointCount: 0,
      status: 'RECORDING',
      isDemo: true
    };
    this.points = [];
    this.startTicker();

    // Urban route delta simulation points
    const simSteps = [
      [0.0000, 0.0000],
      [0.0008, 0.0007],
      [0.0016, 0.0014],
      [0.0025, 0.0022],
      [0.0035, 0.0031],
      [0.0046, 0.0038],
      [0.0058, 0.0046],
      [0.0070, 0.0055],
      [0.0083, 0.0066],
      [0.0096, 0.0078],
      [0.0108, 0.0090],
      [0.0121, 0.0102],
      [0.0134, 0.0115],
      [0.0148, 0.0128],
      [0.0162, 0.0142],
      [0.0175, 0.0155],
      [0.0189, 0.0169]
    ];

    let stepIdx = 0;
    this.simInterval = setInterval(() => {
      if (this.state !== 'RECORDING') return;

      if (stepIdx < simSteps.length) {
        const [dLat, dLon] = simSteps[stepIdx];
        const pt = {
          latitude: baseLat + dLat,
          longitude: baseLon + dLon,
          altitude: 920 + stepIdx * 2,
          speed: 1.35 + (Math.random() * 0.2 - 0.1), // ~4.9 km/h
          accuracy: 3.5,
          timestamp: Date.now()
        };
        this.processLocationFix(pt);
        stepIdx++;
      } else {
        clearInterval(this.simInterval);
      }
    }, 1500);

    this.notify();
  }

  processLocationFix(pt) {
    if (this.points.length > 0) {
      const prev = this.points[this.points.length - 1];
      const dist = calculateHaversine(prev.latitude, prev.longitude, pt.latitude, pt.longitude);
      if (dist >= 0.8) { // Filter sub-meter micro-jitter
        this.currentJourney.recordedDistanceMeters += dist;
      }
    }

    this.points.push(pt);
    if (this.currentJourney) {
      this.currentJourney.pointCount = this.points.length;
    }
    this.lastLocation = pt;
    this.notify();
  }

  pause() {
    if (this.state !== 'RECORDING') return;
    this.state = 'PAUSED';
    sound.playTap();
    this.notify();
  }

  resume() {
    if (this.state !== 'PAUSED') return;
    this.state = 'RECORDING';
    sound.playStart();
    this.notify();
  }

  async stop() {
    this._clearWatches();

    const journeyToSave = this.currentJourney;
    const pointsToSave = [...this.points];

    this.state = 'IDLE';
    this.currentJourney = null;
    this.points = [];
    this.notify();

    if (journeyToSave && pointsToSave.length > 0) {
      sound.playStop();
      journeyToSave.status = 'COMPLETED';
      journeyToSave.endTime = Date.now();
      journeyToSave.durationSeconds = Math.max(1, Math.floor((journeyToSave.endTime - journeyToSave.startTime) / 1000));
      journeyToSave.points = pointsToSave;

      try {
        await db.saveJourney(journeyToSave);
      } catch (err) {
        console.error('Failed to save completed journey:', err);
      }
    }
  }
}

export const tracker = new TrackingManager();
