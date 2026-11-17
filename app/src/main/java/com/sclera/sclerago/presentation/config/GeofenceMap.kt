package com.sclera.sclerago.presentation.config

import com.sclera.sclerago.domain.model.Fence
import com.sclera.sclerago.domain.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * OpenStreetMap (or Esri satellite imagery) via the bundled Leaflet, with address search (Nominatim) and simple fence
 * drawing: Draw, then tap the map to add corners and Finish; tap a fence (or pick it from the list) to drag its corners,
 * add one (the handle in the middle of a side) or remove one (tap it, then Remove corner). The app talks to the page only
 * through evaluateJavascript (`adc*` functions) and reads what it is doing from the page title
 * (`adc:mode:points:selected:dirty:n:corners:corner:suggestions`), so no JavaScript interface is added. Fences go in
 * and out as GeoJSON rings of [lng, lat].
 */
internal const val MAP_HTML = """<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<link rel="stylesheet" href="https://appassets.androidplatform.net/assets/leaflet/leaflet.css">
<style>
html,body{margin:0;height:100%;background:#aad3df}
#m{position:absolute;top:0;left:0;right:0;bottom:0}
#e{position:absolute;inset:0;display:none;align-items:center;justify-content:center;font:15px sans-serif;color:#333;text-align:center;padding:24px}
.h{background:#fff;border:3px solid #f5b82e;border-radius:50%;box-sizing:border-box}
.h.on{background:#f5b82e;border-color:#fff}
.mh{background:rgba(255,255,255,.75);border:2px solid #f5b82e;border-radius:50%;box-sizing:border-box}
</style></head><body>
<div id="m"></div>
<div id="e">The map needs an internet connection.</div>
<script src="https://appassets.androidplatform.net/assets/leaflet/leaflet.js"></script>
<script>
if (!window.L) { document.getElementById('e').style.display = 'flex'; }
else {
  var map = L.map('m', {worldCopyJump: true, zoomControl: false}).setView([20, 0], 2);
  var street = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 19, attribution: '&copy; OpenStreetMap contributors'});
  var satellite = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {maxZoom: 19, attribution: 'Tiles &copy; Esri'});
  street.addTo(map);
  // The view may get its final size after load; let Leaflet re-measure so tiles fill it.
  setTimeout(function () { map.invalidateSize(); }, 300);
  window.addEventListener('resize', function () { map.invalidateSize(); });
  window.adcSetSatellite = function (on) {
    if (on) { map.removeLayer(street); satellite.addTo(map); } else { map.removeLayer(satellite); street.addTo(map); }
  };
  var mk, sugs = [], sugSeq = 0, sugAsk = 0;
  function goTo(lat, lon, name) {
    var p = [lat, lon];
    map.setView(p, 18);
    if (mk) mk.remove();
    mk = L.marker(p).addTo(map).bindPopup(name).openPopup();
  }
  window.adcGoTo = function (lat, lon, name) { goTo(+lat, +lon, String(name || '')); };
  window.adcSearch = function (q) {
    q = String(q || '').trim();
    if (!q) return;
    fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' + encodeURIComponent(q))
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (!d.length) { alert('No place found for "' + q + '"'); return; }
        goTo(+d[0].lat, +d[0].lon, d[0].display_name);
      })
      .catch(function () { alert('Search needs an internet connection.'); });
  };
  // Up to five places for what is being typed; the app reads them with adcSuggestions() once the title says they changed.
  window.adcSuggest = function (q) {
    q = String(q || '').trim();
    var ask = ++sugAsk;
    if (q.length < 2) { sugs = []; sugSeq++; report(); return; }
    fetch('https://nominatim.openstreetmap.org/search?format=json&limit=5&q=' + encodeURIComponent(q))
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (ask !== sugAsk) return;
        sugs = d.map(function (x) { return {name: String(x.display_name || ''), lat: +x.lat, lon: +x.lon}; });
        sugSeq++; report();
      })
      .catch(function () { if (ask === sugAsk) { sugs = []; sugSeq++; report(); } });
  };
  window.adcSuggestions = function () { return sugs; };

  var LINE = '#f5b82e';
  var STYLE = {color: LINE, weight: 2.5, fillColor: LINE, fillOpacity: 0.18};
  var HANDLE = L.divIcon({className: 'h', iconSize: [22, 22]});
  var HANDLE_ON = L.divIcon({className: 'h on', iconSize: [24, 24]});
  var MID = L.divIcon({className: 'mh', iconSize: [16, 16]});
  var fences = [], mode = 'view', draft = [], draftLayer = L.layerGroup().addTo(map);
  var selected = null, corner = -1, handles = [], dirty = false, seq = 0;
  function report() {
    seq++;
    var corners = fences.map(function (p) { return p.getLatLngs()[0].length; }).join(',');
    document.title = 'adc:' + mode + ':' + draft.length + ':' + fences.indexOf(selected) + ':' + (dirty ? 1 : 0) + ':' + seq + ':' + corners
      + ':' + corner + ':' + sugSeq;
  }
  function clearHandles() {
    handles.forEach(function (h) { h.remove(); });
    handles = [];
    if (selected) selected.setStyle({weight: 2.5});
    selected = null;
    corner = -1;
  }
  // A picked fence: its corners can be dragged, or tapped (then removed with Remove corner); a corner is added by
  // tapping or dragging the small handle in the middle of a side.
  function select(p, keepCorner) {
    var picked = keepCorner === undefined ? -1 : keepCorner;
    clearHandles();
    selected = p;
    corner = picked;
    p.setStyle({weight: 4});
    var ring = p.getLatLngs()[0];
    ring.forEach(function (pt, i) {
      var h = L.marker(pt, {draggable: true, icon: i === corner ? HANDLE_ON : HANDLE}).addTo(map);
      h.on('drag', function (ev) {
        var rings = p.getLatLngs();
        rings[0][i] = ev.target.getLatLng();
        p.setLatLngs(rings);
      });
      h.on('dragend', function () { dirty = true; select(p, corner); });
      h.on('click', function (e) { L.DomEvent.stopPropagation(e); select(p, corner === i ? -1 : i); });
      handles.push(h);
    });
    ring.forEach(function (pt, i) {
      var next = ring[(i + 1) % ring.length];
      var mid = L.latLng((pt.lat + next.lat) / 2, (pt.lng + next.lng) / 2);
      var m = L.marker(mid, {draggable: true, icon: MID}).addTo(map);
      function insert(at) {
        var rings = p.getLatLngs();
        rings[0].splice(i + 1, 0, at);
        p.setLatLngs(rings);
        dirty = true;
        select(p, i + 1);
      }
      m.on('click', function (e) { L.DomEvent.stopPropagation(e); insert(mid); });
      m.on('dragend', function (ev) { insert(ev.target.getLatLng()); });
      handles.push(m);
    });
    report();
  }
  // Removes the tapped corner of the picked fence; a fence keeps at least 3 corners.
  window.adcRemoveCorner = function () {
    if (!selected || corner < 0) return;
    var rings = selected.getLatLngs();
    if (rings[0].length <= 3) return;
    rings[0].splice(corner, 1);
    selected.setLatLngs(rings);
    dirty = true;
    select(selected);
  };
  function addFence(rings) {
    var p = L.polygon(rings, STYLE).addTo(map);
    p.on('click', function (e) {
      L.DomEvent.stopPropagation(e);
      if (mode === 'view') select(p);
    });
    fences.push(p);
    return p;
  }
  function drawDraft() {
    draftLayer.clearLayers();
    if (draft.length > 1) L.polyline(draft, {color: LINE, weight: 3}).addTo(draftLayer);
    draft.forEach(function (pt, i) {
      // The first corner is yellow (the fence closes back to it), the others white.
      var c = i === 0 ? LINE : '#ffffff';
      L.circleMarker(pt, {radius: 7, color: c, weight: 2, fillColor: c, fillOpacity: 1}).addTo(draftLayer);
    });
  }
  map.on('click', function (e) {
    if (mode === 'draw') { draft.push(e.latlng); drawDraft(); report(); }
    else if (selected) { clearHandles(); report(); }
  });
  window.adcStartDraw = function () { clearHandles(); mode = 'draw'; draft = []; drawDraft(); report(); };
  window.adcUndo = function () { draft.pop(); drawDraft(); report(); };
  window.adcCancelDraw = function () { mode = 'view'; draft = []; drawDraft(); report(); };
  window.adcFinishDraw = function () {
    if (draft.length >= 3) { addFence([draft.slice()]); dirty = true; }
    mode = 'view'; draft = []; drawDraft(); report();
  };
  // A fence picked from the list: zoomed to, and its corners made draggable.
  window.adcSelectFence = function (i) {
    var p = fences[i];
    if (!p || mode !== 'view') return;
    map.fitBounds(p.getBounds(), {padding: [24, 24]});
    select(p);
  };
  window.adcDeleteFence = function (i) {
    var p = fences[i];
    if (!p) return;
    if (p === selected) clearHandles();
    p.remove();
    fences = fences.filter(function (f) { return f !== p; });
    dirty = true;
    report();
  };
  window.adcGetFences = function () {
    return fences.map(function (p) {
      return p.getLatLngs().map(function (ring) {
        return ring.map(function (pt) { return [pt.lng, pt.lat]; });
      });
    });
  };
  window.adcSetFences = function (data) {
    clearHandles();
    fences.forEach(function (f) { f.remove(); });
    fences = []; mode = 'view'; draft = []; drawDraft();
    (data || []).forEach(function (poly) {
      addFence(poly.map(function (ring) { return ring.map(function (p) { return [p[1], p[0]]; }); }));
    });
    if (fences.length) map.fitBounds(L.featureGroup(fences).getBounds(), {padding: [24, 24]});
    else map.setView([20, 0], 2);
    dirty = false;
    report();
  };
  report();
}
</script></body></html>"""

