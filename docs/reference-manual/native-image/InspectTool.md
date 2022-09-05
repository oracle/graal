---
layout: ni-docs
toc_group: debugging-and-diagnostics
link_title: Inspection Tool
permalink: /reference-manual/native-image/debugging-and-diagnostics/InspectTool/
redirect_from: /$version/reference-manual/native-image/inspect/
---

# Native Image Inspection Tool

Native Image Enterprise Edition includes a tool to list the methods included in an executable or shared library created by GraalVM Native Image.
The tool is available as the command `$GRAALVM_HOME/bin/native-image-inspect <path_to_binary>`. It lists methods as a JSON array in the following format:

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

The Native Image tool, by default, includes metadata in the native executable which then enables the inspection tool to list the included methods.

The amount of data included is fairly minimal compared to the overall image size, however you can set the `-H:-IncludeMethodsData` option to disable the metadata emission.
Images compiled with this option will not be able to be inspected by the tool.

## Software Bill of Materials (SBOM)

GraalVM Enterprise Native Image can embed a Software Bill of Materials (SBOM) at build time to detect any libraries that may be susceptible to known security vulnerabilities.
Native Image provides the `--enable-sbom` option to embed an SBOM into a native executable. 

> Note: Embedding a Software Bill of Materials (SBOM) is available with GraalVM Enterprise Native Image. The feature is currently experimental.

The CycloneDX format is supported and the default. 
To embed a CycloneDX SBOM into a native executable, pass the `--enable-sbom` option to the `native-image` command. 

The implementation constructs the SBOM by recovering all version information observable in external library manifests for classes included in a native executable. 
The SBOM is also compressed in order to limit the SBOM's impact on the native executable size. 
Even though the tool is not yet supported on Windows, Windows users can still embed the SBOM with this experimental option. 
The SBOM is stored in the `gzip` format with the exported `sbom` symbol referencing its start address and the `sbom_length` symbol its size.

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

The tool can extract the SBOM from both executables and shared libraries. 
To scan for any vulnerable libraries, submit the SBOM to a vulnerability scanner. 
For example, the popular [Anchore software supply chain management platform](https://anchore.com/) makes the `grype` scanner freely available.
You can check whether the libraries given in your SBOMs have known vulnerabilities documented in Anchore's database. 
For this purpose, the output of the tool can be fed directly to the `grype` scanner to check for vulnerable libraries, using the command `$GRAALVM_HOME/bin/native-image-inspect --sbom <path_to_binary> | grype` which produces the following output:
```shell
NAME                 INSTALLED      VULNERABILITY   SEVERITY
netty-codec-http2    4.1.76.Final   CVE-2022-24823  Medium
```

You can then use this report to update any vulnerable dependencies found in your executable.

### Further Reading

- [Debugging and Diagnostics](DebuggingAndDiagnostics.md)