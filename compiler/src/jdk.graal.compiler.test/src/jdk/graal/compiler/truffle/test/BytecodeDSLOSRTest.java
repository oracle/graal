/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.AbstractBytecodeTruffleException;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.BytecodeOSRMetadata;

import jdk.graal.compiler.test.GraalTest;

public class BytecodeDSLOSRTest extends TestWithSynchronousCompiling {
    private static final BytecodeDSLOSRTestLanguage LANGUAGE = null;

    private static BytecodeDSLOSRTestRootNode parseNode(BytecodeParser<BytecodeDSLOSRTestRootNodeGen.Builder> builder) {
        BytecodeNodes<BytecodeDSLOSRTestRootNode> nodes = BytecodeDSLOSRTestRootNodeGen.create(BytecodeConfig.DEFAULT, builder);
        return nodes.getNodes().get(nodes.getNodes().size() - 1);
    }

    @Rule public TestRule timeout = GraalTest.createTimeout(30, TimeUnit.SECONDS);

    @Before
    @Override
    public void before() {
        setupContext("engine.MultiTier", "false",
                        "engine.OSR", "true",
                        "engine.OSRCompilationThreshold", String.valueOf(10 * BytecodeOSRMetadata.OSR_POLL_INTERVAL),
                        "engine.OSRMaxCompilationReAttempts", String.valueOf(1),
                        "engine.ThrowOnMaxOSRCompilationReAttemptsReached", "true");
    }

    @Test
    public void testInfiniteInterpreterLoop() {
        BytecodeDSLOSRTestRootNode root = parseNode(b -> {
            b.beginRoot(LANGUAGE);
            b.beginBlock();
            b.beginWhile();
            b.emitLoadConstant(true);
            b.emitThrowsInCompiledCode();
            b.endWhile();
            b.endBlock();
            b.endRoot();
        });

        try {
            root.getCallTarget().call();
            Assert.fail("Should not reach here.");
        } catch (BytecodeDSLOSRTestRootNode.InCompiledCodeException ex) {
            // expected
        }
    }

    @TruffleLanguage.Registration(id = "BytecodeDSLOSRTestLanguage")
    static class BytecodeDSLOSRTestLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return new Object();
        }
    }

    @GenerateBytecode(languageClass = BytecodeDSLOSRTestLanguage.class)
    public abstract static class BytecodeDSLOSRTestRootNode extends RootNode implements BytecodeRootNode {

        static class InCompiledCodeException extends AbstractBytecodeTruffleException {
            private static final long serialVersionUID = 1L;
        }

        protected BytecodeDSLOSRTestRootNode(TruffleLanguage<?> language, FrameDescriptor fd) {
            super(language, fd);
        }

        @Operation
        static final class ThrowsInCompiledCode {
            @Specialization
            public static void perform() {
                if (CompilerDirectives.inCompiledCode()) {
                    throw new InCompiledCodeException();
                }
            }
        }

    }

}
