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
import contextlib
import hashlib
import json
import os
import re
import shutil
from typing import Callable, Dict, FrozenSet, Iterable, List, NamedTuple, Optional, Set, Tuple, Union

import mx
import mx_benchmark
import mx_pomdistribution
import mx_sdk_benchmark
import mx_truffle
from mx import AbstractDistribution
from mx_benchmark import DataPoint, DataPoints
from mx_jardistribution import JARDistribution

_polybench_language_registry: Dict[str, "PolybenchLanguageEntry"] = {}
_polybench_benchmark_suite_registry: Dict[str, "PolybenchBenchmarkSuiteEntry"] = {}
_validation_complete = False


def validate_polybench_registrations():
    """
    Validates the static correctness of language and suite specifications.
    If a language/suite specification is invalid (e.g., it refers to an
    unresolved distribution), it will be ignored and made unavailable.

    During registration, the `suppress_validation_warnings` argument can be
    used to suppress warning messages.

    Emitting suppressable warnings instead of aborting allows to register
    languages and suites that cannot always run (e.g., you can register a
    suite that is only available when a particular language is dynamically
    imported).
    """
    for language_name, language in list(_polybench_language_registry.items()):
        if invalid_reason := language.validate():
            if not language.suppress_validation_warnings:
                mx.warn(
                    f'Validation of polybench language "{language.language}" failed, so it will be ignored. Reason: {invalid_reason}'
                )
            _polybench_language_registry.pop(language_name)

    for suite_name, suite in list(_polybench_benchmark_suite_registry.items()):
        if invalid_reason := suite.validate():
            if not suite.suppress_validation_warnings:
                mx.warn(
                    f'Validation of polybench suite "{suite.name}" failed, so it will be ignored. Reason: {invalid_reason}'
                )
            _polybench_benchmark_suite_registry.pop(suite_name)

    global _validation_complete
    _validation_complete = True


def check_late_registration(component: str):
    global _validation_complete
    if _validation_complete:
        mx.abort(
            f"{component} was registered late. "
            "Registration must occur when the mx suite is loaded so that Polybench can perform necessary validations. "
            "The easiest way to achieve this is to place your registrations at the top level of the module."
        )


class PolybenchLanguageEntry(NamedTuple):
    """A language registered for polybench execution."""

    language: str
    distributions: List[str]
    native_distributions: List[str]
    suppress_validation_warnings: bool

    def all_distributions(self):
        return self.distributions + self.native_distributions

    def validate(self) -> Optional[str]:
        """Ensures this language can run. Returns an error string otherwise."""
        for distribution in self.all_distributions():
            resolved_distribution = mx.dependency(distribution, fatalIfMissing=False)
            if not resolved_distribution:
                return f'distribution "{distribution}" could not be resolved.'

        for distribution in self.native_distributions:
            resolved_distribution = mx.dependency(distribution, fatalIfMissing=False)
            if not resolved_distribution.isLayoutDistribution():
                return (
                    f'native distribution "{distribution}" must be a layout distribution. '
                    'Use the "layout" attribute to specify the directory structure of the distribution.'
                )

        return None


# A "polybench_run" function passed to the suite runner. It has the same input format as "mx polybench".
PolybenchRunFunction = Callable[[List[str]], None]

# A user-defined function that runs a suite of benchmarks. Takes a "polybench_run" function and a set of tags.
PolybenchBenchmarkSuiteRunner = Callable[[PolybenchRunFunction, Set[str]], None]


