# Dependency review — 2026-10-23

The 1.5.0 review covers direct declarations, resolved Android/desktop/test Maven
components, Gradle plugin classpaths, Gradle itself and GitHub Actions. Version
metadata comes from Google Maven, Maven Central and official release endpoints.

| Direct dependency / tool | Selected stable version |
| --- | --- |
| Gradle | 9.8.2, distribution and wrapper SHA-256 verified |
| Android Gradle Plugin | 9.4.1 |
| Kotlin and Compose compiler plugin | 2.4.21 |
| Compose Multiplatform | 1.12.2 |
| Android compile / target / minimum SDK | 37 / 36 / 31 |
| Material Components | 1.14.0 |
| WorkManager | 2.12.0 |
| AndroidX Core | 1.19.1 |
| AndroidX Browser | 1.10.0 |
| SwipeRefreshLayout | 1.2.0 |
| desugar_jdk_libs | 2.1.5 |
| JSON-java, all JVM modules | 20260814 |
| kotlinx.coroutines core and Swing | 1.11.0 |
| JUnit 4 | 4.13.2 |
| Android test runner / rules | 1.7.0 |
| Espresso core / intents | 3.7.0 |
| AndroidX test JUnit | 1.3.0 |
| checkout / setup-java / upload-artifact | 7.0.1 / 6.1.0 / 7.0.2 |
| CodeQL action | 4.39.1 |

Actions remain pinned to full commit SHAs. CI requests the latest available
Temurin 21 patch. Desktop distributions embed the JDK selected by CI. Python
scripts use the standard library; there is no production npm dependency tree.

## Vulnerability fixes

The initial OSV scan found seven advisories in five Maven components, all in
the root build plugin classpath. These components were absent from the checked
application runtime classpaths. Root buildscript constraints upgrade their
implementations without adding them to the app.

| Build component | Before → after | OSV / GitHub advisory |
| --- | --- | --- |
| Commons Lang | 3.16.0 → 3.21.0 | [GHSA-j288-q9x7-2f5v](https://osv.dev/vulnerability/GHSA-j288-q9x7-2f5v) |
| jose4j | 0.9.5 → 0.9.7 | [GHSA-3677-xxcr-wjqv](https://osv.dev/vulnerability/GHSA-3677-xxcr-wjqv) |
| Bouncy Castle provider | 1.80.2 → 1.86 | [GHSA-9pwp-9qqc-pr26](https://osv.dev/vulnerability/GHSA-9pwp-9qqc-pr26), [GHSA-c3fc-8qff-9hwx](https://osv.dev/vulnerability/GHSA-c3fc-8qff-9hwx), [GHSA-qp49-qgx5-5m26](https://osv.dev/vulnerability/GHSA-qp49-qgx5-5m26) |
| Bouncy Castle PKIX | 1.80.2 → 1.86 | [GHSA-wg6q-6289-32hp](https://osv.dev/vulnerability/GHSA-wg6q-6289-32hp) |
| JDOM | 2.0.6 → 2.0.6.1 | [GHSA-2363-cqg2-863c](https://osv.dev/vulnerability/GHSA-2363-cqg2-863c) |

Bouncy Castle utilities are aligned to 1.86. The subsequent OSV scan checked
**363 unique Maven package/version pairs with zero affected components**.
The GitHub Dependabot API reported zero open alerts; OSV also returned no
advisories for the four selected GitHub Actions versions.

## Reproduce and inspect

With JDK 21 and the Android SDK configured:

```powershell
python scripts/audit_dependencies.py
```

The script resolves application, test, desugaring and build classpaths, fails
on unresolved dependencies or incomplete OSV responses, and exits nonzero for
known advisories. Only public package coordinates are sent to OSV. CI runs the
same audit. Detailed reports are written under ignored `work/dependency-audit`.

- [Selected versions and latest stable metadata for all 347 packages](docs/dependency-versions.json)
- [OSV results for all 363 selected versions](docs/dependency-audit.json)

Transitive packages follow the compatible sets selected by AGP, Compose and
AndroidX, with explicit fixes for the affected build packages. Some upstream
sets contain earlier versions or previews, so a newer metadata entry alone
does not mean the selected version is vulnerable. The version inventory records
these differences. OSV and Dependabot cover known published advisories; they
do not establish the absence of undisclosed vulnerabilities or audit application
logic, the operating system, or every native binary bundled by upstream tools.
