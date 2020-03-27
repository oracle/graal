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

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.pecoff.PECoffMachine;
import com.oracle.objectfile.pecoff.PECoffObjectFile;

import java.util.Map;
import java.util.Set;

/**
 * CVSections is a container class for all the CodeView sections to be emitted in the object file.
 * Currently, that will be .debug$S (CVSymbolSectionImpl) (and .debug$T (CVTypeSectionImpl) when implemented)
 * Common data (useful to more than one CodeView section) goes here, mostly that gathered by calls to installDebugInfo->addRange() and installDebugInfo->addSubRange()
 */
public final class CVSections extends DebugInfoBase {

    @SuppressWarnings("unused") private PECoffMachine machine;
    private CVSymbolSectionImpl cvSymbolSection;
    private CVTypeSectionImpl cvTypeSection;

    public CVSections(PECoffMachine targetMachine) {
        machine = targetMachine;
        cvSymbolSection = new CVSymbolSectionImpl(this);
        cvTypeSection   = new CVTypeSectionImpl();
    }

    public CVSymbolSectionImpl getCVSymbolSection() {
        return cvSymbolSection;
    }

    public CVTypeSectionImpl getCVTypeSection() {
        return cvTypeSection;
    }

    abstract static class CVSectionImplBase extends BasicProgbitsSectionImpl {

        int debugLevel = 1;
        long debugTextBase = 0;
        long debugAddress = 0;
        int debugBase = 0;

        CVSectionImplBase() {
            checkDebug(0);
        }

        @Override
        public void setElement(ObjectFile.Element e) {
            super.setElement(e);
            /* define the section as a COFF symbol */
            getOwner().createDefinedSymbol(getSectionName(), getElement(), 0, 0, false, false);
        }

        void checkDebug(int pos) {
            /* if the env var relevant to this element type is set then switch on debugging */
            String envVarName =  "DEBUG_" + getSectionName().substring(1).toUpperCase();
            if (System.getenv(envVarName) != null) {
                debugLevel = 1;
                debugBase = pos;
                debugAddress = debugTextBase;
            }
        }

        @Override
        public int getAlignment() {
            return 1;
        }

        public void debug(String format, Object ... args) {
            if (debugLevel > 1) {
                CVUtil.debug(format + "\n", args);
            }
        }

        public void info(String format, Object ... args) {
            if (debugLevel > 0) {
                CVUtil.debug(format + "\n", args);
            }
        }

        @Override
        public byte[] getOrDecideContent(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            /* ensure content byte[] has been created before calling super method */
            createContent();

            /* ensure content byte[] has been written before calling super method */
            writeContent();

            return super.getOrDecideContent(alreadyDecided, contentHint);
        }

        @Override
        public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
            Set<BuildDependency> deps = super.getDependencies(decisions);
            String targetName = getSectionName();
            @SuppressWarnings("unused") PECoffObjectFile.PECoffSection targetSection = (PECoffObjectFile.PECoffSection) getElement().getOwner().elementForName(targetName);
            LayoutDecision ourContent =  decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
            LayoutDecision ourSize =  decisions.get(getElement()).getDecision(LayoutDecision.Kind.SIZE);
            //LayoutDecision.Kind[] targetKinds = targetSectionKinds();
            /* make our content depend on the size and content of the target */
            //for (LayoutDecision.Kind targetKind : targetKinds) {
            //    LayoutDecision targetDecision =  decisions.get(targetSection).getDecision(targetKind);
            //    deps.add(BuildDependency.createOrGet(ourContent, targetDecision));
            //}
            /* make our size depend on our content */
            deps.add(BuildDependency.createOrGet(ourSize, ourContent));
            return deps;
        }

        //public abstract LayoutDecision.Kind[] targetSectionKinds();
        public abstract void createContent();
        public abstract void writeContent();
        public abstract String getSectionName();
    }
}
