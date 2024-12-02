---
layout: docs
toc_group: security-guide
link_title: Software Bill of Materials (SBOM) in Native Image
permalink: /security-guide/native-image/sbom/
---

# Software Bill of Materials (SBOM) in Native Image

GraalVM Native Image can assemble a Software Bill of Materials (SBOM) at build time to detect any libraries that may be susceptible to known security vulnerabilities.
Native Image provides the `--enable-sbom` option to embed an SBOM into a native executable (only available in Oracle GraalVM).
In addition to being embedded, the SBOM can be added to the classpath or exported as a JSON file by using `--enable-sbom=classpath,export`.

The CycloneDX format is supported and is the default.
To embed a CycloneDX SBOM into a native executable, pass the `--enable-sbom` option to the `native-image` command.

The implementation constructs the SBOM by recovering all version information observable in external library manifests for classes included in a native executable.
The SBOM is compressed to limit the SBOM's impact on the native executable size.
The SBOM is stored in the `gzip` format with the exported `sbom` symbol referencing its start address and the `sbom_length` symbol referencing its size.

After embedding the compressed SBOM into the executable, the [Native Image Inspect Tool](../reference-manual/native-image/InspectTool.md) is able to extract the compressed SBOM using an optional `--sbom` parameter accessible through `$JAVA_HOME/bin/native-image-inspect --sbom <path_to_binary>` from both executables and shared libraries.
It outputs the SBOM in the following format:

```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.5",
  "version": 1,
  "components": [
    {
      "bom-ref": "pkg:maven/io.netty/netty-codec-http2@4.1.104.Final",
      "type": "library",
      "group": "io.netty",
      "name": "netty-codec-http2",
      "version": "4.1.104.Final",
      "purl": "pkg:maven/io.netty/netty-codec-http2@4.1.104.Final",
      "properties": [
        {
          "name": "syft:cpe23",
          "value": "cpe:2.3:a:codec:codec:4.1.76.Final:*:*:*:*:*:*:*"
        },
        {
          "name": "syft:cpe23",
          "value": "cpe:2.3:a:codec:netty-codec-http2:4.1.76.Final:*:*:*:*:*:*:*"
        },
        {
          "name": "syft:cpe23",
          "value": "cpe:2.3:a:codec:netty_codec_http2:4.1.76.Final:*:*:*:*:*:*:*"
        },
        ...
      ]
    },
    ...
  ],
  "dependencies": [
    {
      "ref": "pkg:maven/io.netty/netty-codec-http2@4.1.104.Final",
      "dependsOn": [
        "pkg:maven/io.netty/netty-buffer@4.1.104.Final",
        "pkg:maven/io.netty/netty-codec-http@4.1.104.Final",
        "pkg:maven/io.netty/netty-codec@4.1.104.Final",
        "pkg:maven/io.netty/netty-common@4.1.104.Final",
        "pkg:maven/io.netty/netty-transport@4.1.104.Final"
      ]
    },
    ...
  ],
  "serialNumber": "urn:uuid:51ec305f-616e-4139-a033-a094bb94a17c"
}
```

## Vulnerability Scanning

