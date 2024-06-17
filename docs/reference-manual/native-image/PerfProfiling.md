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
native-image -g <entry_class>
```

The `-g` option instructs Native Image to produce debug information for the generated binary.
`perf` can use this debug information, for example, to provide proper names for types and methods in traces.

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

### Related Documentation

* [Debug Information](DebugInfo.md)
* [JDK Flight Recorder (JFR)](JFR.md)