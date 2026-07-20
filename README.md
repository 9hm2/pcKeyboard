# pcKeyboard

A full PC-style virtual keyboard for Android, built for **foldable devices**
(Samsung Galaxy Fold, Pixel Fold, …) but equally usable on regular phones.
Same key set as a desktop keyboard — function row, modifier cluster, arrow
keys, AltGr — with proper IME integration so apps see real `Ctrl+C`,
`Alt+Tab`, `Shift+Home`, etc.

```
Esc F1 F2 F3 F4 F5 F6 F7 F8 F9 F10 F11 Home End
`   1   2   3   4   5   6   7   8   9   0   -   =   ⌫
Tab q   w   e   r   t   y   u   i   o   p   [   ]   \
Caps a   s   d   f   g   h   j   k   l   ;   '   ⏎
⇧   z   x   c   v   b   n   m   ,   .   /   ▲   ⇧
Ctrl 🌐 Alt        space      123  ◀ ▼ ▶
```

## Highlights

- **Real PC layout** — F-row, Esc, Tab, Caps Lock, Ctrl + Alt + Win as actual
  modifiers (not toggles to a symbols page), arrow cluster with both ▲ above ▼
  and ◀ / ▶ flanking. Modifier presses become `KeyEvent` meta flags so
  shortcuts work everywhere.
- **Foldable-first** — at ≥ 600 dp the full PC layout shows; on narrow phones
  the function row is dropped automatically, but can be force-enabled from the
  globe menu so power users keep their F-keys.
- **Two-phase touchpad** in Space — slide into the centre indicator to arm,
  release to enter free touchpad mode with relative cursor deltas, repeat
  drags any number of times, ✕ to exit.
- **Full emoji picker** (~1500 emojis across 9 categories + dynamic Recents)
  with **inline search using the real keyboard** for typing the query.
- **Clipboard manager** with cards for the last 20 entries — tap commits,
  long-press edits, ✕ deletes one, "Clear all" wipes the rest with a two-tap
  confirm.
- **Per-language AltGr layer** — held Alt commits the locale's xkb glyph
  (e.g. HU `e` → `€`, `w` → `|`; DE `q` → `@`, `<` → `|`; ES `2` → `@`).
- **In-app self-update** to the app's own cache directory with a live
  progress dialog and a FileProvider hand-off to the system installer.
- **Custom themes** with an ARGB colour picker (sliders + hex display +
  Default reset) and a live preview against the actual keyboard view.
- **Offline autocorrect & suggestions** — per-language frequency
  dictionaries (HU 400k / DE 250k / EN 250k / ES 250k word forms,
  inflections included, ~5.4 MB total) power a passive suggestion strip
  with accent restoration (`kerdojel` → `kérdőjel`), typo correction
  and word completion; an opt-in Auto mode fixes obvious typos at word
  boundaries (Space, punctuation, Enter, tap-away) — retyping a
  corrected word keeps it and teaches it permanently. A per-language
  **learning dictionary** picks up
  the user's own words: anything typed twice is suggested from then on
  and shielded from auto-correction. Off / Suggest / Auto in Settings.

## Layout

### Languages

English (US — ANSI QWERTY), Hungarian (ISO QWERTZ), German (ISO QWERTZ), Spanish
(ISO QWERTY). Each comes with its xkb AltGr table baked in as `Key.altLabel`
and the same set is also at the head of the long-press popup, so every
diacritic / AltGr glyph is reachable two ways — held-Alt for one-shot, long-
press for browsing.

The active layout is cycled from the globe long-press menu, restricted to the
languages the user actually enabled in Settings → Languages. New locales are
one `*.Layout.kt` file plus a `LayoutPack` entry in `LayoutRegistry` and a
`<subtype>` in `res/xml/method.xml`.

### Rows

- **Function row** (always shown on ≥ 600 dp, optional on narrower screens):
  `Esc F1–F11 Home End`. Toggle from the globe menu.
- **Number row**: locale-specific (HU has `0 1-9 ö ü ó`, DE has `^ 1-0 ß '`,
  ES has `º 1-0 ' ¡`, EN has `` ` `` `1-0 - =`).
- **Top letter row**: locale-specific, ending with the bracket/backslash set
  most users need.
- **Home row**: starts with **Caps Lock** (where Ctrl traditionally lives on
  consumer PCs), ends with Enter.
- **Bottom letter row**: ⇧ + letters + ▲ + ⇧, so the up-arrow sits directly
  above ▼ in the row below for a real inverted-T arrow cluster.
- **Control row**: `Ctrl 🌐 Alt Space 123 ◀ ▼ ▶`. The slot to the right of
  Space is **configurable in Settings** — pick `123` (default) or `😀` for
  one-tap access to the emoji picker.

Every row totals weight 14, so a "weight 1" letter is exactly the same width
in any row.

### Two symbol pages

`123` flips into the first symbols page (`1-0`, punctuation, `=\<` toggles
into page 2 for `~ • ° § © ® ™ € £ ¥ ¢ ÷ × ± ¬ < > { } [ ] \ …`). `ABC` flips
back.

## Modifiers

Shift, Ctrl, Alt, Meta (Win), Fn and Caps Lock follow the classic
`OFF → ONCE → LOCKED` tap cycle:

- **Off** — default appearance.
- **Once** — thick accent border + a small accent dot in the corner. Applies
  to the next key press, then auto-releases.
- **Locked** — full accent background + an underline under the label. Stays
  on until tapped a third time.

State converts to `KeyEvent` meta flags via `ModifierState.toMetaState()`, so
apps see proper `Ctrl+C`, `Shift+Tab`, `Alt+F4`, etc. The state **resets on
input dismiss**, so a Shift you forgot to clear in one app never carries over
into the next.

## Long-press alternates

Long-pressing any key with alternates opens a popup directly above it. To
make sure the popup fits even when the pressed key is in the very top row,
KeyboardView is **intentionally rendered taller than the visible keyboard**
(90 dp of transparent space at the top), while the IME service reports a
smaller `contentTopInsets` so the app keeps fitting above the keys. That
reserved zone hosts the popup, and the app shows through it when nothing is
being shown.

- The **base character sits in the centre** of the popup, pre-selected — a
  straight release commits it; sliding left/right walks symmetrically through
  the alternates.
- The selected cell is highlighted with an **accent-coloured rounded pill**
  that's slightly larger than the surrounding cells, with rounded corners on
  all four sides.
- For symbol keys the **shift-label is folded into the alternates** — long-
  pressing `,` exposes `<`, `«`, `‹`, `„`.
- Cell width and text size **scale with screen width** so even keys with 8+
  accented variants don't spill off-screen.

## Two-phase Space-trackpad

Long-press Space → trackpad overlay appears.

**Phase 1 — Arming.** A circular indicator sits in the centre of the
keyboard area; the user must slide their finger from Space *into* the
indicator to arm it. Releasing without entering the indicator restores the
keyboard and emits no Space character — an accidental long-press costs
nothing.

**Phase 2 — Free touchpad.** On release after arming, the trackpad doesn't
close — it switches into a real touchpad surface:

- Each finger-down sets a new origin; subsequent moves emit **relative pixel
  deltas**. Slow drags move the cursor character-by-character (precision),
  fast drags fly across lines (range).
- A small accent dot follows the finger so the user can see where the
  trackpad is reading them from.
- An ✕ button at the top-right closes the trackpad. **It's hidden while the
  finger is on the surface**, so a stray edge-touch during a drag never
  accidentally dismisses the session.
- The user can do as many discrete drags as they want — no need to hold one
  long gesture.

Cursor speed is tunable from Settings → Trackpad sensitivity (0.3× – 3.0×).

## Cursor keys & Enter

Arrow keys, Home / End, Page Up / Page Down and Enter go through
`InputConnection.setSelection` / `performEditorAction` rather than
`KeyEvent.KEYCODE_DPAD_*`. That keeps the gesture inside the focused editor —
on foldables and in free-form / multi-window mode the system window manager
would otherwise steal a DPAD event to select the floating-window grab handle,
jump focus to a sibling Send button, etc.

- **← / →** — move caret one character; with **Ctrl** jump by word
  boundary; with **Shift** extend selection instead of moving.
- **↑ / ↓** — move caret one line, preserving column.
- **Home / End** — start / end of current line (extends selection under
  Shift).
- **Page Up / Page Down** — ±10 lines.
- **Enter** — runs the editor's declared `IME_ACTION_*` (Send / Done / Search
  / Next / …) when one is set; otherwise commits `\n`. Holding any modifier
  (`Shift+Enter`, `Ctrl+Enter`, …) always commits `\n` — the universal
  "newline in a chat field" shortcut.

## Emoji picker

Opens from the globe menu, or one-tap from the control row if the user picked
"😀 Emoji" for the right-of-Space slot.

- **9 categories**: 🕒 Recents (dynamic), 😀 Smileys, 👋 People, 🐶 Animals,
  🍎 Food, ⚽ Activities, 🚗 Travel, 💡 Objects, ❤️ Symbols, 🏁 Flags
  (every UN country flag plus the rainbow / transgender / pirate / England /
  Scotland / Wales variants). ~1500 emojis total.
- **Recents tab** is fed by an `EmojiUsageTracker` that ranks by use-count
  then most-recent timestamp, with a starter pack so the tab isn't empty on
  first open. Search-picked emojis count toward Recents too.
- **Inline search**: the magnifier pinned at the right of the tab strip flips
  the picker into search mode. A query bar sits at the top, search results
  fill the middle, and **the actual keyboard stays mounted underneath** for
  typing the query — no separate Activity, no second window, no IME juggle.
- Search is prefix-token against `EmojiKeywords` (1500+ entries). Country
  names work for flags (`germany` → 🇩🇪, `magyar` → 🇭🇺, `japan` → 🇯🇵, `usa`
  → 🇺🇸 …); professions for ZWJ profession emojis (`doctor`, `chef`,
  `astronaut`); gestures, weather, currency etc. all by everyday name.
- Bottom row: `ABC` returns to the keyboard, `⌫` deletes a character. Both
  are rendered as rounded accent-coloured pills with a visible gap between
  them so each touch target reads as its own button.
- Category tabs are separated by faint divider lines and a divider runs
  between the last tab and the pinned 🔍.

## Clipboard manager

Opens from the globe menu. A vertical list of up to 20 cards, newest first,
each showing up to 4 lines of the captured text.

- The IME service registers a `ClipboardManager.OnPrimaryClipChangedListener`
  so any copy from any app lands here automatically.
- **Tap** a card to commit the text into the focused editor.
- **Long-press** opens a full-screen `ClipboardEditorActivity` where the
  text can be tweaked — Send commits the edited version (and replaces the
  history entry so the next paste has the fix); X cancels.
- **Each card has a ✕ button** on the right that removes that one entry.
- A `🗑 Clear all` button at the top-right wipes the whole history with a
  two-tap confirmation — the first tap arms it ("Tap to confirm" in accent
  colour), the second confirms; it auto-disarms after 3 seconds.

## Globe action menu

Long-press the 🌐 key to open a vertical action menu anchored above it.

- Lists every language the user enabled in Settings → Languages, with the
  current one highlighted.
- `☐ / ☑ Function row (Esc, F1…)` toggle to force the F-row on on narrow
  phones.
- `😀 Emoji`, `📋 Clipboard`, `⚙ Keyboard settings` for the rest of the
  overlays.
- **Slide-to-select**: keep the finger pressed, slide up onto the menu and a
  small haptic ticks every time a row is crossed; releasing on a row fires
  it. Releasing back on the globe leaves the menu up for tap-to-select.
- **Auto-scroll while sliding**: if the list is taller than fits, the menu
  starts scrolled to the bottom (the row the finger lands on first), and the
  finger drifting near the top edge scrolls more rows into view.

## Settings

- **Sizing**:
  - Height scale (50 % – 160 %).
  - Width margin (0 % – 30 % per side) — narrows the keyboard symmetrically
    from both sides, useful on tablets and unfolded foldables.
  - Split keyboard mode (≥ 600 dp screens only) with a centre-gap slider
    (0.5 – 6.0 row weight). Space stretches across the gap so either thumb
    can hit it.
- **Long-press delay** (150 – 1000 ms in 10 ms steps) — applies to every
  key, both popup-character and trackpad gestures.
- **Trackpad sensitivity** (0.3× – 3.0×) — captured at arming time so cursor
  speed stays consistent for the whole touchpad session.
- **Right-of-Space slot** — `123 Symbols` (default), `😀 Emoji` or `⌥ Alt`
  (a second sticky AltGr for the right thumb). Also reassignable straight
  from the keyboard by long-pressing the slot itself.
- **Autocorrect** — `Off` / `Suggest` (default; passive candidate strip
  above the keys, never edits your text) / `Auto` (additionally replaces
  a confidently-wrong word at word boundaries: Space, punctuation,
  Enter, or tapping elsewhere in the text; retyping the corrected word
  keeps your spelling, vetoes it and teaches it permanently — Backspace
  itself always just deletes). Suggestions are disabled automatically in
  password / URL / email fields and terminals.
- **Themes** — built-in (Light / Dark / Black) plus any custom themes the
  user has saved; **+ New theme** opens the editor.
- **Languages** — switches per language; the globe cycles only the enabled
  set.
- **Updates** — Auto-update toggle (on / off), interval toggle (12 h / 24 h),
  manual *Check now* button, *Open install permissions* shortcut with an
  inline explainer about Samsung Auto Blocker (which silently blocks installs
  that don't come from Play Store / Galaxy Store — temporarily disable it to
  update).

## Themes

Built-in **Light, Dark, Black (AMOLED)** and an unlimited number of custom
themes.

The **theme editor** has:

- Live preview against the real keyboard view.
- One row per colour (background, key, pressed, text, secondary text,
  modifier, modifier text, accent, accent text) with a hex input *and* a
  tinted swatch chip at the end of the field — tapping the swatch opens an
  **ARGB colour picker dialog** (4 SeekBars + a live preview rectangle + a
  read-only `#AARRGGBB` label).
