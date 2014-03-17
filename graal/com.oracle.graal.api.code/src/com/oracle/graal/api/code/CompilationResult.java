/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.Collections.*;

import java.io.*;
import java.nio.*;
import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Represents the output from compiling a method, including the compiled machine code, associated
 * data and references, relocation information, deoptimization information, etc.
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
     * Represents an infopoint with associated debug info. Note that safepoints are also infopoints.
     */
    public static class Infopoint extends Site implements Comparable<Infopoint> {

        private static final long serialVersionUID = 2479806696381720162L;
        public final DebugInfo debugInfo;

        public final InfopointReason reason;

        public Infopoint(int pcOffset, DebugInfo debugInfo, InfopointReason reason) {
            super(pcOffset);
            this.debugInfo = debugInfo;
            this.reason = reason;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(pcOffset);
            sb.append("[<infopoint>]");
            appendDebugInfo(sb, debugInfo);
            return sb.toString();
        }

        @Override
        public int compareTo(Infopoint o) {
            if (pcOffset < o.pcOffset) {
                return -1;
            } else if (pcOffset > o.pcOffset) {
                return 1;
            }
            return this.reason.compareTo(o.reason);
        }
    }

    /**
     * Represents a call in the code.
     */
    public static final class Call extends Infopoint {

        private static final long serialVersionUID = 1440741241631046954L;

        /**
         * The target of the call.
         */
        public final InvokeTarget target;

        /**
         * The size of the call instruction.
         */
        public final int size;

        /**
         * Specifies if this call is direct or indirect. A direct call has an immediate operand
         * encoding the absolute or relative (to the call itself) address of the target. An indirect
         * call has a register or memory operand specifying the target address of the call.
         */
        public final boolean direct;

        public Call(InvokeTarget target, int pcOffset, int size, boolean direct, DebugInfo debugInfo) {
            super(pcOffset, debugInfo, InfopointReason.CALL);
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
     * Represents some external data that is referenced by the code.
     */
    public abstract static class Data {

        private final int alignment;

        protected Data(int alignment) {
            this.alignment = alignment;
        }

        public int getAlignment() {
            return alignment;
        }

        public abstract int getSize(TargetDescription target);

        public abstract Kind getKind();

        public abstract void emit(TargetDescription target, ByteBuffer buffer);
    }

    /**
     * Represents a Java primitive value used in a {@link DataPatch}. This implementation uses
     * {@link Kind#getByteCount()} bytes to encode each value.
     */
    public static final class PrimitiveData extends Data {

        public final Constant constant;

        public PrimitiveData(Constant constant, int alignment) {
            super(alignment);
            assert constant.getKind().isPrimitive();
            this.constant = constant;
        }

        @Override
        public int getSize(TargetDescription target) {
            return constant.getKind().getByteCount();
        }

        @Override
        public Kind getKind() {
            return constant.getKind();
        }

        @Override
        public void emit(TargetDescription target, ByteBuffer buffer) {
            switch (constant.getKind()) {
                case Boolean:
                    buffer.put(constant.asBoolean() ? (byte) 1 : (byte) 0);
                    break;
                case Byte:
                    buffer.put((byte) constant.asInt());
                    break;
                case Char:
                    buffer.putChar((char) constant.asInt());
                    break;
                case Short:
                    buffer.putShort((short) constant.asInt());
                    break;
                case Int:
                    buffer.putInt(constant.asInt());
                    break;
                case Long:
                    buffer.putLong(constant.asLong());
                    break;
                case Float:
                    buffer.putFloat(constant.asFloat());
                    break;
                case Double:
                    buffer.putDouble(constant.asDouble());
                    break;
            }
        }

        @Override
        public String toString() {
            return constant.toString();
        }

        @Override
        public int hashCode() {
            return constant.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof PrimitiveData) {
                PrimitiveData other = (PrimitiveData) obj;
                return constant.equals(other.constant);
            } else {
                return false;
            }
        }
    }

    public static final class RawData extends Data {

        public final byte[] data;

        public RawData(byte[] data, int alignment) {
            super(alignment);
            this.data = data;
        }

        @Override
        public int getSize(TargetDescription target) {
            return data.length;
        }

        @Override
        public Kind getKind() {
            return Kind.Illegal;
        }

        @Override
        public void emit(TargetDescription target, ByteBuffer buffer) {
            buffer.put(data);
        }

        @Override
        public String toString() {
            Formatter ret = new Formatter();
            boolean first = true;
            for (byte b : data) {
                ret.format(first ? "%02X" : " %02X", b);
                first = false;
            }
            return ret.toString();
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof RawData) {
                RawData other = (RawData) obj;
                return Arrays.equals(data, other.data);
            } else {
                return false;
            }
        }
    }

    /**
     * Represents a code site that references some data. The associated data can be either a
     * reference to an external {@link Data} item in the data section, or it may be an inlined
     * {@link Constant} that needs to be patched.
     */
    public static final class DataPatch extends Site {

        private static final long serialVersionUID = 5771730331604867476L;
        public Data data;
        public boolean inline;

        public DataPatch(int pcOffset, Data data, boolean inline) {
            super(pcOffset);
            this.data = data;
            this.inline = inline;
        }

        @Override
        public String toString() {
            return String.format("%d[<data patch referring to %s data %s>]", pcOffset, inline ? "inline" : "external", data.toString());
        }
    }

    /**
     * Provides extra information about instructions or data at specific positions in
     * {@link CompilationResult#getTargetCode()}. This is optional information that can be used to
     * enhance a disassembly of the code.
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
     * Describes a table of signed offsets embedded in the code. The offsets are relative to the
     * starting address of the table. This type of table maybe generated when translating a
     * multi-way branch based on a key value from a dense value set (e.g. the {@code tableswitch}
     * JVM instruction).
     * 
     * The table is indexed by the contiguous range of integers from {@link #low} to {@link #high}
     * inclusive.
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
     * Represents exception handler information for a specific code position. It includes the catch
     * code position as well as the caught exception type.
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
     * Represents a mark in the machine code that can be used by the runtime for its own purposes. A
     * mark can reference other marks.
     */
    public static final class Mark extends Site {

        private static final long serialVersionUID = 3612943150662354844L;
        public final Object id;
        public final Mark[] references;

        public Mark(int pcOffset, Object id, Mark[] references) {
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

    private int id = -1;
    private int entryBCI = -1;

    private final List<Infopoint> infopoints = new ArrayList<>();
    private final List<DataPatch> dataReferences = new ArrayList<>();
    private final List<ExceptionHandler> exceptionHandlers = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();

    private int frameSize = -1;
    private int customStackAreaOffset = -1;
    private int registerRestoreEpilogueOffset = -1;

    private final String name;

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

    public CompilationResult() {
        this(null);
    }

    public CompilationResult(String name) {
        this.name = name;
    }

    /**
     * @return the compile id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the compile id to set
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * @return the entryBCI
     */
    public int getEntryBCI() {
        return entryBCI;
    }

    /**
     * @param entryBCI the entryBCI to set
     */
    public void setEntryBCI(int entryBCI) {
        this.entryBCI = entryBCI;
    }

    public void setAssumptions(Assumptions assumptions) {
        this.assumptions = assumptions;
    }

    public Assumptions getAssumptions() {
        return assumptions;
    }

    /**
     * Sets the frame size in bytes. Does not include the return address pushed onto the stack, if
     * any.
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
     * Records a reference to the data section in the code section (e.g. to load an integer or
     * floating point constant).
     * 
     * @param codePos the position in the code where the data reference occurs
     * @param data the data that is referenced
     */
    public void recordDataReference(int codePos, Data data) {
        assert codePos >= 0 && data != null;
        dataReferences.add(new DataPatch(codePos, data, false));
    }

    /**
     * Records a reference to an inlined constant in the code section (e.g. to load a constant oop).
     * 
     * @param codePos the position in the code where the inlined constant occurs
     * @param data the data that is referenced
     */
    public void recordInlineData(int codePos, Data data) {
        assert codePos >= 0 && data != null;
        dataReferences.add(new DataPatch(codePos, data, true));
    }

    /**
     * Records a call in the code array.
     * 
     * @param codePos the position of the call in the code array
     * @param size the size of the call instruction
     * @param target the being called
     * @param debugInfo the debug info for the call
     * @param direct specifies if this is a {@linkplain Call#direct direct} call
     */
    public void recordCall(int codePos, int size, InvokeTarget target, DebugInfo debugInfo, boolean direct) {
        final Call call = new Call(target, codePos, size, direct, debugInfo);
        addInfopoint(call);
    }

    /**
     * Records an exception handler for this method.
     * 
     * @param codePos the position in the code that is covered by the handler
     * @param handlerPos the position of the handler
     */
    public void recordExceptionHandler(int codePos, int handlerPos) {
        exceptionHandlers.add(new ExceptionHandler(codePos, handlerPos));
    }

    /**
     * Records an infopoint in the code array.
     * 
     * @param codePos the position of the infopoint in the code array
     * @param debugInfo the debug info for the infopoint
     */
    public void recordInfopoint(int codePos, DebugInfo debugInfo, InfopointReason reason) {
        addInfopoint(new Infopoint(codePos, debugInfo, reason));
    }

    private void addInfopoint(Infopoint infopoint) {
        // The infopoints list must always be sorted
        if (!infopoints.isEmpty() && infopoints.get(infopoints.size() - 1).pcOffset >= infopoint.pcOffset) {
            // This re-sorting should be very rare
            Collections.sort(infopoints);
        }
        infopoints.add(infopoint);
    }

    /**
     * Records an instruction mark within this method.
     * 
     * @param codePos the position in the code that is covered by the handler
     * @param markId the identifier for this mark
     * @param references an array of other marks that this mark references
     */
    public Mark recordMark(int codePos, Object markId, Mark[] references) {
        Mark mark = new Mark(codePos, markId, references);
        marks.add(mark);
        return mark;
    }

    /**
     * Allows a method to specify the offset of the epilogue that restores the callee saved
     * registers. Must be called iff the method is a callee saved method and stores callee registers
     * on the stack.
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
    public int getFrameSize() {
        assert frameSize != -1 : "frame size not yet initialized!";
        return frameSize;
    }

    /**
     * @return the code offset of the start of the epilogue that restores all callee saved
     *         registers, or -1 if this is not a callee saved method
     */
    public int getRegisterRestoreEpilogueOffset() {
        return registerRestoreEpilogueOffset;
    }

    /**
     * Offset in bytes for the custom stack area (relative to sp).
     * 
     * @return the offset in bytes
     */
    public int getCustomStackAreaOffset() {
        return customStackAreaOffset;
    }

    /**
     * @see #getCustomStackAreaOffset()
     * @param offset
     */
    public void setCustomStackAreaOffset(int offset) {
        customStackAreaOffset = offset;
    }

    /**
     * @return the machine code generated for this method
     */
    public byte[] getTargetCode() {
        return targetCode;
    }

    /**
     * @return the size of the machine code generated for this method
     */
    public int getTargetCodeSize() {
        return targetCodeSize;
    }

    /**
     * @return the code annotations or {@code null} if there are none
     */
    public List<CodeAnnotation> getAnnotations() {
        if (annotations == null) {
            return Collections.emptyList();
        }
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
            ReferenceMap refMap = info.getReferenceMap();
            if (refMap != null) {
                if (refMap.hasFrameRefMap()) {
                    sb.append(" stackMap[");
                    refMap.appendFrameMap(sb, null);
                    sb.append(']');
                }
                if (refMap.hasRegisterRefMap()) {
                    sb.append(" registerMap[");
                    refMap.appendRegisterMap(sb, null);
                    sb.append(']');
                }
            }
            RegisterSaveLayout calleeSaveInfo = info.getCalleeSaveInfo();
            if (calleeSaveInfo != null) {
                sb.append(" callee-save-info[");
                String sep = "";
                for (Map.Entry<Register, Integer> e : calleeSaveInfo.registersToSlots(true).entrySet()) {
                    sb.append(sep).append(e.getKey()).append("->").append(e.getValue());
                    sep = ", ";
                }
                sb.append(']');
            }
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

    /**
     * @return the list of infopoints, sorted by {@link Site#pcOffset}
     */
    public List<Infopoint> getInfopoints() {
        if (infopoints.isEmpty()) {
            return emptyList();
        }
        return unmodifiableList(infopoints);
    }

    /**
     * @return the list of data references
     */
    public List<DataPatch> getDataReferences() {
        if (dataReferences.isEmpty()) {
            return emptyList();
        }
        return unmodifiableList(dataReferences);
    }

    /**
     * @return the list of exception handlers
     */
    public List<ExceptionHandler> getExceptionHandlers() {
        if (exceptionHandlers.isEmpty()) {
            return emptyList();
        }
        return unmodifiableList(exceptionHandlers);
    }

    /**
     * @return the list of marks
     */
    public List<Mark> getMarks() {
        if (marks.isEmpty()) {
            return emptyList();
        }
        return unmodifiableList(marks);
    }

    public String getName() {
        return name;
    }
}
