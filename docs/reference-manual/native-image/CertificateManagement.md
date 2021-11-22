---
layout: docs
toc_group: native-image
link_title: Certificate Management in Native Image
permalink: /reference-manual/native-image/CertificateManagement/
---
# Certificate Management in Native Image

Native Image provides multiple ways to specify the certificate file used to define the default TrustStore.
In the following sections we describe the available build-time and run-time options.
Note: The default behavior for `native-image` is to capture and use the default TrustStore from the build-time host environment.

## Build-time Options

During the image building process, the `native-image` builder captures the host environment's default TrustStore and embeds it into the native executable.
This TrustStore is by default created from the root certificate file provided within the JDK, but can be changed to use a different certificate file by setting the build-time system property `javax.net.ssl.trustStore` (see [Properties](Properties.md) for how to do it).

Since the contents of the build-time certificate file is embedded into the native executable, the file itself does not need to be present in the target environment.

## Run-time Options

The certificate file can also be changed dynamically at run time via setting the `javax.net.ssl.trustStore\*` system properties.

If any of the following system properties are set during the image execution, `native-image` also requires `javax.net.ssl.trustStore` to be set, and for it to point to an accessible certificate file:
- `javax.net.ssl.trustStore`
- `javax.net.ssl.trustStoreType`
- `javax.net.ssl.trustStoreProvider`
- `javax.net.ssl.trustStorePassword`

If any of these properties are set and `javax.net.ssl.trustStore` does not point to an accessible file, then an `UnsupportedFeatureError` will be thrown.

Note that this behavior is different than OpenJDK.
When the `javax.net.ssl.trustStore` system property is unset or invalid, OpenJDK will fallback to using a certificate file shipped within the JDK.
However, such files will not be present alongside the image executable and hence cannot be used as a fallback.

During the execution, it also possible to dynamically change the `javax.net.ssl.trustStore\*` properties and for the default TrustStore to be updated accordingly.

Finally, whenever all of the `javax.net.ssl.trustStore\*` system properties listed above are unset, the default TrustStore will be the one captured during the build time, as described in the [prior section](#build-time-options).

## Untrusted Certificates

During the image building process, a list of untrusted certificates is loaded from the file `<java.home>/lib/security/blacklisted.certs`.
This file is used when validating certificates at both build time and run time.
In other words, when a new certificate file is specified at run time via setting the `javax.net.ssl.trustStore\*` system properties, the new certificates will still be checked against the `<java.home>/lib/security/blacklisted.certs` loaded at
image build time.
