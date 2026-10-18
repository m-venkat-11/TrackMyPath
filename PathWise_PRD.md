# PATHWISE — Product Requirements Document (PRD)
**Version:** 2.0 — Holographic UI Edition  
**Status:** Build specification for Google Antigravity  
**Platform:** Native Android application  
**Primary implementation target:** Personal Android APK first; public release is out of scope for MVP  
**Product principle:** Futuristic in appearance, practical in use, honest about GPS and map accuracy.

---

## 0. Executive Summary

PathWise is a privacy-first Android app that helps people learn and remember their town or city by recording journeys, visualising the actual paths travelled, identifying available roads and localities, saving personal landmarks, replaying journeys, and practising distance and route recall.

The app should have a **cinematic 3D holographic interface inspired by futuristic interfaces in science-fiction films**: layered translucent panels, luminous route lines, depth, subtle parallax, glowing map markers, animated scanning effects, and clean floating controls. This is a visual style, not a claim that a phone can project a physical hologram.

The UI must remain readable, calm, high-performance, and accessible. Visual effects must never obscure map labels, tracking status, permissions, or essential controls.

**Product promise:** “Record your routes. Learn your landmarks. Remember your city.”

### Core principles
1. **Learning first:** PathWise helps users understand their surroundings, not merely navigate to a destination.
2. **Actual route fidelity:** Preserve the GPS path actually travelled; never silently replace it with a suggested route.
3. **Privacy by default:** No account required for core features; journeys remain on-device unless the user explicitly exports or enables sync.
4. **Truthful tracking:** Never show “Recording” unless the location service is actually active.
5. **Free-first:** Use free development tools and open-source components where practical; respect all map-data licences, attribution, quotas, and fair-use limits.
6. **Premium-looking, not cluttered:** Holographic styling supports the task; it must not become visual noise.
7. **Graceful degradation:** If 3D effects or map services are unavailable, the app remains useful with a simpler interface.

---

## 1. Problem Statement

Some people repeatedly travel familiar routes but still struggle to recall street names, locality names, landmark order, or distances between places. Conventional navigation tools mainly help people get to a destination; they do not necessarily help users build a mental map of their city.

PathWise combines:
- Explicitly started GPS journey recording
- A detailed interactive map
- Available street and locality labels
- Personal landmark notes
- Journey replay and route comparison
- Distance measurement and estimation
- Gentle recall quizzes
- A clean, futuristic, 3D-inspired interface that makes route exploration engaging

PathWise must not diagnose a medical condition or imply that difficulty remembering routes means a person has a disorder.

---

## 2. Product Goals and Success Measures

### MVP goals
- Build a genuine native Android app and installable debug APK.
- Record the user's route when they explicitly start tracking.
- Support background recording through a correctly configured Android foreground service.
- Persist location points incrementally to local storage.
- Display and replay the route that was actually recorded.
- Let users add, edit, and remove custom landmarks.
- Support distance tools and basic memory quizzes.
- Deliver a distinctive holographic visual identity without harming readability or performance.
- Work without an account or paid backend for core features.

### Success measures for the personal MVP
- A user can start, pause, resume, and stop a journey without confusion.
- Saved GPS points survive app restarts after they have been committed to the database.
- The app clearly communicates when tracking is active, paused, interrupted, or unavailable.
- A saved journey can be reopened and replayed.
- Users can correct missing or incorrect local landmark labels manually.
- The main map and primary controls remain legible in bright outdoor conditions and dark environments.
- Holographic effects do not cause persistent stutter, crashes, or excessive battery use during a test journey.

These are acceptance targets, not guarantees of universal GPS accuracy or uninterrupted tracking.

---

## 3. Target Users and Primary Scenarios

### Primary users
- People who want to become more familiar with their own town or city.
- Students and commuters who repeatedly travel between home, college, shops, and transport points.
- People learning unfamiliar neighbourhoods.
- Users who prefer a modern, futuristic interface but need practical tools.

