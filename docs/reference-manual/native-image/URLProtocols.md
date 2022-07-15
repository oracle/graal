---
layout: ni-docs
toc_group: dynamic-features
link_title: URL Protocols
permalink: /reference-manual/native-image/dynamic-features/URLProtocols/
redirect_from: /$version/reference-manual/native-image/URLProtocols/
---

# URL Protocols in Native Image

URL Protocols in Native Image can be divided into three classes:

* supported and enabled by default
* supported and disabled by default
* HTTPS support

URL Protocols that are supported and enabled by default will be included into every generated native binary.
Currently, `file` and `resource` are the only supported URL protocols enabled by default.

There are URL Protocols that are supported but not enabled by default when building a native binary.
They must be enabled during build time by using the `--enable-url-protocols=<protocols>` option on the command line.
The option accepts a list of comma-separated protocols.

The rationale behind enabling protocols on-demand is that you can start with a minimal binary and add features as you need them.
This way your binary will only include the features you use, which helps keep the overall size small.
Currently `http` and `https` are the only URL protocols that are supported and can be enabled on demand.
They can be enabled using the `--enable-http` and `--enable-https` command-line options.

## HTTPS Support
Support for the `https` URL protocol relies on the Java Cryptography Architecture (JCA) framework.
Thus enabling `https` will add the code required by the JCA to the generated binary, including statically linked native libraries that the JCA may depend on.
See the [documentation on security services](JCASecurityServices.md) for more details.

No other URL protocols are currently tested.
They can still be enabled using `--enable-url-protocols=<protocols>`, however they might not work as expected.

### Related Documentation

- [Native Image Build Configuration](BuildConfiguration.md)