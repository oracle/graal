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
package com.oracle.svm.hosted.image;

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange.Type.CONTRACT;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange.Type.EXTEND;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedInterface;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;

import com.oracle.svm.hosted.substitute.InjectedFieldsType;
import com.oracle.svm.hosted.substitute.SubstitutionField;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.SourceMapping;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.nativeimage.ImageSingletons;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Implementation of the DebugInfoProvider API interface that allows type, code and heap data info
 * to be passed to an ObjectFile when generation of debug info is enabled.
 */
class NativeImageDebugInfoProvider implements DebugInfoProvider {
    private static final JavaKind[] ARRAY_KINDS = {JavaKind.Boolean, JavaKind.Byte, JavaKind.Char, JavaKind.Short, JavaKind.Int, JavaKind.Float, JavaKind.Long, JavaKind.Double, JavaKind.Object};

    private final DebugContext debugContext;
    private final NativeImageCodeCache codeCache;
    @SuppressWarnings("unused") private final NativeImageHeap heap;
    boolean useHeapBase;
    int heapShift;
    int primitiveStartOffset;
    int referenceStartOffset;

    NativeImageDebugInfoProvider(DebugContext debugContext, NativeImageCodeCache codeCache, NativeImageHeap heap) {
        super();
        this.debugContext = debugContext;
        this.codeCache = codeCache;
        this.heap = heap;
        ObjectInfo primitiveFields = heap.getObjectInfo(StaticFieldsSupport.getStaticPrimitiveFields());
        ObjectInfo objectFields = heap.getObjectInfo(StaticFieldsSupport.getStaticObjectFields());
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
            this.useHeapBase = compressEncoding.hasBase();
            this.heapShift = (compressEncoding.hasShift() ? compressEncoding.getShift() : 0);
        } else {
            this.useHeapBase = false;
            this.heapShift = 0;
        }
        // offsets need to be adjusted relative to the heap base plus partition-specific offset
        primitiveStartOffset = (int) primitiveFields.getOffset();
        referenceStartOffset = (int) objectFields.getOffset();
    }

    @Override
    public boolean useHeapBase() {
        return useHeapBase;
    }

    @Override
    public Stream<DebugTypeInfo> typeInfoProvider() {
        Stream<DebugTypeInfo> headerTypeInfo = computeHeaderTypeInfo();
        Stream<DebugTypeInfo> heapTypeInfo = heap.getUniverse().getTypes().stream().map(this::createDebugTypeInfo);
        return Stream.concat(headerTypeInfo, heapTypeInfo);
    }

    @Override
    public Stream<DebugCodeInfo> codeInfoProvider() {
        return codeCache.compilations.entrySet().stream().map(entry -> new NativeImageDebugCodeInfo(entry.getKey(), entry.getValue()));
    }

    @Override
    public Stream<DebugDataInfo> dataInfoProvider() {
        return heap.getObjects().stream().filter(this::acceptObjectInfo).map(this::createDebugDataInfo);
    }

    static ObjectLayout OBJECTLAYOUT = ConfigurationValues.getObjectLayout();

    /*
     * HostedType wraps an AnalysisType and both HostedType and AnalysisType punt calls to
     * getSourceFilename to the wrapped class so for consistency we need to do type names and path
     * lookup relative to the doubly unwrapped HostedType.
     *
     * However, note that the result of the unwrap on the AnalysisType may be a SubstitutionType
     * which wraps both an original type and the annotated type that substitutes it. Unwrapping
     * normally returns the AnnotatedType which we need to use to resolve the file name. However, we
     * need to use the original to name the owning type to ensure that names found in method param
     * and return types resolve correctly.
     */
    protected static ResolvedJavaType getJavaType(HostedType hostedType, boolean wantOriginal) {
        ResolvedJavaType javaType;
        if (wantOriginal) {
            // check for wholesale replacement of the original class
            javaType = hostedType.getWrapped().getWrappedWithoutResolve();
            if (javaType instanceof SubstitutionType) {
                return ((SubstitutionType) javaType).getOriginal();
            } else if (javaType instanceof CustomSubstitutionType<?, ?>) {
                return ((CustomSubstitutionType<?, ?>) javaType).getOriginal();
            } else if (javaType instanceof LambdaSubstitutionType) {
                return ((LambdaSubstitutionType) javaType).getOriginal();
            } else if (javaType instanceof InjectedFieldsType) {
                return ((InjectedFieldsType) javaType).getOriginal();
            } else {
                return javaType;
            }
        }
        return hostedType.getWrapped().getWrapped();
    }

    protected static ResolvedJavaType getJavaType(HostedMethod hostedMethod, boolean wantOriginal) {
        if (wantOriginal) {
            // check for wholesale replacement of the original class
            HostedType hostedType = hostedMethod.getDeclaringClass();
            ResolvedJavaType javaType = hostedType.getWrapped().getWrappedWithoutResolve();
            if (javaType instanceof SubstitutionType) {
                return ((SubstitutionType) javaType).getOriginal();
            } else if (javaType instanceof CustomSubstitutionType<?, ?>) {
                return ((CustomSubstitutionType<?, ?>) javaType).getOriginal();
            } else if (javaType instanceof LambdaSubstitutionType) {
                return ((LambdaSubstitutionType) javaType).getOriginal();
            } else if (javaType instanceof InjectedFieldsType) {
                return ((InjectedFieldsType) javaType).getOriginal();
            }
            // check for replacement of the original method only
            ResolvedJavaMethod javaMethod = hostedMethod.getWrapped().getWrapped();
            if (javaMethod instanceof SubstitutionMethod) {
                javaMethod = ((SubstitutionMethod) javaMethod).getOriginal();
            } else if (javaMethod instanceof CustomSubstitutionMethod) {
                javaMethod = ((CustomSubstitutionMethod) javaMethod).getOriginal();
            }
            return javaMethod.getDeclaringClass();
        }
        ResolvedJavaMethod javaMethod = hostedMethod.getWrapped().getWrapped();
        return javaMethod.getDeclaringClass();
    }

    protected static ResolvedJavaType getJavaType(HostedField hostedField, boolean wantOriginal) {
        if (wantOriginal) {
            // check for wholesale replacement of the original class
            HostedType hostedType = hostedField.getDeclaringClass();
            ResolvedJavaType javaType = hostedType.getWrapped().getWrappedWithoutResolve();
            if (javaType instanceof SubstitutionType) {
                return ((SubstitutionType) javaType).getOriginal();
            } else if (javaType instanceof CustomSubstitutionType<?, ?>) {
                return ((CustomSubstitutionType<?, ?>) javaType).getOriginal();
            } else if (javaType instanceof LambdaSubstitutionType) {
                return ((LambdaSubstitutionType) javaType).getOriginal();
            } else if (javaType instanceof InjectedFieldsType) {
                return ((InjectedFieldsType) javaType).getOriginal();
            }
            // check for replacement of the original field only
            ResolvedJavaField javaField = hostedField.wrapped.wrapped;
            if (javaField instanceof SubstitutionField) {
                javaField = ((SubstitutionField) javaField).getOriginal();
            }
            return javaField.getDeclaringClass();
        }
        ResolvedJavaField javaField = hostedField.wrapped.wrapped;
        return javaField.getDeclaringClass();
    }

    private static final Path cachePath = SubstrateOptions.getDebugInfoSourceCacheRoot();

    private abstract class NativeImageDebugFileInfo implements DebugFileInfo {
        private Path fullFilePath;

        @SuppressWarnings("try")
        NativeImageDebugFileInfo(HostedType hostedType) {
            ResolvedJavaType javaType = getJavaType(hostedType, false);
            Class<?> clazz;
            if (hostedType instanceof OriginalClassProvider) {
                clazz = ((OriginalClassProvider) hostedType).getJavaClass();
            } else {
                clazz = null;
            }
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
                fullFilePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @SuppressWarnings("try")
        NativeImageDebugFileInfo(HostedMethod hostedMethod) {
            ResolvedJavaType javaType = getJavaType(hostedMethod, false);
            HostedType hostedType = hostedMethod.getDeclaringClass();
            Class<?> clazz;
            if (hostedType instanceof OriginalClassProvider) {
                clazz = ((OriginalClassProvider) hostedType).getJavaClass();
            } else {
                clazz = null;
            }
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
                fullFilePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @SuppressWarnings("try")
        NativeImageDebugFileInfo(HostedField hostedField) {
            ResolvedJavaType javaType = getJavaType(hostedField, false);
            HostedType hostedType = hostedField.getDeclaringClass();
            Class<?> clazz;
            if (hostedType instanceof OriginalClassProvider) {
                clazz = ((OriginalClassProvider) hostedType).getJavaClass();
            } else {
                clazz = null;
            }
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
                fullFilePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @Override
        public String fileName() {
            if (fullFilePath != null) {
                Path filename = fullFilePath.getFileName();
                if (filename != null) {
                    return filename.toString();
                }
            }
            return "";
        }

        @Override
        public Path filePath() {
            if (fullFilePath != null) {
                return fullFilePath.getParent();
            }
            return null;
        }

        @Override
        public Path cachePath() {
            return cachePath;
        }
    }

    private abstract class NativeImageDebugTypeInfo extends NativeImageDebugFileInfo implements DebugTypeInfo {
        protected final HostedType hostedType;

        @SuppressWarnings("try")
        protected NativeImageDebugTypeInfo(HostedType hostedType) {
            super(hostedType);
            this.hostedType = hostedType;
        }

        @SuppressWarnings("try")
        @Override
        public void debugContext(Consumer<DebugContext> action) {
            try (DebugContext.Scope s = debugContext.scope("DebugTypeInfo", typeName())) {
                action.accept(debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        public String toJavaName(HostedType hostedType) {
            return getJavaType(hostedType, true).toJavaName();
        }

        @Override
        public String typeName() {
            return toJavaName(hostedType);
        }

        @Override
        public int size() {
            if (hostedType instanceof HostedInstanceClass) {
                // We know the actual instance size in bytes.
                return ((HostedInstanceClass) hostedType).getInstanceSize();
            } else if (hostedType instanceof HostedArrayClass) {
                // Use the size of header common to all arrays of this type.
                return OBJECTLAYOUT.getArrayBaseOffset(hostedType.getComponentType().getStorageKind());
            } else if (hostedType instanceof HostedInterface) {
                // Use the size of the header common to all implementors.
                return OBJECTLAYOUT.getFirstFieldOffset();
            } else {
                // Use the number of bytes needed needed to store the value.
                assert hostedType instanceof HostedPrimitiveType;
                JavaKind javaKind = hostedType.getStorageKind();
                return (javaKind == JavaKind.Void ? 0 : javaKind.getByteCount());
            }
        }
    }

    private class NativeImageHeaderTypeInfo implements DebugHeaderTypeInfo {
        String typeName;
        int size;
        JavaKind elementKind;
        List<DebugFieldInfo> fieldInfos;

        NativeImageHeaderTypeInfo(String typeName, int size, JavaKind baseKind) {
            this.typeName = typeName;
            this.size = size;
            this.elementKind = baseKind;
            this.fieldInfos = new LinkedList<>();
        }

        void addField(String name, String valueType, int offset, int size) {
            NativeImageDebugHeaderFieldInfo fieldinfo = new NativeImageDebugHeaderFieldInfo(name, typeName, valueType, offset, size);
            fieldInfos.add(fieldinfo);
        }

        @SuppressWarnings("try")
        @Override
        public void debugContext(Consumer<DebugContext> action) {
            try (DebugContext.Scope s = debugContext.scope("DebugTypeInfo", typeName())) {
                action.accept(debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @Override
        public String typeName() {
            return typeName;
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.HEADER;
        }

        @Override
        public String fileName() {
            return "";
        }

        @Override
        public Path filePath() {
            return null;
        }

        @Override
        public Path cachePath() {
            return null;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public Stream<DebugFieldInfo> fieldInfoProvider() {
            return fieldInfos.stream();
        }

        private class NativeImageDebugHeaderFieldInfo implements DebugFieldInfo {
            private final String name;
            private final String ownerType;
            private final String valueType;
            private final int offset;
            private final int size;
            private final int modifiers;

            NativeImageDebugHeaderFieldInfo(String name, String ownerType, String valueType, int offset, int size) {
                this.name = name;
                this.ownerType = ownerType;
                this.valueType = valueType;
                this.offset = offset;
                this.size = size;
                this.modifiers = Modifier.PUBLIC;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String ownerType() {
                return ownerType;
            }

            @Override
            public String valueType() {
                return valueType;
            }

            @Override
            public int offset() {
                return offset;
            }

            @Override
            public int size() {
                return size;
            }

            @Override
            public int modifiers() {
                return modifiers;
            }

            @Override
            public String fileName() {
                return "";
            }

            @Override
            public Path filePath() {
                return null;
            }

            @Override
            public Path cachePath() {
                return null;
            }
        }
    }

    private Stream<DebugTypeInfo> computeHeaderTypeInfo() {
        List<DebugTypeInfo> infos = new LinkedList<>();
        int hubOffset = OBJECTLAYOUT.getHubOffset();
        int referenceSize = OBJECTLAYOUT.getReferenceSize();
        int hubFieldSize = referenceSize;
        String hubTypeName = "java.lang.Class";
        int arrayLengthOffset = OBJECTLAYOUT.getArrayLengthOffset();
        int arrayLengthSize = OBJECTLAYOUT.sizeInBytes(JavaKind.Int);
        int arrayIdHashOffset = OBJECTLAYOUT.getArrayIdentityHashcodeOffset();
        int arrayIdHashSize = OBJECTLAYOUT.sizeInBytes(JavaKind.Int);
        int objHeaderSize = OBJECTLAYOUT.getFirstFieldOffset();
        // we need array headers for all Java kinds

        NativeImageHeaderTypeInfo objHeader = new NativeImageHeaderTypeInfo("_objhdr", objHeaderSize, null);
        objHeader.addField("hub", hubTypeName, hubOffset, hubFieldSize);
        infos.add(objHeader);

        // create a header for each
        for (JavaKind arrayKind : ARRAY_KINDS) {
            String name = "_arrhdr" + arrayKind.getTypeChar();
            int headerSize = OBJECTLAYOUT.getArrayBaseOffset(arrayKind);
            NativeImageHeaderTypeInfo arrHeader = new NativeImageHeaderTypeInfo(name, headerSize, arrayKind);
            arrHeader.addField("hub", hubTypeName, hubOffset, hubFieldSize);
            arrHeader.addField("len", "int", arrayLengthOffset, arrayLengthSize);
            if (arrayIdHashOffset > 0) {
                arrHeader.addField("idHash", "int", arrayIdHashOffset, arrayIdHashSize);
            }
            infos.add(arrHeader);
        }
        return infos.stream();
    }

    private class NativeImageDebugEnumTypeInfo extends NativeImageDebugInstanceTypeInfo implements DebugEnumTypeInfo {
        HostedInstanceClass enumClass;

        NativeImageDebugEnumTypeInfo(HostedInstanceClass enumClass) {
            super(enumClass);
            this.enumClass = enumClass;
        }

        public DebugTypeKind typeKind() {
            return DebugTypeKind.ENUM;
        }
    }

    private class NativeImageDebugInstanceTypeInfo extends NativeImageDebugTypeInfo implements DebugInstanceTypeInfo {
        NativeImageDebugInstanceTypeInfo(HostedType hostedType) {
            super(hostedType);
        }

        public DebugTypeKind typeKind() {
            return DebugTypeKind.INSTANCE;
        }

        @Override
        public int headerSize() {
            return OBJECTLAYOUT.getFirstFieldOffset();
        }

        @Override
        public Stream<DebugFieldInfo> fieldInfoProvider() {
            Stream<DebugFieldInfo> instanceFieldsStream = Arrays.stream(hostedType.getInstanceFields(false)).map(this::createDebugFieldInfo);
            if (hostedType instanceof HostedInstanceClass && hostedType.getStaticFields().length > 0) {
                Stream<DebugFieldInfo> staticFieldsStream = Arrays.stream(hostedType.getStaticFields()).map(this::createDebugStaticFieldInfo);
                return Stream.concat(instanceFieldsStream, staticFieldsStream);
            } else {
                return instanceFieldsStream;
            }
        }

        @Override
        public Stream<DebugMethodInfo> methodInfoProvider() {
            return Arrays.stream(hostedType.getAllDeclaredMethods()).map(this::createDebugMethodInfo);
        }

        @Override
        public String superName() {
            HostedClass superClass = hostedType.getSuperclass();
            /*
             * HostedType wraps an AnalysisType and both HostedType and AnalysisType punt calls to
             * getSourceFilename to the wrapped class so for consistency we need to do the path
             * lookup relative to the doubly unwrapped HostedType.
             */
            if (superClass != null) {
                return getJavaType(superClass, true).toJavaName();
            }
            return null;
        }

        @Override
        public Stream<String> interfaces() {
            final NativeImageDebugTypeInfo typeInfo = this;
            return Arrays.stream(hostedType.getInterfaces()).map(this::toJavaName);
        }

        protected NativeImageDebugFieldInfo createDebugFieldInfo(HostedField field) {
            return new NativeImageDebugFieldInfo(field);
        }

        protected NativeImageDebugFieldInfo createDebugStaticFieldInfo(ResolvedJavaField field) {
            return new NativeImageDebugFieldInfo((HostedField) field);
        }

        protected NativeImageDebugMethodInfo createDebugMethodInfo(HostedMethod method) {
            return new NativeImageDebugMethodInfo(method);
        }

        protected class NativeImageDebugFieldInfo extends NativeImageDebugFileInfo implements DebugFieldInfo {
            private final HostedField field;

            NativeImageDebugFieldInfo(HostedField field) {
                super(field);
                this.field = field;
            }

            @Override
            public String name() {
                return field.getName();
            }

            @Override
            public String ownerType() {
                return typeName();
            }

            @Override
            public String valueType() {
                HostedType valueType = field.getType();
                return toJavaName(valueType);
            }

            @Override
            public int offset() {
                int offset = field.getLocation();
                // for static fields we need to add in the appropriate partition base
                // but only if we have a real offset
                if (isStatic() && offset >= 0) {
                    if (isPrimitive()) {
                        offset += primitiveStartOffset;
                    } else {
                        offset += referenceStartOffset;
                    }
                }
                return offset;
            }

            @Override
            public int size() {
                return OBJECTLAYOUT.sizeInBytes(field.getType().getStorageKind());
            }

            @Override
            public int modifiers() {
                return field.getModifiers();
            }

            private boolean isStatic() {
                return Modifier.isStatic(modifiers());
            }

            private boolean isPrimitive() {
                return field.getType().getStorageKind().isPrimitive();
            }
        }

        protected class NativeImageDebugMethodInfo extends NativeImageDebugFileInfo implements DebugMethodInfo {
            private final HostedMethod hostedMethod;

            NativeImageDebugMethodInfo(HostedMethod hostedMethod) {
                super(hostedMethod);
                this.hostedMethod = hostedMethod;
            }

            @Override
            public String name() {
                String name = hostedMethod.format("%n");
                if ("<init>".equals(name)) {
                    ResolvedJavaMethod unwrapped = hostedMethod.getWrapped().getWrapped();
                    if (unwrapped instanceof SubstitutionMethod) {
                        unwrapped = ((SubstitutionMethod) unwrapped).getOriginal();
                    }
                    name = unwrapped.format("%h");
                    if (name.indexOf('$') >= 0) {
                        name = name.substring(name.lastIndexOf('$') + 1);
                    }
                }
                return name;
            }

            @Override
            public String ownerType() {
                return typeName();
            }

            @Override
            public String valueType() {
                return hostedMethod.getSignature().getReturnType(null).toJavaName();
            }

            @Override
            public List<String> paramTypes() {
                LinkedList<String> paramTypes = new LinkedList<>();
                Signature signature = hostedMethod.getSignature();
                for (int i = 0; i < signature.getParameterCount(false); i++) {
                    paramTypes.add(signature.getParameterType(i, null).toJavaName());
                }
                return paramTypes;
            }

            @Override
            public List<String> paramNames() {
                // can only provide blank names for now
                LinkedList<String> paramNames = new LinkedList<>();
                Signature signature = hostedMethod.getSignature();
                for (int i = 0; i < signature.getParameterCount(false); i++) {
                    paramNames.add("");
                }
                return paramNames;
            }

            @Override
            public int modifiers() {
                return hostedMethod.getModifiers();
            }
        }
    }

    private class NativeImageDebugInterfaceTypeInfo extends NativeImageDebugInstanceTypeInfo implements DebugInterfaceTypeInfo {
        HostedInterface interfaceClass;

        NativeImageDebugInterfaceTypeInfo(HostedInterface interfaceClass) {
            super(interfaceClass);
            this.interfaceClass = interfaceClass;
        }

        public DebugTypeKind typeKind() {
            return DebugTypeKind.INTERFACE;
        }
    }

    private class NativeImageDebugArrayTypeInfo extends NativeImageDebugTypeInfo implements DebugArrayTypeInfo {
        HostedArrayClass arrayClass;

        NativeImageDebugArrayTypeInfo(HostedArrayClass arrayClass) {
            super(arrayClass);
            this.arrayClass = arrayClass;
        }

        public DebugTypeKind typeKind() {
            return DebugTypeKind.ARRAY;
        }

        @Override
        public int headerSize() {
            return OBJECTLAYOUT.getArrayBaseOffset(arrayClass.getComponentType().getStorageKind());
        }

        @Override
        public int lengthOffset() {
            return OBJECTLAYOUT.getArrayLengthOffset();
        }

        @Override
        public String elementType() {
            HostedType elementType = arrayClass.getComponentType();
            return toJavaName(elementType);
        }
    }

    private class NativeImageDebugPrimitiveTypeInfo extends NativeImageDebugTypeInfo implements DebugPrimitiveTypeInfo {
        private final HostedPrimitiveType primitiveType;

        NativeImageDebugPrimitiveTypeInfo(HostedPrimitiveType primitiveType) {
            super(primitiveType);
            this.primitiveType = primitiveType;
        }

        public DebugTypeKind typeKind() {
            return DebugTypeKind.PRIMITIVE;
        }

        @Override
        public int bitCount() {
            JavaKind javaKind = primitiveType.getStorageKind();
            return (javaKind == JavaKind.Void ? 0 : javaKind.getBitCount());
        }

        @Override
        public char typeChar() {
            return primitiveType.getStorageKind().getTypeChar();
        }

        @Override
        public int flags() {
            char typeChar = primitiveType.getStorageKind().getTypeChar();
            switch (typeChar) {
                case 'B':
                case 'S':
                case 'I':
                case 'J': {
                    return FLAG_NUMERIC | FLAG_INTEGRAL | FLAG_SIGNED;
                }
                case 'C': {
                    return FLAG_NUMERIC | FLAG_INTEGRAL;
                }
                case 'F':
                case 'D': {
                    return FLAG_NUMERIC;
                }
                default: {
                    assert typeChar == 'V' || typeChar == 'Z';
                    return 0;
                }
            }
        }
    }

    private NativeImageDebugTypeInfo createDebugTypeInfo(HostedType hostedType) {
        if (hostedType.isEnum()) {
            return new NativeImageDebugEnumTypeInfo((HostedInstanceClass) hostedType);
        } else if (hostedType.isInstanceClass()) {
            return new NativeImageDebugInstanceTypeInfo((HostedInstanceClass) hostedType);
        } else if (hostedType.isInterface()) {
            return new NativeImageDebugInterfaceTypeInfo((HostedInterface) hostedType);
        } else if (hostedType.isArray()) {
            return new NativeImageDebugArrayTypeInfo((HostedArrayClass) hostedType);
        } else if (hostedType.isPrimitive()) {
            return new NativeImageDebugPrimitiveTypeInfo((HostedPrimitiveType) hostedType);
        } else {
            throw new RuntimeException("Unknown type kind " + hostedType.getName());
        }
    }

    /**
     * Implementation of the DebugCodeInfo API interface that allows code info to be passed to an
     * ObjectFile when generation of debug info is enabled.
     */
    private class NativeImageDebugCodeInfo extends NativeImageDebugFileInfo implements DebugCodeInfo {
        private final HostedMethod hostedMethod;
        private final CompilationResult compilation;

        @SuppressWarnings("try")
        NativeImageDebugCodeInfo(HostedMethod method, CompilationResult compilation) {
            super(method);
            this.hostedMethod = method;
            this.compilation = compilation;
        }

        @SuppressWarnings("try")
        @Override
        public void debugContext(Consumer<DebugContext> action) {
            try (DebugContext.Scope s = debugContext.scope("DebugCodeInfo", hostedMethod)) {
                action.accept(debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @Override
        public String className() {
            return getJavaType(hostedMethod, true).toJavaName();
        }

        @Override
        public String methodName() {
            ResolvedJavaMethod targetMethod = hostedMethod.getWrapped().getWrapped();
            if (targetMethod instanceof SubstitutionMethod) {
                targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
            } else if (targetMethod instanceof CustomSubstitutionMethod) {
                targetMethod = ((CustomSubstitutionMethod) targetMethod).getOriginal();
            }
            String name = targetMethod.getName();
            if (name.equals("<init>")) {
                name = targetMethod.format("%h");
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            }
            return name;
        }

        @Override
        public String symbolNameForMethod() {
            return NativeBootImage.localSymbolNameForMethod(hostedMethod);
        }

        public String paramSignature() {
            return hostedMethod.format("%P");
        }

        @Override
        public String returnTypeName() {
            return hostedMethod.format("%R");
        }

        @Override
        public int addressLo() {
            return hostedMethod.getCodeAddressOffset();
        }

        @Override
        public int addressHi() {
            return hostedMethod.getCodeAddressOffset() + compilation.getTargetCodeSize();
        }

        @Override
        public int line() {
            LineNumberTable lineNumberTable = hostedMethod.getLineNumberTable();
            if (lineNumberTable != null) {
                return lineNumberTable.getLineNumber(0);
            }
            return -1;
        }

        @Override
        public Stream<DebugLineInfo> lineInfoProvider() {
            if (fileName().length() == 0) {
                return Stream.empty();
            }
            return compilation.getSourceMappings().stream().map(sourceMapping -> new NativeImageDebugLineInfo(sourceMapping));
        }

        @Override
        public int getFrameSize() {
            return compilation.getTotalFrameSize();
        }

        @Override
        public List<DebugFrameSizeChange> getFrameSizeChanges() {
            List<DebugFrameSizeChange> frameSizeChanges = new LinkedList<>();
            for (CompilationResult.CodeMark mark : compilation.getMarks()) {
                // we only need to observe stack increment or decrement points
                if (mark.id.equals(SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP)) {
                    NativeImageDebugFrameSizeChange sizeChange = new NativeImageDebugFrameSizeChange(mark.pcOffset, EXTEND);
                    frameSizeChanges.add(sizeChange);
                    // } else if (mark.id.equals("PROLOGUE_END")) {
                    // can ignore these
                    // } else if (mark.id.equals("EPILOGUE_START")) {
                    // can ignore these
                } else if (mark.id.equals(SubstrateBackend.SubstrateMarkId.EPILOGUE_INCD_RSP)) {
                    NativeImageDebugFrameSizeChange sizeChange = new NativeImageDebugFrameSizeChange(mark.pcOffset, CONTRACT);
                    frameSizeChanges.add(sizeChange);
                } else if (mark.id.equals(SubstrateBackend.SubstrateMarkId.EPILOGUE_END) && mark.pcOffset < compilation.getTargetCodeSize()) {
                    // there is code after this return point so notify stack extend again
                    NativeImageDebugFrameSizeChange sizeChange = new NativeImageDebugFrameSizeChange(mark.pcOffset, EXTEND);
                    frameSizeChanges.add(sizeChange);
                }
            }
            return frameSizeChanges;
        }

        @Override
        public boolean isDeoptTarget() {
            return methodName().endsWith(HostedMethod.METHOD_NAME_DEOPT_SUFFIX);
        }

        public int getModifiers() {
            return hostedMethod.getModifiers();
        }
    }

    /**
     * Implementation of the DebugLineInfo API interface that allows line number info to be passed
     * to an ObjectFile when generation of debug info is enabled.
     */
    private class NativeImageDebugLineInfo implements DebugLineInfo {
        private final int bci;
        private final ResolvedJavaMethod method;
        private final int lo;
        private final int hi;
        private Path cachePath;
        private Path fullFilePath;

        NativeImageDebugLineInfo(SourceMapping sourceMapping) {
            NodeSourcePosition position = sourceMapping.getSourcePosition();
            int posbci = position.getBCI();
            this.bci = (posbci >= 0 ? posbci : 0);
            this.method = position.getMethod();
            this.lo = sourceMapping.getStartOffset();
            this.hi = sourceMapping.getEndOffset();
            this.cachePath = SubstrateOptions.getDebugInfoSourceCacheRoot();
            computeFullFilePath();
        }

        @Override
        public String fileName() {
            if (fullFilePath != null) {
                Path fileName = fullFilePath.getFileName();
                if (fileName != null) {
                    return fileName.toString();
                }
            }
            return null;
        }

        @Override
        public Path filePath() {
            if (fullFilePath != null) {
                return fullFilePath.getParent();
            }
            return null;
        }

        @Override
        public Path cachePath() {
            return cachePath;
        }

        @Override
        public String className() {
            return method.format("%H");
        }

        @Override
        public String methodName() {
            ResolvedJavaMethod targetMethod = method;
            if (targetMethod instanceof HostedMethod) {
                targetMethod = ((HostedMethod) targetMethod).getWrapped();
            }
            if (targetMethod instanceof AnalysisMethod) {
                targetMethod = ((AnalysisMethod) targetMethod).getWrapped();
            }
            if (targetMethod instanceof SubstitutionMethod) {
                targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
            } else if (targetMethod instanceof CustomSubstitutionMethod) {
                targetMethod = ((CustomSubstitutionMethod) targetMethod).getOriginal();
            }
            String name = targetMethod.getName();
            if (name.equals("<init>")) {
                name = targetMethod.format("%h");
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            }
            return name;
        }

        @Override
        public String symbolNameForMethod() {
            return NativeBootImage.localSymbolNameForMethod(method);
        }

        @Override
        public int addressLo() {
            return lo;
        }

        @Override
        public int addressHi() {
            return hi;
        }

        @Override
        public int line() {
            LineNumberTable lineNumberTable = method.getLineNumberTable();
            if (lineNumberTable != null) {
                return lineNumberTable.getLineNumber(bci);
            }
            return -1;
        }

        @SuppressWarnings("try")
        private void computeFullFilePath() {
            ResolvedJavaType declaringClass = method.getDeclaringClass();
            Class<?> clazz = null;
            if (declaringClass instanceof OriginalClassProvider) {
                clazz = ((OriginalClassProvider) declaringClass).getJavaClass();
            }
            /*
             * HostedType wraps an AnalysisType and both HostedType and AnalysisType punt calls to
             * getSourceFilename to the wrapped class so for consistency we need to do the path
             * lookup relative to the doubly unwrapped HostedType or singly unwrapped AnalysisType.
             */
            if (declaringClass instanceof HostedType) {
                declaringClass = ((HostedType) declaringClass).getWrapped();
            }
            if (declaringClass instanceof AnalysisType) {
                declaringClass = ((AnalysisType) declaringClass).getWrapped();
            }
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugCodeInfo", declaringClass)) {
                fullFilePath = sourceManager.findAndCacheSource(declaringClass, clazz, debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

    }

    /**
     * Implementation of the DebugFrameSizeChange API interface that allows stack frame size change
     * info to be passed to an ObjectFile when generation of debug info is enabled.
     */
    private class NativeImageDebugFrameSizeChange implements DebugFrameSizeChange {
        private int offset;
        private Type type;

        NativeImageDebugFrameSizeChange(int offset, Type type) {
            this.offset = offset;
            this.type = type;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public Type getType() {
            return type;
        }
    }

    private class NativeImageDebugDataInfo implements DebugDataInfo {
        HostedClass hostedClass;
        ImageHeapPartition partition;
        long offset;
        long address;
        long size;
        ResolvedJavaType javaType;
        Class<?> clazz;
        String typeName;
        String provenance;

        @SuppressWarnings("try")
        @Override
        public void debugContext(Consumer<DebugContext> action) {
            try (DebugContext.Scope s = debugContext.scope("DebugCodeInfo", provenance)) {
                action.accept(debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        NativeImageDebugDataInfo(ObjectInfo objectInfo) {
            hostedClass = objectInfo.getClazz();
            partition = objectInfo.getPartition();
            offset = objectInfo.getOffset();
            address = objectInfo.getAddress();
            size = objectInfo.getSize();
            provenance = objectInfo.toString();
            /*
             * HostedType wraps an AnalysisType and both HostedType and AnalysisType punt calls to
             * getSourceFilename to the wrapped class so for consistency we need to do the type name
             * and path lookup relative to the doubly unwrapped HostedType.
             */
            javaType = hostedClass.getWrapped().getWrappedWithoutResolve();
            if (hostedClass instanceof OriginalClassProvider) {
                clazz = ((OriginalClassProvider) hostedClass).getJavaClass();
            } else {
                clazz = null;
            }
            typeName = hostedClass.toJavaName();
        }

        // accessors
        public String getProvenance() {
            return provenance;
        }

        public String getTypeName() {
            return typeName;
        }

        public String getPartition() {
            return partition.getName() + "{" + partition.getSize() + "}@" + partition.getStartOffset();
        }

        public long getOffset() {
            return offset;
        }

        public long getAddress() {
            return address;
        }

        public long getSize() {
            return size;
        }
    }

    private boolean acceptObjectInfo(ObjectInfo objectInfo) {
        // this rejects filler partition objects
        return (objectInfo.getPartition().getStartOffset() > 0);
    }

    private DebugDataInfo createDebugDataInfo(ObjectInfo objectInfo) {
        return new NativeImageDebugDataInfo(objectInfo);
    }
}