- The picker has **OK / Default / Cancel**: *Default* puts every slider
  back to the colour the picker opened with, without closing the dialog.
- Sliders for corner radius and key spacing.

Custom themes are persisted to `SharedPreferences` via `ThemeRepository` and
listed alongside the built-ins.

## In-app updater

Polls `https://api.github.com/repos/9hm2/pcKeyboard/releases/latest` and
compares the release tag against `BuildConfig.VERSION_NAME`. A manual *Check
now* in Settings hits the network unconditionally; automatic checks (via
`WorkManager`) are throttled to the user-chosen 12 h / 24 h interval and
only run while pcKeyboard is the default IME.

When a newer release is found:

- **Foreground** (Setup / Settings dialog): a Material dialog shows the
  version + release notes and offers *Download / View on GitHub / Later*.
  Tapping Download opens a non-cancellable progress dialog that shows
  `47 %  ·  2.3 MB / 4.9 MB` while `UpdateDownloader` streams the APK into
  `context.cacheDir` (no storage permission needed). On completion it fires
  the system installer through a **FileProvider URI**, so the user goes
  straight from the in-app download to the install prompt.
- **Background** (`UpdateCheckWorker`): same downloader, no progress UI; on
  completion it fires the same FileProvider install intent.

Samsung's Auto Blocker overrides the system "install unknown apps" permission
and silently blocks installs from non-store sources. The Updates card in
Settings has an *Open install permissions* button (with a fallback to
*Application details*) plus an inline note explaining that the user has to
flip Auto Blocker off briefly to update (it auto-re-enables 30 minutes later
if you keep that option on).

