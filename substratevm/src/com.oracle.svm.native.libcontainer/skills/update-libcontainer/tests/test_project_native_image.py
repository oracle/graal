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

import pathlib
import sys
import tempfile
import unittest


SKILL_ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SKILL_ROOT / "scripts"))

import project_native_image


class ProjectLinesTest(unittest.TestCase):
    def project(self, source):
        return "".join(project_native_image.project_lines(source.splitlines(keepends=True)))

    def test_removes_ifndef_branch(self):
        source = "before\n#ifndef NATIVE_IMAGE\nhotspot\n#endif // !NATIVE_IMAGE\nafter\n"
        self.assertEqual("before\nafter\n", self.project(source))

    def test_keeps_ifdef_branch(self):
        source = "before\n#ifdef NATIVE_IMAGE\nnative image\n#endif // NATIVE_IMAGE\nafter\n"
        self.assertEqual("before\nnative image\nafter\n", self.project(source))

    def test_selects_else_branches(self):
        source = (
            "#ifdef NATIVE_IMAGE\n"
            "native ifdef\n"
            "#else\n"
            "hotspot ifdef\n"
            "#endif\n"
            "#ifndef NATIVE_IMAGE\n"
            "hotspot ifndef\n"
            "#else\n"
            "native ifndef\n"
            "#endif\n"
        )
        self.assertEqual("native ifdef\nnative ifndef\n", self.project(source))

    def test_preserves_nested_unrelated_conditionals(self):
        source = (
            "#ifndef NATIVE_IMAGE\n"
            "#if HOTSPOT\n"
            "excluded\n"
            "#endif\n"
            "#else\n"
            "#if defined(LINUX)\n"
            "linux\n"
            "#else\n"
            "other\n"
            "#endif // LINUX\n"
            "#endif // !NATIVE_IMAGE\n"
        )
        expected = "#if defined(LINUX)\nlinux\n#else\nother\n#endif // LINUX\n"
        self.assertEqual(expected, self.project(source))

    def test_projects_native_image_conditionals_inside_unrelated_conditions(self):
        source = (
            "#if OUTER\n"
            "#ifdef NATIVE_IMAGE\n"
            "kept\n"
            "#else\n"
            "removed\n"
            "#endif\n"
            "#endif // OUTER\n"
        )
        self.assertEqual("#if OUTER\nkept\n#endif // OUTER\n", self.project(source))

    def test_rejects_native_image_expression(self):
        source = "#if defined(NATIVE_IMAGE)\nnative image\n#endif\n"
        with self.assertRaisesRegex(
            project_native_image.ProjectionError, "unsupported NATIVE_IMAGE conditional"
        ):
            self.project(source)

    def test_rejects_native_image_elif(self):
        source = "#if OTHER\nother\n#elif defined(NATIVE_IMAGE)\nnative image\n#endif\n"
        with self.assertRaisesRegex(
            project_native_image.ProjectionError, "unsupported NATIVE_IMAGE conditional"
        ):
            self.project(source)

    def test_rejects_unbalanced_conditionals(self):
        with self.assertRaisesRegex(project_native_image.ProjectionError, "unterminated #ifdef"):
            self.project("#ifdef NATIVE_IMAGE\nnative image\n")
        with self.assertRaisesRegex(project_native_image.ProjectionError, "unmatched #endif"):
            self.project("#endif\n")


class ProjectTreeTest(unittest.TestCase):
    def test_copies_tree_and_projects_source_files(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary = pathlib.Path(temporary_directory)
            source = temporary / "source"
            source.mkdir()
            (source / "example.cpp").write_text(
                "#ifndef NATIVE_IMAGE\nremoved\n#else\nkept\n#endif\n", encoding="utf-8"
            )
            (source / "data.txt").write_text("unchanged\n", encoding="utf-8")
            destination = temporary / "destination"

            project_native_image.copy_source(source.resolve(), destination.resolve())
            changed = sum(
                project_native_image.project_file(path)
                for path in project_native_image.source_files(destination)
            )

            self.assertEqual(1, changed)
            self.assertEqual("kept\n", (destination / "example.cpp").read_text(encoding="utf-8"))
            self.assertEqual(
                "unchanged\n", (destination / "data.txt").read_text(encoding="utf-8")
            )

    def test_current_libcontainer_sources_use_only_supported_forms(self):
        source_root = SKILL_ROOT.parents[1] / "src"
        source_files = project_native_image.source_files(source_root)
        self.assertGreater(len(source_files), 0)
        for source_file in source_files:
            with self.subTest(source_file=source_file):
                with source_file.open("r", encoding="utf-8", newline="") as source:
                    project_native_image.project_lines(source.readlines(), str(source_file))

    def test_validates_all_files_before_writing(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary = pathlib.Path(temporary_directory)
            valid = temporary / "a.cpp"
            invalid = temporary / "b.cpp"
            original = "#ifndef NATIVE_IMAGE\nremoved\n#endif\n"
            valid.write_text(original, encoding="utf-8")
            invalid.write_text(
                "#if defined(NATIVE_IMAGE)\nunsupported\n#endif\n", encoding="utf-8"
            )

            with self.assertRaisesRegex(
                project_native_image.ProjectionError, "unsupported NATIVE_IMAGE conditional"
            ):
                project_native_image.project_files((valid, invalid))

            self.assertEqual(original, valid.read_text(encoding="utf-8"))

    def test_validates_source_before_copying_destination(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary = pathlib.Path(temporary_directory)
            source = temporary / "source.cpp"
            destination = temporary / "destination.cpp"
            source.write_text(
                "#if defined(NATIVE_IMAGE)\nunsupported\n#endif\n", encoding="utf-8"
            )

            with self.assertRaisesRegex(
                project_native_image.ProjectionError, "unsupported NATIVE_IMAGE conditional"
            ):
                project_native_image.main((str(source), str(destination)))

            self.assertFalse(destination.exists())


if __name__ == "__main__":
    unittest.main()
