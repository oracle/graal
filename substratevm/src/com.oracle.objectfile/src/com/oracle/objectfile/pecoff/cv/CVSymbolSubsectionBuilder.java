/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

import com.oracle.objectfile.SectionName;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;

import java.lang.reflect.Modifier;

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_INTEGRAL;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugPrimitiveTypeInfo.FLAG_NUMERIC;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_CL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_CX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_DI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_DIL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_DL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_DX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_ECX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_EDI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_EDX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_ESI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R8;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R8B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R8D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R8W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R9;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R9B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R9D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R9W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RCX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RDI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RDX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RSI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_SI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_SIL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM0L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM0_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM1L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM1_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM2L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM2_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM3L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM3_0;
import static com.oracle.objectfile.pecoff.cv.CVSymbolSubrecord.CVSymbolFrameProcRecord.FRAME_ASYNC_EH;
import static com.oracle.objectfile.pecoff.cv.CVSymbolSubrecord.CVSymbolFrameProcRecord.FRAME_LOCAL_BP;
import static com.oracle.objectfile.pecoff.cv.CVSymbolSubrecord.CVSymbolFrameProcRecord.FRAME_PARAM_BP;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MAX_PRIMITIVE;

final class CVSymbolSubsectionBuilder {

    private static final short[] javaGP64registers = {CV_AMD64_RDX, CV_AMD64_R8, CV_AMD64_R9, CV_AMD64_RDI, CV_AMD64_RSI, CV_AMD64_RCX};
    private static final short[] javaGP32registers = {CV_AMD64_EDX, CV_AMD64_R8D, CV_AMD64_R9D, CV_AMD64_EDI, CV_AMD64_ESI, CV_AMD64_ECX};
    private static final short[] javaGP16registers = {CV_AMD64_DX, CV_AMD64_R8W, CV_AMD64_R9W, CV_AMD64_DI, CV_AMD64_SI, CV_AMD64_CX};
    private static final short[] javaGP8registers = {CV_AMD64_DL, CV_AMD64_R8B, CV_AMD64_R9B, CV_AMD64_DIL, CV_AMD64_SIL, CV_AMD64_CL};
    private static final short[] javaFP64registers = {CV_AMD64_XMM0L, CV_AMD64_XMM1L, CV_AMD64_XMM2L, CV_AMD64_XMM3L};
    private static final short[] javaFP32registers = {CV_AMD64_XMM0_0, CV_AMD64_XMM1_0, CV_AMD64_XMM2_0, CV_AMD64_XMM3_0};

    private final CVDebugInfo cvDebugInfo;
    private final CVSymbolSubsection cvSymbolSubsection;
    private final CVLineRecordBuilder lineRecordBuilder;

    private final String heapName;
    private final short heapRegister;

    CVSymbolSubsectionBuilder(CVDebugInfo cvDebugInfo) {
        this.cvDebugInfo = cvDebugInfo;
        this.cvSymbolSubsection = new CVSymbolSubsection(cvDebugInfo);
        this.lineRecordBuilder = new CVLineRecordBuilder(cvDebugInfo);
        this.heapName = SectionName.SVM_HEAP.getFormatDependentName(cvDebugInfo.getCVSymbolSection().getOwner().getFormat());
        /* For isolates, Graal currently uses r14; this code will handle r8-r15. */
        assert 8 <= cvDebugInfo.getHeapbaseRegister() && cvDebugInfo.getHeapbaseRegister() <= 15;
        this.heapRegister = (short) (CV_AMD64_R8 + cvDebugInfo.getHeapbaseRegister() - 8);
    }

    /**
     * Build DEBUG_S_SYMBOLS record from all classEntries. (CodeView 4 format allows us to build one
     * per class or one per function or one big record - which is what we do here).
     *
     * The CodeView symbol section Prolog is also a CVSymbolSubsection, but it is not built in this
     * class.
     */
    void build() {
        /* Loop over all classes defined in this module. */
        for (TypeEntry typeEntry : cvDebugInfo.getTypes()) {
            /* Add type record for this entry. */
            if (typeEntry.isClass()) {
                buildClass((ClassEntry) typeEntry);
            } else {
                addTypeRecords(typeEntry);
            }
        }
        cvDebugInfo.getCVSymbolSection().addRecord(cvSymbolSubsection);
    }

