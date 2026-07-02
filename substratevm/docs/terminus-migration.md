# Project Terminus Migration Guide

This guide is for Native Image developers migrating build-time code toward Terminus-safe guest-context APIs.

For the broader design, see [Project Terminus](project-terminus.md).
For context and lifecycle terms, see [Project Terminus Terminology](terminus-terminology.md).

## Core Migration Rule

The primary API for working with the guest context is `GuestAccess`.

When migrating code for Terminus, prefer JVMCI- and `VMAccess`-based APIs over Java core reflection whenever the code interacts with guest-loaded types, methods, fields, modules, or invocation paths.

## Reflection And Metadata Replacements

| Core reflection / hosted API                                                                 | Terminus-safe replacement                                                                                | Notes |
| -------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | --- |
| `RuntimeReflection`                                                                          | `JVMCIRuntimeReflection`                                                                                 | Use Terminus-safe registration paths instead of host reflection state. |
| `RuntimeJNIAccess`                                                                           | `JVMCIRuntimeJNIAccess`                                                                                  | Prefer guest-aware access paths. |
| `RuntimeProxyCreation`                                                                       | `JVMCIRuntimeProxyCreation`                                                                              | Avoid assuming host reflection ownership. |
| `JNIAccess`                                                                                  | `JVMCIJNIAccess`                                                                                         | Same class name pattern with `JVMCI` prefix. |
| `AccessCondition`                                                                            | `JVMCIAccessCondition`                                                                                   | Use the JVMCI-aware variant. |
| `FeatureAccess.findClassByName()`                                                            | `InternalFeatureAccess.findTypeByName()`                                                                 | Use type lookup instead of host `Class<?>` lookup. |
| `Class.getDeclaredMethod()`                                                                  | `JVMCIReflectionUtil.getUniqueDeclaredMethod(...)`                                                       | Returns `null` when name and parameter types do not uniquely identify a method. |
| `Class.getDeclaredConstructor()`                                                             | `JVMCIReflectionUtil.getDeclaredConstructor(...)`                                                        | Same uniqueness caveat as method lookup. |
| `Class.getConstructors()`                                                                    | `JVMCIReflectionUtil.getConstructors(...)`                                                               | Keep lookup in JVMCI space. |
| `Class.getDeclaredMethods()`                                                                 | `ResolvedJavaType.getAllMethods(true)`                                                                   | The `true` value for `forceLink` is important so methods with bytecodes have non-null `ResolvedJavaMethod.getCode()`. |
| `Class.getDeclaredFields()`                                                                  | `JVMCIReflectionUtil.getAllFields(ResolvedJavaType)`                                                     | JVMCI includes internal fields (`ResolvedJavaField.isInternal()`), unlike core reflection. |
| `Class.getTypeName()`                                                                        | `JVMCIReflectionUtil.getTypeName(ResolvedJavaType)`                                                      | Direct type-name replacement. |
| `Class.getCanonicalName()`                                                                   | No direct replacement                                                                                    | Usually only needed for reporting; prefer suitable `JavaType` / `ResolvedJavaType` naming helpers. |
| `Class.getSimpleName()`                                                                      | `JavaType.toJavaName()`                                                                                  | Not a 1:1 semantic replacement; use mainly for reporting. |
| `Module`, `ModuleLayer`, `Package`                                                           | `ResolvedJavaModule`, `ResolvedJavaModuleLayer`, `ResolvedJavaPackage`                                   | These are the JVMCI-side equivalents used during Terminus migration. |
| `ModuleLayer.boot()`                                                                         | `JVMCIReflectionUtil.bootModuleLayer()`                                                                  | Use the JVMCI boot layer view. |
| `ModuleLayer.boot().findModule(moduleName)`                                                  | `JVMCIReflectionUtil.bootModuleLayer().findModule(moduleName)`                                           | Common in `afterRegistration`; transitional and expected to become less important after migration. |
| `clazz.getProtectionDomain().getCodeSource().getLocation()`                                  | `JVMCIReflectionUtil.getOrigin(type)`                                                                    | Use JVMCI type origin lookup. |
| `Class.isAssignableFrom(Class)`                                                              | `ResolvedJavaType.isAssignableFrom(ResolvedJavaType)` or `ReflectionUtil.isAssignableFrom(Class, Class)` | Use `ResolvedJavaType` for runtime or guest types; `ReflectionUtil` is acceptable for known hosted literals when satisfying `VerifyReflectionUsage`. |
| `ReflectionUtil.invokeMethod(...)`, `Method.invoke(...)`, and ordinary reflective invocation | `GuestAccess.get().invoke(...)`                                                                          | Preferred invocation path for guest methods. |

