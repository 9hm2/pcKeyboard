# pcKeyboard

A full PC-style virtual keyboard for Android, designed for **foldable devices** (Samsung Galaxy Fold, Pixel Fold, etc.) but equally usable on regular phones.

## Features

- **Full PC keyboard layout**: dedicated F1–F12 row, Esc, Tab, Caps Lock, Ctrl, Alt, Win/Meta, arrow cluster — just like Samsung's foldable keyboard.
- **Real modifier keys**: Ctrl/Alt/Meta/Shift forwarded as Android `KeyEvent` meta flags so apps receive proper Ctrl+C, Alt+Tab, etc.
- **Sticky modifiers**: tap-once / tap-lock cycle on every modifier (Samsung-style).
- **Foldable-aware**: automatically picks a compact layout (no F-row) on narrow phone screens, full PC layout when the device is unfolded.
- **Three built-in themes**: Light, Dark, Black (AMOLED).
- **Custom theme editor**: live preview + hex color inputs + corner radius / spacing sliders.
- **Extensible languages**: add a new locale by registering a `KeyboardLayout` in `LayoutRegistry`.
- **Modern Android**: targets API 35, min API 26, Material 3 UI, Kotlin only.

## Build

```bash
./gradlew :app:assembleDebug
```

CI builds debug + release APKs on every push — see `.github/workflows/android.yml`.

## Install & enable

1. Install the APK.
2. Open the app → **Open system input settings** → enable pcKeyboard.
3. Tap **Switch keyboard** → pick pcKeyboard.

## Architecture

- `model/` — data classes for keys, layouts, modifier state.
- `layout/` — locale layouts (English shipped, registry for more).
- `theme/` — built-in themes + custom theme repository.
- `view/` — `KeyboardView` (renders rows) and `KeyView` (draws a single key).
- `service/` — `PcKeyboardService`, the `InputMethodService` entry point.
- `settings/` — `SetupActivity` (onboarding) and `SettingsActivity` (theme picker).
- `editor/` — `ThemeEditorActivity`.
