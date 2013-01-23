/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * Augments a {@link CompilationResult} with HotSpot-specific information.
 */
public final class HotSpotCompilationResult extends CompilerObject {

    private static final long serialVersionUID = 7807321392203253218L;
    public final CompilationResult comp;
    public final HotSpotResolvedJavaMethod method; // used only for methods
    public final int entryBCI; // used only for methods
    public final String name; // used only for stubs

    public final Site[] sites;
    public final ExceptionHandler[] exceptionHandlers;

    public HotSpotCompilationResult(HotSpotResolvedJavaMethod method, int entryBCI, CompilationResult comp) {
        this.method = method;
        this.comp = comp;
        this.entryBCI = entryBCI;
        this.name = null;

        sites = getSortedSites(comp);
        if (comp.getExceptionHandlers() == null) {
            exceptionHandlers = null;
        } else {
            exceptionHandlers = comp.getExceptionHandlers().toArray(new ExceptionHandler[comp.getExceptionHandlers().size()]);
        }
    }

    static class SiteComparator implements Comparator<Site> {

        public int compare(Site s1, Site s2) {
            if (s1.pcOffset == s2.pcOffset && (s1 instanceof Mark ^ s2 instanceof Mark)) {
                return s1 instanceof Mark ? -1 : 1;
            }
            return s1.pcOffset - s2.pcOffset;
        }
    }

    private static Site[] getSortedSites(CompilationResult target) {
        List<?>[] lists = new List<?>[]{target.getSafepoints(), target.getDataReferences(), target.getMarks()};
        int count = 0;
        for (List<?> list : lists) {
            count += list.size();
        }
        Site[] result = new Site[count];
        int pos = 0;
        for (List<?> list : lists) {
            for (Object elem : list) {
                result[pos++] = (Site) elem;
            }
        }
        Arrays.sort(result, new SiteComparator());
        return result;
    }
}
