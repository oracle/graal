# [GraalVM](https://graalvm.org/)

GraalVM is a wide-ranging project from [Oracle Labs](https://labs.oracle.com/pls/apex/f?p=LABS:10::::::) developing a new JIT Compiler and Polyglot Runtime for the JVM.
It removes the isolation between programming languages and enables interoperability in a high-performance runtime.
GraalVM includes the following components:

* [Graal SDK](../sdk) contains long term supported APIs of GraalVM.

* [Graal](../compiler) is a dynamic compiler written in Java that integrates with the HotSpot JVM.

* [Truffle](../truffle) is a framework for implementing languages and instruments that use Graal as a dynamic compiler.

* [Tools](../tools) contains a set of tools for Truffle guest languages implemented using the instrumentation framework.

* [Substrate VM](../substratevm) is a framework that allows ahead-of-time (AOT) compilation of Java applications under closed-world assumption into executable images or shared objects.

* [TRegex](../regex) is an implementation of regular expressions which leverages Graal and Truffle for efficient compilation of automata.

## Downloads
Proceed to [Oracle Technology Network](http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) page to download the latest release of Oracle Labs GraalVM bundled with JDK8.
Make sure to accept the OTN License Agreement to download this software.
Our open-source artifacts are also available on [Maven Central](https://mvnrepository.com/artifact/org.graalvm/graal-sdk) repository.

## Documentation
A comprehensive GraalVM documentation can be found [on the website](https://graalvm.org).  
It is divided into several sections:
* [Docs](/docs/)
* [Get Started](/docs/start/)
* [Advanced References](/docs/advanced/)
* [FAQ](/faq/)

If you can't find the answer you need or have a troubleshooting query,
[get in touch with us](/community/).

## Examples
There are multiply examples [on the website](https://graalvm.org).
To demonstrate GraalVM interoperability, let us refer to this tiny example, where you access Java from JavaScript code:

```
$ node --jvm
> var BigInteger = Java.type('java.math.BigInteger');
> console.log(BigInteger.valueOf(2).pow(100).toString(16));
10000000000000000000000000
```
The methods in other GraalVM supported languages can be also called with `--polyglot` flag:
```
$ node --jvm --polyglot
> console.log(Interop.eval('R', 'runif(100)')[0]);
0.8198353068437427
```

## Installing GraalVM
GraalVM is available for Linux and macOS on AMD64, and Solaris on Sparc.
- [Download GraalVM]((http://www.oracle.com/technetwork/oracle-labs/program-languages/downloads/index.html) and extract the archive to your file system.
- Add the GraalVM `/bin` folder to your `PATH` environment variable in order to deploy the executables.
- Optionally set the `JAVA_HOME` environment variable to resolve to the GraalVM installation directory.
You can also specify GraalVM as the JRE or JDK installation in your Java ID.

## Contributing
Get acquainted with our [contributing guide](../compiler/CONTRIBUTING.md) to learn how you can take part in improving Graal.
Contribute your code or improvements on [GitHub](https://github.com/oracle/graal).


## Code of Conduct
We adopted a Code of Conduct that we expect project contributors to follow.  
Please read our [Contributor Covenant Code of Conduct](/community/) to understand what actions will and will not be tolerated.

## Issues
Please report bugs or enhancement requests on [GitHub Issues](https://github.com/oracle/graal/issues).
We listen to input and feedback from the community.
We cannot give guarantees for issues to be addressed and will make decisions whether to address an issue based on the overall project direction.
Customers using the enterprise edition can report their bugs via the standard support channels and according to the service level agreements.

## License
There is a community edition and an enterprise edition of Graal VM.
The community edition is distributed under a GPLv2 open source license. 
It is free to use in production and comes with no strings attached, but also no guarantees or support.
The enterprise edition is available from the Oracle Technology Network under an evaluation license.
It provides improved performance and security for production deployments. Each GraalVM component is licensed:
[Truffle](../truffle) and its dependency [Graal SDK](../sdk) are licensed under the [GPL 2 with Classpath exception](../truffle/LICENSE.GPL.md).
The [Tools](../tools) project is licensed under the [GPL 2 with Classpath exception](../tools/LICENSE.GPL.md).
The [TRegex](../regex) project is licensed under the [GPL 2 with Classpath exception](../regex/LICENSE.GPL.md).
The [Graal compiler](../compiler) is licensed under the [GPL 2](../compiler/LICENSE.md).
The [Substrate VM](../substratevm) is licensed under the [GPL 2](../substratevm/LICENSE.md).
