---
layout: ni-docs
toc_group: how-to-guides
link_title: Embed an SBOM in a Native Executable to Identify Its Dependencies
permalink: /reference-manual/native-image/guides/use-sbom-support/
---

# Embed an SBOM in a Native Executable to Identify Its Dependencies

Native Image embeds a Software Bill of Materials (SBOM) into the resulting binary by default. (Not available in GraalVM Community Edition.)
An SBOM is an inventory of all the components, libraries, and modules that make up your application.
It provides detailed information about all open-source and proprietary libraries used by the application and their versions, and it supports the CycloneDX format by default.
You can configure this behavior with the `--enable-sbom` option. See [Software Bill of Materials (SBOM) in Native Image](../../../security/SBOM.md) for more information.

### Prerequisites

* Make sure you have installed Oracle GraalVM.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).
* [Syft](https://github.com/anchore/syft)

## Generate a Native Executable

For the demo application, you will use the `jwebserver` tool, and package it as a native executable with an embedded SBOM.

> `jwebserver` is a minimal HTTP server for serving static files from a single directory hierarchy, included in the JDK. It was [added in Java 18](https://blogs.oracle.com/javamagazine/post/java-18-simple-web-server).

1. Save the following code to a file named _index.html_, so the web server has content to serve:
    ```html
    <!DOCTYPE html>
    <html>
        <head>
            <title>jwebserver</title>
        </head>
        <body>
        <h2>Hello, GraalVM user!<p>
        </body>
    </html>
    ```

2. From the directory where you saved _index.html_, run the following command to create a native executable:
    ```bash
    native-image -m jdk.httpserver -o jwebserver
    ```
    Native Image compiles `jwebserver` from the `jdk.httpserver` module, provided with the JDK, by passing the `-m` option.
    It produces a native executable containing a GZIP format compressed SBOM.

3. (Optional) Run the compiled `jwebserver` executable and go to _localhost:8000_ in a browser:
    ```bash
    ./jwebserver
    ```

## Extract the Embedded SBOM

There are two possible ways to extract the compressed SBOM contents into a human-readable format:
- using [Syft](https://github.com/anchore/syft)
- using the [Native Image Inspect Tool](../InspectTool.md)

### Syft

Syft, `syft`, is an open source tool maintained by [Anchore](https://anchore.com/).
Syft can extract an embedded SBOM which it can present in both a native Syft format or CycloneDX.
Thanks to a contribution from the GraalVM team, `syft` can now extract an SBOM given within a native executable, built for Linux, macOS, or Windows.

Run `syft` on the native executable to read its SBOM contents:
```bash
syft jwebserver
```
It lists all of the Java libraries included in it.

### Native Image Inspect Tool

GraalVM Native Image provides the [Inspect Tool](../InspectTool.md) to retrieve an SBOM embedded in a native executable.
The Inspect Tool is a viable alternative if you prefer not to install `syft`.

Run the following command to read the SBOM contents using the Inspect Tool:
```bash
native-image-inspect --sbom jwebserver
```

To take it further, you can submit the SBOM to any available vulnerability scanner, and check if the recorded libraries have known security vulnerabilities.
Vulnerability scanners cross-reference the components listed in an SBOM with CVEs in vulnerability databases.

This guide demonstrated how you can get insights on your application supply chain to help assess risks associated with the third-party dependencies.
Native Image can embed an SBOM into a native executable or shared library at build time.

### Related Documentation

* [Software Bill of Materials (SBOM) in Native Image](../../../security/SBOM.md)
* [Security Considerations in Native Image](../../../security/native-image.md)