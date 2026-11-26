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

import static org.junit.Assert.assertEquals;

import java.util.function.BiConsumer;

import org.graalvm.polyglot.Context;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeVersion;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class GR81615Test extends TestWithSynchronousCompiling {

    private static final Object DEFAULT_LOCAL_VALUE = new Object();

    @Before
    public void initializeLanguage() {
        Context context = setupContext(newContextBuilder());
        context.initialize(BytecodeDSLTestLanguage.ID);
    }

    @Test
    public void testLoadLocalDefaultThenIntDoesNotDeoptLoop() {
        assertCallOrderAndStableCompilation(GR81615RootNodeGen.Builder::emitLoadLocal, true);
    }

    @Test
    public void testLoadLocalIntThenDefaultDoesNotDeoptLoop() {
        assertCallOrderAndStableCompilation(GR81615RootNodeGen.Builder::emitLoadLocal, false);
    }

    @Test
    public void testGetLocalValueDefaultThenIntDoesNotDeoptLoop() {
        assertCallOrderAndStableCompilation((b, local) -> b.emitGetLocal(local.getLocalOffset()), true);
    }

    @Test
    public void testGetLocalValueIntThenDefaultDoesNotDeoptLoop() {
        assertCallOrderAndStableCompilation((b, local) -> b.emitGetLocal(local.getLocalOffset()), false);
    }

    @Test
    public void testLocalAccessorDefaultThenIntDoesNotDeoptLoop() {
        assertCallOrderAndStableCompilation(GR81615RootNodeGen.Builder::emitGetLocalAccessor, true);
    }

    @Test
    public void testLocalAccessorIntThenDefaultDoesNotDeoptLoop() {
        assertCallOrderAndStableCompilation(GR81615RootNodeGen.Builder::emitGetLocalAccessor, false);
    }

    private void assertCallOrderAndStableCompilation(BiConsumer<GR81615RootNodeGen.Builder, BytecodeLocal> emitRead, boolean defaultThenInt) {
        BytecodeRootNodes<GR81615RootNode> roots = GR81615RootNodeGen.create(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            BytecodeLocal local = b.createLocal();

            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginStoreLocal(local);
            b.emitLoadConstant(42);
            b.endStoreLocal();
            b.endIfThen();

            b.beginReturn();
            emitRead.accept(b, local);
            b.endReturn();
            b.endRoot();
        });
        GR81615RootNode root = roots.getNode(0);
        if (defaultThenInt) {
            assertEquals(DEFAULT_LOCAL_VALUE, root.getCallTarget().call(false));
            assertEquals(42, root.getCallTarget().call(true));
        } else {
            assertEquals(42, root.getCallTarget().call(true));
            assertEquals(DEFAULT_LOCAL_VALUE, root.getCallTarget().call(false));
        }
        // Call the root node again to ensure instructions stabilize in both branches.
        assertEquals(DEFAULT_LOCAL_VALUE, root.getCallTarget().call(false));
        assertEquals(42, root.getCallTarget().call(true));

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        target.compile(true);
        assertCompiled(target);

        assertEquals(DEFAULT_LOCAL_VALUE, target.call(false));
        assertCompiled(target);

        assertEquals(42, target.call(true));
        assertCompiled(target);
    }

    @GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, //
                    defaultLocalValue = "DEFAULT_LOCAL_VALUE", //
                    boxingEliminationTypes = int.class)
    public abstract static class GR81615RootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        static final Object DEFAULT_LOCAL_VALUE = GR81615Test.DEFAULT_LOCAL_VALUE;

        protected GR81615RootNode(BytecodeDSLTestLanguage language, FrameDescriptor.Builder frameDescriptor) {
            super(language, frameDescriptor.build());
        }

        @Operation
        @ConstantOperand(type = int.class)
        static final class GetLocal {
            @Specialization
            static Object get(VirtualFrame frame, int localOffset,
                            @Bind BytecodeNode bytecode,
                            @Bind("$bytecodeIndex") int bci) {
                return bytecode.getLocalValue(bci, frame, localOffset);
            }
        }

        @Operation
        @ConstantOperand(type = LocalAccessor.class)
        static final class GetLocalAccessor {
            @Specialization
            static Object get(VirtualFrame frame, LocalAccessor accessor,
                            @Bind BytecodeNode bytecode) {
                return accessor.getObject(bytecode, frame);
            }
        }
    }
}
