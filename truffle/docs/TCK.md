# The polyglot API based Test Compatibility Kit

## Overview
The test compatibility kit (TCK) is a collection of tests verifying the [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html)
inter-operability and instrumentation. The TCK is based on the org.graalvm.polyglot API.

## Adding a language
To test your language implement the [LanguageProvider](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/LanguageProvider.html).
The `LanguageProvider`s are loaded using the `java.util.ServiceLoader` therefore you need to register your
implementation in the `META-INF/services/org.graalvm.polyglot.tck.LanguageProvider` file.
The `LanguageProvider` should provide the language data types, language expressions (operators) and language control flow statements
represented as functions returning the data type or executing the operator (statement).
To allow composition of the returned functions the parameter and return types have to be assigned to them using
the [Snippet.Builder](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/Snippet.Builder.html).
The `LanguageProvider` should also provide simple but representative scripts which the TCK can use to test
instrumentation.

## Running tests
The tests are executed using `mx unitest`. When running the tests all `LanguageProvider`s in the primary suite
and dependent suites are used. The `truffle` suite provides the `java-host` `LanguageProvider` creating java data
types and [Proxies](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/proxy/Proxy.html) to
test java inter-operability.

To run just the TCK tests use:

`mx unittest com.oracle.truffle.tck.tests`

or simply:

`mx tck`

To restrict the TCK tests to test certain language use the `tck.language` property. The following
example tests JavaScript with data types from all available languages:

`mx tck -Dtck.language=js`

To restrict the data types to certain language use the `tck.values` property. The following
example tests JavaScript with Java types:

`mx tck -Dtck.values=java-host -Dtck.language=js`

To run the TCK tests on the GraalVM it's enough to set the mx `--java-home` to point to the GraalVM:

`mx --java-home=<path_to_graalvm> tck`

To enable output and error output use the `tck.verbose` property:

`mx tck -Dtck.verbose=true`

To enable output and error output only for a certain test use the `tck.{TestSimpleName}.verbose` property:

`mx tck -Dtck.ErrorTypeTest.verbose=true`

You can also enable output and error output for all tests but one:

`mx tck -Dtck.verbose=true -Dtck.ErrorTypeTest.verbose=false`
