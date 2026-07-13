# ItemLimiter

A Paper plugin that caps how many of a specific item a player can hold at
once (main inventory + armor + offhand, combined). Once a player is at the
cap, picking up more of that item off the ground is blocked and it just
stays on the ground.

Built against Paper **26.1.2**.

## Important - I could not compile this for you

I write this code in a sandboxed container with no internet access, so I
can't reach PaperMC's Maven repository to download the Paper API and
actually produce a `.jar`. What's below is complete, ready-to-build source -
you (or a CI service) need to do the actual `gradle build` step. Three ways
to do that, easiest first:

### Option A - GitHub Actions (no installs on your end)

1. Create a new **public** GitHub repo and upload everything in this folder
   (including the hidden `.github` folder - make sure your upload method
   doesn't skip dotfiles).
2. GitHub will automatically run `.github/workflows/build.yml` on push.
3. Go to the **Actions** tab -> click the latest run -> download the
   `ItemLimiter-jar` artifact. Unzip it to get `ItemLimiter-1.0.0.jar`.

### Option B - IntelliJ IDEA (Community Edition is free)

1. Install IntelliJ IDEA Community.
2. `File > Open` this folder. Let it import as a Gradle project (it can
   download a matching JDK for you automatically if you don't have one).
3. Open the Gradle panel (right sidebar) -> `Tasks > build > build`.
4. The jar appears in `build/libs/ItemLimiter-1.0.0.jar`.

### Option C - Command line

1. Install a JDK 25+ (e.g. [Adoptium Temurin](https://adoptium.net/)).
2. Install [Gradle](https://gradle.org/install/) (or use an existing
   install).
3. From this folder, run:
   ```
   gradle build
   ```
4. The jar appears in `build/libs/ItemLimiter-1.0.0.jar`.

Then, on your server: stop it, drop the jar into `plugins/`, and start it
back up. A `config.yml` will be generated in `plugins/ItemLimiter/`.

**If the Paper API version has moved on by the time you build this**
(Paper ships builds frequently), and you get a dependency-resolution error,
open `build.gradle.kts` and check the exact version string against what
your own server reports on startup, or against
https://papermc.io/downloads/paper - the format is `26.1.2.build.<N>-stable`,
or `26.1.2.build.+` to always grab the latest build for that release.

## Configuring limits

Edit `plugins/ItemLimiter/config.yml`:

```yaml
limits:
  TOTEM_OF_UNDYING: 5
  ENDER_PEARL: 16
```

Add as many materials as you want, one per line, using the Bukkit
`Material` enum name (find them at
https://jd.papermc.io/paper/26.1.2/org/bukkit/Material.html). Then either
restart the server or run `/itemlimiter reload`.

## Commands & permissions

- `/itemlimiter reload` - reloads config.yml without restarting.
  Requires `itemlimiter.reload` (default: op).
- `itemlimiter.bypass` - exempts a player from all limits entirely
  (default: op). Handy for admins/build teams.

## How it avoids the dupe-glitch problems other plugins have

- **Ground pickup** is blocked outright when it would push a player over
  the cap - nothing is split or partially added, the stack just stays on
  the ground exactly as it was. There's no arithmetic here that could
  create or destroy items.
- **Everything else** (crafting, chests, shift-click, dragging, creative
  mode, joining/respawning with too many, `/give`, other plugins) is
  handled by one shared pass: after the relevant event, it measures what
  the player is actually holding, removes whatever's over the limit, and
  drops *exactly* that amount on the ground a tick later. Total item count
  never changes - it only ever moves from inventory to ground.
- A repeating check every 5 seconds catches anything that doesn't fire a
  Bukkit event at all (e.g. another plugin granting an item directly).

## Project layout

```
build.gradle.kts / settings.gradle.kts   - Gradle build files
src/main/java/com/itemlimiter/plugin/
  ItemLimiterPlugin.java                 - lifecycle, config, core logic
  LimiterListener.java                   - event wiring
  ItemLimiterCommand.java                - /itemlimiter reload
src/main/resources/
  plugin.yml                             - plugin descriptor
  config.yml                             - default config (see above)
.github/workflows/build.yml              - CI build -> downloadable jar
```