    /**
     * Build all debug info for a classEntry.
     *
     * @param classEntry current class
     */
    private void buildClass(ClassEntry classEntry) {

        /* Define all functions defined in this class. */
        classEntry.compiledEntries().toList().forEach(this::buildFunction);

        /* Define the class itself. */
        addTypeRecords(classEntry);

        /* Add manifested static fields as S_GDATA32 records. */
        classEntry.fields().filter(CVSymbolSubsectionBuilder::isManifestedStaticField).forEach(f -> {
            int typeIndex = cvDebugInfo.getCVTypeSection().getIndexForPointer(f.getValueType());
            String displayName = CVNames.fieldNameToCodeViewName(f);
            if (cvDebugInfo.useHeapBase()) {
                /*
                 * Isolates are enabled. Static member is located at REL32 offset from heap base
                 * register.
                 */
                addSymbolRecord(new CVSymbolSubrecord.CVSymbolRegRel32Record(cvDebugInfo, displayName, typeIndex, f.getOffset(), heapRegister));
            } else {
                /* Isolates are disabled. Static member is located at offset from heap begin. */
                addSymbolRecord(new CVSymbolSubrecord.CVSymbolGData32Record(cvDebugInfo, heapName, displayName, typeIndex, f.getOffset(), (short) 0));
            }
        });
    }

    private static boolean isManifestedStaticField(FieldEntry fieldEntry) {
        return Modifier.isStatic(fieldEntry.getModifiers()) && fieldEntry.getOffset() >= 0;
    }

    /**
     * Emit records for each function: PROC32 S_FRAMEPROC S_END and line number records. (later:
     * type records as required).
     *
     * @param compiledEntry compiled method for this function
     */
    private void buildFunction(CompiledMethodEntry compiledEntry) {

        final Range primaryRange = compiledEntry.getPrimary();

        /* The name as it will appear in the debugger. */
        final String debuggerName = CVNames.methodNameToCodeViewName(primaryRange.getMethodEntry());

        /* The name as exposed to the linker. */
        final String externalName = primaryRange.getSymbolName();

        /* add function definition. */
        final int functionTypeIndex = addTypeRecords(compiledEntry);
        final byte funcFlags = 0;
        CVSymbolSubrecord.CVSymbolGProc32IdRecord proc32 = new CVSymbolSubrecord.CVSymbolGProc32IdRecord(cvDebugInfo, externalName, debuggerName, 0, 0, 0, primaryRange.getHi() - primaryRange.getLo(), 0,
                        0, functionTypeIndex, (short) 0, funcFlags);
        addSymbolRecord(proc32);

        final int frameFlags = FRAME_LOCAL_BP + FRAME_PARAM_BP;
        addSymbolRecord(new CVSymbolSubrecord.CVSymbolFrameProcRecord(cvDebugInfo, compiledEntry.getFrameSize(), frameFlags));

        addLocals(compiledEntry);

        /* S_PROC_ID_END add end record. */
        addSymbolRecord(new CVSymbolSubrecord.CVSymbolProcIdEndRecord(cvDebugInfo));

        /* Add line number records. */
        addLineNumberRecords(compiledEntry);
    }

