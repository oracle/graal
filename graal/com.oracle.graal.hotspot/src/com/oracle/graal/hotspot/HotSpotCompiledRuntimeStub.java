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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Infopoint;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.stubs.*;

/**
 * {@link HotSpotCompiledCode} destined for installation as a RuntimeStub.
 */
public final class HotSpotCompiledRuntimeStub extends HotSpotCompiledCode {

    private static final long serialVersionUID = -4506206868419153274L;

    public final String stubName;

    public HotSpotCompiledRuntimeStub(TargetDescription target, Stub stub, CompilationResult compResult) {
        super(target, compResult);
        assert checkStubInvariants(compResult);
        this.stubName = stub.toString();
    }

    /**
     * Checks the conditions a compilation must satisfy to be installed as a RuntimeStub.
     */
    private boolean checkStubInvariants(CompilationResult compResult) {
        assert compResult.getExceptionHandlers().isEmpty();
        for (DataPatch data : compResult.getDataReferences()) {
            Constant constant = data.getConstant();
            if (constant != null) {
                assert constant.getKind() != Kind.Object : this + " cannot have embedded object constant: " + constant;
                assert constant.getPrimitiveAnnotation() == null : this + " cannot have embedded metadata: " + constant;
            }
        }
        for (Infopoint infopoint : compResult.getInfopoints()) {
            assert infopoint instanceof Call : this + " cannot have non-call infopoint: " + infopoint;
            Call call = (Call) infopoint;
            assert call.target instanceof HotSpotForeignCallLinkage : this + " cannot have non runtime call: " + call.target;
            HotSpotForeignCallLinkage linkage = (HotSpotForeignCallLinkage) call.target;
            assert !linkage.isCompiledStub() : this + " cannot call compiled stub " + linkage;
        }
        return true;
    }
}