### Primary scenarios
1. Record “Home to College” while travelling.
2. Lock the phone and continue a supported background recording.
3. Open the saved journey later and replay the actual GPS trace.
4. Tap landmarks along the trace and learn their names.
5. Add a local name for a small shop, junction, shortcut, or familiar building.
6. Hide labels and test recall of the route and landmark order.
7. Estimate the distance between two saved places, then compare the estimate with the measured distance.
8. Compare two routes taken to the same destination.
9. Use the app offline to review previously saved routes and notes.
10. Understand clearly when GPS or map data is inaccurate or unavailable.

---

## 4. Scope and Priorities

### P0 — Essential MVP
- First-run introduction and contextual permission flow.
- Holographic home dashboard.
- Interactive map with current location when permitted.
- Start / pause / resume / stop recording.
- Native foreground location service and persistent notification.
- Incremental local database writes.
- Journey list, rename, delete, and detail view.
- Route polyline representing the actual recorded path.
- Journey replay with timeline.
- User-created landmarks and notes.
- Straight-line distance measurement.
- One basic route-memory quiz.
- Tracking status, GPS accuracy, offline, error, and recovery states.
- Privacy controls and manual export.
- Reduced-motion / simpler-effects option.
- Functional and performance testing on a physical Android phone.

### P1 — After core reliability
- Road/locality search and reverse geocoding.
- Road-distance calculation through a selected routing provider.
- Route comparison.
- Distance estimation quiz and progress.
- Landmark categories and optional photos.
- GPX/JSON export and import.
- Optional offline map caching only where the provider licence allows it.

### P2 — Future
- Optional cloud backup or cross-device sync.
- Voice reading of landmarks.
- Spaced repetition for forgotten routes.
- Additional languages and local place-name variants.
- Optional home-screen widget and reminders.
- More advanced 3D terrain or route visualisation if performance and map data justify it.

### Non-goals for MVP
- Physical hologram projection.
- Full turn-by-turn navigation.
- Always-on or covert tracking.
- Public social profiles or shared location feeds.
- Paid AI services as a dependency.
- Guaranteed naming of every road or informal landmark.
- Guaranteed background tracking under every Android device and battery setting.
- Play Store publication as a prerequisite.

---

## 5. Visual Direction — “Clean Holographic Navigation HUD”

### 5.1 Design intent
Create the feeling of a precise futuristic navigation console: layered glass-like panels floating over a dark map, cyan/teal route traces, restrained violet accents, thin technical outlines, subtle depth, and purposeful motion. It should feel cinematic but not resemble a cluttered game HUD.

**Reference adjectives:** holographic, spatial, precise, elegant, minimal, high-tech, calm, legible, responsive.

**Avoid:** excessive neon, rainbow gradients everywhere, tiny text, busy grid overlays, constant flicker, oversized glowing borders, unnecessary spinning objects, illegible translucent text, and fake data.

### 5.2 Design tokens
- **Base background:** near-black navy / deep midnight blue.
- **Surface layers:** dark blue-grey panels with restrained transparency.
- **Primary accent:** electric cyan.
- **Secondary accent:** teal.
- **Tertiary accent:** violet used sparingly for selected or secondary states.
- **Success:** readable green, not the primary decorative colour.
- **Warning:** amber.
- **Error / stopped / permission issue:** accessible red/orange with text and icon; never colour alone.
- **Text primary:** near-white.
- **Text secondary:** cool grey with sufficient contrast.
- **Map route:** bright cyan line with a subtle glow or halo; the route core must remain sharply visible.
- **Map labels:** use the map provider's readable labels or a carefully designed label overlay; don't cover important names with decorative effects.

All colours must be centralised as theme tokens, not hard-coded throughout the UI. Maintain contrast for essential text and controls.

### 5.3 Surfaces and depth
- Use layered cards with modest corner radii, thin low-opacity borders, and controlled shadows.
- Use translucent/glass effects selectively. If the platform or GPU cannot render blur efficiently, use opaque dark surfaces with a gradient edge instead.
- Use depth to show hierarchy: map in the background, route and landmarks in the middle, controls and info panels in the foreground.
- Keep key buttons visually solid enough to be easy to tap.
- No essential text may rely on background blur or glow to remain readable.

