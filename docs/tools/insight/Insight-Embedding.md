---
layout: docs-experimental
toc_group: tools
link_title: Embedding Insight into Applications
permalink: /tools/graalvm-insight/embedding/
---

# Embedding Insight into Applications

## Embedding Insight into Java

GraalVM languages (languages implemented with the Truffle framework, i.e., JavaScript, Python, Ruby, R) can be embedded into custom Java applications via [Polyglot Context API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html).
GraalVM Insight can also be controlled via the same API.
For example:

```java
final Engine engine = context.getEngine();
Instrument instrument = engine.getInstruments().get("insight");
Function<Source, AutoCloseable> access = instrument.lookup(Function.class);
AutoCloseable handle = access.apply(agentSrc);
```

Obtain `Engine` for `Context` and ask for the `insight` instrument.
Then create `Source` with the GraalVM Insight script and apply it while obtaining its instrumentation handle.
Use `handle.close()` to disable all the script's instrumentations when when no longer needed.

### Ignoring Internal Scripts

Often one wants to treat certain code written in a dynamic language as a priviledged one.
Imagine various bindings to OS concepts or other features of one's application.
Such scripts are better to remain blackboxed and hidden from GraalVM Insight instrumentation capabilities.

To hide priviledged scripts from sight, [mark them as internal](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.Builder.html#internal-boolean-).
By default GraalVM Insight ignores and does not process internal scripts.

### Extending Functionality of Insight Scripts

When embedding GraalVM Insight into a Java application, you can make additional objects available to the Insight scripts being evaluated.
For example:

```java
@TruffleInstrument.Registration(
    id = "meaningOfWorld", name = "Meaning Of World", version = "demo",
    services = { Insight.SymbolProvider.class }
)
public final class MeaningOfWorldInstrument extends TruffleInstrument {
    @Override
    protected void onCreate(Env env) {
        Map<String, Integer> symbols = Collections.singletonMap("meaning", 42);
        Insight.SymbolProvider provider = () -> symbols;
        env.registerService(provider);
    }
}
```

The previous Java code creates an instrument which registers a new symbol `meaning` to every Insight script evaluated.
Each script can then reference it and use it, for example, to limit the number of method invocations:

```java
insight.on('enter', (ctx, frames) => { if (--meaning <= 0) throw 'Stop!' }, { roots : true });
```

It is possible to expose simple values, as well as complex objects.
See the [javadoc](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.SymbolProvider.html) for more detailed information.
Note that instrumentation can alter many aspects of program execution and are not subject to any security sandbox.

## Embedding Insight into Node.js

The [Insight Manual](Insight-Manual.md) shows many examples of using GraalVM Insight with `node`.
However most of them rely on the command line option `--insight` and do not benefit from the dynamic nature of the tool.
The next example shows how to create an admin server.

Save this code to `adminserver.js`:
```js
function initialize(insight, require) {
    const http = require("http");
    const srv = http.createServer((req, res) => {
        let method = req.method;
        if (method === 'POST') {
            var data = '';
            req.on('data', (chunk) => {
                data += chunk.toString();
            });
            req.on('end', () => {
                const fn = new Function('insight', data);
                try {
                    fn(insight);
                    res.write('GraalVM Insight hook activated\n');
                } finally {
                    res.end();
                }
            });
        }
    });
    srv.listen(9999, () => console.log("Admin ready at 9999"));
}


let waitForRequire = function (event) {
  if (typeof process === 'object' && process.mainModule && process.mainModule.require) {
    insight.off('source', waitForRequire);
    initialize(insight, process.mainModule.require.bind(process.mainModule));
  }
};

insight.on('source', waitForRequire, { roots: true });
```

The program opens an HTTP server at port `9999` and listens for incoming scripts to be applied any time later.
Invoke the application:

```bash
node --insight=adminserver.js yourapp.js
Admin ready at 9999
```

While it is running, connect to the admin port.
Send in any GraalVM Insight script to it.
For example, the following script is going to observe who calls `process.exit`:

```bash
curl --data \
'insight.on("enter", (ctx, frame) => { console.log(new Error("call to exit").stack); }, \
{ roots: true, rootNameFilter: "exit" });' \
-X POST http://localhost:9999/
```

When writing your own `adminserver.js`, pay attention to security.
Only an authorized person should apply arbitrary hooks to your application.
Do not open the admin server port to everybody.

### What to Read Rext

To learn more about Insight and find some usecases, go to the [Insight Manual](Insight-Manual.md).
It starts with an obligatory _HelloWorld_ example and then demonstrates more challenging tasks.
