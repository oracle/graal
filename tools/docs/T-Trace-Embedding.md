# [T-Trace](T-Trace.md): Embedding

[T-Trace](T-Trace-Manual.md) is a multipurpose, flexible tool providing
enourmous possiblilities when it comes to dynamic understanding of user
application behavior. See its [manual](T-Trace-Manual.md) for more details.
Read on to learn how to use [T-Trace](T-Trace-Manual.md) in your own application.

### Embedding T-Trace into Java Application

[GraalVM](http://graalvm.org) languages can be embedded into custom Java applications via polyglot
[Context](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html) API.
[T-Trace](T-Trace-Manual.md) isn't an exception and it can also be
controlled via the same API as well. See
[AgentScript class documentation](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html)
for more details:

```java
final Engine engine = context.getEngine();
Instrument instrument = engine.getInstruments().get("agentscript");
Function<Source, AutoCloseable> access = instrument.lookup(Function.class);
AutoCloseable handle = access.apply(agentSrc);
```

Obtain `Engine` for your `Context` and ask for `agentscript` instrument. Then create
`Source` with your [T-Trace](T-Trace-Manual.md) script and apply it while obtaining
its *instrumentation handle*. Use `handle.close()` to disable all the script's
instrumentations when when no longer needed.

### Ignoring Internal Scripts

Often one wants to treat certain code written in a dynamic language as a
priviledged one - imagine various bindings to OS concepts or other features
of one's application. Such scripts are better to remain blackboxed and hidden
from [T-Trace](T-Trace-Manual.md) instrumentation capabilities.

To hide priviledged scripts from [T-Trace](T-Trace.md) sight
[mark such scripts as internal](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.Builder.html#internal-boolean-)
- by default [T-Trace](T-Trace.md) ignores and doesn't process *internal* scripts.

### Embedding T-Trace into node.js Application

The [T-Trace hacker's manual](T-Trace-Manual.md) shows many examples of using
[T-Trace](T-Trace.md) with `node` - however most of them rely on the command
line option `--agentscript` and don't benefit from the dynamic nature of
[T-Trace](T-Trace.md) much. Let's fix that by showing how to create an
*admin server*. Define `adminserver.js`:

```js
function initializeAgent(agent, require) {
    const http = require("http");
    const srv = http.createServer((req, res) => {
        let method = req.method;
        if (method === 'POST') {
            var data = '';
            req.on('data', (chunk) => {
                data += chunk.toString();
            });
            req.on('end', () => {
                const fn = new Function('agent', data);
                try {
                    fn(agent);
                    res.write('T-Trace hook activated\n');
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
    agent.off('source', waitForRequire);
    initializeAgent(agent, process.mainModule.require);
  }
};

agent.on('source', waitForRequire, { roots: true });
```

which opens an HTTP server at port `9999` and listens for incoming scripts to
be applied any time later. Invoke your application as

```bash
$ node --agentscript=adminserver.js --experimental-options yourapp.js
Admin ready at 9999
```

and while it is running connect to the admin port. Send in any *T-Trace* script you want.
For example following script is going to observe who calls `process.exit`:

```bash
$ curl --data \
  'agent.on("enter", (ctx, frame) => { console.log(new Error("call to exit").stack); }, \
  { roots: true, rootNameFilter: n => n === "exit" });' \
  -X POST http://localhost:9999/
```

When writing your own `adminserver.js` pay attention to security. [T-Trace](T-Trace.md)
scripts are very powerful and you want only authorized persons to apply arbitrary
hooks to your application. Don't open the admin server port to everybody.

## Where next?

Read about more [T-Trace](T-Trace.md) use-cases in its [hacker's manual](T-Trace-Manual.md).
