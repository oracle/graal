---
layout: docs-experimental
toc_group: insight
link_title: GraalVM Insight
permalink: /tools/graalvm-insight/
---

# GraalVM Insight

* [Get Started](#get-started)
* [Polyglot Tracing](#polyglot-tracing)
* [Inspecting Values](#inspecting-values)
* [What to Read Next](#what-to-read-next)

GraalVM Insight is a multipurpose, flexible tool that traces program runtime behavior and gathers insights.

The dynamic nature of the tool helps users to selectively apply tracing pointcuts on already running applications with no loss of performance.
GraalVM Insight also provides detailed access to runtime behavior of a program, allowing users to inspect values and types at invocation or allocation sites.
The tool further permits users to modify computed values, interrupt execution, and quickly experiment with behavioral changes without modifying the application code.
The implementation details of the tool can be found in the [API specification](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html).

This page provides information on GraalVM Insight as of the 20.1 version.
To learn about Insight on versions 20.0 and 19.3, proceed [here](https://github.com/oracle/graal/blob/release/graal-vm/20.0/tools/docs/T-Trace.md).

## Get Started

1. Create a simple _source-tracing.js_ script with the following content:
```javascript
insight.on('source', function(ev) {
    if (ev.characters) {
        print(`Loading ${ev.characters.length} characters from ${ev.name}`);
    }
});
```
2. Having installed the [Node.js runtime](https://github.com/oracle/graaljs/blob/master/docs/user/NodeJS.md#nodejs-runtime), start the `node` launcher with the `--insight` tool and observe what scripts are being loaded and evaluated:
```shell
./bin/node --insight=source-tracing.js --js.print --experimental-options -e "print('The result: ' + 6 * 7)" | tail -n 10
Loading 215 characters from internal/modules/esm/transform_source.js
Loading 12107 characters from internal/modules/esm/translators.js
Loading 1756 characters from internal/modules/esm/create_dynamic_module.js
Loading 12930 characters from internal/vm/module.js
Loading 2710 characters from internal/modules/run_main.js
Loading 308 characters from module.js
Loading 10844 characters from internal/source_map/source_map.js
Loading 170 characters from [eval]-wrapper
Loading 29 characters from [eval]
The result: 42
```
The _source-tracing.js_ script used the provided `insight` object to attach a source listener to the runtime.
Whenever the script was loaded, the listener got notified of it and could take an action -- printing the length and name of the processed script.

The Insight information can be collected to a print statement or a histogram.
The following _function-hotness-tracing.js_ script counts all method invocations and dumps the most frequent ones when the execution of a program is over:

```javascript
var map = new Map();

function dumpHotness() {
    print("==== Hotness Top 10 ====");
    var count = 10;
    var digits = 3;
    Array.from(map.entries()).sort((one, two) => two[1] - one[1]).forEach(function (entry) {
        var number = entry[1].toString();
        if (number.length >= digits) {
            digits = number.length;
        } else {
            number = Array(digits - number.length + 1).join(' ') + number;
        }
        if (count-- > 0) print(`${number} calls to ${entry[0]}`);
    });
    print("========================");
}

insight.on('enter', function(ev) {
    var cnt = map.get(ev.name);
    if (cnt) {
        cnt = cnt + 1;
    } else {
        cnt = 1;
    }
    map.set(ev.name, cnt);
}, {
    roots: true
});

insight.on('close', dumpHotness);
```

The `map` is a global variable shared inside of the Insight script that allows the code to share data between the `insight.on('enter')` function and the `dumpHotness`
function.
The latter is executed when the node process execution is over (registered via `insight.on('close', dumpHotness`).
A table with names and counts of function invocations is printed out when the `node` process exits.

Invoke it as:
```shell
./bin/node --insight=function-hotness-tracing.js --js.print --experimental-options -e "print('The result: ' + 6 * 7)"
The result: 42
==== Hotness Top 10 ====
516 calls to isPosixPathSeparator
311 calls to :=>
269 calls to E
263 calls to makeNodeErrorWithCode
159 calls to :anonymous
157 calls to :program
 58 calls to getOptionValue
 58 calls to getCLIOptionsFromBinding
 48 calls to validateString
 43 calls to hideStackFrames
========================
```

## Polyglot Tracing

The previous examples were written in JavaScript, but due to GraalVM's polyglot nature, you can take the same instrument and use it in a program written in, e.g., the Ruby language.

1. Create the _source-trace.js_ file:
```javascript
insight.on('source', function(ev) {
   if (ev.uri.indexOf('gems') === -1) {
     let n = ev.uri.substring(ev.uri.lastIndexOf('/') + 1);
     print('JavaScript instrument observed load of ' + n);
   }
});
```
2. Prepare the _helloworld.rb_ Ruby file:
```ruby
puts 'Hello from GraalVM Ruby!'
```
3. Apply the JavaScript instrument to the Ruby program:
```shell
./bin/ruby --polyglot --insight=source-trace.js helloworld.rb
JavaScript instrument observed load of helloworld.rb
Hello from GraalVM Ruby!
```
It is necessary to start the Ruby launcher with the `--polyglot` parameter, as the _source-tracing.js_ script remains written in JavaScript.

A user can instrument any language on top of GraalVM, but also the Insight scripts can be written in any of the GraalVM supported languages (implemented with the [Truffle language implementation framework](../../../truffle/docs/README.md)).

1. Create the _source-tracing.rb_ Ruby file:
```ruby
puts "Ruby: Initializing GraalVM Insight script"
insight.on('source', ->(ev) {
    name = ev[:name]
    puts "Ruby: observed loading of #{name}"
})
puts 'Ruby: Hooks are ready!'
```

2. Launch a Node.js application and instrument it with the Ruby script:
```shell
./bin/node --polyglot --insight=source-tracing.rb -e "console.log('With Ruby: ' + 6 * 7)" | grep Ruby
Ruby: Initializing GraalVM Insight script
Ruby: Hooks are ready!
Ruby: observed loading of internal/per_context/primordials.js
Ruby: observed loading of internal/per_context/setup.js
Ruby: observed loading of internal/per_context/domexception.js
....
Ruby: observed loading of internal/modules/cjs/loader.js
Ruby: observed loading of vm.js
Ruby: observed loading of fs.js
Ruby: observed loading of internal/fs/utils.js
Ruby: observed loading of [eval]-wrapper
Ruby: observed loading of [eval]
With Ruby: 42
```

## Inspecting Values

GraalVM Insight not only allows one to trace where the program execution is happening, it also offers access to values of local variables and function arguments during program execution.
You can, for example, write an instrument that shows the value of argument `n` in the function `fib`:

```javascript
insight.on('enter', function(ctx, frame) {
   print('fib for ' + frame.n);
}, {
   roots: true,
   rootNameFilter: (name) => 'fib' === name
});
```

This instrument uses the second function argument, `frame`, to get access to values of local variables inside every instrumented function.
The above script also uses `rootNameFilter` to apply its hook only to the function named `fib`:

```javascript
function fib(n) {
  if (n < 1) return 0;
  if (n < 2) return 1;
  else return fib(n - 1) + fib(n - 2);
}
print("Two is the result " + fib(3));
```

When the instrument is stored in a `fib-trace.js` file and the actual code is in `fib.js`, invoking the following command yields detailed information about the program execution and parameters passed between function invocations:
```shell
./bin/node --insight=fib-trace.js --js.print --experimental-options fib.js
fib for 3
fib for 2
fib for 1
fib for 0
fib for 1
Two is the result 2
```

## What to Read Next

### Insight Deep Dive

Any moderately skilled developer can easily create own so called "hooks" and dynamically apply them to the actual programs.
That provides ultimate insights into execution and behavior of one's application without compromising the execution speed.

To continue learning and deep dive into GraalVM Insight, proceed to the [Insight Manual](Insight-Manual.md) which starts with an obligatory _HelloWorld_ example and then demonstrates more challenging tasks.

### Embedding GraalVM Insight into Applications

GraalVM languages (languages implemented with the Truffle framework) can be embedded into custom applications via [Polyglot Context API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html).
GraalVM Insight can also be controlled via the same API.

Read the [embedding documentation](Insight-Embedding.md) to learn how to integrate GraalVM Insight capabilities into applications in a secure way.

### Tracing with GraalVM Insight

GraalVM Insight dynamically adds tracing capabilities into existing code.
Write your application as normally and apply [Open Telemetry](https://opentelemetry.io/) traces dynamicall when needed.
Read more about Insight and Jaeger integration in a [dedicated guide](Insight-Tracing.md).

### API Specification

If you are interested in the implementation details, check the [API specification](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html).
There you will find the information on the `insight` object properties, functions, etc.
