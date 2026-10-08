# 🐾 Pawcount — Countdowns with a Dog Who Can't Wait Either

> A beautifully animated iOS & Android countdown app for couples, where a dog companion gets more and more excited as your trips, birthdays, and big days get closer.

**Working name:** Pawcount
**Alternatives:** Wag Days · Till Then · Fetch · Sit. Stay. Soon.
**Platforms:** iOS 17+ · Android 10+
**Tagline:** *Every day closer is another tail wag.*

---

## 1. Why this exists

We have a year of plans: vacations, international travel, birthdays, anniversaries. Generic countdown apps are cold. Ours has a dog who sits by the door, wags harder each day, packs a suitcase the week before, and loses its mind on the big day.

**Design goal:** an app that feels alive, warm, and made for two people. Not a utility with a mascot added on.

---

## 2. Award-worthy pillars

Apple Design Awards and Google Play's Best Of reward a short list of things. We design for each from day one.

| Pillar | What it means for Pawcount |
|---|---|
| **Delight & Fun** | Every countdown has a dog with a personality whose mood changes as the date approaches |
| **Interaction** | Tap, pet, and shake the dog; it reacts. Haptics on every meaningful moment |
| **Visuals & Graphics** | Hand-drawn style, state-machine animations, 60/120fps, coherent palette |
| **Inclusivity** | Full VoiceOver/TalkBack, Dynamic Type, reduce-motion alternatives, colorblind-safe |
| **Innovation** | Shared couple countdowns, live time-zone-aware arrival countdowns, Live Activities |
| **Platform craft** | Home/Lock Screen widgets, Live Activities, Dynamic Island, Material You, Wear OS/watchOS complications |

---

## 3. The dogs (core of the experience)

### Breeds (launch set)
Each countdown picks a dog. Each breed has its own personality in animation.

| Breed | Personality | Best for |
|---|---|---|
| **Golden Retriever** "Sunny" | Pure joy, zoomies | Vacations, beach trips |
| **Corgi** "Biscuit" | Dramatic, bossy | Birthdays |
| **Shiba Inu** "Miso" | Cool, secretly thrilled | International travel |
| **Dachshund** "Noodle" | Snuggly, patient | Anniversaries, date nights |
| **Husky** "Blizzard" | Loud, talks back | Ski trips, adventures |
| **Mutt** "Lucky" | Customizable colors/ears | Anything — or "draw your own dog" |

> 💡 Future: upload a photo of your real dog → stylized custom companion.

### Excitement curve (state machine)
The dog's behavior is driven by **% of time remaining** since the countdown was created.

| Stage | Time left | Dog behavior |
|---|---|---|
| 😴 **Napping** | > 75% | Curled up, slow breathing, occasional ear twitch |
| 👀 **Curious** | 75–40% | Sits up, head tilts when you open the app |
| 🐕 **Waiting** | 40–15% | Sits by the door, tail wagging slowly |
| 🎒 **Packing** | 15–3% (or last 7 days) | Event-specific: drags suitcase, wears party hat, holds passport |
| ⚡ **Zoomies** | Last 24 h | Runs laps, spinning, tail a blur |
| 🎉 **IT'S TODAY** | Day of | Full celebration + confetti + optional sound |
| 💛 **Memory** | After | Dog lies on a souvenir; countdown becomes a memory card |

### Interactions
- **Tap** → dog barks (haptic tick) and shows a cute line ("12 more sleeps!!")
- **Long-press / pet** → dog leans in, hearts float, soft haptic purr
- **Shake phone** → dog sneezes, scene items bounce
- **Pull to refresh** → dog fetches a ball back to the top
- **Milestones** (100, 50, 30, 10, 7, 1 days) → one-time celebration animation

---

## 4. Event types

Each type changes the scene, props, copy, and notifications.

| Type | Scene & props | Special features |
|---|---|---|
| ✈️ **International trip** | Airport window, plane, passport, stamps | Destination time zone; country flag; "Arrives in local time" mode; packing checklist; passport-expiry reminder |
| 🏝️ **Vacation** | Beach / mountains / city skyline | Weather preview (near date); packing list |
| 🎂 **Birthday** | Party room, cake, balloons | **Auto-repeats yearly**; age turning; gift-ideas note |
| 💍 **Anniversary** | Picnic blanket, flowers | Repeats yearly; "together for X days" counter |
| 🎄 **Holiday** | Seasonal scene | Prebuilt templates |
| ⭐ **Custom** | Choose a background + emoji | Free-form |

