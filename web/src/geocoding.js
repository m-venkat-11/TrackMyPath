/**
 * Geocoding Repository using OpenStreetMap Nominatim.
 * Provides reverse-geocoding of coordinates and place search with in-memory caching.
 */

const cache = new Map();

export async function reverseGeocode(lat, lon) {
  const key = `${lat.toFixed(4)},${lon.toFixed(4)}`;
  if (cache.has(key)) return cache.get(key);

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
      const road = data.address.road || data.address.pedestrian || data.address.path || data.address.cycleway;
      const sub = data.address.suburb || data.address.neighbourhood || data.address.residential;
      const city = data.address.city || data.address.town || data.address.village;
      
      let label = '';
      if (road && sub) label = `${road}, ${sub}`;
      else if (road) label = `${road}, ${city || ''}`;
      else if (sub) label = `${sub}, ${city || ''}`;
      else label = data.display_name?.split(',').slice(0, 2).join(',') || 'Known Locality';

      cache.set(key, label);
      return label;
    }
  } catch (e) {
    console.warn('Reverse geocoding error:', e);
  }
  return null;
}

export async function searchPlaces(query) {
  if (!query || query.trim().length < 2) return [];
  try {
    const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=5&addressdetails=1`;
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
