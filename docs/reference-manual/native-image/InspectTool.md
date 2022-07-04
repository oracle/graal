---
layout: docs
toc_group: native-image
link_title: Native Image Inspection Tool
permalink: /reference-manual/native-image/inspect/
---

# Native Image Inspection Tool

Native Image Enterprise Edition comes with a tool outputting the list of methods included in a given executable or shared library compiled with GraalVM Native Image.
The tool is accessible through `$GRAALVM_HOME/bin/native-image-inspect <path_to_binary>` and outputs this list as a JSON array in the following format:

```shell
$GRAALVM_HOME/bin/native-image-inspect helloworld
{
  "methods": [
    {
      "declaringClass": "java.lang.Object",
      "name": "equals",
      "paramTypes": [
        "java.lang.Object"
      ]
    },
    {
      "declaringClass": "java.lang.Object",
      "name": "toString",
      "paramTypes": []
    },
    ...
  ]
}
```

The Native Image compilation process, by default, includes metadata in the executable allowing the inspection tool to emit the list of included methods.

The amount of data included is fairly minimal compared to the overall image size, however users can set the `-H:-IncludeMethodsData` option to disable the metadata emission.
Images compiled with this option will not be able to be inspected by the tool.

## Software Bill of Materials (SBOM)

Embedding a Software Bill of Materials (SBOM) is available with GraalVM Enterprise Native Image. The feature is currently experimental and is supported on Linux, macOS, and Windows platforms. Note that the Native Image Inspection Tool is only supported on Linux and macOS, and details necessary for obtaining the embedded SBOM without the tool are given below. In order to detect any libraries that may be susceptible to known security vulnerabilities, users may use the `-H:IncludeSBOM` option to embed an SBOM into the executable. Currently, the option supports embedding an SBOM in the CycloneDX format and takes `cyclonedx` as an argument. Users can embed a CycloneDX SBOM into a native executable by passing the `-H:IncludeSBOM=cyclonedx` option to the native-image tool at build time. The current implementation constructs the SBOM by recovering all version information observable in external library manifests for classes included in the executable. The SBOM is also compressed in order to limit the SBOM's impact on the executable's size. Even though the tool is not yet supported on Windows, Windows users can still embed the SBOM with this experimental option. The SBOM is stored in the `gzip` format with the exported `sbom` symbol referencing its start address and the `sbom_length` symbol its size.

After embedding the compressed SBOM into the executable, the tool is able to extract the compressed SBOM using an optional `--sbom` parameter accessible through `$GRAALVM_HOME/bin/native-image-inspect --sbom <path_to_binary>` and outputs the SBOM in the following format:

```json
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.4",
  "version": 1,
  "components": [
    {
      "type": "library",
      "group": "io.netty",
      "name": "netty-codec-http2",
      "version": "4.1.76.Final",
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
  "serialNumber": "urn:uuid:51ec305f-616e-4139-a033-a094bb94a17c"
}
```

The tool can extract the SBOM from both executables and shared libraries. To scan for any vulnerable libraries, users may directly submit the SBOM to a vulnerability scanner. For example, the popular Anchore software supply chain management platform makes the `grype` scanner freely available to users. Users can check whether the libraries given in their SBOMs have known vulnerabilities documented in Anchore's database. For this purpose, the output of the tool can be fed directly to the `grype` scanner to check for vulnerable libraries, through `$GRAALVM_HOME/bin/native-image-inspect --sbom <path_to_binary> | grype` which produces the following output:

```shell
NAME                 INSTALLED      VULNERABILITY   SEVERITY
netty-codec-http2    4.1.76.Final   CVE-2022-24823  Medium
```

Users can then use this report to update any vulnerable dependencies found in their executable.

## Evolution

The tool is continuously being improved upon. Envisioned new features include:

* Outputting the list of classes and fields included in the image alongside the methods.
* Windows support