class PolybenchBenchmarkSuiteEntry(NamedTuple):
    """A benchmark suite registered for polybench execution."""

    mx_suite: mx.Suite
    name: str
    languages: List[str]
    benchmark_distribution: str
    benchmark_file_filter: Union[re.Pattern, Callable[[str], bool]]
    runner: Optional[PolybenchBenchmarkSuiteRunner]
    tags: Set[str]
    suppress_validation_warnings: bool

    def validate(self) -> Optional[str]:
        """Ensures this suite can run. Returns an error string otherwise."""
        for language in self.languages:
            if language not in _polybench_language_registry:
                return f'unknown benchmark language "{language}".'
        resolved_distribution = mx.dependency(self.benchmark_distribution, fatalIfMissing=False)
        if not resolved_distribution:
            return f'distribution "{self.benchmark_distribution}" could not be resolved.'
        if not resolved_distribution.isLayoutDistribution():
            return (
                f'distribution "{self.benchmark_distribution}" is not a layout distribution. '
                "All polybench benchmark distributions must be layout distributions. "
                'Use the "layout" attribute to specify the directory structure of the distribution.'
            )
        return None

    def get_benchmark_files(self) -> Dict[str, "ResolvedPolybenchBenchmark"]:
        """
        Returns a mapping from benchmark targets (e.g., foo.sl) to a ResolvedPolybenchBenchmark, which contains
        data about the actual benchmark (the absolute path to the benchmark, the defining suite, etc.).
        """
        if _check_dist(self.benchmark_distribution, require_built=False) is None:
            return {}
        resolved_distribution = mx.dependency(self.benchmark_distribution)
        root = resolved_distribution.get_output_root()
        result: Dict[str, ResolvedPolybenchBenchmark] = {}
        # Walk the distribution layout and register a benchmark for each file matching the filter.
        for path, _, files in os.walk(root):
            file: str
            for file in files:
                absolute_path = os.path.join(path, file)
                benchmark_file = os.path.relpath(absolute_path, root)
                if self._filter_benchmark_file(benchmark_file):
                    _add_benchmark(result, ResolvedPolybenchBenchmark(benchmark_file, absolute_path, self))
        if len(result) == 0:
            mx.warn(f"{self._description()} does not contain any benchmark files matching its benchmark file filter.")
        return result

    def _filter_benchmark_file(self, benchmark_file: str) -> bool:
        if isinstance(self.benchmark_file_filter, re.Pattern):
            return self.benchmark_file_filter.search(benchmark_file) is not None
        else:
            return self.benchmark_file_filter(benchmark_file)

    def _description(self):
        return f'Benchmark suite "{self.name}" (with distribution "{self.benchmark_distribution}", declared by suite "{self.mx_suite.name}")'


class ResolvedPolybenchBenchmark(NamedTuple):
    """Represents a concrete benchmark resolved by walking the layout of a benchmark distribution."""

    name: str
    absolute_path: str
    suite: "PolybenchBenchmarkSuiteEntry"


def _add_benchmark(benchmarks: Dict[str, ResolvedPolybenchBenchmark], resolved_benchmark: ResolvedPolybenchBenchmark):
    if resolved_benchmark.name in benchmarks:
        old_benchmark = benchmarks[resolved_benchmark.name]
        mx.abort(
            f"Benchmark file {resolved_benchmark.name} is supplied by multiple distributions: "
            f"{old_benchmark.suite.benchmark_distribution} at {old_benchmark.absolute_path} and "
            f"{resolved_benchmark.suite.benchmark_distribution} at {resolved_benchmark.absolute_path}."
        )
    benchmarks[resolved_benchmark.name] = resolved_benchmark


def _check_dist(dist_name: str, require_built: bool = True) -> Optional[str]:
    """
    Checks that the distribution exists, aborting otherwise. Returns the path to the distribution.
    If require_built is false, prints a warning and returns None instead.
    """
    dist = mx.distribution(dist_name)
    if not dist.exists():
        msg = f"The distribution {dist_name} does not exist. "
        msg += f"This might be solved by running: mx build --dependencies={dist_name}"
        mx.abort_or_warn(msg, require_built)
        return None

    if isinstance(dist, JARDistribution):
        return dist.path
    elif isinstance(dist, AbstractDistribution):
        return dist.get_output()
    elif isinstance(dist, mx_pomdistribution.POMDistribution):
        return dist.output_directory()
    else:
        mx.abort(f"Unsupported distribution kind {type(dist)}")


