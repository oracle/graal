#
# Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
import argparse
import contextlib
import shlex
from argparse import ArgumentParser
from enum import Enum
from typing import List, Set, Tuple, NamedTuple

import mx
import mx_benchmark
import mx_sdk
from mx_polybench.model import _resolve_all_benchmarks, _get_all_suites, PolybenchBenchmarkSuiteEntry

_suite = mx.suite("truffle")


class PolybenchArgumentsSpecification(NamedTuple):
    """Models the different argument lists used in a polybench run."""

    mx_benchmark_args: List[str] = []
    vm_args: List[str] = []
    polybench_args: List[str] = []

    MX_BENCHMARK_FLAG = "--mx-benchmark-args"
    VM_FLAG = "--vm-args"
    POLYBENCH_FLAG = "--polybench-args"

    @classmethod
    def parser_help_message(cls):
        return (
            "additional benchmark arguments. By default, arguments are passed to the Polybench launcher, "
            f"but you may use flags to forward arguments to specific components: "
            f'"{cls.MX_BENCHMARK_FLAG}" for mx benchmark, '
            f'"{cls.VM_FLAG}" for the host VM or native-image, or '
            f'"{cls.POLYBENCH_FLAG}" for the Polybench launcher (pass "--help" to see launcher help). '
            f'For example: "-i 2 {cls.MX_BENCHMARK_FLAG} --fail-fast {cls.VM_FLAG} -ea '
            f'{cls.POLYBENCH_FLAG} --engine.TraceCompilation=true".'
        )

    @classmethod
    def parse(cls, args: List[str]) -> "PolybenchArgumentsSpecification":
        """Extract a set of arguments for mx benchmark, the VM, and polybench from command line arguments."""
        mx_benchmark_args = []
        vm_args = []
        polybench_args = []
        flag_to_args = {
            cls.MX_BENCHMARK_FLAG: mx_benchmark_args,
            cls.VM_FLAG: vm_args,
            cls.POLYBENCH_FLAG: polybench_args,
        }

        current_args = polybench_args
        for arg in args:
            if arg in flag_to_args:
                current_args = flag_to_args[arg]
            else:
                current_args.append(arg)

        return PolybenchArgumentsSpecification(mx_benchmark_args, vm_args, polybench_args)

    def to_normalized_command_line(self) -> List[str]:
        result = []
        if self.mx_benchmark_args:
            result.append(PolybenchArgumentsSpecification.MX_BENCHMARK_FLAG)
            result.extend(self.mx_benchmark_args)
        if self.vm_args:
            result.append(PolybenchArgumentsSpecification.VM_FLAG)
            result.extend(self.vm_args)
        if self.polybench_args:
            result.append(PolybenchArgumentsSpecification.POLYBENCH_FLAG)
            result.extend(self.polybench_args)
        return result

    def __str__(self):
        return '"' + " ".join(self.to_normalized_command_line()) + '"'

    def __bool__(self):
        return bool(self.mx_benchmark_args or self.vm_args or self.polybench_args)

    def append(self, other: "PolybenchArgumentsSpecification") -> "PolybenchArgumentsSpecification":
        return PolybenchArgumentsSpecification(
            mx_benchmark_args=self.mx_benchmark_args + other.mx_benchmark_args,
            vm_args=self.vm_args + other.vm_args,
            polybench_args=self.polybench_args + other.polybench_args,
        )


def polybench_list():
    benchmarks = _resolve_all_benchmarks()
    mx.log("Listing all available polybench benchmarks.")
    mx.log('Benchmark files (run using "mx polybench <glob_pattern>"):')
    file_found = False
    for benchmark_name, resolved_benchmark in benchmarks.items():
        file_found = True
        mx.log(f"\t{benchmark_name}")
        mx.logv(f"\t\tabsolute path: {resolved_benchmark.absolute_path}")
        mx.logv(f"\t\tdistribution: {resolved_benchmark.suite.benchmark_distribution}")
        mx.logv(f"\t\tdeclaring suite: {resolved_benchmark.suite.mx_suite.name}")
        mx.logv(f"\t\trequired languages: {resolved_benchmark.suite.languages}")
    if not file_found:
        mx.log("\tno benchmark files found")

    suites = _get_all_suites()
    mx.log(
        'Suites (run using "mx polybench --suite <suite_name>" or "mx polybench --suite <suite_name>:<tag1>,<tag2>,..."):'
    )
    suite_found = False
    for suite_name, suite in suites.items():
        suite_found = True
        mx.log(f"\t{suite_name}: {suite.tags if suite.tags else '{}'}")
    if not suite_found:
        mx.log("\tno suites found")


