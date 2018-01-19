# Hello Polyglot World

The Graal Polyglot SDK lets you embed and run code from guest languages in
JVM-based host applications.

Throughout this section you learn how to create a host application in Java that
runs on GraalVM and directly calls a guest language. You will learn this using the JavaScript language as an example.
The Javadoc reference for the polyglot API can be found [here](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html).

Ensure you set up GraalVM before you begin. See [Get Started](../README.md).

## Compile and Run a Polyglot Application

Polyglot applications run code written in any programming language that GraalVM
supports. These programming languages are also known as Graal languages.

Complete the steps in this section to create a sample polyglot
application that runs on GraalVM and demonstrates programming language
interoperability.

1&#46; Create a `hello-polyglot` project directory.

2&#46; In your project directory, add a `HelloPolyglot.java` file that includes
the following code:

```java
public class HelloPolyglot {
    public static void main(String[] args) {
        System.out.println("Hello Java!");
        Context context = Context.create();
        context.eval("js", "print('Hello JavaScript!');");
    }
}
```

&nbsp;In this code:
* `import org.graalvm.polyglot.*` imports the API for the Graal Polyglot SDK.
* `Context` provides an execution environment for guest languag* .
* `eval` evaluates the specified snippet of guest language code.

3&#46; Run `javac HelloPolyglot.java` to compile `HelloPolyglot.java` with
GraalVM.

4&#46; Run `java HelloPolyglot` to run the application on GraalVM.

You now have a polyglot application that consists of a Java host application
and guest language code that run on GraalVM. You can use this application with
other code examples to demonstrate more advanced capabilities of the
Graal Polyglot SDK.

To use other code examples in this section, you simply need to do the following:

1&#46; Add the code snippet to the main method of `HelloPolyglot.java`.

2&#46; Compile and run your polyglot application.

## Define Guest Language Functions as Java Values
Polyglot applications let you take values from one programming language and
use them with other languages.

Use the code example in this section with your polyglot application to show
how the Graal Polyglot SDK can return JavaScript, R, Ruby or Python functions as Java
values.

```java
Context context = Context.create();
Value function = context.eval("js", "function(x) x + 1");
assert function.canExecute();
int x = function.execute(41).asInt();
assert x == 42;
```

&nbsp;In this code:
- `Value` is a Java value that refers to a function.
- The `eval` call parses the script and returns the Graal language function.
- The first assertion checks that the value returned by the code snippet can be
executed.
- The `execute` call executes the function with the argument `41`.
- The `asInt` call converts the result to a Java `int`.
- The second assertion checks that the Java integer has a value of `42`.

## Access Graal Languages Directly from Java
Polyglot applications, can readily access most language types and are not
limited to functions. Host languages, such as Java, can directly access guest
language code embedded in the polyglot application.

Use the code example in this section with your polyglot application to show
how the Graal Polyglot SDK can access numbers, strings, and arrays.

```java
Context context = Context.create();
Value result = context.eval("js", "({ "   +
                    "id     : 42, "       +
                    "text   : '42', "     +
                    "arr    : [1,42,3] " +
                "})");
assert result.hasMembers();

int id = result.getMember("id").asInt();
assert id == 42;

String text = result.getMember("text").asString();
assert text.equals("42");

Value array = result.getMember("arr");
assert array.hasArrayElements();
assert array.getArraySize() == 3;
assert array.getArrayElement(1).asInt() == 42;
```

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
Graal Polyglot SDK supports big arrays, so the array length is of type `long`.
- Finally we verify that the array element at index `1` equals `42`. Array
indexing with polyglot values is always zero-based, even for languages such as
R where indices start with one.

## Access Java from Graal Languages
Polyglot applications offer bi-directional access between guest languages and
host languages. As a result, you can pass Java objects to Graal languages.

Use the code example in this section with your polyglot application to show how
Graal languages can access primitive Java values, objects, arrays, and
functional interfaces.

**Note:** This code example is supported when running on the Java Virtual
Machine (JVM) only. Running in native image executables is not currently
supported.

```java
public static class MyClass {
    public int               id    = 42;
    public String            text  = "42";
    public int[]             arr   = new int[]{1, 42, 3};
    public Callable<Integer> ret42 = () -> 42;
}

public static void main(String[] args) {
    Context context = Context.create();
    context.exportSymbol("javaObj", new MyClass());
    boolean valid = context.eval("js",
           "javaObj = Interop.import('javaObj');" +
           "    javaObj.id         == 42"         +
           " && javaObj.text       == '42'"       +
           " && javaObj.arr[1]     == 42"         +
           " && javaObj.ret42()    == 42")
       .asBoolean();
    assert valid == true;
}
```