## Moving Code From Host/Core To Guest Or Shared Modules

When moving runtime code from host/core modules into `com.oracle.svm.shared`,
`com.oracle.svm.guest.staging`, or `com.oracle.svm.guest`, adapt APIs and comments that
were only valid because the code previously lived with hosted or compiler dependencies.

### Package And Dependency Shape

| Host/core pattern | Guest/shared adaptation | Notes |
| --- | --- | --- |
| Moving `com.oracle.svm.core...` code to guest staging | Insert `guest.staging` after `com.oracle.svm`, e.g., `com.oracle.svm.core.memory` to `com.oracle.svm.guest.staging.core.memory` | Preserve the original package suffix so moves stay mechanical and match prior Terminus refactorings. |
| Javadoc `{@link ...}` to a target that is no longer visible after the move | Use `{@code ...}` | Preserve the referenced name in Javadoc without adding guest/shared dependencies only to keep documentation links resolvable. |

### Builder-To-Guest API Adaptations

Moving code from the builder into guest staging is not only a package rename.
Code must stop depending on builder-only helpers and compiler-only annotations unless those dependencies are valid in the new module.

| Builder/core pattern | Guest/shared adaptation | Notes |
| --- | --- | --- |
| `jdk.graal.compiler.api.replacements.Fold` | `com.oracle.svm.shared.meta.GuestFold` | Use for Fold-like constants in runtime/guest-side code without adding a `jdk.graal.compiler` dependency. Folding is performed in the guest context. |
| `@AutomaticallyRegisteredImageSingleton` on a class moved to guest staging | Manual guest singleton installation | Do not use the annotation for guest/staging classes for now; install the guest singleton manually. GR-76880 tracks evidence for whether automatic registration support is worth implementing, so this guidance might change later. |
| Builder singleton installed in the guest through `GuestAccess.createCallback(...)` | Shared provider interface plus builder and guest helper classes | Keep the builder-facing helper in its original module when hosted code still uses it. Register the callback under the shared provider key, such as `ImageLayerBuildingSupportProvider`, so guest code can look it up through `ImageSingletons`. |
| Guest code must call a builder-owned service, such as `UserError.abort(...)` | Shared service interface plus a builder implementation installed in the guest as a host proxy | Register the builder implementation in the builder `ImageSingletons`; for a fully isolated guest, use `GuestAccess.createHostProxy(...)` and `GuestImageSingletonSupport.add(...)` under the shared interface key. Host proxies cannot pass a guest `Object[]` directly to a host `Object[]`; accept it as a `JavaConstant` in the host proxy target and convert it with `GuestAccess.asHostObject(...)`. |
| Static builder helper such as `ImageLayerBuildingSupport` | Guest helper such as `GuestImageLayerBuildingSupport` | Use the guest helper from moved code so the moved code does not depend on builder-only packages. |

### Preserve Guest Module Encapsulation

Never add an `opens` edge from `SVM_GUEST` or `SVM_GUEST_STAGING` to `org.graalvm.nativeimage.builder`.
If builder code appears to require reflective access to a guest or guest-staging package, treat that requirement as a boundary-design red flag.
Keep the operation in the guest context and expose a narrow guest-aware API or bridge instead.

