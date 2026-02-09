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
import dataclasses
import hashlib
import json
import os
import re
import shutil
from argparse import ArgumentParser, Namespace
from enum import Enum
from pathlib import Path
from typing import Callable, Dict, FrozenSet, Iterable, List, NamedTuple, Optional, Set, Tuple, Union, Any, Generator

import mx
import mx_benchmark
import mx_pomdistribution
import mx_sdk_benchmark
import mx_truffle
from mx import AbstractDistribution
from mx_sdk_benchmark import parse_prefixed_args
from mx_util import Stage, StageName
from mx_benchmark import (
    DataPoint,
    DataPoints,
    ParserEntry,
    add_parser,
    get_parser,
    DataPointsPostProcessor,
    ForkInfo,
    BenchmarkExecutionConfiguration,
    BenchmarkDispatcher,
    BenchmarkDispatcherState,
    bm_exec_context,
    BoxContextValue,
    ConstantContextValueManager,
)
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
    additional_polybench_args: List[str]

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


@dataclasses.dataclass(frozen=True)
class OutlierExclusionConfig:
    """Record class that contains the outlier exclusion lower and upper percentiles."""

    lower_percentile: float
    upper_percentile: float

    @staticmethod
    def from_string(s: str) -> "OutlierExclusionConfig":
        """Constructs an `OutlierExclusionConfig` object from a "<lower_percentile>-<upper_percentile>" string."""
        parts = s.strip().split("-")
        if len(parts) != 2:
            raise ValueError(f"Invalid outlier exclusion value: '{s}'")
        return OutlierExclusionConfig(float(parts[0]), float(parts[1]))


class SuiteStableRunConfig:
    """Interface for a PolyBench Stable-Run Configuration file."""

    def __init__(self, file_path: Path):
        with open(file_path) as f:
            self._dict: dict = json.load(f)

    def get_benchmark(self, bench_name: str) -> "BenchmarkStableRunConfig":
        """Returns an interface for the benchmark entry of the configuration file."""
        return BenchmarkStableRunConfig(self._dict[bench_name])

    def contains(self, bench_name: str) -> bool:
        """Returns whether an entry in the configuration file exists for a benchmark."""
        return bench_name in self._dict

    def benchmarks(self) -> List[str]:
        """Returns all the benchmarks for which an entry is defined in the configuration file."""
        return self._dict.keys()


class StableRunPolicy(Enum):
    INDIVIDUAL_BUILDS = "outlier-elimination-individual-builds"
    ALL_BUILDS = "outlier-elimination-all-builds"


class BenchmarkStableRunConfig:
    """Interface for a benchmark entry of a PolyBench Stable-Run Configuration file."""

    def __init__(self, d: dict):
        self._dict: dict = d

    @property
    def policy(self) -> StableRunPolicy:
        """
        The policy of the benchmark configuration entry.

        Different policies warrant specific handling in the computation of the stabilized metric.
        Different policies may also require different entry formats.
        """
        # We should move towards deprecating the INDIVIDUAL_BUILDS policy (GR-71845)
        return StableRunPolicy(self._dict.get("policy", StableRunPolicy.INDIVIDUAL_BUILDS))

    @property
    def builds(self):
        """The number of image builds to execute (in the case of Native Image benchmarks)."""
        if self.policy == StableRunPolicy.INDIVIDUAL_BUILDS:
            return self._dict["builds"]["count"]
        return self._parse_builds_x_forks()[0]

    @property
    def forks(self):
        """The number of forks to execute (per image build, in the case of Native Image benchmarks)."""
        if self.policy == StableRunPolicy.INDIVIDUAL_BUILDS:
            return self._dict["run-forks"]["count"]
        return self._parse_builds_x_forks()[1]

    @property
    def outlier_exclusion(self) -> OutlierExclusionConfig:
        """The outlier exclusion configuration to be used on fork data."""
        if self.policy != StableRunPolicy.ALL_BUILDS:
            raise ValueError(f"This property is not available for the {self.policy} policy!")
        return OutlierExclusionConfig.from_string(self._dict.get("focus"))

    @property
    def build_outlier_exclusion(self) -> OutlierExclusionConfig:
        """The outlier exclusion configuration to be used on image build data."""
        if self.policy == StableRunPolicy.ALL_BUILDS:
            return self.outlier_exclusion
        config = self._dict["builds"]
        return OutlierExclusionConfig(config["lower-percentile"], config["upper-percentile"])

    @property
    def fork_outlier_exclusion(self) -> OutlierExclusionConfig:
        """The outlier exclusion configuration to be used on data belonging to forks of one image build."""
        if self.policy == StableRunPolicy.ALL_BUILDS:
            return self.outlier_exclusion
        config = self._dict["run-forks"]
        return OutlierExclusionConfig(config["lower-percentile"], config["upper-percentile"])

    def _parse_builds_x_forks(self) -> (int, int):
        """Parses a "<builds>x<forks>" string into a tuple containing the build and fork numbers."""
        if self.policy != StableRunPolicy.ALL_BUILDS:
            raise ValueError(f"This method is not available for the {self.policy} policy!")
        forks = self._dict["forks"]
        parts = forks.strip().split("x")
        if len(parts) != 2:
            raise ValueError(f"Invalid forks value: '{forks}'")
        return int(parts[0]), int(parts[1])


