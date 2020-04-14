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
import com.oracle.objectfile.pecoff.PECoffObjectFile;
import org.graalvm.compiler.debug.DebugContext;

import java.util.Map;
import java.util.Set;

abstract class CVSectionImpl extends BasicProgbitsSectionImpl {

    boolean debug = false;
    long debugTextBase = 0;
    long debugAddress = 0;
    int debugBase = 0;

    CVSectionImpl() {
    }

    @Override
    public void setElement(ObjectFile.Element e) {
        super.setElement(e);
        /* define the section as a COFF symbol */
        getOwner().createDefinedSymbol(getSectionName(), getElement(), 0, 0, false, false);
    }

    private String debugSectionLogName() {
        /*
         * Use prefix cv4 plus the section name (which already includes a dot separator) for the
         * context key. For example messages for type section will be keyed using "cv4.debug$T".
         * Other info formats use their own format-specific prefix.
         */
        assert getSectionName().startsWith(".debug$");
        return "cv4" + getSectionName();
    }

    protected void enableLog(DebugContext context, int pos) {
        /*
         * Unlike in the Dwarf debug code, debug output is enabled in both the sizing and writing
         * phases. At this time, debugBase and debugAddress aren't used but are there for the
         * future.
         */
        if (context.areScopesEnabled()) {
            debug = true;
            debugBase = pos;
            debugAddress = debugTextBase;
        }
    }

    @Override
    public int getAlignment() {
        return 1;
    }

    protected void log(DebugContext context, String format, Object... args) {
        if (debug) {
            context.logv(DebugContext.INFO_LEVEL, format, args);
        }
    }

    protected void verboseLog(DebugContext context, String format, Object... args) {
        if (debug) {
            context.logv(DebugContext.VERBOSE_LEVEL, format, args);
        }
    }

    @Override
    public byte[] getOrDecideContent(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {

        /* ensure content byte[] has been created before calling super method */
        getOwner().debugContext(debugSectionLogName(), this::createContent);

        /* ensure content byte[] has been written before calling super method */
        getOwner().debugContext(debugSectionLogName(), this::writeContent);

        return super.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        String targetName = getSectionName();
        @SuppressWarnings("unused")
        PECoffObjectFile.PECoffSection targetSection = (PECoffObjectFile.PECoffSection) getElement().getOwner().elementForName(targetName);
        LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        LayoutDecision ourSize = decisions.get(getElement()).getDecision(LayoutDecision.Kind.SIZE);
        // LayoutDecision.Kind[] targetKinds = targetSectionKinds();
        /* make our content depend on the size and content of the target */
        // for (LayoutDecision.Kind targetKind : targetKinds) {
        // LayoutDecision targetDecision = decisions.get(targetSection).getDecision(targetKind);
        // deps.add(BuildDependency.createOrGet(ourContent, targetDecision));
        // }
        /* make our size depend on our content */
        deps.add(BuildDependency.createOrGet(ourSize, ourContent));
        return deps;
    }

    // public abstract LayoutDecision.Kind[] targetSectionKinds();
    public abstract void createContent(DebugContext debugContext);

    public abstract void writeContent(DebugContext debugContext);

    public abstract String getSectionName();
}
