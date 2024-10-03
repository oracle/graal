---
layout: docs-experimental
toc_group: insight
link_title: Insight Manual
permalink: /tools/graalvm-insight/manual/
---

# Insight Manual

GraalVM Insight is a multipurpose, flexible tool to write reliable applications.
The dynamic nature of the tool enables you to selectively apply tracing pointcuts on existing applications with no loss of performance.

Any moderately skilled hacker can easily create so called **Insight snippets** and dynamically apply them to the actual applications.
This provides ultimate insights into the execution and behavior of your program without compromising its speed.

### Table of contents
- [Quick Start](#quick-start)
- [Hotness Top 10 Example](#hotness-top-10-example)
- [Apply Insight to Any GraalVM Language](#apply-insight-to-any-graalvm-language)
- [Insights with JavaScript](#insights-with-javascript)
- [Insights with Python](#insights-with-python)
- [Insights with Ruby](#insights-with-ruby)
- [Insights with R](#insights-with-r)
- [Insight into C Code](#insight-into-c-code)
- [Inspecting Values](#inspecting-values)
- [Modifying Local Variables](#modifying-local-variables)
- [Insight to a Specific Location](#insight-to-a-specific-location)
- [Delaying Insight Initialization in Node.JS](#delaying-insight-initialization-in-nodejs)
- [Handling Exceptions](#handling-exceptions)
- [Intercepting and Altering Execution](#intercepting-and-altering-execution)
- [Minimal Overhead](#minimal-overhead)
- [Minimal Overhead when Accessing Locals](#minimal-overhead-when-accessing-locals)
- [Accessing Execution Stack](#accessing-execution-stack)
- [Note on GraalVM Insight API](#note-on-graalvm-insight-api)
- [Heap Dumping](#heap-dumping)

## Quick Start

Get started with an obligatory **HelloWorld** example.
Create a script named _source-tracing.js_ with following content:

```js
insight.on('source', function(ev) {
    if (ev.characters) {
        print(`Loading ${ev.characters.length} characters from ${ev.name}`);
    }
});
```
Run it with GraalVM's `node` launcher adding the `--insight` instrument option.
Observe the scripts that are loaded and evaluated:

```bash
./bin/node --js.print --experimental-options --insight=source-tracing.js -e "print('The result: ' + 6 * 7)" | tail -n 10
Loading 29938 characters from url.js
Loading 345 characters from internal/idna.js
Loading 12642 characters from punycode.js
Loading 33678 characters from internal/modules/cjs/loader.js
Loading 13058 characters from vm.js
Loading 52408 characters from fs.js
Loading 15920 characters from internal/fs/utils.js
Loading 505 characters from [eval]-wrapper
Loading 29 characters from [eval]
The result: 42
```

What has just happened? GraalVM Insight _source-tracing.js_ script has used the provided `insight` object to attach a **source** listener to the runtime.
As such, whenever `node` loaded a script, the listener was notified of it and could take an action (in this case printing the length and name of processed script).

## Hotness Top 10 Example

Collecting insights information is not limited to a print statement.
You can perform any Turing-complete computation in your language.
For example, a program that counts all method invocations and dumps the most frequent ones when the execution is over.

Save the following code to _function-hotness-tracing.js_:

```js
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

The `map` is a global variable visible for the whole Insight script that enables the code to share data between the `insight.on('enter')` function and the `dumpHotness` function.
The latter is executed when the `node` process execution is over (registered via `insight.on('close', dumpHotness)`).
Run the program:

```bash
./bin/node --js.print --experimental-options --insight=function-hotness-tracing.js -e "print('The result: ' + 6 * 7)"
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

A table with names and counts of function invocations is printed out when the `node` process exits.

## Apply Insight to Any GraalVM Language

The previous examples were written in JavaScript and used `node`, but due to the polyglot nature of GraalVM you can take the same instrument and apply it to any language that GraalVM supports.
For example, test the Ruby language with GraalVM Insight.

To start, create the instrument in the _source-trace.js_ file:

```js
insight.on('source', function(ev) {
   if (ev.uri.indexOf('gems') === -1) {
     let n = ev.uri.substring(ev.uri.lastIndexOf('/') + 1);
     print('JavaScript instrument observed load of ' + n);
   }
});
```

Prepare your Ruby program in the _helloworld.rb_ file:

```ruby
puts 'Hello from GraalVM Ruby!'
```

Note: Make sure the Ruby support is enabled. See [Polyglot Programming guide](../../reference-manual/polyglot-programming.md).

Apply the JavaScript instrument to the Ruby program. Here is what you should see:

```bash
./bin/ruby --polyglot --insight=source-trace.js helloworld.rb
JavaScript instrument observed load of helloworld.rb
Hello from GraalVM Ruby!
```

It is necessary to start GraalVM's Ruby launcher with the `--polyglot` parameter as the _source-tracing.js_ script remains written in JavaScript.

## Insights with JavaScript

As stated in the previous section, GraalVM Insight is not limited to Node.js.
It is available in all languages runtimes GraalVM provides.
Try the JavaScript implementation that comes with GraalVM.

Create the _function-tracing.js_ script:

```js
var count = 0;
var next = 8;

insight.on('enter', function(ev) {
    if (count++ % next === 0) {
        print(`Just called ${ev.name} as ${count} function invocation`);
        next *= 2;
    }
}, {
    roots: true
});
```

Run it on top of [_sieve.js_](https://github.com/oracle/graal/blob/5ec71a206aa422078ac21be9949f8eb8918b3d3c/vm/benchmarks/agentscript/sieve.js){:target="_blank"}.
It is a sample script which uses a variant of the Sieve of Eratosthenes to compute one hundred thousand of prime numbers:

```bash
./bin/js --insight=function-tracing.js sieve.js | grep -v Computed
Just called :program as 1 function invocation
Just called Natural.next as 17 function invocation
Just called Natural.next as 33 function invocation
Just called Natural.next as 65 function invocation
Just called Natural.next as 129 function invocation
Just called Filter as 257 function invocation
Just called Natural.next as 513 function invocation
Just called Natural.next as 1025 function invocation
Just called Natural.next as 2049 function invocation
Just called Natural.next as 4097 function invocation
```

## Insights with Python

Not only one can instrument any GraalVM language, but also the Insight scripts can be written in that language.
In this section you will find a Python example.

It is possible to write GraalVM Insight scripts in Python.
Such insights can be applied to programs written in Python or any other language.

Here is an example of a script that prints out value of variable `n` when a function `minusOne` is called.
Save this code to the _agent.py_ file:

```python
def onEnter(ctx, frame):
    print(f"minusOne {frame.n}")

class At:
    sourcePath = ".*agent-fib.js"

class Roots:
    roots = True
    at = At()
    rootNameFilter = "minusOne"

insight.on("enter", onEnter, Roots())
```
This code uses a declarative specification of source location introduced in GraalVM 22.2. Use a dynamic `sourceFilter` with older GraalVM versions:

```python
def onEnter(ctx, frame):
    print(f"minusOne {frame.n}")

class Roots:
    roots = True
    rootNameFilter = "minusOne"

    def sourceFilter(self, src):
        return src.name == "agent-fib.js"

insight.on("enter", onEnter, Roots())
```
Apply this script to [_agent-fib.js_](https://github.com/oracle/graal/blob/5ec71a206aa422078ac21be9949f8eb8918b3d3c/vm/tests/all/agentscript/agent-fib.js){:target="_blank"} using the following command:

```bash
`./bin/js --polyglot --insight=agent.py agent-fib.js`
```

Note: Make sure the Python support is enabled. See [Polyglot Programming guide](../../reference-manual/polyglot-programming.md).

## Insights with Ruby

It is possible to write GraalVM Insight scripts in Ruby.
Such insights can be applied to programs written in Ruby or any other language.

Note: Make sure the Ruby support is enabled. See [Polyglot Programming guide](../../reference-manual/polyglot-programming.md).

Create the _source-tracing.rb_ script:

```ruby
puts("Ruby: Insight version #{insight.version} is launching")

insight.on("source", -> (env) {
  puts "Ruby: observed loading of #{env.name}"
})
puts("Ruby: Hooks are ready!")
```

Launch a Node.js program and instrument it with the Ruby script:

```bash
./bin/node --js.print --experimental-options --polyglot --insight=source-tracing.rb agent-fib.js
Ruby: Initializing GraalVM Insight script
Ruby: Hooks are ready!
Ruby: observed loading of node:internal/errors
Ruby: observed loading of node:internal/util
Ruby: observed loading of node:events
....
Ruby: observed loading of node:internal/modules/run_main
Ruby: observed loading of <...>/agent-fib.js
Three is the result 3
```

To track variable values, create _agent.rb_ script:
```ruby
insight.on("enter", -> (ctx, frame) {
    puts("minusOne #{frame.n}")
}, {
  roots: true,
  rootNameFilter: "minusOne",
  at: {
    sourcePath: ".*agent-fib.js"
  }
})
```
This code uses a declarative specification of source location introduced in GraalVM 22.2. Use a dynamic `sourceFilter` with older GraalVM versions:
```ruby
insight.on("enter", -> (ctx, frame) {
    puts("minusOne #{frame.n}")
}, {
  roots: true,
  rootNameFilter: "minusOne",
  sourceFilter: -> (src) {
    return src.name == Dir.pwd+"/agent-fib.js"
  }
})
```

The above Ruby script example prints out value of variable `n` when a function `minusOne` in the [_agent-fib.js_](https://github.com/oracle/graal/blob/5ec71a206aa422078ac21be9949f8eb8918b3d3c/vm/tests/all/agentscript/agent-fib.js){:target="_blank"} program is called:
```bash
./bin/node --js.print --experimental-options --polyglot --insight=agent.rb agent-fib.js
minusOne 4
minusOne 3
minusOne 2
minusOne 2
Three is the result 3
```

## Insights with R

The same instrument can be written in the R language.

Create the _agent-r.R_ script:

```R
cat("R: Initializing GraalVM Insight script\n")

insight@on('source', function(env) {
    cat("R: observed loading of ", env$name, "\n")
})

cat("R: Hooks are ready!\n")
```

Use it to trace a _test.R_ program:

```bash
./bin/Rscript --insight=agent-r.R test.R
R: Initializing GraalVM Insight script
R: Hooks are ready!
R: observed loading of test.R
```

The only change is the R language. All the other GraalVM Insight features and [APIs](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html#VERSION) remain the same.

## Insight into C Code

Not only it is possible to interpret dynamic languages, but with the help of the [GraalVM's LLI implementation](../../reference-manual/llvm/README.md), one can mix in even statically compiled programs written in **C**, **C++**, **Fortran**, **Rust**, etc.

Take, for example, a long running program such as [_sieve.c_](https://github.com/oracle/graal/blob/master/vm/tests/all/agentscript/agent-sieve.c), which contains never-ending `for` loop in its `main` method. You would like to give it some execution quota.

First, execute the program on GraalVM:

```bash
export TOOLCHAIN_PATH=`.../bin/lli --print-toolchain-path`
${TOOLCHAIN_PATH}/clang agent-sieve.c -lm -o sieve
./bin/lli sieve
```

The GraalVM `clang` wrapper adds special options instructing the regular `clang` to keep the LLVM bitcode information in the `sieve` executable along the normal native code.
The GraalVM's `lli` interpreter can then use the bitcode to interpret the program at full speed.
By the way, compare the result of direct native execution via `./sieve` and interpreter speed of `./bin/lli sieve`.
It should show quite good results as for an interpreter.

Now focus on breaking the endless loop. You can do it with this JavaScript _agent-limit.js_ Insight script:

```js
var counter = 0;

insight.on('enter', function(ctx, frame) {
    if (++counter === 1000) {
        throw `GraalVM Insight: ${ctx.name} method called ${counter} times. enough!`;
    }
}, {
    roots: true,
    rootNameFilter: 'nextNatural'
});
```

The script counts the number of invocations of the C `nextNatural` function and when the function gets invoked a thousand times, it emits an error to stop the `sieve` execution.
Run the program as:

```bash
./bin/lli --polyglot --insight=agent-limit.js sieve
Computed 97 primes in 181 ms. Last one is 509
GraalVM Insight: nextNatural method called 1000 times. enough!
        at <js> :anonymous(<eval>:7:117-185)
        at <llvm> nextNatural(agent-sieve.c:14:186-221)
        at <llvm> nextPrime(agent-sieve.c:74:1409)
        at <llvm> measure(agent-sieve.c:104:1955)
        at <llvm> main(agent-sieve.c:123:2452)
```

It is possible to access primitive local variables from the native code.
Replace the above Insight script with:

```js
insight.on('enter', function(ctx, frame) {
    print(`found new prime number ${frame.n}`);
}, {
    roots: true,
    rootNameFilter: (n) => n === 'newFilter'
});
```

Print out a message every time a new prime is added into the filter list:

```bash
./bin/lli --polyglot --insight=agent-limit.js sieve | head -n 3
found new prime number 2
found new prime number 3
found new prime number 5
```

The mixture of `lli`, polyglot and GraalVM Insight opens enormous possibilities in tracing, controlling and interactive or batch debugging of native programs.

## Inspecting Values

GraalVM Insight not only allows one to trace where the program execution is happening, but it also offers access to values of local variables and function arguments during execution.
One can for example write instrument that shows a value of argument `n` in a function `fib`:

```js
insight.on('enter', function(ctx, frame) {
   print('fib for ' + frame.n);
}, {
   roots: true,
   rootNameFilter: 'fib'
});
```

This instrument uses the second function argument `frame` to get access to values of local variables inside of every instrumented function.
The above Insight script also uses `rootNameFilter` to apply its hook only to function named `fib`:

```js
function fib(n) {
  if (n < 1) return 0;
  if (n < 2) return 1;
  else return fib(n - 1) + fib(n - 2);
}
print("Two is the result " + fib(3));
```

When the instrument is stored in a _fib-trace.js_ file and the actual code in _fib.js_, then invoking following command yields detailed information about the program execution and parameters passed between function invocations:

```bash
./bin/node --js.print --experimental-options --insight=fib-trace.js fib.js
fib for 3
fib for 2
fib for 1
fib for 0
fib for 1
Two is the result 2
```

To summarize this section, GraalVM Insight is a useful tool for polyglot, language agnostic aspect oriented programming.

## Modifying Local Variables

Not only that GraalVM Insight can access local variables, but it can also modify them.
Take, for example, this program summing an array:

```js
function plus(a, b) {
  return a + b;
}

var sum = 0;
[1, 2, 3, 4, 5, 6, 7, 8, 9].forEach((n) => sum = plus(sum, n));
print(sum);
```

It prints out a number `45`.
Apply the following Insight script to "erase" non-even numbers before adding them:

```js
insight.on('enter', function zeroNonEvenNumbers(ctx, frame) {
    if (frame.b % 2 === 1) {
        frame.b = 0;
    }
}, {
    roots: true,
    rootNameFilter: 'plus'
});
```

When launched with `js --insight=erase.js sumarray.js`, only the value `20` gets printed.

GraalVM Insight `enter` and `return` hooks can only modify existing variables.
They cannot introduce new ones.
Attempts to do so yield an exception.

## Insight to a Specific Location

To get to variables at a specific code location, the `at` object may have not only one of the mandatory source specifications:
a `sourcePath` property with the regular expression matching the source file path, or `sourceURI` property with string representation of the source URI.
There can also be an optional `line` and/or `column` specified. Let's have a _distance.js_ source file:
```js
(function(x, y) {
    let x2 = x*x;
    let y2 = y*y;
    let d = Math.sqrt(x2 + y2);
    for (let i = 0; i < d; i++) {
        // ...
    }
    return d;
})(3, 4);
```

Then we can apply following _distance-trace.js_ insight script to get values of variables:
```js
insight.on('enter', function(ctx, frame) {
    print("Squares: " + frame.x2 + ", " + frame.y2);
}, {
    statements: true,
    at: {
        sourcePath: ".*distance.js",
        line: 4
    }
});

insight.on('enter', function(ctx, frame) {
    print("Loop var i = " + frame.i);
}, {
    expressions: true,
    at: {
        sourcePath: ".*distance.js",
        line: 5,
        column: 21
    }
});
```
That gives us:
```bash
./bin/js --insight=distance-trace.js distance.js
Squares: 9, 16
Loop var i = 0
Loop var i = 1
Loop var i = 2
Loop var i = 3
Loop var i = 4
Loop var i = 5
```

## Delaying Insight Initialization in Node.JS

GraalVM Insight can be used in any GraalVM language runtime, including the `node` implementation.
However, when in `node`, one does not want to write plain Insight scripts. You would probably want to use full power of the `node` ecosystem including its modules.
Here is a sample _agent-require.js_ script that does it:

```js
let initialize = function (require) {
    let http = require("http");
    print(`${typeof http.createServer} http.createServer is available to the agent`);
}

let waitForRequire = function (event) {
  if (typeof process === 'object' && process.mainModule && process.mainModule.require) {
    insight.off('source', waitForRequire);
    initialize(process.mainModule.require.bind(process.mainModule));
  }
};

insight.on('source', waitForRequire, { roots: true });
```

The Insight scripts are initialized as soon as possible, and at that moment the `require` function is not yet ready.
As such, the script first attaches a listener on loaded scripts and, when the main user script is being loaded, it obtains its `process.mainModule.require` function.
Then it removes the probes using `insight.off` and invokes the actual `initialize` function to perform the real initialization while having access to all the node modules.
The script can be run with:

```js
./bin/node --js.print --experimental-options --insight=agent-require.js yourScript.js
```

This initialization sequence is known to work on GraalVM's `node` version 12.10.0 launched with the main `yourScript.js` parameter.

## Handling Exceptions

The GraalVM Insight instrument can throw exceptions which are then propagated to the surrounding user scripts.
Imagine you have a program _seq.js_ logging various messages:

```js
function log(msg) {
    print(msg);
}

log('Hello GraalVM Insight!');
log('How');
log('are');
log('You?');
```

You can register an instrument _term.js_ and stop the execution in the middle of the _seq.js_ program, based on observing the logged message:

```js
insight.on('enter', (ev, frame) => {
    if (frame.msg === 'are') {
        throw 'great you are!';
    }
}, {
    roots: true,
    rootNameFilter: 'log'
});
```

The _term.js_ instrument waits for a call to `log` function with message `are` and, at that moment, it emits its own exception effectively interrupting the user program execution.
As a result one gets:

```bash
./bin/js --polyglot --insight=term.js seq.js
Hello GraalVM Insight!
How
great you are!
        at <js> :=>(term.js:3:75-97)
        at <js> log(seq.js:1-3:18-36)
        at <js> :program(seq.js:7:74-83)
```

The exceptions emitted by Insight instrument are treated as regular language exceptions.
The _seq.js_ program could use the regular `try { ... } catch (e) { ... }` block to catch them and deal with them as if they were emitted by the regular user code.

## Intercepting and Altering Execution

GraalVM Insight is capable to alter the execution of a program.
It can skip certain computations and replace them with own alternatives.
The the following `plus` function as an example:

```js
function plus(a, b) {
    return a + b;
}
```

It is easy to change the behavior of the `plus` method.
The following Insight script replaces the `+` operation with multiplication by using the `ctx.returnNow` functionality:

```js
insight.on('enter', function(ctx, frame) {
    ctx.returnNow(frame.a * frame.b);
}, {
    roots: true,
    rootNameFilter: 'plus'
});
```

The `returnNow` method immediately stops execution and returns to the caller of the `plus` function.
The body of the `plus` method is not executed at all because the insight `on('enter', ...)` was applied, for example, before the actual body of the function was executed.
Multiplying instead of adding two numbers may not sound very tempting, but the same approach is useful in providing add-on caching (for example, memoization) of repeating function invocations.

It is also possible to let the original function code run and just alter its result.
For example, alter the result of `plus` function to be always non-negative:

```js
insight.on('return', function(ctx, frame) {
    let result = ctx.returnValue(frame);
    ctx.returnNow(Math.abs(result));
}, {
    roots: true,
    rootNameFilter: 'plus'
});
```

The Insight hook is executed on return of the `plus` function and is using the `returnValue` helper function to obtain the computed return value from the current `frame` object.
Then it can alter the value and `returnNow` returns a new result instead.
The `returnValue` function is always available on the provided `ctx` object, but it only returns a meaningful value when used in `on('return', ...)` hooks.

## Minimal Overhead

If you ask whether GraalVM Insight causes any performance overhead when the scripts are applied, the answer is "No" or "Minimal".
The overhead depends on what your scripts do.
When they add and spread complex computations all around your code base, then the price for the computation will be paid.
However, that would be overhead of your code, not of the instrumentation.
Using a simple _function-count.js_ script, measure overhead.

```js
var count = 0;
function dumpCount() {
    print(`${count} functions have been executed`);
}

insight.on('enter', function(ev) {
    count++;
}, {
    roots: true
});

insight.on('close', dumpCount);
```

Use the script on fifty iterations of the [_sieve.js_](https://github.com/oracle/graal/blob/5ec71a206aa422078ac21be9949f8eb8918b3d3c/vm/benchmarks/agentscript/sieve.js){:target="_blank"} sample which uses a variant of the Sieve of Eratosthenes to compute one hundred thousand of prime numbers.
Repeating the computation fifty times gives the runtime a chance to warm up and properly optimize.
Here is the optimal run:

```bash
./bin/js sieve.js | grep -v Computed
Hundred thousand prime numbers in 75 ms
Hundred thousand prime numbers in 73 ms
Hundred thousand prime numbers in 73 ms
```

Now compare it to execution time when running with the GraalVM Insight script enabled:

```bash
./bin/js --insight=function-count.js sieve.js  | grep -v Computed
Hundred thousand prime numbers in 74 ms
Hundred thousand prime numbers in 74 ms
Hundred thousand prime numbers in 75 ms
72784921 functions have been executed
```

The difference is 2 milliseconds.
GraalVM Insight blends the difference between program code and insight gathering scripts making all code work as one.
The `count++` invocation becomes a natural part of the program at all the places representing `ROOT` of program functions.

## Minimal Overhead when Accessing Locals

GraalVM Insight is capable to access local variables, almost "for free".
GraalVM Insight code, accessing local variables, blends with the actual function code defining them and there is no visible slowdown.

This can be demonstrated with this [_sieve.js_](https://github.com/oracle/graal/blob/5ec71a206aa422078ac21be9949f8eb8918b3d3c/vm/benchmarks/agentscript/sieve.js){:target="_blank"} algorithm to compute hundred thousand of prime numbers.
It keeps the found prime numbers in a linked list constructed via following function:

```js
function Filter(number) {
    this.number = number;
    this.next = null;
    this.last = this;
}
```

First, test the behavior by invoking the computation fifty times and measuring time it takes to finish the last round:

```bash
./bin/js -e "var count=50" --file sieve.js | grep Hundred | tail -n 1
Hundred thousand prime numbers in 73 ms
```

Then "tease" the system by observing each allocation of a new prime number slot, for example, the call to `new Filter` constructor:

```js
var sum = 0;
var max = 0;

insight.on('enter', (ctx, frame) => {
    sum += frame.number;
    if (frame.number > max) {
        max = frame.number;
    }
}, {
  roots: true,
  rootNameFilter: 'Filter'
});

insight.on('return', (ctx, frame) => {
    log(`Hundred thousand prime numbers from 2 to ${max} has sum ${sum}`);
    sum = 0;
    max = 0;
}, {
    roots: true,
    rootNameFilter: 'measure'
});
```

Every time a `new Filter(number)` is allocated, the maximum value of `number` is captured (for example, the highest prime number found), and also `sum` of all prime numbers found so far.
When the main loop in `measure` is over (meaning there are hundred thousand prime numbers), the result is printed.

Now try the following:

```bash
./bin/js  -e "var count=50" --insight=sieve-filter1.js --file sieve.js | grep Hundred | tail -n 2
Hundred thousand prime numbers from 2 to 1299709 has sum 62260698721
Hundred thousand prime numbers in 74 ms
```

There is no slowdown at all.
GraalVM Insight, when combined with inlining algorithms of the GraalVM compiler, enables great instrumentation capabilities with almost no performance penalty.

## Accessing Execution Stack

There is a way for GraalVM Insight to access the whole execution stack.
The following code snippet shows how to do that:

```js
insight.on("return", function(ctx, frame) {
  print("dumping locals");
  ctx.iterateFrames((at, vars) => {
      for (let p in vars) {
          print(`    at ${at.name} (${at.source.name}:${at.line}:${at.column}) ${p} has value ${vars[p]}`);
      }
  });
  print("end of locals");
}, {
  roots: true
});
```

Whenever the Insight hook is triggered, it prints the current execution stack with `name` of the function, `source.name`, `line` and `column`.
Moreover, it also prints values of all local `vars` at each frame.
It is also possible to modify values of existing variables by assigning new values to them: `vars.n = 42`.
Accessing the whole stack is flexible, but unlike [access to locals in the current execution frame](#modifying-local-variables), it is not a fast operation, use it wisely, if you want your program to continue running at full speed.

## Heap Dumping

GraalVM Insight can be used to snapshot a region of your program heap during the execution.
Use the `--heap.dump=/path/to/output.hprof` option together with a regular `--insight` one.
The Insight script will get access to `heap` object with the `dump` function.
Place your hook wherever needed and at the right moment dump the heap:

```js
insight.on('return', (ctx, frame) => {
    heap.dump({
        format: '1.0',
        depth: 50, // set max depth for traversing object references
        events: [
            {
                stack : [
                    {
                        at : ctx, // location of dump sieve.js:73
                        frame : {
                            // assemble frame content as you want
                            primes : frame.primes, // capture primes object
                            cnt : frame.cnt, // capture cnt value
                        },
                        depth : 10 // optionally override depth to ten references
                    }, // there can be more stack elements like this one
                ]
            },
            // there can be multiple events like the previous one
        ],
    });
    throw 'Heap dump written!';
}, {
    roots: true,
    rootNameFilter: 'measure'
});
```

Save the code snippet as a _dump.js_ file.
Get the [_sieve.js_](https://github.com/oracle/graal/blob/5ec71a206aa422078ac21be9949f8eb8918b3d3c/vm/benchmarks/agentscript/sieve.js){:target="_blank"} file and launch it as:

```bash
./bin/js --insight=dump.js --heap.dump=dump.hprof --file sieve.js
```

![Heap Stack](img/Insight-HeapStack.png)

A _dump.hprof_ file is going to be created at the end of the `measure` function capturing the state of your program memory.
Inspect the generated _.hprof_ file with regular tools such as [VisualVM](https://www.graalvm.org/tools/visualvm/) or [NetBeans](http://netbeans.org):

![Heap Inspect](img/Insight-HeapInspect.png)

The previous picture shows the heap dump taken at the end of the `measure` function in the [_sieve.js_](https://github.com/oracle/graal/blob/5ec71a206aa422078ac21be9949f8eb8918b3d3c/vm/benchmarks/agentscript/sieve.js) script.
The function has just computed one hundred thousand (count available in variable `cnt`) prime numbers.
The picture shows a linked list `Filter` holding prime numbers from `2` to `17`.
The rest of the linked list is hidden (only references up to depth `10` were requested) behind `unreachable` object.
Last variable `x` shows the number of searched natural numbers to compute all the prime numbers.

### Heap Dumping Cache

To speed up the heap dumping process and optimize the resulting dump, it's possible to enable a memory cache.
Objects whose properties are not changed between dumps to the cache are stored only once, reducing the resulting heap dump size.
Add (for example) the `--heap.cacheSize=1000` option to use a memory cache for 1000 events. By default, the cache is dumped to the file and cleared when full.
That policy can be changed by `--heap.cacheReplacement=lru` option, which keeps the most recent dump events in the cache and drops the oldest ones when
the cache size limit is reached.

To flush the cache to the heap dump file, `heap.flush()` needs to be called explicitly.

## Note on GraalVM Insight API

The compatibility of the GraalVM Insight API exposed via the `insight` object is implemented in a compatible way.
The GraalVM Insight API can be found by [this link](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html).
The `insight` object properties and functions is available as part of its [javadoc](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html#VERSION).

Future versions will add new features, but whatever has once been exposed, remains functional.
If your script depends on some new feature, it may check version of the exposed API:

```js
print(`GraalVM Insight version is ${insight.version}`);
```

New elements in the [API](https://www.graalvm.org/tools/javadoc/org/graalvm/tools/insight/Insight.html)
carry associated `@since` tag to describe the minimal version the associated functionality is available since.

<!-- ### TODO:

GraalVM comes with a unified set of prepackaged high performance **Insight**
insights at your convenience.

**Insight** is an ideal tool for practicing *aspects oriented programming*
in a completely language agnostic way.
- types at invocation or allocation sites, gathering useful information

- powerful tools to help you write, debug, manage, and organize
your **Insight** insights scripts. It is a matter of pressing a single button
to enable selected **Insight** insight and a matter of another click to
disable it cleanly, returning the program to state prior to the use
of the insight.

- *VisualVM* has been enhanced to provide a unified view of locally as well as
remotely running applications and the **Insight** insights dynamically
applied to each of them. Enlist available *HotSpot* or *native-image* based
virtual machines. Connect to them on demand. Apply selected insights. Let
them gather their data. Obtain the data and analyze them with the integrated
graphical tools. Disable the insights and disconnect. Let the program run
at its original full speed.
 -->
