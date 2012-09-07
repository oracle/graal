/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.api.code;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Represents the output from compiling a method, including the compiled machine code, associated data and references,
 * relocation information, deoptimization information, etc.
 */
public class CompilationResult implements Serializable {

    private static final long serialVersionUID = -1319947729753702434L;

    /**
     * Represents a code position with associated additional information.
     */
    public abstract static class Site implements Serializable {
        private static final long serialVersionUID = -8214214947651979102L;
        /**
         * The position (or offset) of this site with respect to the start of the target method.
         */
        public final int pcOffset;

        public Site(int pos) {
            this.pcOffset = pos;
        }
    }

    /**
     * Represents a safepoint with associated debug info.
     */
    public static class Safepoint extends Site implements Comparable<Safepoint> {
        private static final long serialVersionUID = 2479806696381720162L;
        public final DebugInfo debugInfo;

        Safepoint(int pcOffset, DebugInfo debugInfo) {
            super(pcOffset);
            this.debugInfo = debugInfo;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(pcOffset);
            sb.append("[<safepoint>]");
            appendDebugInfo(sb, debugInfo);
            return sb.toString();
        }

        @Override
        public int compareTo(Safepoint o) {
            if (pcOffset < o.pcOffset) {
                return -1;
            } else if (pcOffset > o.pcOffset) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * Represents a call in the code.
     */
    public static final class Call extends Safepoint {
        private static final long serialVersionUID = 1440741241631046954L;

        /**
         * The target of the call.
         */
        public final Object target;

        /**
         * The size of the call instruction.
         */
        public final int size;

        /**
         * Specifies if this call is direct or indirect. A direct call has an immediate operand encoding
         * the absolute or relative (to the call itself) address of the target. An indirect call has a
         * register or memory operand specifying the target address of the call.
         */
        public final boolean direct;

        Call(Object target, int pcOffset, int size, boolean direct, DebugInfo debugInfo) {
            super(pcOffset, debugInfo);
            this.size = size;
            this.target = target;
            this.direct = direct;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(pcOffset);
            sb.append('[');
            sb.append(target);
            sb.append(']');

            if (debugInfo != null) {
                appendDebugInfo(sb, debugInfo);
            }

            return sb.toString();
        }
    }

    /**
     * Represents a reference to data from the code. The associated data can be any constant.
     */
    public static final class DataPatch extends Site {
        private static final long serialVersionUID = 5771730331604867476L;
        public final Constant constant;
        public final int alignment;

        DataPatch(int pcOffset, Constant data, int alignment) {
            super(pcOffset);
            this.constant = data;
            this.alignment = alignment;
        }

        @Override
        public String toString() {
            return String.format("%d[<data patch referring to data %s>]", pcOffset, constant);
        }
    }

    /**
     * Provides extra information about instructions or data at specific positions in {@link CompilationResult#targetCode()}.
     * This is optional information that can be used to enhance a disassembly of the code.
     */
    public abstract static class CodeAnnotation implements Serializable {
        private static final long serialVersionUID = -7903959680749520748L;
        public final int position;

        public CodeAnnotation(int position) {
            this.position = position;
        }
    }

    /**
     * A string comment about one or more instructions at a specific position in the code.
     */
    public static final class CodeComment extends CodeAnnotation {
        /**
         *
         */
        private static final long serialVersionUID = 6802287188701961401L;
        public final String value;
        public CodeComment(int position, String comment) {
            super(position);
            this.value = comment;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + position + ": " + value;
        }
    }

    /**
     * Labels some inline data in the code.
     */
    public static final class InlineData extends CodeAnnotation {
        private static final long serialVersionUID = 305997507263827108L;
        public final int size;
        public InlineData(int position, int size) {
            super(position);
            this.size = size;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + position + ": size=" + size;
        }
    }

    /**
     * Describes a table of signed offsets embedded in the code. The offsets are relative to the starting
     * address of the table. This type of table maybe generated when translating a multi-way branch
     * based on a key value from a dense value set (e.g. the {@code tableswitch} JVM instruction).
     *
     * The table is indexed by the contiguous range of integers from {@link #low} to {@link #high} inclusive.
     */
    public static final class JumpTable extends CodeAnnotation {
        private static final long serialVersionUID = 2222194398353801831L;

        /**
         * The low value in the key range (inclusive).
         */
        public final int low;

        /**
         * The high value in the key range (inclusive).
         */
        public final int high;

        /**
         * The size (in bytes) of each table entry.
         */
        public final int entrySize;

        public JumpTable(int position, int low, int high, int entrySize) {
            super(position);
            this.low = low;
            this.high = high;
            this.entrySize = entrySize;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + position + ": [" + low + " .. " + high + "]";
        }
    }

    /**
     * Describes a table of key and offset pairs. The offset in each table entry is relative to the address of
     * the table. This type of table maybe generated when translating a multi-way branch
     * based on a key value from a sparse value set (e.g. the {@code lookupswitch} JVM instruction).
     */
    public static final class LookupTable extends CodeAnnotation {
        private static final long serialVersionUID = 8367952567559116160L;

        /**
         * The number of entries in the table.
         */
        public final int npairs;

        /**
         * The size (in bytes) of entry's key.
         */
        public final int keySize;

        /**
         * The size (in bytes) of entry's offset value.
         */
        public final int offsetSize;

        public LookupTable(int position, int npairs, int keySize, int offsetSize) {
            super(position);
            this.npairs = npairs;
            this.keySize = keySize;
            this.offsetSize = offsetSize;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + position + ": [npairs=" + npairs + ", keySize=" + keySize + ", offsetSize=" + offsetSize + "]";
        }
    }

    /**
     * Represents exception handler information for a specific code position. It includes the catch code position as
     * well as the caught exception type.
     */
    public static final class ExceptionHandler extends Site {
        private static final long serialVersionUID = 4897339464722665281L;
        public final int handlerPos;

        ExceptionHandler(int pcOffset, int handlerPos) {
            super(pcOffset);
            this.handlerPos = handlerPos;
        }

        @Override
        public String toString() {
            return String.format("%d[<exception edge to %d>]", pcOffset, handlerPos);
        }
    }

    /**
     * Represents a mark in the machine code that can be used by the runtime for its own purposes. A mark
     * can reference other marks.
     */
    public static final class Mark extends Site {
        private static final long serialVersionUID = 3612943150662354844L;
        public final Object id;
        public final Mark[] references;

        Mark(int pcOffset, Object id, Mark[] references) {
            super(pcOffset);
            this.id = id;
            this.references = references;
        }

        @Override
        public String toString() {
            if (id == null) {
                return String.format("%d[<mark with %d references>]", pcOffset, references.length);
            } else if (id instanceof Integer) {
                return String.format("%d[<mark with %d references and id %s>]", pcOffset, references.length, Integer.toHexString((Integer) id));
            } else {
                return String.format("%d[<mark with %d references and id %s>]", pcOffset, references.length, id.toString());
            }
        }
    }

    private final List<Safepoint> safepoints = new ArrayList<>();
    private final List<DataPatch> dataReferences = new ArrayList<>();
    private final List<ExceptionHandler> exceptionHandlers = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();

    private int frameSize = -1;
    private int customStackAreaOffset = -1;
    private int registerRestoreEpilogueOffset = -1;
    /**
     * The buffer containing the emitted machine code.
     */
    private byte[] targetCode;

    /**
     * The leading number of bytes in {@link #targetCode} containing the emitted machine code.
     */
    private int targetCodeSize;

    private ArrayList<CodeAnnotation> annotations;

    private Assumptions assumptions;

    /**
     * Constructs a new target method.
     */
    public CompilationResult() {
    }

    public void setAssumptions(Assumptions assumptions) {
        this.assumptions = assumptions;
    }

    public Assumptions assumptions() {
        return assumptions;
    }

    /**
     * Sets the frame size in bytes. Does not include the return address pushed onto the
     * stack, if any.
     *
     * @param size the size of the frame in bytes
     */
    public void setFrameSize(int size) {
        frameSize = size;
    }

    /**
     * Sets the machine that has been generated by the compiler.
     *
     * @param code the machine code generated
     * @param size the size of the machine code
     */
    public void setTargetCode(byte[] code, int size) {
        targetCode = code;
        targetCodeSize = size;
    }

    /**
     * Records a reference to the data section in the code section (e.g. to load an integer or floating point constant).
     *
     * @param codePos the position in the code where the data reference occurs
     * @param data the data that is referenced
     * @param alignment the alignment requirement of the data or 0 if there is no alignment requirement
     */
    public void recordDataReference(int codePos, Constant data, int alignment) {
        assert codePos >= 0 && data != null;
        getDataReferences().add(new DataPatch(codePos, data, alignment));
    }

    /**
     * Records a call in the code array.
     *
     * @param codePos the position of the call in the code array
     * @param size the size of the call instruction
     * @param target the {@link CodeCacheProvider#asCallTarget(Object) target} being called
     * @param debugInfo the debug info for the call
     * @param direct specifies if this is a {@linkplain Call#direct direct} call
     */
    public void recordCall(int codePos, int size, Object target, DebugInfo debugInfo, boolean direct) {
        final Call call = new Call(target, codePos, size, direct, debugInfo);
        addSafepoint(call);
    }

    /**
     * Records an exception handler for this method.
     *
     * @param codePos  the position in the code that is covered by the handler
     * @param handlerPos    the position of the handler
     */
    public void recordExceptionHandler(int codePos, int handlerPos) {
        getExceptionHandlers().add(new ExceptionHandler(codePos, handlerPos));
    }

    /**
     * Records a safepoint in the code array.
     *
     * @param codePos the position of the safepoint in the code array
     * @param debugInfo the debug info for the safepoint
     */
    public void recordSafepoint(int codePos, DebugInfo debugInfo) {
        addSafepoint(new Safepoint(codePos, debugInfo));
    }

    private void addSafepoint(Safepoint safepoint) {
        // The safepoints list must always be sorted
        if (!getSafepoints().isEmpty() && getSafepoints().get(getSafepoints().size() - 1).pcOffset >= safepoint.pcOffset) {
            // This re-sorting should be very rare
            Collections.sort(getSafepoints());
        }
        getSafepoints().add(safepoint);
    }

    /**
     * Records an instruction mark within this method.
     *
     * @param codePos the position in the code that is covered by the handler
     * @param id the identifier for this mark
     * @param references an array of other marks that this mark references
     */
    public Mark recordMark(int codePos, Object id, Mark[] references) {
        Mark mark = new Mark(codePos, id, references);
        getMarks().add(mark);
        return mark;
    }

    /**
     * Allows a method to specify the offset of the epilogue that restores the callee saved registers. Must be called
     * iff the method is a callee saved method and stores callee registers on the stack.
     *
     * @param registerRestoreEpilogueOffset the offset in the machine code where the epilogue begins
     */
    public void setRegisterRestoreEpilogueOffset(int registerRestoreEpilogueOffset) {
        assert this.registerRestoreEpilogueOffset == -1;
        this.registerRestoreEpilogueOffset = registerRestoreEpilogueOffset;
    }

    /**
     * The frame size of the method in bytes.
     *
     * @return the frame size
     */
    public int frameSize() {
        assert frameSize != -1 : "frame size not yet initialized!";
        return frameSize;
    }

    /**
     * @return the code offset of the start of the epilogue that restores all callee saved registers, or -1 if this is
     *         not a callee saved method
     */
    public int registerRestoreEpilogueOffset() {
        return registerRestoreEpilogueOffset;
    }

    /**
     * Offset in bytes for the custom stack area (relative to sp).
     * @return the offset in bytes
     */
    public int customStackAreaOffset() {
        return customStackAreaOffset;
    }

    /**
     * @see #customStackAreaOffset()
     * @param offset
     */
    public void setCustomStackAreaOffset(int offset) {
        customStackAreaOffset = offset;
    }

    /**
     * @return the machine code generated for this method
     */
    public byte[] targetCode() {
        return targetCode;
    }

    /**
     * @return the size of the machine code generated for this method
     */
    public int targetCodeSize() {
        return targetCodeSize;
    }

    /**
     * @return the code annotations or {@code null} if there are none
     */
    public List<CodeAnnotation> annotations() {
        return annotations;
    }

    public void addAnnotation(CodeAnnotation annotation) {
        assert annotation != null;
        if (annotations == null) {
            annotations = new ArrayList<>();
        }
        annotations.add(annotation);
    }

    private static void appendDebugInfo(StringBuilder sb, DebugInfo info) {
        if (info != null) {
            appendRefMap(sb, "stackMap", info.getFrameRefMap());
            appendRefMap(sb, "registerMap", info.getRegisterRefMap());
            BytecodePosition codePos = info.getBytecodePosition();
            if (codePos != null) {
                MetaUtil.appendLocation(sb.append(" "), codePos.getMethod(), codePos.getBCI());
                if (info.hasFrame()) {
                    sb.append(" #locals=").append(info.frame().numLocals).append(" #expr=").append(info.frame().numStack);
                    if (info.frame().numLocks > 0) {
                        sb.append(" #locks=").append(info.frame().numLocks);
                    }
                }
            }
        }
    }

    private static void appendRefMap(StringBuilder sb, String name, BitSet map) {
        if (map != null) {
            sb.append(' ').append(name).append('[').append(map.toString()).append(']');
        }
    }

    /**
     * @return the list of safepoints, sorted by {@link Site#pcOffset}
     */
    public List<Safepoint> getSafepoints() {
        return safepoints;
    }

    /**
     * @return the list of data references
     */
    public List<DataPatch> getDataReferences() {
        return dataReferences;
    }

    /**
     * @return the list of exception handlers
     */
    public List<ExceptionHandler> getExceptionHandlers() {
        return exceptionHandlers;
    }

    /**
     * @return the list of marks
     */
    public List<Mark> getMarks() {
        return marks;
    }
}
