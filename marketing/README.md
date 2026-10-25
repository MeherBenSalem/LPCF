# Marketing

Listing copy and artwork for CurseForge and Modrinth.

| Path | What it is |
|------|------------|
| `curseforge/description.md` | The project description (Markdown; also works on Modrinth). Images load from this repo's `main` branch. |
| `curseforge/summary.txt` | The one-line summary (under 250 characters). |
| `images/banner.png` | 1800×600 header banner. |
| `images/icon.png` | 512×512 project icon. |
| `images/card-*.png` | 1600×900 feature cards used in the description and as gallery images. |
| `images/header-*.png`, `images/divider.png` | Transparent section headers and a divider for the description. |
| `images/screenshots/*.jpg` | Real in-game captures (Paper 26.3, Folia 26.3) used by the cards and the gallery. |
| `source/render.cjs` | Renders every PNG above from the screenshots and the bundled fonts. |
| `source/fonts/` | Space Grotesk and Inter, both under the SIL Open Font License (licence files included). |

The look: a server at night. A deep indigo canvas with a faint grid, the translucent dark plate Minecraft draws
nametags on, and the coral-to-amber gradient of a rank prefix; cyan for links and mentions. Headings are Space
Grotesk, body text Inter. Section headers are titles on a nametag plate between gradient brackets, next to a chat
bubble with typing dots; the divider is the same typing bubble on a gradient line; the icon is a `[LP]` nametag plate
under a typing bubble.

## Regenerating the images

Needs Node.js and Playwright with Chromium:

```bash
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs                # everything
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs card-chat      # one asset
```

The release name, platforms, Minecraft versions and the builds checked in game (the compatibility card) are at the
top of `render.cjs`. Supporting a new Minecraft version means editing `VERSIONS` there, plus the compatibility tables
in `description.md` and the README.

## Retaking the screenshots

`tools/qa/marketing_shots.py` captures every screenshot from real vanilla clients (see `tools/qa/` and
`CONTRIBUTING.md`): 1920×1080 windows under Xvfb, on a Paper 26.3 server with a default (not flat) world. Steve is
the camera; Builder and Alex are the players being photographed.

```bash
./gradlew shadowJar qaHelperJar
python3 tools/qa/server.py prepare paper 26.3 --normal-world && python3 tools/qa/server.py start paper 26.3
for p in Builder Alex Steve; do python3 tools/qa/mcclient.py launch 26.3 $p 127.0.0.1:25565 --log /tmp/$p.log; done
python3 tools/qa/marketing_shots.py              # or some: hero chat
# Folia: prepare and start folia 26.3 the same way with Builder and Steve, then
python3 tools/qa/marketing_shots.py folia
```

Every screenshot is a single unedited frame of Steve's game window. What is staged:

* The LuckPerms groups (`owner` with a gradient `[Owner]` prefix, `vip` with a cyan `[VIP]` prefix) and who is in
  them are set up by the script; the chat lines are typed by the players' clients.
* The players stand still where the script teleports them (they are idle clients). Time is set to noon, weather clear.
* `nametag-lines.jpg` uses a two-line `nametags.lines` (prefix and name, then health from PlaceholderAPI's player
  expansion and the world name); every other shot uses the default one line.
* The HUD is hidden (F1) for the nametag and bubble shots; text displays stay visible without the HUD, chat does not.

| Screenshot | Scene |
|------------|-------|
| `hero.jpg` | Two ranked players with gradient nametags, one speaking in a bubble |
| `nametag-lines.jpg` | Two-line nametags with a PlaceholderAPI placeholder |
| `chat.jpg` | Chat with ranks, a mention, a link, `[ITEM]` and emoji, and the hover card of a name |
| `tablist.jpg` | The tab list sorted by rank with the header and footer |
| `debug.jpg` | `/lpcf debug` and a player's injection attempt shown as plain text |
| `folia.jpg` | Folia: the full nametag (the vanilla name is hidden by the riding tag) and a bubble, HUD on |
