# T-Trace: User's Handle to the Ultimate Tracing and Insights Gathering

[T-Trace](T-Trace.md) is an end user friendly tool for tracing and
instrumentation of scripts written in any language by scripts written
in any (other) language.

## Hello World!

Create a simple `source-tracing.js` script with following content:

```js
ttrace.on('source', function(ev) {
    print(`Loading ${ev.characters.length} characters from ${ev.name}`);
}, {
    internal: true,
    languages: ['js']
});
```
launch your GraalVM's `bin/node` launcher with the `--ttrace` instrument and
observer what scripts are being loaded and evaluated:

```bash
$ node --ttrace=source-tracing.js -e "print('The result: ' + 6 * 7)"
Loading 26 characters from polyglotEngineWrapper
Loading 1236 characters from unknown source
Loading 13732 characters from internal/bootstrap/loaders.js
Loading 22382 characters from internal/bootstrap/node.js
Loading 15091 characters from events.js
Loading 16054 characters from internal/async_hooks.js
Loading 34391 characters from internal/errors.js
Loading 12508 characters from util.js
Loading 36921 characters from internal/util/inspect.js
Loading 11611 characters from internal/util.js
Loading 2395 characters from internal/util/types.js
Loading 0 characters from bound function
Loading 3899 characters from internal/validators.js
Loading 15569 characters from internal/encoding.js
Loading 33026 characters from buffer.js
Loading 2622 characters from internal/graal/buffer.js
Loading 23088 characters from internal/buffer.js
Loading 10931 characters from internal/process/per_thread.js
Loading 3522 characters from internal/process/main_thread_only.js
Loading 6051 characters from internal/process/stdio.js
Loading 22345 characters from assert.js
Loading 8453 characters from internal/assert.js
Loading 49799 characters from fs.js
Loading 45633 characters from path.js
Loading 1520 characters from internal/constants.js
Loading 10019 characters from internal/fs/streams.js
Loading 11854 characters from internal/fs/utils.js
Loading 3177 characters from stream.js
Loading 2169 characters from internal/streams/pipeline.js
Loading 2848 characters from internal/streams/end-of-stream.js
Loading 2056 characters from internal/streams/legacy.js
Loading 35329 characters from _stream_readable.js
Loading 4 characters from ^$
Loading 3817 characters from internal/streams/buffer_list.js
Loading 3160 characters from internal/streams/destroy.js
Loading 770 characters from internal/streams/state.js
Loading 20907 characters from _stream_writable.js
Loading 4313 characters from _stream_duplex.js
Loading 7873 characters from _stream_transform.js
Loading 1728 characters from _stream_passthrough.js
Loading 40432 characters from internal/url.js
Loading 2831 characters from internal/querystring.js
Loading 4537 characters from internal/process/warning.js
Loading 3925 characters from internal/process/next_tick.js
Loading 4124 characters from internal/process/promises.js
Loading 4245 characters from internal/fixed_queue.js
Loading 380 characters from internal/options.js
Loading 23804 characters from timers.js
Loading 1180 characters from internal/linkedlist.js
Loading 4419 characters from internal/timers.js
Loading 14121 characters from console.js
Loading 4825 characters from tty.js
Loading 45129 characters from net.js
Loading 2214 characters from internal/net.js
Loading 3224 characters from internal/stream_base_commons.js
Loading 4658 characters from internal/tty.js
Loading 4678 characters from internal/modules/cjs/helpers.js
Loading 30006 characters from url.js
Loading 12629 characters from punycode.js
Loading 785 characters from internal/safe_globals.js
Loading 26762 characters from internal/modules/cjs/loader.js
Loading 13487 characters from vm.js
Loading 318 characters from [eval]-wrapper
Loading 29 characters from [eval]
The result: 42
```

What has just happened? The T-Tracing `source-tracing.js` script has used
the provided `ttrace` object to attach a *source* listener to the runtime.
As such, whenever the *node.js* framework loaded internal or user script,
the listener got notified of it and could take an action - in this case
printing the length and name of processed script.

## Histogram - Use Full Power of Your Language!

Collecting the insight informations isn't limited to simple print statement.
One can perform any Turing complete computation in your language. Imagine
following `function-histogram-tracing.js` that counts all method invocations
and dumps then when the execution of your problem is over:

```js
print(ttrace);

var map = new Map();

function dumpHistogram() {
    print("=== Histogram ===");
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
    print("=================");
}

ttrace.on('enter', function(ev) {
    var cnt = map.get(ev.name);
    if (cnt) {
        cnt = cnt + 1;
    } else {
        cnt = 1;
    }
    map.set(ev.name, cnt);
}, {
    tags: ['ROOT']
});

ttrace.on('close', dumpHistogram);
```

