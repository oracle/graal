/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debuginfo;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;

/**
 * Interfaces used to allow a native image to communicate details of types, code and data to the
 * underlying object file so that the latter can insert appropriate debug info.
 */
public interface DebugInfoProvider {
    boolean useHeapBase();

    /**
     * Number of bits oops are left shifted by when using compressed oops.
     */
    int oopCompressShift();

    /**
     * Mask delecting low order bits used for tagging oops.
     */
    int oopTagsMask();

    /**
     * Number of bytes used to store an oop reference.
     */
    int oopReferenceSize();

    /**
     * Number of bytes used to store a raw pointer.
     */
    int pointerSize();

    /**
     * Alignment of object memory area (and, therefore, of any oop) in bytes.
     */
    int oopAlignment();

    /**
     * An interface implemented by items that can be located in a file.
     */
    interface DebugFileInfo {
        /**
         * @return the name of the file containing a file element excluding any path.
         */
        String fileName();

        /**
         * @return a relative path to the file containing a file element derived from its package
         *         name or {@code null} if the element is in the empty package.
         */
        Path filePath();

        /**
         * @return a relative path to the source cache containing the cached source file of a file
         *         element or {@code null} if sources are not available.
         */
        Path cachePath();
    }

    interface DebugTypeInfo extends DebugFileInfo {
        enum DebugTypeKind {
            PRIMITIVE,
            ENUM,
            INSTANCE,
            INTERFACE,
            ARRAY,
            HEADER;

            @Override
            public String toString() {
                switch (this) {
                    case PRIMITIVE:
                        return "primitive";
                    case ENUM:
                        return "enum";
                    case INSTANCE:
                        return "instance";
                    case INTERFACE:
                        return "interface";
                    case ARRAY:
                        return "array";
                    case HEADER:
                        return "header";
                    default:
                        return "???";
                }
            }
        }

        void debugContext(Consumer<DebugContext> action);

        /**
         * @return the fully qualified name of the debug type.
         */
        String typeName();

        DebugTypeKind typeKind();

        int size();
    }

    interface DebugInstanceTypeInfo extends DebugTypeInfo {
        int headerSize();

        Stream<DebugFieldInfo> fieldInfoProvider();

        Stream<DebugMethodInfo> methodInfoProvider();

        String superName();

        Stream<String> interfaces();
    }

    interface DebugEnumTypeInfo extends DebugInstanceTypeInfo {
    }

    interface DebugInterfaceTypeInfo extends DebugInstanceTypeInfo {
    }

    interface DebugArrayTypeInfo extends DebugTypeInfo {
        int baseSize();

        int lengthOffset();

        String elementType();

        Stream<DebugFieldInfo> fieldInfoProvider();
    }

    interface DebugPrimitiveTypeInfo extends DebugTypeInfo {
        /*
         * NUMERIC excludes LOGICAL types boolean and void
         */
        int FLAG_NUMERIC = 1 << 0;
        /*
         * INTEGRAL excludes FLOATING types float and double
         */
        int FLAG_INTEGRAL = 1 << 1;
        /*
         * SIGNED excludes UNSIGNED type char
         */
        int FLAG_SIGNED = 1 << 2;

        int bitCount();

        char typeChar();

        int flags();
    }

    interface DebugHeaderTypeInfo extends DebugTypeInfo {

        Stream<DebugFieldInfo> fieldInfoProvider();
    }

    interface DebugMemberInfo extends DebugFileInfo {

        String name();

        String valueType();

        int modifiers();
    }

    interface DebugFieldInfo extends DebugMemberInfo {
        int offset();

        int size();
    }

    interface DebugMethodInfo extends DebugMemberInfo {
        /**
         * @return an array of Strings identifying the method parameters.
         */
        List<String> paramTypes();

        /**
         * @return an array of Strings with the method parameters' names.
         */
        List<String> paramNames();

        /**
         * @return the symbolNameForMethod string
         */
        String symbolNameForMethod();

        /**
         * @return true if this method has been compiled in as a deoptimization target
         */
        boolean isDeoptTarget();
    }

    /**
     * Access details of a compiled method producing the code in a specific
     * {@link com.oracle.objectfile.debugentry.Range}.
     */
    interface DebugRangeInfo extends DebugMethodInfo {
        ResolvedJavaType ownerType();
    }

    /**
     * Access details of a specific compiled method.
     */
    interface DebugCodeInfo extends DebugRangeInfo {
        void debugContext(Consumer<DebugContext> action);

        /**
         * @return the lowest address containing code generated for the method represented as an
         *         offset into the code segment.
         */
        int addressLo();

        /**
         * @return the first address above the code generated for the method represented as an
         *         offset into the code segment.
         */
        int addressHi();

        /**
         * @return the starting line number for the method.
         */
        int line();

        /**
         * @return a stream of records detailing line numbers and addresses within the compiled
         *         method.
         */
        Stream<DebugLineInfo> lineInfoProvider();

        /**
         * @return the size of the method frame between prologue and epilogue.
         */
        int getFrameSize();

        /**
         * @return a list of positions at which the stack is extended to a full frame or torn down
         *         to an empty frame
         */
        List<DebugFrameSizeChange> getFrameSizeChanges();
    }

    /**
     * Access details of a specific heap object.
     */
    interface DebugDataInfo {
        void debugContext(Consumer<DebugContext> action);

        String getProvenance();

        String getTypeName();

        String getPartition();

        long getOffset();

        long getAddress();

        long getSize();
    }

    /**
     * Access details of code generated for a specific outer or inlined method at a given line
     * number.
     */
    interface DebugLineInfo extends DebugRangeInfo {
        /**
         * @return the lowest address containing code generated for an outer or inlined code segment
         *         reported at this line represented as an offset into the code segment.
         */
        int addressLo();

        /**
         * @return the first address above the code generated for an outer or inlined code segment
         *         reported at this line represented as an offset into the code segment.
         */
        int addressHi();

        /**
         * @return the line number for the outer or inlined segment.
         */
        int line();

        /**
         * @return the {@link DebugLineInfo} of the nested inline caller-line
         */
        DebugLineInfo getCaller();
    }

    interface DebugFrameSizeChange {
        enum Type {
            EXTEND,
            CONTRACT
        }

        int getOffset();

        DebugFrameSizeChange.Type getType();
    }

    @SuppressWarnings("unused")
    Stream<DebugTypeInfo> typeInfoProvider();

    Stream<DebugCodeInfo> codeInfoProvider();

    @SuppressWarnings("unused")
    Stream<DebugDataInfo> dataInfoProvider();
}
