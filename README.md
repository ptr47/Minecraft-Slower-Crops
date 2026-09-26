# Slower Crops

A NeoForge mod for Minecraft 1.21.1 that gives supported crops and tree saplings bounded, more consistent growth times. Other random-ticking blocks are unaffected.

## Configuration

The common config is `config/slowercrops-common.toml`:

```toml
[growth]
	cropMinDays = 12
	cropMaxDays = 18
	treeMinDays = 16
	treeMaxDays = 24
```

In ideal conditions, crops mature in a duration selected from the configured crop range, and saplings grow into trees in a duration selected from the tree range. The default ranges are 12–18 in-game days for crops and 16–24 days for trees. The configured duration includes the normal day/night cycle. At night, plants continue accumulating growth time at a slower rate; insufficient light, dry farmland, or other unfavorable conditions can extend the duration.

Growth clocks are saved with the world and start in proportion to a plant's current age, so crops and saplings already growing when the mod is installed do not restart their full growth period. Bone meal retains its vanilla effect and advances the plant's timer along with its age. When Jade is installed, its tooltip also shows a rough estimate of the remaining growth time for supported immature crops and saplings. Estimates assume favorable conditions; poor light, dry farmland, or other unfavorable conditions can make actual growth take longer. Supported vanilla crop families include crops, stems, nether wart, pitcher crops, and cocoa. Modded blocks that extend the standard vanilla crop or sapling classes are recognized automatically. Custom growth systems that do not use those classes are left alone.