class StabilizingPolybenchBenchmarkDispatcher(mx_benchmark.DefaultBenchmarkDispatcher):
    """
    Custom dispatching class for non-native-image PolybenchBenchmarkSuite stable runs that facilitates scheduling based
    on a `--stable-run-config` configuration file:
    * Schedules the appropriate number of forks for each specified benchmark according to their configuration.

    The `--stable-run-config` configuration file should be a JSON object, where each key is a benchmark name.
    Each entry must include a "run-forks" dictionary with a "count" property that specifies the number of forks
    to schedule for that benchmark.
    Additional properties may be present, but they are not relevant to this dispatcher class.

    Example:

    If the `--stable-run-config` configuration file specifies the following configuration (fields irrelevant
    for scheduling have been omitted):
    ```
    {
      "interpreter/sieve.py": {
        "run-forks": { "count": 1 }
      },
      "interpreter/fibonacci.py": {
        "run-forks": { "count": 3 }
      },
      "interpreter/richards.py": {
        "run-forks": { "count": 2 }
      }
    }
    ```

    This dispatcher will produce the following schedule (can be obtained by appending the `--dry-stable-run` option
    to your `mx benchmark` command):
    ```
    * Bench batch #1
      [#1] Fork #0: interpreter/richards.py
      [#2] Fork #0: interpreter/fibonacci.py
      [#3] Fork #0: interpreter/sieve.py
    * Bench batch #2
      [#4] Fork #1: interpreter/richards.py
      [#5] Fork #1: interpreter/fibonacci.py
    * Bench batch #3
      [#6] Fork #2: interpreter/fibonacci.py
    ```

    * There are 3 batches, as that is how many the benchmark that requires the most run-forks (fibonacci.py) requires.
    * The first batch includes all three benchmarks.
    * Starting from the second batch, 'sieve.py' is excluded as it only requires 1 run-fork.
    * In the third batch, 'richards.py' is excluded as it only requires 2 run-forks.
    * For example, the log line "[#5] Fork #1: interpreter/fibonacci.py" indicates that:
      * This is the 5th dispatch from the dispatcher - the 5th invocation of the `BenchmarkSuite.run` method.
      * This dispatch will only execute the 'interpreter/fibonacci.py' benchmark.
      * This is the 2nd fork (`metric.fork-number = 1`, but indexing starts from 0) of the 'interpreter/fibonacci.py'
        benchmark.
    """

    def __init__(self, state: BenchmarkDispatcherState, stable_run_config: str):
        super().__init__(state)
        self._stable_run_config_path: Path = Path(stable_run_config).absolute()
        if not self._stable_run_config_path.exists():
            msg = f"Cannot initialize {self.__class__.__name__} instance with non-existing configuration file '{self._stable_run_config_path}'!"
            raise ValueError(msg)
        self._stable_run_config: SuiteStableRunConfig = SuiteStableRunConfig(self._stable_run_config_path)

    def validated_env_dispatch(self) -> Generator[BenchmarkExecutionConfiguration, Any, None]:
        """
        Verifies the configuration, runs a sub-generator dry-run, and then finally yields from sub-generator.

        1. Starts by parsing the benchmark list and verifying that each benchmark has an entry
           in the stable run configuration file.
        2. Executes a dry-run of the sub-generator to:
           * compute the number of dispatches.
           * log the dispatching schedule to stdout.
        3. Yields from the sub-generator, which dispatches according to the schedule.
        """
        if not isinstance(self.state.suite, PolybenchBenchmarkSuite):
            msg = f"Expected a PolybenchBenchmarkSuite instance, instead got an instance of {self.state.suite.__class__.__name__}!"
            raise ValueError(msg)
        self._verify_no_conflicting_args_are_set()
        benchmarks = self._parse_benchmark_list()
        if len(benchmarks) == 0:
            raise ValueError(f"No benchmarks selected!")
        self._verify_stable_run_config(benchmarks)
        # Dry-run of the sub-generator to get the number of dispatches (yields) and present the schedule to stdout
        mx.log(f"{self.__class__.__name__} will dispatch the following schedule:")
        dispatch_count = len(list(self.dispatch_with_fork_context(benchmarks, 0, True)))
        if self.state.suite.polybench_bench_suite_args(self.state.bm_suite_args).dry_stable_run:
            return
        # Delegate to sub-generator
        mx.log(f"{self.__class__.__name__} is starting dispatch...")
        yield from self.dispatch_with_fork_context(benchmarks, dispatch_count, False)

    def dispatch_with_fork_context(
        self, benchmarks: List[str], total_dispatch_count: int, dry_run: bool
    ) -> Generator[BenchmarkExecutionConfiguration, Any, None]:
        """Resets the fork number overrides and then yields according to the schedule."""
        fork_number_dict = self._init_fork_number_dict(benchmarks)
        with ConstantContextValueManager(PolybenchBenchmarkSuite.FORK_OVERRIDE_MAP, fork_number_dict):
            yield from self.dispatch_and_log(benchmarks, total_dispatch_count, fork_number_dict, dry_run)

    def dispatch_and_log(
        self, benchmarks: List[str], total_dispatch_count: int, fork_number_dict: Dict[str, int], dry_run: bool
    ) -> Generator[BenchmarkExecutionConfiguration, Any, None]:
        """
        Yields according to the schedule:
        * First, it iterates over the benchmark batches, determined by the highest requested run-fork count.
        * Second, it iterates over each benchmark which requires to be run in the current benchmark batch.
        """
        dispatch_counter = 0
        number_of_batches = max([self._stable_run_config.get_benchmark(bench).forks for bench in benchmarks])
        for batch_index in range(number_of_batches):
            if dry_run:
                mx.log(f" * Bench batch #{batch_index + 1}")
            benchmarks_for_batch = self._get_benchmarks_for_batch(benchmarks, batch_index)
            for benchmark in benchmarks_for_batch:
                if dry_run:
                    mx.log(f"   [#{dispatch_counter + 1}] Fork #{fork_number_dict[benchmark]}: {benchmark}")
                else:
                    mx.log(f"Execution of dispatch {dispatch_counter + 1}/{total_dispatch_count} running {benchmark}")
                mx_benchmark_args = self.state.mx_benchmark_args
                bm_suite_args = self.state.bm_suite_args
                last_dispatch = dispatch_counter + 1 == total_dispatch_count
                with ConstantContextValueManager("last_dispatch", last_dispatch):
                    fork_info = ForkInfo(
                        fork_number_dict[benchmark], self._stable_run_config.get_benchmark(benchmark).forks
                    )
                    yield BenchmarkExecutionConfiguration([benchmark], mx_benchmark_args, bm_suite_args, fork_info)
                dispatch_counter += 1
                fork_number_dict[benchmark] += 1

    def _get_benchmarks_for_batch(self, benchmarks: List[str], batch_index: int):
        return [bench for bench in benchmarks if self._stable_run_config.get_benchmark(bench).forks > batch_index]

    def _verify_no_conflicting_args_are_set(self):
        mx_benchmark_args_dict = vars(self.state.mx_benchmark_args)
        if mx_benchmark_args_dict.get("fork_count_file") is not None:
            msg = f"Setting the 'mx benchmark' option 'fork_count_file' is not supported when using {self.__class__.__name__} as a dispatcher!"
            raise ValueError(msg)
        if mx_benchmark_args_dict.get("default_fork_count", 1) != 1:
            msg = f"Setting the 'mx benchmark' option 'default_fork_count' is not supported when using {self.__class__.__name__} as a dispatcher!"
            raise ValueError(msg)

    def _parse_benchmark_list(self) -> List[str]:
        if any([sublist is None for sublist in self.state.bench_names_list]):
            raise ValueError(f"The {self.__class__.__name__} dispatcher cannot dispatch without specified benchmarks!")
        benchmarks = [bench for sublist in self.state.bench_names_list for bench in sublist]
        seen = set()
        unique_list = [bench for bench in benchmarks if not (bench in seen or seen.add(bench))]
        return [bench for bench in unique_list if not self.skip_platform_unsupported_benchmark(bench)]

    def _verify_stable_run_config(self, benchmarks: List[str]):
        levels = self._get_required_config_levels()
        fields = ["count"]
        v2_fields = ["forks", "focus"]
        for bench in benchmarks:
            if not self._stable_run_config.contains(bench):
                msg = f"PolyBench stable run configuration file at '{self._stable_run_config_path}' is missing an entry for the '{bench}' benchmark!"
                raise ValueError(msg)
            bench_config = self._stable_run_config.get_benchmark(bench)
            if bench_config.policy == StableRunPolicy.ALL_BUILDS:
                for field in v2_fields:
                    if field not in bench_config._dict:
                        msg = f"PolyBench stable run configuration file at '{self._stable_run_config_path}' is missing the '{field}' key in the '{bench}' object!"
                        raise ValueError(msg)
                continue
            # To be removed once all INDIVIDUAL_BUILDS policy benchmarks are updated
            for level in levels:
                if level not in bench_config._dict:
                    msg = f"PolyBench stable run configuration file at '{self._stable_run_config_path}' is missing the '{level}' key in the '{bench}' object!"
                    raise ValueError(msg)
                level_config = bench_config._dict[level]
                for field in fields:
                    if field not in level_config:
                        msg = f"PolyBench stable run configuration file at '{self._stable_run_config_path}' is missing the '{field}' key in the '{bench}.{level}' object!"
                        raise ValueError(msg)

    def _get_required_config_levels(self) -> List[str]:
        return ["run-forks"]

    def _init_fork_number_dict(self, benchmarks) -> Dict[str, int]:
        return {benchmark: 0 for benchmark in benchmarks}


