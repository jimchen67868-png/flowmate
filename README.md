# Flowmate — an Automate-style visual automation app

A starter Android project (Kotlin + Jetpack Compose) that mirrors the core
idea of the Automate app: drag blocks onto a canvas, connect a trigger to a
chain of actions, and the flow runs automatically when the trigger fires.

## What's implemented

- **Visual flow editor** (`ui/FlowEditorScreen.kt`): pan/scroll canvas,
  draggable block cards, tap-to-connect ports, per-block config dialog.
- **Flow graph model** (`model/Flow.kt`, `BlockType.kt`): blocks + connections,
  saved as JSON to app-private storage (`model/FlowRepository.kt`).
- **Execution engine** (`engine/FlowEngine.kt`): walks the graph from a fired
  trigger, running actions and logic (wait) in sequence.
- **Triggers**: time schedule (WorkManager polling every 15 min), battery
  level, charging state, screen on/off (foreground service), notification
  received (NotificationListenerService).
- **Actions**: send SMS, show notification, show toast, vibrate, launch app,
  set volume.

## Opening the project

1. Install Android Studio (Koala or newer).
2. Open the `AutomateClone` folder as an existing project.
3. Let Gradle sync (it will download the Gradle 8.7 wrapper + AGP 8.5.2 the
   first time — needs network access).
4. Run on a device/emulator with API 26+.

## Permissions to grant manually after first install

- **Notification access** (for `NOTIFICATION_RECEIVED` triggers): Settings →
  Apps → Special access → Notification access → enable Flowmate. This can't
  be requested as a normal runtime permission, only deep-linked to.
- SMS, notifications, vibrate are requested on first launch.

## Where to extend next

- **Zoom/pinch on the canvas** — currently scroll-only, no pinch-zoom.
- **IF block's true/false branches** — the model supports an `IF_CONDITION`
  logic block, but the engine currently always continues down every outgoing
  connection; wire a real condition-variable store to make branching work.
- **More triggers/actions** — Wi-Fi/Bluetooth state, location geofences,
  clipboard, HTTP request action, app-launched trigger, etc. Add a new
  `BlockType` entry + a case in `ActionExecutor` or a new trigger service.
- **Undo/redo, multi-select, block search** in the editor.
- **Exact-time alarms**: swap the 15-minute WorkManager poll for
  `AlarmManager.setExactAndAllowWhileIdle` per scheduled trigger if you need
  minute-precision firing.

## Architecture at a glance

```
MainActivity
 └─ FlowListScreen  ──tap a flow──>  FlowEditorScreen
                                       ├─ BlockPaletteSheet (add blocks)
                                       ├─ BlockNode × N (drag, connect, edit)
                                       ├─ ConnectionsCanvas (draw edges)
                                       └─ BlockConfigDialog (edit params)

FlowRepository  <── JSON ──>  filesDir/flows.json

Trigger fires (service/worker) ──> FlowEngine.runFrom(flow, triggerBlock)
                                       └─ walks connections, calls ActionExecutor
```
