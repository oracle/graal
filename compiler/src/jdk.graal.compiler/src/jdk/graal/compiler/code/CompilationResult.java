/*
 * Copyright (c) 2009, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.code;

import static jdk.vm.ci.meta.MetaUtil.identityHashCodeString;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.ExceptionHandler;
import jdk.vm.ci.code.site.ImplicitExceptionDispatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.code.site.Reference;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Represents the output from compiling a method, including the compiled machine code, associated
 * data and references, relocation information, deoptimization information, etc.
 */
public class CompilationResult {

    /**
     * Provides extra information about instructions or data at specific positions in
     * {@link CompilationResult#getTargetCode()}. This is optional information that can be used to
     * enhance a disassembly of the code.
     */
    public abstract static class CodeAnnotation {

        private int position;

        public CodeAnnotation(int position) {
            this.position = position;
        }

        @Override
        public final int hashCode() {
            throw new UnsupportedOperationException("hashCode"); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public String toString() {
            return identityHashCodeString(this);
        }

        @Override
        public abstract boolean equals(Object obj);

        public int getPosition() {
            return position;
        }
    }

    /**
     * A string comment about one or more instructions at a specific position in the code.
     */
    public static final class CodeComment extends CodeAnnotation {

        public final String value;

        public CodeComment(int position, String comment) {
            super(position);
            this.value = comment;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof CodeComment) {
                CodeComment that = (CodeComment) obj;
                if (this.getPosition() == that.getPosition() && this.value.equals(that.value)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + getPosition() + ": " + value;
        }
    }

    /**
     * Describes a table of signed offsets embedded in the code. The offsets are relative to the
     * starting address of the table. This type of table can be generated when translating a
     * multi-way branch based on a key value from a dense value set (e.g. the {@code tableswitch}
     * JVM instruction).
     *
     * The table is indexed by the contiguous range of integers from {@link #low} to {@link #high}
     * inclusive.
     */
    public static final class JumpTable extends CodeAnnotation {

        /**
         * Constants denoting the format and size of each entry in a jump table.
         */
        public enum EntryFormat {
            /**
             * Each entry is a 4 byte offset.
             */
            OFFSET_ONLY(4),

            /**
             * Each entry contains a 4 byte value followed by a 4 byte offset.
             */
            VALUE_AND_OFFSET(8);

            EntryFormat(int size) {
                this.size = size;
            }

            /**
             * Gets the size of an entry in bytes.
             */
            public final int size;
        }

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
        public final EntryFormat entryFormat;

        public JumpTable(int position, int low, int high, EntryFormat entryFormat) {
            super(position);
            if (high <= low) {
                throw new IllegalArgumentException(String.format("low (%d) is not less than high(%d)", low, high));
            }
            this.low = low;
            this.high = high;
            this.entryFormat = entryFormat;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof JumpTable) {
                JumpTable that = (JumpTable) obj;
                if (this.getPosition() == that.getPosition() && this.entryFormat == that.entryFormat && this.low == that.low && this.high == that.high) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + getPosition() + ": [" + low + " .. " + high + "]";
        }
    }

    private boolean closed;

    private int entryBCI = -1;

    private final DataSection dataSection = new DataSection();

    private EconomicSet<Call> invalidCallDeoptimizationStates = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
    private List<Infopoint> infopoints = new ArrayList<>();
    private boolean infopointDebugInfoCleared;
    /** Null means released, as opposed to a compilation that never recorded any mappings. */
    private List<SourceMapping> sourceMapping = new ArrayList<>();
    private List<DataPatch> dataPatches = new ArrayList<>();
    private List<ExceptionHandler> exceptionHandlers = new ArrayList<>();
    private List<CodeMark> marks = new ArrayList<>();

    private int totalFrameSize = -1;
    private int maxInterpreterFrameSize = -1;

    /**
     * The minimum alignment for an item in the {@linkplain DataSectionReference data section}.
     */
    private int minDataSectionItemAlignment = 1;

    private StackSlot customStackArea = null;

    /**
     * A customized name that is unrelated to {@link #compilationId}. Can be null if
     * {@link #compilationId} fully describes the compilation.
     */
    private final String name;

