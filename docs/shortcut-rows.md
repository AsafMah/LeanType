# Vertical Shortcut Rows

Vertical shortcut rows add optional, swipe-reachable action rows above and below the alphabet keyboard.

They are designed for commands that are useful while typing but do not need to occupy permanent keyboard space.

## Enable it

Open **Settings → Gesture Typing → Advanced Gestures** and enable either or both:

- **Top shortcut row**
- **Bottom shortcut row**

The settings default to off.

## How to use

- Swipe upward from an eligible key in the top letter row to open the top shortcut row.
- Swipe downward from an eligible key in the bottom letter row to open the bottom shortcut row.
- Keep sliding to the desired shortcut and release.

The swipe must be mostly vertical and cross the normal pointer step threshold. Horizontal movement continues to behave like normal gesture typing or key movement.

## Default shortcuts

The default rows focus on navigation and editing actions that are not already easy to reach from the alphabet keyboard.

Top row:

```text
Home
End
PgUp
PgDn
Tab
Esc
```

Bottom row:

```text
←
↓
↑
→
W←
W→
Ins
```

`W←` and `W→` move by word.

## Customizing layouts

The default layouts are simple row-based layout files:

```text
app/src/main/assets/layouts/shortcut_top/shortcut_top.txt
app/src/main/assets/layouts/shortcut_bottom/shortcut_bottom.txt
```

Each line is one shortcut key. For example:

```text
Home|!code/key_home
End|!code/key_end
PgUp|!code/key_page_up
```

The feature adds stable key-code aliases for navigation keys so custom layouts can avoid raw numeric key codes:

```text
key_arrow_left
key_arrow_down
key_arrow_up
key_arrow_right
key_home
key_end
key_page_up
key_page_down
key_word_left
key_word_right
key_escape
key_insert
key_event_tab
```

`key_tab` remains the existing literal tab character. Use `key_event_tab` when you want a hardware-style Tab key event.

## Interaction rules

Shortcut-row swipes are intentionally narrow:

- only alphabet keyboards can start them
- only normal, enabled, non-modifier source keys are eligible
- the top row starts only from the top eligible letter row
- the bottom row starts only from the bottom eligible letter row
- normal long-press popups, gesture typing, and horizontal swipes stay separate

If the IME receives a cancel event while a shortcut row is open, the popup is dismissed without committing the highlighted shortcut.

## Implementation notes

`PointerTracker` detects the vertical movement and asks `MainKeyboardView` to show a temporary popup keyboard for `LayoutType.SHORTCUT_TOP` or `LayoutType.SHORTCUT_BOTTOM`.

`ShortcutRowKeys` parses the selected shortcut layout and converts its first row into popup key specs on a synthetic parent key.

Popup placement aligns the shortcut icons across the source letter row so the swipe position maps proportionally to the visible shortcuts.
