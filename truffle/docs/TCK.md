---
layout: docs
toc_group: truffle
link_title: Polyglot API Based TCK
permalink: /graalvm-as-a-platform/language-implementation-framework/TCK/
---
# Polyglot API-based Test Compatibility Kit

The Test Compatibility Kit (TCK) is a collection of tests verifying the [TruffleLanguage](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) inter-operability and instrumentation.
The TCK is based on the `org.graalvm.polyglot` API.

## Adding a Language

To test your language, implement the [LanguageProvider](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/LanguageProvider.html).
The `LanguageProvider`s are loaded using the `java.util.ServiceLoader`, so you need to register your implementation in the `META-INF/services/org.graalvm.polyglot.tck.LanguageProvider` file.
The `LanguageProvider` should provide the language data types, language expressions (operators), and language control flow statements represented as functions returning the data type or executing the operator (statement).
To allow composition of the returned functions, the parameter and return types have to be assigned to them using
the [Snippet.Builder](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/tck/Snippet.Builder.html).
The `LanguageProvider` should also provide simple but representative scripts which the TCK can use to test instrumentation.

## Running TCK Tests with `mx`

The tests are executed using `mx unitest`. When running the tests, all `LanguageProvider`s in the primary suite and dependent suites are used. The `truffle` suite provides the `java-host` `LanguageProvider`, creating Java data types and [Proxies](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/proxy/Proxy.html) to test Java inter-operability.

To run just the TCK tests use:

`mx unittest com.oracle.truffle.tck.tests`

Or, simply use:

`mx tck`

To restrict the TCK tests to test a certain language, use the `tck.language` property.
The following example tests JavaScript with data types from all available languages:

`mx tck -Dtck.language=js`

To restrict the data types to a certain language, use the `tck.values` property.
The following example tests JavaScript with Java types:

`mx tck -Dtck.values=java-host -Dtck.language=js`

To run a single test, specify the full test name.
For example, to run a test for SimpleLanguage `+` operator with SimpleLanguage `number` and `big number` use:

`mx tck 'ExpressionTest#testExpression[sl::+(sl::number, sl::number)]'`

To run the TCK tests on GraalVM it is enough to set the mx `--java-home` to point to GraalVM:

`mx --java-home=<path_to_graalvm> tck`

To disable output and error output use the `tck.verbose` property:

`mx tck -Dtck.verbose=false`

To disable output and error output only for a certain test, use the `tck.{TestSimpleName}.verbose` property:

`mx tck -Dtck.ErrorTypeTest.verbose=false`

You can also disable output and error output for all tests but one:

`mx tck -Dtck.verbose=false -Dtck.ErrorTypeTest.verbose=true`

## Running TCK Tests with Apache Maven
The Apache Maven can be used to execute Truffle TCK tests. First, create a Maven module (project) containing the language
TCK provider. Ensure that this module has a test dependency on the language being tested and TCK tests `org.graalvm.truffle:truffle-tck-tests`.
Configure the `maven-surefire-plugin` to identify tests in the `org.graalvm.truffle:truffle-tck-tests` artifact.
This can be achieved using the following snippet within the <build> section of your project's pom.xml:
```
<build>
    <plugins>
        [...]
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.1.2</version>
            <configuration>
                <dependenciesToScan>
                    <dependency>org.graalvm.truffle:truffle-tck-tests</dependency>
                </dependenciesToScan>
            </configuration>
        </plugin>
        [...]
    </plugins>
</build>
```
To include additional languages in the TCK execution add their TCK providers as test dependencies. For example, adding `org.graalvm.js:js-truffle-tck` will include JavaScript in the testing process.
You can utilize the SimpleLanguage TCK provider [pom.xml](https://github.com/oracle/graal/blob/master/truffle/external_repos/simplelanguage/tck/pom.xml) as a template to get started.
To test the runtime optimizations set the `JAVA_HOME` environment variable to the GraalVM location before running `mvn package`.

### Customize TCK Tests
To restrict the TCK tests to test a certain language, use the `tck.language` property.
The following example tests JavaScript with data types from all available languages.
```
<build>
    <plugins>
        [...]
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.1.2</version>
            <configuration>
                <argLine>
                    -Dtck.language=js
                </argLine>
                [...]
            </configuration>
        </plugin>
        [...]
    </plugins>
</build>
```

To restrict the data types to a certain language, use the `tck.values` property.
The following example tests JavaScript with Java types.
```
<build>
    <plugins>
        [...]
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.1.2</version>
            <configuration>
                <argLine>
                    -Dtck.values=java-host
                    -Dtck.language=js
                </argLine>
                [...]
            </configuration>
        </plugin>
        [...]
    </plugins>
</build>
```

To execute a specific TCK test you can use the test parameter along with the `-Dtest` option.
For example: `mvn test -Dtest=ScriptTest`
