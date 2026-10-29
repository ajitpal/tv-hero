# tv-hero design direction (v1, 29 Oct 2026)

**The feeling we design for: "This is finally my TV."** Calm, cinematic, ad-free, and fully owned. Every screen answers three questions at a glance: *what's on, who's watching, who can control this TV.*

Contents: 1 visual system · 2 mobile Home · 3 mobile Remote · 4 TV Home · 5 security UX · 6 components · 7 TV App Library, Settings, Privacy and Profiles · 8 mobile Now Playing · 9 mobile Privacy & Debloat · 10 Devices & Sessions · 11 mobile Settings, Scenes, multi-TV · 12 Family, Guest mode, Parental controls · 13 onboarding and pairing · 14 critical flows · 15 premium details · 16 build status.

Reference devices:
- **Phone:** iPhone 15 Pro (393 × 852 pt), designed iOS-first. tv-hero ships on Android (Poco M8, Android 15), so every section ends with Android notes.
- **TV:** Mi TV 4A Pro. Its UI is 1280 × 720 at 213 dpi; all TV measurements are in **dp at 720p**.
- **TV hardware limits** (a design constraint, not an excuse): 1 GB RAM, Mali-450 GPU with OpenGL ES 2.0, 32-bit CPU. No real-time blur, no video backgrounds, no heavy parallax shaders. "Depth" on the TV comes from scale, shadow, light and pre-baked artwork.

---

## 1. Visual style

### 1.1 Principles, applied

| Principle | On the TV (10-foot) | On the phone |
|---|---|---|
| **Clarity** | One focused thing at a time; text ≥ 18 sp; no more than three rows visible | One primary action per screen; status in words ("Home Wi-Fi · encrypted"), never icons alone |
| **Deference** | Artwork is the UI; chrome fades to 0 % after 6 s idle | Content and controls, not decoration; no gradients behind text, no brand colour floods |
| **Depth** | Focus lifts tiles (scale, shadow, light sweep); shelf art sits *behind* the dock | Translucent bars over scrolling content; sheets layer over the page they came from |
| **Ownership** | Nothing appears that the owner didn't put there: no sponsored rows, ever | Every change is reversible and logged; the owner always sees who has access |

### 1.2 Colour (dark-first)

Neutral, near-black, one accent, and semantic colours that always mean the same thing.

| Token | Dark (default) | Light | Use |
|---|---|---|---|
| `bg` | `#000000` phone / `#05070C` TV | `#F2F2F7` | Page background. On the TV, near-black avoids crushed LCD blacks |
| `surface1` | `#1C1C1E` | `#FFFFFF` | Cells, cards |
| `surface2` | `#2C2C2E` | `#E5E5EA` | Remote buttons, pressed cells, alerts |
| `glass` | white 10 % + 1 px white 12 % stroke | black 4 % | TV pills, dock band, phone floating controls |
| `label` | `#F5F5F7` | `#000000` | Primary text |
| `secondary` | `#98989F` | `#6C6C70` | Secondary text (both ≥ 4.5:1 on their background) |
| `tertiary` | `#6E6E73` | `#8E8E93` | Hints, disabled |
| `separator` | `#38383A` | `#C6C6C8` | Hairlines (0.5 pt) |
| `accent` | `#0A84FF` | `#0062CC` | Interactive text, focus rings, selected tab |
| **`local`** | `#30D158` green | `#248A3D` | **Controlling over the home Wi-Fi** |
| **`remote`** | `#5E5CE6` indigo | `#5856D6` | **Controlling over the internet (encrypted)** |
| `attention` | `#FF9F0A` | `#C93400` | Needs a decision: approval waiting, time running out |
| `danger` | `#FF453A` | `#D70015` | Revoke, delete, restore-to-factory, security alerts |
| `kids[0..9]` | 10 profile colours (`#3D7BFF`, `#E5484D`, `#30A46C`, `#E8B931`, `#8E4EC6`, `#F76B15`, `#12A594`, `#D6409F`, `#6E56CF`, `#0090FF`) | same | Profile avatars and rings only |

Rules:
- The accent never marks status.
- `local` and `remote` never mean anything else.
- `danger` appears only on destructive actions and real security events.
- No colour is the only carrier of meaning: always pair it with a word or a symbol.

### 1.3 Typography

| | Phone (iOS: SF Pro · Android: Inter, OFL, tabular figures on) | TV (Outfit, already in the launcher, OFL) |
|---|---|---|
| Display | — | Clock 104 sp ExtraLight; hero title 40 sp Light |
| Large Title | 34/41 Bold | — |
| Title 1 / 2 / 3 | 28/34 Bold · 22/28 Bold · 20/25 Semibold | 32 sp Regular (screen titles) |
| Headline | 17/22 Semibold | 24 sp Medium (focused tile label) |
| Body | 17/22 Regular | 22 sp Regular |
| Callout / Subhead | 16/21 · 15/20 | 20 sp (metadata) |
| Footnote / Caption | 13/18 · 12/16 | 18 sp (minimum on the TV) |

