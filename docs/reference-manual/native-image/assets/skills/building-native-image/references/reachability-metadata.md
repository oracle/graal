# Reachability Metadata — GraalVM Native Image

## Table of Contents

1. [Diagnosing the Error Type](#1-diagnosing-the-error-type)
2. [Where to Put Metadata Files](#2-where-to-put-metadata-files)
3. [Reflection Metadata](#3-reflection-metadata)
4. [JNI Metadata](#4-jni-metadata)
5. [Resource Metadata](#5-resource-metadata)
6. [Serialization Metadata](#6-serialization-metadata)
7. [Conditional Metadata Entries](#7-conditional-metadata-entries)
8. [Debugging Tips](#8-debugging-tips)
9. [Full Sample reachability-metadata.json](#9-full-sample-reachability-metadatajson)


## 1. Diagnosing the Error Type

Match the runtime error to the metadata section you need to fix:

| Runtime Error | Root Cause | Fix In Section |
|---|---|---|
| `NoClassDefFoundError` | Class not included in binary | [Reflection Metadata](#4-reflection-metadata) — register the type |
| `MissingReflectionRegistrationError` | Reflective access to unregistered class/method/field | [Reflection Metadata](#4-reflection-metadata) |
| `NoSuchMethodException` | Method not registered for reflective invocation | [Reflection Metadata — Methods](#methods) |
| `NoSuchFieldException` | Field not registered for reflective access | [Reflection Metadata — Fields](#fields) |
| `MissingJNIRegistrationError` | JNI lookup of unregistered type/member | [JNI Metadata](#5-jni-metadata) |
| `MissingForeignRegistrationError` | FFM downcall/upcall without registered descriptor | Foreign section (advanced, see GraalVM docs) |
| `MissingResourceException` | Resource bundle not included | [Resource Metadata — Bundles](#resource-bundles) |


**Quick diagnostic command** — run the app with warning mode to see all missing registrations without crashing:
```shell
java -XX:MissingRegistrationReportingMode=Warn -jar your-app.jar
```
Use `Exit` mode during testing to catch errors hidden inside `catch (Throwable t)` blocks:
```shell
java -XX:MissingRegistrationReportingMode=Exit -jar your-app.jar
```
Enable strict metadata mode at build time:
```shell
native-image --exact-reachability-metadata ...
# Or for specific packages only:
native-image --exact-reachability-metadata=com.example.mypackage ...
```

---

## 2. Where to Put Metadata Files

All metadata lives in a single JSON file on the classpath:

```
src/main/resources/
└── META-INF/
    └── native-image/
        └── <groupId>/
            └── <artifactId>/
                └── reachability-metadata.json
```

The file contains a top-level object with one key per metadata type:
```json
{
  "reflection": [],
  "resources": []
}
```

> **Alternative approaches (when JSON isn't enough):**
> - Pass constant arguments to `Class.forName("Foo")`, `getMethod(...)`, etc. — native-image evaluates these at build time automatically.
> - Use `-H:Preserve=<package>` to preserve entire packages.

---

## 3. Reflection Metadata

### Register a Type (fixes `NoClassDefFoundError`, `MissingReflectionRegistrationError`)

```json
{
  "reflection": [
    {
      "type": "com.example.MyClass"
    }
  ]
}
```

This allows `Class.forName("com.example.MyClass")` and reflective lookups to find the type.

### Methods

Fixes `NoSuchMethodError` and `MissingReflectionRegistrationError` on `Method.invoke()` or `Constructor.newInstance()`.

**Register specific methods:**
```json
{
  "type": "com.example.MyClass",
  "methods": [
    { "name": "myMethod", "parameterTypes": ["java.lang.String", "int"] },
    { "name": "<init>", "parameterTypes": [] }
  ]
}
```
> Use `"<init>"` for constructors.

**Register all methods (less precise, larger binary):**
```json
{
  "type": "com.example.MyClass",
  "allDeclaredMethods": true,
  "allPublicMethods": true,
  "allDeclaredConstructors": true,
  "allPublicConstructors": true
}
```
- `allDeclared*` — methods/constructors declared directly on this type
- `allPublic*` — all public methods/constructors including those inherited from supertypes

### Fields

Fixes `NoSuchFieldException` and `MissingReflectionRegistrationError` on `Field.get()` / `Field.set()`.

**Register specific fields:**
```json
{
  "type": "com.example.MyClass",
  "fields": [
    { "name": "myField" },
    { "name": "anotherField" }
  ]
}
```

**Register all fields:**
```json
{
  "type": "com.example.MyClass",
  "allDeclaredFields": true,
  "allPublicFields": true
}
```

### Dynamic Proxies

For classes obtained via `Proxy.newProxyInstance(...)` — the type is the proxy's interface list:
```json
{
  "type": {
    "proxy": ["com.example.IFoo", "com.example.IBar"]
  }
}
```
> The interface order matters — it must match the order passed to `Proxy.newProxyInstance`.

### Unsafe Allocation

For `Unsafe.allocateInstance(MyClass.class)`:
```json
{
  "type": "com.example.MyClass",
  "unsafeAllocated": true
}
```

### Full Type Entry Reference

```json
{
  "condition": { "typeReached": "com.example.TriggerClass" },
  "type": "com.example.MyClass",
  "fields": [{ "name": "fieldName" }],
  "methods": [{ "name": "methodName", "parameterTypes": ["java.lang.String"] }],
  "allDeclaredConstructors": true,
  "allPublicConstructors": true,
  "allDeclaredMethods": true,
  "allPublicMethods": true,
  "allDeclaredFields": true,
  "allPublicFields": true,
  "unsafeAllocated": true,
  "serializable": true
}
```

---

## 4. JNI Metadata

Used when native C/C++ code calls back into Java via JNI. Fixes `MissingJNIRegistrationError`.

> Most JNI libraries don't handle Java exceptions gracefully — always use `--exact-reachability-metadata` with `-XX:MissingRegistrationReportingMode=Warn` to see what's missing.

**Register a JNI-accessible type:**
```json
{
  "reflection": [
    {
      "type": "com.example.MyClass",
      "jniAccessible": true
    }
  ]
}
```

**Add fields and methods for JNI access:**
```json
{
  "type": "com.example.MyClass",
  "jniAccessible": true,
  "fields": [{ "name": "value" }],
  "methods": [
    { "name": "callback", "parameterTypes": ["int"] }
  ],
  "allDeclaredConstructors": true
}
```

JNI metadata follows the same `allDeclared*` / `allPublic*` convenience flags as reflection.

---

## 5. Resource Metadata

### Embed Resources (fixes missing `getResourceAsStream` results)

Resources are specified using glob patterns in the `resources` array:

```json
{
  "resources": [
    { "glob": "config/app.properties" },
    { "glob": "templates/**" },
    { "glob": "**/Resource*.txt" }
  ]
}
```

**Glob rules:**
- `*` matches any characters on one path level
- `**` matches any characters across multiple levels
- No trailing slash, no empty levels, no `***`

**Examples:**
```json
{ "glob": "config/app.properties" }        // exact file
{ "glob": "**/**.json" }                   // all JSON files anywhere
{ "glob": "static/images/*.png" }          // all PNGs in one directory
```

> **Note:** `Class.getResourceAsStream("plan.txt")` with a class literal and string literal is auto-detected by native-image — no JSON needed for those cases.

### Resources from a Specific Module

```json
{
  "resources": [
    {
      "module": "library.module",
      "glob": "resource-file.txt"
    }
  ]
}
```

### Resource Bundles

Fixes `MissingResourceException` from `ResourceBundle.getBundle(...)`.

```json
{
  "resources": [
    { "bundle": "com.example.Messages" },
    { "bundle": "com.example.Errors" }
  ]
}
```

With a specific module:
```json
{
  "resources": [
    { "module": "app.module", "bundle": "com.example.Messages" }
  ]
}
```

Bundles are included for all locales embedded in the image. To control locales:
```shell
native-image -Duser.country=US -Duser.language=en -H:IncludeLocales=fr,de
# or include everything:
native-image -H:+IncludeAllLocales
```

---

## 6. Serialization Metadata

Fixes `InvalidClassException`, serialization `StreamCorruptedException`, or `ClassNotFoundException` during `ObjectInputStream.readObject()`.

### In JSON

```json
{
  "reflection": [
    {
      "type": "com.example.MySerializableClass",
      "serializable": true
    }
  ]
}
```

### Via Code (auto-detected)

If you use `ObjectInputFilter`, native-image detects this automatically when the pattern is a constant:
```java
var filter = ObjectInputFilter.Config.createFilter("com.example.MyClass;!*;");
objectInputStream.setObjectInputFilter(filter);
```

### Proxy Serialization

```json
{
  "reflection": [
    {
      "type": {
        "proxy": ["com.example.IFoo"],
        "serializable": true
      }
    }
  ]
}
```

---

## 7. Conditional Metadata Entries

Use conditions to avoid bloating the binary with metadata for code paths that may never run.

```json
{
  "condition": {
    "typeReached": "com.example.FeatureModule"
  },
  "type": "com.example.OptionalClass",
  "allDeclaredMethods": true
}
```

The metadata for `OptionalClass` is only *active at runtime* once `FeatureModule` has been initialized. It is still *included at build time* if `FeatureModule` is reachable during static analysis.

**A type is "reached" right before its static initializer runs**, or when any of its subtypes are reached.

> Use conditions liberally on third-party library metadata to keep binary size reasonable.

---

## 9. Full Sample reachability-metadata.json

```json
{
  "reflection": [
    {
      "condition": { "typeReached": "com.example.App" },
      "type": "com.example.MyClass",
      "fields": [
        { "name": "myField" }
      ],
      "methods": [
        { "name": "myMethod", "parameterTypes": ["java.lang.String"] },
        { "name": "<init>", "parameterTypes": [] }
      ],
      "allDeclaredConstructors": true,
      "allPublicConstructors": true,
      "allDeclaredFields": true,
      "allPublicFields": true,
      "allDeclaredMethods": true,
      "allPublicMethods": true,
      "unsafeAllocated": true,
      "serializable": true
    },
    {
      "type": {
        "proxy": ["com.example.IFoo", "com.example.IBar"]
      }
    },
    {
      "type": "com.example.JniClass",
      "jniAccessible": true,
      "fields": [{ "name": "nativeHandle" }],
      "allDeclaredMethods": true
    }
  ],
  "resources": [
    {
      "glob": "config/**"
    },
    {
      "module": "app.module",
      "glob": "static/index.html"
    },
    {
      "bundle": "com.example.Messages"
    }
  ]
}
```