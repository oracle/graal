URL Protocols on Substrate VM
-----------------------------

On Substrate VM URL protocols can be divided in three classes:

### Supported and enabled by default
These are protocols that are enabled by default and added to every built image.
Currently `file` and `resource` are the only supported URL protocols enabled by default.

### Supported and disabled by default
These are protocols that are supported but are not enabled by default when building an image.
They must be enabled during image building by adding the `--enable-url-protocols=<protocols>` option to the `native-image` command.
The option accepts a list of comma separated protocols.
The rationale behind enabling protocols on demand is that you can start with a minimal image and add features as you need them.
This way your image will only include the features that you use which helps keeping the overall size small.
Currently `http` and `https` are the only URL protocols that are supported and can be enabled on demand.
Alternatively `http` and `https` can be enabled using the `--enable-http` and `--enable-https` options.

#### HTTPS support
Support for the `https` URL protocol relies on the Java Cryptography Architecture (JCA) framework.
Thus when `https` is enabled `--enable-all-security-services` is set by default.
This adds to the generated image the code required by the JCA.
It also enables JNI by default since some providers like SunEC are implemented in native code.
However, it doesn't include the corresponding native library in the image, i.e., `libsunec.so` for SunEC.

You need to ship that with the image and set the `java.library.path` system property accordingly. The `libsunec.{so|dylib}` static object library is from a JDK for the native app to work (it is found in the `${JAVA_HOME}/jre/lib/amd64` for Linux, `${JAVA_HOME}/jre/lib` for MacOS).

Note: if `libsunec.{so|dylib}` from GraalVM's JDK is bundled, then you do not need to additionally bundle `${JAVA_HOME}/jre/lib/security/cacerts` with the native app, otherwise we need to bundle it to avoid encountering the `Unexpected error - SSLException: java.lang.RuntimeException: Unexpected error: java.security.InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty` error. In addition to bundling `cacerts`, the native app must be invoked like this: 

```bash
   $ native-app -Djavax.net.ssl.trustStore=[/path/to/]cacerts -Djavax.net.ssl.trustStorePassword=changeit"
```

See the [documentation on security services](JCA-SECURITY-SERVICES.md) for more details.

### Not tested
No other URL protocols are currently tested.
They can still be enabled using `--enable-url-protocols=<protocols>`, however they might not work as expected.