#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
# ----------------------------------------------------------------------------------------------------

import os
from argparse import ArgumentParser
from os.path import join
from pathlib import Path
from typing import List, Optional, Union

import mx
import mx_gate
import mx_javamodules
import mx_sdk_vm
import mx_sdk_vm_impl
import mx_substratevm
import mx_unittest
import mx_util
import mx_compiler
from mx import TimeStampFile
from mx_compiler import GraalArchiveParticipant
from mx_gate import Task, add_gate_runner, Tags
from mx_substratevm import locale_US_args
from mx_unittest import unittest

_suite = mx.suite("web-image")

_web_image_js_engine_name = os.getenv("NODE_EXE", "node")

# Name of GraalVm component defining the web-image macro
web_image_component = "web-image"
# Name of GraalVm component defining the svm-wasm macro
svm_wasm_component = "niwasm"
web_image_builder = "web-image:SVM_WASM"
web_image_builder_jars = [
    web_image_builder,
    "web-image:SVM_WASM_JIMFS",
    "web-image:SVM_WASM_GUAVA",
    "web-image:WEBIMAGE_CLOSURE_SUPPORT",
    "web-image:WEBIMAGE_GOOGLE_CLOSURE",
]
# Hosted options defined in the web-image-enterprise suite
# This list has to be kept in sync with the code (the 'webimageoptions' gate tag checks this)
# See also WebImageConfiguration.hosted_options
web_image_hosted_options = [
    "AnalyzeCompoundConditionals",
    "AutoRunLibraries=",
    "AutoRunVM",
    "BenchmarkName=",
    "ClearFreeMemory",
    "CLIVisualizationMonochrome",
    "ClosureCompiler",
    "ClosurePrettyPrint=",
    "CodeSizeDiagnostics",
    "DebugNames",
    "DisableStackTraces",
    "DumpCurrentCompiledFunction",
    "DumpPreClosure",
    "DumpTypeControlGraph",
    "EnableTruffle",
    "EncodeImageHeapArraysBase64",
    "EntryPointsConfig=",
    "FatalUnsupportedNodes",
    "ForceSinglePrecision",
    "GCStressTest",
    "GenerateSourceMap",
    "GenTimingCode",
    "GrowthTriggerThreshold=",
    "HeapGrowthFactor=",
    "ImageHeapObjectsPerFunction=",
    "JSComments=",
    "JSRuntime=",
    "LogFilter=",
    "LoggingFile=",
    "LoggingStyle=",
    "NamingConvention=",
    "OutlineRuntimeChecks",
    "ReportImageSizeBreakdown",
    "RuntimeDebugChecks",
    "SILENT_COMPILE",
    "SourceMapSourceRoot=",
    "StackSize=",
    "StrictWarnings",
    "UnsafeErrorMessages",
    "UseBinaryen",
    "UsePEA",
    "UseRandomForTempFiles",
    "UseVtable",
    "VerificationPhases",
    "VerifyAllocations",
    "Visualization=",
    "VMClassName=",
    "WasmAsPath=",
    "WasmComments=",
    "WasmVerifyReferences",
    "Wat2WasmPath=",
]


class WebImageConfiguration:
    test_cases = ["WEBIMAGE_TESTCASES"]
    """List of dependencies containing test cases"""

    graalvm_component = web_image_component

    svm_wasm_component = svm_wasm_component

    builder_jars = web_image_builder_jars

    additional_modules = []
    """Additional modules that are added to --add-modules for the web-image launcher"""

    hosted_options = web_image_hosted_options
    """
    Options added to the ProvidedHostedOptions property for the svm-wasm tool macro

    The native-image launcher checks the validity of options in the driver (not the builder),
    for that it discovers all options on its classpath. This will not find the options for Wasm codegen because those
    jars are not on the driver's classpath (they are only added to the builder's classpath through the macro).

    The ProvidedHostedOptions property is a way to let the driver know about additional options it should accept.
    The alternative, adding the Wasm codegen jars to the drivers classpath doesn't work in all cases. It works for the
    bash launchers, but for the native image built from the driver in releases, the available options are looked up at
    build-time in a Feature; there, only options available on the builder's classpath are discovered. It is not possible
    to add the Wasm codegen jars to builder's classpath, because that would make it produce a Wasm binary.
    """

    suite = None
    """Suite used to resolve the location of the web-image executable"""

    @classmethod
    def get_graalvm_component(cls) -> mx_sdk_vm.GraalVmComponent:
        return mx_sdk_vm.graalvm_component_by_name(cls.graalvm_component)

    @classmethod
    def get_svm_wasm_component(cls) -> mx_sdk_vm.GraalVmComponent:
        return mx_sdk_vm.graalvm_component_by_name(cls.svm_wasm_component)

    @classmethod
    def get_suite(cls) -> mx.Suite:
        return cls.suite or mx.primary_suite()

    @classmethod
    def get_builder_jars(cls) -> List[str]:
        return cls.builder_jars

    @classmethod
    def get_additional_modules(cls) -> List[str]:
        return cls.additional_modules

    @classmethod
    def get_hosted_options(cls) -> List[str]:
        return cls.hosted_options


