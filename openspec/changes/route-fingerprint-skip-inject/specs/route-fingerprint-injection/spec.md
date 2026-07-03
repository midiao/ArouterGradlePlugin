## ADDED Requirements

### Requirement: Stable route metadata output
The plugin SHALL generate stable route metadata artifacts for each Android variant so route changes can be detected independently from unrelated class changes.

#### Scenario: First successful transform generates metadata
- GIVEN an application variant that applies `io.github.JailedBird.ARouterPlugin`
- WHEN `TransformAllClassesTask` finishes scanning route classes and locates `LogisticsCenter.class`
- THEN the plugin generates `route-metadata.txt` under `build/intermediates/arouter/<variant>/`
- AND the plugin generates `route-index.json` under the same variant directory
- AND the plugin generates `route-scan-state.txt` under the same variant directory
- AND the plugin generates `route-fingerprint.txt` under the same variant directory

### Requirement: Incremental route scan cache
The plugin SHALL reuse persisted scan state so non-route code changes do not force a full rescan of every route input.

#### Scenario: Non-route class changes in project output
- GIVEN a prior successful transform for a variant has produced `route-scan-state.txt`
- AND a subsequent build changes only a class outside `com/alibaba/android/arouter/routes/`
- WHEN `TransformAllClassesTask` runs again
- THEN the plugin updates route scan state incrementally
- AND the plugin logs `incremental=true`
- AND the aggregated route metadata remains unchanged

#### Scenario: A route class is modified or removed
- GIVEN a prior successful transform for a variant has produced `route-scan-state.txt`
- WHEN a class under `com/alibaba/android/arouter/routes/` is modified or removed
- THEN the plugin updates the affected source entry in scan state
- AND the regenerated route metadata reflects the new route set

### Requirement: Reuse injected LogisticsCenter when route fingerprint is unchanged
The plugin SHALL reuse the previously generated injected `LogisticsCenter.class` when route metadata and the original `LogisticsCenter.class` remain unchanged.

#### Scenario: Re-running transform with unchanged routes
- GIVEN a prior successful transform for a variant has produced `route-fingerprint.txt`, `last-applied-fingerprint.txt`, and `LogisticsCenter.injected.class`
- AND the current scan produces the same route fingerprint
- WHEN the transform task runs again
- THEN the plugin reuses the cached injected `LogisticsCenter.class`
- AND the plugin skips running the ASM injection step again

#### Scenario: Route-related inputs change
- GIVEN a prior successful transform for a variant has produced cached route metadata
- WHEN the current route fingerprint differs from `last-applied-fingerprint.txt`
- THEN the plugin regenerates the injected `LogisticsCenter.class`
- AND the plugin updates the cached fingerprint and injected class output

### Requirement: Configurable route-fingerprint behavior
The plugin SHALL expose configuration to enable or disable route-fingerprint reuse and optional debug logging.

#### Scenario: Disabling route-fingerprint reuse
- GIVEN `arouter_config.onlyInjectWhenRouteChanged = false`
- WHEN the transform task runs
- THEN the plugin always regenerates the injected `LogisticsCenter.class`

#### Scenario: Enabling route fingerprint logging
- GIVEN `arouter_config.logRouteFingerprint = true`
- WHEN the transform task computes the route fingerprint
- THEN the plugin logs the current variant fingerprint and route-index output path
