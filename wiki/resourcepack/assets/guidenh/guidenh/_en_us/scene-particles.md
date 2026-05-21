---
navigation:
  title: Scene Particles
  parent: index.md
  position: 41
categories:
  - scenes
---

# Scene Particles

GuideNH now supports two particle authoring paths:

1. Static scene particles through `<Particle>` inside `<GameScene>`
2. Runtime Ponder particles through the `particles` array in `ImportPonder` JSON

## Static `<Particle>`

`<Particle>` renders a stationary particle at a fixed world-space position. When `name` is omitted,
it uses the default billboard particle.

| Attribute | Default | Description |
| --- | --- | --- |
| `name` | `billboard` | Particle appearance. Supported values: `billboard`, `smoke`, `largesmoke`, `explode`, `flash`, `largeexplode`, `hugeexplosion`. `particle`, `quad`, and `sheet` are accepted aliases for `billboard`. |
| `x`, `y`, `z` | `0.5`, `0.5`, `0.5` | Particle origin in world space |
| `size` | `0.18` | Particle half-size in block units |

```mdx
<GameScene width="192" height="128" zoom={5} interactive={false}>
  <Block id="minecraft:furnace" x="1" />
  <Particle x="1.5" y="1.85" z="0.5" size="0.22" />
  <Particle name="smoke" x="1.5" y="1.35" z="0.5" size="0.18" />
</GameScene>
```

## Ponder `particles`

Ponder particles spawn only when the timeline advances forward into a keyframe. They are not
replayed during reverse scrubbing, which keeps seek behaviour deterministic.

Generic particles:

```json
"particles": [
  {
    "name": "smoke",
    "x": 1.5,
    "y": 1.85,
    "z": 1.5,
    "vx": 0.0,
    "vy": 0.01,
    "vz": 0.0,
    "size": 0.18,
    "time": 16,
    "count": 3
  }
]
```

Explosion preset:

```json
"particles": [
  {
    "preset": "explosion",
    "x": 1.5,
    "y": 1.45,
    "z": 1.5,
    "time": 8,
    "power": 2.4
  }
]
```

| Field | Description |
| --- | --- |
| `preset` | Special preset. `explosion` reproduces a vanilla-style flash, smoke, and outward burst. |
| `name` | Generic particle appearance. Supported values: `billboard`, `smoke`, `largesmoke`, `explode`, `flash`, `largeexplode`, `hugeexplosion`. |
| `particle` / `kind` | Compatibility aliases for `name`. |
| `x`, `y`, `z` | Particle origin in world space. |
| `vx`, `vy`, `vz` | Initial velocity. `motionX/Y/Z` are accepted aliases. |
| `time` / `lifetime` | Particle lifetime in ticks. |
| `size` | Particle half-size in block units. |
| `count` | Generic particle count. When omitted for `explosion`, it scales from `power`. |
| `power` | Explosion strength for the `explosion` preset. |
