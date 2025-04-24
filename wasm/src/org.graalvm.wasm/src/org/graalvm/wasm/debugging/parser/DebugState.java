/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.debugging.parser;

import java.nio.file.Path;
import java.util.List;

import org.graalvm.wasm.debugging.DebugLineMap;

/**
 * State of the line state machine as defined in the Dwarf Debugging Information Format version 4.
 */
@SuppressWarnings("unused")
public class DebugState {
    private int pc = 0;
    private int opIndex = 0;
    private int file = 1;
    private int line = 1;
    private int column = 0;
    private boolean isStatement;
    private boolean basicBlock = false;
    private boolean endSequence = false;
    private boolean prologueEnd = false;
    private boolean epilogueBegin = false;
    private int isa = 0;
    private int discriminator = 0;
    private boolean ignore = false;

    private final int lineBase;
    private final int lineRange;
    private final int opcodeBase;
    private final int minInstrLength;
    private final int maxOpsPerInstr;
    private final boolean initialIsStatement;

    private final int length;
    private final DebugLineMap[] lineMaps;

    private final List<Path> filePaths;

    public DebugState(boolean initialIsStatement, int lineBase, int lineRange, int opcodeBase, int minInstrLength, int maxOpsPerInstr, int length, List<Path> filePaths) {
        assert filePaths.size() > 1;
        this.initialIsStatement = initialIsStatement;
        this.lineBase = lineBase;
        this.lineRange = lineRange;
        this.opcodeBase = opcodeBase;
        this.minInstrLength = minInstrLength;
        this.maxOpsPerInstr = maxOpsPerInstr;
        this.length = length;
        this.filePaths = filePaths;
        this.lineMaps = new DebugLineMap[filePaths.size()];
        this.lineMaps[file] = new DebugLineMap(filePaths.get(file));
        reset();
    }

    public int length() {
        return length;
    }

    private void reset() {
        pc = 0;
        opIndex = 0;
        file = 1;
        line = 1;
        column = 0;
        isStatement = initialIsStatement;
        basicBlock = false;
        endSequence = false;
        prologueEnd = false;
        epilogueBegin = false;
        isa = 0;
        discriminator = 0;
        ignore = false;
    }

    public void addRow() {
        if (!ignore) {
            lineMaps[file].add(pc, line);
        }
        discriminator = 0;
        basicBlock = false;
        prologueEnd = false;
        epilogueBegin = false;
    }

    public void advancePc(int operationAdvance) {
        advance(operationAdvance);
    }

    public void advanceLine(int lineAdvance) {
        line += lineAdvance;
    }

    public void setFile(int f) {
        file = f;
        if (lineMaps[file] == null) {
            lineMaps[file] = new DebugLineMap(filePaths.get(file));
        }
    }

    public void setColumn(int c) {
        column = c;
    }

    public void negateStatement() {
        isStatement = !isStatement;
    }

    public void setBasicBlock() {
        basicBlock = true;
    }

    public void addConstantPc() {
        int adjustedOpcode = 255 - opcodeBase;
        advance(Integer.divideUnsigned(adjustedOpcode, lineRange));
    }

    public void addFixedPc(int advance) {
        pc += advance;
        opIndex = 0;
    }

    public void setPrologueEnd() {
        prologueEnd = true;
    }

    public void setEpilogueBegin() {
        epilogueBegin = true;
    }

    public void setIsa(int i) {
        isa = i;
    }

    public void setEndSequence() {
        endSequence = true;
        addRow();
        reset();
    }

    public void setAddress(int a) {
        pc = a;
        opIndex = 0;
    }

    public void setDiscriminator(int d) {
        discriminator = d;
    }

    public void setIgnore() {
        ignore = true;
    }

    public void specialOpcode(int opcode) {
        int adjustedOpcode = opcode - opcodeBase;
        advance(Integer.divideUnsigned(adjustedOpcode, lineRange));
        line += lineBase + Integer.remainderUnsigned(adjustedOpcode, lineRange);
        addRow();
        basicBlock = false;
        prologueEnd = false;
        epilogueBegin = false;
        discriminator = 0;
    }

    private void advance(int operationAdvance) {
        pc += (int) ((long) minInstrLength * Integer.divideUnsigned((opIndex + operationAdvance), maxOpsPerInstr));
        opIndex = Integer.remainderUnsigned((opIndex + operationAdvance), maxOpsPerInstr);
    }

    public DebugLineMap[] lineMaps() {
        return lineMaps;
    }
}