class StabilizingPolybenchNativeImageBenchmarkDispatcher(StabilizingPolybenchBenchmarkDispatcher):
    """
    Custom dispatching class for native-image PolybenchBenchmarkSuite stable runs that facilitates scheduling based
    on a `--stable-run-config` configuration file:
    * Schedules the appropriate number of forks for each specified benchmark according to their configuration.
    * Reduces the number of language-launcher image builds, by reusing the same launcher across multiple benchmarks.
    * Only runs agent and instrumentation stages once, if the VM configuration requires these stages.

    The `--stable-run-config` configuration file should be a JSON object, where each key is a benchmark name.
    Each entry must include both "builds" and a "run-forks" dictionary, each with a "count" property that
    specifies the number of builds or forks (respectively) to schedule for that benchmark.
    Additional properties may be present, but they are not relevant to this dispatcher class.

    Example:

    If the `--stable-run-config` configuration file specifies the following configuration (fields irrelevant
    for scheduling have been omitted):
    ```
    {
      "interpreter/sieve.py": {
        "builds": { "count": 1 },
        "run-forks": { "count": 1 }
      },
      "interpreter/fibonacci.py": {
        "builds": { "count": 2 },
        "run-forks": { "count": 3 }
      },
      "interpreter/richards.py": {
        "builds": { "count": 3 },
        "run-forks": { "count": 2 }
      }
    }
    ```

    This dispatcher will produce the following schedule (can be obtained by appending the `--dry-stable-run` option
    to your `mx benchmark` command):
    ```
    * Build #1
      * Preparation batch (batch #1)
        [#1] Fork #0: 'agent' stage on interpreter/sieve.py
        [#2] Fork #0: 'agent' stage on interpreter/richards.py
        [#3] Fork #0: 'agent' stage on interpreter/fibonacci.py
      * Preparation batch (batch #2)
        [#4] Fork [interpreter/sieve.py#0][interpreter/richards.py#0][interpreter/fibonacci.py#0]: 'instrument-image' stage
      * Preparation batch (batch #3)
        [#5] Fork #0: 'instrument-run' stage on interpreter/sieve.py
        [#6] Fork #0: 'instrument-run' stage on interpreter/richards.py
        [#7] Fork #0: 'instrument-run' stage on interpreter/fibonacci.py
      * Preparation batch (batch #4)
        [#8] Fork [interpreter/sieve.py#0][interpreter/richards.py#0][interpreter/fibonacci.py#0]: 'image' stage
      * Bench batch #1 (batch #5)
        [#9] Fork #0: 'run' stage on interpreter/sieve.py
        [#10] Fork #0: 'run' stage on interpreter/richards.py
        [#11] Fork #0: 'run' stage on interpreter/fibonacci.py
      * Bench batch #2 (batch #6)
        [#12] Fork #1: 'run' stage on interpreter/richards.py
        [#13] Fork #1: 'run' stage on interpreter/fibonacci.py
      * Bench batch #3 (batch #7)
        [#14] Fork #2: 'run' stage on interpreter/fibonacci.py
    * Build #2
      * Preparation batch (batch #1)
        [#15] Fork [interpreter/richards.py#2][interpreter/fibonacci.py#3]: 'image' stage
      * Bench batch #1 (batch #2)
        [#16] Fork #2: 'run' stage on interpreter/richards.py
        [#17] Fork #3: 'run' stage on interpreter/fibonacci.py
      * Bench batch #2 (batch #3)
        [#18] Fork #3: 'run' stage on interpreter/richards.py
        [#19] Fork #4: 'run' stage on interpreter/fibonacci.py
      * Bench batch #3 (batch #4)
        [#20] Fork #5: 'run' stage on interpreter/fibonacci.py
    * Build #3
      * Preparation batch (batch #1)
        [#21] Fork [interpreter/richards.py#4]: 'image' stage
      * Bench batch #1 (batch #2)
        [#22] Fork #4: 'run' stage on interpreter/richards.py
      * Bench batch #2 (batch #3)
        [#23] Fork #5: 'run' stage on interpreter/richards.py
    ```

    * Three builds will be scheduled as the benchmark with the most builds requested (richards.py) requires three.
      * Only the first build will include AGENT, INSTRUMENT-IMAGE, and INSTRUMENT-RUN stages in the preparation batch.
      * Every subsequent build will only include an IMAGE stage in the preparation batch.
    * The first build will include 3 batches, as all of them require at least one build.
      * The first bench batch will include all 3 benchmarks, as all of them require at least one run-fork.
      * The second bench batch will include richards.py and fibonacci.py, excluding sieve.py as the configuration for
        this benchmark requires only one run-fork.
      * The third bench batch will include only fibonacci.py, excluding both sieve.py and richards.py as their
        configurations require 1 and 2 run-forks, respectively.
    * Starting from the second build sieve.py will be excluded, as its configuration requires only one build.
    * In the third build fibonacci.py will also be excluded, as its configuration requires two builds.
      * This build will only contain two bench batches, as richards.py is the only remaining benchmark and its
        configuration requires two run-forks.
    * For example, the log line "[#15] Fork [interpreter/richards.py#2][interpreter/fibonacci.py#3]: 'image' stage"
      indicates that:
      * This is the 15th dispatch from the dispatcher - the 15th invocation of the `BenchmarkSuite.run` method.
      * The data collected from this dispatch will be duplicated for the benchmarks 'interpreter/richards.py' and
        'interpreter/fibonacci.py'. Each of these will be labeled as belonging to different forks. The 'richards.py'
        datapoints will be labeled as belonging to fork number 2 (`metric.fork-number = 2`), while the 'fibonacci.py'
        datapoints will be labeled as belonging to fork number 3 (`metric.fork-number = 3`).
        * This data duplication and relabeling is done with the intention of making the data easier to inspect in the
          average use-case - inspecting a certain benchmark. Thanks to benchmark specific fork numbers, the data will
          appear to be present for a continuous selection of forks, regardless of the observed benchmark.
        * The same data can be duplicated and shared across benchmarks due to the fact that a language launcher image
          is built - an image that is benchmark agnostic. For this reason image stages can be shared, while run stages
          belong to a single benchmark.
      * This dispatch will only execute the 'image' stage.
    * The instrumentation profiles collected in the instrument-run stages are all passed to the image stage with the
      `-Dnative-image.benchmark.pgo=` option.
    """

    LANGUAGE_LAUNCHER: str = "<<language_launcher>>"

    def __init__(self, state: BenchmarkDispatcherState, stable_run_config: str):
        super().__init__(state, stable_run_config)
        self._dispatch_counter: int = 0

    def dispatch_and_log(
        self, benchmarks: List[str], total_dispatch_count: int, fork_number_dict: Dict[str, int], dry_run: bool
    ) -> Generator[BenchmarkExecutionConfiguration, Any, None]:
        """
        Yields according to the schedule:
        * First, it iterates over the builds, determined by the highest requested build count. In each iteration, an
          image will be built at the start, and then a number of benchmarking batches will be executed using the image.
        * Second, it iterates over the preparation and benchmark batches. The number of benchmark batches is determined
          by the highest requested run-fork count from the benchmarks running on the current build.
          This loop is implemented in the `dispatch_build` method.
        * Third, it iterates over each benchmark which requires to be run in the current benchmark batch.
          This loop is implemented in the `dispatch_batch` method.
        """
        build_count = max([self._stable_run_config.get_benchmark(bench).builds for bench in benchmarks])
        self._dispatch_counter = 0
        with ConstantContextValueManager(PolybenchBenchmarkSuite.PGO_PROFILES, []):
            for build_index in range(build_count):
                yield from self.dispatch_build(benchmarks, total_dispatch_count, fork_number_dict, dry_run, build_index)

    def dispatch_build(
        self,
        benchmarks: List[str],
        total_dispatch_count: int,
        fork_number_dict: Dict[str, int],
        dry_run: bool,
        build_index: int,
    ) -> Generator[BenchmarkExecutionConfiguration, Any, None]:
        """See the `dispatch_and_log` doc comment."""
        if dry_run:
            mx.log(f" * Build #{build_index + 1}")
        build_stages = ["agent", "instrument-image", "instrument-run", "image"] if build_index == 0 else ["image"]
        current_build_benchmarks = [
            bench for bench in benchmarks if self._stable_run_config.get_benchmark(bench).builds > build_index
        ]
        number_of_preparation_batches = len(build_stages)
        bench_batches = [self._stable_run_config.get_benchmark(bench).forks for bench in current_build_benchmarks]
        number_of_batches = number_of_preparation_batches + max(bench_batches)
        with ConstantContextValueManager(PolybenchBenchmarkSuite.BUILD_BENCHMARKS, current_build_benchmarks):
            for batch_index in range(number_of_batches):
                yield from self.dispatch_batch(
                    current_build_benchmarks,
                    total_dispatch_count,
                    fork_number_dict,
                    dry_run,
                    batch_index,
                    build_stages,
                    number_of_preparation_batches,
                )

    def dispatch_batch(
        self,
        benchmarks: List[str],
        total_dispatch_count: int,
        fork_number_dict: Dict[str, int],
        dry_run: bool,
        batch_index: int,
        build_stages: List[str],
        number_of_preparation_batches: int,
    ) -> Generator[BenchmarkExecutionConfiguration, Any, None]:
        """See the `dispatch_and_log` doc comment."""
        stage = Stage.from_string(build_stages[batch_index] if batch_index < number_of_preparation_batches else "run")
        new_vm_args = [f"-Dnative-image.benchmark.stages={stage}"]
        if stage.is_final() and len(bm_exec_context().get_opt(PolybenchBenchmarkSuite.PGO_PROFILES, [])) > 0:
            pgo_profiles = ",".join(map(str, bm_exec_context().get(PolybenchBenchmarkSuite.PGO_PROFILES)))
            new_vm_args.append(f"-Dnative-image.benchmark.pgo={pgo_profiles}")
        extended_bm_suite_args = self._extend_vm_args(self.state.suite, self.state.bm_suite_args, new_vm_args)
        run_batch_index = batch_index - number_of_preparation_batches
        benchmarks_for_batch = self._get_benchmarks_for_native_batch(benchmarks, stage, run_batch_index)
        if dry_run:
            if run_batch_index < 0:
                mx.log(f"   * Preparation batch (batch #{batch_index + 1})")
            elif run_batch_index >= 0:
                mx.log(f"   * Bench batch #{run_batch_index + 1} (batch #{batch_index + 1})")
        with ConstantContextValueManager(PolybenchBenchmarkSuite.FORK_FOR_IMAGE, run_batch_index):
            for benchmark in benchmarks_for_batch:
                if dry_run:
                    if stage.is_image():
                        fork_numbers = [f"[{bench}#{fork_number_dict[bench]}]" for bench in benchmarks]
                        mx.log(f"     [#{self._dispatch_counter + 1}] Fork {''.join(fork_numbers)}: '{stage}' stage")
                    else:
                        msg = f"     [#{self._dispatch_counter + 1}] Fork #{fork_number_dict[benchmark]}: '{stage}' stage on {benchmark}"
                        mx.log(msg)
                else:
                    msg = f"Execution of dispatch {self._dispatch_counter + 1}/{total_dispatch_count} running {stage} stage on {benchmark}"
                    mx.log(msg)
                mx_bench_args = self.state.mx_benchmark_args
                last_dispatch = self._dispatch_counter + 1 == total_dispatch_count
                with ConstantContextValueManager("last_dispatch", last_dispatch):
                    total_fork_count = (
                        self._stable_run_config.get_benchmark(benchmark).builds
                        * self._stable_run_config.get_benchmark(benchmark).forks
                    )
                    fork_info = ForkInfo(fork_number_dict[benchmark], total_fork_count)
                    yield BenchmarkExecutionConfiguration([benchmark], mx_bench_args, extended_bm_suite_args, fork_info)
                self._dispatch_counter += 1
                if run_batch_index >= 0:
                    fork_number_dict[benchmark] += 1
                if stage.is_image() and stage.is_final():
                    fork_number_dict[self.LANGUAGE_LAUNCHER] += 1

    def _get_benchmarks_for_native_batch(self, benchmarks: List[str], stage: Stage, run_batch_index: int) -> List[str]:
        if stage.is_image():
            return [benchmarks[0]]
        return self._get_benchmarks_for_batch(benchmarks, run_batch_index)

    def _verify_no_conflicting_args_are_set(self):
        super()._verify_no_conflicting_args_are_set()
        vm_args = self.state.suite.vmArgs(self.state.bm_suite_args)
        if len(parse_prefixed_args("-Dnative-image.benchmark.stages=", vm_args)) > 0:
            msg = f"Setting the VM option '-Dnative-image.benchmark.stages' is not supported when using {self.__class__.__name__} as a dispatcher!"
            raise ValueError(msg)

    def _get_required_config_levels(self) -> List[str]:
        return ["builds"] + super()._get_required_config_levels()

    def _extend_vm_args(
        self, suite: "PolybenchBenchmarkSuite", bm_suite_args: List[str], new_vm_args: List[str]
    ) -> List[str]:
        vm_args, run_args = suite.vmAndRunArgs(bm_suite_args)
        return vm_args + new_vm_args + ["--"] + run_args

    def _init_fork_number_dict(self, benchmarks) -> Dict[str, int]:
        return super()._init_fork_number_dict(benchmarks + [self.LANGUAGE_LAUNCHER])