# For Web Image SVM_WASM is part of the modules implicitly available on the module-path
mx_sdk_vm_impl.NativePropertiesBuildTask.implicit_excludes.append(web_image_builder)


def web_image_main_class():
    return "com.oracle.svm.webimage.driver.WebImage"


def _graalvm_web_image_config(suite: mx.Suite):
    if suite.primary:
        return None
    return mx_substratevm.GraalVMConfig.build(primary_suite_dir=suite.dir)


def vm_web_image_path(suite=None):
    suite = suite or WebImageConfiguration.get_suite()
    return mx_substratevm.vm_executable_path("web-image", _graalvm_web_image_config(suite))


def compile_web_image(args, out=None, err=None, cwd=None, nonZeroIsFatal=True, suite=None):
    if any(arg.startswith("--help") or arg == "--version" for arg in args):
        final_args = args
    else:
        # Enables assertions in the JVM running the builder
        extra_args = ["-J-ea", "-J-esa"]
        # Enables assertions in the driver
        extra_args += ["--vm.ea", "--vm.esa"]
        final_args = mx_sdk_vm_impl.svm_experimental_options(extra_args + args)

    return mx.run([vm_web_image_path(suite)] + final_args, nonZeroIsFatal=nonZeroIsFatal, out=out, err=err, cwd=cwd)


@mx.command(_suite.name, "web-image", "options")
def web_image(args=None):
    """Compile Java bytecode to javascript

    Example usage: mx web-image -H:Name=<image-name> -H:Method=main -cp <classpath> HelloWorld
    """

    args = args or []

    # Adding the classpath for the Java test files and benchmarks is not
    # necessary but makes it easier to manually compile the testcases to
    # JavaScript because we don't have to manually specify their classpath.
    # If it needs to be done manually, we offer that option through
    # --include-testcases-to-classpath flag.
    if "--include-testcases-to-classpath" in args:
        args.remove("--include-testcases-to-classpath")
        cp_index, cp_value = mx.find_classpath_arg(args)
        additional_args = get_launcher_flags(WebImageConfiguration.test_cases, cp_suffix=cp_value)
        if cp_index:
            del args[cp_index]
            del args[cp_index - 1]
        args += additional_args

    compile_web_image(args)


class GraalWebImageTags:
    webimagebuild = "webimagebuild"
    webimagespectest = "webimagespectest"
    webimagespectest_closure = "webimagespectest_closure"
    webimagespectest_noclosure = "webimagespectest_no-closure"
    webimageunittest = "webimageunittest"
    webimageprettier = "webimageprettier"
    webimagehelp = "webimagehelp"
    webimageoptions = "webimageoptions"