- **Dynamic Type everywhere on the phone.** Everything is laid out in sp/pt so it survives the Accessibility XL sizes. Rows grow taller, nothing truncates mid-word, and the remote switches to a two-column layout at AX sizes.
- **Numbers that change** (time left, clock, volume) use tabular figures, so they don't jitter.
- Sentence case everywhere except tab names and buttons, which use title case as on iOS.

### 1.4 Iconography

- **iOS:** SF Symbols, weight matching the adjacent text. Outline when idle, filled when selected (tab bar).
- **Android:** Material Symbols *Rounded*, weight 400, optical size 24. Fill 0 → 1 for selected. This is the closest Android match to SF Symbols.
- **TV:** the same Rounded set, 32 dp in pills, always paired with a label on focus.
- **Status glyphs** (one meaning each):
  - house = local
  - globe with lock = internet, encrypted
  - shield = protection on
  - shield with slash = protection off
  - person + key = paired device

### 1.5 Materials

| Material | Phone | TV |
|---|---|---|
| Bars (nav, tab) | Blur 30 + `bg` at 72 %. iOS: `.bar`. Android 12+: `RenderEffect` blur; below that, 94 % opaque | — |
| Floating controls (now-playing mini bar, remote) | "Thick" material: blur 40 + `surface1` at 80 % | — |
| Glass (pills, dock band) | — | **No live blur** (GPU). White 10 % fill + 1 dp white 12 % stroke over pre-blurred art: the artwork is shrunk to 48 × 27 px and stretched back, which is a blur that costs one tiny texture (`HeroArt`) |
| Sheets | iOS sheet: grabber, 10 pt top radius, page behind scales to 94 % | Full-screen overlay at `bg` 92 % |

### 1.6 Motion

- **Respond within 100 ms, finish within 350 ms.** Use springs, not easing curves:
  - phone: response 0.35, damping 0.85
  - TV focus: response 0.22, damping 0.8
- **TV focus, the most important animation in the product:**
  - the focused tile scales 1.00 → 1.10 and its shadow grows 0 → 24 dp (black 60 %)
  - one glass highlight *springs* from tile to tile instead of per-tile borders (the launcher already does this)
  - a light sweep (white 0 → 18 % → 0 diagonal gradient) crosses the tile once, 450 ms after focus lands
  - **parallax:** the tile's art shifts ±4 dp opposite to the last D-pad direction, then settles over 600 ms. That gives depth from across the room without a gyroscope or a shader.
- **Chrome fades:** after 6 s with no key, the clock and pills fade to 0 % (300 ms) and the dock dims to 70 %. Any key brings everything back in 120 ms.
- **Reduce Motion:** scale and sweep become a 150 ms cross-fade; parallax is off. This follows the system setting on both devices.
- **Phone haptics:**
  - *selection* on toggles, segmented controls and wheels
  - *light impact* on every remote press
  - *success* when pairing completes
  - *warning* when a security action needs confirmation

### 1.7 Spacing and shape

- **4 pt grid.**
  - Phone: page margins 16 pt (20 on Pro Max); section gap 24; card padding 16; inset-grouped cells 10 pt radius, cards 16 pt radius, both continuous corners ("squircle"); minimum touch target 44 pt (48 dp on Android).
  - TV: title-safe margins 64 dp horizontal and 36 dp vertical (5 %). Tiles 16 dp radius, pills fully round, gutters 24 dp between dock tiles, rows 40 dp apart.

---

## 2. Mobile Home / Dashboard

**Job:** in one glance, tell the owner *what's on, who's watching, whether everything is safe*, and put the five most common actions under the thumb.

### 2.1 Layout (top to bottom, 393 pt wide)

```
┌───────────────────────────────────────────┐
│ ◉ Home Wi-Fi · 2 can control      [ ⏻ ]   │  ← status bar row (44 pt)
│ Living Room ⌄                              │  ← Large Title = TV name; ⌄ = switch TV
├───────────────────────────────────────────┤
│ ┌───────────────────────────────────────┐ │
│ │ [blurred app art, 16:9, dimmed 45 %]  │ │  ← NOW PLAYING card, 361 × 220
│ │ ◐ Aarav · 25 min left                 │ │    profile chip (avatar 22 pt + name)
│ │                                       │ │
│ │ Peppa Pig: The Big Day Out            │ │    Title 2, max 2 lines
│ │ YouTube Kids · since 7:12 PM          │ │    Subhead secondary
│ │  ⏮   ⏯   ⏭            ▸ Remote        │ │    transport (44 pt) + Remote button
│ └───────────────────────────────────────┘ │
│                                           │
│ ┌──────────┐ ┌──────────┐                 │  ← QUICK CONTROLS, 2 × 2 grid
│ │ ⏸        │ │ 🌙       │                 │    each 174 × 92, radius 16
│ │ Pause TV │ │ Sleep    │                 │    icon in 34 pt colour circle
│ └──────────┘ └──────────┘                 │    label Headline
│ ┌──────────┐ ┌──────────┐                 │
│ │ 👥 Who's │ │ ⏱ More   │                 │
│ │ Watching │ │ Time     │                 │
│ └──────────┘ └──────────┘                 │
│                                           │
│ NEEDS YOU (only when something does)      │  ← attention section, orange dot
│ ▢ Racing Game wants to open   Approve ›   │
│                                           │
│ TODAY                                     │  ← Screen-time rings, horizontal
│  (◯ Aarav 65/90)  (◯ Anaya 60/60 ✓)  …   │    ring 64 pt, Apple Activity style
│                                           │
│ SCENES                                    │  ← horizontally scrolling chips
│ [Movie Night] [Kids' Hour] [Bedtime]  +   │
│                                           │
│ RECENT                                    │  ← 3 rows + "See All"
├───────────────────────────────────────────┤
│  Home   Remote   Family   Privacy   ⚙︎     │  ← tab bar (blur), 49 pt + home indicator
└───────────────────────────────────────────┘
```

