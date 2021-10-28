---
layout: docs-experimental
toc_group: tools
link_title: GraalVM Insight
permalink: /tools/graalvm-insight/
---

# GraalVM Insight

* [Start Using GraalVM Insight](#start-using-graalvm-insight)
* [Polyglot Tracing](#polyglot-tracing)
* [Inspecting Values](#inspecting-values)

GraalVM Insight is a multipurpose, flexible tool for writing reliable microservices solutions that traces program runtime behavior and gathers insights.

The dynamic nature of the tool helps users to selectively apply tracing pointcuts on already running applications with no loss of performance.
GraalVM Insight also provides detailed access to runtime behavior of a program, allowing users to inspect values and types at invocation or allocation sites.
The tool further permits users to modify computed values, interrupt execution, and quickly experiment with behavioral changes without modifying the application code.

This page provides information on GraalVM Insight as of the 20.1 version.
To learn about Insight on versions 20.0 and 19.3, proceed [here](https://github.com/oracle/graal/blob/release/graal-vm/20.0/tools/docs/T-Trace.md).

Note: The GraalVM Insight tool is offered as a technology preview and requires the user to pass the `--experimental-options` option in order to enable the `--insight` instrument.

## Start Using GraalVM Insight

1. Create a simple _source-tracing.js_ script with the following content:
```javascript
insight.on('source', function(ev) {
    print(`Loading ${ev.characters.length} characters from ${ev.name}`);
});
```
2. Having set `JAVA_HOME` to the GraalVM home directory, start the `node` launcher with the `--insight` tool and observe what scripts are being loaded and evaluated:
```shell
$JAVA_HOME/bin/node --experimental-options --insight=source-tracing.js --js.print -e "print('The result: ' + 6 * 7)" | tail -n 10
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
The following _function-histogram-tracing.js_ script counts all method invocations and dumps the most frequent ones when the execution of a program is over:

```javascript
var map = new Map();

function dumpHistogram() {
    print("==== Histogram ====");
    var digits = 3;
    Array.from(map.entries()).sort((one, two) => two[1] - one[1]).forEach(function (entry) {
        var number = entry[1].toString();
        if (number.length >= digits) {
            digits = number.length;
        } else {
            number = Array(digits - number.length + 1).join(' ') + number;
        }
        if (number > 10) print(`${number} calls to ${entry[0]}`);
    });
    print("===================");
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

insight.on('close', dumpHistogram);
```

The `map` is a global variable shared inside of the Insight script that allows the code to share data between the `insight.on('enter')` function and the `dumpHistogram`
function.
The latter is executed when the node process execution is over (registered via `insight.on('close', dumpHistogram`).
Invoke it as:
```shell
$JAVA_HOME/bin/node --experimental-options --insight=function-histogram-tracing.js --js.print -e "print('The result: ' + 6 * 7)"
The result: 42
==== Histogram ====
328 calls to isPosixPathSeparator
236 calls to E
235 calls to makeNodeErrorWithCode
137 calls to :=>
 64 calls to :program
 64 calls to :anonymous
 45 calls to getOptionValue
 45 calls to getOptionsFromBinding
 26 calls to hideStackFrames
 18 calls to validateString
 12 calls to defineColorAlias
===================
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
$JAVA_HOME/bin/ruby --jvm --polyglot --experimental-options --insight=source-trace.js helloworld.rb
JavaScript instrument observed load of helloworld.rb
Hello from GraalVM Ruby!
```
It is necessary to start the Ruby launcher with the `--polyglot` parameter, as the _source-tracing.js_ script remains written in JavaScript.

A user can instrument any language on top of GraalVM, but also the Insight scripts can be written in any of the GraalVM supported languages (implemented with the [Truffle language implementation framework](../../truffle/docs/README.md)).

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
$JAVA_HOME/bin/node --jvm  --polyglot --experimental-options --insight=source-tracing.rb --js.print -e "print('With Ruby: ' + 6 * 7)" | grep Ruby
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
$JAVA_HOME/bin/node --experimental-options --insight=fib-trace.js --js.print fib.js
fib for 3
fib for 2
fib for 1
fib for 0
fib for 1
Two is the result 2
```

To learn more about GraalVM Insight, go to [Insight Manual](https://github.com/oracle/graal/blob/master/tools/docs/Insight-Manual.md).

Documentation on the `insight` object properties and functions is available as part of the [Javadoc](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html).
