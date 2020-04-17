/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.PrimaryEntry;
import com.oracle.objectfile.debugentry.Range;
import org.graalvm.compiler.debug.DebugContext;

import static com.oracle.objectfile.pecoff.cv.CVConstants.functionNamesHashArgs;
import static com.oracle.objectfile.pecoff.cv.CVConstants.replaceMainFunctionName;
import static com.oracle.objectfile.pecoff.cv.CVConstants.emitUnadornedMain;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_NOTYPE;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_VOID;

final class CVSymbolRecordBuilder {

    private final CVDebugInfo cvDebugInfo;
    private final CVSymbolSubsection symbolRecord;
    private DebugContext debugContext = null;

    CVSymbolRecordBuilder(CVDebugInfo cvDebugInfo) {
        this.symbolRecord = new CVSymbolSubsection(cvDebugInfo);
        this.cvDebugInfo = cvDebugInfo;
    }

    /**
     * build DEBUG_S_SYMBOLS record from all classEntries. (could probably build one per class or
     * one per function)
     */
    void build(DebugContext theDebugContext) {
        this.debugContext = theDebugContext;
        for (ClassEntry classEntry : cvDebugInfo.getPrimaryClasses()) {
            build(classEntry);
        }
        cvDebugInfo.getCVSymbolSection().addRecord(symbolRecord);
    }

    /**
     * build all debug info for a classEntry. (does not yet handle member variables)
     *
     * @param classEntry current class
     */
    private void build(ClassEntry classEntry) {
        String previousMethodName = "";
        for (PrimaryEntry primaryEntry : classEntry.getPrimaryEntries()) {
            Range primaryRange = primaryEntry.getPrimary();
            if (primaryRange.getFileName() != null) {
                // for each function
                String newMethodName = fixMethodName(primaryRange);
                if (!newMethodName.equals(previousMethodName)) {
                    previousMethodName = newMethodName;
                    build(primaryEntry, newMethodName);
                }
            }
        }
    }

    /**
     * emit records for each function: PROC32 S_FRAMEPROC S_END and line number records. (later:
     * type records as required)
     *
     * @param primaryEntry primary entry for this function
     * @param methodName method name alias as it will be seen by the user
     */
    private void build(PrimaryEntry primaryEntry, String methodName) {
        final Range primaryRange = primaryEntry.getPrimary();
        // debug("addfunc(" + methodName + ") numtypes = %d\n",
        // cvDebugInfo.getCVTypeSection().getRecords().size());

        /* S_PROC32 add function definition */
        int functionTypeIndex = addTypeRecords(primaryEntry);
        byte funcFlags = 0;
        CVSymbolSubrecord.CVSymbolGProc32Record proc32 = new CVSymbolSubrecord.CVSymbolGProc32Record(cvDebugInfo, methodName, 0, 0, 0, primaryRange.getHi() - primaryRange.getLo(), 0, 0,
                        functionTypeIndex, primaryRange.getLo(), (short) 0, funcFlags);
        addToSymbolRecord(proc32);

        /* S_FRAMEPROC add frame definitions */
        int asynceh = 1 << 9;  /* aync eh  (msc uses 1, clang uses 0) */
        int localBP = 1 << 14; /* local base pointer = SP (0=none, 1=sp, 2=bp 3=r13) */
        int paramBP = 1 << 16; /* param base pointer = SP */
        int frameFlags = asynceh + localBP + paramBP; /* LLVM uses 0x14000; */
        addToSymbolRecord(new CVSymbolSubrecord.CVSymbolFrameProcRecord(cvDebugInfo, primaryRange.getHi() - primaryRange.getLo(), frameFlags));

        /* TODO: add local variables, and their types */
        /* TODO: add block definitions */

        /* S_END add end record */
        addToSymbolRecord(new CVSymbolSubrecord.CVSymbolEndRecord(cvDebugInfo));
        addLineNumberRecords(primaryEntry, methodName);
    }

    private boolean noMainFound = true;

    /**
     * renames a method name ot something user friendly in the debugger. (does not affect external
     * symbols used by linker)
     *
     * first main function becomes class.main (unless replaceMainFunctionName is non-null) if
     * functionNamesHashArgs is true (which it must be for the linker to work properly) all other
     * functions become class.function.999 (where 999 is a hash of the arglist)
     *
     * @param range Range contained in the method of interest
     * @return user debugger friendly method name
     */
    private String fixMethodName(Range range) {
        final String methodName;
        if (replaceMainFunctionName != null && noMainFound && range.getMethodName().equals("main")) {
            noMainFound = false;
            methodName = replaceMainFunctionName;
        } else if (emitUnadornedMain && noMainFound && range.getMethodName().equals("main")) {
            // TODO: check for static void main(String args[])
            noMainFound = false;
            methodName = range.getClassAndMethodName();
        } else if (functionNamesHashArgs) {
            long hash = range.getParamNames().hashCode() & 0xffffffffL;
            methodName = range.getClassAndMethodName() + "." + hash;
        } else {
            methodName = range.getFullMethodName();
        }
        // debug("replacing %s with %s\n", range.getFullMethodName(), methodName);
        return methodName;
    }

    private void addLineNumberRecords(PrimaryEntry primaryEntry, String methodName) {
        CVLineRecord record = new CVLineRecordBuilder(cvDebugInfo).build(primaryEntry, methodName);
        /*
         * if the builder decides this entry is uninteresting, we don't build a record. for example,
         * Graal intrinsics may be uninteresting.
         */
        if (record != null) {
            cvDebugInfo.getCVSymbolSection().addRecord(record);
        }
    }

    private void addToSymbolRecord(CVSymbolSubrecord record) {
        // debug("adding symbol subrecord: %s\n", record);
        symbolRecord.addRecord(record);
    }

    /**
     * add type records for function. (later add arglist, and return type and local types)
     *
     * @param entry primaryEntry containing entities whoses type records must be added
     *
     * @return type index of function type
     */
    private int addTypeRecords(@SuppressWarnings("unused") PrimaryEntry entry) {
        CVTypeRecord.CVTypeArglistRecord argListType = addTypeRecord(new CVTypeRecord.CVTypeArglistRecord().add(T_NOTYPE));
        CVTypeRecord funcType = addTypeRecord(new CVTypeRecord.CVTypeProcedureRecord().returnType(T_VOID).argList(argListType));
        return funcType.getSequenceNumber();
    }

    private <T extends CVTypeRecord> T addTypeRecord(T record) {
        cvDebugInfo.getCVSymbolSection().verboseLog(debugContext, "added type record: %s hash=%d\n", record, record.hashCode());
        return cvDebugInfo.getCVTypeSection().addRecord(record);
    }
}
