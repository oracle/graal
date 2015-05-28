/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.HotSpotHostBackend.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.ConstantReference;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Infopoint;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;

/**
 * {@link HotSpotCompiledCode} destined for installation as a RuntimeStub.
 */
public final class HotSpotCompiledRuntimeStub extends HotSpotCompiledCode {

    public final String stubName;

    public HotSpotCompiledRuntimeStub(Stub stub, CompilationResult compResult) {
        super(compResult);
        this.stubName = stub.toString();
        assert checkStubInvariants(compResult);
    }

    /**
     * Checks the conditions a compilation must satisfy to be installed as a RuntimeStub.
     */
    private boolean checkStubInvariants(CompilationResult compResult) {
        assert compResult.getExceptionHandlers().isEmpty() : this;

        // Stubs cannot be recompiled so they cannot be compiled with
        // assumptions and there is no point in recording evol_method dependencies
        assert compResult.getAssumptions() == null : "stubs should not use assumptions: " + this;
        assert compResult.getMethods() == null : "stubs should not record evol_method dependencies: " + this;

        for (DataPatch data : compResult.getDataPatches()) {
            if (data.reference instanceof ConstantReference) {
                ConstantReference ref = (ConstantReference) data.reference;
                if (ref.getConstant() instanceof HotSpotMetaspaceConstant) {
                    HotSpotMetaspaceConstant c = (HotSpotMetaspaceConstant) ref.getConstant();
                    if (c.asResolvedJavaType() != null && c.asResolvedJavaType().getName().equals("[I")) {
                        // special handling for NewArrayStub
                        // embedding the type '[I' is safe, since it is never unloaded
                        continue;
                    }
                }
            }

            assert !(data.reference instanceof ConstantReference) : this + " cannot have embedded object or metadata constant: " + data.reference;
        }
        for (Infopoint infopoint : compResult.getInfopoints()) {
            assert infopoint instanceof Call : this + " cannot have non-call infopoint: " + infopoint;
            Call call = (Call) infopoint;
            assert call.target instanceof HotSpotForeignCallLinkage : this + " cannot have non runtime call: " + call.target;
            HotSpotForeignCallLinkage linkage = (HotSpotForeignCallLinkage) call.target;
            assert !linkage.isCompiledStub() || linkage.getDescriptor().equals(UNCOMMON_TRAP_HANDLER) : this + " cannot call compiled stub " + linkage;
        }
        return true;
    }

    @Override
    public String toString() {
        return stubName != null ? stubName : super.toString();
    }
}
