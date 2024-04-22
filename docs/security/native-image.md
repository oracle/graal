---
layout: docs
toc_group: security-guide
link_title: Security Considerations in Native Image
permalink: /security-guide/native-image/
---
# Security Considerations in Native Image

The `native-image` builder generates a snapshot of an application after startup and bundles it in a binary executable.
The security report section of the native image [build output](../reference-manual/native-image/BuildOutput.md#security-report) provides security relevant information about the native image build.

## Class Initialization

The `native-image` builder may execute the static initializers of certain classes at build time (see [class initialization](../reference-manual/native-image/ClassInitialization.md) for more details).
Executing static initializers at build time persists the state after initialization in the image heap.
This means that any information that is obtained or computed in static initializers becomes part of a native executable.
This can either result in sensitive data ending up in the snapshot or fixing initialization data that is supposed to be obtained at startup, such as random number seeds.

Developers can request static initializers that process sensitive information to be executed at run time by specifying the `--initialize-at-run-time` CLI parameter when building a native executable, followed by a comma-separated list of packages and classes (and implicitly all of their subclasses) that must be initialized at runtime and not during image building.
Alternatively developers can make use of the [`RuntimeClassInitialization` API](https://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/RuntimeClassInitialization.html).

Developers should run the `native-image` builder in a dedicated environment, such as a container, that does not contain any sensitive information in the first place.

## Software Bill of Materials

GraalVM Native Image can embed a Software Bill of Materials (SBOM) at build time to detect any libraries that may be susceptible to known security vulnerabilities.
Native Image provides the `--enable-sbom` option to embed an SBOM into a native executable (not available in GraalVM Community Edition).

The CycloneDX format is supported and the default. 
To embed a CycloneDX SBOM into a native executable, pass the `--enable-sbom` option to the `native-image` command. 

The implementation constructs the SBOM by recovering all version information observable in external library manifests for classes included in a native executable. 
The SBOM is also compressed in order to limit the SBOM's impact on the native executable size.  
The SBOM is stored in the `gzip` format with the exported `sbom` symbol referencing its start address and the `sbom_length` symbol its size.

After embedding the compressed SBOM into the executable, the [native image inspect tool](../reference-manual/native-image/InspectTool.md) is able to extract the compressed SBOM using an optional `--sbom` parameter accessible through `$JAVA_HOME/bin/native-image-inspect --sbom <path_to_binary>` from both executables and shared libraries.
It outputs the SBOM in the following format:

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

To scan for any vulnerable libraries, submit the SBOM to a vulnerability scanner.
For example, the popular [Anchore software supply chain management platform](https://anchore.com/) makes the `grype` scanner freely available.
You can check whether the libraries given in your SBOMs have known vulnerabilities documented in Anchore's database.
For this purpose, the output of the tool can be fed directly to the `grype` scanner to check for vulnerable libraries, using the command `$JAVA_HOME/bin/native-image-inspect --sbom <path_to_binary> | grype` which produces the following output:
```shell
NAME                 INSTALLED      VULNERABILITY   SEVERITY
netty-codec-http2    4.1.76.Final   CVE-2022-24823  Medium
```

You can then use this report to update any vulnerable dependencies found in your executable.

> Note that if `native-image-inspect` is used without the `--sbom` option, it will execute parts of the specified native binary to extract the method-level information.
This functionality should not be used on native image executables from unknown or untrusted sources.

## Java serialization in Native Image

Native Image supports Serialization to help users deserialize the constructors for classes, contained in a native executable.
Unless picked up by native image analysis automatically, [these classes have to be prespecified](../reference-manual/native-image/Reflection.md#manual-configuration), as classes not contained in a native executable cannot be deserialized.
Native Image cannot prevent exploitation of deserialization vulnerabilities in isolation.
The [serialization and deserialization Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html#8) should be followed.

The security report section of the native image [build output](../reference-manual/native-image/BuildOutput.md#security-report) provides information on whether deserialization code is part of a native image's attack surface or not.

## Miscellaneous

Setting the security manager is not allowed. For more information see the [compatibility documentation](../reference-manual/native-image/Compatibility.md#security-manager).

Native Image provides multiple ways to specify a certificate file used to define the default TrustStore.
While the default behavior for `native-image` is to capture and use the default TrustStore from the build-time host environment, this can be changed at run time by setting the "javax.net.ssl.trustStore\*" system properties.
See the [documentation](../reference-manual/native-image/CertificateManagement.md) for more details.

The directory containing the native executable is part of the search path when loading native libraries using `System.loadLibrary()` at run time.

Native Image will not allow a Java Security Manager to be enabled because this functionality has now deprecated since Java 17.
Attempting to set a security manager will trigger a runtime error.

## Related Documentation

- [Security Guide](security-guide.md)
- [Polyglot Sandboxing](polyglot-sandbox.md)
- [Jipher JCE with Native Image](JipherJCE.md)
