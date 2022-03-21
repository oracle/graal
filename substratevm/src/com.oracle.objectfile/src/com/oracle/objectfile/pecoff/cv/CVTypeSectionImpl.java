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

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.pecoff.PECoffObjectFile;
import org.graalvm.compiler.debug.DebugContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_SIGNATURE_C13;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_SYMBOL_SECTION_NAME;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_TYPE_SECTION_NAME;

public final class CVTypeSectionImpl extends CVSectionImpl {

    private static final int CV_RECORD_INITIAL_CAPACITY = 200;

    /* CodeView 4 type records below 1000 are pre-defined. */
    private int sequenceCounter = 0x1000;

    /* A sequential map of type records, starting at 1000 */
    private final Map<CVTypeRecord, CVTypeRecord> typeMap = new LinkedHashMap<>(CV_RECORD_INITIAL_CAPACITY);

    CVTypeSectionImpl(CVDebugInfo cvDebugInfo) {
        super(cvDebugInfo);
    }

    @Override
    public String getSectionName() {
        return CV_TYPE_SECTION_NAME;
    }

    @Override
    public void createContent(DebugContext debugContext) {
        int pos = 0;
        enableLog(debugContext);
        log("CVTypeSectionImpl.createContent() adding records");
        addRecords();
        log("CVTypeSectionImpl.createContent() start");
        pos = CVUtil.putInt(CV_SIGNATURE_C13, null, pos);
        for (CVTypeRecord record : typeMap.values()) {
            pos = record.computeFullSize(pos);
        }
        byte[] buffer = new byte[pos];
        super.setContent(buffer);
        log("CVTypeSectionImpl.createContent() end");
    }

    @Override
    public void writeContent(DebugContext debugContext) {
        int pos = 0;
        enableLog(debugContext);
        log("CVTypeSectionImpl.writeContent() start");
        byte[] buffer = getContent();
        verboseLog("  [0x%08x] CV_SIGNATURE_C13", pos);
        pos = CVUtil.putInt(CV_SIGNATURE_C13, buffer, pos);
        for (CVTypeRecord record : typeMap.values()) {
            verboseLog("  [0x%08x] 0x%06x %s", pos, record.getSequenceNumber(), record.toString());
            pos = record.computeFullContents(buffer, pos);
        }
        verboseLog("CVTypeSectionImpl.writeContent() end");
    }

    /**
     * Add all relevant type records to the type section.
     */
    private void addRecords() {
        /* if an external PDB file is generated, add CVTypeServer2Record */
        /* for each class, add all members, types, etc */
    }

    /**
     * Return either the caller-created instance or a matching existing instance. Every entry in
     * typeMap is a T, because it is ONLY this function which inserts entries (of type T).
     *
     * @param <T> type of new record
     * @param newRecord record to add if an existing record with same hash hasn't already been added
     * @return the record (if previously unseen) or old record
     */
    @SuppressWarnings("unchecked")
    public <T extends CVTypeRecord> T addOrReference(T newRecord) {
        final T record;
        if (typeMap.containsKey(newRecord)) {
            record = (T) typeMap.get(newRecord);
        } else {
            newRecord.setSequenceNumber(sequenceCounter++);
            typeMap.put(newRecord, newRecord);
            record = newRecord;
        }
        return record;
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        PECoffObjectFile.PECoffSection targetSection = (PECoffObjectFile.PECoffSection) getElement().getOwner().elementForName(CV_SYMBOL_SECTION_NAME);
        LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        /* Make our content depend on the codeview symbol section. */
        deps.add(BuildDependency.createOrGet(ourContent, decisions.get(targetSection).getDecision(LayoutDecision.Kind.CONTENT)));

        return deps;
    }
}