### 5.4 Holographic map treatment
- The map is the main visual canvas.
- Render the actual GPS trace as a clear route line; glow is an additional halo, not a substitute for a clear line.
- Selected landmarks may have a pulsing ring, small vertical beacon, or brief scan sweep.
- Optional faint coordinate/grid marks may appear at low opacity, but must not obscure real streets.
- A route replay may animate a small position marker along the recorded path.
- The user's location must have a distinct, accessible symbol and an accuracy radius when available.
- Avoid excessive 3D tilt by default because tilted maps can make street labels harder to read.
- Offer a flat-map mode or simple-effects mode.

### 5.5 Motion language
Use animation to communicate state:
- Start recording: brief route-line activation and a small status transition.
- GPS fix acquired: subtle location marker settle-in.
- Landmark selected: short ring expansion or beacon pulse.
- Journey replay: marker follows saved coordinates and timeline progress.
- Saving: short confirmation, not a long cinematic sequence.
- Error: static clear icon and message; no distracting flashing.

Motion constraints:
- Typical UI transitions: about 150–250 ms.
- Larger route-reveal effects: brief and skippable.
- No continuous animation on every panel.
- Avoid flicker, rapid flashing, or intense strobing.
- Respect Android's reduced-motion / animator-duration settings where possible.
- Keep animation off or minimal when battery saver is active or device performance is poor.

### 5.6 Typography and iconography
- Use a clean, legible sans-serif font.
- Use monospaced or technical numerals only for small data readouts such as distance, elapsed time, or coordinates.
- Body text should remain comfortably readable on a phone; do not shrink labels to achieve a sci-fi look.
- Use consistent vector icons and text labels for important actions.
- Avoid relying on symbols alone for Start, Pause, Stop, permissions, and recording states.

### 5.7 Responsive layout
- Design primarily for portrait phones.
- The map should occupy most of the screen.
- Put essential actions within comfortable thumb reach.
- Use a bottom control dock or compact floating action group.
- Expand secondary details in bottom sheets or side panels instead of permanently covering the map.
- Landscape mode can be supported later, but portrait must be complete.
- Adapt spacing and panel sizes for smaller screens and Android display scaling.

### 5.8 Accessibility and outdoor usability
- Maintain high contrast for essential labels.
- Touch targets should be approximately 48 dp or larger.
- Do not encode status by colour alone; pair colour with an icon and text.
- Support system font scaling where practical.
- Test the interface in direct sunlight and at night.
- Add a “Simple UI / Reduced Effects” setting that retains all functionality.
- Avoid interaction patterns that require precise tapping on tiny map markers; use a list or selection sheet alternative.

### 5.9 Honest visualisation
- Do not render fake route lines, fabricated location names, fictional metrics, or simulated GPS states as real.
- Any demo route must be visibly marked “Demo”.
- A map may be styled cinematically, but geographic labels and the route trace must remain accurate to their underlying data.
- A decorative scan animation must not imply that the app has discovered or verified a landmark.

---

## 6. Information Architecture and Screens

### 6.1 App navigation
Primary navigation:
1. **Home** — start journey, recent routes, quick actions.
2. **Map** — explore, record, select places.
3. **Journeys** — saved recordings and replay.
4. **Learn** — route recall and distance quizzes.
5. **Settings** — privacy, permissions, map configuration, visual effects.

Use a compact bottom navigation bar or equivalent with clear labels. Avoid a complicated 3D menu that hides standard navigation.

### 6.2 Onboarding
Explain:
- PathWise records only when the user starts a journey.
- Location access is used to record and display their path.
- Active background tracking uses a persistent Android notification.
- GPS accuracy varies by device, surroundings, and signal.
- Core journey data is stored locally by default.
- Map labels may be missing or incorrect and can be corrected manually.

Request permissions contextually, when the user first uses the feature. Do not request every permission during the first launch.

### 6.3 Home dashboard
Visual structure:
- Greeting/title and small PathWise emblem.
- Large primary **Start Journey** control.
- Secondary **Explore Map** action.
- Compact holographic summary cards for recent journey count and learning progress, only when actual data exists.
- Recent journeys list.
- Tracking state banner if a recording is active.
- Subtle animated map/route motif in the background, not a distracting 3D object.

