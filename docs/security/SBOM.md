---
layout: docs
toc_group: security-guide
link_title: Software Bill of Materials (SBOM) in Native Image
permalink: /security-guide/native-image/sbom/
---

# Software Bill of Materials (SBOM) in Native Image

GraalVM Native Image assembles a Software Bill of Materials (SBOM) at build time to detect any libraries that may be susceptible to known security vulnerabilities (only available in Oracle GraalVM).
Pass the `--enable-sbom` option to the `native-image` command to configure the SBOM feature.
The SBOM feature is enabled by default and defaults to the `embed` option which embeds an SBOM into the native executable.
In addition to being embedded, the SBOM can be added to the classpath or exported as a JSON file by using `--enable-sbom=classpath,export`.

The CycloneDX format is supported and is the default.

The implementation constructs the SBOM by recovering all version information observable in external library manifests for classes included in a native executable.
The SBOM is compressed to limit the SBOM's impact on the native executable size.
The compressed size is typically less than 1/10,000 of the overall image size.
The SBOM is stored in the `gzip` format with the exported `sbom` symbol referencing its start address and the `sbom_length` symbol referencing its size.

The SBOM feature can be disabled with `--enable-sbom=false`.

## Extracting SBOM Contents

After embedding the compressed SBOM into the image, there are two possible ways to extract the SBOM contents:
- using the [Native Image Configure Tool](#native-image-configure-tool)
- using [Syft](https://github.com/anchore/syft){:target="_blank"}

### Native Image Configure Tool

The Native Image Configure Tool can extract the compressed SBOM using the `extract-sbom` command from executables and shared libraries.
```bash
$JAVA_HOME/bin/native-image-configure extract-sbom --image-path=<path_to_binary>
```

It outputs the contents in the JSON format:
```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.6",
  "version": 1,
  "serialNumber": "urn:uuid:51ec305f-616e-4139-a033-a094bb94a17c",
  "metadata": {
    "timestamp": "2025-10-06T15:46:50.593277+02:00",
    "tools": {
      "components": [
        { 
          "type": "library",
          "group": "org.graalvm.sdk",
          "name": "nativeimage",
          "version": "25+37-LTS",
          "purl": "pkg:maven/org.graalvm.sdk/nativeimage@25%2B37-LTS",
          "bom-ref": "pkg:maven/org.graalvm.sdk/nativeimage@25%2B37-LTS"
        }
      ]
    },
    "component": {
      "type": "library",
      "group": "com.sbom",
      "name": "your-app",
      "version": "1.0.0",
      "purl": "pkg:maven/com.sbom/your-app@1.0.0",
      "bom-ref": "pkg:maven/com.sbom/your-app@1.0.0"
    }
  },
  "components": [
    {
      "type": "library",
      "group": "org.json",
      "name": "json",
      "version": "20211205",
      "purl": "pkg:maven/org.json/json@20211205",
      "bom-ref": "pkg:maven/org.json/json@20211205"
    },
    ...
  ],
  "dependencies": [
    {
      "ref": "pkg:maven/com.sbom/your-app@1.0.0",
      "dependsOn": ["pkg:maven/org.json/json@20211205"]
    },
    {
      "ref": "pkg:maven/org.json/json@20211205",
      "dependsOn": []
    },
    ...
  ]
}
```

A few notes about the SBOM structure:
* The `metadata/tools` entry indicates that the SBOM was produced by Native Image. 
* The `metadata/component` is the component of your application. This is included if the image is not built as a shared library and if the class containing the entry point of the image can be associated with exactly one component.
* The `components` entry lists the inventory of first-party and third-party components of your application.

See the [CycloneDX specification](https://cyclonedx.org/docs/1.6/json/) for more information about specific fields.

### Syft

[Syft](https://github.com/anchore/syft){:target="_blank"} is an open-source tool developed by Anchore that generates an SBOM for container images and filesystems.
Additionally, it can extract an embedded SBOM and present it in both its native Syft format and the CycloneDX format.
Thanks to the contribution from the GraalVM team, `syft` can extract an embedded SBOM from within a native image, built for Linux, macOS, or Windows.

Run `syft scan` on the native executable to extract the entire SBOM contents:
```bash
syft scan <path_to_binary> -o cyclonedx-json
```

To list only the Java libraries included in it, run:
```bash
syft <path_to_binary>
```

It outputs the list similar to this:
```bash
NAME               VERSION       TYPE
Oracle GraalVM     25+12-LTS     graalvm-native-image
collections        25+12-LTS     java-archive
commons-validator  1.9.0         java-archive
json               20211205      java-archive
...
```

## Enabling Security Scanning

You can leverage the generated SBOM to integrate with security scanning solutions.
There are a variety of tools to help detect and mitigate security vulnerabilities in your application dependencies.

One example is [Application Dependency Management (ADM)](https://docs.oracle.com/iaas/Content/application-dependency-management/concepts/adm_overview.htm){:target="_blank"} from Oracle.
When submitting your SBOM to the ADM vulnerability scanner, it identifies application dependencies and flags those containing known security vulnerabilities.
ADM relies on vulnerability reports from community sources, including the National Vulnerability Database (NVD).
It also integrates with GitHub Actions, GitLab, and Jenkins Pipelines.

Another popular command-line scanner is `grype`, part of the [Anchore software supply chain management platform](https://anchore.com/){:target="_blank"}.
With `grype`, you can check whether the libraries listed in your SBOMs have known vulnerabilities documented in Anchore's database.
The output of the `native-image-configure` tool can be fed directly into `grype` to scan for vulnerable libraries using the following command:
```bash
native-image-configure extract-sbom --image-path=<path_to_binary> | grype
```
It produces the following output:
```shell
NAME  INSTALLED  FIXED IN  TYPE          VULNERABILITY        SEVERITY  EPSS         RISK  
json  20211205   20230227  java-archive  GHSA-3vqj-43w4-2q58  High      0.7% (71st)  0.5   
json  20211205   20231013  java-archive  GHSA-4jq9-2xhw-jpx7  High      0.5% (66th)  0.4
```

The generated report can then be used to update any vulnerable dependencies in your executable.

### Automated Scanning

Integrating security scanning into your CI/CD workflows has never been easier.
With SBOM support available in the [GraalVM GitHub Action](https://github.com/marketplace/actions/github-action-for-graalvm){:target="_blank"}, your generated SBOM can be automatically submitted and analyzed using [GitHub’s dependency submission API](https://docs.github.com/en/rest/dependency-graph/dependency-submission){:target="_blank"}.
It enables:
- Vulnerability tracking with GitHub's Dependabot.
- Dependency tracking with GitHub's Dependency Graph.

This integration helps ensure that your application is continuously monitored for vulnerabilities throughout the development lifecycle.

## Verifying Component Integrity with Hashes

Use `--enable-sbom=hashes` to associate each component with SHA-256 and SHA-512 hashes.
The hash can be verified against trusted sources such as Maven Central.
Verifying the component hashes can detect malicious tampering or substitutions in your native image builds.
If a compromised dependency poses as a legitimate library, a hash mismatch against the trusted source would reveal tampering.

> Verifying component hashes strengthens integrity verification, but does not provide complete end‑to‑end supply chain security. Use cryptographic signing and SLSA provenances to guarantee authenticity and integrity.

Below is an example of a component that includes the `hashes` field:
```json
{
  "type": "library",
  "group": "io.micronaut",
  "name": "inject",
  "version": "4.2.3",
  "purl": "pkg:maven/io.micronaut/inject@4.2.3",
  "bom-ref": "pkg:maven/io.micronaut/inject@4.2.3",
  "hashes": [
    {
      "alg": "SHA-256",
      "content": "6f39a054d1c589248551c4519e892bf48f65cb9b2e16e8a9079393ecfd27e441"
    },
    {
      "alg": "SHA-512",
      "content": "05f1a81ea70e9fd0607b97bc62d8d210fd66fcc4e5aa6471ab5f278d2bfb41ab52ac013864d5d5529f0f365e677c15912c88ca51452f9419e8dbaee64efb0b03"
    }
  ]
}
```

Hashes are computed for applications JARs and GraalVM components, but not for classpath directories.
The GraalVM components are associated with the hash of the runtime image file. 
These GraalVM components include for example `org.graalvm.nativeimage/svm`, `org.graalvm.sdk/nativeimage`, and `Oracle GraalVM`.

| Kind                  | Hashed | How to verify                                                                                                                    |
|-----------------------|:------:|----------------------------------------------------------------------------------------------------------------------------------|
| Application JARs      |  Yes   | Download the JAR from a trusted source, hash it, and compare with the component hash.                                            |
| GraalVM components    |  Yes   | Hash `$GRAALVM_HOME/lib/modules` from the same GraalVM distribution used to build the image and compare with the component hash. |
| Classpath directories |   No   | -                                                                                                                                |

If you use a fat (or shaded) JAR, the hash is computed from the fat JAR, which will not match individual component hashes published on Maven Central.

Pair this with `--enable-sbom=hashes,strict`.
The `strict` flag enforces completeness by ensuring every class in the image is mapped to a component.
When using `strict`, the `native-image` builder will throw an exception if one or more components cannot be associated with a hash.

The Security Report in the build output indicates whether any components could not be associated with a hash. 
Run the following command to find these components:
```bash
jq '.components[] | select(.hashes == null)' /path/to/app.sbom.json
```

## Dependency Tree

The SBOM provides information about component relationships through its `dependencies` field.
This dependency information is derived from Native Image's static analysis call graph.
Analyzing the dependency graph can help you understand why specific components are included in your application.
For example, discovering an unexpected component in the SBOM allows for tracing its inclusion through the dependency graph to identify which parts of the application are using it.

With the GraalVM GitHub Action, you get access to GitHub's Dependency Graph feature.

## More Accurate SBOMs with Maven

To generate more accurate SBOMs, consider using the [Maven plugin for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html).
This plugin integrates with Native Image to improve the SBOM creation.

The plugin creates a "baseline" SBOM by using the `cyclonedx-maven-plugin`.
The baseline SBOM defines which package names belong to a component, helping Native Image associate classes with their respective components—a task that can be challenging for the `native-image` tool when shading or fat JARs are used.
In this collaborative approach, Native Image is also able to prune components and dependencies more aggressively to produce a minimal SBOM.

These enhancements are available starting with plugin version 0.10.4 and are enabled by default when the `--enable-sbom` option is used.

## Including Class-Level Metadata in the SBOM

Using `--enable-sbom=class-level` adds class-level metadata to the SBOM components.
This metadata includes Java modules, classes, interfaces, records, annotations, enums, constructors, fields, and methods that are part of the native executable.
This information can be useful for:
* **Advanced vulnerability scanning:** When the affected classes or methods of a vulnerability are published as part of a CVE, the class-level metadata can be checked to determine if a native executable with the affected SBOM component is actually vulnerable, thereby reducing the false positive rate of vulnerability scanning.
* **Understanding image contents:** Quickly browse and search the class-level metadata to examine what is included in the native executable.

> Including class-level metadata increases the SBOM size substantially. For this [Micronaut Hello World Rest](https://github.com/graalvm/graalvm-demos/tree/master/native-image/microservices/micronaut-hello-rest-maven) application, the SBOM size is 1.1 MB when embedded, and 13.7 MB when exported. The SBOM without class-level metadata is 3.5 kB when embedded, and 64 kB when exported. The size of the native image without an embedded SBOM is around 52 MB.

Note that including class-level metadata is not supported by [Syft](#syft), as the nested components field containing this metadata is removed from the extracted SBOM.
This limitation affects only metadata visibility in extracted SBOMs; it does not impact vulnerability scanning functionality.

### Data Format

The [CycloneDX specification](https://cyclonedx.org/docs/1.6/json/){:target="_blank"} allows the use of a hierarchical representation by nesting components that have a parent-child relationship.
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
    "name": "your-app",
    "version": "1.0.0",
    "purl": "pkg:maven/com.sbom/your-app@1.0.0",
    "bom-ref": "pkg:maven/com.sbom/your-app@1.0.0",
    "properties": [...],
    "hashes": [...],
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