The `map` is a global variable shared inside of the T-Trace script and it 
shares data between the `ttrace.on('enter')` function and the `dumpHistogram`
function which runs when the `node` process execution is over (registered via
`ttrace.on('close', dumpHistogram)`. Invoke as:

```bash
$ node --ttrace=function-histogram-tracing.js -e "print(6 * 7)"
Object
42
=== Histogram ===
529 calls to isPosixPathSeparator
249 calls to NativeModule.exists
208 calls to NativeModule.require
206 calls to NativeModule.getCached
198 calls to E
198 calls to makeNodeErrorWithCode
192 calls to NativeModule.nonInternalExists
192 calls to NativeModule.isInternal
 68 calls to process.config
 62 calls to :anonymous
 61 calls to :program
 56 calls to NativeModule
 56 calls to NativeModule.cache
 56 calls to NativeModule.compile
 56 calls to NativeModule.getSource
 56 calls to NativeModule.wrap
 44 calls to binding
 37 calls to :=>
 19 calls to internalBinding
 16 calls to deprecate
 16 calls to validateString
 14 calls to uncurryThis
 12 calls to inherits
=================
```

Table with names and counts of function invocations is printed out when the
`node` process exists (requires fix of GR-18337).

## Not Limited to Node

So far the examples used `node.js`, but the **T-Trace** system isn't tight
to Node at all - it is available in all the environments GraalVM provides.
Let's try it on `bin/js` plain JavaScript implementation that comes with 
GraalVM. Let's define `function-tracing.js` script as:

```js
print(ttrace);

var count = 0;
var next = 8;

ttrace.on('enter', function(ev) {
    if (count++ % next === 0) {
        print(`Just called ${ev.name} as ${count} function invocation`);
        next *= 2;
    }
}, {
    tags: ['ROOT']
});
```

and run it on top of [sieve.js](https://github.com/jtulach/sieve/blob/7e188504e6cbd2809037450c845138b45724e186/js/sieve.js)
- a sample script which uses a variant of the Sieve of Erathostenes to compute one hundred
thousand of prime numbers:

```bash
$ js --ttrace=function-tracing.js sieve.js | grep -v Computed 
Object
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
Just called Natural.next as 8193 function invocation
Just called Natural.next as 16385 function invocation
Just called Natural.next as 32769 function invocation
Just called Natural.next as 65537 function invocation
Just called Natural.next as 131073 function invocation
Just called Natural.next as 262145 function invocation
Just called Natural.next as 524289 function invocation
Just called Filter as 1048577 function invocation
Just called Natural.next as 2097153 function invocation
Hundred thousand prime numbers in 1730 ms
Just called Natural.next as 4194305 function invocation
Hundred thousand prime numbers in 619 ms
Just called Natural.next as 8388609 function invocation
Hundred thousand prime numbers in 169 ms
Hundred thousand prime numbers in 115 ms
Hundred thousand prime numbers in 134 ms
Just called Natural.next as 16777217 function invocation
Hundred thousand prime numbers in 111 ms
```

T-Trace scripts are ready to be used in any environment - be it the
default `node` implementation, the lightweight `js` command line tool - 
or your own application that decides to embedd GraalVM scripting capabilities!

## Minimal Overhead

With all the power the **T-Trace** framework brings, it is fair to ask what's
the overhead when the insights are applied? The overhead of course depends
on what your scripts do. When they add and spread complex computations
all around your code base, then the price for the computation will be payed.
However, that would be overhead of your code, not of the instrumentation! Let's
thus measure overhead of a simple `function-count.js` script:

```js
print(ttrace);

var count = 0;
function dumpCount() {
    print(`${count} functions have been executed`);
}

ttrace.on('enter', function(ev) {
    count++;
}, {
    tags: ['ROOT']
});

ttrace.on('close', dumpCount);
```

Let's use the script on fifty iteration of [sieve.js](https://github.com/jtulach/sieve/blob/7e188504e6cbd2809037450c845138b45724e186/js/sieve.js)
sample which uses a variant of the Sieve of Erathostenes to compute one hundred
thousand of prime numbers. Repeating the computation fifty times gives the
runtime a chance to warm up and properly optimize. Here is the optimal run:

```bash
$ graalvm/bin/js sieve.js | tail -n 5
Computed 12416 primes in 4 ms. Last one is 133033
Computed 24832 primes in 11 ms. Last one is 284831
Computed 49664 primes in 27 ms. Last one is 607417
Computed 99328 primes in 69 ms. Last one is 1289927
Hundred thousand prime numbers in 69 ms
```

and now let's compare it to the time running with the T-Trace script:

```bash
$ graalvm/bin/js --ttrace=function-count.js sieve.js | tail -n 5
Computed 24832 primes in 11 ms. Last one is 284831
Computed 49664 primes in 28 ms. Last one is 607417
Computed 99328 primes in 70 ms. Last one is 1289927
Hundred thousand prime numbers in 71 ms
142770421 functions have been executed
```

Two milliseconds!? Seriously? Yes, seriously. The T-Trace framework
blends the difference between application code and insight gathering scripts
making all the code work as one! The `count++` invocation becomes natural part of
the application at all the places representing `ROOT` of application functions.
**T-Trace** system gives you unlimited instrumentation power at no cost!

## TODO:

Apply the **T-Trace** insights to scripts running in *node.js* or
*Ruby on Rails* or your *Python* big data computation pipeline. 


GraalVM comes with a unified set of prepackaged high performance **T-Trace** 
insights at your convenience. 

**T-Trace** insights scripts are primarily targeted
towards ease of use in microservices area - e.g. logging


**T-Trace** is an ideal tool for practicing *aspects oriented programming*
in a completely language agnostic way.
- inspect values, types at invocation or allocation sites, gathering useful information
- modify computed values, interrupt execution 

- C, C++, Rust, Fortran, etc. - Enrich your static code behavior 
by attaching your insights written in dynamic languages.

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
