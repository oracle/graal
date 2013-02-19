/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.ptx;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.ptx.*;
import com.oracle.graal.lir.asm.*;

public class PTXDeoptimizationStub extends PTXCode {

    public static final Descriptor DEOPTIMIZE = new Descriptor("deoptimize", true, void.class);
    public static final Descriptor SET_DEOPT_INFO = new Descriptor("setDeoptInfo", true, void.class, Object.class);

    public final Label label = new Label();
    public final LIRFrameState info;
    public final DeoptimizationAction action;
    public final DeoptimizationReason reason;
    public final Object deoptInfo;

    public PTXDeoptimizationStub(DeoptimizationAction action, DeoptimizationReason reason, LIRFrameState info, Object deoptInfo) {
        this.action = action;
        this.reason = reason;
        this.info = info;
        this.deoptInfo = deoptInfo;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, PTXAssembler masm) {
        masm.exit();
    }

    @Override
    public String description() {
        return "deopt stub[reason=" + reason + ", action=" + action + "]";
    }
}
