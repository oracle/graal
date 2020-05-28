/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.api;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeMemory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@TypeSystemReference(LLVMTypes.class)
public abstract class LLVMNode extends Node {
    public static final int DOUBLE_SIZE_IN_BYTES = 8;
    public static final int FLOAT_SIZE_IN_BYTES = 4;

    public static final int I16_SIZE_IN_BYTES = 2;
    public static final int I16_SIZE_IN_BITS = 16;
    public static final int I16_MASK = 0xffff;

    public static final int I32_SIZE_IN_BYTES = 4;
    public static final int I32_SIZE_IN_BITS = 32;
    public static final long I32_MASK = 0xffffffffL;

    public static final int I64_SIZE_IN_BYTES = 8;
    public static final int I64_SIZE_IN_BITS = 64;

    public static final int I8_SIZE_IN_BYTES = 1;
    public static final int I8_SIZE_IN_BITS = 8;
    public static final int I8_MASK = 0xff;

    public static final int I1_SIZE_IN_BYTES = 1;

    public static final int ADDRESS_SIZE_IN_BYTES = 8;

    protected static PrintStream nativeCallStatisticsStream(ContextReference<LLVMContext> context) {
        CompilerAsserts.neverPartOfCompilation();
        return context.get().nativeCallStatsStream();
    }

    protected static boolean nativeCallStatisticsEnabled(ContextReference<LLVMContext> context) {
        CompilerAsserts.neverPartOfCompilation();
        return nativeCallStatisticsStream(context) != null;
    }

    protected static boolean isFunctionDescriptor(Object object) {
        return object instanceof LLVMFunctionDescriptor;
    }

    protected static LLVMFunctionDescriptor asFunctionDescriptor(Object object) {
        return object instanceof LLVMFunctionDescriptor ? (LLVMFunctionDescriptor) object : null;
    }

    protected static boolean isSameObject(Object a, Object b) {
        // used as a workaround for a DSL bug
        return a == b;
    }

    public final DataLayout getDataLayout() {
        return findDataLayout(this);
    }

    public static DataLayout findDataLayout(Node node) {
        Node datalayoutNode = node;
        while (!(datalayoutNode instanceof LLVMHasDatalayoutNode)) {
            if (datalayoutNode.getParent() != null) {
                assert !(datalayoutNode instanceof RootNode) : "root node must not have a parent";
                datalayoutNode = datalayoutNode.getParent();
            } else {
                return LLVMLanguage.getContext().getLibsulongDataLayout();
            }
        }
        return ((LLVMHasDatalayoutNode) datalayoutNode).getDatalayout();
    }

    private static final WeakHashMap<Node, Long> nodeIdentifiers = new WeakHashMap<>();
    private static final AtomicLong identifiers = new AtomicLong();

    private static synchronized long getNodeId(Node node) {
        return nodeIdentifiers.computeIfAbsent(node, (n) -> identifiers.incrementAndGet());
    }

    /**
     * See {@link #getShortString(Node, String...)}.
     */
    protected final String getShortString(String... fields) {
        return getShortString(this, fields);
    }

    /**
     * Creates a short (single line) textual description of the given node.
     *
     * A unique name is built from the class name and a global map of node identifiers.
     *
     * A source location will be appended if available.
     *
     * The given fields will be extracted (in the given order) using {@link String#valueOf(Object)}
     * (with a special case for arrays).
     */
    public static String getShortString(Node node, String... fields) {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder str = new StringBuilder();
        if (node instanceof LLVMInstrumentableNode) {
            LLVMInstrumentableNode instruction = (LLVMInstrumentableNode) node;
            if (instruction.hasTag(StatementTag.class)) {
                LLVMSourceLocation location = instruction.getSourceLocation();
                str.append(location.getName()).append(":").append(location.getLine()).append(" ");
            }

        }
        str.append(node.getClass().getSimpleName()).append("#").append(getNodeId(node));

        for (String field : fields) {
            Class<?> c = node.getClass();
            while (c != Object.class) {
                try {
                    Field declaredField = c.getDeclaredField(field);
                    declaredField.setAccessible(true);
                    Object value = declaredField.get(node);
                    str.append(" ").append(field).append("=").append(formatFieldValue(value));
                    break;
                } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                    // skip
                }
                c = c.getSuperclass();
            }
        }

        return str.toString();
    }

    @Override
    public String toString() {
        return getShortString();
    }

    private static Object formatFieldValue(Object value) {
        if (value != null) {
            if (value.getClass().isArray()) {
                return Arrays.asList((Object[]) value).toString();
            }
        }
        return String.valueOf(value);
    }

    public static boolean isAutoDerefHandle(LLVMLanguage language, LLVMNativePointer addr) {
        return isAutoDerefHandle(language, addr.asNative());
    }

    public static boolean isAutoDerefHandle(LLVMLanguage language, long addr) {
        // checking the bit is cheaper than getting the assumption in interpreted mode
        if (CompilerDirectives.inCompiledCode() && language.getNoDerefHandleAssumption().isValid()) {
            return false;
        }
        return LLVMNativeMemory.isDerefHandleMemory(addr);
    }

    /**
     * Get the closest parent of {@code node} with the given type, or {@code null} is no node in the
     * parent chain has the given type. This method will also look into wrapped parents, returning
     * the delegate node if it has the given type.
     */
    public static <T extends Node> T getParent(Node node, Class<T> clazz) {
        Node current = node;
        while (current != null) {
            if (clazz.isInstance(current)) {
                return clazz.cast(current);
            }
            if (current instanceof WrapperNode) {
                Node delegate = ((WrapperNode) current).getDelegateNode();
                if (clazz.isInstance(delegate)) {
                    return clazz.cast(delegate);
                }
            }
            current = current.getParent();
        }
        return null;
    }
}
