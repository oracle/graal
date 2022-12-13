---
layout: docs
toc_group: embedding
link_title: Embedding Reference
permalink: /reference-manual/embed-languages/
---

# Embedding Languages

* [Compile and Run a Polyglot Application](#compile-and-run-a-polyglot-application)
* [Define Guest Language Functions as Java Values](#define-guest-language-functions-as-java-values)
* [Access Guest Languages Directly from Java](#access-guest-languages-directly-from-java)
* [Access Java from Guest Languages](#access-java-from-guest-languages)
* [Lookup Java Types from Guest Languages](#lookup-java-types-from-guest-languages)
* [Computed Arrays Using Polyglot Proxies](#computed-arrays-using-polyglot-proxies)
* [Host Access](#host-access)
* [Build Native Executables from Polyglot Applications](#build-native-executables-from-polyglot-applications)
* [Code Caching Across Multiple Contexts](#code-caching-across-multiple-contexts)
* [Embed languages in Guest Languages](#embed-languages-in-guest-languages)
* [Build a Shell for Many Languages](#build-a-shell-for-many-languages)
* [Step Through with Execution Listeners](#step-through-with-execution-listeners)
* [Dependency setup](#dependency-setup)

The GraalVM Polyglot API lets you embed and run code from guest languages in JVM-based host applications.

Throughout this section, you will learn how to create a host application in Java that runs on GraalVM and directly calls a guest language.
You can use the tabs beneath each code example to choose between JavaScript, R, Ruby, and Python.

Ensure you set up GraalVM before you begin.

## Compile and Run a Polyglot Application
GraalVM can run polyglot applications written in any language implemented with the [Truffle language implementation framework](../../../truffle/docs/README.md).
These languages are henceforth referenced as **guest languages**.

Complete the steps in this section to create a sample polyglot application that runs on GraalVM and demonstrates programming language interoperability.

1&#46; Create a `hello-polyglot` project directory.

2&#46; In your project directory, add a `HelloPolyglot.java` file that includes
the following code:
{%
include snippet-tabs
tab1type="java" tab1id="Hello_Polyglot_JS" tab1name="JavaScript" tab1path="embed/hello_polyglot_js.java"
tab2type="java" tab2id="Hello_Polyglot_R" tab2name="R" tab2path="embed/hello_polyglot_R.java"
tab3type="java" tab3id="Hello_Polyglot_Ruby" tab3name="Ruby" tab3path="embed/hello_polyglot_ruby.java"
tab4type="java" tab4id="Hello_Polyglot_Python" tab4name="Python" tab4path="embed/hello_polyglot_python.java"
%}

&nbsp;In this code:
- `import org.graalvm.polyglot.*` imports the base API for the Polyglot API.
- `import org.graalvm.polyglot.proxy.*` imports the proxy classes of the Polyglot API, needed in later examples.
- `Context` provides an execution environment for guest languages.
R currently requires the `allowAllAccess` flag to be set to `true` to run the example.
- `eval` evaluates the specified snippet of guest language code.
- The `try` with resource statement initializes the `Context` and ensures that it
is closed after use. Closing the context ensures that all resources including
potential native resources are freed eagerly. Closing a context is optional but
recommended. Even if a context is not closed and no longer referenced it will be
freed by the garbage collector automatically.

3&#46; Run `javac HelloPolyglot.java` to compile `HelloPolyglot.java` with
GraalVM.

4&#46; Run `java HelloPolyglot` to run the application on GraalVM.

You now have a polyglot application that consists of a Java host application and guest language code that run on GraalVM.
You can use this application with other code examples to demonstrate more advanced capabilities of the Polyglot API.

To use other code examples in this section, you simply need to do the following:

1&#46; Add the code snippet to the main method of `HelloPolyglot.java`.

2&#46; Compile and run your polyglot application.

## Define Guest Language Functions as Java Values

Polyglot applications let you take values from one programming language and use them with other languages.

Use the code example in this section with your polyglot application to show how the Polyglot API can return JavaScript, R, Ruby, or Python functions as Java values.

{%
include snippet-tabs
tab1type="java" tab1id="Function_JS" tab1name="JavaScript" tab1path="embed/function_js.java"
tab2type="java" tab2id="Function_R" tab2name="R" tab2path="embed/function_R.java"
tab3type="java" tab3id="Function_Ruby" tab3name="Ruby" tab3path="embed/function_ruby.java"
tab4type="java" tab4id="Function_Python" tab4name="Python" tab4path="embed/function_python.java"
%}

&nbsp;In this code:
- `Value function` is a Java value that refers to a function.
- The `eval` call parses the script and returns the guest language function.
- The first assertion checks that the value returned by the code snippet can be
executed.
- The `execute` call executes the function with the argument `41`.
- The `asInt` call converts the result to a Java `int`.
- The second assertion verifies that the result was incremented by one as expected.

## Access Guest Languages Directly from Java

Polyglot applications can readily access most language types and are not limited to functions.
Host languages, such as Java, can directly access guest language values embedded in the polyglot application.

Use the code example in this section with your polyglot application to show how the Polyglot API can access objects, numbers, strings, and arrays.

{%
include snippet-tabs
tab1type="java" tab1id="Access_JS" tab1name="JavaScript" tab1path="embed/access_js_from_java.java"
tab2type="java" tab2id="Access_R" tab2name="R" tab2path="embed/access_R_from_java.java"
tab3type="java" tab3id="Access_Ruby" tab3name="Ruby" tab3path="embed/access_ruby_from_java.java"
tab4type="java" tab4id="Access_Python" tab4name="Python" tab4path="embed/access_python_from_java.java"
%}

&nbsp;In this code:
- `Value result` is an Object that contains three members: a number named `id`,
a string named `text`, and an array named `arr`.
- The first assertion verifies that the return value can contain members, which
indicates that the value is an object-like structure.
- The `id` variable is initialized by reading the member with the name `id` from
the resulting object. The result is then converted to a Java `int`
using `asInt()`.
- The next assert verifies that result has a value of `42`.
- The `text` variable is initialized using the value of the member `text`,
 which is also converted to a Java `String` using `asString()`.
- The following assertion verifies the result value is equal to the
Java `String` `"42"`.
- Next the `arr` member that holds an array is read.
- Arrays return `true` for `hasArrayElements`. R array instances can have
members and array elements at the same time.
- The next assertion verifies that the size of the array equals three. The
Polyglot API supports big arrays, so the array length is of type `long`.
- Finally we verify that the array element at index `1` equals `42`. Array
indexing with polyglot values is always zero-based, even for languages such as
R where indices start with one.

## Access Java from Guest Languages

Polyglot applications offer bi-directional access between guest languages and host languages.
As a result, you can pass Java objects to guest languages.

Since the Polyglot API is secure by default, access is limited in the default configuration.
To permit guest languages to access any public method or field of a Java object, you have to explicitly specify `allowAllAccess(true)` when the context is built.
In this mode, the guest language code can access any resource that is accessible to host Java code.

Use the code example in this section with your polyglot application to show how guest languages can access primitive Java values, objects, arrays, and functional interfaces.

{%
include snippet-tabs
tab1type="java" tab1id="Access_Java_from_JS" tab1name="JavaScript" tab1path="embed/access_java_from_js.java"
tab2type="java" tab2id="Access_Java_from_R" tab2name="R" tab2path="embed/access_java_from_R.java"
tab3type="java" tab3id="Access_Java_from_Ruby" tab3name="Ruby" tab3path="embed/access_java_from_ruby.java"
tab4type="java" tab4id="Access_Java_from_Python" tab4name="Python" tab4path="embed/access_java_from_python.java"
%}

&nbsp;In this code:
- The Java class `MyClass` has four public fields `id`, `text`, `arr`, and
`ret42`. The fields are initialized with `42`, `"42"`, `new int[]{1, 42, 3}`, and
lambda `() -> 42` that always returns an `int` value of `42`.
- The Java class `MyClass` is instantiated and exported with the name `javaObj`
into the polyglot scope, which allows the host and guest languages to exchange
symbols.
- A guest language script is evaluated that imports the `javaObj` symbol and
assigns it to the local variable which is also named `javaObj`. To avoid
conflicts with variables, every value in the polyglot scope must be explicitly
imported and exported in the top-most scope of the language.
- The next two lines verify the contents of the Java object by comparing it
to the number `42` and the string `'42'`.
- The third verification reads from the second array position and compares it
to the number `42`. Whether arrays are accessed using 0-based or 1-based indices
depends on the guest language. Independently of the language, the Java array
stored in the `arr` field is always accessed using translated 0-based indices. For
example, in the R language, arrays are 1-based so the second array element is
accessible using index `2`. In the JavaScript and Ruby languages, the second
array element is at index `1`. In all language examples, the Java array is read
from using the same index `1`.
- The last line invokes the Java lambda that is contained in the field `ret42`
and compares the result to the number value `42`.
- After the guest language script executes, validation takes place to ensure
that the script returns a `boolean` value of `true` as a result.

## Lookup Java Types from Guest Languages

In addition to passing Java objects to the guest language, it is possible to allow the lookup of Java types in the guest language.

Use the code example in this section with your polyglot application to show how guest languages lookup Java types and instantiate them.

{%
include snippet-tabs
tab1type="java" tab1id="Lookup_Java_from_JS" tab1name="JavaScript" tab1path="embed/lookup_java_from_js.java"
tab2type="java" tab2id="Lookup_Java_from_R" tab2name="R" tab2path="embed/lookup_java_from_R.java"
tab3type="java" tab3id="Lookup_Java_from_Ruby" tab3name="Ruby" tab3path="embed/lookup_java_from_ruby.java"
tab4type="java" tab4id="Lookup_Java_from_Python" tab4name="Python" tab4path="embed/lookup_java_from_python.java"
%}

&nbsp;In this code:
- A new context is created with all access enabled (`allowAllAccess(true)`).
- A guest language script is evaluated.
- The script looks up the Java type `java.math.BigDecimal` and stores it in a variable named `BigDecimal`.
- The static method `BigDecimal.valueOf(long)` is invoked to create new
`BigDecimal`s with value `10`. In addition to looking up static Java methods, it
is also possible to directly instantiate the returned Java type., e.g., in
JavaScript using the `new` keyword.
- The new decimal is used to invoke the `pow` instance method with `20` which calculates  `10^20`.
- The result of the script is converted to a host object by calling `asHostObject()`. The return value is automatically cast to the `BigDecimal` type.
- The result decimal string is asserted to equal to `"100000000000000000000"`.

## Computed Arrays Using Polyglot Proxies

The Polyglot API includes polyglot proxy interfaces that let you customize Java interoperability by mimicking guest language types, such as objects, arrays, native objects, or primitives.

Use the code example in this section with your polyglot application to see how you can implement arrays that compute their values lazily.

> Note: The Polyglot API supports polyglot proxies either on the JVM or in Native Image.

{%
include snippet-tabs
tab1type="java" tab1id="Proxy_JS" tab1name="JavaScript" tab1path="embed/proxy_js.java"
tab2type="java" tab2id="Proxy_R" tab2name="R" tab2path="embed/proxy_R.java"
tab3type="java" tab3id="Proxy_Ruby" tab3name="Ruby" tab3path="embed/proxy_ruby.java"
tab4type="java" tab4id="Proxy_Python" tab4name="Python" tab4path="embed/proxy_python.java"
%}

&nbsp;In this code:
- The Java class `ComputedArray` implements the proxy interface `ProxyArray` so
that guest languages treat instances of the Java class like arrays.
- `ComputedArray` array overrides the method `get` and computes the value
using an arithmetic expression.
- The array proxy does not support write access. For this reason, it throws
an `UnsupportedOperationException` in the implementation of `set`.
- The implementation for `getSize` returns `Long.MAX_VALUE` for its length.
- The main method creates a new polyglot execution context.
- A new instance of the `ComputedArray` class is then exported using the name `arr`.
- The guest language script imports the `arr` symbol, which returns the
exported proxy.
- The second element and the `1000000000`th element is accessed, summed up, and
then returned. Note that array indices from 1-based languages such as R are
converted to 0-based indices for proxy arrays.
- The result of the language script is returned as a long value and verified.

For more information about the polyglot proxy interfaces, see the [Polyglot API JavaDoc](http://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/package-summary.html).

## Host Access

The Polyglot API by default restricts access to certain critical functionality, such as file I/O.
These restrictions can be lifted entirely by setting `allowAllAccess` to `true`.

> Note: The access restrictions are currently only supported with JavaScript.

### Controlling Access to Host Functions

It might be desireable to limit the access of guest applications to the host.
For example, if a Java method is exposed that calls `System.exit` then the guest application will be able to exit the host process.
In order to avoid accidentally exposed methods, no host access is allowed by default and every public method or field needs to be annotated with `@HostAccess.Export` explicitly.

{%
include snippet-tabs
tab1type="java" tab1id="ExplicitHostAccess_js" tab1name="JavaScript" tab1path="embed/explicit_access_java_from_js.java"
%}

&nbsp;In this code:
- The class `Employee` is declared with a field `name` of type `String`. Access to the `getName` method is explicitly allowed by annotating the method with `@HostAccess.Export`.
- The `Services` class exposes two methods, `createEmployee` and `exitVM`. The `createEmployee` method takes the name of the employee as an argument and creates a new `Employee` instance. The `createEmployee` method is annotated with `@HostAccess.Export` and therefore accessible to the guest application. The `exitVM` method is not explicitly exported and therefore not accessible.
- The `main` method first creates a new polyglot context in the default configuration, disallowing host access except for methods annotated with `@HostAccess.Export`.
- A new `Services` instance is created and put into the context as global variable `services`.
- The first evaluated script creates a new employee using the services object and returns its name.
- The returned name is asserted to equal the expected name `John Doe`.
- A second script is evaluated that calls the `exitVM` method on the services object. This fails with a `PolyglotException` as the exitVM method is not exposed to the guest application.

Host access is fully customizable by creating a custom [`HostAccess`](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/HostAccess.html) policy.

### Controlling Host Callback Parameter Scoping

By default, a `Value` lives as long as the corresponding `Context`.
However, it may be desireable to change this default behavior and bind a value to a scope, such that when execution leaves the scope, the value is invalidated.
An example for such a scope are guest-to-host callbacks, where a `Value` may be passed as a callback parameter.
We have already seen above how passing callback parameters works with the default `HostAccess.EXPLICIT`:

```java
public class Services {
    Value lastResult;

    @HostAccess.Export
    public void callback(Value result) {
        this.lastResult = result;
    }

    String getResult() {
        return this.lastResult.asString();
    }
}

public static void main(String[] args) {
    Services s = new Services()
    try (Context context = Context.newBuilder().allowHostAccess(HostAccess.EXPLICIT).build()) {
        context.getBindings("js").putMember("services", s);
        context.eval("js", "services.callback('Hello from JS');");
        System.out.println(s.getResult());
    }
}
```

In this example, `lastResult` maintains a reference to the value from the guest that is stored on the host and remains accessible also after the scope of `callback()` has ended.

However, this is not always desireable, as keeping the value alive may block resources unnecessarily or not reflect the behavior of ephemeral values correctly.
For these cases, `HostAccess.SCOPED` can be used, which changes the default behavior for all callbacks, such that values that are passed as callback parameters are only valid for the duration of the callback.

To make the above code work with `HostAccess.SCOPED`, individual values passed as a callback parameters can be pinned to extend their validity until after the callback returns:
```java
public class Services {
    Value lastResult;

    @HostAccess.Export
    void callback(Value result, Value notneeded) {
        this.lastResult = result;
        this.lastResult.pin();
    }

    String getResult() {
        return this.lastResult.asString();
    }
}

public static void main(String[] args) {
    Services s = new Services()
    try (Context context = Context.newBuilder().allowHostAccess(HostAccess.SCOPED).build()) {
        context.getBindings("js").putMember("services", s);
        context.eval("js", "services.callback('Hello from JS', 'foobar');");
        System.out.println(services.getResult());
    }
}
```

Alternatively, the entire callback method can opt out from scoping if annotated with `@HostAccess.DisableMethodScope`, maintaining regular semantics for all parameters of the callback:
```java
public class Services {
    Value lastResult;
    Value metaInfo;

    @HostAccess.Export
    @HostAccess.DisableMethodScope
    void callback(Value result, Value metaInfo) {
        this.lastResult = result;
        this.metaInfo = metaInfo;
    }

    String getResult() {
        return this.lastResult.asString() + this.metaInfo.asString();
    }
}

public static void main(String[] args) {
    Services s = new Services()
    try (Context context = Context.newBuilder().allowHostAccess(HostAccess.SCOPED).build()) {
        context.getBindings("js").putMember("services", s);
        context.eval("js", "services.callback('Hello from JS', 'foobar');");
        System.out.println(services.getResult());
    }
}
```

### Access Privilege Configuration

It is possible to configure fine-grained access privileges for guest applications.
The configuration can be provided using the [`Context.Builder`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html) class when constructing a new context.
The following access parameters may be configured:

* Allow access to other languages using [`allowPolyglotAccess`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowPolyglotAccess-org.graalvm.polyglot.PolyglotAccess-).
* Allow and customize access to host objects using [`allowHostAccess`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowHostAccess-org.graalvm.polyglot.HostAccess-).
* Allow and customize host lookup to host types using [`allowHostClassLookup`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowHostClassLookup-java.util.function.Predicate-). Allows the guest application to look up the host application classes permitted by the lookup predicate. For example, a Javascript context can create a Java ArrayList, provided that ArrayList is allowlisted by the `classFilter` and access is permitted by the host access policy: `context.eval("js", "var array = Java.type('java.util.ArrayList')")`
* Allow host class loading using [`allowHostClassLoading`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowHostClassLoading-boolean-). Classes are only accessible if access to them is granted by the host access policy.
* Allow the creation of threads using [`allowCreateThread`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowCreateThread-boolean-).
* Allow access to native APIs using [`allowNativeAccess`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowNativeAccess-boolean-).
* Allow access to IO using [`allowIO`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#allowIO-boolean-) and proxy file accesses using [`fileSystem`](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.Builder.html#fileSystem-org.graalvm.polyglot.io.FileSystem-).

> Note: Granting access to class loading, native APIs, or host I/O effectively grants all access, as these privileges can be used to bypass other access restrictions.

## Build Native Executables from Polyglot Applications

Polyglot embeddings can also be compiled ahead-of-time using [Native Image](../native-image/README.md).
By default, no language is included if the Polyglot API is used.
To enable guest languages, the `--language:<languageId>` (e.g., `--language:js`) option needs to be specified.
All examples on this page can be converted to native executables with the `native-image` builder.

The following example shows how a simple HelloPolyglot JavaScript application can be built using `native-image`.

```shell
javac HelloPolyglot.java
native-image --language:js -cp . HelloPolyglot
./hellopolyglot
```

Please note that some languages (e.g. Python, Ruby) need their language home directories to work without limitations.
If the polyglot application runs on a JVM (e.g. [here](#compile-and-run-a-polyglot-application)), the language homes are discovered automatically.
However, for native images, paths to language homes have to be stored in the image or specified at runtime.

By default, the `native-image` builder copies the necessary language homes to the `resources` directory located in the same directory as the produced image.
The paths to the copied homes are written to the image's build artifacts file and also stored in the image itself so that the homes are automatically discovered as long as their relative paths with respect to the image file stay the same.
That means that the `resources` directory should be always distributed together with the image file.     

```shell
native-image --language:python -cp . HelloPolyglot
./hellopolyglot
```

In case an installed GraalVM is available, it is possible to use language homes from the GraalVM home directory. A GraalVM home can be specified at runtime using the option `-Dorg.graalvm.home=$GRAALVM_HOME`, assuming the environment variable `GRAALVM_HOME` is populated with an absolute path to the GraalVM home directory.
Language homes are automatically discovered in the specified directory. For example:

```shell
./hellopolyglot -Dorg.graalvm.home=$GRAALVM_HOME
```

> Note: The `-Dorg.graalvm.home` option has precedence over any relative language home paths stored in the image.

> Note: The version of GraalVM the home of which is specified at runtime must match the version of GraalVM used to build the native executable/library. 

### Excluding the JIT compiler 

It is possible to include a guest language in the native executable, but exclude the JIT compiler by passing the `-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime` option to the builder.
Be aware, the flag `-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime` has to placed *after* all the Truffle language/tool options, so that it will override the default settings.

The following example shows a native image build command that creates an image that will only contain the Truffle language interpreter (the Graal compiler will not be included in the image).
```shell
native-image --language:js -Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime -cp . HelloPolyglotInterpreter
```

### Configuring Native Host Reflection

Accessing host Java code from the guest application requires Java reflection in order to work.
When reflection is used within a native executable, the [reflection configuration file](../native-image/Reflection.md) is required.

For this example we use JavaScript to show host access with native executables.
Copy the following code in a new file named `AccessJavaFromJS.java`.

```java
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.*;
import java.util.concurrent.*;

public class AccessJavaFromJS {

    public static class MyClass {
        public int               id    = 42;
        public String            text  = "42";
        public int[]             arr   = new int[]{1, 42, 3};
        public Callable<Integer> ret42 = () -> 42;
    }

    public static void main(String[] args) {
        try (Context context = Context.newBuilder()
                                   .allowAllAccess(true)
                               .build()) {
            context.getBindings("js").putMember("javaObj", new MyClass());
            boolean valid = context.eval("js",
                   "    javaObj.id         == 42"          +
                   " && javaObj.text       == '42'"        +
                   " && javaObj.arr[1]     == 42"          +
                   " && javaObj.ret42()    == 42")
               .asBoolean();
            System.out.println("Valid " + valid);
        }
    }
}
```

Copy the following code into `reflect.json`:

{% highlight java %}
{% include embed/access_java_from_reflection_config.json %}
{% endhighlight %}

Now you can create a native executable that supports host access:

```shell
javac AccessJavaFromJS.java
native-image --language:js -H:ReflectionConfigurationFiles=reflect.json -cp . AccessJavaFromJS
./accessjavafromjs
```

Note that in case assertions are needed in the image, the `-H:+RuntimeAssertions` option can be passed to `native-image`.
For production deployments, this option should be omitted.

## Code Caching Across Multiple Contexts

The GraalVM Polyglot API allows code caching across multiple contexts.
Code caching allows compiled code to be reused and allows sources to be parsed only once.
Code caching can often reduce memory consumption and warm-up time of the application.

By default, code is cached within a single context instance only.
To enable code caching between multiple contexts, an explicit engine needs to be specified.
The engine is specified when creating the context using the [context builder](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html).
The scope of code sharing is determined by the engine instance.
Code is only shared between contexts associated with one engine instance.

All sources are cached by default.
Caching may be disabled explicitly by setting [cached(boolean cached)](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.Builder.html#cached-boolean-) to `false`. Disabling caching may be useful in case the source is known to only be evaluated once.

Consider the following code snippet as an example:

```java
public class Main {
    public static void main(String[] args) {
        try (Engine engine = Engine.create()) {
            Source source = Source.create("js", "21 + 21");
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                    int v = context.eval(source).asInt();
                    assert v == 42;
            }
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                    int v = context.eval(source).asInt();
                    assert v == 42;
            }
        }
    }
}
```

In this code:
- `import org.graalvm.polyglot.*` imports the base API for the Polyglot API.
- `Engine.create()` creates a new engine instance with the default configuration.
- `Source.create()` creates a source object for the expression “21 + 21”
with "js" language, which is the language identifier for JavaScript.
- `Context.newBuilder().engine(engine).build()` builds a new context with
an explicit engine assigned to it. All contexts associated with an engine share the code.
- `context.eval(source).asInt()` evaluates the source and returns the result as `Value` instance.

## Embed Guest languages in Guest Languages

The GraalVM Polyglot API can be used from within a guest language using Java interoperability.
This can be useful if a script needs to run isolated from the parent context.
In Java as a host language a call to `Context.eval(Source)` returns an instance of `Value`, but since we executing this code as part of a guest language we can use the language-specific interoperability API instead.
It is therefore possible to use values returned by contexts created inside of a language, like regular values of the language.
In the example below we can conveniently write `value.data` instead of `value.getMember("data")`.
Please refer to the individual language documentation for details on how to interoperate with foreign values.
More information on value sharing between multiple contexts can be found [here](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.Builder.html#allowValueSharing-boolean-).

Consider the following code snippet as an example:

```java
import org.graalvm.polyglot.*;

public class Main {
    public static void main(String[] args) {
        try (Context outer = Context.newBuilder()
                                   .allowAllAccess(true)
                               .build()) {
            outer.eval("js", "inner = Java.type('org.graalvm.polyglot.Context').create()");
            outer.eval("js", "value = inner.eval('js', '({data:42})')");
            int result = outer.eval("js", "value.data").asInt();
            outer.eval("js", "inner.close()");

            System.out.println("Valid " + (result == 42));
        }
    }
}
```

In this code:
- `Context.newBuilder().allowAllAccess(true).build()` builds a new outer context with all privileges.
- `outer.eval` evaluates a JavaScript snippet in the outer context.
- `inner = Java.type('org.graalvm.polyglot.Context').create()` the first JS script line looks up the Java host type Context and creates a new inner context instance with no privileges (default).
- `inner.eval('js', '({data:42})');` evaluates the JavaScript code `({data:42})` in the inner context and returns stores the result.
- `"value.data"` this line reads the member `data` from the result of the inner context. Note that this result can only be read as long as the inner context is not yet closed.
- `context.eval("js", "c.close()")` this snippet closes the inner context. Inner contexts need to be closed manually and are not automatically closed with the parent context.
- Finally the example is expected to print `Valid true` to the console.

## Build a Shell for Many Languages

With just a few lines of code, the GraalVM Polyglot API lets you build applications that integrate with any guest language supported by GraalVM.

This shell implementation is agnostic to any particular guest language.

```java
BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
PrintStream output = System.out;
Context context = Context.newBuilder().allowAllAccess(true).build();
Set<String> languages = context.getEngine().getLanguages().keySet();
output.println("Shell for " + languages + ":");
String language = languages.iterator().next();
for (;;) {
    try {
        output.print(language + "> ");
        String line = input.readLine();
        if (line == null) {
            break;
        } else if (languages.contains(line)) {
            language = line;
            continue;
        }
        Source source = Source.newBuilder(language, line, "<shell>")
                        .interactive(true).buildLiteral();
        context.eval(source);
    } catch (PolyglotException t) {
        if(t.isExit()) {
            break;
        }
        t.printStackTrace();
    }
}
```

## Step Through with Execution Listeners

The GraalVM Polyglot API allows users to instrument the execution of guest languages through [ExecutionListener class](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/management/ExecutionListener.html).
For example, it lets you attach an execution listener that is invoked for every statement of the guest language program.
Execution listeners are designed as simple API for polyglot embedders and may become handy in, e.g., single-stepping through the program.

```java
import org.graalvm.polyglot.*;
import org.graalvm.polyglot.management.*;

public class ExecutionListenerTest {
    public static void main(String[] args) {
        try (Context context = Context.create("js")) {
            ExecutionListener listener = ExecutionListener.newBuilder()
                      .onEnter((e) -> System.out.println(
                              e.getLocation().getCharacters()))
                      .statements(true)
                      .attach(context.getEngine());
            context.eval("js", "for (var i = 0; i < 2; i++);");
            listener.close();
        }
    }
}
```

In this code:
- The `Context.create()` call creates a new context for the guest language.
- Create an execution listener builder by invoking `ExecutionListeners.newBuilder()`.
- Set `onEnter` event to notify when element's execution is entered and consumed. At least one event consumer and one filtered source element needs to be enabled.
- To complete the listener attachment, `attach()` needs to be invoked.
- The `statements(true)` filters execution listeners to statements only.
- The `context.eval()` call evaluates a specified snippet of guest language code.
- The `listener.close()` closes a listener earlier, however execution listeners are automatically closed with the engine.

## Polyglot Isolates

On GraalVM Enterprise, a Polyglot engine can be configured to run in a dedicated `native-image` isolate.
This experimental feature is enabled with the `--engine.SpawnIsolate` option.
An engine running in this mode executes within a VM-level fault domain with its own garbage collector and JIT compiler.
The fact that an engine runs within an isolate is completely transparent with respect to the Polyglot API and interoperability:

```java
import org.graalvm.polyglot.*;

public class PolyglotIsolate {
  public static void main(String[] args) {
    Context context = Context.newBuilder("js")
      .allowHostAccess(HostAccess.SCOPED)
      .allowExperimentalOptions(true)
      .option("engine.SpawnIsolate", "true").build();
    Value function = context.eval("js", "x => x+1")
    assert function.canExecute();
    int x = function.execute(41).asInt();
    assert x == 42;
  }
}
```

Since the host's GC and the isolate's GC are not aware of one another, cyclic references between objects on both heaps may occur.
We thus strongly recommend to use [scoped parameters for host callbacks](#controlling-host-callback-parameter-scoping) to avoid cyclic references.

Multiple contexts can be spawned in the same isolated engine by [sharing engines](#code-caching-across-multiple-contexts):

```java
public class PolyglotIsolateMultipleContexts {
    public static void main(String[] args) {
        try (Engine engine = Engine.newBuilder()
                .allowExperimentalOptions(true)
                .option("engine.SpawnIsolate", "js").build()) {
            Source source = Source.create("js", "21 + 21");
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                    int v = context.eval(source).asInt();
                    assert v == 42;
            }
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                    int v = context.eval(source).asInt();
                    assert v == 42;
            }
        }
    }
}
```

Note how we need to specify the language for the isolated engine as a parameter to `--engine.SpawnIsolate` in this case.
The reason is that an isolated engine needs to know which set of languages should be available.
Behind the scenes, GraalVM will then locate the corresponding Native Image language library.
If only a single language is selected, then the library for the language will be loaded.
If multiple languages are selected, then `libpolyglot`, the library containing all Truffle languages shipped with GraalVM, will be loaded.
If a matching library is not available, creation of the engine will fail.

Only one language library can be loaded during GraalVM's lifetime.
This means that the first isolated engine that is created sets the default for the remainder of the execution: if an isolated engine with solely JavaScript was created first, only JavaScript will be available in isolated engines.

### Setting the Heap Size

### Passing Native Image Runtime Options

Engines running in an isolate can make use of [Native Image runtime options](../native-image/HostedvsRuntimeOptions.md) by passing `--engine.IsolateOption.<option>` to the engine builder.
For example, this can be used to limit the maximum heap memory used by an engine by setting the maximum heap size for the isolate via `--engine.IsolateOption.MaxHeapSize=128m`:

```java
import org.graalvm.polyglot.*;

public class PolyglotIsolateMaxHeap {
  public static void main(String[] args) {
    try {
      Context context = Context.newBuilder("js")
        .allowHostAccess(HostAccess.SCOPED)
        .allowExperimentalOptions(true)
        .option("engine.SpawnIsolate", "true")
        .option("engine.IsolateOption.MaxHeapSize", "64m").build()
      context.eval("js", "var a = [];while (true) {a.push('foobar');}");
    } catch (PolyglotException ex) {
      if (ex.isResourceExhausted()) {
        System.out.println("Resource exhausted");
      }
    }
  }
}
```
Exceeding the maximum heap size will automatically close the context and raise a `PolyglotException`.

### Ensuring Host Callback Stack Headroom

With Polyglot Isolates, the experimental `--engine.HostCallStackHeadRoom` option can require a minimum stack size that is guaranteed when performing a host callback.
If the available stack size drops below the specified threshold, the host callback fails.

### Memory Protection

In Linux environments that support Memory Protection Keys, the experimental `--engine.MemoryProtection=true` option can be used to isolate the heaps of Polyglot Isolates at the hardware level.
If an engine is created with this option, a dedicated protection key will be allocated for the isolated engine's heap.
GraalVM will only enable access to the engine's heap when executing code of the Polyglot Isolate.

## Dependency Setup

To best make use of the embedding API of GraalVM (i.e. `org.graalvm.polyglot.*`) your project should use a GraalVM as `JAVA_HOME`.
In addition to that, you should specify the `graal-sdk.jar` (which is included in GraalVM) as a provided dependency to your projects.
This is mainly to provide IDEs and other tools with the information that the project uses this API.
An example of this for Maven means adding the following to the `pom.xml` file.

```xml
<dependency>
    <groupId>org.graalvm.sdk</groupId>
    <artifactId>graal-sdk</artifactId>
    <version>${graalvm.version}</version>
    <scope>provided</scope>
</dependency>
```

Additionally, when using Java modules, your `module-info.java` file should require `org.graalvm.sdk`.

```java
module com.mycompany.app {
  requires org.graalvm.sdk;

}
```