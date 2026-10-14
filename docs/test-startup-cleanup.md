# TEST startup cleanup

STARTUP-001: When the network loads Staff, Market and STARTUP-phase Guilds, the dependency metadata SHALL preserve the required Guilds classpath without a circular enable-order constraint. Guilds' existing STARTUP phase provides its service before POSTWORLD Market enables; no domain behavior or interface is removed.

Spec/evidence: TEST startup 2026-10-10 reports `Staff -> Market -> Guilds -> Staff`. Paper supports `load: OMIT` independently of `required` and `join-classpath`: https://docs.papermc.io/paper/dev/getting-started/paper-plugins/. The companion descriptor declares `load: STARTUP`; Market retains `required: true` and `join-classpath: true`. Current canonical main remains 14351db4dc416138341a11d0e4e27602f207221b; the combined TEST development stack is preserved.

This is an infrastructure metadata change; behavioral engine changes do not apply. The metadata regression and TEST restart establish the relevant ordering evidence. Project-local EARS/state helpers are absent; this file records the manual requirement/task/evidence. Refine gates: full local checks, final-head hosted analysis, reviewable source PR, clean artifact/source/hash record, TEST backup/upload/download-back verification and successful startup. Minecraft client acceptance and production are separate.

Local proof: metadata regression failed before the edit; check and shadowJar passed with 984 cases, zero failures/errors, 14 optional skips. TEST activation evidence is recorded in the operational receipt.
