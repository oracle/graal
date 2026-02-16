/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2025, Red Hat Inc. All rights reserved.
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

import java.lang.reflect.Modifier;

import com.oracle.objectfile.SectionName;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.ConstantValueEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.LocalValueEntry;
import com.oracle.objectfile.debugentry.RegisterValueEntry;
import com.oracle.objectfile.debugentry.StackValueEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.range.Range;
import jdk.vm.ci.amd64.AMD64;

import java.util.List;
import java.util.Map;

import static com.oracle.objectfile.pecoff.cv.CVSymbolSubrecord.CVSymbolFrameProcRecord.FRAME_LOCAL_BP;
import static com.oracle.objectfile.pecoff.cv.CVSymbolSubrecord.CVSymbolFrameProcRecord.FRAME_PARAM_BP;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MAX_PRIMITIVE;

final class CVSymbolSubsectionBuilder {

    private final CVDebugInfo cvDebugInfo;
    private final CVSymbolSubsection cvSymbolSubsection;
    private final CVLineRecordBuilder lineRecordBuilder;

    private final String heapName;
    private final short heapRegister;

    /**
     * Create a symbol section by iterating over all classes, emitting types and line numbers as we
     * go. See SubstrateAMD64RegisterConfig.java
     *
     * @param cvDebugInfo debugInfo container
     */
    CVSymbolSubsectionBuilder(CVDebugInfo cvDebugInfo) {
        this.cvDebugInfo = cvDebugInfo;
        this.cvSymbolSubsection = new CVSymbolSubsection(cvDebugInfo);
        this.lineRecordBuilder = new CVLineRecordBuilder(cvDebugInfo);
        this.heapName = SectionName.SVM_HEAP.getFormatDependentName(cvDebugInfo.getCVSymbolSection().getOwner().getFormat());
        /* For isolates, Graal currently uses r14 as the heap base; this code will handle r8-r15. */
        assert AMD64.r8.number <= CVDebugInfo.getHeapbaseRegister() && CVDebugInfo.getHeapbaseRegister() <= AMD64.r15.number;
        this.heapRegister = CVRegisterUtil.getCVRegister(CVDebugInfo.getHeapbaseRegister(), CVDebugInfo.POINTER_LENGTH);
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
            if (typeEntry instanceof ClassEntry classEntry) {
                buildClass(classEntry);
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

        /* Define all the functions in this class all functions defined in this class. */
        classEntry.compiledMethods().forEach(this::buildFunction);

        /* Define the class itself. */
        addTypeRecords(classEntry);

        /* Add manifested static fields as S_GDATA32 records. */
        classEntry.getFields().stream().filter(CVSymbolSubsectionBuilder::isManifestedStaticField).forEach(f -> {
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
        final Range primaryRange = compiledEntry.primary();

        /* The name as it will appear in the debugger. */
        final String debuggerName = CVNames.methodNameToCodeViewName(primaryRange.getMethodEntry());

        /* The name as exposed to the linker. */
        final String externalName = primaryRange.getSymbolName();

        /* add function definition. */
        final int functionTypeIndex = addTypeRecords(compiledEntry);
        final byte funcFlags = 0;
        CVSymbolSubrecord.CVSymbolGProc32IdRecord proc32 = new CVSymbolSubrecord.CVSymbolGProc32IdRecord(cvDebugInfo, externalName, debuggerName, 0, 0, 0,
                        primaryRange.getHiOffset() - primaryRange.getLoOffset(),
                        0, 0, functionTypeIndex, (short) 0, funcFlags);
        addSymbolRecord(proc32);

        final int frameFlags = FRAME_LOCAL_BP + FRAME_PARAM_BP;
        addSymbolRecord(new CVSymbolSubrecord.CVSymbolFrameProcRecord(cvDebugInfo, compiledEntry.frameSize(), frameFlags));

        addParameters(compiledEntry, primaryRange.getVarRangeMap());
        /* In the future: addLocals(compiledEntry, varRangeMap); */

        /* S_PROC_ID_END add end record. */
        addSymbolRecord(new CVSymbolSubrecord.CVSymbolProcIdEndRecord(cvDebugInfo));

        /* Add line number records. */
        addLineNumberRecords(compiledEntry);
    }

    void addParameters(CompiledMethodEntry primaryEntry, Map<LocalEntry, List<Range>> varRangeMap) {
        final Range primaryRange = primaryEntry.primary();
        /* The name as exposed to the linker. */
        final String externalName = primaryRange.getSymbolName();
        final MethodEntry method = primaryRange.getMethodEntry();

        /* define function parameters */
        assert Modifier.isStatic(method.getModifiers()) || !method.getParams().isEmpty();
        for (LocalEntry paramInfo : method.getParams()) {
            final TypeEntry paramType = paramInfo.type();
            final int typeIndex = cvDebugInfo.getCVTypeSection().getIndexForPointer(paramType);
            emitLocal(paramInfo, varRangeMap, paramInfo.name(), paramType, typeIndex, true, externalName, primaryRange);
        }
    }

    private static int infoTypeToInt(LocalValueEntry info) {
        return switch (info) {
            case RegisterValueEntry p -> p.regIndex();
            case StackValueEntry p -> -p.stackSlot();
            default -> 0;
        };
    }

    void emitLocal(LocalEntry info, Map<LocalEntry, List<Range>> varRangeMap, String name, TypeEntry typeEntry, int typeIndex, boolean isParam,
                    String procName, Range range) {
        short flags = isParam ? CVSymbolSubrecord.CVSymbolLocalRecord.S_LOCAL_FLAGS_IS_PARAM : 0;
        List<Range> ranges = varRangeMap.get(info);
        addSymbolRecord(new CVSymbolSubrecord.CVSymbolLocalRecord(cvDebugInfo, name, typeIndex, flags));
        long currentHigh = Integer.MIN_VALUE;
        int registerOrSlot = 0; /* -slot or +register or 0=unknown */
        CVSymbolSubrecord.CVSymbolDefRangeBase currentRecord = null;
        for (Range subrange : ranges) {
            LocalValueEntry value = subrange.lookupValue(info);
            if (value != null) {
                if (subrange.getLo() == currentHigh && registerOrSlot == infoTypeToInt(value)) {
                    /* if we can, merge records */
                    currentHigh = subrange.getHi();
                    if (currentRecord != null) {
                        long reclen = currentHigh - currentRecord.procOffset - range.getLo();
                        if (reclen > 0xffff) {
                            /*
                             * Variable span is too large to fit into 16 bits; emit what we have so
                             * far. Future work could emit more ranges or utilize gaps.
                             */
                            currentRecord.length = (short) 0xffff;
                            return;
                        }
                        currentRecord.length = (short) reclen;
                    }
                    continue;
                }
                currentHigh = subrange.getHi();
                registerOrSlot = infoTypeToInt(value);
                switch (value) {
                    case RegisterValueEntry v -> {
                        short cvreg = CVRegisterUtil.getCVRegister(v.regIndex(), typeEntry);
                        /*
                         * It could be that Graal has allocated a register that we don't know how to
                         * represent in CodeView. In that case, getCVRegister() will return a
                         * negative number and no local variable record is issued; instead a warning
                         */
                        if (cvreg >= 0) {
                            currentRecord = new CVSymbolSubrecord.CVSymbolDefRangeRegisterRecord(cvDebugInfo, procName, (int) (subrange.getLo() - range.getLo()),
                                            (short) (subrange.getHi() - subrange.getLo()), cvreg);
                            addSymbolRecord(currentRecord);
                        } else {
                            cvDebugInfo.getCVSymbolSection().warn("Register %s unimplemented in Windows debug support", v.toString());
                        }
                    }
                    case StackValueEntry v -> {
                        currentRecord = new CVSymbolSubrecord.CVSymbolDefRangeFramepointerRel(cvDebugInfo, procName, (int) (subrange.getLo() - range.getLo()),
                                        (short) (subrange.getHi() - subrange.getLo()),
                                        v.stackSlot());
                        addSymbolRecord(currentRecord);
                    }
                    case ConstantValueEntry v -> {
                        /* For now, silently ignore constant definitions an parameters. */
                        /* JavaConstant constant = value.constantValue(); */
                    }
                    default -> {
                        /* Unimplemented - this is a surprise. */
                        assert (false);
                    }
                }
            }
        }
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
