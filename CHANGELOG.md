# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html). The plugin's
public contract is its configuration keys, its commands and permissions, and the shape of
`data.yml`; a change to any of those is what decides the version.

## [Unreleased]

## [1.5.1] - 2026-10-24

A fix-only release. The set of configuration keys is identical to 1.5.0 (172), `plugin.yml` is
unchanged, and `data.yml` is not read or written any differently.

### Added

- A release workflow: pushing a `v*` tag builds the jar and publishes a GitHub Release, refusing a
  tag that disagrees with the version in `pom.xml`. First active in this release.
- Internal: `MaterialTiers` (material rules by tier and kind), and `PowerCostTest`.

### Changed

- Material rules are decided by tier and kind instead of by listing individual items. The rules
  themselves are unchanged; a few items may now be accepted or refused where the old lists and the
  rules disagreed, so it is worth checking anything you rely on a specific material for.
- Test hall (not in the jar): the conversion room says which of the two things happened, boards
  answer the left button, the demon's stand-in carries netherrack, and one hint no longer repeats
  itself on every click.

### Fixed

- A WitchHunter's grapple moves the player again — it was setting a velocity the server discarded.
- A cooldown taken by one arrow no longer applies to the other four.
- An enderborn's pearl is noticed in either hand, not only the swinging one.
- A cost exactly equal to the power you hold can be paid; the comparison was `>` where the race
  meant `>=`, so a bar of exactly five thousand could not pay five thousand.
- The ghoul's truce list grows from six creatures to nineteen (`Ghoul.Truce.Creatures`): both the
  zombie family and the skeleton family complete, plus the phantom, and the creeper and enderman
  that were already there.

### Upgrading

Drop the jar into `plugins/` and restart. Nothing to migrate.

`config.yml` is rewritten once if `Ghoul.Truce.Creatures` still holds exactly the old six-mob
default — the file is the truth in this plugin, so a widened default would otherwise never reach an
existing server. A list you have customised is left alone.

## [1.5.0] - 2026-10-17

### Added

- `EnderBorn.Convert.Chance` (default `0.10`) — the enderborn's turning was the one roll in the
  plugin with no setting behind it.

### Changed

- Power that cannot go down (the operator permission) now says so once per player per run, instead
  of refusing every cost in silence.

### Fixed

- A hand-edited save file can no longer bring in power above the ceiling, or a value that is not a
  number: the file carried the power twice, and the unbounded copy won.
- A demon's netherrack can hurt a demon — the conversion used to charge full price, cancel the blow,
  and change nothing.
- An angel's summoning spends its offering (beef, bone, porkchop).
- A banish with no banish point costs nothing and says so; both players are told where the victim
  went.
- A bow with no arrows says so, instead of drawing and producing nothing.
- A bond that ends no longer throws when it is the bonded side that ends it.
- A grapple ends instead of throwing when its player and its target are in different worlds.

## [1.4.1] - 2026-10-14

The first published build.

### Added

- A test harness: unit tests, plus scenario tests that boot a real Paper server and drive fake
  players through a hall of rooms, one per step, in each race's own world.

### Fixed

- The plugin's clock advanced once per player instead of once per tick, so every timed rule —
  regeneration, power over time, the truce timer, the daylight rules — ran faster the more people
  were online.

[Unreleased]: https://github.com/VampireAchao/mmSupernaturals/compare/v1.5.1...HEAD
[1.5.1]: https://github.com/VampireAchao/mmSupernaturals/compare/v1.5.0...v1.5.1
[1.5.0]: https://github.com/VampireAchao/mmSupernaturals/compare/v1.4.1...v1.5.0
[1.4.1]: https://github.com/VampireAchao/mmSupernaturals/releases/tag/v1.4.1
