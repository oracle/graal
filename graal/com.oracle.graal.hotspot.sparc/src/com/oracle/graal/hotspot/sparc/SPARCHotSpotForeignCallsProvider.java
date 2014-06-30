/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.api.meta.Value.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.*;
import static com.oracle.graal.hotspot.HotSpotHostBackend.*;
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

public class SPARCHotSpotForeignCallsProvider extends HotSpotHostForeignCallsProvider {

    private final Value[] nativeABICallerSaveRegisters;

    public SPARCHotSpotForeignCallsProvider(HotSpotGraalRuntime runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache, Value[] nativeABICallerSaveRegisters) {
        super(runtime, metaAccess, codeCache);
        this.nativeABICallerSaveRegisters = nativeABICallerSaveRegisters;
    }

    @Override
    public void initialize(HotSpotProviders providers, HotSpotVMConfig config) {
        TargetDescription target = providers.getCodeCache().getTarget();
        Kind word = target.wordKind;

        // The calling convention for the exception handler stub is (only?) defined in
        // TemplateInterpreterGenerator::generate_throw_exception()
        // in templateInterpreter_sparc.cpp around line 1925
        RegisterValue outgoingException = o0.asValue(Kind.Object);
        RegisterValue outgoingExceptionPc = o1.asValue(word);
        RegisterValue incomingException = i0.asValue(Kind.Object);
        RegisterValue incomingExceptionPc = i1.asValue(word);
        CallingConvention outgoingExceptionCc = new CallingConvention(0, ILLEGAL, outgoingException, outgoingExceptionPc);
        CallingConvention incomingExceptionCc = new CallingConvention(0, ILLEGAL, incomingException, incomingExceptionPc);
        register(new HotSpotForeignCallLinkage(EXCEPTION_HANDLER, 0L, PRESERVES_REGISTERS, LEAF_NOFP, outgoingExceptionCc, incomingExceptionCc, NOT_REEXECUTABLE, ANY_LOCATION));
        register(new HotSpotForeignCallLinkage(EXCEPTION_HANDLER_IN_CALLER, JUMP_ADDRESS, PRESERVES_REGISTERS, LEAF_NOFP, outgoingExceptionCc, incomingExceptionCc, NOT_REEXECUTABLE, ANY_LOCATION));

        link(new SPARCDeoptimizationStub(providers, target, registerStubCall(DEOPTIMIZATION_HANDLER, REEXECUTABLE, LEAF, NO_LOCATIONS)));
        link(new SPARCUncommonTrapStub(providers, target, registerStubCall(UNCOMMON_TRAP_HANDLER, REEXECUTABLE, LEAF, NO_LOCATIONS)));

        super.initialize(providers, config);
    }

    @Override
    public Value[] getNativeABICallerSaveRegisters() {
        return nativeABICallerSaveRegisters;
    }
}
