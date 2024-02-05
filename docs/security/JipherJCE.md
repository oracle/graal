---
layout: docs
toc_group: security-guide
link_title: Jipher JCE with Native Image
permalink: /security-guide/native-image/Jipher/
---

# Jipher JCE with Native Image

Jipher JCE is an Oracle-developed [Java Cryptography Architecture (JCA)](../reference-manual/native-image/JCASecurityServices.md) provider that packages a pre-configured and FIPS validated version of OpenSSL 3.0. 
The Jipher provider supports algorithms which are allowed by [FIPS](https://en.wikipedia.org/wiki/FIPS_140), including the OpenSSL 3.0's FIPS provider. 
Jipher provides competitive performance compared to Bouncy Castle or the default JDK providers.
It is recommended to enable Jipher with Native Image in contexts where only FIPS-allowed algorithms should be used. 
Note that some algorithms are allowed by FIPS for specific use cases only. As a result, some algorithms provided by Jipher might not be allowed by FIPS for all purposes.

> Note: Jipher is not available in GraalVM Community Edition. It is supported on Linux and macOS (macOS 10.15 and higher) on both AMD64 and AArch64 architectures.

Jipher JARs are included in the Oracle GraalVM core package at: _lib/jipher/jipher-jce.jar_ and _lib/jipher/jipher-pki.jar_.
To enable Jipher, pass these JARs on the application class path.

This page describes how to use Jipher with GraalVM Native Image.

## Build a Native Executable with Jipher

JCA algorithms rely on reflection. 
To include all required code paths in the native executable during ahead-of-time compilation, the `native-image` tool needs to be made aware of any dynamically accessed Java code at run time, via reflection, as well as the native code which may be invoked. (Learn more [here](../reference-manual/native-image/NativeImageBasics.md#static-analysis)).
This can be done by providing the JSON-based [metadata collected by the agent](../reference-manual/native-image/AutomaticMetadataCollection.md). 
Any dynamically-accessed JCA services through Jipher are automatically registered by the agent too.

The steps below show how to embedded Jipher in a native executable, using a simple Java application that does some RSA based signature creation and validation.

1. Save the following code into the file named _JipherExample.java_:

    ```java
    import java.security.*;
    import java.util.*;
    import com.oracle.jipher.provider.*;

    class JipherExample {
        public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
            Provider jipher = new JipherJCE();
            Security.insertProviderAt(jipher, 1);

            byte[] data = new byte[1024];
            new Random().nextBytes(data);

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", jipher);
            keyGen.initialize(4096);
            KeyPair keypair = keyGen.generateKeyPair();
            
            Signature signer = Signature.getInstance("SHA512withRSA", jipher);
            signer.initSign(keypair.getPrivate());
            signer.update(data);
            byte[] signature = signer.sign();
            
            Signature verifier = Signature.getInstance("SHA512withRSA", jipher);
            verifier.initVerify(keypair.getPublic());
            verifier.update(data);
            boolean isValid = verifier.verify(signature);
            assert(isValid);
        }
    }
    ```

2. Compile the application with Jipher JARs on the classpath:

    ```shell
    javac -cp $GRAALVM_HOME/lib/jipher/jipher-jce.jar:$GRAALVM_HOME/lib/jipher/jipher-pki.jar JipherExample.java
    ```
3. Run the application on the JVM with the agent enabled. The tracing agent captures and writes down all the dynamic features encountered during a test run into multiple _*-config.json_ files.

    ```shell
    java java -cp $GRAALVM_HOME/lib/jipher/jipher-jce.jar:$GRAALVM_HOME/lib/jipher/jipher-pki.jar:. -agentlib:native-image-agent=config-output-dir=<path> JipherExample
    ```
    Where `<path>` should point to the directory in which to store the configuration files.
    It is recommended that the output directory is `/META-INF/native-image/` (if you build with Maven or Gradle, then `/resources/META-INF/native-image/`). 
    Later, when building a native executable, the `native-image` builder will pick up the files from that location automatically. 

    For this Java application, the agent creates multiple configuration files. The ones to check are:

    - **resource-config.json**: Jipher bundles the OpenSSL libraries (for all supported platforms) within the JAR. This file lists entries for _libjipher.so_, _fips.so_, and _openssl.cnf_ along with the corresponding checksum files. The specific entries pertain to the platform on which the agent is run, and should correspond to the platform for which the native executable is built. For example, on Linux x64, the content should be similar to:
        ```json
        {
        "resources":{
        "includes":[
            {
            "pattern":"\\Qlibs/linux_x64/fips.so.crc32\\E"
            },
            {
            "pattern":"\\Qlibs/linux_x64/fips.so\\E"
            },
            {
            "pattern":"\\Qlibs/linux_x64/libjipher.so.crc32\\E"
            },
            {
            "pattern":"\\Qlibs/linux_x64/libjipher.so\\E"
            },
            {
            "pattern":"\\Qlibs/linux_x64/openssl.cnf.crc32\\E"
            },
            {
            "pattern":"\\Qlibs/linux_x64/openssl.cnf\\E"
            },
            {
            "pattern":"\\Qlibs\\E"
            }
        ]},
        "bundles":[]
        }
        ```
    - **jni-config.json**: This file lists entries from Jipher internal OpenSSL packages for Java classes with native method declarations. The content should be similar to:
        ```json
        {
        "name":"[B"}
        ,
        {
        "name":"[[B"}
        ,
        {
        "name":"com.oracle.jipher.internal.openssl.JniOpenSsl"}
        ,
        {
        "name":"java.lang.Boolean",
        "methods":[{"name":"getBoolean","parameterTypes":["java.lang.String"] }]
        }
        ```
    - **reflect-config.json**: This file lists entries from Jipher internal SPI packages for Java classes which implement the JCE SPI. The content should be similar to:
        ```json
        [
        {
        "name":"com.oracle.jipher.internal.spi.KeyPairGen$Rsa",
        "methods":[{"name":"<init>","parameterTypes":[] }]}
        ,
        {
        "name":"com.oracle.jipher.internal.spi.RsaDigestSig$Sha512WithRsa",
        "methods":[{"name":"<init>","parameterTypes":[] }]}
        ,
        ...]
        ```
4. For the agent to discover all possible calls to Jipher, re-run the application with the agent on the JVM (you can re-run the agent as many times as needed). This will rgenerate the entire configuration suite including any negative test cases (to allow for exception classes to be captured). For the subsequent runs, use this command:

    ```shell
    java -agentlib:native-image-agent=config-merge-dir=<path> JipherExample
    ```
    The `config-merge-dir` command will merge the new configuration with configuration from previous test runs.

5. Build a native executable with the provided configuration:

    ```shell
    native-image JipherExample
    ```
    If the configuration files have been placed in a different directory than `/META-INF/native-image/`, pass this flag `-H:ConfigurationFileDirectories=<path>` at build time to inform the `native-image` tool of a new location:

    ```shell
    native-image -H:ConfigurationFileDirectories=<path> JipherExample
    ```

6. Run the native executable:
    ```shell
    ./jipherexample
    ```
        
When Jipher **is not** embedded in a native executable, but is instead being loaded by the JVM, it extracts the native libraries and the _openssl.cnf_ file embedded in the JAR to the filesystem and then dynamically loads them into the JVM process. 
When Jipher **is** embedded in a native executable, it continues to extract the native libraries and the _openssl.cnf_ file to the filesystem and dynamically load them into the native process.
Jipher is recommended for GraalVM Native Image when only FIPS-allowed algorithm should be used. Learn more about JCA services support in Native Image [here](../reference-manual/native-image/JCASecurityServices.md).

### Related Documentation

* [Native Image Security Aspects](native-image.md)
* [JCA Security Services in Native Image](../reference-manual/native-image/JCASecurityServices.md)
* [OpenSSL FIPS Provider Security Policy](https://csrc.nist.gov/CSRC/media/projects/cryptographic-module-validation-program/documents/security-policies/140sp4506.pdf)