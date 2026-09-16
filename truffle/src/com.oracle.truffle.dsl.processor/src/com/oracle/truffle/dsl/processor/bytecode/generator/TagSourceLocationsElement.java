/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.bytecode.generator;

import static com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.addJavadoc;
import static com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.SourceInfoTable.emitDecodeVarintEntry;
import static com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeRootNodeElement.SourceInfoTable.emitInitCompressedSourceIterationVariables;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.Arrays;
import java.util.Set;

import javax.lang.model.element.ElementKind;

import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

/**
 * Generates a lookup index mapping instrumentation tag entry BCIs to source-table offsets. Builds
 * the index in a single pass over the source table, avoiding a separate scan for each tag BCI.
 * <p>
 * Each input instrumentation tag node has an entry bytecode index (BCI). Lookup depends only on
 * this BCI, not on the node's tag types, so nodes with the same BCI share a lookup entry. Each
 * source table entry describes a range of BCIs and the corresponding source section. If several
 * source entries cover a BCI, the first one in table order must win, just as it does in the
 * ordinary source lookup.
 * <p>
 * The input excludes the synthetic root tag node, whose entry BCI is -1. Unreachable tag nodes use
 * the builder's current, nonnegative BCI. Because unreachable code emits no instructions, several
 * nodes can share that BCI. These duplicate input BCIs are removed when building the index.
 * <p>
 * The generated constructor builds the index as follows:
 * <ol>
 * <li>Sort and deduplicate the tag BCIs. Create a parallel array of source offsets, initially all
 * -1 (no match), with one slot per distinct BCI.</li>
 * <li>Walk the source table in its original order. For each source range, use binary search to find
 * the first indexed BCI that is at least the range's start.</li>
 * <li>Assign this source entry's offset to each still-unassigned BCI before the range's exclusive
 * end. BCIs already assigned by an earlier entry keep their existing offset.</li>
 * </ol>
 * The important optimization is how step 3 skips BCIs that already have an answer. Think of the
 * sorted array as a to-do list. Initially, every distinct BCI is on the list. Once a BCI is
 * assigned, it is effectively removed: its slot in {@code nextUnassigned} points forward to another
 * candidate instead of to itself. Searching for the next unassigned BCI follows these links. It
 * also rewrites the links along the way to point directly to the result, so later searches can skip
 * the same completed stretch in one step. This shortcutting is called path compression. An extra
 * slot at the end represents "no BCIs left"; it does not correspond to a real BCI.
 * <p>
 * For example, suppose the tag BCIs are 2, 5, and 8, and the source entries cover [4, 7) followed
 * by [0, 10). The first entry assigns BCI 5. The second assigns BCIs 2 and 8, skipping BCI 5 so
 * that its more specific, earlier match is preserved. The same skipping avoids repeatedly visiting
 * completed stretches when many enclosing source ranges overlap. In the diagrams below, A and B
 * denote the offsets of those two source entries, not BCIs:
 *
 * <pre>
 * Source table order:  A: [4, 7)    B: [0, 10)
 *
 * Retained index after processing both entries:
 *
 * array index i       0       1       2
 *                 +-------+-------+-------+
 * tagBcis         |   2   |   5   |   8   |
 *                 +-------+-------+-------+
 * sourceOffsets   |   B   |   A   |   B   |
 *                 +-------+-------+-------+
 *
 * Temporary skip links during construction:
 *
 * array index i       0       1       2       3 (sentinel)
 *                 +-------+-------+-------+-------+
 * initially       |   0   |   1   |   2   |   3   |
 *                 +-------+-------+-------+-------+
 * after A         |   0   |   2   |   2   |   3   |
 *                 +-------+-------+-------+-------+
 * after B         |   2   |   2   |   3   |   3   |
 *                 +-------+-------+-------+-------+
 *
 * Each cell above is nextUnassigned[i], an ARRAY INDEX, not a BCI.
 * After A, index 1 (BCI 5) points to index 2 (BCI 8), skipping its assigned slot.
 * After B, following links from index 0 gives: 0 -> 2 -> 3 (no work left).
 * A subsequent findUnassigned(0) shortens that path to: 0 -> 3.
 * </pre>
 *
 * The sentinel has no corresponding slot in {@code tagBcis} or {@code sourceOffsets}. Source
 * offsets are byte indices into compressed source data or int indices into uncompressed source
 * data; -1 means no source entry matched that BCI.
 * <p>
 * Each distinct BCI receives an offset at most once. After construction, the temporary skip links
 * are discarded. The owning tag root can then binary-search the sorted BCIs and decode the recorded
 * source entry on demand, without retaining a separate source-section object for every index slot.
 */
