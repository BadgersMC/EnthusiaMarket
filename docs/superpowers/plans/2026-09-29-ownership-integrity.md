# EnthusiaMarket 26.2 Ownership Integrity â€” SPEAR Implementation Plan

**Date:** 2026-09-29  
**Baseline:** `main@8d04bd9`  
**Design:** `docs/superpowers/specs/2026-09-29-ownership-integrity-design.md`  
**Audit:** `docs/audits/ownership-integrity-2026-09-29.md`  
**Requirements:** REQ-314..322

## Goal

Eliminate ownership overlap and ghost ownership without regressing PR #194 Staff Market/moderation behavior, repair provably stale legacy data, and add `/em rent extendall <duration>`.

## Standing rules

- Use one SPEAR cycle per task: spec â†’ prove â†’ engine â†’ arch â†’ refine.
- Characterize PR #194 before changing ownership code.
- No implementation may bypass moderation acquisition permits, mutation gates, durable locks, or optimistic revisions.
- Never edit V001..V028; schema repair uses a forward migration only.
- Never infer guild ownership by comparing a guild ID to a player UUID.
- Preserve admin shops during ordinary ownership cleanup.
- Run focused tests first, then full test + detekt + architecture/Konsist + shadowJar before merge.
- Test migration/reconciliation against disposable databases and a copied production snapshot before production.
## Workstream A â€” Protect the #194 baseline

### A1 â€” TDD-320 moderation characterization

Add/extend tests around:

- prepare snapshot and shop freeze;
- MODERATION_HOLD transition;
- exact restore of owner, state, members, timing, and shop freeze flags;
- revision/checksum conflicts;
- durable stall locks and player acquisition fences;
- `MarketRegionAccessCoordinator` clear/rebuild behavior;
- blocked sell-offer, buyout, and auction award paths.

Do not refactor production code in this task except test seams required to observe existing behavior.

**Gate:** focused #194 tests green on unmodified baseline.

## Workstream B â€” Domain ownership invariants

### B1 â€” TDD-314 active ownership counting

Files:
- `application/StallOwnershipCounter.kt`
- `application/StallOwnershipCounterTest.kt`

Red test: same SOLO owner across all lifecycle states; only OWNED and GRACE count.

### B2 â€” TDD-315 successor and release state

Files:
- `domain/stall/Stall.kt`
- domain tests

Red tests:
- `awardTo` clears previous delegated members;
- canonical release to UNOWNED clears owner, ownerSince, winningBid, members, and nextRentAt.
## Workstream C â€” Ordinary transfer cleanup

### C1 â€” TDD-316 direct sell offer

Files likely touched:
- `application/SellOfferService.kt`
- ownership-transition helper/service introduced only if tests justify it
- shop repository/service collaborators
- sell-offer tests

Required sequence:
1. preserve `withAcquisitionPermit` and mutation-gate checks;
2. prove buyer charge compensation still works;
3. remove previous-context non-admin shops;
4. save successor stall with empty delegated members;
5. synchronize region ownership for the buyer;
6. delete completed offer and preserve existing payout/tax semantics.

### C2 â€” TDD-317 auction settlement

Cover both owner-created and emergency auction awards.

Keep existing close-first/charge-exactly-once/refund behavior intact. Add the same successor cleanup invariants without changing auction money semantics.

**Important:** `WorldGuardRegionMemberSync.setOwner` already clears previous owners/members. Use it; do not replace it.

## Workstream D â€” Emergency auction lifecycle

### D1 â€” TDD-319 clean forfeiture at GRACE expiry

On GRACE â†’ EMERGENCY_AUCTIONING:

- remove previous non-admin shops;
- remove delegated region/member access;
- restore schematic when enabled;
- retain former-owner identity only if settlement/recovery requires provenance;
- ensure ownership counting excludes the stall.

### D2 â€” TDD-318 canonical auction-driven release

Route auction no-bid settlement, `closeWithoutAward`, system-auction cancellation/revert, and failed-award recovery through one canonical auction release behavior instead of partial copies.

### D3 â€” TDD-326 canonical rent-orphan stall/shop recovery

Route `RentCollectionService.recoverOrphanedEmergencyStalls` through `Stall.releaseOwnership()`, remove stale non-admin shops while preserving admin shops, and prove repeated recovery is idempotent using only its current collaborators.

### D4 â€” TDD-325 rent-orphan access/IP/moderation cleanup

Add the lifecycle collaborators missing from `RentCollectionService`: moderation mutation gate, region access synchronization, and IP ownership release. Prove locked stalls are skipped, persist the canonical UNOWNED row before destructive cleanup so repository fencing wins races safely, and clear WG/IP projections only after the authoritative save succeeds without altering Staff Market semantics.

Canonical UNOWNED result:

- owner NONE;
- ownerSince null;
- winningBid reset;
- members empty;
- nextRentAt null;
- no previous-owner non-admin shops;
- no WG owners/members;
- IP ownership released where applicable;
- schematic restored where the transition requires it;
- state-change event emitted consistently.

## Workstream E â€” Legacy reconciliation

### E1 â€” TDD-321 forward repair

Create a forward migration/reconciliation after V028.

Test cases must include:

- stale SOLO previous-owner shop;
- valid current SOLO shop;
- guild-owned stall/shop data;
- admin shop;
- active moderation reservation;
- MODERATION_HOLD;
- repeat execution.

Ambiguous rows are retained and reported. The migration must not need production credentials or network services.

## Workstream F â€” Bulk rent extension

### F1 â€” TDD-322 application service

Add `BulkRentExtensionService` with a structured result containing updated, recovered, skipped, and failed counts.

Rules:
- duration must be positive;
- only OWNED and GRACE are eligible;
- no economy withdrawal/deposit;
- shift `nextRentAt`;
- GRACE returns to OWNED only when the new deadline is future;
- one bad stall does not abort the batch.

### F2 â€” TDD-323 command wiring

Add `/em rent extendall <duration>` under existing admin authorization. Support concise durations such as `7d`, `12h`, and `30m`; reject zero/negative/invalid inputs.
## Workstream G â€” Documentation and release validation

### G1 â€” DOC-322

After code is complete, update operator/developer docs with:

- active ownership states;
- transfer cleanup semantics;
- moderation boundary;
- reconciliation behavior;
- bulk rent extension usage.

### G2 â€” Full verification

Run the repository's current Java/Gradle toolchain from the 26.2 baseline:

- focused tests for each SPEAR task;
- complete test suite;
- detekt;
- architecture/Konsist checks;
- shadow JAR build.

Then validate on a disposable/copy database and a test server:

1. sell stall to another player;
2. confirm old shops no longer control sign/container break rights;
3. confirm old delegated members lose access;
4. confirm previous owner can buy another stall after emergency forfeiture;
5. settle normal and emergency auctions;
6. exercise no-bid/orphan recovery;
7. run moderation prepare â†’ hold â†’ restore and compare exact snapshot state;
8. run `/em rent extendall 7d` and verify no balances change.

## Merge strategy

Keep implementation commits narrow and ordered by SPEAR task. The final PR must target current `main`, not the stale `chore/mc-26.2` branch.

Do not squash away the characterization-test checkpoint until review is complete; it provides a clear proof that p2wn's #194 behavior was captured before ownership code changed.
