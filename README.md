# Dawn Swap

**The first time you tap that icon in the morning, something better opens instead. Every tap after that, it behaves normally.**

Built for [this r/androidapps request](https://www.reddit.com/r/androidapps/comments/1vywswz/i_need_an_app_that_will_rearrange_the_icons_when_i/), where two people replied that no such app exists.

> "When I wake up and reach for my telephone, I always open app X by pressing an icon. I want app Z to be opened instead. Is there a way to do it, so that Z shows in place of X, and right after the first instance, X comes back to its place."

---

## "But you can't rearrange icons on Android"

That's true, and it doesn't matter.

No app can move icons around someone else's home screen. Pixel Launcher, One UI and Nova each keep their layout in a private database with no public API. Only a launcher can move icons.

**But muscle memory doesn't tap an app — it taps a position.** Half-asleep, your thumb goes to a *spot on the glass*. So Dawn Swap doesn't move anything. It puts a shortcut at that spot, wearing the icon and name of the app you'd normally open, and decides *at the moment you tap it* which app to launch.

That works on every launcher, with no root, no Tasker, no accessibility service, and no replacing your home screen.

## How it behaves

| When you tap | What opens |
|---|---|
| First tap inside your window | Your replacement |
| Every later tap that day | The usual app |
| Outside the window | The usual app |
| Tomorrow morning | Your replacement again, once |

Turn on **Swap both icons** and you get the OP's literal request: during the window the two positions genuinely trade places, so the original app is still one tap away at the *other* spot.

## What it costs you

**One inexact alarm per day.** That's the entire background footprint.

There is no service watching your screen, no accessibility hook, no polling. The decision is made by reading the clock in the microsecond after you tap — which is also why a delayed alarm can never open the wrong app. Worst case, an icon looks stale for a bit.

There is **no `INTERNET` permission** in the manifest. The app cannot phone home, by construction. It also doesn't use `QUERY_ALL_PACKAGES`; listing your apps goes through a scoped `<queries>` declaration instead.

## Setup

1. Build and install the APK — see [Building it](#building-it) — then open **Dawn Swap**.
2. Pick **the app you want to interrupt** — the one your thumb goes to.
3. Pick **what to open instead** — another installed app, or a web address (the original poster's replacement was a webapp they built).
4. Set your window. Default is 06:00–10:00. Windows that cross midnight work fine.
5. Tap **Add to home screen** and confirm the shortcut.
6. **Drag the new shortcut to exactly where the original icon sits**, then move the original into a folder or your app drawer.

Hit **Try it now** to fire it once without waiting for morning.

## The one honest caveat

Which app opens is always correct, on every launcher.

The icon *artwork* swapping relies on `updateShortcuts()`. Pixel Launcher and One UI honour it. Some third-party launchers — Nova is the known case — snapshot a shortcut's icon when you pin it and never refresh it. On those you'll get the right app behind a static icon.

That's a launcher limitation, not something an app can work around, so it's stated here rather than buried.

## How it's built

Two modules, split so the part that matters is testable without a phone:

- **`:logic`** — plain Kotlin, zero Android dependencies. Every rule about *when* the swap fires lives here and is covered by unit tests that run on any JDK.
- **`:app`** — the Android shell: the invisible trampoline activity, shortcut management, the daily alarm, and the setup screen.

The whole rule set is one pure function, [`SwapDecider.decide()`](logic/src/main/kotlin/dev/dawnswap/logic/SwapDecider.kt). A slot always displays the icon of whatever it will open, so "which icon?" and "which app?" are the same question — there is no second rule set that can drift out of sync.

### The subtle bit

A window like **23:00–02:00** must fire once per *night*, not once per calendar date. Keying "once a day" on the date would let it fire at 23:30 and then again at 00:30, because midnight silently rolls the date over mid-window.

So the swap is keyed on the date the current window *occurrence started* — see `SwapWindow.occurrenceDate()`. There are dedicated regression tests for exactly this.

## Building it

```bash
gradle :logic:test             # decision logic, JDK only
gradle :app:testDebugUnitTest  # Android layer, via Robolectric
gradle :app:assembleDebug      # the APK
```

You need JDK 17 and the Android SDK (platform 35).

**There is no prebuilt APK to download.** The Actions tab is empty on purpose — this repo is published anonymously, and a workflow run permanently records the GitHub account that triggered it. Runs are verified and then deleted, which removes their artifacts too.

Forking gets you the build for free: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs both test suites and uploads the APK, and on your own fork the runs are yours to keep.

## Status

Both layers are covered by the workflow above, which refuses to report success if either module executes zero tests. Last verified green: 164 tests — 120 logic, 44 Android — plus a successful debug APK build.

- **Decision logic** — exhaustively tested, including minute-by-minute sweeps across a full day for both normal and midnight-crossing windows.
- **Android layer** — tested with Robolectric, which stands the real activity up on the JVM. The end-to-end path is asserted the way a launcher actually drives it: build the intent a pinned shortcut carries, hand it to the trampoline, and check which app comes out.

It has still **not run on physical hardware**. If you install it and something misbehaves, please open an issue with your launcher and Android version.

## Built with

Planned, red-teamed, written and tested with **Omniscio**.

## Licence

MIT — see [LICENSE](LICENSE).
