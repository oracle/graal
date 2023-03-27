#!/usr/bin/env python3
# Copyright (c) Facebook, Inc. and its affiliates. (http://www.facebook.com)

import argparse
import os
import shlex
import subprocess
import sys
import textwrap


def run(
    cmd,
    verbose=True,
    cwd=None,
    check=True,
    capture_output=False,
    encoding="utf-8",
    # Specify an integer number of seconds
    timeout=-1,
    **kwargs,
):
    if verbose:
        info = "$ "
        if cwd is not None:
            info += f"cd {cwd}; "
        info += " ".join(shlex.quote(c) for c in cmd)
        if capture_output:
            info += " >& ..."
        lines = textwrap.wrap(
            info,
            break_on_hyphens=False,
            break_long_words=False,
            replace_whitespace=False,
            subsequent_indent="  ",
        )
        print(" \\\n".join(lines))
    if timeout != -1:
        cmd = ["timeout", "--signal=KILL", f"{timeout}s", *cmd]
    try:
        return subprocess.run(
            cmd,
            cwd=cwd,
            check=check,
            capture_output=capture_output,
            encoding=encoding,
            **kwargs,
        )
    except subprocess.CalledProcessError as e:
        if e.returncode == -9:
            # Error code from `timeout` command signaling it had to be killed
            raise TimeoutError("Command timed out", cmd)
        raise


def native_image_arg(arg):
    return f"-Dnative-image.benchmark.extra-image-build-argument={arg}"


ALL_CONFIGS = {
    "1-default": ["-H:AnalysisContextSensitivity=insens"],
    "2-no-saturation": ["-H:-RemoveSaturatedTypeFlows", "-H:-AliasArrayTypeFlows"],
    "3-allocsens": [
        "-H:-RemoveSaturatedTypeFlows",
        "-H:-AliasArrayTypeFlows",
        "-H:AnalysisContextSensitivity=allocsens",
        "-H:+HybridStaticContext",
    ],
    "4-one-obj": [
        "-H:-RemoveSaturatedTypeFlows",
        "-H:-AliasArrayTypeFlows",
        "-H:AnalysisContextSensitivity=_1obj",
        "-H:+HybridStaticContext",
    ],
    "5-two-obj-one-heap": [
        "-H:-RemoveSaturatedTypeFlows",
        "-H:-AliasArrayTypeFlows",
        "-H:AnalysisContextSensitivity=_2obj1h",
        "-H:+HybridStaticContext",
    ],
}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--context-sensitive", default="1-default", choices=ALL_CONFIGS.keys()
    )
    parser.add_argument("--dump-heap", action="store_true")
    parser.add_argument("--dump-igv", action="store_true")
    parser.add_argument("--memory", help="For example, '10g'")
    parser.add_argument("--verbose", action="store_true", default=False)
    parser.add_argument(
        "benchmark",
        help="For example, 'renaissance-native-image:dec-tree' or 'console-native-image:helloworld'",
    )
    args = parser.parse_args()
    mx = os.path.expanduser("~/Documents/dev/mx/mx")
    cmd = [
        mx,
        "--env",
        "ni-ce",
        "benchmark",
        args.benchmark,
        "--",
        "--jvm=native-image",
        "--jvm-config=default-ce",
        "-Dnative-image.benchmark.stages=image",
    ]
    if args.dump_heap:
        cmd.append(native_image_arg("-H:DumpHeap=during-analysis"))
    if args.memory:
        cmd.append(native_image_arg(f"-J-Xmx{args.memory}"))
    if args.dump_igv:
        cmd.append(native_image_arg("-H:Dump=:2"))
        cmd.append(native_image_arg("-H:MethodFilter=HashMap.get"))
        cmd.append(native_image_arg("-H:PrintGraph=Network"))
    extra_args = [native_image_arg(arg) for arg in ALL_CONFIGS[args.context_sensitive]]
    cmd.extend(extra_args)
    run(cmd, verbose=args.verbose)
