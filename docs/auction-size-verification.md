# Auction region size — 2026-11-01

## Spec and evidence

The user approved showing stall dimensions and volume in auction listings. The
example 5 × 5 × 10 has a bounding volume of 250 blocks. REQ-397 labels this as
the enclosing region bounds, not a guarantee that every block is empty,
accessible, buildable or inside a nonrectangular region.

Authoritative parent main 57704f1 and fork main 2870eb9 were fetched; this
isolated branch is based on fork main and preserves the separate finder work.
The existing RegionProvider documents inclusive bounds. WorldGuard's adapter
can return polygon bounding boxes; it calls Bukkit.getWorld, so the auction
menu resolves visible-region sizes on its synchronous render path rather than
the async database prefetch. Absent, invalid, overflowing or failed bounds return
an unavailable label; no database, bid, auction or region state is mutated.

Four tests covering inclusive coordinates, one block, unavailable/inverted
bounds and arithmetic overflow are authored but not yet run. Locale YAML and
`git diff --check` pass. There is no project-local EARS validator or SPEAR
state helper; this requirement, task and evidence record are maintained
manually. A clean historical red run is not claimed for this new feature.

## Preview and remaining verification

[Interactive auction preview](https://enthusia-market-native-fixes-review.xxnatetheprogamerxx.chatgpt.site/auction-size.html)
shows three schematic listings including 5 × 5 × 10 and unavailable bounds.
A JavaScript syntax and three-selection logic check passed locally. The owner-
private hosted Site deployed successfully, but this session's browser surface
is unavailable, so actual browser and authenticated mobile access remain
unverified. Icons and data are illustrative; no bids can be placed in preview.

Local Gradle and hosted CI need current capacity dispositions. Native Java and
Bedrock acceptance, final review, PR, merge and TEST activation are not claimed.

### Polygon rehearsal

The previous width × length × height calculation includes the public cutout. Trace a six-by-six footprint through (0,0), (5,0), (5,3), (3,3), (3,5), (0,5). At Y=100 through 103 the region covers 32 columns and 128 blocks. (3,5) belongs to the alcove and (4,5) belongs to the aisle. Public paths need their own build protection if the surrounding world otherwise allows building.

The WorldGuard adapter counts interior and boundary lattice points using Pick's theorem, caches the result by outline and supplies the same measurement to auction lore and rent pricing. Self-intersecting or degenerate outlines have no quoted volume. Regression coverage uses actual WorldGuard containment for a concave boundary and a diagonal polygon, and checks the rent and rendered auction lore.
