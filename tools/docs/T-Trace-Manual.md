# T-Trace: The Ultimate Tracing and Insights Gathering Platform

**T-Trace** is multipurpose, flexible tool that greatly reduces 
the effort of writing reliable microservices solutions. The dynamic nature
of **T-Trace** helps everyone to selectively apply complex tracing
pointcuts on already deployed applications running at full speed. 
**T-Trace** further blurs the difference between various DevOps tasks -
code once, apply your insights anytime, anywhere!

## Tracing in any Language and any Framework

The traditional tracing solution requires every developer to modify their own code 
with manually added traces. **T-Trace** brings such experience to another 
level by using the powerful instrumentation capabilities built 
into any GraalVM language (JavaScript, Python, Ruby, R)
and letting you dynamically apply the tracing when needed, without altering the
original code of the application at all. All GraalVM languages and technologies 
are designed with support for tracing in mind. 
Apply the **T-Trace** insights to scripts running in *node.js* or
*Ruby on Rails* or your *Python* big data computation pipeline. All of that
is possible and ready to be explored.

Every user can easily create own
**T-Trace** insights in a language of one's choice. The insights are well
crafted code that, when enabled, gets automatically spread around the codebase 
of one's application and is applied at critical tracing pointcuts.
The code is smoothly blended into bussiness code of the application 
enriching the core functionality with additional cross cutting concerns
(for example security).

## Excellent for Research

While common GraalVM **T-Trace** sample scripts primarily targeted
ease of use in the microservices area, the functionality of **T-Trace**
pointcuts isn't limited to such area at all!

**T-Trace** is an ideal tool for practicing *aspects oriented programming*
in a completely language agnostic way. **T-Trace** insights allow detailed
access to runtime behavior of a program at all possible pointcuts allowing one to
inspect values, types at invocation or allocation sites, gathering useful information
and collecting and presenting it in unrestricted ways. The **T-Trace** insights
allow one to modify computed values, interrupt execution and 
quickly experiment with behavioral changes without modifying the
application code.

The applicability of **T-Trace** isn't limited only to scripting
languages. Any language written using Truffle API can be a target of
**T-Trace** insights including static languages handled by Sulong
(e.g. C, C++, Rust, Fortran, etc.). Enrich your static code behavior 
by attaching your insights written in dynamic languages.

**T-Trace** framework brings powerful cross-language yet language agnostic
metaprogramming features into hands of every researcher and practitioner.

## Running at Full Speed

GraalVM languages are well known for running with excellent performance and **T-Trace** 
makes no compromises to that! Your applications are inherently ready for
tracing without giving up any speed. Launch your application 
as you are used to. Let it run at full speed. When needed, connect to its GraalVM
and enable requested **T-Trace** insights. Their code gets automatically
blended into the code of your application, making them a natural part
of surrounding code. There is no loss of performance compared to code that
would be manually tweaked to contain the insights at appropriate places, but
such modification doesn't have to be done in advance - it can be fully applied
only when needed.

The flexibility and the power of standard as well as hand written
**T-Trace** insights makes them an excellent choice for vendors of cloud
based offerings. There is no other system that could compete with the 
multi-language offerings of GraalVM. The ability to create custom **T-Trace** 
based insights in any language brings the combined offering to yet another level.
GraalVM with **T-Trace** is the dream come true for anyone seeking security,
[embeddablity](T-Trace-Embedding.md), configurability, robustness and performance at the cloud scale.

## Hacker's Handle to the Ultimate Tracing Framework

Any moderately skilled hacker can easily create own 
so called **T-Trace** snippets and dynamically apply them to 
the actual programs. That provides ultimate insights into
execution and behavior of once application without compromising the speed 
of the execution. Let's get started with an obligatory Hello World example.

### Hello World!

Create a simple `source-tracing.js` script with following content:

```js
agent.on('source', function(ev) {
    print(`Loading ${ev.characters.length} characters from ${ev.name}`);
});
```
launch your GraalVM's `bin/node` launcher with the `--agentscript` instrument and
observe what scripts are being loaded and evaluated:

```bash
$ graalvm/bin/node --experimental-options --js.print --agentscript=source-tracing.js -e "print('The result: ' + 6 * 7)" | tail -n 10
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

What has just happened? The *T-Tracing* `source-tracing.js` script has used
the provided `agent` object to attach a *source* listener to the runtime.
As such, whenever the *node.js* framework loaded a script,
the listener got notified of it and could take an action - in this case
printing the length and name of processed script.

### Histogram - Use Full Power of Your Language!

Collecting the insights information isn't limited to simple print statement.
One can perform any Turing complete computation in your language. Imagine
following `function-histogram-tracing.js` that counts all method invocations
and dumps the most frequent ones when the execution of your program is over:

```js
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

agent.on('enter', function(ev) {
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

agent.on('close', dumpHistogram);
```

The `map` is a global variable shared inside of the **T-Trace** script that 
allows the code to share data between the `agent.on('enter')` function and the `dumpHistogram`
function. The latter is executed when the `node` process execution is over (registered via
`agent.on('close', dumpHistogram)`. Invoke as:

```bash
$ graalvm/bin/node --experimental-options --js.print --agentscript=function-histogram-tracing.js -e "print('The result: ' + 6 * 7)"
The result: 42
=== Histogram ===
543 calls to isPosixPathSeparator
211 calls to E
211 calls to makeNodeErrorWithCode
205 calls to NativeModule
198 calls to uncurryThis
154 calls to :=>
147 calls to nativeModuleRequire
145 calls to NativeModule.compile
 55 calls to internalBinding
 53 calls to :anonymous
 49 calls to :program
 37 calls to getOptionValue
 24 calls to copyProps
 18 calls to validateString
 13 calls to copyPrototype
 13 calls to hideStackFrames
 13 calls to addReadOnlyProcessAlias
=================
```

Table with names and counts of function invocations is printed out when the
`node` process exits.

### Not Limited to Node

So far the examples used `node.js`, but the **T-Trace** system isn't tight
to Node.js at all - it is available in all the environments GraalVM provides.
Let's try it on `bin/js` - pure JavaScript implementation that comes with 
GraalVM. Let's define `function-tracing.js` script as:

```js
var count = 0;
var next = 8;

agent.on('enter', function(ev) {
    if (count++ % next === 0) {
        print(`Just called ${ev.name} as ${count} function invocation`);
        next *= 2;
    }
}, {
    roots: true
});
```

and run it on top of [sieve.js](https://raw.githubusercontent.com/jtulach/sieve/7e188504e6cbd2809037450c845138b45724e186/js/sieve.js) - 
a sample script which uses a variant of the Sieve of Erathostenes to compute one hundred
thousand of prime numbers:

```bash
$ graalvm/bin/js --experimental-options --agentscript=function-tracing.js sieve.js | grep -v Computed
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

**T-Trace** scripts are ready to be used in any environment - be it the
default `node` implementation, the lightweight `js` command line tool - 
or your own application that decides to [embedd GraalVM scripting](T-Trace-Embedding.md)
capabilities!

### Trully Polyglot - T-Trace any Language

The previous examples were written in JavaScript, but due to the polyglot
nature of GraalVM, we can take the same instrument and use it 
in a program written in the Ruby language.
Here is an example - create `source-trace.js` file:

```js
agent.on('source', function(ev) {
   if (ev.uri.indexOf('gems') === -1) {
     let n = ev.uri.substring(ev.uri.lastIndexOf('/') + 1);
     print('JavaScript instrument observed load of ' + n);
   }
});
```

and prepare your Ruby file `helloworld.rb` (make sure GraalVM Ruby is
installed with `gu install ruby`):

```ruby
puts 'Hello from GraalVM Ruby!'
```

when you apply the JavaScript instrument to the Ruby program, here is what
you get:

```bash
$ graalvm/bin/ruby --polyglot --experimental-options --agentscript=source-trace.js helloworld.rb
JavaScript instrument observed load of helloworld.rb
Hello from GraalVM Ruby!
```

It is necessary to start GraalVM's Ruby launcher with `--polyglot` parameter
as the `source-tracing.js` script remains written in JavaScript. That's all
fine - mixing languages has never been a problem for GraalVM!

### Minimal Overhead

With all the power the **T-Trace** framework brings, it is fair to ask what's
the overhead when the insights are applied? The overhead of course depends
on what your scripts do. When they add and spread complex computations
all around your code base, then the price for the computation will be payed.
However, that would be overhead of your code, not of the instrumentation! Let's
thus measure overhead of a simple `function-count.js` script:

```js
var count = 0;
function dumpCount() {
    print(`${count} functions have been executed`);
}

agent.on('enter', function(ev) {
    count++;
}, {
    roots: true
});

agent.on('close', dumpCount);
```

Let's use the script on fifty iterations of [sieve.js](https://raw.githubusercontent.com/jtulach/sieve/7e188504e6cbd2809037450c845138b45724e186/js/sieve.js)
sample which uses a variant of the Sieve of Erathostenes to compute one hundred
thousand of prime numbers. Repeating the computation fifty times gives the
runtime a chance to warm up and properly optimize. Here is the optimal run:

```bash
$ graalvm/bin/js sieve.js | grep -v Computed
Hundred thousand prime numbers in 75 ms
Hundred thousand prime numbers in 73 ms
Hundred thousand prime numbers in 73 ms
```

and now let's compare it to execution time when running with the **T-Trace** script enabled:

```bash
$ graalvm/bin/js --experimental-options --agentscript=function-count.js sieve.js  | grep -v Computed
Hundred thousand prime numbers in 74 ms
Hundred thousand prime numbers in 74 ms
Hundred thousand prime numbers in 75 ms
72784921 functions have been executed

```

Two milliseconds!? Seriously? Yes, seriously. The **T-Trace** framework
blends the difference between application code and insight gathering scripts
making all the code work as one! The `count++` invocation becomes natural part of
the application at all the places representing `ROOT` of application functions.
**T-Trace** system gives you unlimited instrumentation power at no cost!

### Trully Polyglot - T-Tracing with Ruby

Not only one can instrument any GraalVM language, but also the **T-Trace**
scripts can be written in any GraalVM supported language. Take for example
Ruby and create `source-tracing.rb` (make sure GraalVM Ruby is installed via
`gu install ruby`) file:

```ruby
puts "Ruby: Initializing T-Trace script"

agent.on('source', ->(ev) {
    name = ev[:name]
    puts "Ruby: observed loading of #{name}" 
})

puts 'Ruby: Hooks are ready!'
```

and then you can launch your `node` application and instrument it with such
Ruby written script:

```bash
$ graalvm/bin/node --experimental-options --js.print --polyglot --agentscript=source-tracing.rb -e "print('With Ruby: ' + 6 * 7)" | grep Ruby:
Ruby: Initializing T-Trace script
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

Write your **T-Trace** scripts in any language you wish! They'll be
ultimatelly useful accross the whole GraalVM ecosystem.

### Inspecting Values

**T-Trace** not only allows one to trace where the program execution is
happening, but it also offers access to values of local variables and function
arguments during execution. One can for example write instrument that shows
value of argument `n` in a function `fib`:

```js
agent.on('enter', function(ctx, frame) {
   print('fib for ' + frame.n);
}, {
   roots: true,
   rootNameFilter: (name) => 'fib' === name
});
```

This instrument uses second function argument `frame` to get access to values
of local variables inside of every instrumented function. 
The above **T-Trace** script also uses `rootNameFilter` to apply its hook only
to function named `fib`:

```js
function fib(n) {
  if (n < 1) return 0;
  if (n < 2) return 1;
  else return fib(n - 1) + fib(n - 2);
}
print("Two is the result " + fib(3));
```

When the instrument is stored in a `fib-trace.js` file and the actual code
in `fib.js`, then invoking following command yields detailed information about
the program execution and parameters passed between function invocations:

```bash
$ graalvm/bin/node --experimental-options --js.print --agentscript=fib-trace.js fib.js
fib for 3
fib for 2
fib for 1
fib for 0
fib for 1
Two is the result 2
```

**T-Trace** is a perfect tool for polyglot, language agnostic aspect oriented
programming!

### API of **T-Trace**

The **T-Trace** functionality is offered as a technology preview and 
requires one to use `--experimental-options` to enable the `--agentscript`
instrument. Never the less, the compatibility of the **T-Trace** API 
exposed via the `agent` object
is treated seriously.

The [documentation](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html)
of the `agent` object properties and functions is available as part of its
[javadoc](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html#VERSION).

Future versions will add new features, but whatever has
once been exposed, remains functional. If your script depends on some fancy new
feature, it may check version of the exposed API:

```js
print(`Agent version is ${agent.version}`);
```

and act accordingly to the obtained version. New elements in the
[documentation](https://www.graalvm.org/tools/javadoc/com/oracle/truffle/tools/agentscript/AgentScript.html)
carry associated `@since` tag to describe the minimimal version the associated
functionality/element is available since.

### Delaying **T-Trace** Initialization in **node.js**

**T-Trace** can be used in any GraalVM enabled environment including GraalVM's
`node` implementation. However, when in `node`, one doesn't want to write 
plain simple **T-Trace** scripts - one wants to use full power of `node` 
ecosystem including its modules. Here is a sample `agent-require.js` script that does it:

```js
let initializeAgent = function (require) {
    let http = require("http");
    print(`${typeof http.createServer} http.createServer is available to the agent`);
}

let waitForRequire = function (event) {
  if (typeof process === 'object' && process.mainModule && process.mainModule.require) {
    agent.off('source', waitForRequire);
    initializeAgent(process.mainModule.require);
  }
};

agent.on('source', waitForRequire, { roots: true });
```

The script solves an important problem: **T-Trace** agents are
initialized as soon as possible and at that moment the `require` function isn't
yet ready. As such the agent first attaches a listener on loaded scripts and when 
the main user script is being loaded, it obtains its `process.mainModule.require` 
function. Then it removes the probes using `agent.off` and invokes the actual 
`initializeAgent` function to perform the real initialization while having 
access to all the node modules. The script can be used as

```js
$ graalvm/bin/node --experimental-options --js.print --agentscript=agent-require.js yourScript.js
```

This initialization sequence is known to work on GraalVM's node `v12.10.0`
launched with a main `yourScript.js` parameter.

### Handling Exceptions

The T-Trace agents can throw exceptions which are then propagated to the
surrounding user scripts. Imagine you have a program `seq.js`
logging various messages:

```js
function log(msg) {
    print(msg);
}

log('Hello T-Trace!');
log('How');
log('are');
log('You?');
```

You can register an instrument `term.js` and terminate the execution in the middle
of the `seq.js` program execution based on observing the logged message:

```js
agent.on('enter', (ev, frame) => { 
    if (frame.msg === 'are') {
        throw 'great you are!';
    }
}, {
    roots: true,
    rootNameFilter: (n) => n === 'log'
});
```

The `term.js` instrument waits for a call to `log` function with message 'are'
and at that moment it emits its own exception effectively interrupting the user
program execution. As a result one gets:

```bash
$ graalvm/bin/js --polyglot --experimental-options --agentscript=term.js seq.js
Hello T-Trace!
How
great you are!
        at <js> :=>(term.js:3:75-97)
        at <js> log(seq.js:1-3:18-36)
        at <js> :program(seq.js:7:74-83)
```

The exceptions emitted by T-Trace instruments are treated as regular language
exceptions. The `seq.js` program could use regular `try { ... } catch (e) { ... }`
block to catch them and deal with them as if they were emitted by the regular
user code.

### Hack into the C Code!

The polyglot capabilities of GraalVM know no limits. Not only it is possible
to interpret dynamic languages, but with the help of the
[lli launcher](https://www.graalvm.org/docs/reference-manual/languages/llvm/)
one can mix in even *statically compiled* programs written in **C**, **C++**,
**Fortran**, **Rust**, etc.

Imagine you have a long running program just like
[sieve.c](https://github.com/oracle/graal/blob/master/vm/tests/all/agentscript/agent-sieve.c)
(which contains never-ending `for` loop in `main` method) and you'd like to give
it some execution quota. That is quite easy to do with GraalVM and T-Trace! First
of all execute the program on GraalVM:

```bash
$ export TOOLCHAIN_PATH=`graalvm/bin/lli --print-toolchain-path`
$ ${TOOLCHAIN_PATH}/clang agent-sieve.c -lm -o sieve
$ graalvm/bin/lli sieve
```

Why toolchain? The GraalVM `clang` wrapper adds special options
instructing the regular `clang` to keep the LLVM bitcode information in the `sieve`
executable along the normal native code.
The GraalVM's `lli` interpreter can then use the bitcode to interpret the program
at full speed.  Btw. compare the result of direct native execution via `./sieve`
and interpreter speed of `graalvm/bin/lli sieve` - quite good result for an
interpreter, right?

Anyway let's focus on breaking the endless loop. You can do it with a JavaScript
`agent-limit.js` T-Trace script:

```js
var counter = 0;

agent.on('enter', function(ctx, frame) {
    if (++counter === 1000) {
        throw `T-Trace: ${ctx.name} method called ${counter} times. enough!`;
    }
}, {
    roots: true,
    rootNameFilter: (n) => n === 'nextNatural'
});
```

The script counts the number of invocations of the C `nextNatural` function
and when the function gets invoked a thousand times, it emits an error
to terminate the `sieve` execution. Just run the program as:

```bash
$ lli --polyglot --agentscript=agent-limit.js --experimental-options sieve
Computed 97 primes in 181 ms. Last one is 509
T-Trace: nextNatural method called 1000 times. enough!
        at <js> :anonymous(<eval>:7:117-185)
        at <llvm> nextNatural(agent-sieve.c:14:186-221)
        at <llvm> nextPrime(agent-sieve.c:74:1409)
        at <llvm> measure(agent-sieve.c:104:1955)
        at <llvm> main(agent-sieve.c:123:2452)
```

The mixture of `lli`, polyglot and T-Trace opens enormous possibilities in tracing,
controlling and interactive or batch debugging of native programs. Write in
C, C++, Fortran, Rust and inspect with JavaScript, Ruby & co.!

<!--

### TODO:

GraalVM comes with a unified set of prepackaged high performance **T-Trace** 
insights at your convenience. 

**T-Trace** insights scripts are primarily targeted
towards ease of use in microservices area - e.g. logging


**T-Trace** is an ideal tool for practicing *aspects oriented programming*
in a completely language agnostic way.
- inspect values, types at invocation or allocation sites, gathering useful information
- modify computed values, interrupt execution 

- powerful tools to help you write, debug, manage, and organize
your **T-Trace** insights scripts. It is a matter of pressing a single button
to enable selected **T-Trace** insight and a matter of another click to 
disable it cleanly, returning the application to state prior to the use 
of the insight.

- *VisualVM* has been enhanced to provide a unified view of locally as well as
remotely running applications and the **T-Trace** insights dynamically 
applied to each of them. Enlist available *HotSpot* or *native-image* based
virtual machines. Connect to them on demand. Apply selected insights. Let
them gather their data. Obtain the data and analyze them with the integrated
graphical tools. Disable the insights and disconnect. Let the application run
at its original full speed.

### OpenTracing API on top of **T-Trace**

It is possible to use the **T-Trace** system to implement smooth, declarative
logging via standard OpenTracing API. Use the `npm` command to install
one of the JavaScript libraries for tracing:

```bash
$ graalvm/bin/npm install opentracing
```

Now you can use its API in your instrument `function-tracing.js` via the
`require` function (once it becomes available):

```js
var tracer = null;
var ignore = false;
var countSpan = 0;

agent.on('enter', function(ev) {
    if (!ignore) {
        let prev = ignore;
        ignore = true;
        try {
            if (!tracer) {
                if (typeof require === 'function') {
                    let opentracing = require('opentracing');
                    class MockTracer extends opentracing.Tracer {
                        startSpan(n) {
                            return {
                                'finish' : function() {
                                    countSpan++;
                                }
                            };
                        }
                    }
                    
                    tracer = new MockTracer();
                }
            }
            if (tracer) {
                let span = tracer.startSpan(ev.name);
                span.finish();
            }
        } finally {
            ignore = prev;
        }
    }
}, {
    roots: true
});

ttrace.on('close', function() {
    print(`Used ${countSpan} spans!`);
});
```

With such instrument, it is just a matter of selecting the right `ttrace`
pointcuts - declaratively, selectively, precisely, accuratelly 
(via specifying the right tags and filtering on function names, location in
sources and other characteristics) and the OpenTracing will happen 
automatically and only on demand, without modifying the application code
at all.

-->