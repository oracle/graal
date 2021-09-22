---
layout: docs
toc_group: native-image
link_title: Certificate Management in Native Image
permalink: /reference-manual/native-image/CertificateManagement/
---
# Certificate Management in Native Image

Native-image provides multiple ways to specify the certificate file used to
define the default TrustStore. In the following sections we describe the
available buildtime and runtime options. Note the default behavior for
native-image is to capture and use the default TrustStore from the buildtime
host environment.

## Buildtime Options

During the image building process, native-image captures the host environment's
default TrustStore and embeds it into the native image. This TrustStore is
by default created from the root certificate file provided within the JDK, but
can be changed to use a different certificate file by setting the buildtime
system property "javax.net.ssl.trustStore" (see [Properties](Properties.md) for
how to do so).

Since the contents of the buildtime certificate file is embedded into the image
executable, the file itself does not need to present in the target environment.

## Runtime Options

The certificate file can also be changed dynamically at runtime via setting
the "javax.net.ssl.trustStore\*" system properties.

If any of the following system properties are set during image execution,
native-image also requires "javax.net.ssl.trustStore" to be set and for it
to point to an accessible certificate file:
- javax.net.ssl.trustStore
- javax.net.ssl.trustStoreType
- javax.net.ssl.trustStoreProvider
- javax.net.ssl.trustStorePassword

If any of these properties are set and "javax.net.ssl.trustStore" does not point
to an accessible file, then an UnsupportedFeatureError will be thrown.

Note that this behavior is different than OpenJDK. When the
"javax.net.ssl.trustStore" system property is unset/invalid, OpenJDK will
fallback to using a certificate file shipped within the JDK; however, such
files will not be present alongside the image executable and hence cannot be
used as a fallback.

During the execution, it also possible to dynamically change the
"javax.net.ssl.trustStore\*" properties and for the default TrustStore to be
updated accordingly.

Finally, whenever all of the "javax.net.ssl.trustStore\*" system properties
listed above are unset, the default TrustStore will be the one captured during
buildtime, as described in the [prior section](#buildtime-options).

## Untrusted Certificates

During the image building process, a list of untrusted certificates is loaded
from the file <java.home>/lib/security/blacklisted.certs. This file is used
when validating certificates at both buildtime and runtime.  In other words,
when a new certificate file is specified at runtime via setting the
"javax.net.ssl.trustStore\*" system properties, the new certificates will still
be checked against the <java.home>/lib/security/blacklisted.certs loaded at
image buildtime.