Empty state: explain how to record the first journey. Do not display fabricated stats.

### 6.4 Live map and recording screen
Show:
- Full or near-full-screen map.
- Current location and accuracy indication when available.
- Actual recorded path as it grows.
- Elapsed time, recorded distance, and GPS state.
- Start / Pause / Resume / Stop controls.
- A visible state label: Recording, Paused, Waiting for GPS, Permission Needed, or Interrupted.
- Stop action available in-app and via the system notification where supported.
- A confirmation before discarding a journey.
- Minimal animation while recording to reduce distraction and battery use.

The recording service must not depend on the map screen remaining visible.

### 6.5 Journey library
Each journey card should display only real stored information:
- Name or date-based default title.
- Start date/time.
- Duration.
- Recorded distance estimate.
- Point count.
- Completed or interrupted status.
Use holographic edge highlights sparingly. Support search/sort later if needed.

### 6.6 Journey detail and replay
- Draw the saved GPS path.
- Show start/end time, duration, distance, and point count.
- Provide play/pause and a timeline slider.
- Show available road/locality names and saved personal landmarks.
- Allow adding a landmark at a selected point.
- Clearly distinguish the recorded path from any suggested route.
- Offer a simple list-based route sequence as an alternative to map-only review.

### 6.7 Explore map
- Pan/zoom the map.
- Search for places if a geocoding provider is configured.
- Select a point to create a personal landmark.
- Display map provider attribution.
- Show a clear unavailable/offline state if tiles or search cannot load.
- Don't claim that all streets or locality names are complete.

### 6.8 Saved places
Users can create a landmark from the current location, a selected map point, or a saved journey point.

Fields:
- Name (required)
- Category (optional)
- Notes (optional)
- Latitude/longitude
- Source: user-created or provider-supplied
- Optional photo in a later milestone

User-created labels must not be silently overwritten by provider data.

### 6.9 Learn screen
Initial activities:
- Landmark order quiz.
- Hide-label route recall.
- Identify a saved landmark.
- Estimate distance between saved places.
- Review missed questions.

Tone must be supportive. No shaming, public ranking, or claims that quiz results diagnose memory ability.

### 6.10 Distance tool
- Select two saved places or map points.
- Show straight-line distance with explicit label.
- Show road distance only if a routing provider returns a valid result.
- Travel-time estimates must be labelled estimates.
- If road routing is unavailable, don't invent a value.

### 6.11 Settings
- Location permission status and Android settings guidance.
- Tracking notification explanation.
- Map/geocoding provider and attribution.
- Visual intensity: Cinematic / Balanced / Reduced Effects.
- Haptic feedback toggle if implemented.
- Data export and delete controls.
- Privacy explanation.
- App version and non-sensitive diagnostics.

---

## 7. Functional Requirements

### FR-01: Explicit recording
A journey starts only after the user presses Start and grants the necessary permissions. No covert or automatic recording.

### FR-02: Background recording
Use a native Android foreground location service for an active recording. Follow target Android SDK rules for foreground-service types, notification permission, and location permissions. Provide a persistent notification with a Stop action. Never claim tracking is active unless the service is active.

### FR-03: Location point model
Persist each accepted GPS point with:
- Unique ID
- Journey ID
- Latitude and longitude
- Timestamp
- Horizontal accuracy when available
- Optional altitude and speed
- Quality status

Use sensible quality checks and retain enough information to explain uncertainty. Avoid smoothing the trace so aggressively that it no longer reflects the path travelled.

### FR-04: Incremental persistence
Write points to Room/SQLite as they arrive or in small, safe batches. The route must not exist only in memory. Recover all points that have already been committed after app restart.

### FR-05: Journey lifecycle
Support start, pause, resume, stop, completed, and interrupted/recovered states. Pausing stops adding points. Stopping finalises duration and distance. Repeated Stop actions must be safe.

### FR-06: Distance calculation
Calculate recorded distance from sequential accepted points using a geodesic/Haversine calculation. Label it as recorded GPS distance, which can be affected by noise. Keep road distance separate.

