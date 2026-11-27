# Market upstream reconciliation verification

Date: 2026-11-27

## Scope and source pins

- Upstream release candidate PR #226 branch started at `2a1019ab5ebe64b712e1cc62a8887a5453fb17ba`.
- Fork `main`: `39bc541afe27e52f440b7b0ceb6bcba18b4a0bbc`.
- Emergency auction shop preview: `55abd07214b946c23eaa1bc1fe69fb53913ec0a4`.
- In-game irregular stall region editor: `9198b428058b1684367fbd8c5078e4d1b88abd26`.

## Reconciliation decisions

- Merged the fork's current `main`, the emergency-auction shop preview, and the in-game irregular region editor into the release candidate.
- Resolved overlapping release code by retaining the release branch's readable `/em list`, tab-completion wiring, and Balanced locale implementation while keeping the new fork features.
- Resolved the Codacy annotations reported on the combined PR #226 head: permission registration and maintenance messaging are extracted; polygon/editor/WorldGuard validation and preview helpers are simplified; emergency listing tests and SQL row mapping are separated; schematic restoration and rent test fixtures use small context objects.
- Emergency snapshot capture remains an explicit option on the existing auction creation operation and transactional with auction insertion.

## Verification

- `test detekt shadowJar -PuseMavenLocal=true`: passed.
- Java 22 ran Detekt; Gradle/Kotlin used the project's Java 25 toolchain.
- Test results: 1,269 tests, 0 failures, 0 errors, 17 skipped.
- `git diff --check`: passed.
- A Detekt invocation under Java 25 failed before analysis with the runtime/tool error `25.0.3`; rerunning on the supported Java 22 analyzer passed.
- Updated upstream PR #226 branch `codex/market-test-integration-20261030` through commit `cc08b58ebd65fd235c658d9398272a0807cb3dfc`.
- Hosted checks passed on that exact head: build, security, Codacy with zero findings, diagnostics, Markdown lint, frontmatter checks and strict MkDocs build. CodeRabbit was skipped because manual review is required for this repository.

## Remaining release gates

- BadgersMC maintainer review and merge remain outstanding. No upstream PR merge, server deployment, or runtime activation is part of this work.