class ImageStageDatapointDuplicatingPostProcessor(DataPointsPostProcessor):
    """
    Ensures the datapoints from the image stage are duplicated so that there is a datapoint for:
     * each benchmark: To facilitate easier access to image stage metrics to users inspecting a specific benchmark.
     * the executable name (e.g. "python"): To indicate that the image does not have anything to do with the
                                            currently running benchmarks - as the image produced is a language
                                            launcher that will take the benchmark file as input.
    Doing both of these might seem counterintuitive, but it is done with two different users in mind.

    Does not have any effect if not in native mode and thus no image stage datapoints are present.
    """

    def __init__(self, suite: "PolybenchBenchmarkSuite"):
        super().__init__()
        self._suite = suite

    def process_datapoints(self, datapoints: DataPoints) -> DataPoints:
        copies = []
        executable_name = bm_exec_context().get(PolybenchBenchmarkSuite.CURRENT_IMAGE).executable_name()
        all_benchmarks = bm_exec_context().get("benchmarks")
        benchmarks = bm_exec_context().get_opt(PolybenchBenchmarkSuite.BUILD_BENCHMARKS, all_benchmarks)
        override_map = bm_exec_context().get_opt(PolybenchBenchmarkSuite.FORK_OVERRIDE_MAP)
        for dp in datapoints:
            stage = dp.get("native-image.stage")
            if stage is not None and "image" in stage:
                self.set_benchmark_specific_dimensions(
                    dp,
                    executable_name,
                    override_map,
                    StabilizingPolybenchNativeImageBenchmarkDispatcher.LANGUAGE_LAUNCHER,
                )
                dp["extra.duplicated"] = "false"
                for benchmark in benchmarks:
                    copy = dp.copy()
                    self.set_benchmark_specific_dimensions(copy, benchmark, override_map)
                    copy["extra.duplicated"] = "true"
                    copies.append(copy)
        return datapoints + copies

    def set_benchmark_specific_dimensions(
        self, dp: DataPoint, benchmark: str, override_map: Dict[str, int], override_key: Optional[str] = None
    ):
        dp["benchmark"] = benchmark
        if override_map is None or dp.get("metric.fork-number") is None:
            return
        if override_key is None:
            override_key = benchmark
        if override_key not in override_map:
            raise ValueError(f"No fork override provided for '{override_key}'!")
        dp["metric.fork-number"] = override_map[override_key]


