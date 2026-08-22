# Supply-chain evidence

PayFlow v0.16.0 Increment 6 records dependency, secret-scan, container, SBOM, and local build-input evidence without turning local evidence into a release or provenance-certification claim.

## SBOM generator

The repository generator is:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\security\generate-sbom-provenance.ps1 -ExpectedHead (git rev-parse HEAD)
```

Run it only from a clean checkout of the exact candidate that is being reviewed. Generated evidence is written below:

```text
.runtime/security/supply-chain/<commit-sha>/
```

`.runtime` is ignored and generated evidence is not committed.

The generator uses Syft 1.50.0 through the immutable image:

```text
ghcr.io/anchore/syft@sha256:1288ea4c8b38767b4e620c1e312c8cb26b6e887a99b4f07ab6cd19fc6f225026
```

The executable Spring Boot JAR is catalogued as CycloneDX JSON. The generator validates CycloneDX metadata, the Syft version, a non-trivial component inventory, and package URLs.

## Exact candidate binding

The generator requires an explicit 40-character `ExpectedHead` and fails unless:

- the current Git HEAD exactly equals that value;
- the working tree is clean;
- `git diff --check` passes;
- `mvnw` remains committed with executable mode `100755`;
- Maven Wrapper remains 3.9.16 with its distribution SHA-256 pin;
- Java remains on major version 21;
- the reviewed immutable Docker builder and runtime base pins remain present.

The evidence records the commit SHA, Git tree SHA, project version, source-tree manifest hash, `pom.xml` hash, wrapper hashes, Dockerfile hash, dependency-tree hash, toolchain versions, artifact size/hash, checksum-file hash, SBOM hash, and the relationship between the SBOM and the exact JAR.

## Diagnostic evidence

Before the committed generator was introduced, CP5b was exercised against exact checkpoint:

```text
28d8ff195f4b76632ec620db81426540baf34674
```

That diagnostic resolved the same Syft 1.50.0 immutable image and produced a CycloneDX 1.7 SBOM with 130 components, 129 of them carrying package URLs. The diagnostic JAR was 100,566,919 bytes. Its artifact SHA-256 was:

```text
984e5df6461940215dfce9714cf66c3a4efcbbbb119379dff7f86de2b28cc0b6
```

The diagnostic SBOM SHA-256 was:

```text
06bac79e329b9a46fad106f8861080670f2a6c7bf3a54dcd0157e13ada8614d6
```

Those hashes describe that local diagnostic build only. They are **not** release-asset hashes and are not treated as immutable v0.16.0 publication evidence.

## Evidence boundary

The JSON file named `local-build-provenance.json` is intentionally local build-input evidence. It is not a SLSA attestation, not a signature, and not a claim of reproducible builds.

The generator explicitly records that it does **not** claim:

- GitHub-hosted workflow provenance;
- SLSA level or SLSA conformance;
- reproducible-build equivalence;
- artifact signing;
- provenance attestation;
- production certification;
- release publication.

GitHub-hosted CI and Docker Smoke results remain separate merge-gate evidence. A future release stage may record publication-specific evidence, but Increment 6 does not sign or publish v0.16.0.

## Secret and privacy boundary

Generated evidence contains hashes, dependency/package metadata, tool versions, and repository build inputs. The generator does not intentionally record credentials, access tokens, private keys, personal data, local home-directory paths, or environment-specific secret values.

The existing committed-content Gitleaks verifier remains a separate control:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\security\verify-gitleaks.ps1
```

## Interpretation

A successful run means a clean exact commit produced a locally built JAR, a CycloneDX SBOM, and a hash-linked local evidence manifest with the reviewed toolchain. It does not prove byte-for-byte reproducibility across hosts and does not replace dependency vulnerability review, secret scanning, exact-head CI, Docker Smoke, or protected-merge review.
