/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.api.test.SubprocessTestUtils.WithSubprocess;

public class BytecodeNodeClassLoadingTest {

    private static final String ROOT_CLASS = "com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterProductionRootScopingTailCall";
    private static final String CACHED_BYTECODE_NODE = ROOT_CLASS + "$CachedBytecodeNode";
    private static final String CACHED_BYTECODE_NODE_TAIL_CALL = ROOT_CLASS + "$CachedBytecodeNodeTailCall";
    private static final String UNCACHED_BYTECODE_NODE_TAIL_CALL = ROOT_CLASS + "$UncachedBytecodeNodeTailCall";

    @Test
    @WithSubprocess
    public void testInactiveTailCallLayoutIsNotLoadedOnHotSpot() throws IOException, InterruptedException {
        assumeHotSpot();
        SubprocessTestUtils.newBuilder(BytecodeNodeClassLoadingTest.class, () -> {
            assumeHotSpot();
            BytecodeRootNodes<BasicInterpreter> nodes = BasicInterpreterProductionRootScopingTailCall.create(null, BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();
                b.beginReturn();
                b.emitLoadConstant(42L);
                b.endReturn();
                b.endRoot();
            });
            BasicInterpreter root = nodes.getNode(0);
            root.getBytecodeNode().setUncachedThreshold(0);
            RootCallTarget callTarget = root.getCallTarget();
            for (int i = 0; i < 3; i++) {
                assertEquals(42L, callTarget.call());
            }
            assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
        }).prefixVmOption("-Xlog:class+load=info").onExit(process -> {
            assertLoaded(process.output, CACHED_BYTECODE_NODE);
            assertNotLoaded(process.output, CACHED_BYTECODE_NODE_TAIL_CALL);
            assertNotLoaded(process.output, UNCACHED_BYTECODE_NODE_TAIL_CALL);
        }).run();
    }

    private static void assumeHotSpot() {
        assumeFalse(TruffleOptions.AOT);
        String vmName = System.getProperty("java.vm.name");
        assumeTrue("HotSpot VM expected, got: " + vmName,
                        vmName.contains("HotSpot") || vmName.contains("OpenJDK"));
    }

    private static void assertLoaded(List<String> output, String className) {
        assertTrue(className + " was not loaded. Output:\n" + String.join("\n", output),
                        countClassLoadEvents(output, className) > 0);
    }

    private static void assertNotLoaded(List<String> output, String className) {
        assertEquals(className + " was loaded. Output:\n" + String.join("\n", output), 0,
                        countClassLoadEvents(output, className));
    }

    private static long countClassLoadEvents(List<String> output, String className) {
        return output.stream().filter(line -> line.contains("[class,load]") && line.contains(className)).count();
    }
}
