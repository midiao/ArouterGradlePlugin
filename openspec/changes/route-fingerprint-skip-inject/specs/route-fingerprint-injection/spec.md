## ADDED Requirements

### Requirement: Stable route metadata output
The plugin SHALL generate stable route metadata artifacts for each Android variant so route changes can be detected independently from unrelated business class changes.

#### Scenario: First successful metadata collection generates artifacts
- GIVEN an application variant that applies `io.github.JailedBird.ARouterPlugin`
- WHEN `CollectRouteMetadataTask` scans the variant project class outputs and compile classpath
- THEN the plugin generates `route-metadata.txt` under `build/intermediates/arouter/<variant>/`
- AND the plugin generates `route-index.json` under the same variant directory
- AND the plugin generates `route-scan-state.txt` under the same variant directory
- AND the plugin generates `route-fingerprint.txt` under the same variant directory

### Requirement: Incremental route scan cache
The plugin SHALL reuse persisted scan state so non-route code changes do not force a full rescan of every route input.

#### Scenario: Non-route class changes in project output
- GIVEN a prior successful metadata collection for a variant has produced `route-scan-state.txt`
- AND a subsequent build changes only a class outside `com/alibaba/android/arouter/routes/`
- WHEN `CollectRouteMetadataTask` runs again
- THEN the plugin updates route scan state incrementally
- AND the plugin logs `incremental=true`
- AND the aggregated route metadata remains unchanged

#### Scenario: A route class is modified or removed
- GIVEN a prior successful metadata collection for a variant has produced `route-scan-state.txt`
- WHEN a class under `com/alibaba/android/arouter/routes/` is modified or removed
- THEN the plugin updates the affected source entry in scan state
- AND the regenerated route metadata reflects the new route set

### Requirement: Targeted LogisticsCenter instrumentation by default
When `arouter_config.onlyInjectWhenRouteChanged = true`, the plugin SHALL instrument only `com.alibaba.android.arouter.core.LogisticsCenter` through AGP Instrumentation API instead of running a custom aggregate classes transform.

#### Scenario: Default route-changed mode instruments only LogisticsCenter
- GIVEN `arouter_config.onlyInjectWhenRouteChanged = true`
- WHEN the variant registers ASM instrumentation
- THEN the plugin uses `variant.instrumentation.transformClassesWith(...)`
- AND the instrumentation scope is `ALL`
- AND `AsmClassVisitorFactory.isInstrumentable(...)` returns true only for `com.alibaba.android.arouter.core.LogisticsCenter`

#### Scenario: Independent route collection avoids CLASSES cycle
- GIVEN the plugin needs route metadata before ASM instrumentation runs
- WHEN the plugin wires the default Phase 3 pipeline
- THEN the route collection task reads project compilation outputs and compile classpath directly
- AND the plugin does not use `toGet(ScopedArtifact.CLASSES)` for that metadata collection step

### Requirement: Compatible fallback for always-regenerate mode
The plugin SHALL keep a compatible fallback path for builds that explicitly disable route-changed reuse.

#### Scenario: Disabling route-changed reuse falls back to legacy transform
- GIVEN `arouter_config.onlyInjectWhenRouteChanged = false`
- WHEN the plugin configures the variant
- THEN the plugin registers the legacy `TransformAllClassesTask`
- AND the plugin keeps the previous aggregate transform behavior that always regenerates injected output

### Requirement: Configurable route fingerprint logging
The plugin SHALL expose configuration for optional route fingerprint logging.

#### Scenario: Enabling route fingerprint logging
- GIVEN `arouter_config.logRouteFingerprint = true`
- WHEN the route metadata task computes the route fingerprint
- THEN the plugin logs the current variant fingerprint and route-index output path
