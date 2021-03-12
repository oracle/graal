# JCA Security Services in Native Image

This section refers to the use of the services provided by the Java Cryptography Architecture (JCA) framework.
For more details see the following guides: [JDK8](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html), [JDK11](https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html).

The JCA framework uses a provider architecture to access security services such as digital signatures, message digests, certificates and certificate validation, encryption (symmetric/asymmetric block/stream ciphers), key generation and management, and secure random number generation, etc.
To achieve algorithm independence and extensibility it relies on reflection, therefore it requires a custom configuration in Native Image.
The Native Image builder uses static analysis to discover which of these services are used.

Each provider registers concrete implementation classes for the algorithms it supports.
Each of the service classes (`Signature`, `Cipher`, `Mac`, `KeyPair`, `KeyGenerator`, `KeyFactory`, `KeyStore`, etc.,) declares a series of `getInstance(<algorithm>, <provider>)` factory methods which provide a concrete service implementation.
When a specific algorithm is requested the framework searches the registered providers for the corresponding implementation classes and it dynamically allocates objects for concrete service implementations.
The Native Image builder uses static analysis to discover which of these services are used.
It does so by registering reachability handlers for each of the `getInstance()` factory methods.
When it determines that a `getInstance()` method is reachable at run time it automatically performs the reflection registration for all the concrete implementations of the corresponding service type.
This mechanism is implemented in the `com.oracle.svm.hosted.SecurityServicesFeature` class.

The simplest images contain support for the `SecureRandom` and `MessageDigest` engines from the `SUN` provider.
These are core security services needed by the VM itself.

Note: The `--enable-all-security-services` option is now deprecated and it will be removed in a future release.

## Provider Registration
The native image builder captures the list of providers and their preference order from the underlying JVM.
The provider order is specified in the `java.security` file under `<java-home>/lib/security/java.security`.
New security providers cannot be registered at run time; all providers must be statically configured during a native image building.

## SecureRandom

The SecureRandom implementations open the `/dev/random` and `/dev/urandom` files which are used as sources for entropy.
These files are usually opened in class initializers.
To avoid capturing state from the machine that runs the Native Image builder these classes need to be initialized at run time.