class PolybenchImageCacheEntry(NamedTuple):
    """
    Represents the parameters of a cached image build. When possible, PolybenchBenchmarkSuite will
    reuse an image across benchmark runs.
    """

    languages: FrozenSet[str]
    build_args: Tuple[str, ...]

    @classmethod
    def create(cls, languages: List[str], build_args: List[str]) -> "PolybenchImageCacheEntry":
        return cls(frozenset(languages), tuple(build_args))

    def executable_name(self):
        """The friendly name used to identify the image in the NI benchmarking infrastructure."""
        return "-".join(sorted(self.languages))

    def full_executable_name(self):
        """The name used to identify the image in the NI benchmarking infrastructure."""
        return f"{self.executable_name()}-{self._hash_build_args()}"

    def _hash_build_args(self) -> str:
        build_args_string = json.dumps(self.build_args)
        return hashlib.sha256(build_args_string.encode("utf-8")).hexdigest()[:8]


class PolybenchBenchmarkSuite(
    mx_benchmark.JavaBenchmarkSuite, mx_benchmark.TemporaryWorkdirMixin, mx_sdk_benchmark.NativeImageBenchmarkMixin
):
    POLYBENCH_MAIN = "org.graalvm.polybench.PolyBenchLauncher"
    # Maps some polybench metrics to standardized metric names
    POLYBENCH_METRIC_MAPPING = {
        "compilation time": "compile-time",
        "partial evaluation time": "pe-time",
        "allocated-bytes": "allocated-memory",
        "peak time": "time",
        "one-shot time": "one-shot",
        "instructions-metric": "instructions",
        "metaspace-memory-metric": "metaspace-memory",
        "application-memory-metric": "application-memory",
        "none": None,
    }

    def __init__(self):
        super(PolybenchBenchmarkSuite, self).__init__()
        self._image_cache: Set[PolybenchImageCacheEntry] = set()
        self._current_image: Optional[PolybenchImageCacheEntry] = None

    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def name(self):
        return "polybench"

    def version(self):
        return "0.2.0"

    def _resolve_benchmarks(self) -> Dict[str, ResolvedPolybenchBenchmark]:
        if not hasattr(self, "_benchmarks"):
            self._benchmarks = _resolve_all_benchmarks()
        return self._benchmarks

    def benchmarkList(self, bmSuiteArgs):
        return list(self._resolve_benchmarks().keys())

    def default_stages(self) -> List[str]:
        # Never run the agent stage (PGO stages will be filtered if not requested).
        return ["instrument-image", "instrument-run", "image", "run"]

    def extra_image_build_argument(self, benchmark_name, args):
        return super().extra_image_build_argument(benchmark_name, args) + [
            "--link-at-build-time",
            "-H:+AssertInitializationSpecifiedForAllClasses",
            "-H:+GuaranteeSubstrateTypesLinked",
            "-H:+VerifyRuntimeCompilationFrameStates",
        ]

    def checkSamplesInPgo(self):
        # Sampling does not support images that use runtime compilation.
        return False

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        # name used by NativeImageBenchmarkMixin
        self.benchmark_name = benchmarks[0]

        working_directory = self.workingDirectory(benchmarks, bmSuiteArgs) or os.getcwd()
        resolved_benchmark = self._resolve_current_benchmark(benchmarks)

        mx.log(f'Running polybench benchmark "{resolved_benchmark.name}"".')
        mx.logv(f"CWD: {working_directory}")
        mx.logv(f"Languages included on the classpath: {resolved_benchmark.suite.languages}")

        env_vars = PolybenchBenchmarkSuite._prepare_distributions(working_directory, resolved_benchmark)
        with _extend_env(env_vars):
            if self._can_use_image_cache(bmSuiteArgs):
                return self._run_with_image_cache(resolved_benchmark, benchmarks, bmSuiteArgs)
            else:
                return self.intercept_run(super(), benchmarks, bmSuiteArgs)

    def _resolve_current_benchmark(self, benchmarks) -> ResolvedPolybenchBenchmark:
        if benchmarks is None or len(benchmarks) != 1:
            mx.abort(f"Must specify one benchmark at a time (given: {benchmarks})")
        return self._resolve_benchmarks()[benchmarks[0]]

    @staticmethod
    def _prepare_distributions(
        working_directory: str, resolved_benchmark: ResolvedPolybenchBenchmark
    ) -> Dict[str, str]:
        """
        Checks that required distributions exist, copying native distributions to the CWD. Returns a dict of additional
        variables that should be added to the environment (e.g., library paths).
        """
        java_distributions = _get_java_distributions(resolved_benchmark)
        native_distributions, env_vars = _get_native_distributions(resolved_benchmark)
        for dist in java_distributions | native_distributions:
            _check_dist(dist)

        # Before running, copy library distributions to the working directory so that libraries can be resolved.
        for native_distribution in native_distributions:
            native_distribution_path = mx.dependency(native_distribution).get_output_root()
            path: str
            for path, _, files in os.walk(native_distribution_path):
                for file in files:
                    absolute_path = os.path.join(path, file)
                    relative_path = os.path.relpath(absolute_path, native_distribution_path)
                    destination_file_path = os.path.join(working_directory, relative_path)
                    os.makedirs(os.path.dirname(destination_file_path), exist_ok=True)
                    mx.logv(f"Copied native distribution library {absolute_path} to {destination_file_path}")
                    shutil.copy(absolute_path, os.path.dirname(destination_file_path))

        return env_vars

    def _can_use_image_cache(self, bm_suite_args) -> bool:
        return self.is_native_mode(bm_suite_args) and "pgo" not in self.jvmConfig(bm_suite_args)

    def _run_with_image_cache(
        self, resolved_benchmark: ResolvedPolybenchBenchmark, benchmarks: List[str], bm_suite_args: List[str]
    ) -> DataPoints:
        with self._set_image_context(resolved_benchmark, bm_suite_args):
            image_build_datapoints = self._build_cached_image(benchmarks, bm_suite_args)
            image_run_datapoints = self._run_cached_image(benchmarks, bm_suite_args)
            return list(image_build_datapoints) + list(image_run_datapoints)

    @contextlib.contextmanager
    def _set_image_context(self, resolved_benchmark: ResolvedPolybenchBenchmark, bm_suite_args: List[str]):
        """
        Defines a context for the "current" image. This field determines the executable name, which
        is used by NI benchmarking infra to resolve the name/location of the built image.
        """
        entry = PolybenchImageCacheEntry.create(resolved_benchmark.suite.languages, self.vmArgs(bm_suite_args))
        assert (
            not self._current_image
        ), f"Tried to set current image to {entry.executable_name()}, but there is already a current image ({self._current_image.executable_name()})."
        self._current_image = entry
        yield
        self._current_image = None

    def executable_name(self) -> Optional[str]:
        """Overrides the image name used to build/run the image."""
        if self._current_image:
            return self._current_image.full_executable_name()
        return None

    def _build_cached_image(self, benchmarks: List[str], bm_suite_args: List[str]) -> DataPoints:
        if self._current_image in self._image_cache:
            # already built
            return []

        image_build_datapoints = self.intercept_run(
            super(), benchmarks, self._extend_vm_args(bm_suite_args, ["-Dnative-image.benchmark.stages=image"])
        )
        self._image_cache.add(self._current_image)
        for datapoint in image_build_datapoints:
            # associate any image build datapoints with the name of the image (rather than the benchmark)
            datapoint["benchmark"] = self._current_image.executable_name()
        return image_build_datapoints

    def _run_cached_image(self, benchmarks: List[str], bm_suite_args: List[str]) -> DataPoints:
        return self.intercept_run(
            super(), benchmarks, self._extend_vm_args(bm_suite_args, ["-Dnative-image.benchmark.stages=run"])
        )

    def _extend_vm_args(self, bm_suite_args: List[str], new_vm_args: List[str]) -> List[str]:
        vmArgs, runArgs = self.vmAndRunArgs(bm_suite_args)
        return vmArgs + new_vm_args + ["--"] + runArgs

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        resolved_benchmark = self._resolve_current_benchmark(benchmarks)

        java_distributions = _get_java_distributions(resolved_benchmark)
        vm_args = mx.get_runtime_jvm_args(names=java_distributions) + self.vmArgs(bmSuiteArgs)
        mx_truffle.enable_truffle_native_access(vm_args)
        polybench_args = ["--path=" + resolved_benchmark.absolute_path] + self.runArgs(bmSuiteArgs)
        return vm_args + [PolybenchBenchmarkSuite.POLYBENCH_MAIN] + polybench_args

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        """Delegates to the super implementation then injects engine.config into every datapoint."""
        ret_code, out, dims = super().runAndReturnStdOut(benchmarks, bmSuiteArgs)
        dims["engine.config"] = self._get_mode(bmSuiteArgs)
        return ret_code, out, dims

    def rules(self, output, benchmarks, bmSuiteArgs):
        metric_name = PolybenchBenchmarkSuite._get_metric_name(output)
        if metric_name is None:
            return []
        rules = []
        benchmark_name = benchmarks[0]
        if metric_name == "time":
            # For metric "time", two metrics are reported:
            # - "warmup" (per-iteration data for "warmup" and "run" iterations)
            # - "time" (per-iteration data for only the "run" iterations)
            rules += [
                mx_benchmark.StdOutRule(
                    r"\[.*\] iteration ([0-9]*): (?P<value>.*) (?P<unit>.*)",
                    {
                        "benchmark": benchmark_name,
                        "metric.better": "lower",
                        "metric.name": "warmup",
                        "metric.unit": ("<unit>", str),
                        "metric.value": ("<value>", float),
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.iteration": ("$iteration", int),
                    },
                ),
                ExcludeWarmupRule(
                    r"\[.*\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)",
                    {
                        "benchmark": benchmark_name,
                        "metric.better": "lower",
                        "metric.name": "time",
                        "metric.unit": ("<unit>", str),
                        "metric.value": ("<value>", float),
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.iteration": ("<iteration>", int),
                    },
                    startPattern=r"::: Running :::",
                ),
            ]
        elif metric_name in ("allocated-memory", "metaspace-memory", "application-memory", "instructions"):
            rules += [
                ExcludeWarmupRule(
                    r"\[.*\] iteration (?P<iteration>[0-9]*): (?P<value>.*) (?P<unit>.*)",
                    {
                        "benchmark": benchmark_name,
                        "metric.better": "lower",
                        "metric.name": metric_name,
                        "metric.unit": ("<unit>", str),
                        "metric.value": ("<value>", float),
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.iteration": ("<iteration>", int),
                    },
                    startPattern=r"::: Running :::",
                )
            ]
        elif metric_name is not None:
            rules += [
                mx_benchmark.StdOutRule(
                    r"\[.*\] after run: (?P<value>.*) (?P<unit>.*)",
                    {
                        "benchmark": benchmark_name,
                        "metric.better": "lower",
                        "metric.name": metric_name,
                        "metric.unit": ("<unit>", str),
                        "metric.value": ("<value>", float),
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.iteration": 0,
                    },
                )
            ]
        # Always report the following metrics:
        # - "context-init-time" (the time to initialize the context)
        # - "context-eval-time" (the time to evaluate the source)
        rules += [
            mx_benchmark.StdOutRule(
                r"### init time \((?P<unit>.*)\): (?P<delta>.*)",
                {
                    "benchmark": benchmark_name,
                    "metric.name": "context-init-time",
                    "metric.value": ("<delta>", float),
                    "metric.unit": ("<unit>", str),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0,
                },
            ),
            mx_benchmark.StdOutRule(
                r"### load time \((?P<unit>.*)\): (?P<delta>.*)",
                {
                    "benchmark": benchmark_name,
                    "metric.name": "context-eval-time",
                    "metric.value": ("<delta>", float),
                    "metric.unit": ("<unit>", str),
                    "metric.type": "numeric",
                    "metric.score-function": "id",
                    "metric.better": "lower",
                    "metric.iteration": 0,
                },
            ),
        ]
        return rules

    @staticmethod
    def _get_metric_name(bench_output) -> Optional[str]:
        match = re.search(r"metric:\s*(?P<metric_name>(\w|-| )+) \(", bench_output)
        if not match:
            return None
        metric_name = match.group("metric_name")

        if metric_name in PolybenchBenchmarkSuite.POLYBENCH_METRIC_MAPPING:
            return PolybenchBenchmarkSuite.POLYBENCH_METRIC_MAPPING[metric_name]
        else:
            mx.warn(
                f"Polybench metric {metric_name} could not be mapped to a standardized name. Consider updating POLYBENCH_METRIC_MAPPING."
            )
            return metric_name

    def _get_mode(self, bmSuiteArgs):
        """Determines the "mode" to report in benchmark data points."""
        if "--engine.Compilation=false" in self.runArgs(
            bmSuiteArgs
        ) or "-Dpolyglot.engine.Compilation=false" in self.vmArgs(bmSuiteArgs):
            return "interpreter"
        return "standard"