## Install & enable

1. Install the APK.
2. Launch the app → **Open system input settings** → enable pcKeyboard.
3. Tap **Switch keyboard** → pick pcKeyboard.

## Build

```bash
gradle :app:assembleDebug    # debug APK, signed with bundled debug.keystore
gradle :app:assembleRelease  # release APK; signed only if env vars are set
```

CI is **manual-only** (`workflow_dispatch`) — see
`.github/workflows/android.yml`. The workflow installs the Android SDK, runs
lint and builds the debug APK. **Release APKs are built on the developer's
device only**, never on CI, so the release signing key never has to leave the
local machine.

Every workflow run **always publishes a GitHub Release** with the freshly-
built debug APK attached, so the in-app updater always has something newer
to find. Inputs:

- `release_tag` — optional. Custom tag like `v1.2.0`. If empty, auto-tags
  with `ci-YYYYMMDD-HHMMSS` (UTC).
- `prerelease` — optional boolean. Marks the release as a pre-release, which
  excludes it from `/releases/latest` (and therefore from the in-app
  updater) so you can publish test builds without notifying users.

The debug keystore (`app/debug.keystore`, the well-known Android default) is
committed so debug APKs are signed identically on every machine. The release
keystore is **not** in the repo — see `app/build.gradle.kts` for the env
vars `assembleRelease` reads.