@mx.command(_suite.name, "webimageprettier")
def prettier(args=None):
    parser = ArgumentParser(prog="mx webimageprettier")
    parser.add_argument(
        "source_files", metavar="FILE", nargs="*", help="Source files to format (formats all by default)"
    )
    parser.add_argument(
        "-n",
        "--dry-run",
        action="store_true",
        help="Do not write files to disk. Will abort if any files have formatting errors",
    )
    parsed_args = parser.parse_args(args)

    source_files = parsed_args.source_files

    if not source_files:
        source_files = []
        projectsToProcess = mx.projects(limit_to_primary=True)
        for p in projectsToProcess:
            sourceDirs = p.source_dirs()
            for sourceDir in sourceDirs:
                for root, _, files in os.walk(sourceDir):
                    for f in [join(root, name) for name in files if name.endswith(".js")]:
                        source_files.append(f)

    (rc, out, err) = prettier_runner(["--list-different"], files=source_files, nonZeroIsFatal=False)

    if err:
        mx.log(err)

    diff_files = out.strip().split(os.linesep)

    if rc != 0:
        if not diff_files:
            mx.abort("An error occured while formatting")

        for f in diff_files:
            mx.log(f)
            (_, formatted, _) = prettier_runner([], files=[f], nonZeroIsFatal=True)
            mx.run(["diff", "-u", "-p", f, "-"], stdin=formatted, nonZeroIsFatal=False)

            if not parsed_args.dry_run:
                with open(f, "w") as out_file:
                    out_file.write(formatted)

        if parsed_args.dry_run:
            mx.abort("{} files have formatting errors. Run mx webimageprettier".format(len(diff_files)))
        else:
            mx.log("Formatted {} files".format(len(diff_files)))
    else:
        mx.log("No files with formatting errors")


def prettier_gate():
    prettier(["-n"])


def prettier_runner(args=None, files=None, nonZeroIsFatal=False):
    """
    :return: A triple (exit code, stdout, stderr)
    """
    if args is None:
        args = []

    config = _suite.mxDir + "/.prettierrc"

    exe = os.environ.get("PRETTIER_EXE", "npx prettier").split(" ")

    if not files:
        mx.log("[no JS sources - skipping]")
        return (0, "", "")

    final_rc = 0

    outCapture = mx.OutputCapture()
    errCapture = mx.OutputCapture()

    for chunk in mx._chunk_files_for_command_line(files):
        rc = mx.run(
            exe + ["--config", config] + args + chunk, out=outCapture, err=errCapture, nonZeroIsFatal=nonZeroIsFatal
        )

        final_rc = max(final_rc, rc)

    return (final_rc, outCapture.data, errCapture.data)


def hosted_options_gate() -> None:
    """
    Checks that WebImageConfiguration.hosted_options matches the options in the code.

    For that, there is an extra hosted option, DumpProvidedHostedOptionsAndExit, that just prints the expected
    contents of the ProvidedHostedOptions property.
    """
    out = mx.LinesOutputCapture()
    # The driver requires a class name to invoke the builder, so we just pass an arbitrary name, the builder will exit
    # before it tries to load it anyway.
    compile_web_image(
        ["SomeClassName", "-H:+DumpProvidedHostedOptionsAndExit"],
        out=out,
        nonZeroIsFatal=True,
        suite=WebImageConfiguration.get_suite(),
    )
    actual_options = sorted(out.lines)
    hardcoded_options = sorted(WebImageConfiguration.get_hosted_options())

    if hardcoded_options != actual_options:
        actual_options_set = set(actual_options)
        hardcoded_options_set = set(hardcoded_options)

        missing_hardcoded_options = list(actual_options_set - hardcoded_options_set)
        additional_hardcoded_options = list(hardcoded_options_set - actual_options_set)

        mx.abort(
            "Mismatch in hardcoded options and options defined in the code\n"
            + (
                "Options that are not hardcoded: " + str(missing_hardcoded_options) + "\n"
                if missing_hardcoded_options
                else ""
            )
            + (
                "Hardcoded options that don't exist: " + str(additional_hardcoded_options) + "\n"
                if additional_hardcoded_options
                else ""
            )
            + "Please update the list of hardcoded options to reflect the Web Image options declared in the codebase"
        )


def determine_gate_unittest_args(gate_args):
    vm_options = ["-Dwebimage.test.max_failures=10"]

    if gate_args.extra_unittest_argument:
        vm_options += gate_args.extra_unittest_argument

    builder_options = ["-H:+StrictWarnings", f"-H:Backend={gate_args.backend}"]
    if gate_args.spectest_argument:
        builder_options += gate_args.spectest_argument

    if builder_options:
        vm_options += ["-Dwebimage.test.additional_vm_options=" + (",".join(builder_options))]

    if gate_args.spectest:
        spec_tests = [gate_args.spectest]
    else:
        spec_tests = ["JS_JTT_Spec_Test"]

    return vm_options + spec_tests


