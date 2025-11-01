/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.InstructionDescriptor;
import com.oracle.truffle.api.bytecode.InstructionDescriptor.ArgumentDescriptor;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.InstructionTracingTest;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Tests basic bytecode descriptor functionality like instruction descriptor reflection and format
 * dumping. For instruction tracing tests see {@link InstructionTracingTest}.
 */
public class BytecodeDescriptorTest extends AbstractBasicInterpreterTest {

    public BytecodeDescriptorTest(TestRun run) {
        super(run);
    }

    @Test
    public void testDescriptor() {
        var descriptor = run.bytecode();
        assertSame(BasicInterpreter.class, descriptor.getSpecificationClass());
        assertSame(BytecodeDSLTestLanguage.class, descriptor.getLanguageClass());
        assertNotNull(descriptor.dump());
        assertNotNull(descriptor.toString());
        for (InstructionDescriptor d : descriptor.getInstructionDescriptors()) {
            assertSame(d, descriptor.getInstructionDescriptor(d.getOperationCode()));
            assertNotEquals(0, d.getLength());
            assertNotNull(d.getName());
            for (ArgumentDescriptor ad : d.getArgumentDescriptors()) {
                assertNotNull(ad.getKind());
                assertNotNull(ad.getName());
                ad.getLength();
                assertNotNull(ad.toString());
            }
            assertNotNull(d.toString());
        }
    }

    @Test
    public void testCast() {
        var descriptor = run.bytecode();
        BasicInterpreter node = descriptor.create(LANGUAGE, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        }).getNode(0);
        assertNotNull(descriptor.toString());
        assertSame(node.getRootNode(), descriptor.cast(node.getRootNode()));
        assertSame(node.getRootNode(), descriptor.cast(node.getRootNode().getCallTarget()));
        assertThrows(NullPointerException.class, () -> {
            descriptor.cast((RootNode) null);
        });
        assertThrows(NullPointerException.class, () -> {
            descriptor.cast((CallTarget) null);
        });
        assertNull(descriptor.cast(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        }));
    }

}