class ExcludeWarmupRule(mx_benchmark.StdOutRule):
    """Rule that behaves as the StdOutRule, but skips input until a certain pattern."""

    def __init__(self, *args, **kwargs):
        self.startPattern = re.compile(kwargs.pop("startPattern"))
        super(ExcludeWarmupRule, self).__init__(*args, **kwargs)

    def parse(self, text) -> Iterable[DataPoint]:
        m = self.startPattern.search(text)
        if m:
            return super(ExcludeWarmupRule, self).parse(text[m.end() + 1 :])
        else:
            return []


@contextlib.contextmanager
def _extend_env(env_vars: Dict[str, str]):
    old_env = dict(os.environ)
    try:
        for k, v in env_vars.items():
            mx.logv(f"Setting environment variable {k}={v}")
            os.environ[k] = v
        yield
    finally:
        os.environ.clear()
        os.environ.update(old_env)


def is_enterprise() -> bool:
    """Returns whether enterprise extensions are available."""
    return mx_truffle._get_enterprise_truffle() is not None


def _resolve_polybench_java_distributions() -> Set[str]:
    result = {"POLYBENCH", "POLYBENCH_INSTRUMENTS"}
    if is_enterprise():
        import mx_truffle_enterprise

        result |= mx_truffle_enterprise._resolve_polybench_ee_java_distributions()
    return result


