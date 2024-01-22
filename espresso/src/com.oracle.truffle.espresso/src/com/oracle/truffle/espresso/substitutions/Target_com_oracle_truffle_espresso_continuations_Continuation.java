/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.ContinuationSupport;

/**
 * VM entry point from the Continuation class, responsible for unwinding and rewinding the stack.
 */
@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_continuations_Continuation {
    @Substitution
    @TruffleBoundary
    static void suspend0() {
        // This internal exception will be caught in BytecodeNode's interpreter loop. Frame records
        // will be added to the exception object in a linked list until it's caught below.
        throw new ContinuationSupport.Unwind();
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    static void resume0(
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
                    @Inject Meta meta,
                    @Inject EspressoContext context) {
        ContinuationSupport.HostFrameRecord stack = ContinuationSupport.HostFrameRecord.copyFromGuest(self, meta, context);

        // This will break if the continuations API is redefined - TODO: find a way to block that.
        var runMethod = meta.com_oracle_truffle_espresso_continuations_Continuation_run.getMethodVersion();

        // The entry node will unpack the head frame record into the stack and then pass the
        // remaining records into the bytecode interpreter, which will then pass them down the stack
        // until everything is fully unwound. TODO: Is creating a new node the right way to do it?
        // Probably we should be using an explicit node with cached arguments and stuff?
        try {
            runMethod.getCallTarget().call(self, stack);
        } catch (ContinuationSupport.Unwind unwind) {
            CompilerDirectives.transferToInterpreter();
            meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.setObject(self, unwind.toGuest(meta));
        }
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    static void start0(
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/continuations/Continuation;") StaticObject self,
                    @Inject Meta meta) {
        try {
            // The run method is private in Continuation and is the continuation delimiter. Frames
            // from run onwards will be unwound on suspend, and rewound on resume.
            meta.com_oracle_truffle_espresso_continuations_Continuation_run.invokeDirect(self);
        } catch (ContinuationSupport.Unwind unwind) {
            // Guest called suspend(). By the time we get here the frame info has been gathered up
            // into host-side objects so we just need to copy the data into the guest world.
            meta.com_oracle_truffle_espresso_continuations_Continuation_stackFrameHead.setObject(self, unwind.toGuest(meta));
        }
    }

    @Substitution
    static boolean isSupported() {
        return true;
    }
}
