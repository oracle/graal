# [GraalVM Insight](Insight.md): Embedding

GraalVM [Insight](Insight.md) is a multipurpose, flexible tool providing
enourmous possiblilities when it comes to dynamic understanding of user
application behavior. See its [manual](Insight-Manual.md) for more details.
Read on to learn how to embed [Insight](Insight.md) into your own application.

### Embedding Insight into Java Application

[GraalVM](http://graalvm.org) languages can be embedded into custom Java applications via polyglot
[Context](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html) API.
[Insight](Insight-Manual.md) isn't an exception and it can also be
controlled via the same API as well. See
[AgentScript class documentation](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html)
for more details:

```java
final Engine engine = context.getEngine();
Instrument instrument = engine.getInstruments().get("insight");
Function<Source, AutoCloseable> access = instrument.lookup(Function.class);
AutoCloseable handle = access.apply(agentSrc);
```

Obtain `Engine` for your `Context` and ask for `insight` instrument. Then create
`Source` with your [Insight](Insight-Manual.md) script and apply it while obtaining
its *instrumentation handle*. Use `handle.close()` to disable all the script's
instrumentations when when no longer needed.

### Ignoring Internal Scripts

Often one wants to treat certain code written in a dynamic language as a
priviledged one - imagine various bindings to OS concepts or other features
of one's application. Such scripts are better to remain blackboxed and hidden
from [Insight](Insight-Manual.md) instrumentation capabilities.

To hide priviledged scripts from [Insight](Insight.md) sight
[mark such scripts as internal](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Source.Builder.html#internal-boolean-). By default [Insight](Insight.md) ignores and doesn't process *internal* scripts.

### Embedding Insight into node.js Application

The [Insight hacker's manual](Insight-Manual.md) shows many examples of using
[Insight](Insight.md) with `node` - however most of them rely on the command
line option `--insight` and don't benefit from the dynamic nature of
[Insight](Insight.md) much. Let's fix that by showing how to create an
*admin server*. Define `adminserver.js`:

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

which opens an HTTP server at port `9999` and listens for incoming scripts to
be applied any time later. Invoke your application as

```bash
$ node --insight=adminserver.js --experimental-options yourapp.js
Admin ready at 9999
```

and while it is running connect to the admin port. Send in any *Insight* script you want.
For example following script is going to observe who calls `process.exit`:

```bash
$ curl --data \
  'insight.on("enter", (ctx, frame) => { console.log(new Error("call to exit").stack); }, \
  { roots: true, rootNameFilter: "exit" });' \
  -X POST http://localhost:9999/
```

When writing your own `adminserver.js` pay attention to security. [Insight](Insight.md)
scripts are very powerful and you want only authorized persons to apply arbitrary
hooks to your application. Don't open the admin server port to everybody.

## Where next?

Read about more [Insight](Insight.md) use-cases in its [hacker's manual](Insight-Manual.md).
