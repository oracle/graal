/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

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
     * Mask selecting low order bits used for tagging oops.
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

    int compiledCodeMax();

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
    }

    interface DebugTypeInfo extends DebugFileInfo {
        ResolvedJavaType idType();

        enum DebugTypeKind {
            PRIMITIVE,
            ENUM,
            INSTANCE,
            INTERFACE,
            ARRAY,
            HEADER,
            FOREIGN;

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
                    case FOREIGN:
                        return "foreign";
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

        /**
         * returns the offset in the heap at which the java.lang.Class instance which models this
         * class is located or -1 if no such instance exists for this class.
         *
         * @return the offset of the java.lang.Class instance which models this class or -1.
         */
        long classOffset();

        int size();
    }

    interface DebugInstanceTypeInfo extends DebugTypeInfo {
        String loaderName();

        Stream<DebugFieldInfo> fieldInfoProvider();

        Stream<DebugMethodInfo> methodInfoProvider();

        ResolvedJavaType superClass();

        Stream<ResolvedJavaType> interfaces();
    }

    interface DebugEnumTypeInfo extends DebugInstanceTypeInfo {
    }

    interface DebugInterfaceTypeInfo extends DebugInstanceTypeInfo {
    }

    interface DebugForeignTypeInfo extends DebugInstanceTypeInfo {
        String typedefName();

        boolean isWord();

        boolean isStruct();

        boolean isPointer();

        boolean isIntegral();

        boolean isFloat();

        boolean isSigned();

        ResolvedJavaType parent();

        ResolvedJavaType pointerTo();
    }

    interface DebugArrayTypeInfo extends DebugTypeInfo {
        int baseSize();

        int lengthOffset();

        ResolvedJavaType elementType();

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

        ResolvedJavaType valueType();

        int modifiers();
    }

    interface DebugFieldInfo extends DebugMemberInfo {
        int offset();

        int size();

        boolean isEmbedded();
    }

    interface DebugMethodInfo extends DebugMemberInfo {
        /**
         * @return the line number for the outer or inlined segment.
         */
        int line();

        /**
         * @return an array of DebugLocalInfo objects holding details of this method's parameters
         */
        DebugLocalInfo[] getParamInfo();

        /**
         * @return a DebugLocalInfo objects holding details of the target instance parameter this if
         *         the method is an instance method or null if it is a static method.
         */
        DebugLocalInfo getThisParamInfo();

        /**
         * @return the symbolNameForMethod string
         */
        String symbolNameForMethod();

        /**
         * @return true if this method has been compiled in as a deoptimization target
         */
        boolean isDeoptTarget();

        /**
         * @return true if this method is a constructor.
         */
        boolean isConstructor();

        /**
         * @return true if this is a virtual method. In Graal a virtual method can become
         *         non-virtual if all other implementations are non-reachable.
         */
        boolean isVirtual();

        /**
         * @return the offset into the virtual function table for this method if virtual
         */
        int vtableOffset();

        /**
         * @return true if this method is an override of another method.
         */
        boolean isOverride();

        /*
         * Return the unique type that owns this method. <p/>
         *
         * @return the unique type that owns this method
         */
        ResolvedJavaType ownerType();

        /*
         * Return the unique identifier for this method. The result can be used to unify details of
         * methods presented via interface DebugTypeInfo with related details of compiled methods
         * presented via interface DebugRangeInfo and of call frame methods presented via interface
         * DebugLocationInfo. <p/>
         *
         * @return the unique identifier for this method
         */
        ResolvedJavaMethod idMethod();
    }

    /**
     * Access details of a compiled top level or inline method producing the code in a specific
     * {@link com.oracle.objectfile.debugentry.range.Range}.
     */
    interface DebugRangeInfo extends DebugMethodInfo {

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
    }

    /**
     * Access details of a specific compiled method.
     */
    interface DebugCodeInfo extends DebugRangeInfo {
        void debugContext(Consumer<DebugContext> action);

        /**
         * @return a stream of records detailing source local var and line locations within the
         *         compiled method.
         */
        Stream<DebugLocationInfo> locationInfoProvider();

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

        long getSize();
    }

    /**
     * Access details of code generated for a specific outer or inlined method at a given line
     * number.
     */
    interface DebugLocationInfo extends DebugRangeInfo {
        /**
         * @return the {@link DebugLocationInfo} of the nested inline caller-line
         */
        DebugLocationInfo getCaller();

        /**
         * @return a stream of {@link DebugLocalValueInfo} objects identifying local or parameter
         *         variables present in the frame of the current range.
         */
        DebugLocalValueInfo[] getLocalValueInfo();

        boolean isLeaf();
    }

    /**
     * A DebugLocalInfo details a local or parameter variable recording its name and type, the
     * (abstract machine) local slot index it resides in and the number of slots it occupies.
     */
    interface DebugLocalInfo {
        ResolvedJavaType valueType();

        String name();

        String typeName();

        int slot();

        int slotCount();

        JavaKind javaKind();

        int line();
    }

    /**
     * A DebugLocalValueInfo details the value a local or parameter variable present in a specific
     * frame. The value may be undefined. If not then the instance records its type and either its
     * (constant) value or the register or stack location in which the value resides.
     */
    interface DebugLocalValueInfo extends DebugLocalInfo {
        enum LocalKind {
            UNDEFINED,
            REGISTER,
            STACKSLOT,
            CONSTANT
        }

        LocalKind localKind();

        int regIndex();

        int stackSlot();

        long heapOffset();

        JavaConstant constantValue();
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

    Path getCachePath();

    void recordActivity();
}
