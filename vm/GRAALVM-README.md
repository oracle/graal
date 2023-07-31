# GraalVM

GraalVM compiles your Java applications ahead of time into standalone binaries that start instantly, provide peak performance with no warmup, and use fewer compute resources. You can use GraalVM just like any other Java Development Kit in your IDE.

GraalVM provides the following benefits:

* **Low Resource Usage**: Java applications compiled ahead-of-time by GraalVM require less memory and CPU to run. No memory and CPU cycles are spent on just-in-time compilation. As a result, your applications need fewer resources to run and are cheaper to operate at scale.
* **Improved Security**: GraalVM reduces the attack surface of your Java application by excluding the following from the binary: unreachable code (unused classes, methods, and fields), the just-in-time compilation infrastructure, and build-time initialized code. GraalVM's closed world assumption prevents your application from loading unknown code by disabling dynamic features such as reflection, serialization, and so on at run time, and requires an explicit include list of such classes, methods, and fields at build time. GraalVM can embed a software bill of materials (SBOM) in your binary making it easier for you to use common security scanners to check your Java application binaries for published CVEs (Common Vulnerabilities and Exposures).
* **Fast Startup**: With GraalVM, you can start your Java applications faster by initializing parts of the application at build time instead of run time, and instantly achieve predictable peak performance with no warmup.
* **Compact Packaging**: Java applications compiled ahead-of-time by GraalVM are small and can be easily packaged into lightweight container images for fast and efficient deployment.
* **Easily Build Cloud Native Microservices**: Popular microservices frameworks such as Spring Boot, Micronaut, Helidon, and Quarkus, and cloud platforms such as Oracle Cloud Infrastructure (OCI), Amazon Web Services (AWS), Google Cloud Platform (GCP), and Microsoft Azure all support GraalVM. This makes it easy for you to build cloud native Java microservices, compiled as binaries, packaged in small containers, and run on cloud platforms - OCI, AWS, GCP and Azure.
* **Extend your Java Application with Python and Other Languages**: With GraalVM you can embed languages such as Python, JavaScript, and others to extend your Java application.
* **Use Existing Development and Monitoring Tools**: Your existing Java application development and monitoring tools work with GraalVM application binaries. GraalVM provides build plugins for Maven and Gradle, and GitHub Actions for CI/CD. GraalVM supports Java Flight Recorder (JFR), Java Management Extensions (JMX), heap dumps, VisualVM, and other monitoring tools. GraalVM works with existing Java editors/IDEs, and unit test frameworks such as JUnit.

## Resources

- [graalvm.org](https://www.graalvm.org/): the GraalVM website provides a rich set of developer guides, reference manuals, code snippets, security guidelines, and API documentation--everything you need to get started with GraalVM.
- [GraalVM demos](https://github.com/graalvm/graalvm-demos): the GitHub repository contains example applications.
- [GraalVM SDK Javadoc](http://www.graalvm.org/sdk/javadoc): Java APIs for application developers, or those who write Java compatibility tests, or seek to re-implement the GraalVM platform.