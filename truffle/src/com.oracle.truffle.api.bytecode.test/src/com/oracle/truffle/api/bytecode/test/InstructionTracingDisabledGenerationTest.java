/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Regression test for generated code with {@code enableInstructionTracing = false} and no
 * instrumentations: {@code updateBytecode} must not call {@code publishOldBytecodes()}, which is
 * only generated when instruction tracing (or other bytecode-updatable features) is enabled.
 *
 * @see <a href="https://github.com/oracle/graal/issues/14682">oracle/graal#14682</a>
 */
public class InstructionTracingDisabledGenerationTest {

    @Test
    public void testPublishOldBytecodesNotGeneratedWhenTracingDisabled() {
        TracingDisabledRootNode node = TracingDisabledRootNodeGen.create(null, BytecodeConfig.DEFAULT, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        assertNotNull(node);
        assertEquals(42, node.getCallTarget().call());

        List<Class<?>> generatedClasses = new ArrayList<>();
        collectClasses(TracingDisabledRootNodeGen.class, generatedClasses);
        assertFalse("publishOldBytecodes must not be generated when instruction tracing is disabled",
                        declaresPublishOldBytecodes(generatedClasses));
        assertFalse("generated class files must not mention publishOldBytecodes when instruction tracing is disabled",
                        classFilesContain(generatedClasses, "publishOldBytecodes"));
    }

    private static boolean declaresPublishOldBytecodes(List<Class<?>> generatedClasses) {
        for (Class<?> type : generatedClasses) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals("publishOldBytecodes") && method.getParameterCount() == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectClasses(Class<?> type, List<Class<?>> classes) {
        classes.add(type);
        for (Class<?> nested : type.getDeclaredClasses()) {
            collectClasses(nested, classes);
        }
    }

    private static boolean classFilesContain(List<Class<?>> types, String needle) {
        byte[] needleBytes = needle.getBytes(StandardCharsets.UTF_8);
        for (Class<?> type : types) {
            if (containsBytes(readClassFile(type), needleBytes)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readClassFile(Class<?> type) {
        String resourceName = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException("Class file not found for " + type.getName());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        int limit = haystack.length - needle.length;
        for (int start = 0; start <= limit; start++) {
            boolean match = true;
            for (int offset = 0; offset < needle.length; offset++) {
                if (haystack[start + offset] != needle[offset]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableInstructionTracing = false)
    public abstract static class TracingDisabledRootNode extends RootNode implements BytecodeRootNode {

        protected TracingDisabledRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

}
