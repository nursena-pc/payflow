# Secret scanning baseline

PayFlow treats secret scanning as an evidence-producing security control, not as a claim that a scanner can prove the absence of all secrets.

## Scanner and scope

The committed-content verifier uses Gitleaks 8.30.1 through the immutable container image recorded in `gitleaks-baseline.json`. The verifier scans an exact `git archive HEAD` snapshot, so ignored runtime files, local environment files, and unrelated working-tree changes are not part of the committed-content result.

Run:

```powershell
pwsh ./scripts/security/verify-gitleaks.ps1
```

The verifier writes only redacted/safe metadata under the ignored `.runtime/security/gitleaks` directory. Raw Gitleaks report material is created only in a temporary directory and is deleted before the command completes.

## Reviewed baseline

The v0.16.0 Increment 6 review at source HEAD `54226bc9625326a4dbcde0cec525867e3003b973` produced 10 committed-snapshot findings and 10 full-history findings. The committed snapshot contained no new location relative to the initial review packet and required no unresolved manual-review row.

The 10 committed-snapshot findings were reviewed as:

| Classification | Count | Meaning |
| --- | ---: | --- |
| Synthetic CI fixture | 2 | Deterministic values scoped to the Docker smoke workflow |
| Documentation false positive | 1 | Roadmap prose, not credential material |
| OpenAPI example fixture | 4 | Deterministic authentication examples used only for API documentation |
| Controller-test fixture | 3 | Deterministic refresh-credential values used only by controller tests |

No Gitleaks allowlist, rule suppression, path suppression, or history rewrite is used to make these findings disappear.

## v1.0.0 release-preparation delta review

The CP7 exact candidate at source HEAD
$OldHead produced 12 committed-snapshot findings with the pinned scanner.
Two locations were new relative to the v0.16.0 reviewed baseline:
CHANGELOG.md:21 and README.md:276. Both are release-state documentation
prose and were explicitly reviewed as documentation-false-positive; neither
line contains credential or secret material.

The reviewed committed-snapshot baseline therefore contains 12 findings:
2 synthetic CI fixtures, 3 documentation false positives, 4 OpenAPI example
fixtures, and 3 controller-test fixtures. No Gitleaks allowlist, rule
suppression, path suppression, or scanner retuning was introduced for this
review.
## Verification invariant

`gitleaks-baseline.json` stores only safe metadata: rule ID, repository path, line range, SHA-256 digest of the reviewed source line, classification, and rationale. It does not store the matched secret text, the Gitleaks match field, or raw source-line content.

Verification fails when:

- a new finding appears;
- a reviewed finding moves;
- the rule or line range changes;
- the reviewed line digest changes;
- a reviewed finding disappears without an explicit baseline review.

This intentionally makes baseline changes reviewable rather than silently accepting scanner drift.

## Full-history treatment

Full-history scanning is retained as review evidence. Historical synthetic fixtures are not a reason to rewrite repository history. If a future review identifies a real credential or secret, the first response is rotation/revocation and incident handling; history remediation is a separate, explicitly reviewed decision.

## Interpretation

A passing verifier means the committed tree contains exactly the reviewed finding set for the pinned scanner version. It does **not** mean “zero findings,” and it does not replace code review, credential management, dependency review, or runtime security controls.
