# Repository security and maintenance audit

Date: 2026-11-03. Repository: `Bluetwinklez/minecraf-ai-agent`.

## Scope and method

Default-branch contents and implementation PR #1, including historical branch build status.

Fetched the repository and scanned all locally fetched Git refs/history with Gitleaks 8.30.1 in fully redacted mode. Reviewed tracked sensitive-file patterns and personal-email candidates. Dependency audits describe the installed/resolved graph on this date; they are not a penetration test or a guarantee that no undiscovered vulnerability exists. Uploaded binaries, remote service configuration and files outside accessible Git refs are outside this scan.

## Changes

Replaced the placeholder README with accurate English navigation to the implementation branch and its stated validation limits.

All maintained repositories receive an English security policy/audit entry point, a pinned and checksum-verified secret-scan workflow, credential ignore patterns and immutable action references. Existing publishing jobs retain their declared permissions. Known false positives, if any, are exempted only by exact historic finding fingerprints; no entire directory or credential rule is disabled.

## Credential and personal-data findings

No Gitleaks matches were found in fetched Git history.

No working private credential was confirmed in this review. No credential values are included in this report. If a real credential is discovered later, revoke it at its provider; deleting source cannot revoke a key or remove forks, caches or clones. Existing GitHub encrypted secrets were neither read nor copied. No live-account credential testing was attempted.

## Validation

Workflow syntax and repository history scan checked. Historical implementation-branch build success is not a new mod test.

GitHub Actions YAML was checked with Actionlint. Local log files are kept outside the repositories; do not confuse historical README test counts with new results.

## Remaining limits

Implementation stays in PR #1. No Java/GameTest run, real Minecraft client or model service was exercised in this default-branch documentation change.

Main READMEs and new maintenance documents are English. Historical Turkish records and existing application translations remain; this maintenance does not claim a full UI translation. Changes are published in reviewable branches/PRs, not as an untested release.

## Suggested GitHub About description

Minecraft builder-NPC mod project; implementation and build instructions are tracked in pull request #1.

The current GitHub integration rejected repository metadata edits with HTTP 403. This prepared description can be applied when repository administration access is available; it has not been represented as an applied settings change.

## Additional implementation-branch review

A subsequent source review of the implementation behind PR #1 found two concrete issues, corrected in [PR #5](https://github.com/Bluetwinklez/minecraf-ai-agent/pull/5):

- Provider HTTP errors and invalid JSON could place raw response content in exceptions forwarded to operators. Error messages now retain a safe status/category without copying the provider response or parser details.
- The schematic root check was lexical and followed file symlinks. Candidates now resolve to real filesystem paths and must remain within the real schematic root.

Five isolated JUnit regression tests passed. All three provider-privacy tests failed against the prior helper and passed after correction. The full Fabric build workflow also passed for both configured Minecraft versions, including its headless tests. This does not certify real-client rendering or live model services. The fix was merged into the implementation branch; `main` remains a documentation entry point.
