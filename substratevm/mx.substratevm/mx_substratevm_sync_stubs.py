#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

from pathlib import Path

import mx

SNIPPET_ANNOTATION = '@Snippet'
RUNTIME_CHECKED_SUFFIX = 'RTC'
RUNTIME_CHECKED_FEATURES = 'Stubs.getRuntimeCheckedCPUFeatures()'
SNIPPET_FILES = [
    (
        'compiler/src/org.graalvm.compiler.hotspot.amd64/src/org/graalvm/compiler/hotspot/amd64/AMD64CalcStringAttributesStub.java',
        'substratevm/src/com.oracle.svm.graal/src/com/oracle/svm/graal/stubs/SVMAMD64CalcStringAttributesForeignCalls.java'
    ), (
        'compiler/src/org.graalvm.compiler.hotspot.amd64/src/org/graalvm/compiler/hotspot/amd64/AMD64ArrayEqualsWithMaskStub.java',
        'substratevm/src/com.oracle.svm.graal/src/com/oracle/svm/graal/stubs/SVMAMD64ArrayEqualsWithMaskForeignCalls.java'
    ), (
        'compiler/src/org.graalvm.compiler.hotspot.amd64/src/org/graalvm/compiler/hotspot/amd64/AMD64ArrayCompareToStub.java',
        'substratevm/src/com.oracle.svm.graal/src/com/oracle/svm/graal/stubs/SVMArrayCompareToForeignCalls.java'
    ), (
        'compiler/src/org.graalvm.compiler.hotspot.amd64/src/org/graalvm/compiler/hotspot/amd64/AMD64ArrayCopyWithConversionsStub.java',
        'substratevm/src/com.oracle.svm.graal/src/com/oracle/svm/graal/stubs/SVMArrayCopyWithConversionsForeignCalls.java'
    ), (
        'compiler/src/org.graalvm.compiler.hotspot.amd64/src/org/graalvm/compiler/hotspot/amd64/AMD64ArrayEqualsStub.java',
        'substratevm/src/com.oracle.svm.graal/src/com/oracle/svm/graal/stubs/SVMArrayEqualsForeignCalls.java'
    ), (
        'compiler/src/org.graalvm.compiler.hotspot.amd64/src/org/graalvm/compiler/hotspot/amd64/AMD64ArrayRegionCompareToStub.java',
        'substratevm/src/com.oracle.svm.graal/src/com/oracle/svm/graal/stubs/SVMArrayRegionCompareToForeignCalls.java'),
    (
        'compiler/src/org.graalvm.compiler.hotspot/src/org/graalvm/compiler/hotspot/ArrayIndexOfStub.java',
        'substratevm/src/com.oracle.svm.graal/src/com/oracle/svm/graal/stubs/SVMArrayIndexOfForeignCalls.java'
    ), (
        'compiler/src/org.graalvm.compiler.hotspot.amd64/src/org/graalvm/compiler/hotspot/amd64/AMD64VectorizedMismatchStub.java',
        'substratevm/src/com.oracle.svm.graal/src/com/oracle/svm/graal/stubs/SVMAMD64VectorizedMismatchForeignCalls.java'
    ),
]


def _check_file_exists(path: Path):
    if not path.exists():
        mx.abort(f'file "${path}" not found')


def _transform_hotspot_snippet_stub(snippet: str, name_suffix: str = None):
    if name_suffix is not None:
        if 'ArrayIndexOfNode.optimizedArrayIndexOf' in snippet:
            snippet = snippet.replace('array, offset,', f'{RUNTIME_CHECKED_FEATURES}, array, offset,')
        else:
            snippet = snippet.replace(');', f', {RUNTIME_CHECKED_FEATURES});')
        snippet = snippet.replace('(', name_suffix + '(', 1)
    snippet = snippet.replace(
        SNIPPET_ANNOTATION,
        ('@Uninterruptible(reason = "Must not do a safepoint check.")\n'
         '    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)'))

    if name_suffix is not None:
        snippet = snippet.replace(
            '{\n',
            ('{\n'
             '        RuntimeCPUFeatureRegion region = RuntimeCPUFeatureRegion.enterSet(%s);\n'
             '        try {\n    ') % RUNTIME_CHECKED_FEATURES).replace(
            '}',
            ('    } finally {\n'
             '            region.leave();\n'
             '        }\n'
             '    }'))
    return '\n' + snippet + '\n'


def _check_graal_stubs_synced(suite):
    base_path = Path(suite.vc_dir)
    target_files = [(src, dst, mx.FileInfo(base_path / Path(dst))) for src, dst in SNIPPET_FILES]
    _sync_graal_stubs(suite)
    ret = 0
    for src, dst, fi in target_files:
        if fi.update(False, True):
            diffs = ''.join(fi.diff)
            mx.log(f' - source file {src}:')
            mx.log(f' - changes in dest file {dst}:')
            mx.log(diffs)
            ret = 1
    return ret


def _sync_graal_stubs(suite):
    base_path = Path(suite.vc_dir)
    marker_begin = 'GENERATED CODE BEGIN'
    marker_end = 'GENERATED CODE END'
    for src_rel, dst_rel in SNIPPET_FILES:
        src = base_path / Path(src_rel)
        dst = base_path / Path(dst_rel)
        _check_file_exists(src)
        _check_file_exists(dst)
        content = dst.read_text()
        i_begin = content.find(marker_begin)
        i_end = content.find(marker_end)
        if i_begin < 0:
            mx.abort(f'could not find insertion marker "{marker_begin}" in ${dst}')
        if i_end < 0:
            mx.abort(f'could not find end of insertion marker "{marker_begin}" in ${dst}')
        generated = ''
        generated_runtime_checked = ''
        content_src = src.read_text()
        pos = content_src.find(SNIPPET_ANNOTATION)
        while pos >= 0:
            start = content_src.rfind('\n', 0, pos) + 1
            end = content_src.find('}', pos) + 1
            generated += _transform_hotspot_snippet_stub(content_src[start:end])
            generated_runtime_checked += _transform_hotspot_snippet_stub(content_src[start:end], RUNTIME_CHECKED_SUFFIX)
            pos = content_src.find(SNIPPET_ANNOTATION, pos + len(SNIPPET_ANNOTATION))
        before_generated = content[0:content.find('\n', i_begin) + 1]
        after_generated = content[content.rfind('\n', i_begin, i_end):]
        comment = f'\n    // GENERATED FROM:\n    // {src.relative_to(base_path)}\n    // BY: "mx svm-sync-graal-stubs"\n'
        new_text = before_generated + comment + generated + generated_runtime_checked + after_generated
        dst.write_text(new_text)
