/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.graal.compiler.truffle.test;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

public class BytecodeDSLUnwindPartialEvaluationTest extends PartialEvaluationTest {

    @Test
    public void testUnwindToBoundary() {
        UnwindInterpreter root = parse(b -> {
            b.beginRoot();
            b.beginTryCatch();
            b.beginTryFinally(b::emitNop);
            b.emitThrowTransfer();
            b.endTryFinally();
            b.beginReturn();
            b.emitLoadConstant(-1L);
            b.endReturn();
            b.endTryCatch();
            b.endRoot();
        });
        assertPartialEvalEquals(RootNode.createConstantNode(42L), new TransferBoundary(root));
    }

    @Test
    public void testNestedFinally() {
        UnwindInterpreter root = parse(b -> {
            b.beginRoot();
            b.beginTryFinally(b::emitNop);
            b.beginTryCatch();
            b.beginTryFinally(b::emitNop);
            b.emitThrowTransfer();
            b.endTryFinally();
            b.beginReturn();
            b.emitLoadConstant(-1L);
            b.endReturn();
            b.endTryCatch();
            b.endTryFinally();
            b.endRoot();
        });
        assertPartialEvalEquals(RootNode.createConstantNode(42L), new TransferBoundary(root));
    }

    @Test
    public void testFinallyReturn() {
        UnwindInterpreter root = parse(b -> {
            b.beginRoot();
            BytecodeLocal value = b.createLocal();
            b.beginStoreLocal(value);
            b.emitLoadConstant(42L);
            b.endStoreLocal();
            b.beginTryFinally(() -> {
                b.beginReturn();
                b.emitLoadLocal(value);
                b.endReturn();
            });
            b.emitThrowTransfer();
            b.endTryFinally();
            b.endRoot();
        });
        assertPartialEvalEquals(RootNode.createConstantNode(42L), root);
    }

    @Test
    public void testGuestUnwindFinallyReturn() {
        UnwindInterpreter root = parse(b -> {
            b.beginRoot();
            b.beginTryCatch();
            b.beginTryFinally(() -> {
                b.beginReturn();
                b.emitLoadConstant(42L);
                b.endReturn();
            });
            b.emitThrowGuestTransfer();
            b.endTryFinally();
            b.beginReturn();
            b.emitLoadConstant(-1L);
            b.endReturn();
            b.endTryCatch();
            b.endRoot();
        });
        assertPartialEvalEquals(RootNode.createConstantNode(42L), root);
    }

    private static UnwindInterpreter parse(BytecodeParser<UnwindInterpreterGen.Builder> parser) {
        UnwindInterpreter root = UnwindInterpreterGen.create(null, BytecodeConfig.DEFAULT, parser).getNode(0);
        root.getBytecodeNode().setUncachedThreshold(0);
        return root;
    }

    static final class TransferBoundary extends RootNode {
        @Child private DirectCallNode call;

        TransferBoundary(UnwindInterpreter root) {
            super(null);
            call = DirectCallNode.create(root.getCallTarget());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return call.call();
            } catch (Transfer transfer) {
                return 42L;
            }
        }
    }

    public static final class Transfer extends ControlFlowException {
        private static final long serialVersionUID = 1L;
    }

    public static final class GuestTransfer extends AbstractTruffleException {
        private static final long serialVersionUID = 1L;
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, unwindExceptions = {Transfer.class, GuestTransfer.class})
    public abstract static class UnwindInterpreter extends RootNode implements BytecodeRootNode {
        protected UnwindInterpreter(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        public Object interceptControlFlowException(ControlFlowException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
            throw new AssertionError("Unwind must bypass interception.");
        }

        @Override
        public AbstractTruffleException interceptTruffleException(AbstractTruffleException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
            throw new AssertionError("Unwind must bypass interception.");
        }

        @Override
        public Throwable interceptInternalException(Throwable ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
            throw new AssertionError("Unwind must bypass interception.");
        }

        @Operation
        public static final class ThrowTransfer {
            @Specialization
            public static void perform() {
                throw new Transfer();
            }
        }

        @Operation
        public static final class ThrowGuestTransfer {
            @Specialization
            public static void perform() {
                throw new GuestTransfer();
            }
        }

        @Operation
        public static final class Nop {
            @Specialization
            public static void perform() {
            }
        }

        @EpilogExceptional
        public static final class ExceptionalEpilog {
            @Specialization
            public static void perform(@SuppressWarnings("unused") AbstractTruffleException ex) {
                throw new AssertionError("Unwind must bypass exceptional epilog.");
            }
        }
    }
}
