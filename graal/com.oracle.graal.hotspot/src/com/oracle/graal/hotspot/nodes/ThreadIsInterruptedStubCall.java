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
package com.oracle.graal.hotspot.nodes;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;

/**
 * Node implementing a call to {@code GraalRuntime::thread_is_interrupted}.
 */
public class ThreadIsInterruptedStubCall extends DeoptimizingStubCall implements LIRGenLowerable {

    @Input private ValueNode thread;
    @Input private ValueNode clearIsInterrupted;
    public static final ForeignCallDescriptor THREAD_IS_INTERRUPTED = new ForeignCallDescriptor("thread_is_interrupted", boolean.class, Object.class, boolean.class);

    public ThreadIsInterruptedStubCall(ValueNode thread, ValueNode clearIsInterrupted) {
        super(StampFactory.forInteger(Kind.Int, 0, 1));
        this.thread = thread;
        this.clearIsInterrupted = clearIsInterrupted;
    }

    @Override
    public void generate(LIRGenerator gen) {
        ForeignCallLinkage linkage = gen.getRuntime().lookupForeignCall(ThreadIsInterruptedStubCall.THREAD_IS_INTERRUPTED);
        Variable result = gen.emitForeignCall(linkage, this, gen.operand(thread), gen.operand(clearIsInterrupted));
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static boolean call(Thread thread, boolean clearIsInterrupted) {
        try {
            Method isInterrupted = Thread.class.getDeclaredMethod("isInterrupted", boolean.class);
            isInterrupted.setAccessible(true);
            return (Boolean) isInterrupted.invoke(thread, clearIsInterrupted);
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }
}
