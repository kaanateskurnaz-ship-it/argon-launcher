# Argon Launcher (prototype)

An original, from-scratch Android app covering the *surrounding* features of a Minecraft
launcher: accounts, mod browsing with dependency resolution, and a customizable on-screen
touch-control overlay that auto-hides when a physical keyboard, mouse, or game controller is
used and reappears on tap. Every file here is new code written against public API
documentation (Microsoft/Xbox/Minecraft Services auth docs, Ely.by's own OAuth docs, the
Modrinth and CurseForge REST APIs) — none of it is copied or adapted from any existing launcher
project.

## What's here

- **Accounts** (`account/`) — offline accounts (vanilla's own `OfflinePlayer:<name>` UUID
  scheme), Microsoft sign-in (device-code flow → Xbox Live → XSTS → Minecraft Services), and
  Ely.by sign-in (its own OAuth2 flow). Accounts persist as JSON in app-private storage.
- **Mods** (`mods/`) — search and install from Modrinth or CurseForge, with recursive required-
  dependency resolution (shared/duplicate dependencies are only downloaded once, one broken
  dependency doesn't abort the rest), and SHA-1 verification on every download.
- **Touch controls** (`controls/`) — a fraction-positioned button layout you can drag to
  reposition, add to, or remove from in an edit mode; `InputModeState` + `MainActivity`'s
  `dispatchKeyEvent`/`dispatchGenericMotionEvent`/`dispatchTouchEvent` overrides implement the
  auto-hide-on-physical-input / show-on-tap behavior centrally, so it works the same regardless
  of which screen is showing.
- **UI** (`ui/`) — simple Compose screens tying the above together, navigated with
  Navigation-Compose.

## What's deliberately *not* here

This project does **not** include a Minecraft Java Edition execution engine: no JVM-on-Android,
no OpenGL/Vulkan translation layer, no native rendering bridge. That's a separate, multi-year
engineering effort in its own right (it's what projects like PojavLauncher spend most of their
code on), and building an equivalent from scratch was out of scope here. Practically, that means
**the app cannot currently start or run Minecraft** — "Touch Controls" opens a stand-in colored
surface so you can try the overlay and its auto-hide behavior, not a real game session. Wiring a
real engine in later is possible (the account/mod/control pieces don't need to change), but it's
a project of its own.

Relatedly, `ElyByAuth` gets you an Ely.by-authenticated account (username/UUID/token) but does
not wire up `authlib-injector` — the piece that makes the *game process* trust Ely.by's identity
instead of Mojang's — since that lives inside the execution engine.

## Before it'll run

1. **Microsoft sign-in**: register a free app at [portal.azure.com](https://portal.azure.com)
   (public client / native platform, `XboxLive.signin offline_access` scope) and put its
   Application (client) ID into `MicrosoftAuth.CLIENT_ID`.
2. **Ely.by sign-in**: register a free app at
   [ely.by/dev/applications/add](https://ely.by/dev/applications/add) with redirect URI
   `argonlauncher://elyby-callback`, and put its client id/secret into the `ElyByAuth(...)`
   construction in `MainActivity.onCreate`.
3. **CurseForge mods** (optional, Modrinth needs no key): get a free API key at
   [console.curseforge.com](https://console.curseforge.com) and paste it into the in-app field
   when you pick CurseForge as the mod source.

## Opening the project

Open the folder in a recent Android Studio (Hedgehog/2023.1 or newer) and let it sync — Android
Studio will offer to generate the Gradle wrapper if it's missing (this sandbox had no network
access to fetch the wrapper jar, so it isn't included). Requires JDK 17 and `minSdk 26`.

## Status

This has not been compiled or run — the sandbox this was built in has no Android SDK and no
network access for Gradle to resolve dependencies. Treat it as a reviewed-by-reading starting
point rather than a tested build; expect to fix the first-compile issues Android Studio turns up.
