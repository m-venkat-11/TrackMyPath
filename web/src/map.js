/**
 * PathWise Leaflet Holographic Map Controller.
 * Features 3D drone tilt perspective, Carto Dark Matter basemap,
 * glowing laser route polylines, pulsing user beacon, and laser distance tool.
 */

import L from 'leaflet';
import { calculateHaversine } from './tracker.js';
import { sound } from './audio.js';

export class HolographicMapController {
  constructor(containerId, options = {}) {
    this.containerId = containerId;
    this.options = options;
    this.map = null;
    this.userMarker = null;
    this.routeCore = null;
    this.routeHalo = null;
    this.landmarksLayer = null;
    this.measureMarkers = [];
    this.measurePolyline = null;
    this.measurePoints = [];
    this.isMeasureActive = false;
    this.onMeasureCallback = null;
    this.onMapClickCallback = null;
  }

  init(center = [12.9716, 77.5946], zoom = 15) {
    const el = document.getElementById(this.containerId);
    if (!el || this.map) return;

    this.map = L.map(this.containerId, {
      center,
      zoom,
      zoomControl: false,
      attributionControl: true
    });

    this.currentTileLayers = [];
    this.currentStyle = 'direct-osm'; // 'direct-osm', 'esri-dark', 'cyberpunk-osm'

    this.landmarksLayer = L.layerGroup().addTo(this.map);

    // Initialise with 100% Direct Accurate OpenStreetMap (Zero API key required)
    this.setMapStyle('direct-osm');

    // Map Click Listener
    this.map.on('click', (e) => {
      const { lat, lng } = e.latlng;
      if (this.isMeasureActive) {
        this.handleMeasureClick(lat, lng);
      } else if (this.onMapClickCallback) {
        this.onMapClickCallback(lat, lng);
      }
    });

    // Invalidate size on window resize
    window.addEventListener('resize', () => {
      this.map?.invalidateSize();
    });

    // Initial size check
    setTimeout(() => this.map?.invalidateSize(), 200);
  }

  updateUserLocation(lat, lon) {
    if (!this.map) return;
    const latLng = [lat, lon];

    if (!this.userMarker) {
      const userIcon = L.divIcon({
        className: 'user-beacon-leaflet-icon',
        html: '<div class="user-beacon-wrap"><div class="user-beacon-dot"></div><div class="user-beacon-pulse"></div></div>',
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      });
      this.userMarker = L.marker(latLng, { icon: userIcon, zIndexOffset: 1000 }).addTo(this.map);
    } else {
      this.userMarker.setLatLng(latLng);
    }
  }

  recenter(lat, lon, zoom = 16) {
    if (!this.map) return;
    this.map.setView([lat, lon], zoom, { animate: true });
  }

  setRoutePoints(points) {
    if (!this.map) return;
    const latLngs = points.map(p => [p.latitude, p.longitude]);

    if (latLngs.length < 2) {
      if (this.routeCore) this.map.removeLayer(this.routeCore);
      if (this.routeHalo) this.map.removeLayer(this.routeHalo);
      this.routeCore = null;
      this.routeHalo = null;
      return;
    }

    if (!this.routeHalo) {
      this.routeHalo = L.polyline(latLngs, {
        color: '#00F5FF',
        weight: 10,
        opacity: 0.25,
        lineCap: 'round',
        lineJoin: 'round'
      }).addTo(this.map);

      this.routeCore = L.polyline(latLngs, {
        color: '#00F5FF',
        weight: 3.5,
        opacity: 0.95,
        lineCap: 'round',
        lineJoin: 'round'
      }).addTo(this.map);
    } else {
      this.routeHalo.setLatLngs(latLngs);
      this.routeCore.setLatLngs(latLngs);
    }
  }

  setLandmarks(landmarks) {
    if (!this.map || !this.landmarksLayer) return;
    this.landmarksLayer.clearLayers();

    landmarks.forEach(lm => {
      const icon = L.divIcon({
        className: 'custom-lm-icon',
        html: `<div class="custom-landmark-pin" title="${lm.name}"></div>`,
        iconSize: [20, 20],
        iconAnchor: [10, 10]
      });

      const m = L.marker([lm.latitude, lm.longitude], { icon });
      m.bindPopup(`
        <div style="background:#0D1B2E; color:#F8FAFC; padding:6px; border-radius:6px; font-family:'Outfit', sans-serif;">
          <strong style="color:#00F5FF;">${lm.name}</strong><br/>
          <span style="font-size:0.75rem; color:#94A3B8;">${lm.category || 'Landmark'}</span>
          ${lm.notes ? `<p style="font-size:0.8rem; margin-top:4px;">${lm.notes}</p>` : ''}
        </div>
      `);
      this.landmarksLayer.addLayer(m);
    });
  }

  // ── Laser Distance Measurement Mode ─────────────────────────────────────────
  toggleMeasureMode(active, callback) {
    this.isMeasureActive = active;
    this.onMeasureCallback = callback;
    if (!active) {
      this.clearMeasurement();
    }
  }