def polybench_run(parsed_args, raw_args):
    if parsed_args.suite:
        _run_suite(parsed_args)
    elif parsed_args.dry_run_polybench:
        _dry_run_polybench(raw_args)
    else:
        _run_benchmark_pattern(parsed_args)


def _dry_run_polybench(raw_args):
    raw_args.remove("--dry-run-polybench")
    mx.log(_mx_polybench_command_string(raw_args))


def _run_suite(args):
    suite, tags = _get_suite_and_tags(args)
    if tags - suite.tags:
        mx.abort(
            f'Requested tag(s) not available for suite "{suite}": {tags - suite.tags}. Available tags: {suite.tags}'
        )

    mx.log(f"Running suite {suite.name} with tags {tags}.")

    # Arguments passed in a suite run are appended after the arguments specified by the suite runner.
    if args.benchmark_arguments:
        mx.warn(
            f"Arguments were supplied on the command line ({args.benchmark_arguments}). "
            "These arguments will be inserted after any arguments supplied by the suite runner."
        )
    features = _get_vm_features(args)
    if features:
        mx.warn(
            f"Some features were enabled ({features}), but these are ignored when running a suite. "
            "You can directly enable these features in the suite runner itself."
        )
    override_arguments = (
        PolybenchArgumentsSpecification.parse(args.benchmark_arguments)
        .append(
            # Ensure results are combined across multiple runs.
            PolybenchArgumentsSpecification(mx_benchmark_args=["--append-results"])
        )
        .to_normalized_command_line()
    )

    base_args = []
    if args.dry_run:
        base_args.append("--dry-run")
    elif args.dry_run_polybench:
        base_args.append("--dry-run-polybench")

    def polybench_run_function(argument_list: List[str]) -> None:
        raw_args = base_args + argument_list + override_arguments
        parsed_args = parser.parse_args(raw_args)
        polybench_run(parsed_args, raw_args)

    with _run_suite_context(suite, tags):
        suite.runner(polybench_run_function, tags)


_current_suite_and_tags = None


@contextlib.contextmanager
def _run_suite_context(suite: PolybenchBenchmarkSuiteEntry, tags: Set[str]):
    global _current_suite_and_tags
    if _current_suite_and_tags is not None:
        current_suite, current_tags = _current_suite_and_tags
        mx.abort(
            f"Attempting to run suite {suite.name} with tags {tags} from a suite itself "
            f"(suite {current_suite.name}, tags {current_tags}). "
            "This is not supported and likely indicates a bug in your suite runner."
        )
    try:
        _current_suite_and_tags = suite, tags
        yield
    finally:
        _current_suite_and_tags = None


class VMFeature(Enum):
    NATIVE = 1
    PGO = 2
    G1GC = 3


def _get_vm_features(args) -> Set[VMFeature]:
    def require_native(feature_name):
        if not args.is_native:
            mx.abort(f"Feature {feature_name} is only supported on native runs, but native mode is not selected.")

    result = set()
    if args.is_native:
        result.add(VMFeature.NATIVE)
    if args.pgo:
        require_native("PGO")
        result.add(VMFeature.PGO)
    if args.g1gc:
        require_native("g1gc")
        result.add(VMFeature.G1GC)
    return result


def _run_benchmark_pattern(args):
    arguments_spec = PolybenchArgumentsSpecification.parse(args.benchmark_arguments)
    run_spec = PolybenchRunSpecification(args.benchmarks, _get_vm_features(args), arguments_spec)
    _validate_jdk(run_spec.is_native())
    mx.logv(f"Performing polybench run: {run_spec}")
    _run_specification(run_spec, pattern_is_glob=args.pattern_is_glob, dry_run=args.dry_run)


def _validate_jdk(is_native: bool) -> mx.JDKConfig:
    jdk = mx.get_jdk()
    if not mx_sdk.GraalVMJDKConfig.is_graalvm(jdk.home):
        rerun_details = (
            'You can change the JDK using "mx --java-home $GRAALVM_HOME", where GRAALVM_HOME points to a downloaded GraalVM release '
            '(or a GraalVM built from source, e.g., with "mx -p /vm --env ce build").'
        )
        if is_native:
            mx.abort(
                f"Polybench was invoked with a non-Graal JDK ({jdk.home}), but a native image run was requested. "
                f"Re-run using a Graal JDK. " + rerun_details
            )
        else:
            mx.warn(
                f"Polybench is intended to run on a Graal JDK, but it was invoked with a non-Graal JDK ({jdk.home}). "
                f"If you encounter issues, consider re-running using a GraalVM release. " + rerun_details
            )
    mx.logv(f"Using GraalVM at {jdk.home}")
    return jdk


