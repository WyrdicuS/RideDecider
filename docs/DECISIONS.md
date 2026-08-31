# RideDecider Architectural Decisions & Behavioral Locks

## ADR-001: HUD Overlay Lifecycle & Accessibility Action Shielding

### Context & Problem
When Uber Driver displays incoming trip offers (Direct Offers or Trip Radar), the HUD overlay must appear instantly (<20ms) and disappear cleanly without lingering on screen, interfering with touch events, or flickering during animation frames.

### Decisions & Invariants (DO NOT BREAK)

1. **Instant Action Dismissal (0 ms):**
   - Whenever the driver interacts with the card actions:
     - `Aceptar` / `Accept`
     - `Emparejar` / `Match`
     - `Rechazar` / `Decline` / `Reject`
     - `✕` / `X` / `Cerrar` / `Close` / `Cancelar` / `Cancel` / `Descartar` / `Dismiss` (including resource IDs matching `*close*`, `*dismiss*`, `*cancel*`, `*reject*`, `*cross*`)
   - The HUD MUST call `HudStateHolder.hideImmediately()` with 0 ms delay.

2. **Touch Transparency (`FLAG_NOT_TOUCHABLE`):**
   - The WindowManager LayoutParams for `TYPE_APPLICATION_OVERLAY` MUST include:
     - `FLAG_NOT_FOCUSABLE`
     - `FLAG_NOT_TOUCHABLE`
     - `FLAG_NOT_TOUCH_MODAL`
     - `FLAG_LAYOUT_IN_SCREEN`
     - `FLAG_HARDWARE_ACCELERATED`
   - The HUD is 100% click-through. Touches pass directly to Uber Driver underneath.

3. **Anti-Flicker & Clean Disappearance on `NO_OFFER` (100 ms):**
   - When the offer card disappears from the screen, `onScreenStateChanged(NO_OFFER)` invokes `HudStateHolder.hideWithDelay(100L)`.
   - 100 ms avoids single-frame 15ms animation jitters while ensuring the HUD disappears in 0.1s when the card is really gone.

4. **Active Radar Offer vs. Idle Map Pill:**
   - An idle `"Radar de viaje"` pill/badge on the map without fare, kinematics, or action buttons MUST be classified as `NO_OFFER`.
   - A `RADAR_OFFER` is only triggered when there is evidence of an active offer card (`Emparejar` button, fare, or kinematics).

5. **Single-Segment Kinematic Fallback:**
   - Many Trip Radar cards only display total trip distance/duration without separate pickup kinematics.
   - `UberAccessibilityParser` defaults missing pickup distance and duration to `0.0 km / 0.0 min`.

6. **Safety Timeout:**
   - `HudOverlayManager.SAFETY_TIMEOUT_MS` is locked to 5–6 seconds as maximum safeguard.
