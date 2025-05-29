/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.Instruction;

public class AbstractInstructionTest {

    public static void printInstructions(BytecodeRootNode root) {
        System.out.print("[\n");
        String sep = "";
        for (Instruction s : root.getBytecodeNode().getInstructionsAsList()) {
            System.out.print(sep);
            System.out.print("\"");
            System.out.print(s.getName());
            System.out.print("\"");
            sep = ",\n";
        }
        System.out.println("\n]");
    }

    public record QuickeningCounts(int quickeningCount, int specializeCount) {
    }

    public static QuickeningCounts assertQuickenings(DebugBytecodeRootNode node, QuickeningCounts counts) {
        return assertQuickenings(node, counts.quickeningCount, counts.specializeCount);
    }

    public static QuickeningCounts assertQuickenings(DebugBytecodeRootNode node, int expectedQuickeningCount, int expectedSpecializeCount) {
        assertEquals(expectedQuickeningCount, node.quickeningCount.get());
        assertEquals(expectedSpecializeCount, node.specializeCount.get());
        return new QuickeningCounts(node.quickeningCount.get(), node.specializeCount.get());
    }

    public static void assertInstructions(BytecodeRootNode node, String... expectedInstructions) {
        List<Instruction> actualInstructions = node.getBytecodeNode().getInstructionsAsList();
        if (actualInstructions.size() != expectedInstructions.length) {
            throw throwBytecodeNodeAssertion(node, expectedInstructions, String.format("Invalid instruction size. Expected %s got %s.", expectedInstructions.length, actualInstructions.size()));
        }
        for (int i = 0; i < expectedInstructions.length; i++) {
            String expectedInstruction = expectedInstructions[i];
            Instruction actualInstruction = actualInstructions.get(i);
            if (!expectedInstruction.equals(actualInstruction.getName())) {
                throw throwBytecodeNodeAssertion(node, expectedInstructions, String.format("Invalid instruction at index %s. Expected %s got %s.",
                                i, expectedInstruction, actualInstruction.getName()));
            }
        }
    }

    private static AssertionError throwBytecodeNodeAssertion(BytecodeRootNode node, String[] expectedInstructions, String message) {
        printInstructions(node);
        return new AssertionError(String.format("%s %nExpected instructions(%s): %n    %s %nActual instructions: %s", message,
                        expectedInstructions.length, String.join("\n    ", expectedInstructions), node.dump()));
    }

    public static void assertStable(QuickeningCounts expectedCounts, DebugBytecodeRootNode node, Object... args) {
        for (int i = 0; i < 100; i++) {
            node.getCallTarget().call(args);
        }
        assertQuickenings(node, expectedCounts); // assert stable
    }

    public static void assertFails(Runnable callable, Class<?> exceptionType) {
        assertFails((Callable<?>) () -> {
            callable.run();
            return null;
        }, exceptionType);
    }

    public static void assertFails(Callable<?> callable, Class<?> exceptionType) {
        try {
            callable.call();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                throw new AssertionError("expected instanceof " + exceptionType.getName() + " was " + t.toString(), t);
            }
            return;
        }
        fail("expected " + exceptionType.getName() + " but no exception was thrown");
    }

    public static <T> void assertFails(Runnable run, Class<T> exceptionType, Consumer<T> verifier) {
        try {
            run.run();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                throw new AssertionError("expected instanceof " + exceptionType.getName() + " was " + t.toString(), t);
            }
            verifier.accept(exceptionType.cast(t));
            return;
        }
        fail("expected " + exceptionType.getName() + " but no exception was thrown");
    }

    public static <T> void assertFails(Callable<?> callable, Class<T> exceptionType, Consumer<T> verifier) {
        try {
            callable.call();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                throw new AssertionError("expected instanceof " + exceptionType.getName() + " was " + t.getClass().getName(), t);
            }
            verifier.accept(exceptionType.cast(t));
            return;
        }
        fail("expected " + exceptionType.getName() + " but no exception was thrown");
    }

}
