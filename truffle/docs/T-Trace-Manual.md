# T-Trace: Hacker's Handle to the Ultimate Tracing Framework

[T-Trace](T-Trace.md) is a hacker friendly tool for tracing and
instrumentation of scripts written in any language by scripts written
in any (other) language. It allows a moderately skilled hacker to 
create so called **T-Trace** snippets and dynamically apply them to 
the actual programs. That allows gathering of ultimate insights about 
the execution and behavior without compromising the speed of the execution. 
Let's get started with an obligatory Hello World example.

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
observe what scripts are being loaded and evaluated:

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

What has just happened? The *T-Tracing* `source-tracing.js` script has used
the provided `ttrace` object to attach a *source* listener to the runtime.
As such, whenever the *node.js* framework loaded internal or user script,
the listener got notified of it and could take an action - in this case
printing the length and name of processed script.

## Histogram - Use Full Power of Your Language!

Collecting the insights information isn't limited to simple print statement.
One can perform any Turing complete computation in your language. Imagine
following `function-histogram-tracing.js` that counts all method invocations
and dumps the most frequent ones when the execution of your program is over:

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

The `map` is a global variable shared inside of the **T-Trace** script that 
allows the code to share data between the `ttrace.on('enter')` function and the `dumpHistogram`
function. The latter is executed when the `node` process execution is over (registered via
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
Let's try it on `bin/js` - pure JavaScript implementation that comes with 
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

and run it on top of [sieve.js](https://github.com/jtulach/sieve/blob/7e188504e6cbd2809037450c845138b45724e186/js/sieve.js) - 
a sample script which uses a variant of the Sieve of Erathostenes to compute one hundred
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

**T-Trace** scripts are ready to be used in any environment - be it the
default `node` implementation, the lightweight `js` command line tool - 
or your own application that decides to embedd GraalVM scripting capabilities!

## Trully Polyglot - T-Trace any Language

The previous examples were written in JavaScript, but due to the polyglot
nature of GraalVM, we can take the same instrument and use it for example
for `ruby`. Here is an example:

```bash
$ /graalvm/bin/ruby --polyglot --ttrace=source-tracing.js -e "puts 'Hello from T-Trace...'"
Object
Loading 265 characters from <function>
Loading 10 characters from <internal>
Loading 203 characters from <eval>
Loading 0 characters from <builtin>
registration done
Loading 0 characters from (core)
Loading 2669 characters from resource:/truffleruby/core/pre.rb
Loading 1878 characters from resource:/truffleruby/core/basic_object.rb
Loading 39676 characters from resource:/truffleruby/core/array.rb
Loading 2457 characters from resource:/truffleruby/core/channel.rb
Loading 2195 characters from resource:/truffleruby/core/configuration.rb
Loading 1796 characters from resource:/truffleruby/core/false.rb
Loading 3538 characters from resource:/truffleruby/core/gc.rb
Loading 1997 characters from resource:/truffleruby/core/nil.rb
Loading 1113 characters from resource:/truffleruby/core/truffle/platform.rb
Loading 457 characters from resource:/truffleruby/core/support.rb
Loading 43525 characters from resource:/truffleruby/core/string.rb
Loading 5593 characters from resource:/truffleruby/core/random.rb
Loading 3944 characters from resource:/truffleruby/core/truffle/io_operations.rb
Loading 4856 characters from resource:/truffleruby/core/truffle/kernel_operations.rb
Loading 15300 characters from resource:/truffleruby/core/thread.rb
Loading 1791 characters from resource:/truffleruby/core/true.rb
Loading 19495 characters from resource:/truffleruby/core/type.rb
Loading 9407 characters from resource:/truffleruby/core/truffle/ffi/pointer.rb
Loading 17375 characters from resource:/truffleruby/core/truffle/ffi/pointer_access.rb
Loading 3334 characters from resource:/truffleruby/core/truffle/internal.rb
Loading 15359 characters from resource:/truffleruby/core/kernel.rb
Loading 1833 characters from resource:/truffleruby/core/truffle/boot.rb
Loading 667 characters from resource:/truffleruby/core/truffle/debug.rb
Loading 642 characters from resource:/truffleruby/core/truffle/encoding_operations.rb
Loading 2057 characters from resource:/truffleruby/core/truffle/exception_operations.rb
Loading 579 characters from resource:/truffleruby/core/truffle/hash_operations.rb
Loading 4367 characters from resource:/truffleruby/core/truffle/numeric_operations.rb
Loading 2295 characters from resource:/truffleruby/core/truffle/proc_operations.rb
Loading 3141 characters from resource:/truffleruby/core/truffle/range_operations.rb
Loading 1522 characters from resource:/truffleruby/core/truffle/regexp_operations.rb
Loading 3750 characters from resource:/truffleruby/core/truffle/stat_operations.rb
Loading 5357 characters from resource:/truffleruby/core/truffle/string_operations.rb
Loading 1117 characters from resource:/truffleruby/core/truffle/backward.rb
Loading 1104 characters from resource:/truffleruby/core/truffle/truffleruby.rb
Loading 5635 characters from resource:/truffleruby/core/splitter.rb
Loading 8572 characters from resource:/truffleruby/core/stat.rb
Loading 69571 characters from resource:/truffleruby/core/io.rb
Loading 2432 characters from resource:/truffleruby/core/immediate.rb
Loading 4279 characters from resource:/truffleruby/core/module.rb
Loading 4225 characters from resource:/truffleruby/core/proc.rb
Loading 1899 characters from resource:/truffleruby/core/enumerable_helper.rb
Loading 22925 characters from resource:/truffleruby/core/enumerable.rb
Loading 13599 characters from resource:/truffleruby/core/enumerator.rb
Loading 15211 characters from resource:/truffleruby/core/argf.rb
Loading 11776 characters from resource:/truffleruby/core/exception.rb
Loading 12887 characters from resource:/truffleruby/core/hash.rb
Loading 2929 characters from resource:/truffleruby/core/comparable.rb
Loading 8344 characters from resource:/truffleruby/core/numeric.rb
Loading 2115 characters from resource:/truffleruby/core/truffle/ctype.rb
Loading 7697 characters from resource:/truffleruby/core/integer.rb
Loading 13007 characters from resource:/truffleruby/core/regexp.rb
Loading 11405 characters from resource:/truffleruby/core/transcoding.rb
Loading 6015 characters from resource:/truffleruby/core/encoding.rb
Loading 7403 characters from resource:/truffleruby/core/env.rb
Loading 2534 characters from resource:/truffleruby/core/errno.rb
Loading 38251 characters from resource:/truffleruby/core/file.rb
Loading 8262 characters from resource:/truffleruby/core/dir.rb
Loading 13628 characters from resource:/truffleruby/core/dir_glob.rb
Loading 3516 characters from resource:/truffleruby/core/file_test.rb
Loading 6275 characters from resource:/truffleruby/core/float.rb
Loading 33797 characters from resource:/truffleruby/core/marshal.rb
Loading 1644 characters from resource:/truffleruby/core/object_space.rb
Loading 9535 characters from resource:/truffleruby/core/range.rb
Loading 10473 characters from resource:/truffleruby/core/struct.rb
Loading 2183 characters from resource:/truffleruby/core/tms.rb
Loading 26460 characters from resource:/truffleruby/core/process.rb
Loading 18458 characters from resource:/truffleruby/core/truffle/process_operations.rb
Loading 4307 characters from resource:/truffleruby/core/signal.rb
Loading 4719 characters from resource:/truffleruby/core/symbol.rb
Loading 1664 characters from resource:/truffleruby/core/mutex.rb
Loading 2203 characters from resource:/truffleruby/core/throw_catch.rb
Loading 11674 characters from resource:/truffleruby/core/time.rb
Loading 9852 characters from resource:/truffleruby/core/rational.rb
Loading 3002 characters from resource:/truffleruby/core/rationalizer.rb
Loading 9166 characters from resource:/truffleruby/core/complex.rb
Loading 3003 characters from resource:/truffleruby/core/complexifier.rb
Loading 1776 characters from resource:/truffleruby/core/class.rb
Loading 746 characters from resource:/truffleruby/core/binding.rb
Loading 1138 characters from resource:/truffleruby/core/math.rb
Loading 828 characters from resource:/truffleruby/core/method.rb
Loading 508 characters from resource:/truffleruby/core/unbound_method.rb
Loading 735 characters from resource:/truffleruby/core/warning.rb
Loading 1143 characters from resource:/truffleruby/core/tracepoint.rb
Loading 13354 characters from resource:/truffleruby/core/truffle/interop.rb
Loading 1919 characters from resource:/truffleruby/core/truffle/polyglot.rb
Loading 16950 characters from resource:/truffleruby/core/posix.rb
Loading 3065 characters from resource:/truffleruby/core/main.rb
Loading 4454 characters from resource:/truffleruby/core/post.rb
Loading 2618 characters from resource:/truffleruby/post-boot/post-boot.rb
Loading 22 characters from /graalvm/jre/languages/ruby/lib/truffle/enumerator.rb
Loading 760 characters from /graalvm/jre/languages/ruby/lib/truffle/thread.rb
Loading 35 characters from /graalvm/jre/languages/ruby/lib/truffle/rational.rb
Loading 34 characters from /graalvm/jre/languages/ruby/lib/truffle/complex.rb
Loading 1426 characters from /graalvm/jre/languages/ruby/lib/truffle/truffle/lazy-rubygems.rb
Loading 3531 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean.rb
Loading 42 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/version.rb
Loading 497 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/core_ext/name_error.rb
Loading 1324 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checker.rb
Loading 1377 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/levenshtein.rb
Loading 1833 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/jaro_winkler.rb
Loading 605 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/name_error_checkers.rb
Loading 1242 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/name_error_checkers/class_name_checker.rb
Loading 10695 characters from /graalvm/jre/languages/ruby/lib/mri/delegate.rb
Loading 1988 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/name_error_checkers/variable_name_checker.rb
Loading 1532 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/method_name_checker.rb
Loading 310 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/key_error_checker.rb
Loading 104 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/null_checker.rb
Loading 1002 characters from /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/formatters/plain_formatter.rb
Loading 60 characters from main_boot_source
Loading 29 characters from -e
Hello from T-Trace...
```

It is necessary to start GraalVM's Ruby launcher with `--polyglot` parameter
as the `source-tracing.js` script remains written in JavaScript. That's all
fine - mixing languages has never been a problem for GraalVM!

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

Let's use the script on fifty iterations of [sieve.js](https://github.com/jtulach/sieve/blob/7e188504e6cbd2809037450c845138b45724e186/js/sieve.js)
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

and now let's compare it to execution time when running with the **T-Trace** script enabled:

```bash
$ graalvm/bin/js --ttrace=function-count.js sieve.js | tail -n 5
Computed 24832 primes in 11 ms. Last one is 284831
Computed 49664 primes in 28 ms. Last one is 607417
Computed 99328 primes in 70 ms. Last one is 1289927
Hundred thousand prime numbers in 71 ms
142770421 functions have been executed
```

Two milliseconds!? Seriously? Yes, seriously. The **T-Trace** framework
blends the difference between application code and insight gathering scripts
making all the code work as one! The `count++` invocation becomes natural part of
the application at all the places representing `ROOT` of application functions.
**T-Trace** system gives you unlimited instrumentation power at no cost!

## Trully Polyglot - T-Tracing with Ruby

Not only one can instrument any GraalVM language, but also the **T-Trace**
scripts can be written in any GraalVM supported language. Take for example
Ruby and create `source-tracing.rb` file:

```ruby
puts "Initializing Ruby T-Trace script"

ttrace.on('source', ->(ev) {
    name = Truffle::Interop.read(ev, 'name')
    puts "Loading #{name}" 
})

puts 'Hooks are ready!'
```

and then you can launch your `node` application and instrument it with such
Ruby written script:

```bash
$ /graalvm/bin/node --polyglot --ttrace=source-tracing.rb -e "print(6 * 7)"
Initializing Ruby T-Trace script
Loading (core)
Loading resource:/truffleruby/core/pre.rb
Loading resource:/truffleruby/core/basic_object.rb
Loading resource:/truffleruby/core/array.rb
Loading resource:/truffleruby/core/channel.rb
Loading resource:/truffleruby/core/configuration.rb
Loading resource:/truffleruby/core/false.rb
Loading resource:/truffleruby/core/gc.rb
Loading resource:/truffleruby/core/nil.rb
Loading resource:/truffleruby/core/truffle/platform.rb
Loading resource:/truffleruby/core/support.rb
Loading resource:/truffleruby/core/string.rb
Loading resource:/truffleruby/core/random.rb
Loading resource:/truffleruby/core/truffle/io_operations.rb
Loading resource:/truffleruby/core/truffle/kernel_operations.rb
Loading resource:/truffleruby/core/thread.rb
Loading resource:/truffleruby/core/true.rb
Loading resource:/truffleruby/core/type.rb
Loading resource:/truffleruby/core/truffle/ffi/pointer.rb
Loading resource:/truffleruby/core/truffle/ffi/pointer_access.rb
Loading resource:/truffleruby/core/truffle/internal.rb
Loading resource:/truffleruby/core/kernel.rb
Loading resource:/truffleruby/core/truffle/boot.rb
Loading resource:/truffleruby/core/truffle/debug.rb
Loading resource:/truffleruby/core/truffle/encoding_operations.rb
Loading resource:/truffleruby/core/truffle/exception_operations.rb
Loading resource:/truffleruby/core/truffle/hash_operations.rb
Loading resource:/truffleruby/core/truffle/numeric_operations.rb
Loading resource:/truffleruby/core/truffle/proc_operations.rb
Loading resource:/truffleruby/core/truffle/range_operations.rb
Loading resource:/truffleruby/core/truffle/regexp_operations.rb
Loading resource:/truffleruby/core/truffle/stat_operations.rb
Loading resource:/truffleruby/core/truffle/string_operations.rb
Loading resource:/truffleruby/core/truffle/backward.rb
Loading resource:/truffleruby/core/truffle/truffleruby.rb
Loading resource:/truffleruby/core/splitter.rb
Loading resource:/truffleruby/core/stat.rb
Loading resource:/truffleruby/core/io.rb
Loading resource:/truffleruby/core/immediate.rb
Loading resource:/truffleruby/core/module.rb
Loading resource:/truffleruby/core/proc.rb
Loading resource:/truffleruby/core/enumerable_helper.rb
Loading resource:/truffleruby/core/enumerable.rb
Loading resource:/truffleruby/core/enumerator.rb
Loading resource:/truffleruby/core/argf.rb
Loading resource:/truffleruby/core/exception.rb
Loading resource:/truffleruby/core/hash.rb
Loading resource:/truffleruby/core/comparable.rb
Loading resource:/truffleruby/core/numeric.rb
Loading resource:/truffleruby/core/truffle/ctype.rb
Loading resource:/truffleruby/core/integer.rb
Loading resource:/truffleruby/core/regexp.rb
Loading resource:/truffleruby/core/transcoding.rb
Loading resource:/truffleruby/core/encoding.rb
Loading resource:/truffleruby/core/env.rb
Loading resource:/truffleruby/core/errno.rb
Loading resource:/truffleruby/core/file.rb
Loading resource:/truffleruby/core/dir.rb
Loading resource:/truffleruby/core/dir_glob.rb
Loading resource:/truffleruby/core/file_test.rb
Loading resource:/truffleruby/core/float.rb
Loading resource:/truffleruby/core/marshal.rb
Loading resource:/truffleruby/core/object_space.rb
Loading resource:/truffleruby/core/range.rb
Loading resource:/truffleruby/core/struct.rb
Loading resource:/truffleruby/core/tms.rb
Loading resource:/truffleruby/core/process.rb
Loading resource:/truffleruby/core/truffle/process_operations.rb
Loading resource:/truffleruby/core/signal.rb
Loading resource:/truffleruby/core/symbol.rb
Loading resource:/truffleruby/core/mutex.rb
Loading resource:/truffleruby/core/throw_catch.rb
Loading resource:/truffleruby/core/time.rb
Loading resource:/truffleruby/core/rational.rb
Loading resource:/truffleruby/core/rationalizer.rb
Loading resource:/truffleruby/core/complex.rb
Loading resource:/truffleruby/core/complexifier.rb
Loading resource:/truffleruby/core/class.rb
Loading resource:/truffleruby/core/binding.rb
Loading resource:/truffleruby/core/math.rb
Loading resource:/truffleruby/core/method.rb
Loading resource:/truffleruby/core/unbound_method.rb
Loading resource:/truffleruby/core/warning.rb
Loading resource:/truffleruby/core/tracepoint.rb
Loading resource:/truffleruby/core/truffle/interop.rb
Loading resource:/truffleruby/core/truffle/polyglot.rb
Loading resource:/truffleruby/core/posix.rb
Loading resource:/truffleruby/core/main.rb
Loading resource:/truffleruby/core/post.rb
Loading resource:/truffleruby/post-boot/post-boot.rb
Loading /graalvm/jre/languages/ruby/lib/truffle/enumerator.rb
Loading /graalvm/jre/languages/ruby/lib/truffle/thread.rb
Loading /graalvm/jre/languages/ruby/lib/truffle/rational.rb
Loading /graalvm/jre/languages/ruby/lib/truffle/complex.rb
Loading /graalvm/jre/languages/ruby/lib/truffle/truffle/lazy-rubygems.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/version.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/core_ext/name_error.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checker.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/levenshtein.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/jaro_winkler.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/name_error_checkers.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/name_error_checkers/class_name_checker.rb
Loading /graalvm/jre/languages/ruby/lib/mri/delegate.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/name_error_checkers/variable_name_checker.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/method_name_checker.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/key_error_checker.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/spell_checkers/null_checker.rb
Loading /graalvm/jre/languages/ruby/lib/gems/gems/did_you_mean-1.3.0/lib/did_you_mean/formatters/plain_formatter.rb
Loading source-tracing.rb
Hooks are ready!
Loading polyglotEngineWrapper
Loading <internal>
Loading unknown source
Loading <builtin>
Loading internal/bootstrap/loaders.js
Loading internal/bootstrap/node.js
Loading events.js
Loading internal/async_hooks.js
Loading internal/errors.js
Loading util.js
Loading internal/util/inspect.js
Loading internal/util.js
Loading internal/util/types.js
Loading bound function
Loading internal/validators.js
Loading internal/encoding.js
Loading buffer.js
Loading internal/graal/buffer.js
Loading internal/buffer.js
Loading internal/process/per_thread.js
Loading internal/process/main_thread_only.js
Loading internal/process/stdio.js
Loading assert.js
Loading internal/assert.js
Loading fs.js
Loading path.js
Loading internal/constants.js
Loading internal/fs/streams.js
Loading internal/fs/utils.js
Loading stream.js
Loading internal/streams/pipeline.js
Loading internal/streams/end-of-stream.js
Loading internal/streams/legacy.js
Loading _stream_readable.js
Loading ^$
Loading internal/streams/buffer_list.js
Loading internal/streams/destroy.js
Loading internal/streams/state.js
Loading _stream_writable.js
Loading _stream_duplex.js
Loading _stream_transform.js
Loading _stream_passthrough.js
Loading internal/url.js
Loading internal/querystring.js
Loading internal/process/warning.js
Loading internal/process/next_tick.js
Loading internal/process/promises.js
Loading internal/fixed_queue.js
Loading internal/options.js
Loading timers.js
Loading internal/linkedlist.js
Loading internal/timers.js
Loading console.js
Loading tty.js
Loading net.js
Loading internal/net.js
Loading internal/stream_base_commons.js
Loading internal/tty.js
Loading internal/modules/cjs/helpers.js
Loading url.js
Loading punycode.js
Loading internal/safe_globals.js
Loading internal/modules/cjs/loader.js
Loading vm.js
Loading [eval]-wrapper
Loading [eval]
42
```

Write your **T-Trace** scripts in any language you wish! They'll be
ultimatelly useful accross the whole GraalVM ecosystem.

## OpenTracing API on top of **T-Trace**

It is possible to use the **T-Trace** system to implement smooth, declarative
logging via standard OpenTracing API. Use the `npm` command to install
one of the JavaScript libraries for tracing:

```bash
$ npm install opentracing
```

Now you can use its API in your instrument `function-tracing.js` via the
`require` function (once it becomes available):

```js
print(ttrace);

var tracer = null;
var ignore = false;
var countSpan = 0;

ttrace.on('enter', function(ev) {
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
    tags: ['ROOT']
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

<!--

## TODO:

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

-->