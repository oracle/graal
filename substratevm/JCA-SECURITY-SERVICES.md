JCA Security Services on Substrate VM
-----------------------------

This section refers to the use of the services provided by the [Java Cryptography Architecture (JCA)](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html) framework.
The JCA framework relies on reflection to achieve algorithm independence and extensibility, therefore it requires a custom configuration on Substrate VM.
Additionally, seed generators that use system files like `/dev/random` or `/dev/urandom` need to be re-initialized at runtime.

By default a native image is built with support for the `SecureRandom` and `MessageDigest` engines from the `SUN` provider.
These are core security services needed by the VM itself.
All the other Java security services (`Signature`, `Cipher`, `Mac`, `KeyPair`, `KeyGenerator`, `KeyFactory`, `KeyStore`, etc.) must be enabled adding the `--enable-all-security-services` option to the `native-image` command.
The reason behind enabling only core security services by default is that you can start with a basic image and add more security services as you need them.
This helps keeping the overall image size small.

Note: The `--enable-all-security-services` option is enabled by default when `https` support is enabled.
See the [documentation on URL protocols](URL-PROTOCOLS.md) for more details.

### Provider registration

The image builder captures the list of providers and their preference order from the underlying JVM.
The provider order is specified in the `java.security` file under `<java-home>/lib/security/java.security`.
New security providers cannot be registered at run time, all providers must be statically configured during native image building.

### Alternative to `--enable-all-security-services`

Registering *all* security services doesn't come for free.
The additional code increases the native image size.
If your application only requires a subset of the security services you can manually register the corresponding classes for reflection and push the initialization of some seed generators to runtime.
However this requires deep knowledge of the JCA architecture.
We are investigating the posibility to provide a finer grain declarative configuration of security services for future releases.
If you want to take on this task youreslf you can start by reading the `com.oracle.svm.hosted.SecurityServicesFeature` class.
This is where most of the code behind the `--enable-all-security-services` option is implemented.