class FinalDispatchFinalStageAverageWithOutlierRemovalPostProcessor(
    mx_benchmark.DataPointsAverageProducerWithOutlierRemoval
):
    """
    Customizable post-processor that is intended to execute only once: after the run stage (last stage) of
    the last dispatch, but considers all the datapoints (from previous stages and dispatches)
    when removing outliers and computing the average.

    DEVELOPER NOTES:
    * Should be scheduled to execute only once!
    * Groups formed by the `key_fn` should contain only datapoints with the same "benchmark" dimension!
    """

    def __init__(
        self,
        suite: "PolybenchBenchmarkSuite",
        selector_fn: Optional[Callable[[DataPoint], bool]],
        key_fn: Optional[Callable[[DataPoint], Any]],
        field: str,
        update_fn: Optional[Callable[[DataPoint], DataPoint]],
        final_consumer: bool,
    ):
        # The lower and upper percentiles will be set on a per-group basis in `calculate_aggregate_value` - as they
        # can have different values for different benchmarks.
        super().__init__(selector_fn, key_fn, field, update_fn, 0, 1)
        self._suite = suite
        self._final_consumer = final_consumer

    def select_datapoints(self, datapoints: DataPoints) -> DataPoints:
        # Select datapoints from all forks and stages. The latest datapoints (the ones in the `datapoints` argument)
        # have not yet been added to PolybenchBenchmarkSuite.DATAPOINTS by the `ContextStorePostProcessor`, so
        # we add them here.
        return super().select_datapoints(bm_exec_context().get(PolybenchBenchmarkSuite.DATAPOINTS) + datapoints)

    def process_datapoints(self, datapoints: DataPoints) -> DataPoints:
        if self._final_consumer:
            if bm_exec_context().get(PolybenchBenchmarkSuite.CONSUMED):
                msg = "Failed to guarantee a single execution! The aggregate datapoints were already produced!"
                raise ValueError(msg)
            bm_exec_context().update(PolybenchBenchmarkSuite.CONSUMED, True)
        return super().process_datapoints(datapoints)

    def calculate_aggregate_value(self, datapoints: DataPoints) -> Any:
        self.determine_outlier_exclusion_percentiles(datapoints)
        return super().calculate_aggregate_value(datapoints)

    def determine_outlier_exclusion_percentiles(self, datapoints: DataPoints):
        config: Optional[SuiteStableRunConfig] = bm_exec_context().get(PolybenchBenchmarkSuite.STABLE_CONFIG)
        benchmark: str = self.get_and_verify_unique_benchmark_dimension(datapoints)
        if config is None:
            # Handle non-stable-run benchmarks
            self._lower_percentile = 0
            self._upper_percentile = 1
            return
        # Handle stable-run benchmarks
        bench_config = config.get_benchmark(benchmark)
        self.determine_stable_run_outlier_exclusion_percentiles(bench_config)

    def determine_stable_run_outlier_exclusion_percentiles(self, bench_config: BenchmarkStableRunConfig):
        self._lower_percentile = bench_config.outlier_exclusion.lower_percentile
        self._upper_percentile = bench_config.outlier_exclusion.upper_percentile

    def get_and_verify_unique_benchmark_dimension(self, datapoints: DataPoints) -> str:
        benchmark = datapoints[0]["benchmark"]
        for dp in datapoints:
            if dp["benchmark"] != benchmark:
                raise ValueError("The datapoints group is expected to share the 'benchmark' dimension but does not!")
        return benchmark

    def verify_and_process_id_score_function(self, datapoint: DataPoint):
        score_function = datapoint.get("metric.score-function", "id")
        if score_function != "id":
            raise ValueError(
                f"{self.__class__.__name__} can only post-process datapoints with a 'metric.score-function' of value 'id'! Encountered score function: '{score_function}'."
            )
        datapoint["metric.score-value"] = datapoint["metric.value"]


class NonNativeImageBenchmarkSummaryPostProcessor(FinalDispatchFinalStageAverageWithOutlierRemovalPostProcessor):
    """
    Post-processor that calculates the outlier-excluded average of the "avg-time" metric across dispatches
    and produces a final "time" metric for a benchmark.
    Should only be used when running a benchmark in server (non-native) mode.
    """

    def __init__(self, suite: "PolybenchBenchmarkSuite"):
        selector_fn = lambda dp: dp["metric.name"] == "avg-time" and dp["metric.object"] == "fork"
        key_fn = lambda dp: dp["benchmark"]
        field = "metric.value"

        def update_fn(dp):
            dp["metric.name"] = "time"
            if "metric.object" in dp:
                del dp["metric.object"]
            if "metric.fork-number" in dp:
                del dp["metric.fork-number"]
            self.verify_and_process_id_score_function(dp)
            return dp

        super().__init__(suite, selector_fn, key_fn, field, update_fn, True)

    def determine_stable_run_outlier_exclusion_percentiles(self, bench_config: BenchmarkStableRunConfig):
        self._lower_percentile = bench_config.fork_outlier_exclusion.lower_percentile
        self._upper_percentile = bench_config.fork_outlier_exclusion.upper_percentile


class NativeModeBuildSummaryPostProcessor(FinalDispatchFinalStageAverageWithOutlierRemovalPostProcessor):
    """
    Post-processor that calculates the outlier-excluded average of the "avg-time" metric across run-only-forks
    and produces the "avg-time" metric for an image build.
    Should only be used when running a benchmark in native mode.
    """

    def __init__(self, suite: "PolybenchBenchmarkSuite"):
        selector_fn = lambda dp: dp["metric.name"] == "avg-time" and dp["metric.object"] == "fork"
        key_fn = lambda dp: (dp["benchmark"], dp["native-image.stage"], dp["native-image.rebuild-number"])
        field = "metric.value"

        def update_fn(dp):
            dp["metric.object"] = "build"
            if "metric.fork-number" in dp:
                del dp["metric.fork-number"]
            if "native-image.image-fork-number" in dp:
                del dp["native-image.image-fork-number"]
            self.verify_and_process_id_score_function(dp)
            return dp

        super().__init__(suite, selector_fn, key_fn, field, update_fn, False)

    def determine_stable_run_outlier_exclusion_percentiles(self, bench_config: BenchmarkStableRunConfig):
        self._lower_percentile = bench_config.fork_outlier_exclusion.lower_percentile
        self._upper_percentile = bench_config.fork_outlier_exclusion.upper_percentile


class NativeModeBenchmarkSummaryPostProcessor(FinalDispatchFinalStageAverageWithOutlierRemovalPostProcessor):
    """
    Post-processor that calculates the outlier-excluded average of the "avg-time" metric across image builds
    and produces a final "time" metric for a benchmark (separate "run" and "instrument-run" datapoints).
    Should only be used when running a benchmark in native mode.
    """

    def __init__(self, suite: "PolybenchBenchmarkSuite"):
        selector_fn = lambda dp: dp["metric.name"] == "avg-time" and dp["metric.object"] == "build"
        key_fn = lambda dp: (dp["benchmark"], dp["native-image.stage"])
        field = "metric.value"

        def update_fn(dp):
            dp["metric.name"] = "time"
            if "metric.fork-number" in dp:
                del dp["metric.fork-number"]
            if "native-image.image-fork-number" in dp:
                del dp["native-image.image-fork-number"]
            if "metric.object" in dp:
                del dp["metric.object"]
            if "native-image.rebuild-number" in dp:
                del dp["native-image.rebuild-number"]
            self.verify_and_process_id_score_function(dp)
            return dp

        config: Optional[SuiteStableRunConfig] = bm_exec_context().get(PolybenchBenchmarkSuite.STABLE_CONFIG)
        if config is not None:
            self._stable_run: bool = True
            self._v1_benchmarks: List[str] = [
                b for b in config.benchmarks() if config.get_benchmark(b).policy == StableRunPolicy.INDIVIDUAL_BUILDS
            ]
        else:
            self._stable_run: bool = False
            self._v1_benchmarks: List[str] = []

        super().__init__(suite, selector_fn, key_fn, field, update_fn, True)

    def select_datapoints(self, datapoints: DataPoints) -> DataPoints:
        if self._stable_run:
            self._selector_fn = lambda dp: (
                (
                    dp["benchmark"] in self._v1_benchmarks
                    and dp["metric.name"] == "avg-time"
                    and dp["metric.object"] == "build"
                )
                or (
                    dp["benchmark"] not in self._v1_benchmarks
                    and dp["metric.name"] == "avg-time"
                    and dp["metric.object"] == "fork"
                )
            )
        return super().select_datapoints(datapoints)

    def determine_stable_run_outlier_exclusion_percentiles(self, bench_config: BenchmarkStableRunConfig):
        self._lower_percentile = bench_config.build_outlier_exclusion.lower_percentile
        self._upper_percentile = bench_config.build_outlier_exclusion.upper_percentile


