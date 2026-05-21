---
navigation:
  title: 场景粒子
  parent: index.md
  position: 41
categories:
  - scenes
---

# 场景粒子

GuideNH 现在支持两条粒子编写链路：

1. 在 `<GameScene>` 里使用静态 `<Particle>`
2. 在 `ImportPonder` 的 JSON 里使用运行时 `particles`

## 静态 `<Particle>`

`<Particle>` 会在固定的世界坐标渲染一个静止粒子。不填写 `name` 时会使用默认的面片粒子。

| 属性 | 说明 |
| --- | --- |
| `name` | 粒子外观，默认 `billboard`。支持 `billboard`、`smoke`、`largesmoke`、`explode`、`flash`、`largeexplode`、`hugeexplosion`。`particle`、`quad`、`sheet` 会作为 `billboard` 的别名处理。 |
| `x`、`y`、`z` | 粒子的世界坐标原点，默认都是 `0.5` |
| `size` | 粒子的半尺寸，单位为方块，默认 `0.18` |

```mdx
<GameScene width="192" height="128" zoom={5} interactive={false}>
  <Block id="minecraft:furnace" x="1" />
  <Particle x="1.5" y="1.85" z="0.5" size="0.22" />
  <Particle name="smoke" x="1.5" y="1.35" z="0.5" size="0.18" />
</GameScene>
```

## Ponder `particles`

Ponder 粒子只会在时间轴向前推进到关键帧时生成。向后拖动时间轴不会重复补播，
因此拖动预览和跳转仍然保持确定性。

普通粒子：

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

爆炸预设：

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

| 字段 | 说明 |
| --- | --- |
| `preset` | 特殊预设。`explosion` 会生成接近原版爆炸的闪光、烟雾和外扩爆裂粒子。 |
| `name` | 普通粒子外观。支持 `billboard`、`smoke`、`largesmoke`、`explode`、`flash`、`largeexplode`、`hugeexplosion`。 |
| `particle` / `kind` | `name` 的兼容别名。 |
| `x`、`y`、`z` | 粒子的世界坐标起点。 |
| `vx`、`vy`、`vz` | 初速度，`motionX/Y/Z` 也可作为别名。 |
| `time` / `lifetime` | 粒子生命周期，单位为 tick。 |
| `size` | 粒子半尺寸，单位为方块。 |
| `count` | 普通粒子的生成数量；爆炸预设省略时会根据 `power` 自动缩放。 |
| `power` | `explosion` 预设的爆炸强度。 |
