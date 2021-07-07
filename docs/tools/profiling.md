---
layout: docs
toc_group: tools
link_title: Profiling Command Line Tools
permalink: /tools/profiling/
---

# Profiling Command Line Tools

GraalVM **profiling command line tools** help you optimize your code
through analysis of CPU and memory usage.

Most applications spend 80% of their runtime in 20% of the code. For this
reason, to optimize the code, it is essential to know where the application
spends its time. In this section, we use an example application to demonstrate
the three main profiling capabilities that GraalVM offers: CPU Tracer, CPU
Sampler, and Memory Tracer.

This example application uses a basic prime
number calculator based on the [Sieve of Eratosthenes](https://en.wikipedia.org/wiki/Sieve_of_Eratosthenes)
algorithm.

1. Copy the following code into a new file named `primes.js`:

    ```javascript
    class AcceptFilter {
        accept(n) {
            return true
        }
    }

    class DivisibleByFilter {
        constructor(number, next) {
            this.number = number;
            this.next = next;
        }

        accept(n) {
            var filter = this;
            while (filter != null) {
                if (n % filter.number === 0) {
                    return false;
                }
                filter = filter.next;
            }
            return true;
        }
    }

    class Primes {
        constructor() {
            this.number = 2;
            this.filter = new AcceptFilter();
        }

        next() {
            while (!this.filter.accept(this.number)) {
                this.number++;
            }
            this.filter = new DivisibleByFilter(this.number, this.filter);
            return this.number;
        }
    }

    var primes = new Primes();
    var primesArray = [];
    for (let i = 0; i < 5000; i++) {
        primesArray.push(primes.next());
    }
    console.log(`Computed ${primesArray.length} prime numbers. ` +
                `The last 5 are ${primesArray.slice(-5)}.`);
    ```

2. Run `js primes.js`. The example application should print output as follows:
    ```shell
    js primes.js
    Computed 5000 prime numbers. The last 5 are 48563,48571,48589,48593,48611.
    ```

    This program takes a moment to compute. Next you will check where the time is spent.

3. Run `js primes.js --cpusampler` to enable CPU sampling. CPU Sampler should print output for the example application as follows:
    ```shell
    js primes.js --cpusampler
    Computed 5000 prime numbers. The last 5 are 48563,48571,48589,48593,48611.
    ---------------------------------------------------------------------------------------------------
    Sampling Histogram. Recorded 1184 samples with period 1ms
      Self Time: Time spent on the top of the stack.
      Total Time: Time the location spent on the stack.
      Opt %: Percent of time spent in compiled and therfore non-interpreted code.
    ---------------------------------------------------------------------------------------------------
     Name        |      Total Time     |  Opt % ||       Self Time     |  Opt % | Location
    ---------------------------------------------------------------------------------------------------
     next        |       1216ms  98.5% |  87.9% ||       1063ms  85.9% |  99.0% | primes.js~31-37:564-770
     accept      |        159ms  11.2% |  22.7% ||        155ms  12.5% |  14.8% | primes.js~13-22:202-439
     :program    |       1233ms 100.0% |   0.0% ||         18ms   1.5% |   0.0% | primes.js~1-47:0-1024
     constructor |          1ms   0.1% |   0.0% ||          1ms   0.1% |   0.0% | primes.js~7-23:72-442
    ---------------------------------------------------------------------------------------------------

    ```
    The sampler prints an execution time histogram for each JavaScript function.
    By default, CPU sampling takes a sample every single millisecond. From the
    result, we can see that roughly 96% of the time is spent
    in the `DivisibleByFilter.accept` function.

    ```javascript
    accept(n) {
        var filter = this;
        while (filter != null) {
            if (n % filter.number === 0) {
                return false;
            }
            filter = filter.next;
        }
        return true;
    }
    ```
    Now you can find out more about this function by filtering the samples, and
    include statements in the profile in addition to methods.

4. Run `js primes.js --cpusampler --cpusampler.Mode=statements --cpusampler.FilterRootName=*accept`
to collect statement samples for all functions that end with `accept`:
    ```shell
    js primes.js --cpusampler --cpusampler.Mode=statements --cpusampler.FilterRootName=*accept
    Computed 5000 prime numbers. The last 5 are 48563,48571,48589,48593,48611.
    ----------------------------------------------------------------------------------------------------
    Sampling Histogram. Recorded 1567 samples with period 1ms
      Self Time: Time spent on the top of the stack.
      Total Time: Time the location spent on the stack.
      Opt %: Percent of time spent in compiled and therfore non-interpreted code.
    ----------------------------------------------------------------------------------------------------
     Name         |      Total Time     |  Opt % ||       Self Time     |  Opt % | Location
    ----------------------------------------------------------------------------------------------------
     accept~16-18 |        436ms  27.8% |  94.3% ||        435ms  27.8% |  94.5% | primes.js~16-18:275-348
     accept~15    |        432ms  27.6% |  97.0% ||        432ms  27.6% |  97.0% | primes.js~15:245-258
     accept~19    |        355ms  22.7% |  95.5% ||        355ms  22.7% |  95.5% | primes.js~19:362-381
     accept~17    |          1ms   0.1% |   0.0% ||          1ms   0.1% |   0.0% | primes.js~17:322-334
    ----------------------------------------------------------------------------------------------------

    ```
    Roughly 30% of the time is spent in this _if condition_:
    ```javascript
    if (n % filter.number === 0) {
        return false;
    }

    ```
    The _if condition_ contains an expensive modulo operation, which might
    explain the runtime of the statement.

    Now use CPU Tracer to collect execution counts of each statement:

5. Run `js primes.js --cputracer --cputracer.TraceStatements --cputracer.FilterRootName=*accept`
to collect execution counts for all statements in methods ending with `accept`:
    ```shell
    js primes.js --cputracer --cputracer.TraceStatements --cputracer.FilterRootName=*accept
    Computed 5000 prime numbers. The last 5 are 48563,48571,48589,48593,48611.
    -----------------------------------------------------------------------------------------
    Tracing Histogram. Counted a total of 351278226 element executions.
      Total Count: Number of times the element was executed and percentage of total executions.
      Interpreted Count: Number of times the element was interpreted and percentage of total executions of this element.
      Compiled Count: Number of times the compiled element was executed and percentage of total executions of this element.
    -----------------------------------------------------------------------------------------
     Name     |          Total Count |    Interpreted Count |       Compiled Count | Location
    -----------------------------------------------------------------------------------------
     accept   |     117058669  33.3% |         63575   0.1% |     116995094  99.9% | primes.js~15:245-258
     accept   |     117053670  33.3% |         63422   0.1% |     116990248  99.9% | primes.js~16-18:275-348
     accept   |     117005061  33.3% |         61718   0.1% |     116943343  99.9% | primes.js~19:362-381
     accept   |         53608   0.0% |          1857   3.5% |         51751  96.5% | primes.js~14:215-227
     accept   |         53608   0.0% |          1857   3.5% |         51751  96.5% | primes.js~13-22:191-419
     accept   |         48609   0.0% |          1704   3.5% |         46905  96.5% | primes.js~17:322-334
     accept   |          4999   0.0% |           153   3.1% |          4846  96.9% | primes.js~21:409-412
     accept   |             1   0.0% |             1 100.0% |             0   0.0% | primes.js~2-4:25-61
     accept   |             1   0.0% |             1 100.0% |             0   0.0% | primes.js~3:52-55
    -----------------------------------------------------------------------------------------
    ```

    The output shows execution counters for each statement, instead of timing
    information. Tracing histograms often provides insights into the behavior
    of the algorithm that needs optimization.

6. Run `js primes.js --experimental-options --memtracer` to display source code locations and
counts of reported allocations. Note that the Memory Tracer tool for capturing allocations is currently an
experimental feature in GraalVM. As such, `--memtracer` must
be preceded by the `--experimental-options` command line option.
    ```shell
    js primes.js --experimental-options --memtracer
    Computed 5000 prime numbers. The last 5 are 48563,48571,48589,48593,48611.
    ------------------------------------------------------------
     Location Histogram with Allocation Counts. Recorded a total of 5013 allocations.
       Total Count: Number of allocations during the execution of this element.
       Self Count: Number of allocations in this element alone (excluding sub calls).
    ------------------------------------------------------------
     Name        |      Self Count |     Total Count |  Location
    ------------------------------------------------------------
     next        |     5000  99.7% |     5000  99.7% | primes.js~31-37:537-737
     :program    |       11   0.2% |     5013 100.0% | primes.js~1-46:0-966
     Primes      |        1   0.0% |        1   0.0% | primes.js~25-38:454-739
    ------------------------------------------------------------

    ```

    This output shows the number of allocations which were recorded per function.
    For each prime number that was computed, the program allocates one object in
    `next` and one in `constructor` of `DivisibleByFilter`. Allocations are recorded
    independently of whether they could get eliminated by the compiler.

    The GraalVM compiler is particularly powerful in optimizing allocations and can push
    allocations into infrequent branches to increase execution performance. The
    GraalVM team plans to add information about memory optimizations to the
    memory tracer in the future.

## Tools Options
Use the `--help:tools` option in all guest language launchers to display
reference information for CPU Sampler, CPU Tracer, and Memory Tracer.

The current set of available options is as follows:

### CPU Sampler Options

- `--cpusampler`: enables CPU Sampler. Disabled by default.
- `--cpusampler.Delay=<Long>`: delays the sampling for the given number of milliseconds (default: 0).
- `--cpusampler.FilterFile=<Expression>`: applies a wildcard filter for source
file paths, for example, `*program*.sl`. The default is &lowast;.
- `--cpusampler.FilterLanguage=<String>`: profiles languages only with the
matching mime-type, for example, `+`. The default is no filter.
- `--cpusampler.FilterRootName=<Expression>`: applies a wildcard filter for
program roots, for example, `Math.*`. The default is &lowast;.
- `--cpusampler.GatherHitTimes`: saves a timestamp for each taken sample. The default is false.
- `--cpusampler.Mode=<Mode>`:  describes the level of sampling detail. Please note that increased detail can lead to reduced accuracy.
    - `exclude_inlined_roots`: samples roots excluding inlined functions (enabled by default)
    - `roots`: samples roots including inlined functions
    - `statements`: samples all statements
- `--cpusampler.Output=<Output>`: prints a `histogram` or `calltree` as output.
The default is `histogram`.
- `--cpusampler.Period=<Long>`: specifies the period, in milliseconds, to
sample the stack.
- `--cpusampler.SampleInternal`: captures internal elements. The default is
false.
- `--cpusampler.StackLimit=<Integer>`: specifies the maximum number of stack
elements.
- `--cpusampler.SummariseThreads `: prints sampling output as a summary of all `per thread` profiles. The default is false.

### CPU Tracer Options

- `--cputracer`: enables the CPU tracer. Disabled by default.
- `--cputracer.FilterFile=<Expression>`: applies a wildcard filter for source
file paths, for example, `*program*.sl`. The default is &lowast;.
- `--cputracer.FilterLanguage=<String>`: profiles languages only with the
matching mime-type, for example, `+`. The default is no filter.
- `--cputracer.FilterRootName=<Expression>`: applies a wildcard filter for
program roots, for example, `Math.*`. The default is &lowast;.
- `--cputracer.Output=<Output>` prints a `histogram` or `json` as output. The default is `histogram`.
- `--cputracer.TraceCalls`: captures calls when tracing. The default is false.
- `--cputracer.TraceInternal`: traces internal elements. The default is false.
- `--cputracer.TraceRoots=<Boolean>`: captures roots when tracing. The default
is true.
- `--cputracer.TraceStatements`: captures statements when tracing. The default
is false.

### Memory Tracer Options

The memory tracer tool is currently an experimental tool. Make sure to prepend the `--experimental-options` flag to enable `--memtracer`.

- `--memtracer`: enables the memory tracer. Disabled by default.
- `--memtracer.FilterFile=<Expression>`: applies a wildcard filter for source file paths, for example, `*program*.sl`. The default is &lowast;.
- `--memtracer.FilterLanguage=<String>`: profiles languages only with the matching mime-type, for example, `+`. The default is no filter.
- `--memtracer.FilterRootName=<Expression>`: applies a wildcard filter for program roots, for example, `Math.*`. The default is &lowast;.
- `--memtracer.Output=<Format>`: prints a `typehistogram`, `histogram`, or `calltree` as output. The default is `histogram`.
- `--memtracer.StackLimit=<Integer>`: sets the maximum number of maximum stack elements.
- `--memtracer.TraceCalls`: captures calls when tracing. The default is false.
- `--memtracer.TraceInternal`: captures internal elements. The default is false.
- `--memtracer.TraceRoots=<Boolean>`: captures roots when tracing. The default is true.
- `--memtracer.TraceStatements`: captures statements when tracing. The default is false.
