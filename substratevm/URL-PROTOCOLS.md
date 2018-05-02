URL Protocols on Substrate VM
-----------------------------

On Substrate VM URL protocols can be divided in four classes:

### Supported and enabled by default
These are protocols that are enabled by default and added to every built image.
Currently `file` is the only supported URL protocol enabled by default.

### Supported and disabled by default
These are protocols that are supported but are not enabled by default when building an image.
They must be enabled during image building by adding the `-H:EnableURLProtocols=<protocol>` option to the `native-image` command.
The option accepts a list of comma separated protocols.
The rationale behind enabling protocols on demand is that you can start with a minimal image and add features as you need them.
This way your image will only include the features that you use which helps keeping the overall size small.
Currently `http` is the only URL protocol that can be enabled on demand.

### Unsupported and disallowed
These are protocols that are known to not work on Substrate VM.
If you try to enable them, i.e., using `-H:EnableURLProtocols=<protocol>`, the image might still build but you will get a runtime error.
The `https` protocol is currently the only protocol that was tested and is known to not work; it is however under development and will be supported soon.

### Not tested
No other URL protocols are currently tested.
They can still be enabled using `-H:EnableURLProtocols=<protocol>`, however they might not work as expected.