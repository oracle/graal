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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.ExceptionHandler;
import com.oracle.graal.api.code.CompilationResult.Infopoint;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.code.CompilationResult.Site;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;

/**
 * Augments a {@link CompilationResult} with HotSpot-specific information.
 */
public final class HotSpotCompilationResult extends CompilerObject {

    private static final long serialVersionUID = 7807321392203253218L;
    public final CompilationResult comp;

    /**
     * Non-null for installation of an nmethod.
     */
    public final HotSpotResolvedJavaMethod method;
    public final int entryBCI;

    /**
     * Non-null for installation of a RuntimeStub.
     */
    public final String stubName;

    public final Site[] sites;
    public final ExceptionHandler[] exceptionHandlers;

    public HotSpotCompilationResult(HotSpotResolvedJavaMethod method, int entryBCI, CompilationResult comp) {
        this.method = method;
        this.stubName = null;
        this.comp = comp;
        this.entryBCI = entryBCI;

        sites = getSortedSites(comp);
        if (comp.getExceptionHandlers().isEmpty()) {
            exceptionHandlers = null;
        } else {
            exceptionHandlers = comp.getExceptionHandlers().toArray(new ExceptionHandler[comp.getExceptionHandlers().size()]);
        }
    }

    public HotSpotCompilationResult(Stub stub, CompilationResult comp) {
        assert checkStubInvariants(comp);
        this.method = null;
        this.stubName = stub.toString();
        this.comp = comp;
        this.entryBCI = 0;

        sites = getSortedSites(comp);
        assert comp.getExceptionHandlers().isEmpty();
        exceptionHandlers = null;
    }

    /**
     * Checks the conditions a compilation must satisfy to be installed as a RuntimeStub.
     */
    private boolean checkStubInvariants(CompilationResult compResult) {
        for (DataPatch data : compResult.getDataReferences()) {
            Constant constant = data.constant;
            assert constant.getKind() != Kind.Object : this + " cannot have embedded object constant: " + constant;
            assert constant.getPrimitiveAnnotation() == null : this + " cannot have embedded metadata: " + constant;
        }
        for (Infopoint infopoint : compResult.getInfopoints()) {
            assert infopoint instanceof Call : this + " cannot have non-call infopoint: " + infopoint;
            Call call = (Call) infopoint;
            assert call.target instanceof HotSpotRuntimeCallTarget : this + " cannot have non runtime call: " + call.target;
            HotSpotRuntimeCallTarget callTarget = (HotSpotRuntimeCallTarget) call.target;
            assert callTarget.getAddress() == graalRuntime().getConfig().uncommonTrapStub || callTarget.isCRuntimeCall() : this + "must only call C runtime or deoptimization stub, not " + call.target;
        }
        return true;
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
        List<?>[] lists = new List<?>[]{target.getInfopoints(), target.getDataReferences(), target.getMarks()};
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