def _parse_mx_benchmark_pattern(pattern: str, pattern_is_glob: bool) -> str:
    if ":" in pattern:
        message = f'Invalid benchmark pattern "{pattern}".'
        if pattern.count(":") == 1:
            message += ' This pattern looks like a suite. Did you forget "--suite"?'
        else:
            message += ' Use "mx polybench -h" to view the expected format for benchmark patterns.'
        mx.abort(message)

    if pattern_is_glob:
        # * should match every character except a file separator.
        regex_pattern = pattern.replace("*", "[^/]*")
    else:
        # already a regex.
        regex_pattern = pattern

    return f"r[{regex_pattern}]"


def _get_suite_and_tags(args) -> Tuple[PolybenchBenchmarkSuiteEntry, Set[str]]:
    polybench_suite_name, tags = _parse_suite_and_tags(args)
    all_suites = _get_all_suites()
    suite = all_suites.get(polybench_suite_name, None)
    if not suite:
        mx.abort(f'Suite "{polybench_suite_name}" not found. Available suites: {list(all_suites.keys())}')
    if not suite.runner:
        mx.abort(
            f'Suite "{polybench_suite_name}" does not define a runner. '
            f'Remove "--suite {args.benchmarks}" and specify a benchmark pattern, '
            "or define a suite runner in the mx registration to continue."
        )
    tags = tags or suite.tags
    if tags - suite.tags:
        mx.abort(f'Unknown tags for suite "{polybench_suite_name}": {tags - suite.tags}. Available tags: {suite.tags}')
    return suite, tags


def _parse_suite_and_tags(args) -> Tuple[str, Set[str]]:
    assert args.suite
    parts = args.benchmarks.split(":")
    if len(parts) == 1:
        return parts[0], set()
    elif len(parts) == 2:
        return parts[0], set(parts[1].split(","))
    else:
        mx.abort(
            f'Invalid suite specification "{args.benchmarks}". '
            "Specification should be a suite name, optionally followed by a colon and a comma-separated "
            'list of tags (e.g., "sl:gate,benchmark")'
        )


class PolybenchRunSpecification(NamedTuple):
    """Models a single polybench run."""

    pattern: str
    vm_features: Set[VMFeature] = set()
    arguments: PolybenchArgumentsSpecification = PolybenchArgumentsSpecification()

    def append_arguments(self, other: PolybenchArgumentsSpecification) -> "PolybenchRunSpecification":
        return PolybenchRunSpecification(
            pattern=self.pattern, vm_features=self.vm_features, arguments=self.arguments.append(other)
        )

    def is_native(self) -> bool:
        return VMFeature.NATIVE in self.vm_features

    def jvm_name(self) -> str:
        return "native-image-java-home" if self.is_native() else "java-home"

    def jvm_config(self) -> str:
        features = []
        if VMFeature.G1GC in self.vm_features:
            features.append("g1gc")
        if VMFeature.PGO in self.vm_features:
            features.append("pgo")
        return "-".join(features) if features else "default"


def _run_specification(spec: PolybenchRunSpecification, pattern_is_glob: bool = True, dry_run: bool = False):
    pattern = _parse_mx_benchmark_pattern(spec.pattern, pattern_is_glob)
    mx_benchmark_args = (
        [f"polybench:{pattern}"]
        + spec.arguments.mx_benchmark_args
        + ["--", f"--jvm={spec.jvm_name()}", f"--jvm-config={spec.jvm_config()}"]
        + spec.arguments.vm_args
        + ["--"]
        + spec.arguments.polybench_args
    )
    command_string = _mx_benchmark_command_string(mx_benchmark_args)
    if dry_run:
        mx.log(command_string)
        return

    mx.logv(f"Running command: {command_string}")
    mx_benchmark.benchmark(mx_benchmark_args)


def _base_mx_command() -> List[str]:
    command = ["mx", "--java-home", mx.get_jdk().home]
    for dynamic_import, in_subdir in mx.get_dynamic_imports():
        if in_subdir:
            command += ["--dy", f"/{dynamic_import}"]
        else:
            command += ["--dy", dynamic_import]
    return command


