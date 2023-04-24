/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.HashMap;

import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import static com.oracle.svm.core.util.VMError.unimplemented;

import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;

public class LLVMDebugInfoProvider implements DebugInfoProvider {
    public static NativeImageHeap heap;
    NativeImageDebugInfoProvider dbgInfoHelper;
    public static HashMap<String, HostedType> typeMap = new HashMap<String, HostedType>();
    public DebugContext debugContext;

    public LLVMDebugInfoProvider() {
    }

   public static void initializeHeap(NativeImageHeap heapArg) {
       heap = heapArg;
   }

   public NativeImageDebugInfoProvider getHelper() {
        return dbgInfoHelper;
   }

   @Override
    public Stream<DebugTypeInfo> typeInfoProvider() {
        throw unimplemented();
    }

   public static void generateTypeMap() {
        Stream<HostedType> typeStream = heap.getUniverse().getTypes().stream();
        typeStream.forEach(hostedType -> typeMap.put(hostedType.getName(), hostedType));
    }

    public class LLVMDebugFieldInfo extends LLVMDebugFileInfo implements DebugFieldInfo {
        HostedField field;

        LLVMDebugFieldInfo(HostedField field) {
            super(field);
            this.field = field;
        }

        public HostedField getField() {
            return field;
        }

        @Override
        public String name() {
            return field.getName();
        }

        @Override
        public ResolvedJavaType valueType() {
            return DebugInfoProviderHelper.getOriginal(field.getType());
        }

        @Override
        public int modifiers() {
            throw unimplemented();
        }

        @Override
        public int offset() {
            return field.getOffset();
        }

        @Override
        public int size() {
            return DebugInfoProviderHelper.getObjectLayout().sizeInBytes(field.getType().getStorageKind());
        }

        public boolean isEnumerator() {
            return ((field.getType() instanceof HostedClass) && (((HostedClass) (field.getType())).isEnum()));
        }
    }

    public class LLVMDebugPrimitiveTypeInfo extends LLVMDebugTypeInfo implements DebugPrimitiveTypeInfo {
        private final HostedPrimitiveType primitiveType;
        public LLVMDebugPrimitiveTypeInfo(HostedPrimitiveType primitiveType) {
            super(primitiveType);
            this.primitiveType = primitiveType;

        }
        @Override
        public int bitCount() {
            return DebugInfoProviderHelper.getPrimitiveBitCount(primitiveType);
        }

        @Override
        public char typeChar() {
            throw unimplemented();
        }

        @Override
        public int flags() {
            return DebugInfoProviderHelper.getPrimitiveFlags(primitiveType);
        }

        public byte computeEncoding() {
            return DebugInfoProviderHelper.computeEncoding(flags(), bitCount());
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.PRIMITIVE;
        }
    }


    public class LLVMDebugEnumTypeInfo extends LLVMDebugInstanceTypeInfo implements DebugEnumTypeInfo {
        public LLVMDebugEnumTypeInfo(HostedInstanceClass enumClass) {
            super(enumClass);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.ENUM;
        }
    }


    public class LLVMDebugInstanceTypeInfo extends LLVMDebugTypeInfo implements DebugInstanceTypeInfo {

        public LLVMDebugInstanceTypeInfo(HostedType hostedType) {
            super(hostedType);
        }

        @Override
        public int headerSize() {
            throw unimplemented();
        }

        @Override
        public String loaderName() {
            throw unimplemented();
        }

        @Override
        public Stream<DebugFieldInfo> fieldInfoProvider() {
            Stream<DebugFieldInfo> instanceFieldsStream =
                    Arrays.stream(hostedType.getInstanceFields(false)).map(this::createDebugFieldInfo);
            if (hostedType instanceof HostedInstanceClass && hostedType.getStaticFields().length > 0) {
                Stream<DebugFieldInfo> staticFieldsStream = Arrays.stream(hostedType.getStaticFields()).map(this::createDebugStaticFieldInfo);
                return Stream.concat(instanceFieldsStream, staticFieldsStream);
            } else {
                return instanceFieldsStream;
            }
        }

        public Stream<DebugFieldInfo> staticFieldInfoProvider() {
            if (hostedType instanceof HostedInstanceClass && hostedType.getStaticFields().length > 0) {
                Stream<DebugFieldInfo> staticFieldsStream =
                        Arrays.stream(hostedType.getStaticFields()).map(this::createDebugStaticFieldInfo);
                return staticFieldsStream;
            } else {
                return null;
            }
        }

        private LLVMDebugFieldInfo createDebugFieldInfo(HostedField field) {
            return new LLVMDebugFieldInfo(field);
        }

        private LLVMDebugFieldInfo createDebugStaticFieldInfo(ResolvedJavaField field) {
            return new LLVMDebugFieldInfo((HostedField) field);
        }

        @Override
        public Stream<DebugMethodInfo> methodInfoProvider() {
            throw unimplemented();
        }

        @Override
        public ResolvedJavaType superClass() {
            return hostedType.getSuperclass();
        }

        @Override
        public Stream<ResolvedJavaType> interfaces() {
            throw unimplemented();
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.INSTANCE;
        }
    }

