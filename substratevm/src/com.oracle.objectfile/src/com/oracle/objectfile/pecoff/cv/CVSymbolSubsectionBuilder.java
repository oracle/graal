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

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.Range;

import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_NOTYPE;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_VOID;

final class CVSymbolSubsectionBuilder {

    private final CVDebugInfo cvDebugInfo;
    private final CVSymbolSubsection cvSymbolSubsection;
    private final CVLineRecordBuilder lineRecordBuilder;

    private boolean noMainFound = true;

    CVSymbolSubsectionBuilder(CVDebugInfo cvDebugInfo) {
        this.cvDebugInfo = cvDebugInfo;
        this.cvSymbolSubsection = new CVSymbolSubsection(cvDebugInfo);
        this.lineRecordBuilder = new CVLineRecordBuilder(cvDebugInfo);
    }

    /**
     * Build DEBUG_S_SYMBOLS record from all classEntries. (CodeView 4 format allows us to build one
     * per class or one per function or one big record - which is what we do here).
     *
     * The CodeView symbol section Prolog is also a CVSymbolSubsection, but it is not build in this
     * class.
     */
    void build() {
        /* loop over all classes defined in this module. */
        for (ClassEntry classEntry : cvDebugInfo.getInstanceClasses()) {
            build(classEntry);
        }
        cvDebugInfo.getCVSymbolSection().addRecord(cvSymbolSubsection);
    }

    /**
     * Build all debug info for a classEntry. (does not yet handle member variables).
     *
     * @param classEntry current class
     */
    private void build(ClassEntry classEntry) {
        /* Loop over all functions defined in this class. */
        classEntry.compiledEntries().forEach(compiledEntry -> build(compiledEntry));
    }

    /**
     * Emit records for each function: PROC32 S_FRAMEPROC S_END and line number records. (later:
     * type records as required).
     *
     * @param compiledEntry compiled method for this function
     */
    private void build(CompiledMethodEntry compiledEntry) {
        final Range primaryRange = compiledEntry.getPrimary();

        /* The name as it will appear in the debugger. */
        final String debuggerName = getDebuggerName(primaryRange);

        /* The name as exposed to the linker. */
        final String externalName = primaryRange.getSymbolName();

        /* S_PROC32 add function definition. */
        int functionTypeIndex = addTypeRecords(compiledEntry);
        byte funcFlags = 0;
        CVSymbolSubrecord.CVSymbolGProc32Record proc32 = new CVSymbolSubrecord.CVSymbolGProc32Record(cvDebugInfo, externalName, debuggerName, 0, 0, 0, primaryRange.getHi() - primaryRange.getLo(), 0,
                        0, functionTypeIndex, primaryRange.getLo(), (short) 0, funcFlags);
        addToSymbolSubsection(proc32);

        /* S_FRAMEPROC add frame definitions. */
        int asynceh = 1 << 9; /* Async exception handling (vc++ uses 1, clang uses 0). */
        int localBP = 1 << 14; /* Local base pointer = SP (0=none, 1=sp, 2=bp 3=r13). */
        int paramBP = 1 << 16; /* Param base pointer = SP. */
        int frameFlags = asynceh + localBP + paramBP; /* NB: LLVM uses 0x14000. */
        addToSymbolSubsection(new CVSymbolSubrecord.CVSymbolFrameProcRecord(cvDebugInfo, compiledEntry.getFrameSize(), frameFlags));

        /* TODO: add local variables, and their types. */
        /* TODO: add block definitions. */

        /* S_END add end record. */
        addToSymbolSubsection(new CVSymbolSubrecord.CVSymbolEndRecord(cvDebugInfo));

        /* Add line number records. */
        addLineNumberRecords(compiledEntry);
    }

    /**
     * Rename function names for usability or functionality.
     *
     * First encountered main function becomes class.main. This is for usability.
     *
     * All other functions become class.function.999 (where 999 is a hash of the arglist). This is
     * because The standard link.exe can't handle odd characters (parentheses or commas, for
     * example) in debug information.
     *
     * This does not affect external symbols used by linker.
     *
     * TODO: strip illegal characters from arg lists instead ("link.exe" - safe names)
     *
     * @param range Range contained in the method of interest
     * @return user debugger friendly method name
     */
    private String getDebuggerName(Range range) {
        final String methodName;
        if (noMainFound && range.getMethodName().equals("main")) {
            noMainFound = false;
            methodName = range.getFullMethodName();
        } else {
            /* In the future, use a more user-friendly name instead of a hash function. */
            methodName = range.getSymbolName();
        }
        return methodName;
    }

    private void addLineNumberRecords(CompiledMethodEntry compiledEntry) {
        CVLineRecord record = lineRecordBuilder.build(compiledEntry);
        /*
         * If there are no file entries (perhaps for a synthetic function?), we don't add this
         * record.
         */
        if (!record.isEmpty()) {
            cvDebugInfo.getCVSymbolSection().addRecord(record);
        }
    }

    /**
     * Add a record to the symbol subsection. A symbol subsection is contained within the top level
     * .debug$S symbol section.
     *
     * @param record the symbol subrecord to add.
     */
    private void addToSymbolSubsection(CVSymbolSubrecord record) {
        cvSymbolSubsection.addRecord(record);
    }

    /**
     * Add type records for function. (later add arglist, and return type and local types).
     *
     * @param entry compild method containing entities whoses type records must be added
     * @return type index of function type
     */
    private int addTypeRecords(@SuppressWarnings("unused") CompiledMethodEntry entry) {
        CVTypeRecord.CVTypeArglistRecord argListType = addTypeRecord(new CVTypeRecord.CVTypeArglistRecord().add(T_NOTYPE));
        CVTypeRecord funcType = addTypeRecord(new CVTypeRecord.CVTypeProcedureRecord().returnType(T_VOID).argList(argListType));
        return funcType.getSequenceNumber();
    }

    private <T extends CVTypeRecord> T addTypeRecord(T record) {
        return cvDebugInfo.getCVTypeSection().addOrReference(record);
    }
}
