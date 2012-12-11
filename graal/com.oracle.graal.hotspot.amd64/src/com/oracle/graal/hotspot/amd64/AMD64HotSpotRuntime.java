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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.compiler.amd64.AMD64DeoptimizationStub.*;
import static com.oracle.graal.compiler.amd64.AMD64LIRGenerator.*;
import static com.oracle.graal.hotspot.nodes.MonitorEnterStubCall.*;
import static com.oracle.graal.hotspot.nodes.MonitorExitStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewArrayStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewInstanceStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewMultiArrayStubCall.*;
import static com.oracle.graal.hotspot.nodes.VMErrorNode.*;
import static com.oracle.graal.hotspot.nodes.VerifyOopStubCall.*;
import static com.oracle.graal.hotspot.nodes.IdentityHashCodeStubCall.*;
import static com.oracle.graal.hotspot.nodes.ThreadIsInterruptedStubCall.*;
import static com.oracle.graal.lir.amd64.AMD64Call.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

public class AMD64HotSpotRuntime extends HotSpotRuntime {

    public AMD64HotSpotRuntime(HotSpotVMConfig config, HotSpotGraalRuntime graalRuntime) {
        super(config, graalRuntime);

        Kind word = graalRuntime.getTarget().wordKind;

        addRuntimeCall(DEOPTIMIZE, config.deoptimizeStub,
                /*           temps */ null,
                /*             ret */ ret(Kind.Void));

        addRuntimeCall(SET_DEOPT_INFO, config.setDeoptInfoStub,
                /*           temps */ null,
                /*             ret */ ret(Kind.Void),
                /* arg0:      info */ scratch(Kind.Object));

        addRuntimeCall(DEBUG, config.debugStub,
                /*           temps */ null,
                /*             ret */ ret(Kind.Void));

        addRuntimeCall(ARITHMETIC_FREM, config.arithmeticFremStub,
                /*           temps */ null,
                /*             ret */ ret(Kind.Float),
                /* arg0:         a */ arg(0, Kind.Float),
                /* arg1:         b */ arg(1, Kind.Float));

        addRuntimeCall(ARITHMETIC_DREM, config.arithmeticDremStub,
                /*           temps */ null,
                /*             ret */ ret(Kind.Double),
                /* arg0:         a */ arg(0, Kind.Double),
                /* arg1:         b */ arg(1, Kind.Double));

        addRuntimeCall(MONITORENTER, config.monitorEnterStub,
                /*        temps */ new Register[] {rax, rbx},
                /*          ret */ ret(Kind.Void),
                /* arg0: object */ arg(0, Kind.Object),
                /* arg1:   lock */ arg(1, word));

        addRuntimeCall(MONITOREXIT, config.monitorExitStub,
                /*        temps */ new Register[] {rax, rbx},
                /*          ret */ ret(Kind.Void),
                /* arg0: object */ arg(0, Kind.Object),
                /* arg1:   lock */ arg(1, word));

        addRuntimeCall(NEW_OBJECT_ARRAY, config.newObjectArrayStub,
                /*        temps */ new Register[] {rcx, rdi, rsi},
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rdx.asValue(word),
                /* arg1: length */ rbx.asValue(Kind.Int));

        addRuntimeCall(NEW_TYPE_ARRAY, config.newTypeArrayStub,
                /*        temps */ new Register[] {rcx, rdi, rsi},
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rdx.asValue(word),
                /* arg1: length */ rbx.asValue(Kind.Int));

        addRuntimeCall(NEW_INSTANCE, config.newInstanceStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rdx.asValue(word));

        addRuntimeCall(NEW_MULTI_ARRAY, config.newMultiArrayStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rax.asValue(word),
                /* arg1:   rank */ rbx.asValue(Kind.Int),
                /* arg2:   dims */ rcx.asValue(word));

        addRuntimeCall(VERIFY_OOP, config.verifyOopStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0: object */ r13.asValue(Kind.Object));

        addRuntimeCall(VM_ERROR, config.vmErrorStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0:  where */ arg(0, Kind.Object),
                /* arg1: format */ arg(1, Kind.Object),
                /* arg2:  value */ arg(2, Kind.Long));

        addRuntimeCall(IDENTITY_HASHCODE, config.identityHashCodeStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Int),
                /* arg0:    obj */ rdx.asValue(Kind.Object));

        addRuntimeCall(THREAD_IS_INTERRUPTED, config.threadIsInterruptedStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Boolean),
                /* arg0: thread */ rdx.asValue(Kind.Object),
      /* arg1: clearInterrupted */ rdx.asValue(Kind.Boolean));
    }

    @Override
    public Register threadRegister() {
        return r15;
    }

    @Override
    public Register stackPointerRegister() {
        return rsp;
    }

    @Override
    protected RegisterConfig createRegisterConfig(boolean globalStubConfig) {
        return new AMD64HotSpotRegisterConfig(config, globalStubConfig);
    }

}
