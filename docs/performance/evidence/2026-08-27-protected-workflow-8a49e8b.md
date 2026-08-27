# Accepted v1.0.0 protected-workflow performance evidence

> **ACCEPTED** for the v1.0.0 CP4 bounded developer-workstation
> release-budget review under issue #197.
>
> Source candidate run: `20260827T152405Z`.
> Source candidate Markdown SHA-256: `183cfb6a31f410d9099c95ea3a02d76d44ce672cf0362a04fc04415342eaa994`.
> Source candidate JSON SHA-256: `52c720bf0e458634153fd09f31a0fb8a4bb255106214474cc3871d33584bd351`.
>
> This evidence is not a production SLO, production-capacity certification,
> regulatory certification, or deployment-capacity guarantee.

- Git commit: 8a49e8bcd05ba64fd07b87bdeca5dc415e87dda3
- Generated UTC: 2026-08-27T15:34:39.6815658Z
- Host OS: Microsoft Windows NT 10.0.26200.0
- Docker server: 29.6.1
- Docker environment: Docker Desktop; 16 CPU; 7.61 GiB
- Java runtime: openjdk 21.0.12 2026-07-21 LTS
- k6 runtime: k6 v2.1.0 (commit/83a87a41e2, go1.26.4, linux/amd64)
- Compose: compose.yml + performance/k6/compose.yml; profiles app, loadtest
- Abuse protection: enabled
- Dataset: disposable example.invalid identities; isolated Redis reset before each independent phase

| Phase | Target it/s | Seconds | Requests | p50 ms | p95 ms | p99 ms | Unexpected | Dropped | Allowed | Blocked client | Saturated |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| warmup | 5 | 30 | 151 | 8.66 | 22.10 | 345.65 | 0.000% | 0 | 20 | 131 | False |
| steady | 10 | 120 | 1201 | 4.23 | 9.67 | 14.34 | 0.000% | 0 | 20 | 1181 | False |
| saturation-10 | 10 | 60 | 601 | 3.65 | 6.49 | 8.24 | 0.000% | 0 | 20 | 581 | False |
| saturation-20 | 20 | 60 | 1201 | 3.26 | 5.91 | 8.58 | 0.000% | 0 | 20 | 1181 | False |
| saturation-40 | 40 | 60 | 2401 | 3.03 | 6.04 | 7.31 | 0.000% | 0 | 20 | 2381 | False |
| saturation-80 | 80 | 60 | 4801 | 2.21 | 4.81 | 6.14 | 0.000% | 0 | 20 | 4781 | False |
| overload | 120 | 60 | 7201 | 2.11 | 4.37 | 5.30 | 0.000% | 0 | 20 | 7181 | False |

Steady-state accepted: **True**.
First saturation: **not observed through 80 iterations/s**.
Overload rate: **120 iterations/s**.
Recovery: **0.038 seconds**, within the frozen 30-second budget.

## Limitations

- Developer-workstation evidence only; this is not a production capacity certification.
- The representative account-action workflow becomes client-policy-limited after the reviewed twenty-decision boundary in each independently reset phase.
- Each phase resets only the isolated Redis test state before measurement; application and dependency containers otherwise remain unchanged.
- Results are not directly comparable across materially different Docker, CPU, memory, Java, or k6 environments.
- Raw k6 summaries remain ignored under performance/results/ and are not promoted automatically.