final class TagSourceLocationsElement extends AbstractElement {

    TagSourceLocationsElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "SourceLocations");
        addJavadoc(this, """
                        Immutable lookup index shared by the instrumentation tags of a tag root.
                        Maps tag entry BCIs to the first matching source table entry, without creating
                        source sections eagerly. Constructed lazily and published by the owning tag root.
                        """);
        addJavadoc(this.add(new CodeVariableElement(Set.of(FINAL), parent.sourceInfoTable.getSourceInfoType(), "sourceInfo")), """
                        Source table indexed by this lookup. Its identity is the cache key: a replacement
                        table requires a new index because the BCI ranges or entry offsets may have changed.
                        """);
        addJavadoc(this.add(new CodeVariableElement(Set.of(FINAL), arrayOf(type(int.class)), "tagBcis")), """
                        Sorted, distinct, nonnegative tag entry BCIs.
                        This array is not modified after construction and is parallel to {@code sourceOffsets}.
                        """);
        addJavadoc(this.add(new CodeVariableElement(Set.of(FINAL), arrayOf(type(int.class)), "sourceOffsets")), """
                        Source table offsets corresponding to {@code tagBcis}, or -1 if no entry matches.
                        Compressed offsets are byte indices pointing to the source-index varint, after
                        the entry length and BCI range. Uncompressed offsets are int indices pointing
                        to the start of an entry. These are the offsets expected by {@code createSourceSection}.
                        This array is not modified after construction.
                        """);
        this.add(createConstructor());
        this.add(createAssignSource());
        this.add(createFindUnassigned());
    }

    private CodeExecutableElement createConstructor() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(), null, this.getSimpleName().toString());
        ex.addParameter(new CodeVariableElement(parent.abstractBytecodeNode.asType(), "bytecode"));
        ex.addParameter(new CodeVariableElement(arrayOf(parent.tagNode.asType()), "tags"));
        addJavadoc(ex, """
                        Resolves source offsets for all tag BCIs in bulk. Visits source entries in table
                        order and assigns each distinct BCI at most once, preserving first-match semantics for
                        nested, overlapping, and equal ranges. A temporary path-compressed successor
                        structure skips already assigned BCIs instead of repeatedly visiting them.
                        <p>
                        Unreachable tag nodes can share the builder's current, nonnegative BCI because
                        unreachable code emits no instructions. Only distinct BCIs are retained.
                        The synthetic root tag node, whose entry BCI is -1, is not part of the input
                        array and is not indexed.

                        @param bytecode the bytecode node whose source table is indexed
                        @param tags the owning tag root's instrumentation tag nodes, excluding the synthetic root
                        """);
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("this.sourceInfo = bytecode.sourceInfo");
        b.statement("int[] bcis = new int[tags.length]");
        b.startFor().string("int i = 0; i < tags.length; i++").end().startBlock();
        b.statement("bcis[i] = tags[i].enterBci");
        b.end();
        b.startStatement().startStaticCall(type(Arrays.class), "sort").string("bcis").end().end();
        b.statement("int numBcis = 0");
        b.startFor().string("int bci : bcis").end().startBlock();
        b.startIf().string("numBcis == 0 || bcis[numBcis - 1] != bci").end().startBlock();
        b.statement("bcis[numBcis++] = bci");
        b.end(2);
        b.startAssign("this.tagBcis").string("numBcis == bcis.length ? bcis : ");
        b.startStaticCall(type(Arrays.class), "copyOf").string("bcis").string("numBcis").end(2);
        b.statement("this.sourceOffsets = new int[numBcis]");
        b.startStatement().startStaticCall(type(Arrays.class), "fill").string("sourceOffsets").string("-1").end().end();
        b.lineComment("A temporary structure used to speed up source offset computation by skipping BCIs that have already been assigned.");
        b.lineComment("nextUnassigned[i] == i while sourceOffsets[i] is unassigned; otherwise, it points to a later slot that may need assignment.");
        b.lineComment("findUnassigned compresses these links when it searches for the next unassigned entry.");
        b.statement("int[] nextUnassigned = new int[numBcis + 1]");
        b.startFor().string("int i = 0; i < nextUnassigned.length; i++").end().startBlock();
        b.statement("nextUnassigned[i] = i");
        b.end();
        b.declaration(parent.sourceInfoTable.getSourceInfoType(), "info", "this.sourceInfo");
        if (model().enableCompressedSources) {
            emitInitCompressedSourceIterationVariables(b, type(int.class), "index");
            b.startWhile().string("index < info.length - ").variable(parent.sourceInfoTable.footerLengthVariable).end().startBlock();
            b.statement("int entryEnd = index + (info[index++] & 0xFF)");
            emitDecodeVarintEntry(b, "info", "index");
            b.statement("int startBci = (int) decoded");
            emitDecodeVarintEntry(b, "info", "index");
            b.statement("int endBci = startBci + (int) decoded");
            b.statement("assignSource(startBci, endBci, index, nextUnassigned)");
            b.statement("index = entryEnd");
            b.end();
        } else {
            b.startFor().string("int index = 0; index < info.length; index += ").variable(parent.sourceInfoTable.entryLengthVariable).end().startBlock();
            b.declaration(type(int.class), "startBci", parent.sourceInfoTable.loadStartBci("info", "index"));
            b.declaration(type(int.class), "endBci", parent.sourceInfoTable.loadEndBci("info", "index"));
            b.statement("assignSource(startBci, endBci, index, nextUnassigned)");
            b.end();
        }
        return ex;
    }

    private CodeExecutableElement createAssignSource() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "assignSource");
        ex.addParameter(new CodeVariableElement(type(int.class), "startBci"));
        ex.addParameter(new CodeVariableElement(type(int.class), "endBci"));
        ex.addParameter(new CodeVariableElement(type(int.class), "sourceOffset"));
        ex.addParameter(new CodeVariableElement(arrayOf(type(int.class)), "nextUnassigned"));
        addJavadoc(ex, """
                        Assigns an entry's offset to unassigned BCIs in {@code [startBci, endBci)}.
                        Since {@code tagBcis} contains no duplicates, binary search finds either the
                        unique match for {@code startBci} or its insertion point. Already assigned BCIs
                        retain their first match and are skipped using {@code findUnassigned}.

                        @param startBci inclusive start of the source entry's bytecode range
                        @param endBci exclusive end of the source entry's bytecode range
                        @param sourceOffset offset accepted by {@code createSourceSection}
                        @param nextUnassigned temporary successor structure, including an end sentinel
                        """);
        CodeTreeBuilder b = ex.createBuilder();
        b.lineComment("Find the lowest array index for which startBci <= tagBcis[index], or tagBcis.length if none exists.");
        b.startDeclaration(type(int.class), "index").startStaticCall(type(Arrays.class), "binarySearch").string("tagBcis").string("startBci").end(2);
        b.startIf().string("index < 0").end().startBlock();
        b.statement("index = -index - 1");
        b.end();
        b.statement("index = findUnassigned(nextUnassigned, index)");
        b.startWhile().string("index < tagBcis.length && tagBcis[index] < endBci").end().startBlock();
        b.statement("sourceOffsets[index] = sourceOffset");
        b.statement("int next = findUnassigned(nextUnassigned, index + 1)");
        b.statement("nextUnassigned[index] = next");
        b.statement("index = next");
        b.end();
        return ex;
    }

    private CodeExecutableElement createFindUnassigned() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "findUnassigned");
        ex.addParameter(new CodeVariableElement(arrayOf(type(int.class)), "nextUnassigned"));
        ex.addParameter(new CodeVariableElement(type(int.class), "index"));
        addJavadoc(ex, """
                        Finds the first unassigned slot in {@code tagBcis} at or after {@code index}, compressing
                        the traversed path. An unassigned index points to itself; an assigned index
                        points to a later candidate. The final array element is a self-pointing sentinel.

                        @param nextUnassigned mutable successor structure used only during index construction
                        @param index starting array index, or the sentinel index
                        @return the next unassigned index, or the sentinel if none remains
                        """);
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("int result = index");
        b.startWhile().string("nextUnassigned[result] != result").end().startBlock();
        b.statement("result = nextUnassigned[result]");
        b.end();
        b.statement("int current = index");
        b.startWhile().string("current != result").end().startBlock();
        b.statement("int next = nextUnassigned[current]");
        b.statement("nextUnassigned[current] = result");
        b.statement("current = next");
        b.end();
        b.statement("return result");
        return ex;
    }
}