def gate_runner(args, tasks):
    unittest_args = determine_gate_unittest_args(args)

    # build vm
    with Task("BuildHotSpotGraalServer", tasks, tags=[GraalWebImageTags.webimagebuild]) as t:
        if t:
            mx.build([])

    with Task("Web Image JSFormatCheck", tasks, tags=[Tags.style, GraalWebImageTags.webimageprettier]) as t:
        if t:
            prettier_gate()

    with Task("Web Image Unit Tests", tasks, tags=[GraalWebImageTags.webimageunittest]) as t:
        if t:
            unittest(["--verbose", "--enable-timing", "WebImageUnitTests"])

    with Task(
        "Web Image Spec Tests Without Closure Compiler",
        tasks,
        tags=[GraalWebImageTags.webimagespectest, GraalWebImageTags.webimagespectest_noclosure],
    ) as t:
        if t:
            unittest(["--verbose", "--enable-timing", "-Dwebimage.test.closure_compiler=false"] + unittest_args)

    with Task(
        "Web Image Spec Tests With Closure Compiler",
        tasks,
        tags=[GraalWebImageTags.webimagespectest, GraalWebImageTags.webimagespectest_closure],
    ) as t:
        if t:
            unittest(["--verbose", "--enable-timing", "-Dwebimage.test.closure_compiler=true"] + unittest_args)

    with Task("Check mx web-image --help", tasks, tags=[GraalWebImageTags.webimagehelp]) as t:
        if t:
            # This check works by scanning stdout for the 'Usage' keyword. If that keyword does not appear, it means something broke mx web-image --help.
            found_usage = False

            def help_stdout_check(output):
                nonlocal found_usage
                if "Usage" in output:
                    found_usage = True

            # mx web-image --help is definitely broken if a non zero code is returned.
            mx.run(["mx", "web-image", "--help"], out=help_stdout_check, nonZeroIsFatal=True)
            if not found_usage:
                mx.abort(
                    "mx web-image --help does not seem to output the proper message. "
                    "This can happen if you add extra arguments the mx web-image call without checking if an argument was --help or --help-extra."
                )

    with Task(
        "Check that ProvidedHostedOptions contains all Web Image options",
        tasks,
        tags=[GraalWebImageTags.webimageoptions],
    ) as t:
        if t:
            hosted_options_gate()


add_gate_runner(_suite, gate_runner)
# Defaults to JS backend
mx_gate.add_gate_argument("--backend", default="JS", help="Backend that should be used for this gate run")
mx_gate.add_gate_argument("--spectest", help="Test suite that should be run, only used for webimagespectest tags")
mx_gate.add_gate_argument(
    "--spectest-argument", action=mx_compiler.ShellEscapedStringAction, help="Builder flags for spectest runs"
)


def get_launcher_flags(names: [str], cp_suffix: str = None) -> [str]:
    """
    This gathers all the flags (class path, module path, etc.) needed to compile the given names
    (distributions, projects) with web image.

    Many internal distributions directly or indirectly depend on jars in the image builder itself
    (e.g. svm-wasm.jar), which cannot be passed to the launcher again.
    Because of that we omit all the flags required for the image builder jars and their dependencies (since the image
    builder will already have the proper paths set up for those).

    :param names:
    :return: A list of commandline flags for the web-image launcher
    """
    builder_jars = WebImageConfiguration.get_graalvm_component().builder_jar_distributions
    return mx.get_runtime_jvm_args(names, cp_suffix=cp_suffix, exclude_names=builder_jars)


