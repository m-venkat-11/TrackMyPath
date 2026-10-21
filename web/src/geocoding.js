/**
 * Geocoding & Spatial Intelligence Repository.
 * Powered by OpenStreetMap Nominatim and Overpass API with in-memory caching.
 * Supplies real-time street names, suburb boundaries, and surrounding landmark discovery.
 */

const cache = new Map();
const areaDetailsCache = new Map();
const nearbyLandmarksCache = new Map();

export function calculateBearing(lat1, lon1, lat2, lon2) {
  const toRad = deg => (deg * Math.PI) / 180;
  const toDeg = rad => (rad * 180) / Math.PI;
  const y = Math.sin(toRad(lon2 - lon1)) * Math.cos(toRad(lat2));
  const x = Math.cos(toRad(lat1)) * Math.sin(toRad(lat2)) -
            Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(toRad(lon2 - lon1));
  const deg = (toDeg(Math.atan2(y, x)) + 360) % 360;
  return Math.round(deg);
}

export function bearingToCardinal(bearing) {
  const directions = [
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
  ];
  const index = Math.round(bearing / 22.5) % 16;
  return directions[index];
}

export function calculateDistance(lat1, lon1, lat2, lon2) {
  const R = 6371e3; // meters
  const toRad = d => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

export async function reverseGeocode(lat, lon) {
  const key = `${lat.toFixed(4)},${lon.toFixed(4)}`;
  if (cache.has(key)) return cache.get(key);

  const details = await getAreaDetails(lat, lon);
  if (details) {
    const label = details.formattedHeadline;
    cache.set(key, label);
    return label;
  }
  return null;
}

export async function getAreaDetails(lat, lon) {
  const key = `${lat.toFixed(4)},${lon.toFixed(4)}`;
  if (areaDetailsCache.has(key)) return areaDetailsCache.get(key);

  try {
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=18&addressdetails=1`;
    const res = await fetch(url, {
      headers: {
        'Accept': 'application/json',
        'User-Agent': 'PathWiseHolographicWeb/2.0'
      }
    });
    if (!res.ok) return null;
    const data = await res.json();
    if (data && data.address) {
      const addr = data.address;
      const road = addr.road || addr.pedestrian || addr.path || addr.cycleway || addr.footway || null;
      const suburb = addr.suburb || addr.neighbourhood || addr.residential || addr.city_district || addr.quarter || null;
      const city = addr.city || addr.town || addr.municipality || addr.village || null;
      const state = addr.state || null;

      let headline = road || suburb || city || 'Known Location';
      if (road && suburb && road !== suburb) {
        headline = `${road}, ${suburb}`;
      }

      let subtitle = [suburb, city, state].filter(Boolean).filter(s => !headline.includes(s)).slice(0, 2).join(', ');
      if (!subtitle) subtitle = city || state || 'GPS Position Acquired';

      const details = {
        street: road,
        suburb: suburb,
        city: city,
        state: state,
        formattedHeadline: headline,
        formattedSubtitle: subtitle
      };

      areaDetailsCache.set(key, details);
      return details;
    }
  } catch (e) {
    console.warn('Area details reverse geocoding error:', e);
  }
  return null;
}

export async function fetchNearbyLandmarks(lat, lon, radiusMeters = 1200) {
  const key = `${lat.toFixed(3)},${lon.toFixed(3)}`;
  if (nearbyLandmarksCache.has(key)) return nearbyLandmarksCache.get(key);

  try {
    const query = `
      [out:json][timeout:10];
      (
        node["tourism"~"attraction|museum|viewpoint|monument|artwork|hotel"](around:${radiusMeters},${lat},${lon});
        node["amenity"~"hospital|cafe|restaurant|library|college|university|bank|place_of_worship|theatre|cinema"](around:${radiusMeters},${lat},${lon});
        node["railway"~"station|subway_entrance"](around:${radiusMeters},${lat},${lon});
        node["leisure"~"park|garden|sports_centre"](around:${radiusMeters},${lat},${lon});
        node["historic"](around:${radiusMeters},${lat},${lon});
      );
      out center 25;
    `.trim();

    const url = `https://overpass-api.de/api/interpreter?data=${encodeURIComponent(query)}`;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6500);

    const res = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'PathWiseHolographicWeb/2.0' }
    });
    clearTimeout(timeout);

    if (res.ok) {
      const data = await res.json();
      const elements = data.elements || [];
      const discovered = [];

      for (const el of elements) {
        const pLat = el.lat || el.center?.lat;
        const pLon = el.lon || el.center?.lon;
        const tags = el.tags || {};
        const name = tags.name || tags['name:en'] || null;

        if (name && pLat && pLon) {
          const dist = calculateDistance(lat, lon, pLat, pLon);
          const bearing = calculateBearing(lat, lon, pLat, pLon);
          const cardinal = bearingToCardinal(bearing);
          const category = tags.tourism || tags.amenity || tags.railway || tags.leisure || tags.historic || 'Landmark';

          discovered.push({
            id: `osm-${el.id}`,
            name,
            category: category.charAt(0).toUpperCase() + category.slice(1).replace(/_/g, ' '),
            latitude: pLat,
            longitude: pLon,
            distanceMeters: dist,
            bearingDegrees: bearing,
            cardinalDirection: cardinal
          });
        }
      }

      discovered.sort((a, b) => a.distanceMeters - b.distanceMeters);
      const unique = [];
      const seen = new Set();
      for (const p of discovered) {
        if (!seen.has(p.name.toLowerCase())) {
          seen.add(p.name.toLowerCase());
          unique.push(p);
        }
      }

      if (unique.length > 0) {
        nearbyLandmarksCache.set(key, unique);
        return unique;
      }
    }
  } catch (e) {
    console.warn('Overpass nearby query error, falling back to local/Nominatim:', e.message);
  }

  // Fallback: search prominent POIs in Nominatim
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=json&q=landmark+hospital+metro+station+near+${lat},${lon}&limit=6`;
    const res = await fetch(url, {
      headers: { 'Accept': 'application/json', 'User-Agent': 'PathWiseHolographicWeb/2.0' }
    });
    if (res.ok) {
      const list = await res.json();
      const fallbackList = list.map(item => {
        const pLat = parseFloat(item.lat);
        const pLon = parseFloat(item.lon);
        const dist = calculateDistance(lat, lon, pLat, pLon);
        const bearing = calculateBearing(lat, lon, pLat, pLon);
        return {
          id: `nom-${item.place_id}`,
          name: item.display_name.split(',')[0],
          category: item.type || 'Landmark',
          latitude: pLat,
          longitude: pLon,
          distanceMeters: dist,
          bearingDegrees: bearing,
          cardinalDirection: bearingToCardinal(bearing)
        };
      }).sort((a, b) => a.distanceMeters - b.distanceMeters);

      nearbyLandmarksCache.set(key, fallbackList);
      return fallbackList;
    }
  } catch (err) {
    console.warn('Nominatim fallback error:', err);
  }

  return [];
}

export async function searchPlaces(query) {
  if (!query || query.trim().length < 2) return [];
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=6&addressdetails=1`;
    const res = await fetch(url, {
      headers: {
        'Accept': 'application/json',
        'User-Agent': 'PathWiseHolographicWeb/2.0'
      }
    });
    if (!res.ok) return [];
    const list = await res.json();
    return list.map(item => ({
      name: item.display_name.split(',').slice(0, 3).join(','),
      lat: parseFloat(item.lat),
      lon: parseFloat(item.lon)
    }));
  } catch (e) {
    console.warn('Place search error:', e);
    return [];
  }
}