    private final CompilationIdentifier compilationId;

    /**
     * The buffer containing the emitted machine code.
     */
    private byte[] targetCode;

    /**
     * The leading number of bytes in {@link #targetCode} containing the emitted machine code.
     */
    private int targetCodeSize;

    private List<CodeAnnotation> annotations;

    private Assumption[] assumptions;

    /**
     * The set of the methods whose bytecodes were used as input to the compilation. If
     * {@code null}, then the compilation did not record method dependencies. Otherwise, the first
     * element of this array is the root method of the compilation.
     */
    private ResolvedJavaMethod[] methods;

    /**
     * The {@link SpeculationLog} log used during compilation.
     */
    private SpeculationLog speculationLog;

    private int bytecodeSize;

    private boolean hasUnsafeAccess;

    public CompilationResult(CompilationIdentifier compilationId) {
        this(compilationId, null);
    }

    public CompilationResult(CompilationIdentifier compilationId, String name) {
        this.compilationId = compilationId;
        this.name = name;
    }

    public CompilationResult(String name) {
        this(null, name);
    }

    @Override
    public int hashCode() {
        // CompilationResult instances should not be used as hash map keys
        throw new UnsupportedOperationException("hashCode");
    }

    @Override
    public String toString() {
        if (methods != null) {
            return getClass().getName() + "[" + methods[0].format("%H.%n(%p)%r") + "]";
        }
        return identityHashCodeString(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj != null && obj.getClass() == getClass()) {
            CompilationResult that = (CompilationResult) obj;
            // @formatter:off
            if (this.entryBCI == that.entryBCI &&
                Objects.equals(this.customStackArea, that.customStackArea) &&
                this.totalFrameSize == that.totalFrameSize &&
                this.targetCodeSize == that.targetCodeSize &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.compilationId, that.compilationId) &&
                Objects.equals(this.annotations, that.annotations) &&
                Objects.equals(this.dataSection, that.dataSection) &&
                Objects.equals(this.exceptionHandlers, that.exceptionHandlers) &&
                Objects.equals(this.dataPatches, that.dataPatches) &&
                Objects.equals(this.invalidCallDeoptimizationStates, that.invalidCallDeoptimizationStates) &&
                Objects.equals(this.infopoints, that.infopoints) &&
                Objects.equals(this.marks,  that.marks) &&
                Arrays.equals(this.assumptions, that.assumptions) &&
                Arrays.equals(targetCode, that.targetCode)) {
                return true;
            }
            // @formatter:on
        }
        return false;
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
        checkOpen();
        this.entryBCI = entryBCI;
    }

    /**
     * Sets the assumptions made during compilation.
     */
    public void setAssumptions(Assumption[] assumptions) {
        this.assumptions = assumptions;
    }

    /**
     * Gets the assumptions made during compilation.
     *
     * The caller must not modify the contents of the returned array.
     */
    public Assumption[] getAssumptions() {
        return assumptions;
    }

    /**
     * Records the set of methods whose bytecodes were used as input to the compilation.
     *
     * @param rootMethod the root method of the compilation
     * @param inlinedMethods the methods inlined during compilation (may contain duplicates)
     */
    public void setMethods(ResolvedJavaMethod rootMethod, Collection<ResolvedJavaMethod> inlinedMethods) {
        checkOpen();
        assert rootMethod != null;
        assert inlinedMethods != null;

        EconomicSet<ResolvedJavaMethod> methodSet = EconomicSet.create(inlinedMethods.size());
        methodSet.addAll(inlinedMethods);
        methodSet.remove(rootMethod);
        methods = new ResolvedJavaMethod[1 + methodSet.size()];
        methods[0] = rootMethod;
        int i = 1;
        for (ResolvedJavaMethod m : methodSet) {
            methods[i++] = m;
        }
    }

    /**
     * Gets the set of methods whose bytecodes were used as input to the compilation.
     *
     * The caller must not modify the contents of the returned array.
     *
     * @return {@code null} if the compilation did not record method dependencies otherwise the
     *         methods whose bytecodes were used as input to the compilation with the first element
     *         being the root method of the compilation. The entries in a non-null returned array
     *         are guaranteed to be unique.
     */
    public ResolvedJavaMethod[] getMethods() {
        return methods;
    }

