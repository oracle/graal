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
package com.oracle.max.graal.hotspot;

import java.util.*;

import com.oracle.max.graal.hotspot.logging.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.ExceptionHandler;
import com.sun.cri.ci.CiTargetMethod.Mark;
import com.sun.cri.ci.CiTargetMethod.Site;

/**
 * CiTargetMethod augmented with HotSpot-specific information.
 */
public final class HotSpotTargetMethod extends CompilerObject {

    public final CiTargetMethod targetMethod;
    public final HotSpotMethodResolved method; // used only for methods
    public final String name; // used only for stubs

    public final Site[] sites;
    public final ExceptionHandler[] exceptionHandlers;

    private HotSpotTargetMethod(Compiler compiler, HotSpotMethodResolved method, CiTargetMethod targetMethod) {
        super(compiler);
        this.method = method;
        this.targetMethod = targetMethod;
        this.name = null;

        sites = getSortedSites(targetMethod);
        if (targetMethod.exceptionHandlers == null) {
            exceptionHandlers = null;
        } else {
            exceptionHandlers = targetMethod.exceptionHandlers.toArray(new ExceptionHandler[targetMethod.exceptionHandlers.size()]);
        }
    }

    private HotSpotTargetMethod(Compiler compiler, CiTargetMethod targetMethod, String name) {
        super(compiler);
        this.method = null;
        this.targetMethod = targetMethod;
        this.name = name;

        sites = getSortedSites(targetMethod);
        assert targetMethod.exceptionHandlers == null || targetMethod.exceptionHandlers.size() == 0;
        exceptionHandlers = null;
    }

    private Site[] getSortedSites(CiTargetMethod target) {
        List<?>[] lists = new List<?>[] {target.safepoints, target.dataReferences, target.marks};
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
        Arrays.sort(result, new Comparator<Site>() {

            public int compare(Site s1, Site s2) {
                if (s1.pcOffset == s2.pcOffset && (s1 instanceof Mark ^ s2 instanceof Mark)) {
                    return s1 instanceof Mark ? -1 : 1;
                }
                return s1.pcOffset - s2.pcOffset;
            }
        });
        if (Logger.ENABLED) {
            for (Site site : result) {
                Logger.log(site.pcOffset + ": " + site);
            }
        }
        return result;
    }

    public static HotSpotCompiledMethod installMethod(Compiler compiler, HotSpotMethodResolved method, CiTargetMethod targetMethod, boolean installCode) {
        return compiler.getVMEntries().installMethod(new HotSpotTargetMethod(compiler, method, targetMethod), installCode);
    }

    public static Object installStub(Compiler compiler, CiTargetMethod targetMethod, String name) {
        return compiler.getVMEntries().installStub(new HotSpotTargetMethod(compiler, targetMethod, name));
    }

}