### FR-07: Map provider abstraction
Separate map rendering, geocoding, place search, and routing. A provider can be replaced without rewriting the entire app. Respect API keys, attribution, quotas, licensing, caching, and rate limits.

### FR-08: Place labels
Display names only when supplied by map/geocoding data or entered by the user. Allow correction. Do not fabricate missing names.

### FR-09: Offline support
Location recording and local storage should work without internet if the device location service is available. Previously saved journeys, landmarks, and quizzes should be viewable offline. Map tiles and geocoding may require internet unless legitimately cached.

### FR-10: Recovery
On restart, detect an unfinished journey and show it as interrupted/recovered. Restore points already saved and let the user resume or finalise. Never fabricate missing coordinates.

### FR-11: Export
Allow explicit export to JSON or GPX. Warn that route exports contain sensitive location information. Do not export automatically.

### FR-12: UI performance
Map gestures, recording controls, and text must remain responsive. Animations must not block GPS persistence or database writes. Use simpler effects on lower-performance devices and during battery saver.

### FR-13: UI state correctness
UI state must be driven by actual tracking/service state, not animation state. If service status is unknown, display “Checking tracking status” or an honest error state rather than “Recording”.

### FR-14: Units and localisation
Use kilometres/metres by default. Keep strings externalised for future language support. Avoid embedding language-specific text throughout code.

---

## 8. Technical Architecture

### Recommended stack
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3, with a custom design system for the holographic look
- **Architecture:** Simple MVVM with repositories and use cases
- **Local storage:** Room / SQLite
- **Background GPS:** Android foreground service using a suitable Android location API
- **Map rendering:** Evaluate MapLibre first for an open-source renderer; compare alternatives against local data quality and visual requirements
- **Map tiles/data:** Choose a provider with acceptable terms and coverage. Do not assume public OpenStreetMap tile servers are unlimited production infrastructure.
- **Geocoding/routing:** Optional provider interfaces; core recording and learning must not depend on paid API calls
- **3D effects:** Prefer Compose Canvas, vector layers, gradients, and restrained animated overlays for MVP. Consider a dedicated 3D engine only if a real 3D scene adds necessary value; do not introduce heavyweight 3D dependencies just for glow or depth.
- **Build:** Gradle; local debug APK
- **Cloud:** None required for MVP
- **Authentication:** None required for core functionality

### Performance strategy for the holographic UI
- Keep GPS collection and database writes independent of rendering.
- Avoid per-frame recomputation of the full route.
- Simplify or decimate only the display polyline when needed; preserve raw stored points.
- Avoid heavy blur layers over the full map.
- Avoid continuously animated elements on every card.
- Use GPU-friendly effects and profile on a physical device.
- Stop or reduce decorative animations when screens are not visible.
- Prioritise stable map gestures and recording controls over visual spectacle.

### Suggested project structure
- `app/`
  - `ui/theme/` — tokens, typography, shapes, glow and surface styles
  - `ui/components/` — holographic panels, status chips, buttons, map overlays
  - `ui/home/`
  - `ui/map/`
  - `ui/recording/`
  - `ui/journeys/`
  - `ui/landmarks/`
  - `ui/learning/`
  - `ui/distance/`
  - `ui/settings/`
  - `data/local/` — Room entities, DAOs, database
  - `data/repository/`
  - `data/map/`
  - `data/geocoding/`
  - `data/routing/`
  - `domain/model/`
  - `domain/usecase/`
  - `location/` — service, location client, quality filters
  - `util/` — distance calculations, formatting, errors

Keep the initial project straightforward. Do not over-engineer it into many modules before the first APK builds.

### Core entities

**Journey**
- id
- title
- startedAt
- endedAt (nullable while active)
- status
- recordedDistanceMeters
- createdAt
- updatedAt

**TrackPoint**
- id
- journeyId
- latitude
- longitude
- timestamp
- accuracyMeters (nullable)
- altitudeMeters (nullable)
- speedMetersPerSecond (nullable)
- qualityStatus

**Landmark**
- id
- name
- category
- latitude
- longitude
- notes
- source
- createdAt
- updatedAt