    void addLocals(CompiledMethodEntry primaryEntry) {
        final Range primaryRange = primaryEntry.getPrimary();
        /* The name as exposed to the linker. */
        final String externalName = primaryRange.getSymbolName();

        /* Add register parameters - only valid for the first instruction or two. */

        MethodEntry method = primaryRange.getMethodEntry();
        int gpRegisterIndex = 0;
        int fpRegisterIndex = 0;

        /* define 'this' as a local just as we define other object pointers */
        if (!Modifier.isStatic(method.getModifiers())) {
            final TypeEntry thisType = primaryEntry.getClassEntry();
            DebugInfoProvider.DebugLocalInfo thisparam = method.getThisParam();
            final int typeIndex = cvDebugInfo.getCVTypeSection().getIndexForPointer(thisType);
            addSymbolRecord(new CVSymbolSubrecord.CVSymbolLocalRecord(cvDebugInfo, thisparam.name(), typeIndex, 1));
            addSymbolRecord(new CVSymbolSubrecord.CVSymbolDefRangeRegisterRecord(cvDebugInfo, javaGP64registers[gpRegisterIndex], externalName, 0, 8));
            addSymbolRecord(new CVSymbolSubrecord.CVSymbolDefRangeFramepointerRelFullScope(cvDebugInfo, 0));
            gpRegisterIndex++;
        }

        /* define function parameters (p1, p2...) according to the calling convention */
        for (int i = 0; i < method.getParamCount(); i++) {
            final DebugInfoProvider.DebugLocalInfo paramInfo = method.getParam(i);
            final TypeEntry paramType = method.getParamType(i);
            final int typeIndex = cvDebugInfo.getCVTypeSection().addTypeRecords(paramType).getSequenceNumber();
            if (paramType.isPrimitive()) {
                /* simple primitive */
                final PrimitiveTypeEntry primitiveTypeEntry = (PrimitiveTypeEntry) paramType;
                final boolean isFloatingPoint = ((primitiveTypeEntry.getFlags() & FLAG_NUMERIC) != 0 && (primitiveTypeEntry.getFlags() & FLAG_INTEGRAL) == 0);
                if (isFloatingPoint) {
                    /* floating point primitive */
                    if (fpRegisterIndex < javaFP64registers.length) {
                        final short register = paramType.getSize() == Double.BYTES ? javaFP64registers[fpRegisterIndex] : javaFP32registers[fpRegisterIndex];
                        addSymbolRecord(new CVSymbolSubrecord.CVSymbolLocalRecord(cvDebugInfo, paramInfo.name(), typeIndex, 1));
                        addSymbolRecord(new CVSymbolSubrecord.CVSymbolDefRangeRegisterRecord(cvDebugInfo, register, externalName, 0, 8));
                        addSymbolRecord(new CVSymbolSubrecord.CVSymbolDefRangeFramepointerRelFullScope(cvDebugInfo, 0));
                        fpRegisterIndex++;
                    } else {
                        /* TODO: handle stack parameter; keep track of stack offset, etc. */
                        break;
                    }
                } else if (gpRegisterIndex < javaGP64registers.length) {
                    final short register;
                    if (paramType.getSize() == 8) {
                        register = javaGP64registers[gpRegisterIndex];
                    } else if (paramType.getSize() == 4) {
                        register = javaGP32registers[gpRegisterIndex];
                    } else if (paramType.getSize() == 2) {
                        register = javaGP16registers[gpRegisterIndex];
                    } else if (paramType.getSize() == 1) {
                        register = javaGP8registers[gpRegisterIndex];
                    } else {
                        register = 0; /* Avoid warning. */
                        throw new RuntimeException("Unknown primitive (type" + paramType.getTypeName() + ") size:" + paramType.getSize());
                    }
                    addSymbolRecord(new CVSymbolSubrecord.CVSymbolLocalRecord(cvDebugInfo, paramInfo.name(), typeIndex, 1));
                    addSymbolRecord(new CVSymbolSubrecord.CVSymbolDefRangeRegisterRecord(cvDebugInfo, register, externalName, 0, 8));
                    addSymbolRecord(new CVSymbolSubrecord.CVSymbolDefRangeFramepointerRelFullScope(cvDebugInfo, 8));
                    gpRegisterIndex++;
                } else {
                    /* TODO: handle stack parameter; keep track of stack offset, etc. */
                    break;
                }
            } else {
                /* Java object. */
                if (gpRegisterIndex < javaGP64registers.length) {
                    addSymbolRecord(new CVSymbolSubrecord.CVSymbolLocalRecord(cvDebugInfo, paramInfo.name(), typeIndex, 1));
                    addSymbolRecord(new CVSymbolSubrecord.CVSymbolDefRangeRegisterRecord(cvDebugInfo, javaGP64registers[gpRegisterIndex], externalName, 0, 8));
                    addSymbolRecord(new CVSymbolSubrecord.CVSymbolDefRangeFramepointerRelFullScope(cvDebugInfo, 0));
                    gpRegisterIndex++;
                } else {
                    /* TODO: handle stack parameter; keep track of stack offset, etc. */
                    break;
                }
            }
        }
        /* TODO: add entries for stack parameters. */
        /* TODO: add local variables, and their types. */
        /* TODO: add block definitions. */
    }

    private void addLineNumberRecords(CompiledMethodEntry compiledEntry) {
        CVLineRecord lineRecord = lineRecordBuilder.build(compiledEntry);
        /*
         * If there are no file entries (perhaps for a synthetic function?), we don't add this
         * record.
         */
        if (!lineRecord.isEmpty()) {
            cvDebugInfo.getCVSymbolSection().addRecord(lineRecord);
        }
    }

    /**
     * Add a record to the symbol subsection. A symbol subsection is contained within the top level
     * .debug$S symbol section.
     *
     * @param record the symbol subrecord to add.
     */
    private void addSymbolRecord(CVSymbolSubrecord record) {
        cvSymbolSubsection.addRecord(record);
    }

    /**
     * Add type records for a class and all its members.
     *
     * @param typeEntry class to add records for.
     */
    private void addTypeRecords(TypeEntry typeEntry) {
        int typeIdx = cvDebugInfo.getCVTypeSection().addTypeRecords(typeEntry).getSequenceNumber();

        if (typeIdx > MAX_PRIMITIVE) {
            /*
             * Adding an S_UDT (User Defined Type) record ensures the linker doesn't throw away the
             * type definition.
             */
            CVSymbolSubrecord.CVSymbolUDTRecord udtRecord = new CVSymbolSubrecord.CVSymbolUDTRecord(cvDebugInfo, typeIdx, CVNames.typeNameToCodeViewName(typeEntry.getTypeName()));
            addSymbolRecord(udtRecord);
        }
    }

    /**
     * Add type records for a method.
     *
     * @param entry compiled method containing entities whos type records must be added
     * @return type index of function type
     */
    private int addTypeRecords(CompiledMethodEntry entry) {
        return cvDebugInfo.getCVTypeSection().addTypeRecords(entry).getSequenceNumber();
    }
}