class GraalSpecificFieldsRemoverPostProcessor(DataPointsPostProcessor):
    """
    Removes all platform Graal specific fields from all the datapoints.
    Used for cleaning up the bench results of a benchmark that runs on
    a different platform (e.g. CPython).
    The removed fields include:
        * The "guest-vm" and "guest-vm-config" fields.
        * All the "platform.*" fields.
    """

    def process_datapoints(self, datapoints: DataPoints) -> DataPoints:
        return [{k: v for k, v in dp.items() if self._should_be_kept(k)} for dp in datapoints]

    def _should_be_kept(self, key) -> bool:
        return key not in ["guest-vm", "guest-vm-config"] and not key.startswith("platform.")


class ContextStorePostProcessor(DataPointsPostProcessor):
    """
    Post-processor that stores datapoints in the execution context for other post-processors that require access to
    datapoints from all dispatches. Performs no datapoints modifications.
    """

    def process_datapoints(self, datapoints: DataPoints) -> DataPoints:
        existing_datapoints = bm_exec_context().get(PolybenchBenchmarkSuite.DATAPOINTS)
        bm_exec_context().update(PolybenchBenchmarkSuite.DATAPOINTS, existing_datapoints + datapoints)
        return datapoints


class ContextResetPostProcessor(DataPointsPostProcessor):
    """
    Resets fork-batch specific execution context fields for the next fork batch. Performs no datapoints modifications.
    """

    def __init__(self, suite: "PolybenchBenchmarkSuite"):
        super().__init__()
        self._suite = suite

    def process_datapoints(self, datapoints: DataPoints) -> DataPoints:
        if (
            not bm_exec_context().get(PolybenchBenchmarkSuite.CONSUMED)
            and not self._suite.polybench_bench_suite_args(bm_exec_context().get("bm_suite_args")).dry_stable_run
        ):
            msg = f"Failed to produce the aggregate benchmark datapoints! This should have happened in the final fork!"
            raise ValueError(msg)
        bm_exec_context().update(PolybenchBenchmarkSuite.CONSUMED, False)
        bm_exec_context().update(PolybenchBenchmarkSuite.DATAPOINTS, [])
        bm_exec_context().update(PolybenchBenchmarkSuite.REBUILD_NUMBER, -1)
        return datapoints


