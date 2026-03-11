# Project Terminus Migration Guide

This document captures the **Project Terminus Migration Guide**.
It is intended for Native Image developers migrating build-time code away from host reflection and toward Terminus-safe guest-context APIs.

## Core migration rule

The primary API for working with the guest context is `GuestAccess`.

When migrating code for Terminus, prefer JVMCI- and VMAccess-based APIs over Java core reflection whenever the code interacts with guest-loaded types, methods, fields, modules, or invocation paths.

## API replacements

| Core reflection / hosted API                                                                 | Terminus-safe replacement                                                                                | Notes |
| -------------------------------------------------------------------------------------------- |----------------------------------------------------------------------------------------------------------| --- |
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
| `Class.isAssignableFrom(Class)`                                                              | `ResolvedJavaType.isAssignableFrom(ResolvedJavaType)` or `ReflectionUtil.isAssignableFrom(Class, Class)` | Use `ResolvedJavaType` for runtime or guest types; `ReflectionUtil` is acceptable for known hosted literals when satisfying `VerifyReflectionUsage`. |
| `ReflectionUtil.invokeMethod(...)`, `Method.invoke(...)`, and ordinary reflective invocation | `GuestAccess.get().invoke(...)`                                                                          | Preferred invocation path for guest methods. |

## Practical guidance

### Prefer guest-context APIs over host reflection

Under Terminus, code frequently operates on guest-loaded types that do not have a reliable or meaningful host `Class<?>` counterpart.
That means reflection-based code is often either incorrect or only accidentally correct while running in host mode.

Use:

- `GuestAccess.get()` for guest-context operations
- `JVMCIReflectionUtil` for reflection-like helpers
- `ResolvedJavaType`, `ResolvedJavaMethod`, and `ResolvedJavaField` for type and member reasoning
- VMAccess and constant-reflection APIs when translating guest objects into build-time metadata

### Be careful with lookup semantics

Several JVMCI replacements are intentionally stricter than reflection lookups.
For example, unique-declaration helpers can return `null` if a method name and parameter list do not resolve unambiguously.
Migration work should handle that explicitly rather than assuming reflective lookup semantics.

### Module migration is part of Terminus migration

The migration guide treats module and package APIs as part of the same transition.
If code currently reasons in terms of `Module`, `ModuleLayer`, or `Package`, move it toward:

- `ResolvedJavaModule`
- `ResolvedJavaModuleLayer`
- `ResolvedJavaPackage`
- `JVMCIReflectionUtil.bootModuleLayer()`

### Invocation should stay guest-aware

If existing hosted code uses `Method.invoke(...)` or helper wrappers around core reflection, replace that with `GuestAccess.get().invoke(...)` so the invocation stays within the intended guest-context model.

## Migration checklist

- Identify whether the code is operating on guest-loaded types or known hosted types.
- Replace core reflection lookups with `JVMCIReflectionUtil` or JVMCI metadata APIs.
- Replace class-based assignability checks with `ResolvedJavaType.isAssignableFrom(...)` when dealing with guest/runtime types.
- Replace reflective invocation with `GuestAccess.get().invoke(...)`.
- Replace module-layer queries with JVMCI module-layer APIs.
- Document and handle cases where JVMCI helpers can return `null` instead of assuming reflection-style success.