**QuizAttempt**
- id
- quizType
- relatedJourneyId or landmark IDs
- answeredAt
- correctCount
- questionCount

---

## 9. Permissions, Privacy, and Security

- Request foreground location permission in context.
- Request background location only if required by the implementation and supported by a clear user explanation.
- Implement foreground-service and notification permissions for the target Android version.
- Keep tracking visible and user-controlled.
- Include Stop Tracking in the notification where supported.
- Do not upload or share route history without explicit consent.
- No account required for core features.
- Do not collect contacts, call logs, advertising IDs, or unrelated device data.
- Do not commit API keys or secrets to source control.
- Follow map provider terms, attribution, caching, and rate limits.
- Explain that GPS and background recording can be interrupted by device settings, permissions, weak signal, or operating-system restrictions.

---

## 10. Free-First Constraints

The MVP should be buildable and usable at zero cost for personal testing, but no provider should be described as unlimited-free without verifying its current terms.

- Use Android Studio, Kotlin, Jetpack Compose, Room, and open-source libraries.
- Keep data local for the first version.
- Do not require paid AI APIs.
- Do not require cloud storage for core journeys.
- Recording, replay, user landmarks, distance calculation, and quizzes must work without paid geocoding or routing.
- Map tiles and search must be configurable and respect the selected provider's limits.
- Do not violate public tile-server policies.
- Local APK installation is enough for MVP; Play Store publication is not required.

---

## 11. Error States and Edge Cases

Handle:
- Location permission denied or revoked.
- Location services disabled.
- Foreground service unable to start.
- Notification permission/state issues where applicable.
- Weak GPS accuracy or no fix.
- Internet unavailable.
- Map/geocoding/routing provider unavailable or rate-limited.
- App process killed or device rebooted during a journey.
- Database write error or insufficient storage.
- Duplicate or out-of-order timestamps.
- Journey with one or zero usable points.
- User stops a journey more than once.
- No provider label for a location.
- User-created landmark differs from provider data.
- Battery saver or reduced performance.
- Reduced-motion setting enabled.
- GPU or rendering performance too low for decorative effects.

Always preserve existing saved data and explain the actual state. Do not invent coordinates, names, distances, or tracking status.

---

## 12. Acceptance Criteria

The MVP is ready for personal testing when:
1. The app builds as a native Kotlin Android APK.
2. The app installs and opens on a physical Android phone.
3. The UI follows the defined clean holographic design system.
4. Simple-effects mode preserves usability without decorative animation.
5. User can start, pause, resume, and stop a journey.
6. Active tracking displays a persistent notification as required.
7. In supported conditions, recording continues while the app is backgrounded and screen is locked.
8. Location points are written incrementally to the local database.
9. Previously committed points survive app restart.
10. Offline recording continues when device location is available; points are stored locally.
11. Saved journeys can be reopened, replayed, renamed, exported, and deleted.
12. The replay path follows recorded GPS points rather than a newly suggested route.
13. User-created landmarks can be added, edited, and deleted.
14. Straight-line and road distances are distinguished.
15. Missing labels and provider errors are communicated honestly.
16. Core features work without account creation or cloud backend.
17. Animations do not block recording or database writes.
18. No precise route history is uploaded without explicit consent.

---

## 13. Testing Plan

### Functional tests
- Journey lifecycle transitions.
- Local database writes and recovery.
- Landmark CRUD.
- Route replay and timeline.
- Distance calculations using known test coordinates.
- Export/import validation.
- Quiz scoring and missed-question review.

### Device tests
- App foregrounded.
- App backgrounded.
- Screen locked during a test journey.
- Internet disabled.
- Permission denied then granted.
- Location services disabled then enabled.
- Battery saver enabled.
- Process killed and app reopened.
- Device rebooted during a test.
- Small screen and larger font size.
- Outdoor sunlight and dark environment.
- Reduced-effects mode.
- Repeated map gestures while recording.