class CurrentImageManager(ConstantContextValueManager):
    """Represents the currently used PolyBench image cache entry."""

    def __init__(
        self, suite: "PolybenchBenchmarkSuite", resolved_benchmark: ResolvedPolybenchBenchmark, bm_suite_args: List[str]
    ):
        languages = resolved_benchmark.suite.languages
        impactful_vm_args = suite.vm_args_impacting_image_build(bm_suite_args)
        entry = PolybenchImageCacheEntry.create(languages, impactful_vm_args)
        super().__init__(PolybenchBenchmarkSuite.CURRENT_IMAGE, entry)

    def __enter__(self):
        try:
            super().__enter__()
        except ValueError:
            existing_entry = bm_exec_context().get(self._name).executable_name()
            msg = f"Tried to set current image to {self._value.executable_name()}, but there is already a current image ({existing_entry})."
            raise ValueError(msg)


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
    REUSE_DISK_IMAGES = "POLYBENCH_REUSE_DISK_IMAGES"
    POLYBENCH_BENCH_SUITE_PARSER_NAME = "polybench_bench_suite_parser_name"
    # Use "PolybenchBenchmarkSuite.*" execution context keys to avoid potential key collisions
    CURRENT_IMAGE = "PolybenchBenchmarkSuite.current_image"
    IMAGE_CACHE = "PolybenchBenchmarkSuite.image_cache"
    FORK_OVERRIDE_MAP = "PolybenchBenchmarkSuite.fork_number_override_map"
    REBUILD_NUMBER = "PolybenchBenchmarkSuite.rebuild_number"
    DATAPOINTS = "PolybenchBenchmarkSuite.datapoints"
    CONSUMED = "PolybenchBenchmarkSuite.consumed_datapoints"
    STABLE_CONFIG = "PolybenchBenchmarkSuite.stable_run_config"
    FORK_FOR_IMAGE = "PolybenchBenchmarkSuite.image_fork_number"
    BUILD_BENCHMARKS = "PolybenchBenchmarkSuite.current_build_benchmarks"
    PGO_PROFILES = "PolybenchBenchmarkSuite.collected_pgo_profiles"

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        bm_exec_context().add_context_value(PolybenchBenchmarkSuite.IMAGE_CACHE, BoxContextValue(set()))

    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def name(self):
        return "polybench"

    def version(self):
        return "0.4.0"

    def _resolve_benchmarks(self) -> Dict[str, ResolvedPolybenchBenchmark]:
        if not hasattr(self, "_benchmarks"):
            self._benchmarks = _resolve_all_benchmarks()
        return self._benchmarks

    def benchmarkList(self, bmSuiteArgs):
        return list(self._resolve_benchmarks().keys())

    def default_stages(self) -> List[str]:
        # Never run the agent stage (PGO stages will be filtered if not requested).
        return ["instrument-image", "instrument-run", "image", "run"]

    def filter_stages_with_cli_requested_stages(self, bm_suite_args: List[str], stages: List[Stage]) -> List[Stage]:
        # Respect '-Dnative-image.benchmark.stages=' user specified stages if they are present
        if len(parse_prefixed_args("-Dnative-image.benchmark.stages=", self.vmArgs(bm_suite_args))) > 0:
            return super().filter_stages_with_cli_requested_stages(bm_suite_args, stages)
        # Filter stages for optimized fork runs: we might want just a single instrument-image stage and multiple run stages per one image stage
        preserve_only_run_stages = self._image_is_cached(bm_suite_args)
        remove_instrumentation_stages = (
            bm_exec_context().get(PolybenchBenchmarkSuite.REBUILD_NUMBER) > 0
            and not self.polybench_bench_suite_args(bm_suite_args).regenerate_instrumentation_profile
        )
        if preserve_only_run_stages:
            stages = [s for s in stages if StageName.RUN == s.stage_name]
        elif remove_instrumentation_stages:
            stages = [s for s in stages if not s.is_instrument()]
        return stages

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

    @staticmethod
    def resolve_config_field_or_default(config: dict, keys: List[str], default: Any) -> Any:
        """Resolves a nested Polybench config dictionary value, or returns the default value if a key cannot be resolved."""
        if config is None:
            return default
        curr = config
        for key in keys:
            if not isinstance(curr, dict) or key not in curr:
                return default
            curr = curr[key]
        return curr

    def get_dispatcher(self, state: BenchmarkDispatcherState) -> BenchmarkDispatcher:
        """Returns one of the custom dispatchers if the '--stable-run-config' option is set, defaults to super otherwise."""
        stable_run_config = self.polybench_bench_suite_args(state.bm_suite_args).stable_run_config
        if stable_run_config is not None:
            if self.is_native_mode(state.bm_suite_args):
                dispatcher_class = StabilizingPolybenchNativeImageBenchmarkDispatcher
            else:
                dispatcher_class = StabilizingPolybenchBenchmarkDispatcher
            msg = f"Using a {dispatcher_class.__name__} instance for benchmark dispatching due to the '--stable-run-config' option being set."
            mx.log(msg)
            return dispatcher_class(state, stable_run_config)
        return super().get_dispatcher(state)

    def before(self, bmSuiteArgs):
        super().before(bmSuiteArgs)
        bm_exec_context().add_context_value(PolybenchBenchmarkSuite.DATAPOINTS, BoxContextValue([]))
        bm_exec_context().add_context_value(PolybenchBenchmarkSuite.CONSUMED, BoxContextValue(False))
        bm_exec_context().add_context_value(PolybenchBenchmarkSuite.REBUILD_NUMBER, BoxContextValue(-1))

    def after(self, bmSuiteArgs):
        bm_exec_context().remove(PolybenchBenchmarkSuite.DATAPOINTS)
        bm_exec_context().remove(PolybenchBenchmarkSuite.CONSUMED)
        bm_exec_context().remove(PolybenchBenchmarkSuite.REBUILD_NUMBER)
        super().after(bmSuiteArgs)

    def run_stage(self, vm, stage: Stage, command, out, err, cwd, nonZeroIsFatal):
        # Increment rebuild number before running the 'image' stage
        if stage.is_image() and stage.is_final():
            bm_exec_context().update(
                PolybenchBenchmarkSuite.REBUILD_NUMBER,
                bm_exec_context().get(PolybenchBenchmarkSuite.REBUILD_NUMBER) + 1,
            )
        exit_code = super().run_stage(vm, stage, command, out, err, cwd, nonZeroIsFatal)
        # Copy the profile after running the 'instrument-run' stage
        self._ensure_instrumentation_profile_name_is_benchmark_specific(vm, stage)
        return exit_code

    def _ensure_instrumentation_profile_name_is_benchmark_specific(
        self, vm: mx_sdk_benchmark.NativeImageVM, stage: Stage
    ):
        not_instrument_stage = stage.stage_name != StageName.INSTRUMENT_RUN
        no_collection = not bm_exec_context().has(PolybenchBenchmarkSuite.PGO_PROFILES)
        if not_instrument_stage or no_collection:
            return
        # Copy the profile to ensure it isn't overwritten by next benchmark
        new_pgo_profile = vm.config.profile_path
        benchmark_sanitized = bm_exec_context().get("benchmark").replace("/", "-").replace(".", "-")
        bench_unique_profile_path = new_pgo_profile.parent / f"{benchmark_sanitized}.iprof"
        shutil.copy(new_pgo_profile, bench_unique_profile_path)
        # Store the profile for use in upcoming IMAGE stages
        bm_exec_context().get(PolybenchBenchmarkSuite.PGO_PROFILES).append(bench_unique_profile_path)

    def run(self, benchmarks, bmSuiteArgs) -> DataPoints:
        # name used by NativeImageBenchmarkMixin
        self.benchmark_name = benchmarks[0]

        working_directory = self.workingDirectory(benchmarks, bmSuiteArgs) or os.getcwd()
        resolved_benchmark = self._resolve_current_benchmark(benchmarks)

        mx.log(f'Running polybench benchmark "{resolved_benchmark.name}".')
        mx.logv(f"CWD: {working_directory}")
        mx.logv(f"Languages included on the classpath: {resolved_benchmark.suite.languages}")

        env_vars = PolybenchBenchmarkSuite._prepare_distributions(working_directory, resolved_benchmark)
        with _extend_env(env_vars), CurrentImageManager(
            self, resolved_benchmark, bmSuiteArgs
        ), ConstantContextValueManager("benchmark", resolved_benchmark.name), ConstantContextValueManager(
            "native_mode", self.is_native_mode(bmSuiteArgs)
        ), ConstantContextValueManager(
            PolybenchBenchmarkSuite.STABLE_CONFIG, self._resolve_stable_run_config()
        ):
            datapoints = self.intercept_run(super(), benchmarks, bmSuiteArgs)
            if bm_exec_context().get("native_mode"):
                image_cache = bm_exec_context().get(PolybenchBenchmarkSuite.IMAGE_CACHE)
                image_cache.add(bm_exec_context().get(PolybenchBenchmarkSuite.CURRENT_IMAGE))
            return datapoints

    def use_stage_aware_benchmark_mixin_intercept_run(self):
        if self.jvm(bm_exec_context().get("bm_suite_args")) == "cpython":
            return True
        return False

    def _resolve_current_benchmark(self, benchmarks) -> ResolvedPolybenchBenchmark:
        if benchmarks is None or len(benchmarks) != 1:
            mx.abort(f"Must specify one benchmark at a time (given: {benchmarks})")
        return self._resolve_benchmarks()[benchmarks[0]]

    def _resolve_stable_run_config(self) -> Optional[SuiteStableRunConfig]:
        config_path = self.polybench_bench_suite_args(bm_exec_context().get("bm_suite_args")).stable_run_config
        if config_path is None:
            return None
        return SuiteStableRunConfig(config_path)

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

    def vm_args_impacting_image_build(self, bm_suite_args: List[str]) -> List[str]:
        """Returns the VM args excluding any args that do not impact the image."""
        vm_args = self.vmArgs(bm_suite_args)
        impactful_vm_args = []
        for vm_arg in vm_args:
            if vm_arg.startswith("-Dnative-image.benchmark.stages="):
                continue
            impactful_vm_args.append(vm_arg)
        return impactful_vm_args

    def _base_image_name(self) -> Optional[str]:
        """Overrides the image name used to build/run the image."""
        if self.jvm(bm_exec_context().get("bm_suite_args")) == "cpython":
            benchmark_sanitized = bm_exec_context().get("benchmark").replace("/", "-").replace(".", "-")
            return f"{benchmark_sanitized}-staged-benchmark"
        assert bm_exec_context().has(PolybenchBenchmarkSuite.CURRENT_IMAGE), "Image should have been set already"
        return bm_exec_context().get(PolybenchBenchmarkSuite.CURRENT_IMAGE).full_executable_name()

    def _image_is_cached(self, bm_suite_args: List[str]) -> bool:
        current_image = bm_exec_context().get(PolybenchBenchmarkSuite.CURRENT_IMAGE)
        image_cache = bm_exec_context().get(PolybenchBenchmarkSuite.IMAGE_CACHE)
        if current_image in image_cache:
            return True

        if mx.get_env(PolybenchBenchmarkSuite.REUSE_DISK_IMAGES) in ["true", "True"]:
            full_image_name = self.get_full_image_name(self.get_base_image_name(), self.jvmConfig(bm_suite_args))
            image_path = self.get_image_output_dir(
                self.benchmark_output_dir(self.benchmark_name, self.vmArgs(bm_suite_args)), full_image_name
            ) / self.get_image_file_name(full_image_name)
            if os.path.exists(image_path):
                mx.warn(
                    f"Existing image at {image_path} will be reused ({PolybenchBenchmarkSuite.REUSE_DISK_IMAGES} is set to true). "
                    "Reusing disk images is a development feature and it does not detect stale images. Use with caution."
                )
                return True

        return False

    def createCommandLineArgs(self, benchmarks, bmSuiteArgs):
        resolved_benchmark = self._resolve_current_benchmark(benchmarks)

        java_distributions = _get_java_distributions(resolved_benchmark)
        vm_args = mx.get_runtime_jvm_args(names=java_distributions) + self.vmArgs(bmSuiteArgs)
        mx_truffle.enable_truffle_native_access(vm_args)
        polybench_args = (
            ["--path=" + resolved_benchmark.absolute_path]
            + self.runArgs(bmSuiteArgs)
            + resolved_benchmark.suite.additional_polybench_args
        )
        return vm_args + [PolybenchBenchmarkSuite.POLYBENCH_MAIN] + polybench_args

    def parserNames(self) -> List[str]:
        return super().parserNames() + [PolybenchBenchmarkSuite.POLYBENCH_BENCH_SUITE_PARSER_NAME]

    def polybench_bench_suite_args(self, bm_suite_args: List[str]) -> Namespace:
        """Parses the "vm and suite" args for any known Polybench args and returns a namespace with Polybench arg values."""
        vm_and_suite_args = self.vmAndRunArgs(bm_suite_args)[0]
        namespace, _ = get_parser(PolybenchBenchmarkSuite.POLYBENCH_BENCH_SUITE_PARSER_NAME).parse_known_args(
            vm_and_suite_args
        )
        return namespace

    def runAndReturnStdOut(self, benchmarks, bmSuiteArgs):
        ret_code, out, dims = super().runAndReturnStdOut(benchmarks, bmSuiteArgs)
        host_vm_config = self._infer_host_vm_config(bmSuiteArgs, dims)
        guest_vm, guest_vm_config = self._infer_guest_vm_info(benchmarks, bmSuiteArgs)
        dims.update(
            {
                "host-vm-config": host_vm_config,
                "guest-vm": guest_vm,
                "guest-vm-config": guest_vm_config,
            }
        )
        if bm_exec_context().get("native_mode"):
            # max(0, _) to handle instrumentation stages and running on previously built images
            rebuild_num = max(0, bm_exec_context().get(PolybenchBenchmarkSuite.REBUILD_NUMBER))
            dims["native-image.rebuild-number"] = rebuild_num
            if bm_exec_context().has(PolybenchBenchmarkSuite.FORK_FOR_IMAGE):
                fork_for_image = max(0, bm_exec_context().get(PolybenchBenchmarkSuite.FORK_FOR_IMAGE))
                dims["native-image.image-fork-number"] = fork_for_image
        return ret_code, out, dims

    def _infer_host_vm_config(self, bm_suite_args, dims):
        edition = dims.get("platform.graalvm-edition", "unknown").lower()
        if edition not in ["ce", "ee"] or not dims.get("platform.prebuilt-vm", False):
            raise ValueError(f"Polybench should only run with a prebuilt GraalVM. Dimensions found: {dims}")

        if bm_exec_context().get("native_mode"):
            # patch ce/ee suffix
            existing_config = dims["host-vm-config"]
            existing_edition = existing_config.split("-")[-1]
            if existing_edition in ["ce", "ee"]:
                assert (
                    existing_edition == edition
                ), f"Existing host-vm-config {existing_config} conflicts with GraalVM edition {edition}"
                return existing_config
            return dims["host-vm-config"] + "-" + edition
        else:
            non_graal_vms = ["cpython"]
            if self.jvm(bm_suite_args) in non_graal_vms:
                return self.jvmConfig(bm_suite_args)
            # assume config used when building a GraalVM distribution
            return "graal-enterprise-libgraal-pgo" if edition == "ee" else "graal-core-libgraal"

    def _infer_guest_vm_info(self, benchmarks, bm_suite_args) -> Tuple[str, str]:
        resolved_benchmark = self._resolve_current_benchmark(benchmarks)
        # Eventually this must check for exact match for each language and map it to the corresponding guest-vm.
        # Here, we just infer it based on the presence of some language in a list. This must be made more robust
        # and more generic to handle the case when multiple languages are used.
        if "js" in resolved_benchmark.suite.languages:
            guest_vm = "graal-js"
        elif "python" in resolved_benchmark.suite.languages:
            guest_vm = "graalpython"
        elif "wasm" in resolved_benchmark.suite.languages:
            guest_vm = "wasm"
        else:
            guest_vm = "none"
        if "--engine.Compilation=false" in self.runArgs(
            bm_suite_args
        ) or "-Dpolyglot.engine.Compilation=false" in self.vmArgs(bm_suite_args):
            guest_vm_config = "interpreter"
        else:
            guest_vm_config = "default"
        if "-Dpython.EnableBytecodeDSLInterpreter=true" in self.vmArgs(bm_suite_args):
            guest_vm_config += "-bc-dsl"
        return guest_vm, guest_vm_config

    def rules(self, output, benchmarks, bmSuiteArgs):
        metric_name = PolybenchBenchmarkSuite._get_metric_name(output)
        if metric_name is None:
            return []
        rules = []
        benchmark_name = bm_exec_context().get("benchmark")
        if metric_name == "time":
            # For metric "time", two metrics are reported:
            # - "warmup" (per-iteration data for "warmup" and "run" iterations)
            # - "time-sample" (per-iteration data for only the "run" iterations)
            # - "avg-time" (aggregation of per-iteration data for the "run" iterations after outlier removal)
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
                        "metric.name": "time-sample",
                        "metric.unit": ("<unit>", str),
                        "metric.value": ("<value>", float),
                        "metric.type": "numeric",
                        "metric.score-function": "id",
                        "metric.iteration": ("<iteration>", int),
                    },
                    startPattern=r"::: Running :::",
                ),
                ExcludeWarmupRule(
                    r"\[.*\] run aggregate summary: (?P<value>.*) (?P<unit>.*)",
                    {
                        "benchmark": benchmark_name,
                        "metric.better": "lower",
                        "metric.name": "avg-time",
                        "metric.object": "fork",
                        "metric.unit": ("<unit>", str),
                        "metric.value": ("<value>", float),
                        "metric.type": "numeric",
                        "metric.score-function": "id",
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

    def post_processors(self) -> List[DataPointsPostProcessor]:
        post_processors = []

        # Modify the datapoints already produced in this run
        if self.jvm(bm_exec_context().get("bm_suite_args")) == "cpython":
            post_processors.append(GraalSpecificFieldsRemoverPostProcessor())
        if bm_exec_context().get("native_mode"):
            post_processors.append(ImageStageDatapointDuplicatingPostProcessor(self))

        # When running non-native benchmarks there is no concept of stages, there is only a single bench suite run.
        # So we store a pretend "run" stage to indicate that all datapoints (for this fork) have already been produced.
        current_stage = Stage.from_string("run")
        try:
            current_stage = self.stages_info.current_stage
        except AttributeError:
            pass
        last_stage = current_stage.stage_name == StageName.RUN
        # In the final stage of the final dispatch: calculate and add aggregate datapoints
        if bm_exec_context().get("last_dispatch") and last_stage:
            if bm_exec_context().get("native_mode"):
                post_processors += [
                    NativeModeBuildSummaryPostProcessor(self),
                    NativeModeBenchmarkSummaryPostProcessor(self),
                ]
            else:
                post_processors.append(NonNativeImageBenchmarkSummaryPostProcessor(self))
            post_processors.append(ContextResetPostProcessor(self))
        else:
            # Store this run's datapoints in the PolybenchBenchmarkSuite.DATAPOINTS execution context key
            # so they are available for final-dispatch aggregation.
            post_processors.append(ContextStorePostProcessor())

        return post_processors

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


_polybench_bench_suite_parser = ParserEntry(
    ArgumentParser(add_help=False), "Options for the Polybench benchmark suite:"
)
_polybench_bench_suite_parser.parser.add_argument(
    "--stable-run-config",
    help=(
        "Run a longer, more stable version of the benchmark with the specified configuration. "
        "The stability of the benchmark is improved by building the language launcher multiple times and running "
        "multiple benchmark forks on each language launcher image. Outliers are removed and metrics are produced "
        "as an aggregate of the remaining runs. The number of repeated builds and forks, as well as the outlier "
        "exclusion percentiles are defined per-benchmark in the configuration file."
    ),
)
_polybench_bench_suite_parser.parser.add_argument(
    "--dry-stable-run",
    action="store_true",
    help=("Print the dispatching schedule and exit. Only has an effect when '--stable-run-config' is set."),
)
_polybench_bench_suite_parser.parser.add_argument(
    "--regenerate-instrumentation-profile",
    action="store_true",
    help=(
        "Regenerate the instrumentation profile in every fork in which a new image is built instead of sharing the "
        "profile from the first fork. Relevant only for PGO benchmarks that run multiple forks."
    ),
)
add_parser(PolybenchBenchmarkSuite.POLYBENCH_BENCH_SUITE_PARSER_NAME, _polybench_bench_suite_parser)


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
    """Temporarily extends the environment variables for the extent of this context."""
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
    additional_polybench_args: Optional[List[str]] = None,
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
    :param additional_polybench_args: An optional list of arguments to always pass to the benchmark launcher (e.g., to
    specify a load path for benchmark sources).
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
        additional_polybench_args=additional_polybench_args or [],
    )
    if name in _polybench_benchmark_suite_registry:
        mx.abort(
            f"Polybench suite {name} was already registered.\n"
            f"Existing suite: {_polybench_benchmark_suite_registry[name]}\n"
            f"New suite: {entry}"
        )
    mx.logv(f"Registered polybench benchmark suite: {entry}")
    _polybench_benchmark_suite_registry[name] = entry