    public class LLVMDebugArrayTypeInfo extends LLVMDebugTypeInfo implements DebugArrayTypeInfo {
        private HostedArrayClass arrayClass;

        public LLVMDebugArrayTypeInfo(HostedArrayClass arrayClass) {
            super(arrayClass);
            this.arrayClass = arrayClass;
        }

        public int arrayDimension() {
            return arrayClass.getArrayDimension();
        }

        public HostedType baseType() {
            return arrayClass.getBaseType();
        }

        @Override
        public int baseSize() {
            return size();
        }

        @Override
        public int lengthOffset() {
            throw unimplemented();
        }

        @Override
        public ResolvedJavaType elementType() {
            throw unimplemented();
        }

        @Override
        public Stream<DebugFieldInfo> fieldInfoProvider() {
            throw unimplemented();
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.ARRAY;
        }
    }

    public class LLVMDebugTypeInfo extends LLVMDebugFileInfo implements DebugTypeInfo {
        HostedType hostedType;

        public LLVMDebugTypeInfo(HostedType hostedType) {
            super(hostedType);
            this.hostedType = hostedType;
        }

        @Override
        public ResolvedJavaType idType() {
            // always use the original type for establishing identity
            return DebugInfoProviderHelper.getOriginal(hostedType);
        }

        @Override
        public void debugContext(Consumer<DebugContext> action) {

        }

        @Override
        public String typeName() {
            return DebugInfoProviderHelper.toJavaName(hostedType);
        }

        @Override
        public DebugTypeKind typeKind() {
            return null;
        }

        @Override
        public int size() {
            return DebugInfoProviderHelper.typeSize(hostedType);
        }
    }
    public class LLVMDebugFileInfo implements DebugFileInfo {
        Path fullFilePath;

        LLVMDebugFileInfo(ResolvedJavaMethod method, DebugContext debugContext) {
            fullFilePath = DebugInfoProviderHelper.getFullFilePathFromMethod(method, debugContext);
        }

        LLVMDebugFileInfo(HostedType hostedType) {
            fullFilePath = DebugInfoProviderHelper.getFullFilePathFromType(hostedType, debugContext);
        }

        LLVMDebugFileInfo(HostedField hostedField) {
            fullFilePath = DebugInfoProviderHelper.getFullFilePathFromField(hostedField, debugContext);
        }


        @Override
        public String fileName() {
            return DebugInfoProviderHelper.getFileName(fullFilePath);
        }

        @Override
        public Path filePath() {
            return DebugInfoProviderHelper.getFilePath(fullFilePath);
        }

        @Override
        public Path cachePath() {
            return null;
        }
}

   public class LLVMLocationInfo extends LLVMDebugFileInfo implements DebugLocationInfo {
        private int bci;
        private ResolvedJavaMethod method;

        public LLVMLocationInfo(ResolvedJavaMethod method, int bci, DebugContext debugContext) {
            super(method, debugContext);
            this.bci = bci;
            this.method = method;
        }

       @Override
       public int line() {
            return DebugInfoProviderHelper.getLineNumber(this.method, this.bci);
       }

       @Override
       public DebugLocalInfo[] getParamInfo() {
           throw unimplemented();
       }

       @Override
       public DebugLocalInfo getThisParamInfo() {
           throw unimplemented();
       }

       @Override
       public String symbolNameForMethod() {
           throw unimplemented();
       }

       @Override
       public boolean isDeoptTarget() {
           throw unimplemented();
       }

       @Override
       public boolean isConstructor() {
           throw unimplemented();
       }

       @Override
       public boolean isVirtual() {
           throw unimplemented();
       }

       @Override
       public int vtableOffset() {
           throw unimplemented();
       }

       @Override
       public boolean isOverride() {
           throw unimplemented();
       }

       @Override
       public ResolvedJavaType ownerType() {
           throw unimplemented();
       }

       @Override
       public ResolvedJavaMethod idMethod() {
           throw unimplemented();
       }

       @Override
       public String name() {
           return DebugInfoProviderHelper.getMethodName(method);
       }

       @Override
       public ResolvedJavaType valueType() {
           throw unimplemented();
       }

       @Override
       public int modifiers() {
           throw unimplemented();
       }

       @Override
       public int addressLo() {
           throw unimplemented();
       }

       @Override
       public int addressHi() {
           throw unimplemented();
       }

       @Override
       public DebugLocationInfo getCaller() {
           throw unimplemented();
       }

       @Override
       public DebugLocalValueInfo[] getLocalValueInfo() {
           throw unimplemented();
       }
   }



    @Override
    public boolean useHeapBase() {
        throw unimplemented();
    }

    @Override
    public int oopCompressShift() {
        throw unimplemented();
    }

    @Override
    public int oopTagsMask() {
        throw unimplemented();
    }

    @Override
    public int oopReferenceSize() {
        throw unimplemented();
    }

    @Override
    public int pointerSize() {
        throw unimplemented();
    }

    @Override
    public int oopAlignment() {
        throw unimplemented();
    }

    @Override
    public Stream<DebugCodeInfo> codeInfoProvider() {
        throw unimplemented();
    }

    @Override
    public Stream<DebugDataInfo> dataInfoProvider() {
        throw unimplemented();
    }
}
