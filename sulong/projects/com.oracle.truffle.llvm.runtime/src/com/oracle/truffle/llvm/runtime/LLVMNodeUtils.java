/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.llvm.runtime.SulongStackTrace.Element;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.sulong.LLVMPrintStackTrace;

/**
 * Various utility functions that can be used in detail formatter code.
 */
public final class LLVMNodeUtils {
    public static String stackTrace(LLVMNode node) {
        StringLineWriter writer = new StringLineWriter();
        printStackTrace(writer, node);
        return writer.toString();
    }

    public static String nodeAST(Node node) {
        StringLineWriter writer = new StringLineWriter();
        printNodeAST(writer, node);
        return writer.toString();
    }

    public static String stackTraceAndAST(LLVMNode node) {
        StringLineWriter writer = new StringLineWriter();
        printStackTrace(writer, node);
        writer.writeLine();
        printNodeAST(writer, node);
        return writer.toString();
    }

    public abstract static class LineWriter {
        public void writeLine() {
            writeLine("");
        }

        public abstract void writeLine(String line);

        public void writeLineFormat(String line, Object... args) {
            writeLine(String.format(line, args));
        }
    }

    public static class LambdaLineWriter extends LineWriter {
        Consumer<String> lambda;

        public LambdaLineWriter(Consumer<String> lambda) {
            this.lambda = lambda;
        }

        @Override
        public void writeLine(String line) {
            lambda.accept(line);
        }
    }

    public static class StringLineWriter extends LineWriter {
        StringBuilder buffer = new StringBuilder();

        @Override
        public void writeLine(String line) {
            buffer.append(line);
            buffer.append('\n');
        }

        @Override
        public String toString() {
            return buffer.toString();
        }
    }

    public static void printNodeAST(LineWriter writer, Node node) {
        printNodeAST(writer, node, null, 1);
    }

    private static void printNodeAST(LineWriter writer, NodeInterface node, String fieldName, int level) {
        if (node == null) {
            return;
        }

        writer.writeLineFormat("%s%s = %s", "  ".repeat(level), fieldName, node);

        for (Class<?> c = node.getClass(); c != Object.class; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || "parent".equals(field.getName())) {
                    continue;
                }
                if (NodeInterface.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        NodeInterface value = (NodeInterface) field.get(node);
                        if (value != null) {
                            printNodeAST(writer, value, field.getName(), level + 1);
                        }
                    } catch (IllegalAccessException | RuntimeException e) {
                        // ignore
                    }
                } else if (NodeInterface[].class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        NodeInterface[] value = (NodeInterface[]) field.get(node);
                        if (value != null) {
                            for (int i = 0; i < value.length; i++) {
                                printNodeAST(writer, value[i], field.getName() + "[" + i + "]", level + 1);
                            }
                        }
                    } catch (IllegalAccessException | RuntimeException e) {
                        // ignore
                    }
                }
            }
        }
    }

    private static void printStackTrace(LineWriter writer, LLVMNode node) {
        SulongStackTrace stackTrace = LLVMPrintStackTrace.getStackTrace(node);
        for (Element element : stackTrace.getTrace()) {
            writer.writeLine(element.toString());
        }
    }
}
