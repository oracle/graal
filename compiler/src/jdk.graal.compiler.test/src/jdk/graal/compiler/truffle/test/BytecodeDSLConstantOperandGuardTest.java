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

import com.oracle.truffle.api.bytecode.BytecodeVersion;
import org.graalvm.polyglot.Context;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class BytecodeDSLConstantOperandGuardTest extends TestWithSynchronousCompiling {

    @Before
    @Override
    public void before() {
        Context context = setupContext();
        context.initialize(BytecodeDSLTestLanguage.ID);
    }

    @Test
    public void testConstantGuardIsNotCompiled() {
        ConstantGuardRootNode root = ConstantGuardRootNodeGen.create(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginGuarded("allowed");
            b.emitLoadArgument(0);
            b.endGuarded();
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        root.getBytecodeNode().setUncachedThreshold(0);
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        // Exercise monomorphic and polymorphic caches, then the replacing specialization.
        for (int value = 42; value <= 44; value++) {
            assertEquals(value, target.call(value));
            target.compile(true);
            assertCompiled(target);
            assertEquals(value, target.call(value));
            assertCompiled(target);
        }
    }

    @GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, enableQuickening = true)
    @SuppressWarnings("unused")
    public abstract static class ConstantGuardRootNode extends RootNode implements BytecodeRootNode {
        protected ConstantGuardRootNode(BytecodeDSLTestLanguage language, FrameDescriptor.Builder descriptor) {
            super(language, descriptor.build());
        }

        @Operation
        @ConstantOperand(type = String.class)
        static final class Guarded {
            @Idempotent
            static boolean isAllowed(String key) {
                CompilerAsserts.neverPartOfCompilation();
                return key.equals("allowed");
            }

            @Specialization(guards = {"isAllowed(key)", "value == cachedValue"}, limit = "2")
            static Object cached(String key, Object value, @Cached("value") Object cachedValue) {
                return cachedValue;
            }

            @Specialization(replaces = "cached")
            static Object generic(String key, Object value) {
                return value;
            }
        }
    }
}