---

## 5. Features

### MVP (v1.0)
- [ ] Create / edit / delete countdowns (title, date, time, type, dog, color)
- [ ] Home grid of countdown cards with mini-animated dogs
- [ ] Detail screen: full-scene animated dog + live ticking timer (d / h / m / s)
- [ ] Display modes: days only · full timer · "sleeps" · weeks + days
- [ ] Recurring events (yearly birthdays/anniversaries)
- [ ] Time-zone aware events (count to the moment in the destination's time)
- [ ] Local notifications: milestones + morning-of + custom
- [ ] Home Screen widgets (small / medium / large) with dog state
- [ ] Lock Screen widgets (iOS) / At-a-glance (Android)
- [ ] Dark mode, Dynamic Type, reduce-motion support
- [ ] Haptics throughout
- [ ] Offline-first local storage

### v1.1 — For two 💑
- [ ] **Shared countdowns**: invite your partner by link; both phones stay in sync
- [ ] See who "petted" the dog today (little paw-print from each person)
- [ ] Shared notes / checklist per event (packing list, gift ideas, itinerary)
- [ ] Reactions: send a bark to your partner's phone

### v1.2 — Platform delight
- [ ] iOS **Live Activities** + **Dynamic Island** for the final 24 hours
- [ ] Android ongoing notification with live countdown
- [ ] Apple Watch / Wear OS complications
- [ ] Interactive widgets (pet the dog right from the Home Screen)
- [ ] Siri / Google Assistant: "How many days until Japan?"

### v2.0 — Memories
- [ ] After the event: attach photos → countdown becomes a **memory card**
- [ ] Year-in-review: "Biscuit counted down 9 adventures with you in 2027"
- [ ] Shareable animated story cards (Instagram/TikTok vertical video export)
- [ ] Custom dog from a photo of your own pup

---

## 6. Screens

1. **Onboarding (3 screens)**: meet the dogs → pick your first companion → create first countdown. Notification permission is asked with the dog holding a little bell, not a cold system prompt first.
2. **Home**: soonest countdown as a hero card (large animated dog); others in a grid below. Sort by soonest / type.
3. **Countdown detail**: full-screen scene, giant numerals, dog in the foreground, progress "leash" bar along the bottom.
4. **Create / edit**: step flow — What? → When? (date, time, time zone) → Which dog? → Style → Done (dog runs in to celebrate).
5. **Shared/partner**: invite flow, sync status, partner's paw prints.
6. **Memories**: past countdowns with photos.
7. **Settings**: display mode, notifications, sounds, haptics, reduce motion, iCloud/Google backup, app icon picker (different dog icons!).

---

## 7. Design system

### Feel
Warm, hand-drawn, soft rounded shapes, cozy storybook. Think picture book meets Nintendo polish.

### Color tokens
| Token | Hex | Use |
|---|---|---|
| `bone` | `#FFF8EE` | Light background |
| `kibble` | `#E8A15C` | Primary / buttons |
| `tennisBall` | `#D4E157` | Highlights, celebrations |
| `collarRed` | `#E5574F` | Alerts, hearts |
| `pawPink` | `#F6B7B0` | Soft accents |
| `nightWalk` | `#1F2433` | Dark background |
| `fur` | `#8A5A3B` | Text on light / outlines |

Each event type gets an accent palette (travel = sky blues, birthday = confetti brights, anniversary = blush/gold).

### Typography
- Numerals: rounded, chunky display font (e.g., **Fredoka** or **Nunito Black**), tabular figures so digits don't jump
- Body: **Nunito** / system font for accessibility
- All text scales with Dynamic Type

### Motion principles
1. **The dog is always alive**: idle loops (breathing, blinking) — never a static frame.
2. **Ease like a dog moves**: bouncy springs, slight overshoot.
3. **Every animation means something**: excitement = time closeness.
4. **Respect reduce-motion**: swap loops for gentle cross-fades; keep the dog's expression.
5. **60fps minimum, 120fps on ProMotion**.

### Sound & haptics
- Optional soft barks, jingle of a collar tag, celebration chime
- Haptic patterns: light tick (tap), soft rumble (pet), success burst (milestone)

---

## 8. Tech stack

### Recommended: React Native + Expo (one codebase, both stores)
| Layer | Choice | Why |
|---|---|---|
| Framework | **Expo (SDK latest) + React Native**, TypeScript | One codebase for iOS + Android, fast iteration, EAS builds |
| Animation (dogs) | **Rive** (`rive-react-native`) | State machines are perfect for excitement stages + interactions; tiny files; 60fps |
| UI motion | **React Native Reanimated** + **Gesture Handler** | Native-thread animations, springs |
| Effects | **Skia** (`@shopify/react-native-skia`) | Confetti, particles, hearts |
| Navigation | **Expo Router** | File-based routing |
| State | **Zustand** | Simple, small |
| Local DB | **expo-sqlite** + Drizzle ORM (or MMKV for settings) | Offline-first |
| Dates | **date-fns** + **date-fns-tz** | Time-zone-safe countdown math |
| Notifications | **expo-notifications** | Scheduled local notifications |
| Haptics | **expo-haptics** | |
| Widgets (iOS) | **SwiftUI WidgetKit** + ActivityKit via Expo config plugin (`expo-apple-targets`) | Native widgets & Live Activities required |
| Widgets (Android) | **Jetpack Glance** via config plugin, or `react-native-android-widget` | |
| Sync (v1.1) | **Supabase** (auth + Postgres + realtime) | Shared couple countdowns |
| Analytics/crash | Sentry + privacy-respecting analytics (PostHog) | |

> **Alternative:** Native SwiftUI + Jetpack Compose if we want max platform-award polish. More work (two codebases) but the best possible feel. Recommendation: start with Expo; widgets/Live Activities are native anyway.

### Animation pipeline
1. Sketch dogs → vector in Figma/Illustrator
2. Rig & animate in **Rive** editor: one state machine per breed with inputs:
   - `excitement` (number 0–100)
   - `eventType` (number enum)
   - `tap`, `pet`, `celebrate` (triggers)
3. Export `.riv` files (~50–200 KB each) → bundle in app
4. Widget versions: pre-rendered PNG frames per stage (widgets can't run Rive)

---

## 9. Data model

```ts
type EventType = 'intl_trip' | 'vacation' | 'birthday' | 'anniversary' | 'holiday' | 'custom';
type DogBreed  = 'golden' | 'corgi' | 'shiba' | 'dachshund' | 'husky' | 'mutt';
type DisplayMode = 'full' | 'days' | 'sleeps' | 'weeks';

interface Countdown {
  id: string;               // uuid
  title: string;            // "Japan 🇯🇵"
  type: EventType;
  targetAt: string;         // ISO date-time, wall-clock
  timeZone: string;         // IANA, e.g. "Asia/Tokyo"
  allDay: boolean;
  recurrence: 'none' | 'yearly';
  createdAt: string;        // used for excitement % curve
  dog: { breed: DogBreed; name: string; colors?: Record<string, string> };
  accent: string;           // color token
  displayMode: DisplayMode;
  notes?: string;
  checklist?: { id: string; text: string; done: boolean }[];
  destination?: { country: string; city?: string; flag: string };  // travel
  person?: { name: string; birthYear?: number };                    // birthday
  notifications: { offsetMinutes: number; enabled: boolean }[];
  sharedGroupId?: string;   // v1.1 couple sync
  memoryPhotos?: string[];  // v2
  archived: boolean;
}
```

### Countdown math rules
- Store target as **wall-clock time + IANA zone**, convert to an absolute instant at runtime (handles DST correctly).
- Excitement % = `1 - (target - now) / (target - createdAt)`, clamped 0–1. Override to "Packing" when ≤ 7 days left, regardless of %.
- Yearly recurrence: after the date passes, show celebration for the day, then roll to next year and reset `createdAt`.
- "Sleeps" = number of local midnights between now and target.

---

## 10. Notifications plan
| Trigger | Example copy |
|---|---|
| 100 / 50 / 30 days | "🐕 Sunny just counted: **30 days** until Bali!" |
| 7 days | "🎒 Miso started packing. One week until Japan!" |
| 1 day | "⚡ Zoomies activated. Tomorrow is Mia's birthday!" |
| Morning of | "🎉 IT'S TODAY! Biscuit is losing it." |
| Travel: passport check (90 days before) | "🛂 Quick check — passports valid 6+ months past the trip?" |

All schedulable/editable per countdown; no notification spam (max 1/day).

---

## 11. Accessibility checklist
- [ ] Every dog state has a spoken description ("Sunny is sitting by the door, wagging. 23 days left.")
- [ ] Numerals readable by VoiceOver as full phrase, not digit-by-digit
- [ ] Dynamic Type up to XXXL without truncation
- [ ] Reduce Motion → static poses + cross-fades
- [ ] Contrast ratio ≥ 4.5:1 for all text
- [ ] Color never the only signal (icons + labels for event types)
- [ ] Sounds off by default; captions for any audio

---

## 12. Privacy
- Everything local by default; no account required for solo use
- Account only for sharing (Sign in with Apple / Google)
- No ads, no tracking SDKs, no selling data
- App Store privacy label: "Data Not Collected" for solo users

---

## 13. Project structure

```
pawcount/
├── app/                      # Expo Router screens
│   ├── (tabs)/index.tsx      # Home
│   ├── (tabs)/memories.tsx
│   ├── countdown/[id].tsx    # Detail
│   ├── create/               # Create flow steps
│   └── settings.tsx
├── components/
│   ├── DogScene.tsx          # Rive wrapper + inputs
│   ├── CountdownCard.tsx
│   ├── TickingNumerals.tsx   # Reanimated rolling digits
│   └── Confetti.tsx          # Skia
├── lib/
│   ├── countdown.ts          # time math, excitement curve
│   ├── notifications.ts
│   ├── db/                   # sqlite + drizzle schema
│   └── store.ts              # zustand
├── assets/
│   ├── dogs/*.riv
│   ├── widget-frames/
│   └── sounds/
├── targets/
│   ├── ios-widget/           # SwiftUI WidgetKit + Live Activity
│   └── android-widget/       # Glance
└── app.config.ts
```

---

## 14. Roadmap

| Phase | Duration | Deliverables |
|---|---|---|
| **0. Design** | 2 wks | Moodboard, dog sketches, Figma screens, Rive prototype of one dog (Golden) |
| **1. Core app** | 3 wks | Create/list/detail, countdown math, local DB, one animated breed |
| **2. Dogs & delight** | 3 wks | All 6 breeds, excitement stages, interactions, haptics, confetti, milestones |
| **3. Notifications & widgets** | 2 wks | Local notifs, iOS + Android widgets |
| **4. Polish & a11y** | 2 wks | Accessibility pass, reduce-motion, dark mode, app icons, sound |
| **5. Beta** | 2 wks | TestFlight + Play internal testing with friends |
| **6. Launch v1.0** | — | App Store + Google Play |
| **7. v1.1 Couples sync** | 3 wks | Supabase, invite links, shared checklists |
| **8. v1.2 Live Activities** | 2 wks | Dynamic Island, watch complications |

**MVP target: ~12 weeks part-time.**

---

## 15. Launch checklist
- [ ] Apple Developer account ($99/yr) · Google Play Console ($25 one-time)
- [ ] App icon set (+ alternate dog icons)
- [ ] Store screenshots featuring animated dogs; 30-sec preview video
- [ ] Privacy policy page
- [ ] App Store keywords: countdown, trip countdown, birthday countdown, vacation, dog, widget
- [ ] Submit to Apple's "Featuring nominations" form + Google Play's featuring form
- [ ] Press kit: GIFs of each dog's zoomies

---

## 16. Success metrics
- Day-7 retention > 40% (people come back to check the dog)
- Average 3+ countdowns per user
- Widget adoption > 50%
- Store rating ≥ 4.8
- Most importantly: **we use it every day until our trip** ✈️🐾

---

## 17. Our first countdowns (fill these in!)
| Event | Date | Dog | Notes |
|---|---|---|---|
| ✈️ International trip — ______ | ____ | Miso (Shiba) | |
| 🏝️ Vacation — ______ | ____ | Sunny (Golden) | |
| 🎂 ______'s birthday | ____ | Biscuit (Corgi) | |
| 🎂 ______'s birthday | ____ | Biscuit (Corgi) | |
| 💍 Our anniversary | ____ | Noodle (Dachshund) | |