## Architecture

```
model/        Key, KeyType, KeyboardLayout, ModifierState (sticky tap-once /
              tap-lock cycle, KeyEvent meta-flag conversion).

layout/       LayoutBlocks       — shared Fn / number / control / symbols
                                   rows reused by every locale.
              EnglishLayout / HungarianLayout / GermanLayout /
                SpanishLayout     — locale-specific letter rows with
                                    Key.altLabel for the held-Alt AltGr
                                    glyphs from each xkb table.
              LayoutRegistry      — locale id → LayoutPack lookup.
              LayoutVariant /
                LayoutSelector    — COMPACT (no Fn row) ↔ FULL PC variant
                                    chosen from widthDp (overridable via
                                    KeyboardPrefs.showFunctionRow).

theme/        KeyboardTheme data class, built-in Themes (Light / Dark /
              Black), ThemeRepository (custom theme persistence).

view/         KeyboardView       — FrameLayout that hosts mainContainer
                                   (popupZone + optional emojiSearchHeader
                                   + rowsContainer) plus the optional
                                   overlays (popup, action menu, emoji
                                   picker, clipboard view, trackpad).
                                   Reports a smaller contentTopInsets in
                                   PcKeyboardService.onComputeInsets so the
                                   popup zone stays transparent over the
                                   app behind.
              KeyView            — single key renderer (off / armed / locked
                                   states, repeatable hold, long-press
                                   timer).
              KeyPopupView       — horizontal alternate-character strip
                                   with dynamic cell sizing and a fully-
                                   rounded accent-coloured selection pill.
              ActionMenuView     — vertical globe-long-press menu with
                                   slide-to-select + auto-scroll.
              TrackpadView       — two-phase touchpad overlay: arming
                                   indicator → free touchpad with relative
                                   pixel deltas, finger-follow dot, and a
                                   ✕ close button that hides while
                                   touching.
              EmojiView /
                EmojiCatalog /
                EmojiKeywords /
                EmojiUsageTracker /
                EmojiSearchHeaderView — picker, search bar overlay above
                                   the keyboard rows, 9 categories +
                                   Recents.

clipboard/    ClipboardView /
                ClipboardHistory /
                ClipboardEditorActivity /
                ClipboardEditorBridge — list of cards, per-entry delete,
                                   clear-all-with-confirm, full-screen
                                   edit window with a static-singleton
                                   bridge back into the IME service.

editor/       ThemeEditorActivity — live-preview keyboard + hex + swatch
                                   chip per colour.
              ColorPickerDialog   — ARGB SeekBars + preview + Default /
                                   OK / Cancel.

updater/      UpdateChecker      — GitHub releases API + version compare.
              UpdateDownloader   — HttpURLConnection stream → cacheDir
                                   + FileProvider install intent.
              UpdateUi           — Setup / Settings glue: Material dialog
                                   + progress dialog.
              UpdateScheduler /
                UpdateCheckWorker — WorkManager periodic background check.

settings/     SetupActivity      — onboarding (enable + switch IME).
              SettingsActivity   — sizing / theme picker / languages /
                                   updates / right-of-Space.
              KeyboardPrefs      — every persisted preference.
              InsetUtils         — system-bar inset helpers.

service/      PcKeyboardService — InputMethodService entry-point. Converts
                                  Key + ModifierState to InputConnection
                                  calls: commitText for characters,
                                  setSelection for cursor / arrow keys
                                  (with Ctrl word-jump and Shift selection
                                  extension), performEditorAction for
                                  Enter, sendKeyEvent only for keys whose
                                  modifier semantics actually need it
                                  (Backspace, Delete, Tab, Esc, F-row).
                                  Reports contentTopInsets for the popup
                                  zone in onComputeInsets, resets modifier
                                  + emoji + clipboard + symbol-page state
                                  on onFinishInputView so the next session
                                  always starts on the main letters.
```

## License

GPL v3 — see [LICENSE](LICENSE).

The word-frequency dictionaries in `app/src/main/assets/dict/` are
derived from the OpenSubtitles-2018 frequency lists published in
[hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords)
(CC-BY-SA 4.0); see `scripts/generate_dictionaries.py` for how they're
produced.