def _mx_polybench_command_string(mx_polybench_args: List[str]) -> str:
    return _build_command(_base_mx_command() + ["polybench"] + mx_polybench_args)


def _mx_benchmark_command_string(mx_benchmark_args: List[str]) -> str:
    return _build_command(_base_mx_command() + ["benchmark"] + mx_benchmark_args)


def _build_command(command: List[str]) -> str:
    return " ".join(shlex.quote(token) for token in command)


def _create_parser() -> ArgumentParser:
    parser = ArgumentParser(
        prog="mx polybench",
        description=(
            "mx polybench is a simple command line interface for Polybench, the system used to benchmark Truffle languages. "
            "It is a thin wrapper around Polybench's mx benchmark integration that makes it easy to discover and run benchmarks. "
            "It also supports batch execution of the benchmarks in a suite, which is convenient for defining CI jobs."
        ),
        usage="%(prog)s [options*] benchmarks [benchmark_arguments*]",
    )

    parser.add_argument(
        "benchmarks",
        nargs="?",
        help='a glob pattern representing benchmarks to run (e.g., "interpreter/*"), '
        'or a suite specification if "--suite" is provided. '
        'Use "--list" to see a list of available benchmarks.',
    )
    parser.add_argument(
        "--list", action="store_true", help="list all available benchmarks (without actually executing them)."
    )

    run_flags = set()

    def run_flag(run_arg):
        run_flags.add(run_arg)
        return run_arg

    dry_run_group = parser.add_mutually_exclusive_group()
    dry_run_group.add_argument(
        run_flag("--dry-run"),
        action="store_true",
        help="log the mx benchmark commands that would be executed by this command (without actually executing them)",
    )
    dry_run_group.add_argument(
        run_flag("--dry-run-polybench"),
        action="store_true",
        help="log the mx polybench commands that would be executed by this command (without actually executing them)",
    )

    mode_group = parser.add_mutually_exclusive_group()
    mode_group.add_argument(
        run_flag("--jvm"),
        action="store_false",
        dest="is_native",
        default=False,
        help="run the benchmark on the JVM (default)",
    )
    mode_group.add_argument(
        run_flag("--native"),
        action="store_true",
        dest="is_native",
        help="run the benchmark with a native image of the Truffle interpreter",
    )
    parser.add_argument(
        run_flag("--pgo"), action="store_true", default=False, help="use PGO (only valid for native runs)"
    )
    parser.add_argument(
        run_flag("--g1gc"), action="store_true", default=False, help="use G1GC (only valid for native runs)"
    )
    benchmark_pattern_group = parser.add_mutually_exclusive_group()
    benchmark_pattern_group.add_argument(
        run_flag("--suite"),
        action="store_true",
        help='treat "benchmarks" as a suite specification (a suite name, optionally '
        "followed by a colon and a comma-separated list of tags, "
        'e.g., "sl:gate,daily")',
    )
    benchmark_pattern_group.add_argument(
        run_flag("--use-mx-benchmark-pattern"),
        action="store_false",
        dest="pattern_is_glob",
        default=True,
        help='treat "benchmarks" as an mx benchmark pattern instead of a glob '
        '(e.g., "r[interpreter/.*]"; see mx_benchmark.py for details)',
    )

    def benchmark_argument(arg):
        """Prevents users from accidentally passing a run flag in the benchmark arguments section."""
        if arg in run_flags:
            mx.abort(
                f'Flag "{arg}" is an "mx polybench" flag, but it was specified in the extra benchmark arguments section.\n'
                f'Move it before the benchmark pattern/suite specification to fix this error (e.g., "mx polybench {arg} ... benchmark_or_suite ...").'
            )
        return arg

    parser.add_argument(
        "benchmark_arguments",
        nargs=argparse.REMAINDER,
        type=benchmark_argument,
        help=PolybenchArgumentsSpecification.parser_help_message(),
    )

    return parser


parser = _create_parser()


@mx.command(_suite.name, "polybench", usage_msg=parser.format_usage().strip())
def polybench_command(args):
    """Run one or more benchmarks using polybench."""
    parsed_args = parser.parse_args(args)
    mx.logv(f"Running polybench with arguments {parsed_args}")
    if parsed_args.list:
        polybench_list()
    elif parsed_args.benchmarks:
        polybench_run(parsed_args, args)
    else:
        mx.abort('A benchmark pattern or --list must be specified. Use "mx polybench -h" for more info.')
