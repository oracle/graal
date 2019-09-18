# T-Trace: User's Handle to the Ultimate Tracing and Insights Gathering

[T-Trace](T-Trace.md) is an end user friendly tool for tracing and
instrumentation of scripts written in any language by scripts written
in any (other) language.

## Hello World!

Create a simple `source-tracing.js` script with following content:

```js
instrumenter.on('source', function(ev) {
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
the provided `instrumenter` object to attach a *source* listener to the runtime.
As such, whenever the *node.js* framework loaded internal or user script,
the listener got notified of it and could take an action - in this case
printing the length and name of processed script.

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