/**
 * What the map page says it is doing (read from its title): drawing and how many corners so far, which fence is picked
 * (-1: none), whether anything changed since the last load or save, and how many corners each fence has.
 */
internal data class MapState(
    val drawing: Boolean = false,
    val points: Int = 0,
    val selected: Int = -1,
    val dirty: Boolean = false,
    val corners: List<Int> = emptyList(),
    /** The tapped corner of the picked fence (-1: none), which Remove corner takes out. */
    val corner: Int = -1,
    /** Goes up each time the address suggestions change; then they are read with `adcSuggestions()`. */
    val suggestions: Int = 0,
)

/** One address suggestion for what is typed in the search box. */
internal data class PlaceHit(val name: String, val lat: Double, val lon: Double)

/** The page's `adcSuggestions()` answer, or an empty list when it isn't a list of {name, lat, lon}. */
internal fun hitsFromJs(json: String?): List<PlaceHit> = runCatching {
    (Json.parseToJsonElement(json.orEmpty()) as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val name = (o["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val lat = (o["lat"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: return@mapNotNull null
        val lon = (o["lon"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: return@mapNotNull null
        PlaceHit(name.take(300), lat, lon)
    }.take(5)
}.getOrDefault(emptyList())

internal fun parseMapTitle(title: String?): MapState? {
    val parts = title?.takeIf { it.startsWith("adc:") }?.split(':') ?: return null
    if (parts.size < 6) return null
    return MapState(
        drawing = parts[1] == "draw",
        points = parts[2].toIntOrNull()?.coerceAtLeast(0) ?: 0,
        selected = parts[3].toIntOrNull() ?: -1,
        dirty = parts[4] == "1",
        corners = parts.getOrNull(6).orEmpty().split(',').mapNotNull { it.toIntOrNull()?.coerceAtLeast(0) },
        corner = parts.getOrNull(7)?.toIntOrNull() ?: -1,
        suggestions = parts.getOrNull(8)?.toIntOrNull() ?: 0,
    )
}

/** Fences as the page's `adcSetFences` takes them: polygons of rings of [lng, lat] (only finite numbers are written). */
internal fun fencesToJs(fences: List<Fence>): String = JsonArray(fences.map { f ->
    JsonArray(f.rings.map { ring ->
        JsonArray(ring.filter { it.lat.isFinite() && it.lng.isFinite() }.map { p -> JsonArray(listOf(JsonPrimitive(p.lng), JsonPrimitive(p.lat))) })
    })
}).toString()

/** The page's `adcGetFences()` answer as fences, or null when it isn't polygons of rings of [lng, lat]. Rings are left open. */
internal fun fencesFromJs(json: String?): List<Fence>? = runCatching {
    val root = Json.parseToJsonElement(json.orEmpty()) as? JsonArray ?: return null
    root.map { poly ->
        Fence((poly as JsonArray).map { ring ->
            val points = (ring as JsonArray).map { pos ->
                val xy = pos as JsonArray
                val lng = (xy[0] as JsonPrimitive).doubleOrNull ?: return null
                val lat = (xy[1] as JsonPrimitive).doubleOrNull ?: return null
                GeoPoint(lat = lat, lng = lng)
            }
            if (points.size > 1 && points.first() == points.last()) points.dropLast(1) else points
        })
    }
}.getOrNull()
