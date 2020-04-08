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

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.pecoff.PECoffObjectFile;
import org.graalvm.compiler.debug.DebugContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_SIGNATURE_C13;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_SYMBOL_SECTION_NAME;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_TYPE_SECTION_NAME;

public final class CVTypeSectionImpl extends CVSectionImpl {

    private static final int CV_RECORD_INITIAL_CAPACITY = 200;
    private ArrayList<CVTypeRecord> cvRecords = new ArrayList<>(CV_RECORD_INITIAL_CAPACITY);
    private CVTypeRecordBuilder builder = new CVTypeRecordBuilder(this);

    CVTypeSectionImpl() {
    }

    @Override
    public String getSectionName() {
        return CV_TYPE_SECTION_NAME;
    }

    @Override
    public void createContent(DebugContext debugContext) {
        int pos = 0;
        log(debugContext, "CVTypeSectionImpl.createContent() start");
        addRecords();
        pos += computeHeaderSize();
        for (CVTypeRecord record : cvRecords) {
            pos = record.computeFullSize(pos);
        }
        byte[] buffer = new byte[pos];
        super.setContent(buffer);
        log(debugContext, "CVTypeSectionImpl.createContent() end");
    }

    @Override
    public void writeContent(DebugContext debugContext) {
        int pos = 0;
        log(debugContext, "CVTypeSectionImpl.writeContent() start");
        byte[] buffer = getContent();
        pos = CVUtil.putInt(CV_SIGNATURE_C13, buffer, pos);
        for (CVTypeRecord record : cvRecords) {
            pos = record.computeFullContents(buffer, pos);
        }
        log(debugContext, "CVTypeSectionImpl.writeContent() end");
    }

    public List<CVTypeRecord> getRecords() {
        return Collections.unmodifiableList(cvRecords);
    }

    void addUniqueRecord(CVTypeRecord r) {
        cvRecords.add(r);
    }

    <T extends CVTypeRecord> T addRecord(T newRecord) {
        //CVUtil.debug("adding type record: %s hash=%d\n", newRecord, newRecord.hashCode());
        T actual = builder.buildFrom(newRecord);
        return actual;
    }

    private void addClassRecords() {
        /* we may have done this already when emiting globals in debug$S section */
        //for (DebugInfoBase.ClassEntry classEntry : cvDebugInfo.getPrimaryClasses()) {
            // TODO - emit all members, all types, etc
        //}
    }

    private void addRecords() {
        //final CVTypeRecord r0 = addRecord(new CVTypeRecord.CVTypeServer2Record("0123456789abcdef".getBytes(UTF_8), 1, "c:\\tmp\\graal-8\\vc100.pdb"));
        addClassRecords();
    }

    private static int computeHeaderSize() {
        return Integer.BYTES; /* CV_SIGNATURE_C13 = 4; */
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        PECoffObjectFile.PECoffSection targetSection = (PECoffObjectFile.PECoffSection) getElement().getOwner().elementForName(CV_SYMBOL_SECTION_NAME);
        LayoutDecision ourContent =  decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        LayoutDecision ourSize =  decisions.get(getElement()).getDecision(LayoutDecision.Kind.SIZE);
        /* make our content depend on the codeview symbol section */
        deps.add(BuildDependency.createOrGet(ourContent, decisions.get(targetSection).getDecision(LayoutDecision.Kind.CONTENT)));
        /* make our size depend on our content */
        deps.add(BuildDependency.createOrGet(ourSize, ourContent));

        return deps;
    }
}
