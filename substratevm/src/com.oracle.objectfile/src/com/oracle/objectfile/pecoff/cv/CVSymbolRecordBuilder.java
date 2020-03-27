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

import static com.oracle.objectfile.pecoff.cv.CVConstants.functionNamesHashArgs;
import static com.oracle.objectfile.pecoff.cv.CVConstants.replaceMainFunctionName;
import static com.oracle.objectfile.pecoff.cv.CVConstants.emitUnadornedMain;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_NOTYPE;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_VOID;

final class CVSymbolRecordBuilder {

    private final CVSections cvSections;
    private final CVTypeSectionImpl typeSection;
    private final CVSymbolSubsection symbolRecord;

    CVSymbolRecordBuilder(CVSections cvSections) {
        this.symbolRecord = new CVSymbolSubsection(cvSections);
        this.cvSections = cvSections;
        this.typeSection = cvSections.getCVTypeSection();
    }

    /**
     * build DEBUG_S_SYMBOLS record from all classEntries
     * (could probably build one per class or one per function)
     */
    void build() {
        for (ClassEntry classEntry : cvSections.getPrimaryClasses()) {
            build(classEntry);
        }
        cvSections.getCVSymbolSection().addRecord(symbolRecord);
    }

    /**
     * build all debug info for a classEntry
     * (does not yet handle member variables)
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
     * emit records for each function:
     *   PROC32
     *   S_FRAMEPROC
     *   S_END
     *   (later: type records as required)
     *   line number records
     *
     * @param primaryEntry primary entry for this function
     * @param methodName method name alias as it will be seen by the user
     */
    private void build(PrimaryEntry primaryEntry, String methodName) {
        final Range primaryRange = primaryEntry.getPrimary();
        CVUtil.debug("addfunc(" + methodName + ") numtypes = %d\n", typeSection.getRecords().size());
        int functionTypeIndex = addTypeRecords(primaryEntry);
        byte funcFlags = 0;
        CVSymbolSubrecord.CVSymbolGProc32Record proc32 = new CVSymbolSubrecord.CVSymbolGProc32Record(cvSections, methodName, 0, 0, 0, primaryRange.getHi() - primaryRange.getLo(), 0, 0, functionTypeIndex, primaryRange.getLo(), (short) 0, funcFlags);
        addToSymbolRecord(proc32);
        int frameFlags = 0; /* LLVM uses 0x14000; */
        addToSymbolRecord(new CVSymbolSubrecord.CVSymbolFrameProcRecord(cvSections, primaryRange.getHi() - primaryRange.getLo(), frameFlags));
        /* TODO: add local variables, and their types */
        addToSymbolRecord(new CVSymbolSubrecord.CVSymbolEndRecord(cvSections));
        addLineNumberRecords(primaryEntry, methodName);
    }

    private boolean noMainFound = true;

    /**
     * renames a method name ot something user friendly in the debugger
     * (does not affect external symbols used by linker)
     *
     * first main function becomes class.main (unless replaceMainFunctionName is non-null)
     * if functionNamesHashArgs is true (which it must be for the linker to work properly)
     * all other functions become class.function.999 (where 999 is a hash of the arglist)
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
        CVUtil.debug("replacing %s with %s\n", range.getFullMethodName(), methodName);
        return methodName;
    }

    private void addLineNumberRecords(PrimaryEntry primaryEntry, String methodName) {
        CVLineRecord record = new CVLineRecordBuilder(cvSections).build(primaryEntry, methodName);
        /*
         * if the builder decides this entry is uninteresting, we don't build a record.
         * for example, Graal intrinsics may be uninteresting.
         */
        if (record != null) {
            cvSections.getCVSymbolSection().addRecord(record);
        }
    }

    private void addToSymbolRecord(CVSymbolSubrecord record) {
        CVUtil.debug("adding symbol subrecord: %s\n", record);
        symbolRecord.addRecord(record);
    }

    /**
     * add type records for function
     * (later add arglist, and return type and local types)
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
        return cvSections.getCVTypeSection().addRecord(record);
    }
}