### 2.2 Behaviour

- **Control-status row (top):** the trust signal, always visible.
  - **Left side:** coloured dot + plain words.
    - `● Home Wi-Fi` (green) when on the home network
    - `● Internet · encrypted` (indigo) when over the internet
    - `○ Can't reach TV` (tertiary) when unreachable
  - **Then "· N can control":** tapping it opens *Who can control this TV* (see §5.3).
  - **Right side:** the power button (circle, 36 pt) sends standby / wake.
- **Now Playing card:**
  - The art is the app's banner, blurred (iOS: material over the image; Android: `RenderEffect`).
  - Tap opens the full Now Playing sheet (detents: medium, large).
  - The card has four states, each with its own artwork treatment:
    - *Playing*
    - *Home screen*: shows the TV home's own art
    - *TV off*: grey, "In standby · rules still apply"
    - *Locked*: orange chip "Paused by Ajit · 7:40 PM"
  - When the TV doesn't share a title, it falls back to the app name. It never shows a blank title.
- **Quick controls:** a long-press on a tile shows its options. Long-press Sleep for 15 / 30 / 60 / Off; long-press Pause TV for "Pause for 30 min".
- **Needs You** only appears when something needs a decision. When it's empty there's no section and no "all clear" banner: calm by default.
- **Rings:**
  - The ring fills with the kid's colour, and turns `attention` orange in the last 10 minutes.
  - A tap opens the child's page.
  - Rings use the screen-time metaphor people already know from iOS.
- **Pull to refresh:** everything also refreshes live every 5 s while visible.

### 2.3 Android adaptation

- Edge-to-edge, with the status row under the system status bar.
- Predictive back on pushed pages.
- The tab bar stays custom, iOS-style, not Material 3's pill indicator. It is still 48 dp+ and announces "Tab, 1 of 5" to TalkBack.
- Blur via `RenderEffect` on API 31+ (the Poco M8 runs 35).
- No Material You dynamic colour: the palette is the product.

---

## 3. Mobile Remote

**Job:** control the TV one-handed, eyes on the TV, from anywhere in the room.

### 3.1 Layout

Always dark, even in light mode, like the Apple TV Remote. It's used in dim rooms, and that keeps it consistent.

```
┌───────────────────────────────────────────┐
│ Living Room ⌄          ● Home Wi-Fi   ⏻   │  ← compact header, 52 pt
│                                           │
│        ┌─────────────────────────┐        │
│        │                         │        │  ← TOUCH SURFACE 330 × 330, radius 40
│        │       swipe = arrows     │        │    surface2; faint ring guides at 8 %
│        │    click / tap = OK      │        │    arrows render on press only
│        │                         │        │
│        └─────────────────────────┘        │
│   Pad ◐ Buttons                           │  ← segmented: trackpad vs click-pad
│                                           │
│   ( ‹ )   ( TV )   ( ⏯ )   ( 🔇 )         │  ← 4 round buttons, 64 pt, 20 pt apart
│                                           │
│   ┌────┐                        ┌────┐    │
│   │ +  │      ( ⌨︎ )   ( ≡ )      │ ⌃  │    │  ← volume capsule (left), channel (right)
│   │ ─  │                        │ ⌄  │    │    64 × 128, radius 32
│   └────┘                        └────┘    │
│                                           │
│ ▢ Peppa Pig · YouTube Kids        ⏯       │  ← mini now-playing bar (material)
└───────────────────────────────────────────┘
```

### 3.2 Interaction

- **Trackpad mode (default):**
  - A swipe moves focus one tile per 60 pt of travel; a fast flick moves three.
  - A tap or click is OK.
  - A long-press (500 ms) is the long-press on the TV (tile menu).
  - Each focus step plays a light haptic tick, so the thumb feels the grid.
- **Buttons mode** shows four arrow zones and a centre OK, for people who prefer clicks. Today's build ships this click-pad.
- **Keyboard:** the ⌨︎ button opens a text field docked above the system keyboard. Typing streams to the TV (Bluetooth HID keyboard). "Send" adds Enter.
  - Not yet possible: auto-opening the keyboard when the TV focuses a text box. That needs the Android TV Remote protocol (see §7).