def _get_java_distributions(benchmark: ResolvedPolybenchBenchmark) -> Set[str]:
    result = _resolve_polybench_java_distributions() | set(mx_truffle.resolve_truffle_dist_names())
    for language in benchmark.suite.languages:
        result |= set(_polybench_language_registry[language].distributions)
    return result


def _resolve_polybench_native_distributions() -> Tuple[Set[str], Dict[str, str]]:
    if is_enterprise():
        import mx_truffle_enterprise

        return mx_truffle_enterprise._resolve_polybench_ee_native_distributions()
    return set(), {}


def _get_native_distributions(benchmark: ResolvedPolybenchBenchmark) -> Tuple[Set[str], Dict[str, str]]:
    """Returns the native distributions and environment variables required to run this benchmark."""
    dists, env_vars = _resolve_polybench_native_distributions()
    for language in benchmark.suite.languages:
        dists |= set(_polybench_language_registry[language].native_distributions)
    return dists, env_vars


def _resolve_all_benchmarks() -> Dict[str, ResolvedPolybenchBenchmark]:
    benchmarks = {}
    for suite in _polybench_benchmark_suite_registry.values():
        resolved = []
        for resolved_benchmark in suite.get_benchmark_files().values():
            _add_benchmark(benchmarks, resolved_benchmark)
            resolved.append(resolved_benchmark)
        formatted = [f"{benchmark.name} -> {benchmark.absolute_path}" for benchmark in resolved]
        mx.logv(f"Resolved {len(resolved)} Polybench benchmark file(s) for suite {suite.name}: {formatted}")
    return benchmarks