  handleMeasureClick(lat, lon) {
    if (this.measurePoints.length >= 2) {
      this.clearMeasurement();
    }

    this.measurePoints.push([lat, lon]);
    sound.playTap();

    const mIcon = L.divIcon({
      className: 'measure-pin-icon',
      html: '<div style="width:12px;height:12px;background:#FFB800;border:2px solid #FFF;border-radius:50%;box-shadow:0 0 10px #FFB800;"></div>',
      iconSize: [12, 12],
      iconAnchor: [6, 6]
    });
    const m = L.marker([lat, lon], { icon: mIcon }).addTo(this.map);
    this.measureMarkers.push(m);

    if (this.measurePoints.length === 2) {
      sound.playLaserLock();
      const p1 = this.measurePoints[0];
      const p2 = this.measurePoints[1];

      this.measurePolyline = L.polyline(this.measurePoints, {
        color: '#FFB800',
        weight: 3.5,
        dashArray: '8, 8',
        opacity: 0.95
      }).addTo(this.map);

      const dist = calculateHaversine(p1[0], p1[1], p2[0], p2[1]);
      
      // Calculate Azimuth / Bearing in degrees
      const dLon = ((p2[1] - p1[1]) * Math.PI) / 180;
      const y = Math.sin(dLon) * Math.cos((p2[0] * Math.PI) / 180);
      const x = Math.cos((p1[0] * Math.PI) / 180) * Math.sin((p2[0] * Math.PI) / 180) -
                Math.sin((p1[0] * Math.PI) / 180) * Math.cos((p2[0] * Math.PI) / 180) * Math.cos(dLon);
      let brng = (Math.atan2(y, x) * 180) / Math.PI;
      brng = (brng + 360) % 360;

      const walkingMins = Math.round(dist / 83.3); // 5km/h = 83.3m/min

      if (this.onMeasureCallback) {
        this.onMeasureCallback({
          distMeters: dist,
          bearingDeg: Math.round(brng),
          walkMinutes: walkingMins
        });
      }
    }
  }

  clearMeasurement() {
    this.measureMarkers.forEach(m => this.map?.removeLayer(m));
    this.measureMarkers = [];
    if (this.measurePolyline) {
      this.map?.removeLayer(this.measurePolyline);
      this.measurePolyline = null;
    }
    this.measurePoints = [];
  }

  setMapStyle(styleName = 'direct-osm') {
    if (!this.map) return;
    this.currentStyle = styleName;

    // Remove any active tile layers
    if (this.currentTileLayers && this.currentTileLayers.length > 0) {
      this.currentTileLayers.forEach(layer => {
        try { this.map.removeLayer(layer); } catch (e) {}
      });
    }
    this.currentTileLayers = [];

    const container = this.map.getContainer();
    if (container) {
      container.classList.remove(
        'map-style-direct',
        'map-style-hot',
        'map-style-esri-streets',
        'map-style-esri-sat',
        'map-style-esri-dark',
        'map-style-cyberpunk',
        'map-style-opentopo'
      );
    }

    if (styleName === 'osm-hot') {
      if (container) container.classList.add('map-style-hot');
      const hot = L.tileLayer('https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, <a href="https://www.hotosm.org/">HOT</a>',
        subdomains: ['a', 'b', 'c'],
        maxZoom: 19
      }).addTo(this.map);
      this.currentTileLayers.push(hot);
    } else if (styleName === 'esri-streets') {
      if (container) container.classList.add('map-style-esri-streets');
      const esriStreet = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}', {
        attribution: 'Tiles &copy; Esri &mdash; Source: Esri, DeLorme, NAVTEQ',
        maxZoom: 19
      }).addTo(this.map);
      this.currentTileLayers.push(esriStreet);
    } else if (styleName === 'esri-satellite') {
      if (container) container.classList.add('map-style-esri-sat');
      const sat = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
        attribution: 'Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS',
        maxZoom: 19
      }).addTo(this.map);
      const labels = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}', {
        attribution: '',
        maxZoom: 19
      }).addTo(this.map);
      this.currentTileLayers.push(sat, labels);
    } else if (styleName === 'esri-dark') {
      if (container) container.classList.add('map-style-esri-dark');
      const base = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}', {
        attribution: 'Tiles &copy; Esri &mdash; Esri, DeLorme, NAVTEQ',
        maxZoom: 16
      }).addTo(this.map);

      const ref = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}', {
        attribution: '',
        maxZoom: 16
      }).addTo(this.map);

      this.currentTileLayers.push(base, ref);
    } else if (styleName === 'opentopo') {
      if (container) container.classList.add('map-style-opentopo');
      const topo = L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', {
        attribution: 'Map: &copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>, SRTM | Style: &copy; <a href="https://opentopomap.org">OpenTopoMap</a>',
        subdomains: ['a', 'b', 'c'],
        maxZoom: 17
      }).addTo(this.map);
      this.currentTileLayers.push(topo);
    } else if (styleName === 'cyberpunk-osm') {
      if (container) container.classList.add('map-style-cyberpunk');
      const osm = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 19
      }).addTo(this.map);
      this.currentTileLayers.push(osm);
    } else {
      // Default: 'direct-osm' (100% Accurate Direct Street & Road Map, crystal clear, zero API key)
      this.currentStyle = 'direct-osm';
      if (container) container.classList.add('map-style-direct');
      const osm = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 19
      }).addTo(this.map);
      this.currentTileLayers.push(osm);
    }

    // Bring visual overlays to front
    if (this.landmarksLayer) {
      this.landmarksLayer.bringToFront?.();
    }
    if (this.routeHalo) {
      this.routeHalo.bringToFront?.();
    }
    if (this.routeCore) {
      this.routeCore.bringToFront?.();
    }
  }

  toggleNextMapStyle() {
    const cycle = [
      'direct-osm',
      'osm-hot',
      'esri-streets',
      'esri-satellite',
      'esri-dark',
      'cyberpunk-osm',
      'opentopo'
    ];
    const idx = cycle.indexOf(this.currentStyle);
    const nextStyle = cycle[(idx + 1) % cycle.length];
    this.setMapStyle(nextStyle);
    return nextStyle;
  }

  fitBounds(points) {
    if (!this.map || points.length < 2) return;
    const latLngs = points.map(p => [p.latitude, p.longitude]);
    this.map.fitBounds(L.latLngBounds(latLngs), { padding: [40, 40] });
  }
}