&nbsp;In this code:
- The Java class `MyClass` has four public fields `id`, `text`, `arr` and
`ret42`. The fields are initialized with `int`, `String`, `int[]` and
lambda `() -> 42` that always returns an `int` value of `42`.
- The Java class `MyClass` is instantiated and exported with the name `javaObj`
into the polyglot scope, which allows the host and guest languages to exchange
symbols.
- A Graal language script is evaluated that imports the `javaObj` symbol and
assigns it to the local variable which is also named `javaObj`. To avoid
conflicts with variables, every value in the polyglot scope must be explicitly
imported and exported in the top-most scope of the language.
- The next two lines verify the contents of the Java object by comparing it
to the number `42` and the string `'42'`.
- The third verification reads from the second array position and compares it
to the number `42`. Whether arrays are accessed using 0-based or 1-based indices
depends on the Graal language. Independently of the language, the Java array
stored in field `arr` is always accessed using translated 0-based indices. For
example, in the R language, arrays are 1-based so the second array element is
accessible using index `2`. In the JavaScript and Ruby languages, the second
array element is at index `1`. In all language examples, the Java array is read
from using the same index `1`.
- The last line invokes the Java lambda that is contained in the field `ret42`
and compares the result to the `42` number value.
- After the Graal language script executes, validation takes place to ensure
that the script returns a `boolean` value of `true` as a result.


## Computed Arrays Using Polyglot Proxies
The Graal Polyglot SDK includes polyglot proxy interfaces that let you
customize Java interoperability by mimicking guest language types, such as
objects, arrays, native objects, or primitives.

Use the code example in this section with your polyglot application to see how
you can implement arrays that compute their values lazily.

**Note:** The Graal Polyglot SDK supports polyglot proxies either on the JVM or
in a native image executable.

```java
static class ComputedArray implements ProxyArray {
    public Object get(long index) {
        return index * 2;
    }
    public void set(long index, Value value) {
        throw new UnsupportedOperationException();
    }
    public long getSize() {
        return Long.MAX_VALUE;
    }
}

public static void main(String[] args) {
    Context context = Context.create();
    context.exportSymbol("arr", new ComputedArray());
    long result = context.eval("js",
           "arr = Interop.import('arr');" +
           "arr[1] + arr[1000000000]")
       .asLong();
    assert result == 2000000002L;
}
```

&nbsp;In this code:
- The Java class `ComputedArray` implements the proxy interface `ProxyArray` so
that Graal languages treat instances of the Java class like arrays.
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

For more information about the polyglot proxy interfaces, see the
[Polyglot API JavaDoc](http://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/proxy/package-summary.html).


## Reliable Timeouts for Malicious Code
If your polyglot application runs untrusted code from a third-party source, the
Graal Polyglot SDK lets you reliably cancel guest language execution after a
timeout interval to protect against malicious code.

**Note:** A similar security feature for memory allocation is planned for a
future version of the Graal Polyglot SDK.

Use the code example in this section with your polyglot application to see how
malicious code can be reliably cancelled.

```java
Context context = Context.create();
context.initialize("js");

Timer timer = new Timer(true);
timer.schedule(new TimerTask() {
    @Override
    public void run() {
        context.close(true);
    }
}, 1000);

try {
    String maliciousCode = "while(true);";
    context.eval("js", maliciousCode);
    assert false;
} catch (PolyglotException e) {
    assert e.isCancelled();
}
```

&nbsp;In this code:
- The first line of code creates a new context for any language.
- The second line of code ensures the language is initialized. This prevents
the execution from being cancelled during context initialization.
- A new `java.util.Timer` instance is created.
- To close the context, a new `TimerTask` is scheduled to run after one
second. The `true` parameter to `close` indicates that the execution should be
cancelled if it is currently running.
- Malicious code, an infinite while loop, is evaluated. The malicious code will
never complete. For this reason, the timer task closes and cancels the
execution. This scenario causes a `PolyglotException` to be thrown.
- Finally, in the exception catch block, the exception is verified as
originating from a cancel event.


## Build a Shell for Many Languages
With just a few lines of code, the Graal Polyglot API lets you build
applications that integrate with any Graal language that is available by
default with GraalVM.

This shell implementation is agnostic to any particular Graal language.

```java
BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
PrintStream output = System.out;
Context context = Context.create();
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