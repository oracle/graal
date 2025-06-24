---
layout: ni-docs
toc_group: how-to-guides
link_title: Troubleshoot Native Image Run-Time Errors
permalink: /reference-manual/native-image/guides/troubleshoot-run-time-errors/
---

# Troubleshoot Native Image Run-Time Errors

A successful ahead-of-time compilation can still generate images that crash at run time or do not behave the same way as the application would behave on a Java VM. 
In this guide, some reasons for that are shown, along with the strategies to diagnose and resolve the issues. 

Note that sometimes upgrading to the latest version of GraalVM can already resolve an issue.

### 1. Diagnose Missing Metadata Registration

Start by diagnosing if there is any metadata configuration missing.
Native Image requires all utilized classes to be known during the build.
The static analysis tries to make predictions about the run-time behavior of your application.
In some cases, you need to provide the analysis with configuration to make all dynamic feature calls visible to it.
Failing to do so will result in an image that terminates at run-time with hard-to-diagnose errors once the dynamic feature is used in the application.
This can be avoided by eagerly checking for missing metadata.

1. Pass the `--exact-reachability-metadata` option to the `native-image` tool and rebuild the application. If you want to do this only for a specific package, specify a package prefix `--exact-reachability-metadata=[package prefix]`.
    
    > This option was introduced in GraalVM for JDK 23 for debugging purposes. In GraalVM versions prior to JDK 23, use the `-H:ThrowMissingRegistrationErrors=` build option instead.

2. Run the generated native executable passing the `-XX:MissingRegistrationReportingMode=Warn` option to find all places in your code where missing registrations occur.

    > `-XX:MissingRegistrationReportingMode=` was promoted to a run-time option in GraalVM for JDK 23. In GraalVM versions prior to JDK 23, use the `-H:MissingRegistrationReportingMode=Warn` build option instead.

3. If there is some missing metadata reported, make sure to add it to the _reachability-metadata.json_ file. See how to do it in the [Reachability Metadata documentation](https://www.graalvm.org/latest/reference-manual/native-image/metadata/#specifying-metadata-with-json).

    > It is not always necessary to add all reported elements to _reachability-metadata.json_. The one causing the program failure is usually among the last listed.

    > In GraalVM versions prior to JDK 23, errors may be reported for elements already present in _reachability-metadata.json_. These can be safely ignored, as they result from the experimental nature of the `-H:ThrowMissingRegistrationErrors=` option.

#### Shared Libraries

For diagnosing shared libraries built with Native Image, you can either:
* specify `-R:MissingRegistrationReportingMode=Exit` when building a native shared library;
* or specify `-XX:MissingRegistrationReportingMode=Exit` when the isolate is created. `graal_create_isolate_params_t` has `argc (_reserved_1)` and `argv (_reserved_2)` fields that can be used to pass C-style command-line options at run time. However, note that both fields are currently not public APIs.

### 2. Set java.home Explicitly

If your application code uses the `java.home` property, set it explicitly with `-Djava.home=<path>` when running a native executable.
Otherwise, the `System.getProperty("java.home")` call will return a `null` value.

### 3. Enable URL Protocols

Try enabling all URL protocols on-demand at build time: `--enable-url-protocols=<protocols>`.
To enable the HTTPS support only, pass `--enable-https`. 

### 4. Include All Charsets and Locales

Other handy options are `-H:+AddAllCharsets` to add charsets support, and `-H:+IncludeAllLocales` to pre-initialize support for locale-sensitive behavior in the `java.util` and `java.text` packages. 
Pass those options at build time.
This might increase the size of the resulting binary.

### 5. Add Missing Security Providers

If your application is using Security Providers, try to pre-initialize security providers by passing the option `-H:AdditionalSecurityProviders=<list-of-providers>` at build time. 
Here is a list of all JDK security providers to choose from:
`sun.security.provider.Sun,sun.security.rsa.SunRsaSign,sun.security.ec.SunEC,sun.security.ssl.SunJSSE,com.sun.crypto.provider.SunJCE,sun.security.jgss.SunProvider,com.sun.security.sasl.Provider,org.jcp.xml.dsig.internal.dom.XMLDSigRI,sun.security.smartcardio.SunPCSC,sun.security.provider.certpath.ldap.JdkLDAP,com.sun.security.sasl.gsskerb.JdkSASL`.

### 6. File a Native Image Run-Time Issue

Only if you tried all the above suggestions, file a [Native Image Run-Time Issue Report](https://github.com/oracle/graal/issues/new?assignees=&labels=native-image%2Cbug%2Crun-time&projects=&template=1_1_native_image_run_time_bug_report.yml&title=%5BNative+Image%5D+) at GitHub, filling out the necessary information. 

To gather the required information for filing a proper and actionable ticket, it is recommended to run a `native-image` build with the diagnostics mode enabled. 
Pass the `--diagnostics-mode` option enabling diagnostics output for class initialization, substitutions, and so on.

### Related Documentation

* [Specifying Metadata with JSON](../ReachabilityMetadata.md#specifying-metadata-with-json)