def _get_all_suites() -> Dict[str, PolybenchBenchmarkSuiteEntry]:
    return _polybench_benchmark_suite_registry.copy()


def _qualify_distribution(mx_suite: mx.Suite, dist: str) -> str:
    return dist if ":" in dist else f"{mx_suite.name}:{dist}"


def _qualify_distributions(mx_suite: mx.Suite, dists: List[str]) -> List[str]:
    return [_qualify_distribution(mx_suite, dist) for dist in dists]


def register_polybench_language(
    mx_suite: mx.Suite,
    language: str,
    distributions: List[str],
    native_distributions: Optional[List[str]] = None,
    suppress_validation_warnings: bool = False,
):
    """
    Registers a language for execution with polybench.

    It is recommended for an mx suite to register its language(s) at the top level of its suite definition; then,
    whenever the suite is loaded (e.g., as the primary suite, an import, or dynamically included with `--dy`), the
    language will be accessible within polybench.

    :param mx_suite: The mx suite that declares the benchmark suite.
    :param language: The language name.
    :param distributions: The list of distributions required to run the language.
    :param native_distributions: An optional list of native distributions required to run the language. These
    distributions should be layout distributions with native libraries; their contents will be copied into the temporary
    working directory (i.e., the native libraries can be resolved as relative paths).
    :param suppress_validation_warnings: Whether to suppress warning messages when the language specification does not validate.
    """
    check_late_registration(f"Polybench language {language}")
    if language in _polybench_language_registry:
        mx.abort(f"Language {language} was already registered.")
    entry = PolybenchLanguageEntry(
        language=language,
        distributions=_qualify_distributions(mx_suite, distributions),
        native_distributions=_qualify_distributions(mx_suite, native_distributions or []),
        suppress_validation_warnings=suppress_validation_warnings,
    )
    mx.logv(f"Registered polybench language {language}: {entry}")
    _polybench_language_registry[language] = entry


