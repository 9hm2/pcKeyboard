# pcKeyboard

A full PC-style virtual keyboard for Android, designed for **foldable devices**
(Samsung Galaxy Fold, Pixel Fold, etc.) but equally usable on regular phones.
Same key set as a desktop keyboard — F-row, modifier cluster, arrow keys —
with proper IME integration so apps see real `Ctrl+C`, `Alt+Tab`, etc.

## Features

### Layout

- **Full PC keyboard layout**: dedicated F1–F12 row, Esc, Tab, Caps Lock,
  Ctrl, Alt, Win/Meta, arrow cluster, both-side Shift/Ctrl/Alt.
- **Two symbol pages** reached via `123` and `=\<`: page 1 has the standard
  punctuation set, page 2 has currency / brackets / math symbols
  (`~ ` `` ` `` `| • ° ¶ § © ® ™ € £ ¥ ¢ ^ = ÷ × ± ¬ < > { } [ ] \ ...`).
- **Foldable / tablet aware**: at ≥600 dp the full PC layout is shown; on
  narrow phones a compact 5-row variant is shown automatically (F-row
  dropped). Switches live on configuration change (fold / unfold / rotate).
- **Multiple languages out of the box**: English (US, QWERTY), Hungarian
  (QWERTZ with á / é / í / ó ö ő / ú ü ű popups), German (QWERTZ with
  ä / ö / ü / ß), Spanish (QWERTY with ñ as a dedicated key, ¿ on the
  slash). Tap the globe key on the bottom row to cycle.
- **Extensible**: a new locale is one `*.Layout.kt` file using
  `LayoutBlocks` for the shared Fn / number / control / symbol rows, a
  `LayoutPack` entry in `LayoutRegistry`, and a `<subtype>` in
  `res/xml/method.xml`.

### Modifiers

- **Real Shift / Ctrl / Alt / Meta / Fn / Caps Lock** with three states each:
  - **Off** — default appearance.
  - **Armed once** — thick accent border + accent dot in the corner. The
    modifier applies to the next key press, then releases.
  - **Locked** — full accent-coloured background + accent underline below
    the label. Stays on until tapped again.
- Modifier state is converted to `KeyEvent` meta flags, so apps receive
  proper `Ctrl+C`, `Shift+Tab`, `Alt+F4`, etc. instead of broken text.

### Long-press alternates

- Long-pressing any key with alternate characters opens a popup directly
  above the key.
- The **base character is placed in the centre** of the popup and pre-selected,
  so a straight release commits the base char; sliding left/right walks
  symmetrically through the alternates.
- **Selected cell pops out** of the popup card and uses bolder, larger text
  so the focused character is unambiguous.
- The **shift-label is included** in the alternates for symbol keys — e.g.
  long-pressing `,` exposes `<`, `«`, `‹`, `„` in one popup.
- Cell width and text size **scale with screen width** so the popup never
  spills off-screen even for letters with 8+ accented variants.
- The popup never falls below the anchor — it clamps to the top of the
  keyboard if there's not enough room above.

### Space-long-press trackpad

- Long-pressing Space switches the entire keyboard area into a trackpad
  surface with a circular indicator in the middle.
- The user must **slide their finger from Space into the indicator** to arm
  the trackpad — releasing without entering the indicator restores the
  keyboard and emits no Space character (treats it as an accidental
  long-press).
- Once armed (haptic confirmation), every 10 dp of finger movement maps
  to a one-step cursor move; release at any time ends trackpad mode
  without inserting Space.

### Cursor keys & Enter

Arrow keys, Home / End, Page Up / Page Down, and Enter all go through
`InputConnection` editor commands rather than DPAD / Enter `KeyEvent`s.
That keeps the gesture inside the focused editor — on foldables and in
free-form / multi-window mode the system window manager would otherwise
steal a DPAD event to select the floating-window grab handle, jump
focus to a sibling Send button, etc.

- **← / →** — move caret one character; with **Ctrl** jump by word
  boundary; with **Shift** extend selection instead of moving.
- **↑ / ↓** — move caret one line, preserving column.
- **Home / End** — start / end of current line (extends selection
  under Shift).
- **Page Up / Page Down** — ±10 lines.
- **Enter** — runs the editor's declared `IME_ACTION_*` (Send / Done /
  Search / Next / ...) when one is set; otherwise commits `\n`.
  Holding any modifier (Shift+Enter, Ctrl+Enter, ...) always commits
  `\n` — the universal "newline in a chat field" shortcut.

### Sizing & layout settings

- **Height** scale (50 % – 160 %).
- **Width margin** (0 % – 30 % per side) — narrows the keyboard
  symmetrically from both sides, useful on tablets / unfolded foldables.
- **Split keyboard** mode (≥600 dp screens only): adds a centre gap to
  every row at its natural half-weight point. **Space is exempt** — its
  width-weight is enlarged by the gap amount instead, so the Space key
  stretches across the gap and stays reachable with either thumb.
- **Centre gap width** slider (0.5 – 6.0 row weight units).
- **Long-press delay** slider (150 – 1000 ms in 10 ms steps) — applies to
  every key, both popup-character and trackpad gestures.

### Themes & editor

- **Built-in**: Light, Dark, Black (AMOLED).
- **Custom theme editor**: live preview against the real keyboard view,
  hex inputs for background / key / pressed / text / secondary-text /
  modifier / modifier-text / accent / accent-text colours, sliders for
  corner radius and key spacing.
- Custom themes are saved to SharedPreferences and shown alongside the
  built-ins in the settings list.

### System integration

- **Edge-to-edge** Setup / Settings / Theme Editor — the toolbar slides
  under the status bar via `fitsSystemWindows`, FABs and scroll content
  pick up bottom inset via `WindowInsetsCompat`.
- Targets **API 35** (Android 15), `minSdk` 26. Material 3 throughout.
- IME service rebuilds the keyboard view on `onConfigurationChanged`
  (fold / unfold / rotate) and refreshes theme + sizing prefs on every
  `onStartInputView`, so changes show up immediately without restarting
  the IME.

## Build

```bash
./gradlew :app:assembleDebug    # debug APK, signed with bundled debug.keystore
./gradlew :app:assembleRelease  # release APK; signed only if you provide a keystore
```

CI is **manual-only** (`workflow_dispatch`) — see
`.github/workflows/android.yml`. The workflow installs the Android SDK,
generates the Gradle wrapper if missing, runs lint and uploads the debug
APK. **Release APKs are built on the developer's device only**, never on
CI, so the release signing key never has to leave the local machine.

The workflow has an optional `release_tag` input — pass a tag (e.g.
`v1.2.0`) and it'll also publish a GitHub Release with the freshly-built
debug APK attached. The in-app updater (see below) finds this release
and offers it to the user.

### In-app updater

`UpdateChecker` polls
`https://api.github.com/repos/9hm2/pcKeyboard/releases/latest` on
SetupActivity launch (throttled to once per 12 hours), compares the
release tag against `BuildConfig.VERSION_NAME`, and surfaces a Material
dialog when a newer release is published. The dialog shows the version
+ release notes and offers three buttons:

- **Download** — enqueues the .apk asset via `DownloadManager`; the
  system notification on completion lets the user tap to install (the
  app declares `REQUEST_INSTALL_PACKAGES`).
- **View on GitHub** — opens the release page in the browser.
- **Later** — dismisses; the throttle defers the next check by 12h.

The debug keystore (`app/debug.keystore`, the well-known Android default)
is committed so debug APKs are signed identically on every machine. The
release keystore is not in the repo — see `app/build.gradle.kts` for the
env vars `assembleRelease` reads when signing locally.

## License

GPL v3 — see [LICENSE](LICENSE).

## Install & enable

1. Install the APK.
2. Launch the app → **Open system input settings** → enable pcKeyboard.
3. Tap **Switch keyboard** → pick pcKeyboard.

## Architecture

```
model/         Key, KeyType, KeyboardLayout, ModifierState (sticky tap-once /
               tap-lock cycle, KeyEvent meta-flag conversion).

layout/        LayoutBlocks      — shared Fn / number / control / symbols
                                    rows reused by every locale.
               EnglishLayout / HungarianLayout / GermanLayout /
                 SpanishLayout     — locale-specific letter rows.
               LayoutRegistry     — locale id → LayoutPack lookup.
               LayoutVariant /
                 LayoutSelector   — compact (no Fn row) ↔ full PC variant.

theme/         KeyboardTheme data class, built-in Themes (Light / Dark / Black),
               ThemeRepository (custom theme persistence in SharedPreferences).

view/          KeyboardView   — FrameLayout that hosts the rows + optional
                                trackpad overlay; routes long-press / cursor
                                events to the service.
               KeyView        — single key renderer (off / armed / locked
                                states, repeatable hold, long-press timer).
               KeyPopupView   — horizontal alternate-character strip with
                                dynamic cell sizing and selected-cell zoom.
               TrackpadView   — full-keyboard overlay with centre indicator
                                that "arms" when the finger enters it.

service/       PcKeyboardService — InputMethodService entry-point. Converts
                                    Key + ModifierState to InputConnection
                                    calls: commitText for characters,
                                    setSelection for cursor / arrow keys
                                    (with Ctrl word-jump and Shift selection
                                    extension), performEditorAction for
                                    Enter, sendKeyEvent only for keys whose
                                    modifier semantics actually need it
                                    (Backspace, Delete, Tab, Esc, F-row).

settings/      SetupActivity      — onboarding (enable + switch IME).
               SettingsActivity   — size / split / long-press / theme picker.
               ThemeAdapter       — theme list cells.
               KeyboardPrefs      — height, padding, split, gap, LP delay.
               InsetUtils         — system-bar inset helpers.

editor/        ThemeEditorActivity — hex inputs + sliders + live preview.
```