For reviewability, prefer splitting broad moves into one commit for the real code movement and
non-mechanical adaptations, followed by a second commit for mechanical import and reference
adjustments caused by the move. The final tree should be validated as a whole; the split is meant to
show reviewers which diff carries semantic risk and which diff is import fallout.

## Practical Guidance

### Prefer Guest-Context APIs Over Host Reflection

Under Terminus, code frequently operates on guest-loaded types that do not have a reliable or meaningful host `Class<?>` counterpart.
That means reflection-based code is often either incorrect or only accidentally correct while running in host mode.

Use:

- `GuestAccess.get()` for guest-context operations
- `JVMCIReflectionUtil` for reflection-like helpers
- `ResolvedJavaType`, `ResolvedJavaMethod`, and `ResolvedJavaField` for type and member reasoning
- `VMAccess` and constant-reflection APIs when translating guest objects into build-time metadata

### Use JVMCI Feature Access Methods Through Access Implementations

When migrating an `InternalFeature`, keep the lifecycle method signatures typed with the normal `Feature.*Access` interfaces for now.
If the feature needs a JVMCI-based operation, cast the access object to the matching `FeatureImpl.*AccessImpl` class and call the method provided by the corresponding `JVMCIFeatureAccess.*Access` interface.

For example, prefer:

```java
@Override
public void beforeAnalysis(BeforeAnalysisAccess access) {
    FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
    ResolvedJavaType type = accessImpl.findTypeByName("com.example.Foo");
    accessImpl.registerAsInHeap(type);
}
```

over changing the feature method signature to use `JVMCIFeatureAccess.BeforeAnalysisAccess` directly.

The `FeatureImpl.*AccessImpl` classes implement both the public `Feature.*Access` interface and the matching `JVMCIFeatureAccess.*Access` interface.
This lets internal features gradually replace reflection types such as `Class<?>`, `Field`, `Method`, and `Constructor` with JVMCI types such as `ResolvedJavaType`, `ResolvedJavaField`, and `ResolvedJavaMethod`, while migrated and non-migrated features keep the same lifecycle shape.

Once the migration is complete and `InternalFeature` no longer extends the core-reflection-based `Feature` interface, most casts to `FeatureImpl.*AccessImpl` should become cleanup work.

### Be Careful With Lookup Semantics

Several JVMCI replacements are intentionally stricter than reflection lookups.
For example, unique-declaration helpers can return `null` if a method name and parameter list do not resolve unambiguously.
Migration work should handle that explicitly rather than assuming reflective lookup semantics.

### Migrate Module Access

The migration guide treats module and package APIs as part of the same transition.
If code currently reasons in terms of `Module`, `ModuleLayer`, or `Package`, move it toward:

- `ResolvedJavaModule`
- `ResolvedJavaModuleLayer`
- `ResolvedJavaPackage`
- `JVMCIReflectionUtil.bootModuleLayer()`

### Keep Invocation Guest-Aware

If existing hosted code uses `Method.invoke(...)` or helper wrappers around core reflection, replace that with `GuestAccess.get().invoke(...)` so the invocation stays within the intended guest-context model.

## Migration Checklist

- Identify whether the code is operating on guest-loaded types or known hosted types.
- Use `JVMCIFeatureAccess.*Access` methods through the matching `FeatureImpl.*AccessImpl` cast when migrating feature access operations.
- Replace core reflection lookups with `JVMCIReflectionUtil` or JVMCI metadata APIs.
- Replace class-based assignability checks with `ResolvedJavaType.isAssignableFrom(...)` when dealing with guest/runtime types.
- Replace reflective invocation with `GuestAccess.get().invoke(...)`.
- Replace module-layer queries with JVMCI module-layer APIs.
- Document and handle cases where JVMCI helpers can return `null` instead of assuming reflection-style success.
- Confirm that guest and guest-staging packages are not open to the builder; redesign the boundary instead of adding an `opens` edge.
