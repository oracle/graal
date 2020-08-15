# Fuzz Testing Tools

Sulong provides a set of tools to assist fuzz testing and compare the result against
a native executable (differential testing).

> **WARNING:** These tools are in a very early stage. Expect rough edges and incompatible changes.
> Contributions are very welcome.

## Building

The fuzzing tools are contained in the `SULONG_TOOLS` distribution which is not built by default.
You can build it as follows:

```bash
mx build --dependencies SULONG_TOOLS
```

## Tools

The fuzzing suite contains three categories of tools, random test case generators,
test case reduction tools and a differential test.

### Test Case Generation

The test case generator `mx fuzz <outdir>` randomly produces executable bitcode programs and runs
them natively as well as on sulong. If the result differs, the test case is considered "interesting"
and the bitcode is stored in a subdirectory of `<outdir>`. A `--seed` can be provided to get reproducible results.
Example:

```bash
mx fuzz --seed 1591096497 <outdir>
```

The result directories include the generated bitcode (`autogen.ll`), an executable with embedded bitcode (`autogen`),
as well as text files with the standard out and standard error of native and sulong.

`mx fuzz` supports two test case generators. The default is [`--generator llvm-stress`](https://releases.llvm.org/9.0.0/docs/CommandGuide/llvm-stress.html),
which produces random llvm bitcode files.

The second generator is [`--generator csmith`](https://embed.cs.utah.edu/csmith/),
which generates random C programs. Those programs are then compiled to bitcode.
This generator assumes that the `csmith` executable is on the path and the environment variable
`CSMITH_HEADERS` is set to the location of `csmith.h` (e.g. `/usr/include/csmith-2.3.0`).
In addition to the other result files, `csmith` also stores the generated C file (`autogen.c`).
Although `csmith` only produces programs with defined behavior (according to the C standard),
it might produce programs with infinite loops. If the native execution runs into a timeout,
the generated test is not interesting.

For more information, consult `mx fuzz --help`.

### Test Case Reduction

The random test generators tend to produce huge bitcode files. To make it easier to pinpoint the issue,
sulong provides two automatic test case reduction tools.

The first one is `mx ll-reduce <ll-file>` and is based on [`libLLVMFuzzMutate`](https://releases.llvm.org/9.0.0/docs/FuzzingLLVM.html).
It randomly removes instructions while containing semantic correctness.
If the result is still "interesting" (see next section), it keeps the reduced bitcode file.
Otherwise, it discards the reduced file and tries a different reduction ont the input.
The process continues until either a timeout is reached or until the result is _stable_,
meaning no progress has been made for a certain time.
Example:
```bash
mx ll-reduce autogen.ll
```
See `mx ll-reduce --help` for more options.

The second reduction tool is `mx bugpoint` and is a simple wrapper around [LLVM Bugpoint](https://releases.llvm.org/9.0.0/docs/Bugpoint.html).
The wrapper sets some useful defaults, for example to uses the same interestingness test as
`mx ll-reduce`. Example:
```bash
mx bugpoint autogen.ll
```
See `mx bugpoint --help` for more options.

Although the reduction tools perfectly fit into the fuzz testing toolbox,
they work with arbitrary bitcode files.

### Interesting Test Cases (Differential Testing)

Deciding whether a reduced test case is still interesting is difficult.
The `mx check-interesting <ll-file>` implements a basic "interestingness test" to answer that question.
By default, it compiles the input and checks whether the output of sulong differs from
the output of the native execution. In addition, it supports two flags, `--bin-startswith <prefix>` and
`--sulong-startswith <prefix>` to tell the tool to look for the prefix in the reference output and sulong output, respectively.
The tool can be used with `mx ll-reduce` and `mx bugpooint`, which both support the `--interestingness-test` argument:
```bash
mx ll-reduce --interestingness-test="mx check-interesting --bin-startswith='21' --sulong-startswith='ERROR: java.lang.ArithmeticException'"  autogen.ll
```
