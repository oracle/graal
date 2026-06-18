# Project Terminus Migration Guide

This guide is for Native Image developers migrating build-time code away from host reflection and toward Terminus-safe guest-context APIs.

For the broader design, see [Project Terminus](project-terminus.md).
For context and lifecycle terms, see [Project Terminus Terminology](terminus-terminology.md).

## Core Migration Rule

The primary API for working with the guest context is `GuestAccess`.

When migrating code for Terminus, prefer JVMCI- and `VMAccess`-based APIs over Java core reflection whenever the code interacts with guest-loaded types, methods, fields, modules, or invocation paths.

## API Replacements

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
| `BootLoader.packages()`                                                                      | `JVMCIReflectionUtil.bootLoaderPackages()`                                                               | JVMCI-aware boot loader package access. |
| `Class.isAssignableFrom(Class)`                                                              | `ResolvedJavaType.isAssignableFrom(ResolvedJavaType)` or `ReflectionUtil.isAssignableFrom(Class, Class)` | Use `ResolvedJavaType` for run-time or guest types; `ReflectionUtil` is acceptable for known hosted literals. |
| `ReflectionUtil.invokeMethod(...)`, `Method.invoke(...)`, and ordinary reflective invocation | `GuestAccess.get().invoke(...)`                                                                          | Preferred invocation path for guest methods. |

## Moving code from host/core to guest or shared modules

When moving runtime code from host/core modules into `com.oracle.svm.shared`,
`com.oracle.svm.guest.staging`, or `com.oracle.svm.guest`, adapt APIs and comments that
were only valid because the code previously lived with hosted or compiler dependencies.

| Host/core pattern | Guest/shared adaptation | Notes |
| --- | --- | --- |
| Moving `com.oracle.svm.core...` code to guest staging | Insert `guest.staging` after `com.oracle.svm`, e.g., `com.oracle.svm.core.memory` to `com.oracle.svm.guest.staging.core.memory` | Preserve the original package suffix so moves stay mechanical and match prior Terminus refactorings. |
| `jdk.graal.compiler.api.replacements.Fold` | `com.oracle.svm.shared.meta.GuestFold` | Use for Fold-like constants in runtime/guest-side code without adding a `jdk.graal.compiler` dependency. Folding is performed in the guest context. |
| Javadoc `{@link ...}` to a target that is no longer visible after the move | Use `{@code ...}` | Preserve the referenced name in Javadoc without adding guest/shared dependencies only to keep documentation links resolvable. |

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
- Replace core reflection lookups with `JVMCIReflectionUtil` or JVMCI metadata APIs.
- Replace class-based assignability checks with `ResolvedJavaType.isAssignableFrom(...)` when dealing with guest/runtime types.
- Replace reflective invocation with `GuestAccess.get().invoke(...)`.
- Replace module-layer queries with JVMCI module-layer APIs.
- Document and handle cases where JVMCI helpers can return `null` instead of assuming reflection-style success.