class WebImageUnittestConfig(mx_unittest.MxUnittestConfig):
    def __init__(self):
        super().__init__("web-image")

    def apply(self, config):
        vm_args, main_class, main_class_args = config

        vm_args += ["-Dwebimage.test.js=" + _web_image_js_engine_name]
        vm_args += ["-Dwebimage.test.launcher=" + vm_web_image_path()]
        vm_args += ["-Dwebimage.test.flags=" + ",".join(get_launcher_flags(WebImageConfiguration.test_cases))]
        # If any of the arguments contains spaces and double quotes, on Windows it will add its own quotes around
        # the argument but not escape the inner quotes. This will be interpreted by Windows as separate arguments.
        # For example if the command line arguments are (as Java code):
        # cmd = ["prg", "print(\"hello world\")"]
        # That's, on Windows, converted to the commandline (the Windows API does not use a list of arguments but a single command string):
        # prg "print("hello world")"
        # Which is interpreted as multiple arguments.
        # This system property ensures that quotes inside arguments are properly escaped.
        vm_args += ["-Djdk.lang.Process.allowAmbiguousCommands=false"]
        vm_args += locale_US_args()

        # The compiler suite imposes a maximum test execution time, which may not
        # be enough for Web Image tests that build and run large images.
        # We increase the limit to 10 mins if it's currently lower than that.
        max_test_time = 10 * 60
        previous_entry = None
        for idx, entry in enumerate(main_class_args):
            if previous_entry == "-JUnitMaxTestTime" and entry.isdigit():
                limit = int(entry)
                if limit < max_test_time:
                    main_class_args[idx] = str(max_test_time)
                    mx.log(f"{self.name}: increased -JUnitMaxTestTime from {limit} to {max_test_time}")
            previous_entry = entry

        return vm_args, main_class, main_class_args


mx_unittest.register_unittest_config(WebImageUnittestConfig())


class WebImageMacroBuilder(mx.ArchivableProject):
    """
    Builds a ``native-image.properties`` file that contains the configuration needed to run the Web Image builder in the
    Native Image launcher.

    This includes adding additional builder jars, additional builder arguments, and additional exports to the builder JVM.
    """

    def __init__(
        self,
        suite: mx.Suite,
        name: str,
        builder_dists: List[str],
        macro_location: str,
        java_args: Optional[List[str]] = None,
        image_provided_jars: Optional[List[str]] = None,
        provided_hosted_options: Optional[List[str]] = None,
    ):
        """
        :param builder_dists: Distributions included for the builder. Exactly these are added to
        ``ImageBuilderModulePath`` and their required exports to ``JavaArgs``. No transitive dependencies are included,
        so all distributions must be named explicitly.
        :param macro_location: Path to the macro directory. Generated paths in the macro will be relative to this path
        :param java_args: Additional flags to add to ``JavaArgs``
        :param image_provided_jars: Distributions that should be added to ``ImageProvidedJars``
        :param provided_hosted_options: Options that should be added to ``ProvidedHostedOptions``
        """
        super().__init__(suite, name, [], None, None, buildDependencies=builder_dists)
        self.builder_dists = builder_dists
        self.macro_location = macro_location
        self.java_args = java_args or []
        self.image_provided_jars = image_provided_jars or []
        self.provided_hosted_options = provided_hosted_options or []

    def output_dir(self):
        return self.get_output_root()

    def archive_prefix(self):
        return ""

    def getBuildTask(self, args):
        return WebImageMacroBuildTask(self, args)

    def get_file_path(self):
        return os.path.join(self.output_dir(), "native-image.properties")

    def getResults(self):
        return [self.get_file_path()]