- **Power:**
  - Off: the ⏻ button sends standby (guard's lock-screen action) or Bluetooth "Power".
  - On: may not work. The TV drops Wi-Fi in deep standby; to be tested.
- **Volume:**
  - Press and hold repeats at 8 per second, with a light haptic each step.
  - A volume HUD (thin capsule) appears on the phone *and* the TV shows its own.
- **Reachability:** every primary control sits in the lower two-thirds of the screen. The header only holds status and the TV switcher.
- **Connection-aware controls:**
  - Without Bluetooth, arrows and volume show a one-line hint ("Connect Bluetooth for arrows and volume, Set Up ›") instead of failing silently.
  - Home, Back and app launching always work over Wi-Fi or the internet.
- **Game Controller** is a separate full-screen mode, entered from a header button. It rotates to landscape and is laid out like a real controller: D-pad left, A B X Y right, two sticks, L/R shoulders on the top edge.

### 3.3 Android adaptation

- The volume keys on the phone control the TV's volume while the Remote is open.
- A Quick Settings tile ("TV Remote") opens straight into the Remote.
- An optional lock-screen media notification gives play/pause and volume.

---

## 4. TV Home (10-foot)

**Job:** content first, nothing sold to you, and every app one or two clicks away.

### 4.1 Layout (1280 × 720 dp; safe area 64/36)

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │                                                                        │
 │  GOOD EVENING, AARAV                                   (A) (⇥) (◉) (⚙) │ ← top bar: greeting 18 sp,
 │  7:42                                                                  │   clock 104 sp ExtraLight,
 │  Thursday 29 October                                                   │   pills 56 dp glass
 │                                                                        │
 │  [ FULL-BLEED SHELF ART of the focused dock app, pre-blurred,          │ ← CINEMATIC SHELF:
 │    dimmed 55 % at bottom, vignette at edges ]                          │   top 62 % of screen
 │                                                                        │
 │  YouTube Kids                                                          │ ← focused app name 40 sp Light
 │  Continue: Peppa Pig: The Big Day Out · 12 min left                    │   + one line of context 20 sp
 │ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───  │ ← DOCK: 6 tiles 16:9,
 │ │ ▶ FOCUS  │ │          │ │          │ │          │ │          │ │     │   176 × 99 each, 24 gutter,
 │ └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ └───  │   on a glass band
 │                                                                        │
 │  CONTINUE WATCHING                                                     │ ← row 2 (peeks 30 %),
 │  [title card] [title card] [title card] …                              │   full on Down
 └────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Focus engine

- **Start:** focus lands on the dock's first tile, or on the last-used tile if Home is re-entered within 30 minutes.
- **Up** from the dock goes to the top-bar pills (rightmost). **Down** goes to Continue Watching, then the App Library.
- **Navigation is deterministic:** one key press moves one step, and the ends of a row don't wrap.
- **Shelf art** cross-fades in 300 ms, 200 ms after focus settles. The delay avoids flicker while scrolling.
- **The focused tile:** scales 1.10, gets a 24 dp shadow, a light sweep and parallax (§1.6), and shows its label under it in 24 sp. Other tiles stay at 85 % brightness.
- **Long-press OK on a tile:** shows a glass menu with Move, Hide from Home, App Info, and *Why can't Aarav open this?* (kid profiles).

### 4.3 Rows and modes

- **Continue Watching:**
  - Built from tv-hero's own watch history: app + title + time, per profile.
  - OK reopens the app, and most apps resume.
  - Android TV 9 doesn't let a third-party launcher read other apps' Watch Next rows, so "jump to the exact episode" only works for apps that accept deep links. The design never promises more than the app supports.
- **App Library:** a 6-column grid of every app the current profile may open, alphabetical, with a "Recently added" strip first. Apps waiting for approval show a lock badge and "Waiting for a parent".
- **Profiles:**
  - The avatar pill (A) shows who's watching.
  - OK opens *Who's Watching?* (built). Switching to Parent asks for the PIN on the TV.
  - Kids get their own dock automatically (their allowed apps), and their own name in the greeting.
- **Ambient / Art mode:**
  - After 5 minutes idle, the clock and a slow time-of-day sky (`Sky`) or an owner-chosen photo set (stored on the TV, never fetched from ad networks) take over.
  - Any key wakes it.
- **Zero sponsored content.** There is no code path for remote content in the launcher. The home makes **no network requests** (permission-level guarantee: only the guard's local server listens).

### 4.4 TV system overlays (built, tested on the real TV)

- Blocked, time's up, bedtime, paused by a parent, Who's Watching?, PIN pad.
- All are focusable and use one centred column, 32 sp titles and white pill buttons.
- Focus starts on the safe action. Back never leaks to the app underneath.

---

## 5. Security UX patterns

Security has to be visible and calm: plain words, one colour per meaning, and the owner can always undo.

### 5.1 Status, always visible

- The phone's status row (§2.2) and the TV's top bar both show how the TV is controlled.
- **On the TV,** a small glyph next to the clock appears only while a phone is actively controlling over the internet: an indigo globe with a lock, plus "Ajit's phone" for 5 s on first use.
- **Rule:** remote control is never invisible on the TV.

### 5.2 Pairing (physical presence, every time)

1. The phone finds the TV on the same Wi-Fi.
2. The TV shows a 6-digit code **and** "Allow *Ajit's phone* to control this TV? [Allow] [Don't Allow]", with focus on *Don't Allow*.
   - Today's build shows the code only. The on-TV confirmation is the next security step.
3. The phone enters the code and the parent password, or creates it if first.
4. Both sides create long-term keys:
   - phone: an EC P-256 key in Android Keystore / StrongBox (iOS: Secure Enclave)
   - TV: its own key
   - They pin each other. Today the build hands over a shared HMAC secret; moving to per-device key pairs is planned (§7).
5. On success: success haptic, a green check, and on the TV "Ajit's phone can now control this TV".

### 5.3 "Who can control this TV right now?"

A single page, reached from the status row on the phone and from TV Settings → Privacy. It answers, in order:
1. **Phones paired** (name, last used, Home / Internet, "Active now" badge). Each has a red *Remove*.
2. **Internet control:** On/Off and since when, with who turned it on.
3. **Computers:** whether USB/Network debugging (ADB) is on. Red if on, with a one-tap *Turn Off*.
4. **Bluetooth remotes and controllers** paired with the TV.

Every removal takes effect immediately on the TV: its key is deleted and open sessions drop.

### 5.4 Internet control: off by default, explicit to turn on

**Turning it on** takes three deliberate steps:
1. A phone toggle, with biometric confirmation.
2. Confirmation **on the TV** with the remote: "Allow control over the internet? Anyone with a paired phone can control this TV from anywhere."
3. Tailscale linking (QR on the TV).

**While it's on:**
- the indigo globe shows on the phone's status row
- a daily summary appears in the action log

**Turning it off** takes one tap from any paired phone or on the TV. No confirmation is needed: safe directions should be easy.

**Transport:** Tailscale (WireGuard) carries it, so nothing listens on the open internet. The app-level signature (§7) still applies on top.

### 5.5 Strong authentication

- **Biometrics** (Face ID / BiometricPrompt Class 3) gate:
  - unblocking or approving apps
  - turning protection off
  - internet control
  - removing devices
  - restore
- The parent password stays the cross-phone fallback.
- **Passkey-style device keys:** each phone's key lives in hardware and is unlocked by biometrics, so a stolen phone can't act without the owner's face or fingerprint.
- **Optional second factor for internet control:** a request from a new network must be approved on another paired phone ("Ajit's phone wants to control from a new location: Approve?").

### 5.6 Revoking a lost phone (flow)

From any other paired phone, **or on the TV itself** with the parent PIN (Settings → Privacy → Paired Phones), choose Remove.
1. The TV deletes that phone's key and logs it.
2. Every open session drops within one second.
3. Every other phone gets a notice.

No password change is needed, though it's offered.

### 5.7 Privacy Dashboard: "what leaves this TV"

A plain list with a status for each item:
- **tv-hero itself:** Nothing (no internet permission for the home screen; the guard only answers on the home network).
- **Tracking turned off:** Xiaomi analytics, Xiaomi statistics, Google feedback, bug reports, each with "Restore".
- **Ad-filtering DNS:** AdGuard Family, on.
- **Still sending:** Google Play Services and the apps you use. Stated honestly, never hidden.
- **Restore original state:** one button, with a summary of what it re-enables and a 10-second undo window.

---

## 6. Component library

| Component | Spec |
|---|---|
| **Status pill** | Dot 8 pt + label, Footnote, `surface2` capsule. Green = home Wi-Fi, indigo = internet, grey = offline. Tap opens *Who can control*. |
| **Inset-grouped section** | Header Footnote caps `secondary`, 16 pt inset. Cells radius 10, min 44 pt (48 dp). Hairline inset to text. Footer Footnote `secondary`. |
| **Row** | Leading icon tile (29 pt, radius 7, colour by function) · title Body · subtitle Subhead `secondary` · trailing value / switch / chevron. The whole row is the touch target. |
| **Switch** | iOS 51 × 31, green on, spring thumb. Never used for actions that need confirmation (those open an alert). |
| **Buttons** | Prominent filled (accent, white Headline, 50 pt, radius 12) once per screen max. Tinted (accent 14 %) secondary. Destructive is red text in a row, never a filled red button except in the final confirm. |
| **Alerts** | iOS style: centred title Headline, message Footnote, buttons separated by hairlines. The destructive button is red and is never the default. |
| **Sheets** | Grabber, Cancel / Title / Done; medium and large detents. |
| **Remote buttons** | 64 pt circles `surface2`, glyph 42 %, pressed = 55 % opacity + haptic. Hold-to-repeat where it makes sense. |
| **TV tile** | 16:9, radius 16, focus scale 1.10, shadow 24, glass highlight, label 24 sp on focus only. |
| **TV pill** | 56 dp glass circle, icon 32 dp; label appears on focus. |
| **TV overlay** | Full-screen `bg` 92 %, centred column, emoji/symbol 56 sp, title 32 sp, body 18 sp max 620 dp wide, white pill buttons (focused = white fill, black text). |
| **Ring** | 64 pt, 7 pt stroke, kid colour, track `surface2`; orange in the last 10 min. |
| **Empty states** | One sentence, no illustration, a next step if there is one. |

---

## 7. TV: App Library, Settings, Privacy and Profiles

### 7.1 App Library (Down from Continue Watching, or long-press Home)

- A full-screen grid of the apps the current profile may open: 6 columns of 16:9 tiles (176 × 99 dp), 24 dp gutters, title-safe margins.
- **Top strip:** "Recently Added" (max 6), then A–Z.
  - Apps waiting for approval sit at the end with a lock badge and "Waiting for a parent". OK on one shows the waiting screen, not the app.
- **Focus:** same engine as the dock. Up from the first row returns to the Home rows. Focus remembers its last tile for 30 minutes.
- **Long-press OK:** *Move to Home Row*, *Hide*, *App Info*.
  - In a kid profile only *Ask a Parent* appears. It logs a request that shows on the phone as "Aarav asked for Racing Game".
- **Empty profile:** "No apps on Aarav's list yet. A parent can add them in tv-hero on their phone."

### 7.2 TV Settings (gear pill)

The TV's own settings stay where they are. A kid profile is blocked from them, and a parent goes straight in. tv-hero adds a **Parent menu**: open *Who's Watching?* → *Parent Menu* → PIN.

A focusable list overlay, 720 dp wide, centred:

| Row | What it shows / does |
|---|---|
| **Who Can Control This TV** | Each paired phone: name, last used, "Home" / "Internet". OK → *Remove* (confirm, focus on Cancel). |
| **Internet Control** | Off / On since … OK toggles. Turning **on** happens here (physical presence); turning off works from anywhere. |
| **Debugging (ADB)** | Off / On. OK turns it off at once; turning it on is only possible in the TV's own Developer options. |
| **Privacy** | Opens §7.3. |
| **Profiles** | Who's watching, switch, *Guest Mode*. |

### 7.3 TV Privacy Dashboard

A read-only list (the phone is the editor).
- **Header:** "This TV sends: Google Play Services, YouTube, Hotstar, Mi TV+ (apps you use). tv-hero sends nothing."
- **Sections:**
  - *Tracking switched off* (n)
  - *Ads switched off* (n)
  - *Filtering DNS* (provider, on/off)
  - *Last changed* (date and phone)
- Each item shows a green "Off" or an orange "On". There's no destructive action on the TV: restore runs from the phone, where there's a keyboard, biometrics and a summary.

### 7.4 Profile switcher (built)

- **Who's Watching?** shows when the TV wakes (if set to Ask): one 112 dp circle per profile, then *Guest* (grey, dotted outline), then *Parent* (lock glyph).
- **Focus:** scale 1.12 + 4 dp white ring.
- **Kids** switch with no PIN; **Parent** needs the PIN; **Guest** switches at once.
- The greeting and dock change in place (300 ms cross-fade). The chosen profile shows as the avatar pill in the top bar.

## 8. Mobile Now Playing (sheet from the Home card or the mini bar)

```
┌───────────────────────────────────────────┐
│                 ───                       │  grabber
│ ┌───────────────────────────────────────┐ │
│ │      [app art, 16:9, radius 16]       │ │  art 361 × 203
│ └───────────────────────────────────────┘ │
│ Peppa Pig: The Big Day Out                │  Title 1
│ YouTube Kids · Aarav · since 7:12 PM      │  Subhead secondary
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬░░░░  25 min left today │  Aarav's day (not the video's progress)
│                                           │
│      ⏮       ( ⏯ 72 pt )       ⏭          │  transport (Bluetooth media keys)
│   🔈 ─────────●──────── 🔊                 │  volume slider (TV volume, via guard)
│                                           │
│ [ Pause TV ]  [ +15 min ]  [ Sleep ]      │  tinted buttons, 44 pt
│                                           │
│ TODAY ON THIS TV                          │  inset list of today's sessions
└───────────────────────────────────────────┘
```

- "What's playing" is the TV app's media title. When the app doesn't share one, the sheet says "YouTube Kids" and nothing invented.
- The volume slider sets the TV's media volume, capped by Night Volume, which the slider marks with a notch.

## 9. Mobile Privacy & Debloat dashboard (tab)

### 9.1 Layout

1. **Hero summary card:** shield glyph (green), "Your TV is private", then "27 things switched off · ad filter on · tv-hero sends nothing". If anything drifted back (e.g. after a firmware update), the card turns orange: "3 items came back after an update · Fix".
2. **Who can control this TV:** a compact row → §10. Shows "2 phones · Internet off · Debugging off".
3. **Recommendations for this model** (MiTV-AXSO0, PI.6036):
   - grouped: *Tracking*, *Ads & sponsored*, *Background services*, *Apps you didn't pick*
   - each group is a row with a count and state ("4 of 4 off"); a group page lists packages with plain-language names, what they do, and an iOS switch
   - safety-checked items (input-related) show "Kept on: needed for HDMI" and no switch
4. **Network:** filtering DNS (AdGuard Family / Ads only / Off).
5. **What leaves this TV:** a plain list (see §7.3).
6. **Restore Original State** (red text row) → confirmation sheet listing every change it reverses: n apps re-enabled, DNS off, stock home back. A 10-second "Undo" banner follows. It needs biometrics.

### 9.2 How changes reach the TV

Enabling or disabling a system app needs Android's debugging bridge (ADB). tv-hero doesn't leave it open:
1. You tap a switch.
2. The phone asks for biometrics.
3. The guard turns debugging on for that job only.
4. The phone connects with its own ADB key. The TV asks "Allow USB debugging?" the first time only: physical confirmation.
5. The phone applies the change.
6. The guard turns debugging off again.

The UI shows this as one step: "Applying… Done".

## 10. Devices & Sessions ("Who can control this TV right now?")

- **Sections, in order:**
  1. **Active Now:** sessions in the last 2 minutes, with a pulsing dot. "Ajit's phone · Internet · 9:41 PM".
  2. **Paired Phones:** name, model, paired date, last used, last connection (Home / Internet). Swipe left or tap → *Remove* (red). Removing *this* phone asks you to type the TV name.
  3. **Internet Control:** state, "turned on by Ajit's phone on 12 Nov", *Turn Off* (no confirmation).
  4. **Debugging (ADB):** state + *Turn Off*.
  5. **Bluetooth on the TV:** remotes and controllers (read-only list).
- **Action log** (pushed page): every change with *which phone*, *how* (Home / Internet) and *when*. Filters by phone and by kind (security, apps, family). Security events have a red glyph.
- **Lost phone:** the top-right "Lost a Phone?" button opens a one-screen flow: pick the phone → Remove → done. It also offers to change the parent password.

## 11. Mobile Settings, Scenes and multi-TV

### 11.1 Settings

- **TV** (header with name, model, address, connection pill):
  - *Rename*
  - *Home Screen* (app row, apps per row, greeting, hidden apps)
  - *Night Volume*
  - *Ambient Mode* (clock / sky / off; delay)
  - *Picture & Sound* (opens the TV's own picture menu on the TV; tv-hero can't change Xiaomi's picture engine directly)
- **Security:**
  - *Parent Password*
  - *Parent PIN on TV*
  - *Unlock with Fingerprint*: biometrics instead of typing the password; the password is kept encrypted by a hardware key that needs your fingerprint
  - *Remote Access*: internet control status, Tailscale set-up
- **TVs:** every paired TV, the current one ticked; *Add a TV*.
- **This Phone:** name, *Forget This TV*.
- **About:** version, licences, "Made for this TV, owned by you".

### 11.2 Scenes

- **A scene** is a saved set of actions, run with one tap from Home. Possible actions:
  - who's watching
  - open an app
  - set volume
  - sleep timer
  - pause or resume
- **Built-in starters:**
  - *Movie Night*: Parent, Hotstar, volume 60 %, no sleep timer
  - *Kids' Hour*: a chosen child, YouTube Kids, sleep in 60 minutes
  - *Bedtime*: pause the TV, sleep in 15 minutes
- **Editor:** a sheet with an ordered action list, an "Add Action" menu, and a name and emoji.
- **Running:** a progress capsule ("Movie Night · 3 of 4"), then a success haptic. A failed step is named and the rest still run.

### 11.3 Multi-TV

- The Home large title is a menu ("Living Room ⌄"): paired TVs with live status dots, then *Add a TV*.
- Every screen acts on the selected TV. Scenes and settings are per TV.

## 12. Family, Guest mode, Parental controls

- **Family tab:** segmented **Kids | Apps**.
  - *Kids:* profile rows (avatar, time left, bedtime), Add Child (max 10), "When the TV turns on".
  - *Apps:* approvals first, then every app with an Allowed switch.
- **Child page** (built): watching state, daily limit (list), +15/30/60, bedtime switch with time wheels, that child's apps (switches), recent activity, delete.
- **Requests from the TV:** "Aarav asked for Racing Game" appears in Home → Needs You with *Allow for Aarav* / *Not Now*.
- **Guest mode:**
  - A temporary profile for visitors: the household's allowed apps (never Settings or the Play Store), no watch history kept, no PIN needed.
  - It ends when the TV goes to standby, and the TV wakes as usual (Ask / default profile).
  - Shown as "Guest" with a dotted avatar. The phone shows "Guest is watching".
  - **Guest Time** (2.2): an optional daily limit, one allowance shared by every guest that day. Time's up for Guest offers Ask for Time and the Parent PIN (no questions).
- **Earn Time** (2.2): on the Time's up screen a child can answer questions to earn minutes. Four answers in a 2 × 2 grid for the D-pad; a cheer and the minutes earned after a right answer, the right answer shown after a wrong one ("No minutes lost"). Per profile: on/off, minutes per answer (2/5/10), most per day (10/15/20/30), level, topics.

## 13. Onboarding and pairing (phone)

1. **Welcome:** a full-bleed dark page. "Your TV, your rules." Three lines: *No ads. Kids' time. Control from your phone.* Then **Get Started**.
2. **Find your TV:** a list of TVs found on this Wi-Fi with a live search indicator, or enter an address. Explains: "Your phone and TV must be on the same Wi-Fi to pair."
3. **Confirm on TV:** the phone shows "Look at your TV". The TV shows *Allow Ajit's phone to control this TV?*, a 6-digit code, and **Allow** / Don't Allow (focus on Don't Allow).
4. **Enter the code** (6 boxes, numeric keypad, auto-advance) + parent password (create, with a strength hint, at least 4 characters, plus repeat), or enter it if one exists.
5. **Fingerprint** (optional): "Use your fingerprint for parent actions?" → BiometricPrompt.
6. **Done:** success haptic, a check, then "Internet control is off. Turn it on later in Privacy." Lands on Home.

## 14. Critical flows

| Flow | Steps | Safeguards |
|---|---|---|
| **First-time pairing** | Find TV → Allow on TV → code + password → fingerprint (optional) → Home | Physical presence (Allow + code); 5 wrong codes end it; password lock-out after 5 tries |
| **Enable internet control** | Privacy → Who can control → Internet Control → fingerprint → TV asks "Allow control over the internet?" (OK on TV) → Tailscale QR on TV → phone signs in → "Ready" | Off by default; two-device confirmation; TV refuses internet addresses until allowed; indigo status everywhere |
| **Revoke a lost phone** | Any phone: Privacy → Lost a Phone? → pick → Remove; or TV: Parent Menu → Who Can Control → phone → Remove | Instant key deletion; open sessions dropped; other phones notified; offer to change password |
| **Switch profile** | Phone: Home → Who's Watching → child (instant) or Parent (fingerprint/password); TV: Who's Watching? → child, or Parent + PIN | Kids can't reach Parent without the PIN; switch logged |
| **Run a debloat** | Privacy → Recommendations → group → switches → fingerprint → "Applying…" (ADB for this job only) → Done; or Restore Original State | Every change reversible and logged; input-critical packages locked on; ADB closed afterwards |

## 15. Making it feel premium and trustworthy

- **Speed is the luxury.**
  - Remote presses go out within 50 ms over Bluetooth.
  - Status refreshes every 5 s while the app is open, with optimistic UI: a switch flips immediately and reverts with an explanation if the TV refuses.
- **Honest copy.**
  - Never "Secured!" or other theatre. "Encrypted between your phone and TV" only where that's true.
  - Name what still leaves the TV.
- **No dead ends.** Every error says what to do next ("TV not reachable. Is it on? Away from home? Turn on Tailscale.").
- **Quiet defaults:**
  - no notifications except security events and kids' requests
  - no badges for things that don't need you
  - no sounds
- **Consistency across screens:** the same words on TV and phone ("Paused by a parent", "Waiting for approval"), the same colours, the same avatar per child.
- **Ownership touches:**
  - the family name in the greeting
  - "Made for this TV" in About
  - a one-tap way back to factory for everything tv-hero changed

## 16. Build status (tv-hero 2.0, 29 Oct 2026)

"Verified" means checked on the real TV; "Built" means compiled and unit-tested but not yet tried on a device.

| Area | Status |
|---|---|
| TV Home: dock of up to 6 apps, greeting by profile, App Library, ambient clock, no ads | Verified |
| Profiles (up to 10), Who's Watching, parent PIN, limits, bedtime, pause, sleep timer, night volume | Verified (1.1) |
| Family start-up profile, Apps for Everyone, profile button on Home, Parent Menu on the TV (profiles, apps, limits, bedtime, start-up profile, PIN, who can control, internet control, debugging, guest), password fallback without a PIN, "Switch to Parent" on blocked Settings | **Verified** (2.1, 29 Oct 2026) |
| Browser (TV Bro 2.1.6), WhatsApp, YouTube · Brave tile (Brave 1.80.124, one reused tab) | **Verified** installed and opening; WhatsApp not yet linked (the owner accepts its terms) |
| Kids' *Ask a Parent* on the blocked screen → **Needs You** on the phone | Built |
| 2.2: Time's up / Bedtime / Paused lock the TV (media paused, audio focus held, apps sent away), Ask for Time → Needs You, PIN to switch to an unlimited profile | Built |
| 2.2: **Earn Time** quiz on Time's up (Maths generated by level; English, GK, Hindi, Puzzles from built-in banks; 5 min per right answer, 15 min a day, 3 wrong in a row → 10-minute break; settings per profile on TV and phone) | Built |
| 2.2: **Guest Time**: a daily allowance for Guest, set on the TV (Parent Menu) or the phone (Family → Guest), with extra time and requests | Built |
| Pairing: code comparison + Allow / Don't Allow on the TV (focus on Don't Allow) + password over TLS | **Verified** with the Python client; phone UI built |
| Encrypted channel: mutual TLS, Keystore keys on both sides, pinned certificates | **Verified** (TLS 1.2, ECDHE-ECDSA-AES128-GCM) |
| Internet control off by default; on needs password + home network + Allow on the TV | **Verified** (refusal path); Tailscale transport not yet installed on the TV |
| Who can control (last used, home/internet, active now), revoke, action log with the phone's name | Verified with the Python client (one revocation bug found and fixed; re-check pending); phone UI built |
| Phone: power and volume over the encrypted channel | TV side verified; phone UI built |
| Phone app lock (fingerprint / screen lock) | Built |
| Phone 2.1: fixed rows running their tap action while drawing (Settings opened a sub-page by itself, taps did nothing); Profiles + Apps for Everyone + Start-up Profile; safer "removed" detection; every screen rendered in `docs/screenshots/phone/` | Built and screenshot-reviewed; on-phone check by Ajit pending |
| Phone: Home tab with status row, Now Playing sheet, trackpad remote, full-screen controller, Privacy & Debloat dashboard, Scenes, multi-TV switcher | **Not built yet**. The current tabs are TV, Family, Apps, Remote, Settings. |
| Continue Watching row, focus parallax and light sweep, Picture & Sound from the phone, keyboard auto-open | Not built yet |
