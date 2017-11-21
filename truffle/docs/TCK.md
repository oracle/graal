# The polyglot API based Test Compatibility Kit

## Overview
The test compatibility kit (TCK) is a collection of tests verifying the [TruffleLanguage](http://graalvm.github.io/graal/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html)
inter-operability and instrumentation. The TCK is based on the org.graalvm.polyglot API.

## Adding a language
To test your language implement the [LanguageProvider](http://graalvm.github.io/graal/truffle/javadoc/org/graalvm/polyglot/tck/LanguageProvider.html).
The `LanguageProvider`s are loaded using the `java.util.ServiceLoader` therefore you need to register your
implementation in the `META-INF/services/org.graalvm.polyglot.tck.LanguageProvider` file.
The `LanguageProvider` should provide the language data types, language expressions (operators) and language control flow statements
represented as functions returning the data type or executing the operator (statement).
To allow composition of the returned functions the parameter and return types have to be assigned to them using
the [Snippet.Builder](http://graalvm.github.io/graal/truffle/javadoc/org/graalvm/polyglot/tck/Snippet.Builder.html).
The `LanguageProvider` should also provide simple but representative scripts which the TCK can use to test
instrumentation.

## Running tests
The tests are executed using `mx unitest`. When running the tests all `LanguageProvider`s in the primary suite
and dependent suites are used. The `truffle` suite provides the `java-host` `LanguageProvider` creating java data
types and [Proxies](http://graalvm.github.io/graal/truffle/javadoc/org/graalvm/polyglot/proxy/Proxy.html) to
test java inter-operability.

To run just the TCK tests use:

`mx unittest com.oracle.truffle.tck.tests`

To restrict the TCK tests to test certain language use the `tck.language` property. The following
example tests JavaScript with data types from all available languages:

`mx unittest -Dtck.language=js com.oracle.truffle.tck.tests`

To restrict the data types to certain language use the `tck.values` property. The following
example tests JavaScript with Java types:

`mx unittest -Dtck.values=java-host -Dtck.language=js com.oracle.truffle.tck.tests`
