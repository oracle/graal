/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions
public final class Target_jdk_internal_misc_ScopedMemoryAccess {
    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @Substitution
    @TruffleBoundary
    public static boolean closeScope0(@JavaType(internalName = "Ljdk/internal/foreign/MemorySessionImpl;") StaticObject session, @Inject EspressoContext context) {
        CloseScopedMemoryAction action = new CloseScopedMemoryAction(session);
        Future<Void> future = context.getEnv().submitThreadLocal(null, action);
        TruffleSafepoint.setBlockedThreadInterruptible(null, f -> {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }, future);
        return !action.found;
    }

    private static final class CloseScopedMemoryAction extends ThreadLocalAction {
        static final int MAX_CRITICAL_STACK_DEPTH = 10;
        final StaticObject value;
        boolean found;

        protected CloseScopedMemoryAction(StaticObject value) {
            super(false, false);
            this.value = value;
        }

        @Override
        protected void perform(Access access) {
            assert access.getThread() == Thread.currentThread();
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
                int count;

                @Override
                public Object visitFrame(FrameInstance frameInstance) {
                    if (count >= MAX_CRITICAL_STACK_DEPTH) {
                        return this; // stop iteration
                    }
                    CallTarget callTarget = frameInstance.getCallTarget();
                    if (callTarget instanceof RootCallTarget) {
                        RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
                        if (rootNode instanceof EspressoRootNode espressoNode) {
                            count++;
                            Method method = espressoNode.getMethod();
                            if (method.isScoped()) {
                                Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                                int bci = espressoNode.readBCI(frame);
                                assert method.getLocalVariableTable() != LocalVariableTable.EMPTY_LVT;
                                Local[] locals = method.getLocalVariableTable().getLocalsAt(bci);
                                for (Local local : locals) {
                                    // primitive types have length 1, object types have length > 1
                                    if (local.getType().value().length() > 1) {
                                        if (EspressoFrame.getLocalObject(frame, local.getSlot()) == value) {
                                            found = true;
                                            return this;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return null;
                }
            });
        }
    }
}