    /**
     * Sets the {@link SpeculationLog} log used during compilation.
     */
    public void setSpeculationLog(SpeculationLog speculationLog) {
        checkOpen();
        this.speculationLog = speculationLog;
    }

    /**
     * Gets the {@link SpeculationLog} log, if any, used during compilation.
     */
    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    public void setBytecodeSize(int bytecodeSize) {
        checkOpen();
        this.bytecodeSize = bytecodeSize;
    }

    public int getBytecodeSize() {
        return bytecodeSize;
    }

    public DataSection getDataSection() {
        return dataSection;
    }

    /**
     * The total frame size of the method in bytes. This includes the return address pushed onto the
     * stack, if any.
     *
     * @return the frame size
     */
    public int getTotalFrameSize() {
        assert totalFrameSize != -1 : "frame size not yet initialized!";
        return totalFrameSize;
    }

    /**
     * Sets the total frame size in bytes. This includes the return address pushed onto the stack,
     * if any.
     *
     * @param size the size of the frame in bytes
     */
    public void setTotalFrameSize(int size) {
        checkOpen();
        totalFrameSize = size;
    }

    public int getMaxInterpreterFrameSize() {
        return maxInterpreterFrameSize;
    }

    public void setMaxInterpreterFrameSize(int maxInterpreterFrameSize) {
        checkOpen();
        this.maxInterpreterFrameSize = maxInterpreterFrameSize;
    }

    /**
     * Sets the minimum alignment for an item in the {@linkplain DataSectionReference data section}.
     */
    public void setMinDataSectionItemAlignment(int alignment) {
        checkOpen();
        this.minDataSectionItemAlignment = alignment;
    }

    /**
     * Sets the machine that has been generated by the compiler.
     *
     * @param code the machine code generated
     * @param size the size of the machine code
     */
    public void setTargetCode(byte[] code, int size) {
        checkOpen();
        checkCodeEmissionStateNotCleared();
        targetCode = code;
        targetCodeSize = size;
    }

    /**
     * Records a data patch in the code section. The data patch can refer to something in the
     * {@link DataSectionReference data section} or directly to an {@link ConstantReference inlined
     * constant}.
     *
     * @param codePos the position in the code that needs to be patched
     * @param ref the reference that should be inserted in the code
     */
    public void recordDataPatch(int codePos, Reference ref) {
        checkOpen();
        checkCodeEmissionStateNotCleared();
        assert codePos >= 0 && ref != null : codePos + " " + ref;
        dataPatches.add(new DataPatch(codePos, ref));
    }

