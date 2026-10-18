/**
 * PathWise Persistent Local Storage (IndexedDB) per PRD FR-04.
 * 100% on-device, zero-cloud dependency, with auto-seed demo journeys.
 */

const DB_NAME = 'PathWiseDB';
const DB_VERSION = 1;

class DatabaseManager {
  constructor() {
    this.db = null;
  }

  async open() {
    if (this.db) return this.db;
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = (e) => {
        const db = e.target.result;
        if (!db.objectStoreNames.contains('journeys')) {
          const jStore = db.createObjectStore('journeys', { keyPath: 'id' });
          jStore.createIndex('startTime', 'startTime', { unique: false });
        }
        if (!db.objectStoreNames.contains('landmarks')) {
          const lStore = db.createObjectStore('landmarks', { keyPath: 'id' });
          lStore.createIndex('createdAt', 'createdAt', { unique: false });
        }
      };
      request.onsuccess = (e) => {
        this.db = e.target.result;
        resolve(this.db);
      };
      request.onerror = (e) => {
        console.error('Failed to open IndexedDB', e);
        reject(e);
      };
    });
  }

  async saveJourney(journey) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction('journeys', 'readwrite');
      const store = tx.objectStore('journeys');
      const req = store.put(journey);
      req.onsuccess = () => resolve(journey);
      req.onerror = (e) => reject(e);
    });
  }

  async getAllJourneys() {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction('journeys', 'readonly');
      const store = tx.objectStore('journeys');
      const req = store.getAll();
      req.onsuccess = () => {
        const list = req.result || [];
        list.sort((a, b) => b.startTime - a.startTime);
        resolve(list);
      };
      req.onerror = (e) => reject(e);
    });
  }

  async getJourneyById(id) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction('journeys', 'readonly');
      const store = tx.objectStore('journeys');
      const req = store.get(id);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = (e) => reject(e);
    });
  }

  async deleteJourney(id) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction('journeys', 'readwrite');
      const store = tx.objectStore('journeys');
      const req = store.delete(id);
      req.onsuccess = () => resolve(true);
      req.onerror = (e) => reject(e);
    });
  }

  async saveLandmark(lm) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction('landmarks', 'readwrite');
      const store = tx.objectStore('landmarks');
      const req = store.put(lm);
      req.onsuccess = () => resolve(lm);
      req.onerror = (e) => reject(e);
    });
  }

  async getAllLandmarks() {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction('landmarks', 'readonly');
      const store = tx.objectStore('landmarks');
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = (e) => reject(e);
    });
  }

  async deleteLandmark(id) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction('landmarks', 'readwrite');
      const store = tx.objectStore('landmarks');
      const req = store.delete(id);
      req.onsuccess = () => resolve(true);
      req.onerror = (e) => reject(e);
    });
  }

  async clearAll() {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(['journeys', 'landmarks'], 'readwrite');
      tx.objectStore('journeys').clear();
      tx.objectStore('landmarks').clear();
      tx.oncomplete = () => resolve(true);
      tx.onerror = (e) => reject(e);
    });
  }

  /**
   * Seeds rich initial demo journeys so users can immediately test replay and learn features.
   */
  async seedDemoDataIfEmpty() {
    const list = await this.getAllJourneys();
    if (list.length > 0) return;

    const baseLat = 12.9716;
    const baseLon = 77.5946;

    // Route 1: Downtown to Waterfront Walk (18 breadcrumbs)
    const pts1 = [
      [12.9716, 77.5946],
      [12.9723, 77.5952],
      [12.9731, 77.5960],
      [12.9740, 77.5968],
      [12.9748, 77.5978],
      [12.9757, 77.5985],
      [12.9768, 77.5994],
      [12.9779, 77.6003],
      [12.9789, 77.6015],
      [12.9800, 77.6027],
      [12.9812, 77.6038],
      [12.9824, 77.6050],
      [12.9835, 77.6062],
      [12.9847, 77.6074],
      [12.9859, 77.6086],
      [12.9870, 77.6098],
      [12.9882, 77.6110],
      [12.9895, 77.6125]
    ].map((coord, idx) => ({
      latitude: coord[0],
      longitude: coord[1],
      altitude: 920 + idx * 2,
      speed: 1.35,
      accuracy: 3.5,
      timestamp: Date.now() - 3600000 + idx * 45000
    }));

    const journey1 = {
      id: 'demo-journey-01',
      title: 'Demo: Metro Station to Cubbon Park Promenade',
      startTime: Date.now() - 3600000,
      endTime: Date.now() - 2790000,
      durationSeconds: 810,
      recordedDistanceMeters: 2450.0,
      pointCount: pts1.length,
      status: 'COMPLETED',
      isDemo: true,
      points: pts1
    };

    // Route 2: Old City Heritage Corridor
    const pts2 = [
      [12.9650, 77.5800],
      [12.9660, 77.5810],
      [12.9672, 77.5822],
      [12.9685, 77.5835],
      [12.9698, 77.5849],
      [12.9710, 77.5862],
      [12.9721, 77.5875],
      [12.9733, 77.5888],
      [12.9745, 77.5901],
      [12.9758, 77.5915]
    ].map((coord, idx) => ({
      latitude: coord[0],
      longitude: coord[1],
      altitude: 915 + idx * 1.5,
      speed: 1.25,
      accuracy: 4.0,
      timestamp: Date.now() - 86400000 + idx * 50000
    }));

    const journey2 = {
      id: 'demo-journey-02',
      title: 'Demo: Heritage Gateway & Clocktower Circuit',
      startTime: Date.now() - 86400000,
      endTime: Date.now() - 85900000,
      durationSeconds: 500,
      recordedDistanceMeters: 1620.0,
      pointCount: pts2.length,
      status: 'COMPLETED',
      isDemo: true,
      points: pts2
    };

    await this.saveJourney(journey1);
    await this.saveJourney(journey2);

    // Seed Landmarks
    const landmarks = [
      { id: 'lm-1', name: 'Metro Central Gate 2', category: 'Transit', notes: 'Main escalator entry', latitude: 12.9716, longitude: 77.5946, createdAt: Date.now() },
      { id: 'lm-2', name: 'Victorian Library Arch', category: 'Civic', notes: 'Red brick building', latitude: 12.9779, longitude: 77.6003, createdAt: Date.now() },
      { id: 'lm-3', name: 'Old Town Clocktower', category: 'Civic', notes: 'Four-dial stone tower', latitude: 12.9835, longitude: 77.6062, createdAt: Date.now() },
      { id: 'lm-4', name: 'Waterfront Botanical Lake', category: 'Civic', notes: 'Reflecting pond benches', latitude: 12.9895, longitude: 77.6125, createdAt: Date.now() }
    ];

    for (const lm of landmarks) {
      await this.saveLandmark(lm);
    }
  }

  async exportDataJSON() {
    const journeys = await this.getAllJourneys();
    const landmarks = await this.getAllLandmarks();
    const data = {
      app: 'PathWise',
      version: '2.0-web',
      exportedAt: new Date().toISOString(),
      journeys,
      landmarks
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `pathwise_export_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

export const db = new DatabaseManager();