To scan for any vulnerable libraries, submit the SBOM to a vulnerability scanner.
For example, the popular [Anchore software supply chain management platform](https://anchore.com/) makes the `grype` scanner freely available.
You can check whether the libraries given in your SBOMs have known vulnerabilities documented in Anchore's database.
For this purpose, the output of the tool can be fed directly to the `grype` scanner to check for vulnerable libraries, using the command `$JAVA_HOME/bin/native-image-inspect --sbom <path_to_binary> | grype` which produces the following output:
```shell
NAME                 INSTALLED      VULNERABILITY   SEVERITY
netty-codec-http2    4.1.76.Final   CVE-2022-24823  Medium
```

You can then use this report to update any vulnerable dependencies found in your executable.

> Note: Running `native-image-inspect` without `--sbom` executes code from the native binary to extract class information. **Do not use it on untrusted binaries.** This extraction method is deprecated—use [class-level SBOMs](#including-class-level-metadata-in-the-sbom) instead.

## Dependency Tree

The SBOM provides information about component relationships through its `dependencies` field.
This dependency information is derived from Native Image's static analysis call graph.
Analyzing the dependency graph can help you understand why specific components are included in your application.
For example, discovering an unexpected component in the SBOM allows for tracing its inclusion through the dependency graph to identify which parts of the application are using it.

## Enhanced SBOMs with Maven Plugin for Native Image

To generate more accurate SBOMs with richer component metadata, consider using the [Maven plugin for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html).
This plugin integrates with Native Image to enhance the SBOM creation.

The plugin creates a "baseline" SBOM by using the `cyclonedx-maven-plugin`.
This baseline SBOM includes additional metadata that otherwise is not available to the native-image generator, such as `licenses`, `externalReferences`, `hashes`, and `copyright`.
See the [CycloneDX specification](https://cyclonedx.org/docs/1.5/json/#components) for more information about the fields.

The baseline SBOM also defines which package names belong to a component, helping Native Image associate classes with their respective components—a task that can be challenging when shading or fat JARs are used.
In this collaborative approach, Native Image is also able to prune components and dependencies more aggressively to produce a minimal SBOM.

These enhancements are available starting with plugin version 0.10.4 and are enabled by default when the `--enable-sbom` option is used.

## Including Class-Level Metadata in the SBOM

Using `--enable-sbom=class-level` adds class-level metadata to the SBOM components.
This metadata includes Java modules, classes, interfaces, records, annotations, enums, constructors, fields, and methods that are part of the native executable.
This information can be useful for:
* **Advanced vulnerability scanning:** When the affected classes or methods of a vulnerability are published as part of a CVE, the class-level metadata can be checked to determine if a native executable with the affected SBOM component is actually vulnerable, thereby reducing the false positive rate of vulnerability scanning.
* **Understanding image contents:** Quickly browse and search the class-level metadata to examine what is included in the native executable.

> Including class-level metadata increases the SBOM size substantially. For this [Micronaut Hello World Rest](https://github.com/graalvm/graalvm-demos/tree/master/micronaut-hello-rest-maven) application, the SBOM size is 1.1 MB when embedded, and 13.7 MB when exported. The SBOM without class-level metadata is 3.5 kB when embedded, and 64 kB when exported. The size of the native image without an embedded SBOM is around 52 MB.

### Data Format

The [CycloneDX specification](https://cyclonedx.org/docs/1.5/json/) allows the use of a hierarchical representation by nesting components that have a parent-child relationship.
It is used to embed class-level information in SBOM components in the following way:
```
[component] SBOM Component
└── [component] Java Modules
    └── [component] Java Source Files
        ├── [property] Classes
        ├── [property] Interfaces
        ├── [property] Records
        ├── [property] Annotations
        ├── [property] Enums
        ├── [property] Fields
        ├── [property] Constructors
        └── [property] Methods
```
Each SBOM component lists its Java modules in the `components` field.
Each module is identified by its name and lists its Java source files in the `components` field.
Each source file is identified by its path relative to the component's source directory and lists its classes, interfaces, records, annotations, enums, fields, constructors, and methods in the `properties` field.

Consider an example of a simple component containing one Java source file in `mymodule`:
```java
// src/com/sbom/SBOMTestApplication.java
package com.sbom;

import org.apache.commons.validator.routines.RegexValidator;

public class SBOMTestApplication {
    private static final boolean IS_EMPTY_OR_BLANK = new RegexValidator("^[\\s]*$").isValid(" ");

    public static void main(String[] argv) {
        System.out.println(String.valueOf(IS_EMPTY_OR_BLANK));
        ClassInSameFile someClass = new ClassInSameFile("hello ", "world");
        someClass.doSomething();
    }
}

class ClassInSameFile {
    private final String value1;
    private final String value2;

    ClassInSameFile(String value1, String value2) {
        this.value1 = value1;
        this.value2 = value2;
    }

    String concatenate() {
        System.out.println(value1 + value2);
    }

    // This method is unreachable and will therefore not be included in the SBOM
    String unreachable() {
        return value;
    }
}
```
The class-level SBOM component would look like this:
```json
{
    "type": "library",
    "group": "com.sbom",
    "name": "sbom-test-app",
    "version": "1.0.0",
    "purl": "pkg:maven/com.sbom/sbom-test-app@1.0.0",
    "bom-ref": "pkg:maven/com.sbom/sbom-test-app@1.0.0",
    "properties": [...],
    "components": [
        {
            "type": "library",
            "name": "mymodule",
            "components": [
                {
                    "type": "file",
                    "name": "com/sbom/SBOMTestApplication.java",
                    "properties": [
                        {
                            "name": "class",
                            "value": "com.sbom.ClassInSameFile"
                        },
                        {
                            "name": "class",
                            "value": "com.sbom.SBOMTestApplication"
                        },
                        {
                            "name": "field",
                            "value": "com.sbom.ClassInSameFile.value1:java.lang.String"
                        },
                        {
                            "name": "field",
                            "value": "com.sbom.ClassInSameFile.value2:java.lang.String"
                        },
                        {
                            "name": "field",
                            "value": "com.sbom.SBOMTestApplication.IS_EMPTY_OR_BLANK:boolean"
                        },
                        {
                            "name": "constructor",
                            "value": "com.sbom.ClassInSameFile(java.lang.String, java.lang.String)"
                        },
                        {
                            "name": "method",
                            "value": "com.sbom.ClassInSameFile.concatenate():java.lang.String"
                        },
                        {
                            "name": "method",
                            "value": "com.sbom.SBOMTestApplication.<clinit>():void"
                        },
                        {
                            "name": "method",
                            "value": "com.sbom.SBOMTestApplication.main(java.lang.String[]):void"
                        }
                    ]
                }
            ]
        }
    ]
}
```

The following table specifies the format of class-level metadata:

| Kind        | CycloneDX Object | `type`    | `name`                             | `value`                                                   | Notes                                                          |
|-------------|------------------|-----------|------------------------------------|-----------------------------------------------------------|----------------------------------------------------------------|
| Module      | Component        | `library` | module name                        | -                                                         | Unnamed module's `name` is `unnamed module`                    |
| Source File | Component        | `file`    | path relative to the src directory | -                                                         | Ends in `.java`, `/` separator, path derived from package name |
| Class       | Property         | -         | `class`                            | fully qualified name                                      | Includes anonymous, inner, and sealed classes                  |
| Interface   | Property         | -         | `interface`                        | fully qualified name                                      | -                                                              |
| Record      | Property         | -         | `record`                           | fully qualified name                                      | -                                                              |
| Annotation  | Property         | -         | `annotation`                       | fully qualified name                                      | -                                                              |
| Field       | Property         | -         | `field`                            | `className.fieldName:fieldType`                           | Field declaration                                              |
| Constructor | Property         | -         | `constructor`                      | `className(paramType1, paramType2)`                       | Parameter types comma-space separated                          |
| Method      | Property         | -         | `method`                           | `className.methodName(paramType1, paramType2):returnType` | Method with parameters and return type                         |


Some additional notes:
* Array types are suffixed with `[]`. For example, an array of strings becomes `java.lang.String[]`.
* Synthetically generated lambda classes are not included.

When using shaded or fat JARs, the class-level metadata can sometimes not be accurately associated with a component.
When this happens, all unresolved metadata gets collected in a placeholder component:
```json
{
    "type": "data",
    "name": "class-level metadata that could not be associated with a component",
    "components": [
      ...
    ]
}
```

## Related Documentation

- [Using GraalVM Native Image SBOM Support for Vulnerability Scanning](https://medium.com/graalvm/using-graalvm-native-image-sbom-support-for-vulnerability-scanning-4211c747376)
- [Embed an SBOM in a Native Executable to Identify Its Dependencies](../reference-manual/native-image/guides/use-sbom-support.md)
- [Security Guide](security-guide.md)