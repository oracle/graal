---
name: building-native-image
description: Build and troubleshoot GraalVM native image. Use this skill when the user wants to build Java applications using GraalVM Native Image, asks about Native Image options or tips, or asks you to troubleshoot building or running the image.
---

## Environment setup
- If Java program uses Native Image SDK, `JAVA_HOME` must point to a GraalVM distribution.  If you don't know the path to the GraalVM distribution, ask the user to provide it.

## Building image
1. The Java file you want to build with Native Image should be compiled with `javac`.  

2. Native Image build:
```bash
JAVA_HOME/bin/native-image <app-name>
```

3. Run your app like an executable:
```bash
./app-name
```

If native-image can't find your class file, you should explicitly set your classpath with `-cp` option.

## Troubleshooting

### Reachability Metadata

If you hit **runtime errors** in the native binary related to reflection, JNI, resources, serialization,
or dynamic proxies, read the reference file before attempting a fix.
                                                                                                            
**Load [`references/reachability-metadata.md`](references/reachability-metadata.md) when you see any of:**
 - `NoClassDefFoundError` or `MissingReflectionRegistrationError`
 - `MissingJNIRegistrationError`
 - `MissingResourceException` (missing resource bundle)
 - Any user question about: reflection, JNI, proxies, resources, resource bundles, or serialization in native image.

## Native Image options

All available CLI options for native-image: [`references/native-image-options.md`](references/native-image-options.md)