### Visual/performance tests
- Ensure map labels remain readable behind panels.
- Ensure route glow does not obscure the actual route line.
- Check animation smoothness on the target device.
- Confirm no constant animation continues unnecessarily in background.
- Test with a large saved journey.
- Confirm decorative effects can be disabled.
- Confirm all important actions have text or accessible descriptions.
- Confirm no placeholder route or fabricated statistic appears in the production UI.

### Map-data validation
Test several known roads and landmarks in the user's town. Compare map labels against signs and local knowledge. Record missing/wrong names and assess manual correction or another provider. Do not assume a provider is uniformly accurate everywhere.

---

## 14. Development Milestones

### Milestone 0 — Environment and blank APK
- Inspect available development environment.
- Create native Kotlin Android project.
- Configure Gradle and SDK versions deliberately.
- Create the theme foundation and one simple launch screen.
- Build a debug APK.
- Provide exact steps to install on a physical phone.
- Do not implement every feature yet.

### Milestone 1 — Visual design system
- Define colour, typography, spacing, shapes, panel styles, icons, and animation rules.
- Build home screen and bottom navigation using demo-free empty states.
- Build reusable holographic panels and status chips.
- Add Balanced and Reduced Effects modes.
- Validate readability and performance.

### Milestone 2 — Local-first data
- Add Room entities and DAOs.
- Build journey list and lifecycle states.
- Add manual landmark creation/editing/deletion.
- Add local export.

### Milestone 3 — GPS recording
- Implement permission flow.
- Add native location collection and foreground service.
- Persist points incrementally.
- Implement start/pause/resume/stop and recovery.
- Test background/locked-screen behaviour.

### Milestone 4 — Map
- Integrate selected map SDK and tile source.
- Display current location and actual track points.
- Draw recorded path with restrained holographic styling.
- Add map attribution and provider error states.
- Validate local map coverage.

### Milestone 5 — Route learning
- Journey replay.
- Landmark sequence.
- Hide-label recall quiz.
- Straight-line distance tool.
- Distance estimation quiz.

### Milestone 6 — Reliability and personal release
- Test offline and interrupted states.
- Test battery saver and permission changes.
- Fix crashes and data loss issues.
- Profile visual effects on the physical phone.
- Build and install a release APK.
- Document limitations and provider configuration.

Implement one milestone at a time. Build and test before moving forward.

---

## 15. Antigravity Agent Instructions

Treat this PRD as the product source of truth.

### Before coding
1. Inspect the current repository and environment.
2. Confirm Android Studio/SDK/Gradle availability.
3. Propose the architecture and identify any unresolved provider decisions.
4. List dependencies, permissions, API keys, and any usage limits.
5. Keep the next implementation step small and verifiable.

### During implementation
- Build a real native Android application in Kotlin. Do not create a website inside a WebView as a substitute for native background GPS.
- Use Jetpack Compose and Material 3 with a custom holographic design system.
- Use Room for local journey persistence.
- Use a native Android foreground service for active background location recording.
- Keep route recording independent from UI animation and screen lifecycle.
- Keep map renderer, tile provider, geocoding, and routing replaceable.
- Do not introduce paid APIs, ads, analytics, login, or cloud dependencies without approval.
- Never invent API keys, coordinates, place names, route metrics, or test results.
- Do not claim a feature works until it has built and been tested.
- Build after each milestone and fix compilation errors before advancing.
- Keep code readable and beginner-maintainable.
- Add tests for distance calculations and journey state transitions.
- Respect map attribution, licences, quotas, and caching terms.
- Keep API keys out of source control.
- Preserve user data when practical during schema changes.
- Ensure all core actions remain legible and operable in Reduced Effects mode.
- If a dependency is unavailable, explain the issue and offer a free alternative.
- Ask before enabling any paid service or uploading precise location history.

### First task — Milestone 0 only
Create or configure the native Kotlin Android project, establish the basic PathWise theme, build a minimal launch screen, generate a debug APK, and provide exact installation steps for a physical Android device. Do not start the GPS service, map integration, or full feature set until the blank app builds successfully.

### Definition of done for each milestone
- Project builds successfully.
- Changed files are summarised.
- Test steps are documented.
- Known limitations are stated.
- No unrequested paid service or hidden tracking has been introduced.
