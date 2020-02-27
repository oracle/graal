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

import com.oracle.objectfile.pecoff.cv.DebugInfoBase.ClassEntry;
import com.oracle.objectfile.pecoff.cv.DebugInfoBase.PrimaryEntry;
import com.oracle.objectfile.pecoff.cv.DebugInfoBase.Range;

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

    private boolean noMainFound = true;

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
            long hash = ((long) range.getParamNames().hashCode()) & 0xffffffffL;
            methodName = range.getClassAndMethodName() + "." + hash;
        } else {
            methodName = range.getClassAndMethodNameWithParams();
        }
        CVUtil.debug("replacing %s with %s\n", range.getClassAndMethodNameWithParams(), methodName);
        return methodName;
    }

    void build() {
        /* A module has a set of (function def, block def, linenumbers) for each function */
        String previousMethodName = "";
        for (ClassEntry classEntry : cvSections.getPrimaryClasses()) {
            for (PrimaryEntry primary : classEntry.getPrimaryEntries()) {
                Range range = primary.getPrimary();
                // for each function
                String newMethodName = fixMethodName(range);
                if (!newMethodName.equals(previousMethodName)) {
                    previousMethodName = newMethodName;
                    processFunction(newMethodName, range);
                    addLineNumberRecords(newMethodName, primary);
                }
            }
        }
        cvSections.getCVSymbolSection().addRecord(symbolRecord);
    }

    private void processFunction(String methodName, Range range) {

        CVUtil.debug("addfunc(" + methodName + ") numtypes = %d\n", typeSection.getRecords().size());
        int functionTypeIndex = addTypeRecords();
        byte funcFlags = 0;
        CVSymbolSubrecord.CVSymbolGProc32Record proc32 = new CVSymbolSubrecord.CVSymbolGProc32Record(cvSections, methodName, 0, 0, 0, range.getHi() - range.getLo(), 0, 0, functionTypeIndex, range.getLo(), (short) 0, funcFlags);
        addToSymbolRecord(proc32);
        int frameFlags = 0; /* LLVM uses 0x14000; */
        addToSymbolRecord(new CVSymbolSubrecord.CVSymbolFrameProcRecord(cvSections, range.getHi() - range.getLo(), frameFlags));
        /* TODO: add local variavles, and their types */
        addToSymbolRecord(new CVSymbolSubrecord.CVSymbolEndRecord(cvSections));
    }

    private void addLineNumberRecords(String methodName, PrimaryEntry primary) {
        CVLineRecord record = new CVLineRecordBuilder(cvSections).build(methodName, primary);
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

    private int addTypeRecords() {
        /* add type records for function (later add arglist, and arrlist and local types) */
        CVTypeRecord.CVTypeArglistRecord argListType = addTypeRecord(new CVTypeRecord.CVTypeArglistRecord().add(T_NOTYPE));
        CVTypeRecord funcType = addTypeRecord(new CVTypeRecord.CVTypeProcedureRecord().returnType(T_VOID).argList(argListType));
        return funcType.getSequenceNumber();
    }

    private <T extends CVTypeRecord> T addTypeRecord(T record) {
        return cvSections.getCVTypeSection().addRecord(record);
    }
}
