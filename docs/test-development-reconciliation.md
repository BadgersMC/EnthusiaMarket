# TEST development reconciliation

TEST-DEV-001: Preserve the latest development stack together with current canonical main fixes, existing TEST-only behavior, settings, permissions and persistence. These are unmerged TEST artifacts, not production releases.

Spec: combine the reviewed development branch with current main; do not replace live configuration or private artwork. Prove: run the full existing suite, native disposable MariaDB fixtures and actual companion JAR contracts. Engine: resolve overlap in favor of current main correctness while retaining new development features. Architecture: preserve application ports and infrastructure adapters. Refine: record exact build and deployment evidence in the operation receipt. Project-local EARS/state helpers are unavailable; this record is the manual requirement and task evidence.

Task: full checks, source PR, SHA-256 verification, rollback backup, TEST restart and startup inspection. Player/client acceptance remains separate.

## Verification evidence

- [x] Inspect current canonical main and the latest development stack; preserve original worktrees.
- [x] Preserve TEST-only behavior and publish a reviewable combined source PR.
- [x] Full local suite: {'tests': 979, 'failures': 0, 'errors': 0, 'skipped': 14, 'exit': 0}; Java 25 plugin bytecode and the existing canonical build path.
- [x] Native disposable MariaDB and actual companion runtime contracts: {'tests': 14, 'failures': 0, 'errors': 0, 'skipped': 0, 'exit': 0}.
- [ ] Authorized TEST upload, backup, checksum verification and restart: tracked in the separate operation deployment receipt.
- [ ] Minecraft player/client acceptance, maintainer merge and production activation: separate gates.

Market command-scanning regressions register the search argument explicitly; production startup already registered it. Documentation requirement IDs distinguish search from accounting. Private artwork is not committed or replaced. Hosted final-head checks are reported independently from these local results.

## TEST startup refinement

TEST-DEV-002: When the combined development build starts, Nexus SHALL resolve the stall repository/index and access settings/policy to their respective singletons without ambiguous interface registrations.

Prove: the authorized TEST startup at 17:45:55 EDT on 2026-10-10 failed with `Multiple beans found of type StallRepository: [stallRepository, stallAccessIndex]` and disabled Market. Both aliases referred to one object; Nexus indexes concrete registrations under implemented interfaces. The settings/policy pair had the same latent ambiguity.

Engine/architecture: infrastructure startup wiring registers each concrete service once under its existing primary name. Domain interfaces, persistence, accounting, permissions and the development feature stacks are unchanged. Two regression tests exercise the exact startup registration helpers against the real Nexus container and resolve both concrete and interface types.

Refine: full check/Detekt/shadow packaging and replacement TEST startup evidence are recorded in the operation receipt. The earlier 14 native financial/database contract checks remain applicable to the unchanged financial engine; the wiring regressions add container proof. Client acceptance and canonical merge remain separate.

Final startup-fix local verification: 981 suite cases, zero failures/errors, 14 optional external skips; both real-container regressions, architecture checks and Detekt passed. Canonical shadow packaging passed with version 1.0.53-dev.reconciled.2.

TEST-DEV-003: When Paper dispatches its command registration lifecycle, Market SHALL register `/guildsales <stall> [period]`, `/stallsales`, exports and ranges without stopping the server, preserving the report permission and existing authorization/persistence semantics.

Prove: TEST version reconciled.2 resolved the DI issue and enabled, then Paper stopped startup because Nexus attempted `last()` on the empty literal path from `@Subcommand("")` in GuildSalesCommand. The existing FindItemRegistrar already documents this unsupported Nexus pattern. Engine/architecture: GuildSalesCommand remains an injected infrastructure service; a Paper lifecycle adapter builds its Brigadier tree directly, including execution at the stall argument when period is omitted. Real Brigadier execution tests cover all six reporting paths and denied permissions. The accounting engine is unchanged. TEST was temporarily restored with its previous working Market jar during verification; all candidates and original data remain preserved.

Final command-fix local verification: 983 suite cases, zero failures/errors, 14 optional external skips; real-container and real-Brigadier regressions, architecture checks and Detekt passed. Canonical shadow packaging passed with version 1.0.53-dev.reconciled.3.