class WebImageMacroBuildTask(mx.ArchivableBuildTask):
    """
    Build Task for :class:`WebImageMacroBuilder`.

    This technically always does the work of creating the ``native-image.properties`` file, but if the generated file
    contents are the same as in the existing file, it will not write anything and will tell the build system that it
    does not need to build anything.
    This avoids dependents of this project to be rebuilt if the file hasn't changed.
    """

    # The instance field is set in a super-class. This is just here to get better code completion
    subject: WebImageMacroBuilder

    def __init__(self, subject: WebImageMacroBuilder, args):
        super().__init__(subject, args, 1)
        # Cached lines for the native-image.properties file
        self._computed_lines: Optional[List[str]] = None

    def __str__(self):
        return f"Building Native Image macro to add {self.subject.builder_dists} to builder"

    def needsBuild(self, newestInput):
        file_path = self.subject.get_file_path()
        ts_file = TimeStampFile(file_path)

        if newestInput and ts_file.isOlderThan(newestInput):
            return True, f"{ts_file} is older than {newestInput}"

        if not Path(file_path).exists():
            return True, f"{file_path} already exists"

        # We are already computing the file contents here so that we only return True if we would produce different file
        # contents. This way, things that depend on this project are only rebuilt if this dependency really changed
        with open(file_path, "r") as existing_file:
            if existing_file.read() != "\n".join(self.get_or_compute_lines()):
                return True, "Generated native-image.properties file will be different"

        return False, "File already exists and would not change"

    def _macro_relative_paths(self, dists: List[Union[str, mx.Dependency]]) -> List[str]:
        """
        Computes the paths of exactly the given distribution jars (without transitive dependencies) relative to the
        macro folder.
        Those paths can be used in the macro with the ``${.}`` syntax.
        """
        include: List[mx.dependency] = [mx.dependency(d) for d in dists]
        # We want only the classpath entries for exactly the dists jars and none of their transitive
        # dependencies, so we exclude all transitive dependencies from the classpath lookup
        exclude = mx.classpath_entries(include, includeSelf=False)
        # Calculate classpath relative to the macro directory. These paths can be referenced in the macro using
        # the ${.} syntax.
        return mx_sdk_vm_impl.graalvm_home_relative_classpath(
            include, start=self.subject.macro_location, exclude_names=exclude
        ).split(os.pathsep)

    @staticmethod
    def _path_for_macro(macro_relative_path: str) -> str:
        return "${.}/" + macro_relative_path.replace(os.sep, "/")

    @classmethod
    def _escaped_macro_paths(cls, macro_relative_paths: List[str]) -> str:
        """
        Converts the given lists of paths, which are relative to the macro folder to a colon-separated path spec using
        the ``${.}`` syntax.
        """
        return mx_sdk_vm_impl.java_properties_escape(":".join((cls._path_for_macro(e) for e in macro_relative_paths)))

    def get_or_compute_lines(self) -> List[str]:
        if self._computed_lines is None:
            builder_jars: List[mx.Dependency] = [mx.dependency(d) for d in self.subject.builder_dists]

            lines: List[str] = [
                "# This file is auto-generated",
                "ExcludeFromAll = true",
                "ProvidedHostedOptions = " + " ".join(self.subject.provided_hosted_options),
            ]

            if builder_jars:
                # Adds the builder jars
                lines.append(
                    "ImageBuilderModulePath = " + self._escaped_macro_paths(self._macro_relative_paths(builder_jars))
                )

            if self.subject.image_provided_jars:
                # These jars are added to the module path passed to the builder (not the builder's JVM)
                # It only differs from ImageModulePath in that the latter will not result in "." being added to the
                # classpath if the user doesn't specify any module or class path. This can be unexpected for users,
                # which is why ImageProvidedJars is used, which does not have this behavior.
                lines.append(
                    "ImageProvidedJars = "
                    + self._escaped_macro_paths(self._macro_relative_paths(self.subject.image_provided_jars))
                )

            # Required exports for the additional builder jars
            required_exports = mx_javamodules.requiredExports(builder_jars, mx.get_jdk())
            exports_flags = mx_sdk_vm.AbstractNativeImageConfig.get_add_exports_list(required_exports)

            java_args = self.subject.java_args + exports_flags

            if java_args:
                # Provided flags and required --add-export flags passed to the JVM running the builder
                lines.append("JavaArgs = \\")

                for flag in java_args:
                    lines.append("  " + mx_sdk_vm_impl.java_properties_escape(flag) + " \\")

            self._computed_lines = lines

        return self._computed_lines

    def build(self):
        mx_util.ensure_dir_exists(self.subject.output_dir())
        with open(self.subject.get_file_path(), "w") as properties_file:
            properties_file.write("\n".join(self.get_or_compute_lines()))

        return True

    def clean(self, forBuild=False):
        if os.path.exists(self.subject.output_dir()):
            mx.rmtree(self.subject.output_dir())