def register_polybench_benchmark_suite(
    mx_suite: mx.Suite,
    name: str,
    languages: List[str],
    benchmark_distribution: str,
    benchmark_file_filter: Union[str, Callable[[str], bool]],
    runner: Optional[PolybenchBenchmarkSuiteRunner] = None,
    tags: Optional[Set[str]] = None,
    suppress_validation_warnings: bool = False,
):
    """
    Registers a suite of polybench benchmarks. A polybench suite declares a distribution of benchmark files and the
    language(s) required to run them. The files in the suite can be run directly; the suite can also declare a suite
    runner that defines batch execution of its benchmarks.

    It is recommended for an mx suite to register its benchmark suite(s) at the top level of its suite definition; then,
    whenever the suite is loaded (e.g., as the primary suite, an import, or dynamically included with `--dy`), the
    benchmarks will be accessible within polybench.

    :param mx_suite: The mx suite that declares the benchmark suite.
    :param name: The name of the suite.
    :param languages: The languages required to run the benchmarks. These languages will be included on the
    classpath when executing the benchmarks.
    :param benchmark_distribution: A distribution that produces benchmark sources; it must be a layout distribution.
    :param benchmark_file_filter: A regular expression or predicate that filters file paths in the distribution that
    should be treated as benchmark entrypoints.
    :param runner: An optional suite runner, which can be used to perform batch execution of benchmarks (e.g., for CI
    jobs). A runner is a function that takes a callable "polybench_run" argument and a set of string tags. It uses the
    tags to determine which benchmarks to run, and runs them using the "polybench_run" function (which has the same
    input format as the "mx polybench" command).
    :param tags: The set of tags supported by the runner.
    :param suppress_validation_warnings: Whether to suppress warning messages when the suite specification does not validate.
    """
    check_late_registration(f"Polybench benchmark suite {name}")
    if isinstance(benchmark_file_filter, str):
        compiled_file_filter = re.compile(benchmark_file_filter)
    elif callable(benchmark_file_filter):
        compiled_file_filter = benchmark_file_filter
    else:
        raise ValueError(
            f"Error registering polybench suite {name}: value {benchmark_file_filter} supplied for "
            "benchmark_file_filter must be a regex string or a callable filter function."
        )
    entry = PolybenchBenchmarkSuiteEntry(
        mx_suite=mx_suite,
        name=name,
        languages=languages,
        benchmark_distribution=_qualify_distribution(mx_suite, benchmark_distribution),
        benchmark_file_filter=compiled_file_filter,
        runner=runner,
        tags=tags if tags is not None else set(),
        suppress_validation_warnings=suppress_validation_warnings,
    )
    if name in _polybench_benchmark_suite_registry:
        mx.abort(
            f"Polybench suite {name} was already registered.\n"
            f"Existing suite: {_polybench_benchmark_suite_registry[name]}\n"
            f"New suite: {entry}"
        )
    mx.logv(f"Registered polybench benchmark suite: {entry}")
    _polybench_benchmark_suite_registry[name] = entry
