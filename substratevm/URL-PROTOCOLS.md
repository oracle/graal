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
This adds to the generated image the code required by the JCA, including statically linking native libraries that the JCA may depend on.
See the [documentation on security services](JCA-SECURITY-SERVICES.md) for more details.

### Not tested
No other URL protocols are currently tested.
They can still be enabled using `--enable-url-protocols=<protocols>`, however they might not work as expected.