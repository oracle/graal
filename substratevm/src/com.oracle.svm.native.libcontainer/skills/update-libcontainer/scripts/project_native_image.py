#
# Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import argparse
import dataclasses
import pathlib
import re
import shutil
import sys


SOURCE_SUFFIXES = frozenset((".c", ".cc", ".cpp", ".h", ".hpp"))
DIRECTIVE = re.compile(r"^\s*#\s*(ifdef|ifndef|if|elif|else|endif)\b(.*)$")
NATIVE_IMAGE_ARGUMENT = re.compile(r"^\s*NATIVE_IMAGE\s*(?://.*|/\*.*\*/\s*)?$")


class ProjectionError(Exception):
    pass


@dataclasses.dataclass
class Conditional:
    directive: str
    line_number: int
    parent_active: bool
    native_image: bool
    condition_true: bool = False
    saw_else: bool = False


def project_lines(lines, source_name="<input>"):
    output = []
    conditionals = []
    active = True

    for line_number, line in enumerate(lines, start=1):
        match = DIRECTIVE.match(line)
        if match is None:
            if active:
                output.append(line)
            continue

        directive = match.group(1)
        argument = match.group(2)

        if directive in ("ifdef", "ifndef", "if"):
            native_image = (
                directive in ("ifdef", "ifndef")
                and NATIVE_IMAGE_ARGUMENT.fullmatch(argument) is not None
            )
            if "NATIVE_IMAGE" in argument and not native_image:
                raise ProjectionError(
                    f"{source_name}:{line_number}: unsupported NATIVE_IMAGE conditional: {line.rstrip()}"
                )

            conditional = Conditional(
                directive=directive,
                line_number=line_number,
                parent_active=active,
                native_image=native_image,
                condition_true=directive == "ifdef" if native_image else False,
            )
            conditionals.append(conditional)
            if native_image:
                active = active and conditional.condition_true
            elif active:
                output.append(line)
            continue

        if not conditionals:
            raise ProjectionError(f"{source_name}:{line_number}: unmatched #{directive}")

        conditional = conditionals[-1]
        if directive == "else":
            if conditional.saw_else:
                raise ProjectionError(
                    f"{source_name}:{line_number}: duplicate #else for conditional opened "
                    f"on line {conditional.line_number}"
                )
            conditional.saw_else = True
            if conditional.native_image:
                active = conditional.parent_active and not conditional.condition_true
            else:
                active = conditional.parent_active
                if active:
                    output.append(line)
            continue

        if directive == "elif":
            if conditional.saw_else:
                raise ProjectionError(
                    f"{source_name}:{line_number}: #elif after #else for conditional opened "
                    f"on line {conditional.line_number}"
                )
            if conditional.native_image:
                raise ProjectionError(
                    f"{source_name}:{line_number}: #elif is unsupported for a NATIVE_IMAGE conditional"
                )
            if "NATIVE_IMAGE" in argument:
                raise ProjectionError(
                    f"{source_name}:{line_number}: unsupported NATIVE_IMAGE conditional: {line.rstrip()}"
                )
            active = conditional.parent_active
            if active:
                output.append(line)
            continue

        assert directive == "endif"
        conditionals.pop()
        if not conditional.native_image and conditional.parent_active:
            output.append(line)
        active = conditional.parent_active

    if conditionals:
        conditional = conditionals[-1]
        raise ProjectionError(
            f"{source_name}:{conditional.line_number}: unterminated #{conditional.directive}"
        )

    return output


def read_projection(path):
    with path.open("r", encoding="utf-8", newline="") as source:
        lines = source.readlines()
    projected = project_lines(lines, str(path))
    return lines, projected


def project_file(path):
    lines, projected = read_projection(path)
    if projected == lines:
        return False
    with path.open("w", encoding="utf-8", newline="") as destination:
        destination.writelines(projected)
    return True


def project_files(paths):
    changes = []
    for path in paths:
        lines, projected = read_projection(path)
        if projected != lines:
            changes.append((path, projected))
    for path, projected in changes:
        with path.open("w", encoding="utf-8", newline="") as destination:
            destination.writelines(projected)
    return len(changes)


def source_files(path):
    if path.is_file():
        return (path,)
    return tuple(
        candidate
        for candidate in sorted(path.rglob("*"))
        if candidate.is_file()
        and not candidate.is_symlink()
        and candidate.suffix in SOURCE_SUFFIXES
    )


def copy_source(source, destination):
    if destination.exists():
        raise ProjectionError(f"destination already exists: {destination}")
    if source.is_dir():
        try:
            destination.relative_to(source)
        except ValueError:
            pass
        else:
            raise ProjectionError("destination must not be inside the source directory")
        shutil.copytree(source, destination, symlinks=True)
    else:
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)


def parse_arguments(arguments):
    parser = argparse.ArgumentParser(
        description="Materialize the source selected when NATIVE_IMAGE is defined."
    )
    parser.add_argument("source", type=pathlib.Path, help="source file or directory")
    parser.add_argument(
        "destination", type=pathlib.Path, help="new projected file or directory"
    )
    return parser.parse_args(arguments)


def main(arguments=None):
    options = parse_arguments(arguments)
    source = options.source.resolve()
    if not source.exists():
        raise ProjectionError(f"source does not exist: {source}")

    for path in source_files(source):
        read_projection(path)
    target = options.destination.resolve()
    copy_source(source, target)

    changed = project_files(source_files(target))
    print(f"Projected {changed} file(s) for NATIVE_IMAGE under {target}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ProjectionError as error:
        print(error, file=sys.stderr)
        sys.exit(1)
