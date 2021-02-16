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

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import org.graalvm.compiler.debug.DebugContext;

import java.util.Map;
import java.util.Set;

abstract class CVSectionImpl extends BasicProgbitsSectionImpl {

    boolean debug = false;

    CVSectionImpl() {
    }

    private String debugSectionLogName() {
        /*
         * Log messages for the symbol section will be enabled using "PeCoffdebug$S". Log messages
         * for the type section will be enabled using "PeCoffdebug$T".
         */
        assert getSectionName().startsWith(CVConstants.CV_SECTION_NAME_PREFIX);
        return "PeCoff" + getSectionName().replace(".", "");
    }

    protected void enableLog(DebugContext context) {
        /*
         * Unlike in the Dwarf debug code, debug output may be enabled in both the sizing and
         * writing phases. (Currently turned off in the sizing state)
         */
        if (context.areScopesEnabled()) {
            debug = true;
        }
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

        /* Ensure content byte[] has been created before calling super method. */
        getOwner().debugContext(debugSectionLogName(), this::createContent);

        /* Ensure content byte[] has been written before calling super method. */
        getOwner().debugContext(debugSectionLogName(), this::writeContent);

        return super.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        LayoutDecision ourSize = decisions.get(getElement()).getDecision(LayoutDecision.Kind.SIZE);
        /* Make our size depend on our content. */
        deps.add(BuildDependency.createOrGet(ourSize, ourContent));
        return deps;
    }

    public abstract void createContent(DebugContext debugContext);

    public abstract void writeContent(DebugContext debugContext);

    public abstract String getSectionName();
}