    /**
     * Records a data patch in the code section. The data patch can refer to something in the
     * {@link DataSectionReference data section} or directly to an {@link ConstantReference inlined
     * constant}.
     *
     * @param codePos the position in the code that needs to be patched
     * @param ref the reference that should be inserted in the code
     * @param note a note attached to data patch for use by post-processing tools
     */
    public void recordDataPatchWithNote(int codePos, Reference ref, Object note) {
        checkCodeEmissionStateNotCleared();
        assert codePos >= 0 && ref != null : codePos + " " + ref;
        dataPatches.add(new DataPatch(codePos, ref, note));
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
    public Call recordCall(int codePos, int size, InvokeTarget target, DebugInfo debugInfo, boolean direct) {
        checkOpen();
        final Call call = new Call(target, codePos, size, direct, debugInfo);
        addInfopoint(call);
        return call;
    }

    /**
     * Records an exception handler for this method.
     *
     * @param codePos the position in the code that is covered by the handler
     * @param handlerPos the position of the handler
     */
    public void recordExceptionHandler(int codePos, int handlerPos) {
        checkOpen();
        checkCodeEmissionStateNotCleared();
        assert validateExceptionHandlerAdd(codePos, handlerPos) : String.format("Duplicate exception handler for pc 0x%x handlerPos 0x%x", codePos, handlerPos);
        exceptionHandlers.add(new ExceptionHandler(codePos, handlerPos));
    }

    /**
     * Validate if the exception handler for codePos already exists and handlerPos is different.
     *
     * @param codePos
     * @param handlerPos
     * @return true if the validation is successful
     */
    private boolean validateExceptionHandlerAdd(int codePos, int handlerPos) {
        ExceptionHandler exHandler = getExceptionHandlerForCodePos(codePos);
        return exHandler == null || exHandler.handlerPos == handlerPos;
    }

    /**
     * Returns the first ExceptionHandler which matches codePos.
     *
     * @param codePos position to search for
     * @return first matching ExceptionHandler
     */
    private ExceptionHandler getExceptionHandlerForCodePos(int codePos) {
        for (ExceptionHandler h : exceptionHandlers) {
            if (h.pcOffset == codePos) {
                return h;
            }
        }
        return null;
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

    /**
     * Records an implicit exception in the code array.
     *
     * @param codePos the position of the implicit exception in the code array
     * @param dispatchPos the position to resume execution when an implicit exception occurs.
     *            Setting it to the same value of {@code codePos} forces a deoptimization, and will
     *            resume execution at the default deoptimization blob.
     * @param debugInfo the debug info for the infopoint
     */
    public void recordImplicitException(int codePos, int dispatchPos, DebugInfo debugInfo) {
        addInfopoint(new ImplicitExceptionDispatch(codePos, dispatchPos, debugInfo));
    }

    /**
     * Records a custom infopoint in the code section.
     *
     * Compiler implementations can use this method to record non-standard infopoints, which are not
     * handled by dedicated methods like {@link #recordCall}.
     *
     * @param infopoint the infopoint to record, usually a derived class from {@link Infopoint}
     */
    public void addInfopoint(Infopoint infopoint) {
        checkOpen();
        checkCodeEmissionStateNotCleared();
        checkInfopointDebugInfoNotCleared();
        infopoints.add(infopoint);
    }

    /**
     * Mark that the provided call infopoint cannot be used as a deoptimization entrypoint.
     *
     * This distinction is necessary as native-image, in addition to deoptimization support, uses
     * call infopoints for stack traces and debugging information.
     */
    public void recordCallInvalidForDeoptimization(Call call) {
        checkOpen();
        checkCodeEmissionStateNotCleared();
        checkInfopointDebugInfoNotCleared();
        invalidCallDeoptimizationStates.add(call);
    }

    public void recordSourceMapping(int startOffset, int endOffset, NodeSourcePosition sourcePosition) {
        checkOpen();
        checkSourceMappingsNotCleared();
        sourceMapping.add(new SourceMapping(startOffset, endOffset, sourcePosition));
    }

    /**
     * Records an instruction mark within this method.
     *
     * @param codePos the position in the code that is covered by the handler
     * @param markId the identifier for this mark
     */
    public CodeMark recordMark(int codePos, MarkId markId) {
        checkOpen();
        checkCodeEmissionStateNotCleared();
        CodeMark mark = new CodeMark(codePos, markId);
        marks.add(mark);
        return mark;
    }

    /**
     * Start of the custom stack area.
     *
     * @return the first stack slot of the custom stack area
     */
    public StackSlot getCustomStackArea() {
        return customStackArea;
    }

    /**
     * @see #getCustomStackArea()
     * @param slot
     */
    public void setCustomStackAreaOffset(StackSlot slot) {
        checkOpen();
        customStackArea = slot;
    }

    /**
     * @return the machine code generated for this method
     * @throws IllegalStateException after {@link #clearCodeEmissionState()}
     */
    public byte[] getTargetCode() {
        checkCodeEmissionStateNotCleared();
        return targetCode;
    }

    /**
     * @return the size of the machine code generated for this method
     */
    public int getTargetCodeSize() {
        return targetCodeSize;
    }

    /**
     * @return a read-only view of the code annotations, empty if none were recorded. Previously
     *         obtained views retain their annotations after release or reset.
     * @throws IllegalStateException after {@link #clearCodeEmissionState()}
     */
    public List<CodeAnnotation> getCodeAnnotations() {
        checkCodeEmissionStateNotCleared();
        return annotations == null ? Collections.emptyList() : Collections.unmodifiableList(annotations);
    }

    public void addAnnotation(CodeAnnotation annotation) {
        checkOpen();
        checkCodeEmissionStateNotCleared();
        assert annotation != null;
        if (annotations == null) {
            annotations = new ArrayList<>();
        }
        annotations.add(annotation);
    }

    public boolean isValidCallDeoptimizationState(Call call) {
        checkCodeEmissionStateNotCleared();
        checkInfopointDebugInfoNotCleared();
        return call != null && !invalidCallDeoptimizationStates.contains(call);
    }

    /**
     * Returns infopoints while their debug information is available. Code patching that does not
     * inspect debug information can use {@link #getInfopointsForCodeEmission()} after debug-info
     * release.
     *
     * @return the list of infopoints, sorted by {@link Site#pcOffset}
     * @throws IllegalStateException after {@link #clearInfopointDebugInfo()}
     */
    public List<Infopoint> getInfopoints() {
        checkInfopointDebugInfoNotCleared();
        return getInfopointsForCodeEmission();
    }

    /**
     * Returns sites for code patching only. Debug information may already have been released and
     * must not be inspected through this accessor. Consumers needing debug information must use
     * {@link #getInfopoints()}, which rejects access after debug-info release.
     *
     * @throws IllegalStateException after {@link #clearCodeEmissionState()}
     */
    public List<Infopoint> getInfopointsForCodeEmission() {
        checkCodeEmissionStateNotCleared();
        return infopoints.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(infopoints);
    }

    /**
     * @return the list of data references
     * @throws IllegalStateException after {@link #clearCodeEmissionState()}
     */
    public List<DataPatch> getDataPatches() {
        checkCodeEmissionStateNotCleared();
        return dataPatches.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(dataPatches);
    }

    /**
     * @return the list of exception handlers
     * @throws IllegalStateException after {@link #clearCodeEmissionState()}
     */
    public List<ExceptionHandler> getExceptionHandlers() {
        checkCodeEmissionStateNotCleared();
        return exceptionHandlers.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(exceptionHandlers);
    }

    /**
     * An identified mark in the generated code.
     */
    public interface MarkId {

        /**
         * A human readable name for this mark.
         */
        String getName();

        /**
         * Return the object which should be used in the {@link Mark}. On some platforms that may be
         * different than this object.
         */
        default Object getId() {
            return this;
        }

        /**
         * @return {@code true} if only one instance of this {@code MarkID} is allowed
         */
        default boolean isUnique() {
            return true;
        }
    }

    /**
     * An alternative to the existing {@link Mark} which isn't very flexible since it's final. This
     * enforces some API for the mark object and can be converted into the standard mark for code
     * installation if necessary.
     */
    public static class CodeMark extends Site {

        /**
         * An object denoting extra semantic information about the machine code position of this
         * mark.
         */
        public final MarkId id;

        /**
         * Creates a mark that associates {@code id} with the machine code position
         * {@code pcOffset}.
         */
        public CodeMark(int pcOffset, MarkId id) {
            super(pcOffset);
            this.id = id;
            assert id != null : this;
        }

        @Override
        public String toString() {
            return id + "@" + pcOffset;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof CodeMark) {
                CodeMark that = (CodeMark) obj;
                if (this.pcOffset == that.pcOffset && Objects.equals(this.id, that.id)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * @return the list of {@link CodeMark code marks}.
     * @throws IllegalStateException after {@link #clearCodeEmissionState()}
     */
    public List<CodeMark> getMarks() {
        checkCodeEmissionStateNotCleared();
        return marks.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(marks);
    }

    /**
     * @return the list of {@link SourceMapping}s
     * @throws IllegalStateException after {@link #clearSourceMappings()}
     */
    public List<SourceMapping> getSourceMappings() {
        checkSourceMappingsNotCleared();
        return sourceMapping.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(sourceMapping);
    }

    /**
     * Clears source mappings while preserving the rest of the compilation result. This is permitted
     * after {@link #close(OptionValues)} because it does not change generated code. Callers must
     * ensure that all consumers have either finished or recorded the data they need. Subsequent
     * source-mapping access is rejected rather than returning an indistinguishable empty list.
     * Previously obtained views retain their mappings; callers must drop those references to allow
     * reclamation. This guards owner access, not independently retained data.
     *
     * @throws IllegalStateException if source mappings were already cleared
     */
    public void clearSourceMappings() {
        checkSourceMappingsNotCleared();
        // Detach instead of silently emptying a view held by another consumer.
        sourceMapping = null;
    }

    /**
     * Gets the name for this compilation result. This will only be non-null when it provides a
     * value unrelated to {@link #getCompilationId()}.
     */
    public String getName() {
        return name;
    }

    public CompilationIdentifier getCompilationId() {
        return compilationId;
    }

    public void setHasUnsafeAccess(boolean hasUnsafeAccess) {
        checkOpen();
        this.hasUnsafeAccess = hasUnsafeAccess;
    }

    public boolean hasUnsafeAccess() {
        return hasUnsafeAccess;
    }

    /**
     * Clears the information in this object pertaining to generating code. That is, the
     * {@linkplain #getMarks() marks}, {@linkplain #getInfopoints() infopoints},
     * {@linkplain #getExceptionHandlers() exception handlers}, {@linkplain #getDataPatches() data
     * patches} and {@linkplain #getCodeAnnotations() annotations} recorded in this object are
     * cleared.
     */
    public void resetForEmittingCode() {
        checkOpen();
        // A retry must not empty or repopulate collections retained from the previous attempt.
        infopoints = new ArrayList<>();
        sourceMapping = new ArrayList<>();
        dataPatches = new ArrayList<>();
        exceptionHandlers = new ArrayList<>();
        marks = new ArrayList<>();
        dataSection.clear();
        invalidCallDeoptimizationStates = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        annotations = null;
        // reset fields
        targetCode = null;
        targetCodeSize = 0;
        assumptions = null;
        speculationLog = null;
        methods = null;
        bytecodeSize = 0;
        infopointDebugInfoCleared = false;
    }

    public void clearInfopoints() {
        checkCodeEmissionStateNotCleared();
        infopoints.clear();
    }

    /**
     * Clears the debug payload of {@link Call} and {@link ImplicitExceptionDispatch} sites while
     * preserving their code-patching and relocation fields. Other infopoints, including plain
     * {@link Infopoint} instances and custom subtypes, are left unchanged unless a subclass extends
     * {@link #withoutDebugInfo(Infopoint)}. This is permitted after
     * {@link #close(OptionValues)} and may be repeated. Callers must finish encoding runtime
     * metadata and all debug-info consumers first; in particular, deoptimization-state queries
     * are rejected afterward. Code patching must use {@link #getInfopointsForCodeEmission()}.
     * Previously obtained views retain their original infopoints and debug information. They must
     * be dropped by their consumers before that data can be reclaimed. Preserving these views costs
     * a copy of the reference list in addition to the stripped replacements. Debug graphs are not copied.
     */
    public void clearInfopointDebugInfo() {
        checkCodeEmissionStateNotCleared();
        if (infopointDebugInfoCleared) {
            return;
        }
        // Preserve old views and iterators rather than exposing stripped replacements through them.
        ArrayList<Infopoint> emissionInfopoints = new ArrayList<>(infopoints);
        for (int i = 0; i < infopoints.size(); i++) {
            Infopoint infopoint = infopoints.get(i);
            if (infopoint.debugInfo != null) {
                emissionInfopoints.set(i, withoutDebugInfo(infopoint));
            }
        }
        infopoints = emissionInfopoints;
        invalidCallDeoptimizationStates = null;
        infopointDebugInfoCleared = true;
    }

    /**
     * Returns a replacement without debug information for a known infopoint type, or the original
     * infopoint for an unsupported type. Called by {@link #clearInfopointDebugInfo()} only for sites
     * with non-null debug information. Overrides must preserve the concrete type, position, reason
     * and all non-debug fields of any additional types they handle, and leave unknown types intact.
     */
    protected Infopoint withoutDebugInfo(Infopoint infopoint) {
        if (infopoint instanceof Call call) {
            return new Call(call.target, call.pcOffset, call.size, call.direct, null);
        } else if (infopoint instanceof ImplicitExceptionDispatch dispatch) {
            return new ImplicitExceptionDispatch(dispatch.pcOffset, dispatch.dispatchOffset, null);
        } else {
            // Keep other infopoints unchanged rather than assuming their debug payload is unused.
            return infopoint;
        }
    }

    /**
     * Clears code emission state that is no longer needed after the target code bytes, call sites,
     * data patches and annotations have been consumed by the native image writer. This preserves
     * accounting fields such as {@link #getTargetCodeSize()}. Subsequent access to released state is
     * rejected so that a newly introduced late consumer fails at its point of use.
     * Previously obtained views retain their data until their consumers drop those references.
     *
     * @throws IllegalStateException if code-emission state was already cleared
     */
    public void clearCodeEmissionState() {
        checkCodeEmissionStateNotCleared();
        // Detach collections so earlier views cannot mistake released data for an empty result.
        infopoints = null;
        dataPatches = null;
        exceptionHandlers = null;
        marks = null;
        invalidCallDeoptimizationStates = null;
        annotations = null;
        targetCode = null;
    }

    private void checkSourceMappingsNotCleared() {
        if (sourceMapping == null) {
            throw new IllegalStateException("Source mappings were released for " + this);
        }
    }

    private void checkInfopointDebugInfoNotCleared() {
        if (infopointDebugInfoCleared) {
            throw new IllegalStateException("Infopoint debug information was released for " + this);
        }
    }

    private void checkCodeEmissionStateNotCleared() {
        if (infopoints == null) {
            throw new IllegalStateException("Code-emission state was released for " + this);
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException();
        }
    }

    /**
     * Closes this compilation result to future updates.
     */
    public void close(OptionValues options) {
        if (closed) {
            throw new IllegalStateException("Cannot re-close compilation result " + this);
        }
        dataSection.close(options, minDataSectionItemAlignment);
        closed = true;
    }

    public String getCodeSignature() {
        return getSignature(getTargetCode());
    }

    public static String getSignature(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new GraalError(e);
        }
    }

    public record CompilationResultWatermark(int infopointsSize,
                    int dataPatchesSize,
                    int exceptionHandlersSize,
                    int marksSize,
                    int pendingExceptionInfoListSize,
                    int pendingImplicitExceptionListSize) {
    }

    /**
     * @return the sizes of recorded and pending {@link Site}s that either need further patching,
     *         e.g., {@link DataPatch}, or introduce new control flow, e.g.,
     *         {@link ExceptionHandler} or {@link ImplicitExceptionDispatch}.
     */
    public CompilationResultWatermark getSitesWatermark(int pendingExceptionInfoListSize, int pendingImplicitExceptionListSize) {
        checkCodeEmissionStateNotCleared();
        return new CompilationResultWatermark(infopoints.size(), dataPatches.size(), exceptionHandlers.size(), marks.size(), pendingExceptionInfoListSize,
                        pendingImplicitExceptionListSize);
    }

    /**
     * We assume the order of Site lists will not change.
     */
    private boolean verifySites(int codeStart, CompilationResultWatermark watermarkAtCodeStart, int codeEnd, CompilationResultWatermark watermarkAtCodeEnd) {
        for (int i = 0; i < infopoints.size(); i++) {
            int codeOffset = infopoints.get(i).pcOffset;
            if (watermarkAtCodeStart.infopointsSize() <= i && i < watermarkAtCodeEnd.infopointsSize()) {
                assert codeStart <= codeOffset && codeOffset < codeEnd : Assertions.errorMessage(codeOffset, codeStart, codeEnd, infopoints);
            } else {
                assert codeOffset < codeStart || codeOffset >= codeEnd : Assertions.errorMessage(codeOffset, codeStart, codeEnd, infopoints);
            }
        }
        for (int i = 0; i < dataPatches.size(); i++) {
            int codeOffset = dataPatches.get(i).pcOffset;
            if (watermarkAtCodeStart.dataPatchesSize() <= i && i < watermarkAtCodeEnd.dataPatchesSize()) {
                assert codeStart <= codeOffset && codeOffset < codeEnd : Assertions.errorMessage(codeOffset, codeStart, codeEnd, dataPatches);
            } else {
                assert codeOffset < codeStart || codeOffset >= codeEnd : Assertions.errorMessage(codeOffset, codeStart, codeEnd, dataPatches);
            }
        }
        for (int i = 0; i < exceptionHandlers.size(); i++) {
            int codeOffset = exceptionHandlers.get(i).pcOffset;
            if (watermarkAtCodeStart.exceptionHandlersSize() <= i && i < watermarkAtCodeEnd.exceptionHandlersSize()) {
                assert codeStart <= codeOffset && codeOffset < codeEnd : Assertions.errorMessage(codeOffset, codeStart, codeEnd, exceptionHandlers);
            } else {
                assert codeOffset < codeStart || codeOffset >= codeEnd : Assertions.errorMessage(codeOffset, codeStart, codeEnd, exceptionHandlers);
            }
        }
        for (int i = 0; i < marks.size(); i++) {
            int codeOffset = marks.get(i).pcOffset;
            if (watermarkAtCodeStart.marksSize() <= i && i < watermarkAtCodeEnd.marksSize()) {
                assert codeStart <= codeOffset && codeOffset < codeEnd : Assertions.errorMessage(codeOffset, codeStart, codeEnd, marks);
            } else {
                assert codeOffset < codeStart || codeOffset >= codeEnd : Assertions.errorMessage(codeOffset, codeStart, codeEnd, marks);
            }
        }
        return true;
    }

    /**
     * Duplicates the sites between {@code codeStart} and {@code codeEnd} with an offset.
     */
    public void duplicateSites(int codeStart, CompilationResultWatermark watermarkAtCodeStart, int codeEnd, CompilationResultWatermark watermarkAtCodeEnd, int offset) {
        checkCodeEmissionStateNotCleared();
        checkInfopointDebugInfoNotCleared();
        assert verifySites(codeStart, watermarkAtCodeStart, codeEnd, watermarkAtCodeEnd) : Assertions.errorMessage(codeStart, codeEnd);
        for (int i = watermarkAtCodeStart.infopointsSize; i < watermarkAtCodeEnd.infopointsSize; i++) {
            Infopoint infoPoint = infopoints.get(i);
            if (infoPoint instanceof Call call) {
                infopoints.add(new Call(call.target, call.pcOffset + offset, call.size, call.direct, call.debugInfo));
            } else if (infoPoint instanceof ImplicitExceptionDispatch dispatch) {
                infopoints.add(new ImplicitExceptionDispatch(dispatch.pcOffset + offset, dispatch.dispatchOffset, dispatch.debugInfo));
            } else {
                GraalError.guarantee(infoPoint.getClass() == Infopoint.class, "Unsupported Infopoint type %s", infoPoint.getClass());
                infopoints.add(new Infopoint(infoPoint.pcOffset + offset, infoPoint.debugInfo, infoPoint.reason));
            }
        }
        for (int i = watermarkAtCodeStart.dataPatchesSize; i < watermarkAtCodeEnd.dataPatchesSize; i++) {
            DataPatch dataPatch = dataPatches.get(i);
            dataPatches.add(new DataPatch(dataPatch.pcOffset + offset, dataPatch.reference));
        }
        for (int i = watermarkAtCodeStart.exceptionHandlersSize; i < watermarkAtCodeEnd.exceptionHandlersSize; i++) {
            ExceptionHandler handler = exceptionHandlers.get(i);
            exceptionHandlers.add(new ExceptionHandler(handler.pcOffset + offset, handler.handlerPos));
        }
        for (int i = watermarkAtCodeStart.marksSize; i < watermarkAtCodeEnd.marksSize; i++) {
            CodeMark mark = marks.get(i);
            marks.add(new CodeMark(mark.pcOffset + offset, mark.id));
        }
    }
}
