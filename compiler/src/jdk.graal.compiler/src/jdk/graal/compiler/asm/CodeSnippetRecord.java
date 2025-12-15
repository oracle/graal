/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.asm;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.code.CompilationResult.CompilationResultWatermark;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.site.ImplicitExceptionDispatch;
import jdk.vm.ci.code.site.Site;

/**
 * Records a snippet of emitted assembly. The recorded snippet can be replayed, i.e., inserted into
 * the code buffer at different positions, multiple times. On each replay, the snippet is patched
 * based on its insertion position.
 *
 * If a slow path is added via {@link LIR#addSlowPath} during recording, it will not be duplicated
 * during replay, as the replay only copies the exact bytes between {@link #codeStart} and
 * {@link #codeEnd}. As a result, all replayed snippets will jump to the same slow path. The slow
 * path may jump back to the originally recorded snippet (e.g., in the G1 write barrier, or ZGC load
 * and write barriers). This behavior is semantically correct because each replayed snippet is a
 * complete replica. However, it may reduce the effectiveness of branch prediction optimizations
 * introduced by the assembly replay mechanism. To preserve potential branch predictor benefits,
 * {@link LIR#addSlowPath} should be reserved exclusively for genuine slow paths that are
 * infrequently taken and do not usually jump back to the original snippet.
 */
public final class CodeSnippetRecord {
    private int codeStart;
    private int codeEnd;
    private EconomicMap<Integer, Label> codePatchLabels;

    /**
     * Watermarks represent the sizes of recorded or pending {@link Site}s essential for execution,
     * e.g., {@link ImplicitExceptionDispatch}. By capturing watermarks at the start and end of code
     * execution, we can identify the {@link Site}s appended during recording and replicate them
     * during replay.
     */
    private CompilationResultWatermark watermarkAtCodeStart;
    private CompilationResultWatermark watermarkAtCodeEnd;

    CodeSnippetRecord(int codeStart, CompilationResultWatermark watermark) {
        this.codeStart = codeStart;
        this.codeEnd = -1;
        this.watermarkAtCodeStart = watermark;
        this.watermarkAtCodeEnd = null;
        this.codePatchLabels = EconomicMap.create();
    }

    int getCodeStart() {
        return codeStart;
    }

    boolean isRecording() {
        return codeStart != -1 && codeEnd == -1;
    }

    void stopRecording(int pos, CompilationResultWatermark watermark) {
        this.codeEnd = pos;
        this.watermarkAtCodeEnd = watermark;

        // cleanup code patches branching within the current record
        var cursor = codePatchLabels.getEntries();
        while (cursor.advance()) {
            Label label = cursor.getValue();
            if (label.isBound() && isWithinRecord(label)) {
                cursor.remove();
            }
        }
    }

    void addPatch(int branchPosition, Label label) {
        codePatchLabels.put(branchPosition, label);
    }

    /**
     * On AMD64, unbound labels use the start of an instruction as the patching positions. When
     * these labels are bound, we need to adjust their patching positions.
     */
    void updatePatch(int patchPosition, int offset) {
        if (isWithinRecord(patchPosition) && codePatchLabels.containsKey(patchPosition)) {
            Label label = codePatchLabels.removeKey(patchPosition);
            codePatchLabels.put(patchPosition + offset, label);
        }
    }

    boolean isWithinRecord(int pos) {
        return codeStart <= pos && pos < codeEnd;
    }

    boolean isWithinRecord(Label label) {
        return isWithinRecord(label.position());
    }

    /**
     * Emits the same bytes from {@link #codeStart} to {@link #codeEnd}, patches label-based
     * instructions if any, and duplicates the {@link jdk.vm.ci.code.site.Site} recorded in between
     * with offset.
     */
    void replayRecording(CompilationResultBuilder crb, Assembler<?> asm) {
        for (int pos = codeStart; pos < codeEnd; pos++) {
            asm.emitByte(asm.getByte(pos));
        }
        int offset = asm.position() - codeEnd;
        var cursor = codePatchLabels.getEntries();
        while (cursor.advance()) {
            int pos = cursor.getKey() + offset;
            Label label = cursor.getValue();
            if (label.isBound()) {
                if (!isWithinRecord(label)) {
                    asm.patchRelativeJumpTarget(pos, -offset);
                }
            } else {
                label.addPatchAt(pos, asm);
            }
        }
        crb.duplicateSites(codeStart, watermarkAtCodeStart, codeEnd, watermarkAtCodeEnd, offset);
    }
}