def create_web_image_macro_builder(
    defining_suite,
    name: str,
    component: mx_sdk_vm.GraalVmComponent,
    builder_jars: List[str],
    java_args: Optional[List[str]] = None,
    provided_hosted_options: Optional[List[str]] = None,
) -> WebImageMacroBuilder:
    """
    Creates a :class:`WebImageMacroBuilder`.

    Assumes the given component has a single ``support_distribution`` that holds the ``native-image.properties`` file.
    This is required in order to locate the macro in the build directory.

    If the component (and thus the support distribution) is not included in the graalvm distribution, the macro is
    useless since it only makes sense inside a graalvm distribution. And thus, we just generate an empty macro
    """
    assert (
        len(component.support_distributions) == 1
    ), f"Component {component.short_name} does not have exactly one support_distributions. This code assumes it only has one and uses it to locate the macro directory"

    graalvm_dist = mx_sdk_vm_impl.get_final_graalvm_distribution()

    if component not in graalvm_dist.components:
        # If no macro path was found, the macro is useless anyway (since it's not included in a graalvm distribution).
        # Since things depend on this project, we just produce an empty macro
        return WebImageMacroBuilder(defining_suite, name, [], graalvm_dist.jdk_base)
    else:
        # It seems we can't look up the path of the component itself. Instead, we use the support distribution,
        # which is placed at the macro root, to look up the macro location
        macro_path = graalvm_dist.find_single_source_location(
            f"extracted-dependency:{component.support_distributions[0]}", abort_on_multiple=True
        )

        return WebImageMacroBuilder(
            defining_suite,
            name,
            builder_jars,
            macro_path,
            java_args=java_args,
            image_provided_jars=component.jar_distributions,
            provided_hosted_options=provided_hosted_options,
        )


# Defines svm-wasm tool macro that is used to enable the Wasm backend in Native Image
# The macro (native-image.properties) is auto-generated in WebImageMacroBuilder.
svm_wasm_macro = mx_sdk_vm.GraalVmSvmTool(
    suite=_suite,
    name="Native Image Wasm Backend",
    short_name=svm_wasm_component,
    dir_name="svm-wasm",
    license_files=[],
    third_party_license_files=[],
    jar_distributions=[],
    priority=0,
    builder_jar_distributions=[
        "web-image:SVM_WASM",
        "web-image:SVM_WASM_JIMFS",
        "web-image:SVM_WASM_GUAVA",
    ],
    support_distributions=["web-image:NATIVE_IMAGE_WASM_SUPPORT"],
)
mx_sdk_vm.register_graalvm_component(svm_wasm_macro)

web_image_macro = mx_sdk_vm.GraalVmSvmTool(
    suite=_suite,
    name="Web Image",
    short_name=web_image_component,
    dir_name="web-image",
    license_files=[],
    third_party_license_files=[],
    dependencies=["ni"],
    jar_distributions=[
        "web-image:WEBIMAGE_LIBRARY_SUPPORT",
    ],
    builder_jar_distributions=web_image_builder_jars,
    support_distributions=["web-image:WEBIMAGE_DRIVER_SUPPORT"],
    launcher_configs=[
        mx_sdk_vm.LauncherConfig(
            use_modules="image",
            main_module="org.graalvm.extraimage.driver",
            destination="bin/<exe:web-image>",
            jar_distributions=["web-image:WEBIMAGE_DRIVER", "web-image:SVM_WASM"],
            main_class=web_image_main_class(),
            build_args=[],
        ),
    ],
    jlink=False,
)
mx_sdk_vm.register_graalvm_component(web_image_macro)


def mx_register_dynamic_suite_constituents(register_project, register_distribution):
    register_project(
        create_web_image_macro_builder(
            _suite,
            "web-image-macro-builder",
            web_image_macro,
            WebImageConfiguration.get_builder_jars(),
            java_args=[
                # Tell the builder that it was started from the web-image launcher
                "-Dcom.oracle.graalvm.iswebimage=true",
                # The closure compiler is only an optional dependency and as such is not part of the
                # module graph by default (even if it is on the module path), it has to be added
                # explicitly using `--add-modules`.
                "--add-modules=org.graalvm.extraimage.closurecompiler",
                "--add-modules=org.graalvm.wrapped.google.closure",
            ]
            + ["--add-modules=" + module for module in WebImageConfiguration.get_additional_modules()],
        )
    )

    wasm_component = WebImageConfiguration.get_svm_wasm_component()
    register_project(
        create_web_image_macro_builder(
            _suite,
            "svm-wasm-macro-builder",
            wasm_component,
            wasm_component.builder_jar_distributions,
            provided_hosted_options=WebImageConfiguration.get_hosted_options(),
        )
    )


# This callback is essential for mx to generate the manifest file so that the ServiceLoader can find
# the OptionDescripter defined in Web Image
def mx_post_parse_cmd_line(opts):
    for dist in _suite.dists:
        if not dist.isTARDistribution():
            dist.set_archiveparticipant(GraalArchiveParticipant(dist, isTest=dist.name.endswith("_TEST")))
