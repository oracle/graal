---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Linux Perf Profiler Support
permalink: /reference-manual/native-image/debugging-and-diagnostics/perf-profiler/
---

# Linux Perf Profiler Support in Native Image

The [`perf` profiler](https://perf.wiki.kernel.org/){:target="_blank"} is a performance analysis tool in Linux that enables you to collect and analyze various performance-related data such as CPU utilization, memory usage, and more.
It is particularly useful for profiling and understanding the behavior of applications.

## Installation

Perf is a profiler tool for Linux systems.
Most distributions come with `perf` pre-installed, but you can install it using your package manager if it is not available.

To install `perf` on Oracle Linux/Red Hat/CentOS, run this command:

```bash
sudo yum install perf
```

To install `perf` on Debian/Ubuntu, run the following commands one by one:

```bash
sudo apt update
sudo apt install linux-tools-common linux-tools-generic
```

After installing `perf`, backup the default values of the following options:

```bash
cat /proc/sys/kernel/perf_event_paranoid > perf_event_paranoid.backup
cat /proc/sys/kernel/kptr_restrict > kptr_restrict.backup
```

Then set them to the new desired values:

```bash
echo -1 > /proc/sys/kernel/perf_event_paranoid
echo 0 > /proc/sys/kernel/kptr_restrict
```

In the example above, `-1` and `0` are used as values, which are the least restrictive, so it is not recommended to use them in production code.
You can customize these values according to your needs.

_perf_event_paranoid_ has four different levels (values):
- **-1**: Allow use of (almost) all events by all users.
- **>=0**: Disallow `ftrace` function tracepoint by users without `CAP_SYS_ADMIN`.
- **>=1**: Disallow CPU event access by users without `CAP_SYS_ADMIN`.
- **>=2**: Disallow kernel profiling by users without `CAP_SYS_ADMIN`.

_kptr_restrict_ has three different levels (values):
- **0**: Kernel pointers are readable by all users.
- **1**: Kernel pointers are only accessible to privileged users (those with the `CAP_SYS_ADMIN` capability).
- **2**: Kernel pointers are hidden from all users.

Once finished using `perf`, restore the original values:

```bash
cat perf_event_paranoid.backup > /proc/sys/kernel/perf_event_paranoid
cat kptr_restrict.backup > /proc/sys/kernel/kptr_restrict
```

## Building Native Executables

The following command assumes that `native-image` is on the system path and available.
If it is not installed, refer to the [Getting Started](README.md).

```bash
native-image -g -H:+PreserveFramePointer <entry_class>
```

The `-g` option instructs Native Image to produce debug information for the generated binary.
`perf` can use this debug information, for example, to provide proper names for types and methods in traces.
The `-H:+PreserveFramePointer` option instructs Native Image to save frame pointers on the stack.
This allows `perf` to reliably unwind stack frames and reconstruct the call hierarchy.

### Profiling of Runtime-Compiled Methods

Native Image can generate detailed runtime compilation metadata for perf in the [jitdump](https://github.com/torvalds/linux/blob/46a51f4f5edade43ba66b3c151f0e25ec8b69cb6/tools/perf/Documentation/jitdump-specification.txt) format.
This enables perf profiling of runtime compiled methods, for example for Truffle compilations.

#### jitdump

The jitdump format stores detailed metadata for runtime compiled code. 
This requires post-processing of the perf data to inject the runtime compilation metadata.

1. Build with jitdump support:

   ```bash
   native-image -g -H:+PreserveFramePointer -H:+RuntimeDebugInfo -H:RuntimeDebugInfoFormat=jitdump ...
   ```
   
   At image-runtime, the jitdump file _<jitdump_dir>/jit-<pid>.dump_ is created, and runtime compilation metadata is written to it.
   The output directory can be configured with `-R:RuntimeJitdumpDir=<jitdump_dir>` (defaults to _./jitdump_).

2. Record with perf:

   When recording profiling data, use the `-k 1` option to ensure time-based events are ordered correctly for injection:
   
   ```bash
   perf record -k 1 -o perf.data <your-application>
   ```
   
   If the perf data was not recorded with `-k 1`, injecting runtime compilation metadata from a jitdump file will fail.

3. Inject jitdump into perf data:

   ```bash
   perf inject -j -i perf.data -o perf.jit.data
   ```
   
   This step:
    - Locates the jitdump file. 
    - Generates a _.so_ file for each runtime compilation entry in the jitdump file. 
    - Injects runtime compilation metadata into the profiling data and stores it in _perf.jit.data_.

4. Inspect profiling data:

   ```bash
   perf report -i perf.jit.data
   ```
   
   Symbols from the jitdump file appear as coming from _jitted-<pid>-<code_id>.so_, where `code_id` is the index of a compilation entry in the jitdump file.

## Basic Operations

### CPU Profiling

1. List all available events:

   ```bash
   perf list
   ```
   This command displays a list of all available events that you can use for profiling.

2. Record CPU events:

   ```bash
   perf record -e <event> -o perf.data <your_executable>
   ```

   Replace `<event>` with the desired event from the list.
   This command profiles your executable and save the data to a file named _perf.data_.

3. Generate a report:

   ```bash
   perf report
   ```

   This command generates a report based on the collected data.
   You can use various options to customize the output.

### Memory Profiling

1. Record memory events:

   ```bash
   perf record -e memory:<event> -o perf.data <your_executable>
   ```

   Replace `<event>` with a specific memory event.
   This command profiles memory-related events.

2. Generate a memory report:

   ```bash
   perf report --sort=dso
   ```

   This command generates a report focused on memory-related events, sorted by dynamic shared object (DSO).

### Tracing

1. Record system-wide traces:

   ```bash
   sudo perf record -a -g -o perf.data
   ```

   This command records system-wide traces, including call-graph information, and saves the data to a file named _perf.data_. 
   Use sudo for system-wide tracing.

2. Generate a trace report:
   ```bash
   perf script
   ```

   This command generates a script that can be used for analyzing the recorded trace data.

## Generating Flame Graphs from Profiling Data

[FlameGraph](https://github.com/brendangregg/FlameGraph) is a tool written in Perl that can be used to produce flame graphs from perf profiling data.
Flame graphs generated by this tool visualize stack samples as interactive SVGs, making it easy to identify hot code paths in an application.

1. Download the tool and record profiling data as described in [Basic Operations](#basic-operations).

   Make sure the profiling data was recorded with `-g` to capture call graphs, otherwise the flame graph will be flat.

2. Fold stacks:
   ```bash
   perf script -i perf.data | ./stackcollapse-perf.pl > perf.data.folded
   ```

3. Render an SVG:
   ```bash
   ./flamegraph.pl perf.data.folded > perf.data.svg
   ```
   
4. Open the flame graph:

   Use an application to view the generated SVG file (for example, `firefox`, `chromium`).
   ```bash
   firefox perf.data.svg
   ```

### Highlighting Runtime-Compiled Methods

If the native image supports [profiling of runtime-compiled methods](#profiling-of-runtime-compiled-methods), it is possible to highlight runtime-compiled symbols in the flame graph.

1. Build the native image with jitdump support, record profiling data and inject the jitdump information as described in [jitdump](#jitdump).

2. Fold stacks:

   This involves folding the stacks for the non-jitdump-injected _perf.data_ and the jitdump-injected _perf.jit.data_.
   ```bash
   perf script -i perf.data | ./stackcollapse-perf.pl > perf.data.folded
   perf script -i perf.jit.data | ./stackcollapse-perf.pl > perf.jit.data.folded
   ```

3. Generate a consistent color palette map:
   
   Use the non-jitdump-injected _perf.data.folded_ to create a consistent palette map in _palette.map_ for events in _perf.data_.
   The first call with `--cp` will create the map while subsequent calls with `--cp` reuse the map for consistent coloring of known events.
   This also produces a flame graph for the non-jitdump-injected data.
   ```bash
   ./flamegraph.pl --cp perf.data.folded > perf.data.svg
   ```

4. Reuse the color palette map:

   Use the consistent palette for already known events with `--cp` for the jitdump-injected _perf.jit.data.folded_.
   This is, events already seen in the non-jitdump-injected _perf.data.folded_ get a fixed coloring.
   New events get a random coloring from the palette selected with the `--color` option (e.g. `mem`).
   ```bash
   ./flamegraph.pl --cp --color mem perf.jit.data.folded > perf.jit.data.svg
   ```

5. Open the flame graph:
   ```bash
   firefox perf.jit.data.svg
   ```

### Generate an Invocation-Time-Ordered Flame Graph

Generate a stack-reversed flame graph with the topmost frames shown at the bottom of the flame graph in order of invocation time.
Calls appear left-to-right in chronological order, with stack frames in each call arranged top-to-bottom from oldest to newest.
Events from all threads contributing to the profiling data are shown interleaved.
```bash
./flamegraph.pl --reverse perf.data.folded > perf.data.svg
firefox perf.data.svg
```

### Related Documentation

* [Debug Information](DebugInfo.md)
* [JDK Flight Recorder (JFR)](JFR.md)