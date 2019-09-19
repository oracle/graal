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

## Historam - Use Full Power of Your Language!

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
        print(`${number} calls to ${entry[0]}`);
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
 10 calls to getOptionValue
  8 calls to protoGetter
  7 calls to addListener
  7 calls to _addListener
  7 calls to checkListener
  6 calls to emit
  6 calls to isSignal
  6 calls to copyProps
  6 calls to normalizeString
  5 calls to EventEmitter
  5 calls to EventEmitter.init
  5 calls to resolve
  4 calls to emitHookFactory
  4 calls to debuglog
  4 calls to getHighWaterMark
  4 calls to highWaterMarkFrom
  4 calls to Stream
  4 calls to getNewAsyncId
  3 calls to makeSystemErrorWithCode
  3 calls to makeSafe
  3 calls to SafeSet
  3 calls to isEmpty
  2 calls to makeGetter
  2 calls to makeSetter
  2 calls to defineIDLClass
  2 calls to ImmediateList
  2 calls to createWritableStdioStream
  2 calls to WriteStream
  2 calls to Socket
  2 calls to _extend
  2 calls to Duplex
  2 calls to Readable
  2 calls to ReadableState
  2 calls to BufferList
  2 calls to Writable
  2 calls to WritableState
  2 calls to Readable.on
  2 calls to initSocketHandle
  2 calls to undestroy
  2 calls to createWriteErrorHandler
  2 calls to debug
  2 calls to runInThisContext
  2 calls to validateInteger
  2 calls to shift
  1 calls to bootstrapInternalLoaders
  1 calls to bootstrapNodeJSCore
  1 calls to startup
  1 calls to setupProcessObject
  1 calls to setupProcessFatal
  1 calls to setupProcessICUVersions
  1 calls to setupGlobalVariables
  1 calls to makeTextDecoderJS
  1 calls to patchBufferPrototype
  1 calls to createPool
  1 calls to createUnsafeArrayBuffer
  1 calls to setupAssert
  1 calls to setupConfig
  1 calls to setupSignalHandlers
  1 calls to eventNames
  1 calls to setupUncaughtExceptionCapture
  1 calls to setupProcessWarnings
  1 calls to setupNextTick
  1 calls to setupPromises
  1 calls to FixedQueue
  1 calls to FixedCircularBuffer
  1 calls to setupStdio
  1 calls to getMainThreadStdio
  1 calls to setupProcessStdio
  1 calls to setupProcessMethods
  1 calls to setupPosixMethods
  1 calls to setupRawDebug
  1 calls to setupHrtime
  1 calls to setupCpuUsage
  1 calls to setupMemoryUsage
  1 calls to setupKillAndExit
  1 calls to setupChildProcessIpcChannel
  1 calls to setupGlobalTimeouts
  1 calls to setupGlobalConsole
  1 calls to getStdout
  1 calls to getStderr
  1 calls to $getMaxListeners
  1 calls to get
  1 calls to Console
  1 calls to setupInspector
  1 calls to setupGlobalURL
  1 calls to setupAllowedFlags
  1 calls to preloadModules
  1 calls to addBuiltinLibsToObject
  1 calls to evalScript
  1 calls to wrapForBreakOnFirstLine
  1 calls to Module._initPaths
  1 calls to tryGetCwd
  1 calls to Module
  1 calls to updateChildren
  1 calls to join
  1 calls to normalize
  1 calls to Module._nodeModulePaths
  1 calls to Module._compile
  1 calls to stripShebang
  1 calls to dirname
  1 calls to makeRequireFunction
  1 calls to require
  1 calls to Module.require
  1 calls to Module._load
  1 calls to Module._resolveFilename
  1 calls to createScript
  1 calls to Script
  1 calls to getRunInContextArgs
  1 calls to _tickCallback
  1 calls to emitPromiseRejectionWarnings
  1 calls to dumpHistogram
=================
```

Table with names and counts of function invocations is printed out when the
`node` process exists (requires fix of GR-18337).

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

- Your applications are inherently ready for
tracing without giving up any speed. 

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
