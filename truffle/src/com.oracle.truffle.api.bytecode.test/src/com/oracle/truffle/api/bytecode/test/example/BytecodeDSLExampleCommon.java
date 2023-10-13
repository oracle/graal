/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.example;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNodes;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.nodes.RootNode;

public class BytecodeDSLExampleCommon {
    /**
     * Creates a root node using the given parameters.
     *
     * In order to parameterize tests over multiple different interpreter configurations
     * ("variants"), we take the specific interpreterClass as input. Since interpreters are
     * instantiated using a static {@code create} method, we must invoke this method using
     * reflection.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BytecodeDSLExampleBuilder> BytecodeNodes<BytecodeDSLExample> createNodes(Class<? extends BytecodeDSLExample> interpreterClass, BytecodeConfig config,
                    BytecodeParser<T> builder) {
        try {
            Method create = interpreterClass.getMethod("create", BytecodeConfig.class, BytecodeParser.class);
            return (BytecodeNodes<BytecodeDSLExample>) create.invoke(null, config, builder);
        } catch (InvocationTargetException e) {
            // Exceptions thrown by the invoked method can be rethrown as runtime exceptions that
            // get caught by the test harness.
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            // Other exceptions (e.g., NoSuchMethodError) likely indicate a bad reflective call.
            throw new AssertionError("Encountered exception during reflective call: " + e.getMessage());
        }
    }

    public static <T extends BytecodeDSLExampleBuilder> RootCallTarget parse(Class<? extends BytecodeDSLExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeRootNode rootNode = parseNode(interpreterClass, rootName, builder);
        return ((RootNode) rootNode).getCallTarget();
    }

    public static <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNode(Class<? extends BytecodeDSLExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeNodes<BytecodeDSLExample> nodes = BytecodeDSLExampleCommon.createNodes(interpreterClass, BytecodeConfig.DEFAULT, builder);
        BytecodeDSLExample op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    public static <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNodeWithSource(Class<? extends BytecodeDSLExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeNodes<BytecodeDSLExample> nodes = BytecodeDSLExampleCommon.createNodes(interpreterClass, BytecodeConfig.WITH_SOURCE, builder);
        BytecodeDSLExample op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    public static List<Class<? extends BytecodeDSLExample>> allInterpreters() {
        return List.of(BytecodeDSLExampleBase.class, BytecodeDSLExampleUnsafe.class, BytecodeDSLExampleWithUncached.class, BytecodeDSLExampleWithBE.class, BytecodeDSLExampleWithOptimizations.class,
                        BytecodeDSLExampleProduction.class);
    }

    public static boolean hasBE(Class<? extends BytecodeDSLExample> c) {
        return c == BytecodeDSLExampleWithBE.class || c == BytecodeDSLExampleProduction.class;
    }

}
