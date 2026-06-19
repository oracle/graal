---
layout: docs
toc_group: dynamic-features
link_title: URL Protocols
permalink: /reference-manual/native-image/dynamic-features/URLProtocols/
redirect_from: /reference-manual/native-image/URLProtocols/
---

# URL Protocols in Native Image

Native Image includes URL protocol handlers that are reachable in the generated binary.
The `file` and `resource` URL protocols are enabled by default.
Resources must still be included through [reachability metadata](ReachabilityMetadata.md#resources) before the `resource` protocol can resolve them.

Other JDK URL protocol handlers are supported but not enabled by default.
To use one, register the corresponding `sun.net.www.protocol.<protocol>.Handler` constructor in reachability metadata.
For example, to use the `http` and `https` protocols, add this entry to _META-INF/native-image/reachability-metadata.json_:

```json
{
  "reflection": [
    {
      "type": "sun.net.www.protocol.http.Handler",
      "methods": [
        {
          "name": "<init>",
          "parameterTypes": []
        }
      ]
    },
    {
      "type": "sun.net.www.protocol.https.Handler",
      "methods": [
        {
          "name": "<init>",
          "parameterTypes": []
        }
      ]
    }
  ]
}
```

Use the same form for other JDK protocols, for example `jar`, `jrt`, `ftp`, `jmod`, or `mailto`,
by replacing the protocol name in the handler class.
For `jrt`, registering `sun.net.www.protocol.jrt.Handler` only enables URL construction.
Opening a `jrt:` connection also requires `-H:+AllowJRTFileSystem`, and runtime module access depends on `java.home`.
If a native executable accesses a supported protocol that was not registered, the URL lookup fails at run time
and reports the handler class that must be added to reachability metadata.

The `--enable-http`, `--enable-https`, and `--enable-url-protocols` options are deprecated.
Use reachability metadata instead.

## HTTPS Support
Support for the `https` URL protocol relies on the Java Cryptography Architecture (JCA) framework.
Registering the `https` handler can add the code required by the JCA to the generated binary, including statically linked native libraries that the JCA may depend on.
See the [documentation on security services](JCASecurityServices.md) for more details.

URL protocols beyond the JDK handlers discussed above are not currently tested.
They can still be registered with reachability metadata, however they might not work as expected.

### Related Documentation

- [Native Image Build Configuration](BuildConfiguration.md)
- [Reachability Metadata](ReachabilityMetadata.md)
