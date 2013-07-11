/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.api.meta.Value.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

public class SPARCHotSpotRuntime extends HotSpotRuntime {

    public SPARCHotSpotRuntime(HotSpotVMConfig config, HotSpotGraalRuntime graalRuntime) {
        super(config, graalRuntime);
    }

    @Override
    public void registerReplacements(Replacements replacements) {
        Kind word = graalRuntime.getTarget().wordKind;

        // The calling convention for the exception handler stub is (only?) defined in
        // TemplateInterpreterGenerator::generate_throw_exception()
        // in templateInterpreter_sparc.cpp around line 1925
        RegisterValue exception = o0.asValue(Kind.Object);  // XXX should this be i0?
        RegisterValue exceptionPc = o1.asValue(word);  // XXX should this be i1?
        CallingConvention exceptionCc = new CallingConvention(0, ILLEGAL, exception, exceptionPc);
        register(new HotSpotForeignCallLinkage(EXCEPTION_HANDLER, 0L, PRESERVES_REGISTERS, LEAF, exceptionCc, NOT_REEXECUTABLE, ANY_LOCATION));
        register(new HotSpotForeignCallLinkage(EXCEPTION_HANDLER_IN_CALLER, JUMP_ADDRESS, PRESERVES_REGISTERS, LEAF, exceptionCc, NOT_REEXECUTABLE, ANY_LOCATION));

        super.registerReplacements(replacements);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof ConvertNode) {
            // ConvertNodes are handled in SPARCLIRGenerator.emitConvert
        } else {
            super.lower(n, tool);
        }
    }

    @Override
    public Register threadRegister() {
        return g2;
    }

    @Override
    public Register stackPointerRegister() {
        throw new InternalError("NYI: SPARC: Define stack pointer register.");
    }

    @Override
    protected RegisterConfig createRegisterConfig() {
        return new SPARCHotSpotRegisterConfig(graalRuntime.getTarget().arch, config);
    }
}
