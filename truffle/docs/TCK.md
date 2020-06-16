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

## Running TCK tests with mx
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

To run a single test specify the full test name. For example to run a test for SimpleLanguage `+` operator
with SimpleLanguage `number` and `big number` use:

`mx tck 'ExpressionTest#testExpression[sl::+(sl::number, sl::number)]'`

To run the TCK tests on the GraalVM it's enough to set the mx `--java-home` to point to the GraalVM:

`mx --java-home=<path_to_graalvm> tck`

To disable output and error output use the `tck.verbose` property:

`mx tck -Dtck.verbose=false`

To disable output and error output only for a certain test use the `tck.{TestSimpleName}.verbose` property:

`mx tck -Dtck.ErrorTypeTest.verbose=false`

You can also disable output and error output for all tests but one:

`mx tck -Dtck.verbose=false -Dtck.ErrorTypeTest.verbose=true`

## Running TCK tests without mx
The python [TCK runner](../mx.truffle/tck.py) can be used to execute the Truffle TCK on top of the GraalVM. The script requires
a Maven for downloading the TCK artifacts.

To execute TCK tests on given GraalVM use:

`python tck.py -g <path_to_graalvm>`

To include own language and TCK provider use:

`python tck.py -g <path_to_graalvm> -cp <path_to_tck_provider_jars> -lp <path_to_language_jars>`

To restrict tests to certain language use the language id as a first unnamed option. The following example executes tests only for the JavaScript language:

`python tck.py -g <path_to_graalvm> js`

To execute the tests under debugger use the `-d` or `--dbg <port>` option.

`python tck.py -d -g <path_to_graalvm>`

The TCK tests can be filtered by test names. To execute just the `ScriptTest` for JavaScript TCK provider use:
`python tck.py -g <path_to_graalvm> js default ScriptTest`

The TCK tests can be executed in a compile mode in which all calltargets are compiled before they are executed.
To execute JavaScript tests in a compile mode use:
`python tck.py -g <path_to_graalvm> js compile`
