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

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CompilationResultFrameTree.Builder;
import com.oracle.svm.core.code.CompilationResultFrameTree.CallNode;
import com.oracle.svm.core.code.CompilationResultFrameTree.FrameNode;
import com.oracle.svm.core.code.CompilationResultFrameTree.Visitor;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedInterface;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.InjectedFieldsType;
import com.oracle.svm.hosted.substitute.SubstitutionField;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.ImageSingletons;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange.Type.CONTRACT;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange.Type.EXTEND;

/**
 * Implementation of the DebugInfoProvider API interface that allows type, code and heap data info
 * to be passed to an ObjectFile when generation of debug info is enabled.
 */
class NativeImageDebugInfoProvider implements DebugInfoProvider {
    private final DebugContext debugContext;
    private final NativeImageCodeCache codeCache;
    @SuppressWarnings("unused") private final NativeImageHeap heap;
    boolean useHeapBase;
    int compressShift;
    int tagsMask;
    int referenceSize;
    int pointerSize;
    int referenceAlignment;
    int primitiveStartOffset;
    int referenceStartOffset;
    private final Set<HostedMethod> allOverrides;

    NativeImageDebugInfoProvider(DebugContext debugContext, NativeImageCodeCache codeCache, NativeImageHeap heap) {
        super();
        this.debugContext = debugContext;
        this.codeCache = codeCache;
        this.heap = heap;
        ObjectHeader objectHeader = Heap.getHeap().getObjectHeader();
        ObjectInfo primitiveFields = heap.getObjectInfo(StaticFieldsSupport.getStaticPrimitiveFields());
        ObjectInfo objectFields = heap.getObjectInfo(StaticFieldsSupport.getStaticObjectFields());
        this.tagsMask = objectHeader.getReservedBitsMask();
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
            this.useHeapBase = compressEncoding.hasBase();
            this.compressShift = (compressEncoding.hasShift() ? compressEncoding.getShift() : 0);
        } else {
            this.useHeapBase = false;
            this.compressShift = 0;
        }
        this.referenceSize = getObjectLayout().getReferenceSize();
        this.pointerSize = ConfigurationValues.getTarget().wordSize;
        this.referenceAlignment = getObjectLayout().getAlignment();
        /* Offsets need to be adjusted relative to the heap base plus partition-specific offset. */
        primitiveStartOffset = (int) primitiveFields.getAddress();
        referenceStartOffset = (int) objectFields.getAddress();
        /* Calculate the set of all HostedMethods that are overrides. */
        allOverrides = heap.getUniverse().getMethods().stream()
                        .filter(HostedMethod::hasVTableIndex)
                        .flatMap(m -> Arrays.stream(m.getImplementations())
                                        .filter(Predicate.not(m::equals)))
                        .collect(Collectors.toSet());
    }

    @Override
    public boolean useHeapBase() {
        return useHeapBase;
    }

    @Override
    public int oopCompressShift() {
        return compressShift;
    }

    @Override
    public int oopReferenceSize() {
        return referenceSize;
    }

    @Override
    public int pointerSize() {
        return pointerSize;
    }

    @Override
    public int oopAlignment() {
        return referenceAlignment;
    }

    @Override
    public int oopTagsMask() {
        return tagsMask;
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

    static ObjectLayout getObjectLayout() {
        return ConfigurationValues.getObjectLayout();
    }

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
    protected static ResolvedJavaType getDeclaringClass(HostedType hostedType, boolean wantOriginal) {
        // unwrap to the underlying class eihter the original or target class
        if (wantOriginal) {
            return getOriginal(hostedType);
        }
        // we want any substituted target if there is one. directly unwrapping will
        // do what we want.
        return hostedType.getWrapped().getWrapped();
    }

    protected static ResolvedJavaType getDeclaringClass(HostedMethod hostedMethod, boolean wantOriginal) {
        if (wantOriginal) {
            return getOriginal(hostedMethod.getDeclaringClass());
        }
        // we want a substituted target if there is one. if there is a substitution at the end of
        // the method chain fetch the annotated target class
        ResolvedJavaMethod javaMethod = hostedMethod.getWrapped().getWrapped();
        if (javaMethod instanceof SubstitutionMethod) {
            SubstitutionMethod substitutionMethod = (SubstitutionMethod) javaMethod;
            return substitutionMethod.getAnnotated().getDeclaringClass();
        }
        return javaMethod.getDeclaringClass();
    }

    @SuppressWarnings("unused")
    protected static ResolvedJavaType getDeclaringClass(HostedField hostedField, boolean wantOriginal) {
        /* for now fields are always reported as belonging to the original class */
        return getOriginal(hostedField.getDeclaringClass());
    }

    private static ResolvedJavaType getOriginal(HostedType hostedType) {
        /* partially unwrap then traverse through substitutions to the original */
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
        return javaType;
    }

    private static int getOriginalModifiers(HostedMethod hostedMethod) {
        ResolvedJavaMethod targetMethod = hostedMethod.getWrapped().getWrapped();
        if (targetMethod instanceof SubstitutionMethod) {
            targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
        } else if (targetMethod instanceof CustomSubstitutionMethod) {
            targetMethod = ((CustomSubstitutionMethod) targetMethod).getOriginal();
        }
        return targetMethod.getModifiers();
    }

    private static String toJavaName(JavaType javaType) {
        if (javaType instanceof HostedType) {
            return getDeclaringClass((HostedType) javaType, true).toJavaName();
        }
        return javaType.toJavaName();
    }

    private final Path cachePath = SubstrateOptions.getDebugInfoSourceCacheRoot();

    private abstract class NativeImageDebugFileInfo implements DebugFileInfo {
        private Path fullFilePath;

        @SuppressWarnings("try")
        NativeImageDebugFileInfo(HostedType hostedType) {
            ResolvedJavaType javaType = getDeclaringClass(hostedType, false);
            Class<?> clazz = hostedType.getJavaClass();
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
                fullFilePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @SuppressWarnings("try")
        NativeImageDebugFileInfo(HostedMethod hostedMethod) {
            ResolvedJavaType javaType = getDeclaringClass(hostedMethod, false);
            HostedType hostedType = hostedMethod.getDeclaringClass();
            Class<?> clazz = hostedType.getJavaClass();
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
                fullFilePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @SuppressWarnings("try")
        NativeImageDebugFileInfo(HostedField hostedField) {
            ResolvedJavaType javaType = getDeclaringClass(hostedField, false);
            HostedType hostedType = hostedField.getDeclaringClass();
            Class<?> clazz = hostedType.getJavaClass();
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

        public String toJavaName(@SuppressWarnings("hiding") HostedType hostedType) {
            return getDeclaringClass(hostedType, true).toJavaName();
        }

        @Override
        public String typeName() {
            return toJavaName(hostedType);
        }

        @Override
        public int size() {
            if (hostedType instanceof HostedInstanceClass) {
                /* We know the actual instance size in bytes. */
                return ((HostedInstanceClass) hostedType).getInstanceSize();
            } else if (hostedType instanceof HostedArrayClass) {
                /* Use the size of header common to all arrays of this type. */
                return getObjectLayout().getArrayBaseOffset(hostedType.getComponentType().getStorageKind());
            } else if (hostedType instanceof HostedInterface) {
                /* Use the size of the header common to all implementors. */
                return getObjectLayout().getFirstFieldOffset();
            } else {
                /* Use the number of bytes needed needed to store the value. */
                assert hostedType instanceof HostedPrimitiveType;
                JavaKind javaKind = hostedType.getStorageKind();
                return (javaKind == JavaKind.Void ? 0 : javaKind.getByteCount());
            }
        }
    }

    private class NativeImageHeaderTypeInfo implements DebugHeaderTypeInfo {
        String typeName;
        int size;
        List<DebugFieldInfo> fieldInfos;

        NativeImageHeaderTypeInfo(String typeName, int size) {
            this.typeName = typeName;
            this.size = size;
            this.fieldInfos = new LinkedList<>();
        }

        void addField(String name, String valueType, int offset, @SuppressWarnings("hiding") int size) {
            NativeImageDebugHeaderFieldInfo fieldinfo = new NativeImageDebugHeaderFieldInfo(name, valueType, offset, size);
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
    }

    private class NativeImageDebugHeaderFieldInfo implements DebugFieldInfo {
        private final String name;
        private final String valueType;
        private final int offset;
        private final int size;
        private final int modifiers;

        NativeImageDebugHeaderFieldInfo(String name, String valueType, int offset, int size) {
            this.name = name;
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

    private Stream<DebugTypeInfo> computeHeaderTypeInfo() {
        List<DebugTypeInfo> infos = new LinkedList<>();
        int hubOffset = getObjectLayout().getHubOffset();
        int hubFieldSize = referenceSize;
        String hubTypeName = "java.lang.Class";
        int idHashOffset = getObjectLayout().getIdentityHashCodeOffset();
        int idHashSize = getObjectLayout().sizeInBytes(JavaKind.Int);
        int objHeaderSize = getObjectLayout().getMinimumInstanceObjectSize();

        /* We need array headers for all Java kinds */

        NativeImageHeaderTypeInfo objHeader = new NativeImageHeaderTypeInfo("_objhdr", objHeaderSize);
        objHeader.addField("hub", hubTypeName, hubOffset, hubFieldSize);
        if (idHashOffset > 0) {
            objHeader.addField("idHash", "int", idHashOffset, idHashSize);
        }
        infos.add(objHeader);

        return infos.stream();
    }

    private class NativeImageDebugEnumTypeInfo extends NativeImageDebugInstanceTypeInfo implements DebugEnumTypeInfo {

        NativeImageDebugEnumTypeInfo(HostedInstanceClass enumClass) {
            super(enumClass);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.ENUM;
        }
    }

    private class NativeImageDebugInstanceTypeInfo extends NativeImageDebugTypeInfo implements DebugInstanceTypeInfo {
        NativeImageDebugInstanceTypeInfo(HostedType hostedType) {
            super(hostedType);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.INSTANCE;
        }

        @Override
        public int headerSize() {
            return getObjectLayout().getFirstFieldOffset();
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
                return getDeclaringClass(superClass, true).toJavaName();
            }
            return null;
        }

        @Override
        public Stream<String> interfaces() {
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
            public String valueType() {
                HostedType valueType = field.getType();
                return toJavaName(valueType);
            }

            @Override
            public int offset() {
                int offset = field.getLocation();
                /*
                 * For static fields we need to add in the appropriate partition base but only if we
                 * have a real offset
                 */
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
                return getObjectLayout().sizeInBytes(field.getType().getStorageKind());
            }

            @Override
            public int modifiers() {
                ResolvedJavaField targetField = field.wrapped.wrapped;
                if (targetField instanceof SubstitutionField) {
                    targetField = ((SubstitutionField) targetField).getOriginal();
                }
                return targetField.getModifiers();
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
                    name = getDeclaringClass(hostedMethod, true).toJavaName();
                    if (name.indexOf('.') >= 0) {
                        name = name.substring(name.lastIndexOf('.') + 1);
                    }
                    if (name.indexOf('$') >= 0) {
                        name = name.substring(name.lastIndexOf('$') + 1);
                    }
                }
                return name;
            }

            @Override
            public String valueType() {
                return toJavaName((HostedType) hostedMethod.getSignature().getReturnType(null));
            }

            @Override
            public List<String> paramTypes() {
                Signature signature = hostedMethod.getSignature();
                int parameterCount = signature.getParameterCount(false);
                List<String> paramTypes = new ArrayList<>(parameterCount);
                for (int i = 0; i < parameterCount; i++) {
                    paramTypes.add(toJavaName((HostedType) signature.getParameterType(i, null)));
                }
                return paramTypes;
            }

            @Override
            public List<String> paramNames() {
                /* Can only provide blank names for now. */
                Signature signature = hostedMethod.getSignature();
                int parameterCount = signature.getParameterCount(false);
                List<String> paramNames = new ArrayList<>(parameterCount);
                for (int i = 0; i < parameterCount; i++) {
                    paramNames.add("");
                }
                return paramNames;
            }

            @Override
            public String symbolNameForMethod() {
                return NativeImage.localSymbolNameForMethod(hostedMethod);
            }

            @Override
            public boolean isDeoptTarget() {
                return hostedMethod.isDeoptTarget();
            }

            @Override
            public boolean isConstructor() {
                return hostedMethod.isConstructor();
            }

            @Override
            public int modifiers() {
                return getOriginalModifiers(hostedMethod);
            }

            @Override
            public boolean isVirtual() {
                return hostedMethod.hasVTableIndex();
            }

            @Override
            public int vtableOffset() {
                /*
                 * TODO - provide correct offset, not index. In Graal, the vtable is appended after
                 * the dynamicHub object, so can't just multiply by sizeof(pointer).
                 */
                return hostedMethod.hasVTableIndex() ? hostedMethod.getVTableIndex() : -1;
            }

            /**
             * Returns true if this is an override virtual method. Used in Windows CodeView output.
             *
             * @return true if this is a virtual method and overrides an existing method.
             */
            @Override
            public boolean isOverride() {
                return allOverrides.contains(hostedMethod);
            }
        }
    }

    private class NativeImageDebugInterfaceTypeInfo extends NativeImageDebugInstanceTypeInfo implements DebugInterfaceTypeInfo {

        NativeImageDebugInterfaceTypeInfo(HostedInterface interfaceClass) {
            super(interfaceClass);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.INTERFACE;
        }
    }

    private class NativeImageDebugArrayTypeInfo extends NativeImageDebugTypeInfo implements DebugArrayTypeInfo {
        HostedArrayClass arrayClass;
        List<DebugFieldInfo> fieldInfos;

        NativeImageDebugArrayTypeInfo(HostedArrayClass arrayClass) {
            super(arrayClass);
            this.arrayClass = arrayClass;
            this.fieldInfos = new LinkedList<>();
            JavaKind arrayKind = arrayClass.getBaseType().getJavaKind();
            int headerSize = getObjectLayout().getArrayBaseOffset(arrayKind);
            int arrayLengthOffset = getObjectLayout().getArrayLengthOffset();
            int arrayLengthSize = getObjectLayout().sizeInBytes(JavaKind.Int);
            assert arrayLengthOffset + arrayLengthSize <= headerSize;

            addField("len", "int", arrayLengthOffset, arrayLengthSize);
        }

        void addField(String name, String valueType, int offset, @SuppressWarnings("hiding") int size) {
            NativeImageDebugHeaderFieldInfo fieldinfo = new NativeImageDebugHeaderFieldInfo(name, valueType, offset, size);
            fieldInfos.add(fieldinfo);
        }

        @Override
        public DebugTypeKind typeKind() {
            return DebugTypeKind.ARRAY;
        }

        @Override
        public int baseSize() {
            return getObjectLayout().getArrayBaseOffset(arrayClass.getComponentType().getStorageKind());
        }

        @Override
        public int lengthOffset() {
            return getObjectLayout().getArrayLengthOffset();
        }

        @Override
        public String elementType() {
            HostedType elementType = arrayClass.getComponentType();
            return toJavaName(elementType);
        }

        @Override
        public Stream<DebugFieldInfo> fieldInfoProvider() {
            return fieldInfos.stream();
        }
    }

    private class NativeImageDebugPrimitiveTypeInfo extends NativeImageDebugTypeInfo implements DebugPrimitiveTypeInfo {
        private final HostedPrimitiveType primitiveType;

        NativeImageDebugPrimitiveTypeInfo(HostedPrimitiveType primitiveType) {
            super(primitiveType);
            this.primitiveType = primitiveType;
        }

        @Override
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
            return new NativeImageDebugInstanceTypeInfo(hostedType);
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
        public ResolvedJavaType ownerType() {
            return getDeclaringClass(hostedMethod, true);
        }

        @Override
        public String name() {
            ResolvedJavaMethod targetMethod = hostedMethod.getWrapped().getWrapped();
            if (targetMethod instanceof SubstitutionMethod) {
                targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
            } else if (targetMethod instanceof CustomSubstitutionMethod) {
                targetMethod = ((CustomSubstitutionMethod) targetMethod).getOriginal();
            }
            String name = targetMethod.getName();
            if (name.equals("<init>")) {
                name = getDeclaringClass(hostedMethod, true).toJavaName();
                if (name.indexOf('.') >= 0) {
                    name = name.substring(name.lastIndexOf('.') + 1);
                }
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            }
            return name;
        }

        @Override
        public String symbolNameForMethod() {
            return NativeImage.localSymbolNameForMethod(hostedMethod);
        }

        @Override
        public String valueType() {
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
        public Stream<DebugLocationInfo> locationInfoProvider() {
            // can we still provide locals if we have no file name?
            if (fileName().length() == 0) {
                return Stream.empty();
            }
            final CallNode root = new Builder(debugContext, compilation.getTargetCodeSize(), true).build(compilation);
            if (root == null) {
                return Stream.empty();
            }
            final List<DebugLocationInfo> locationInfos = new ArrayList<>();
            final ResolvedJavaMethod rootMethod = root.frame.getMethod();
            if (SubstrateOptions.OmitInlinedMethodDebugLineInfo.getValue()) {
                root.visitChildren(new Visitor() {
                    @Override
                    public void apply(FrameNode node, Object... args) {
                        if (node.frame.getCaller() == null && node.frame.getMethod() == rootMethod) {
                            locationInfos.add(new NativeImageDebugLocationInfo(node));
                        }
                    }
                });
            } else {
                root.visitChildren(new Visitor() {
                    @Override
                    public void apply(FrameNode node, Object... args) {
                        NativeImageDebugLocationInfo callerInfo = (args.length == 0 ? null : (NativeImageDebugLocationInfo) args[0]);
                        NativeImageDebugLocationInfo locationInfo =  new NativeImageDebugLocationInfo(node, callerInfo);
                        locationInfos.add(locationInfo);
                        node.visitChildren(this, locationInfo);
                    }
                });
            }
            return locationInfos.stream();
        }

        @Override
        public int getFrameSize() {
            return compilation.getTotalFrameSize();
        }

        @Override
        public List<DebugFrameSizeChange> getFrameSizeChanges() {
            List<DebugFrameSizeChange> frameSizeChanges = new LinkedList<>();
            for (CompilationResult.CodeMark mark : compilation.getMarks()) {
                /* We only need to observe stack increment or decrement points. */
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
                    /* There is code after this return point so notify a stack extend again. */
                    NativeImageDebugFrameSizeChange sizeChange = new NativeImageDebugFrameSizeChange(mark.pcOffset, EXTEND);
                    frameSizeChanges.add(sizeChange);
                }
            }
            return frameSizeChanges;
        }

        @Override
        public boolean isDeoptTarget() {
            return hostedMethod.isDeoptTarget();
        }

        @Override
        public List<String> paramTypes() {
            Signature signature = hostedMethod.getSignature();
            int parameterCount = signature.getParameterCount(false);
            List<String> paramTypes = new ArrayList<>(parameterCount);
            for (int i = 0; i < parameterCount; i++) {
                JavaType parameterType = signature.getParameterType(i, null);
                paramTypes.add(toJavaName(parameterType));
            }
            return paramTypes;
        }

        @Override
        public List<String> paramNames() {
            /* Can only provide blank names for now. */
            Signature signature = hostedMethod.getSignature();
            int parameterCount = signature.getParameterCount(false);
            List<String> paramNames = new ArrayList<>(parameterCount);
            for (int i = 0; i < parameterCount; i++) {
                paramNames.add("");
            }
            return paramNames;
        }

        @Override
        public int modifiers() {
            return getOriginalModifiers(hostedMethod);
        }

        @Override
        public boolean isConstructor() {
            return hostedMethod.isConstructor();
        }

        @Override
        public boolean isVirtual() {
            return hostedMethod.hasVTableIndex();
        }

        @Override
        public int vtableOffset() {
            /*
             * TODO - provide correct offset, not index. In Graal, the vtable is appended after the
             * dynamicHub object.
             */
            return hostedMethod.hasVTableIndex() ? hostedMethod.getVTableIndex() : -1;
        }

        /**
         * Returns true if this is an override virtual method. Used in Windows CodeView output.
         *
         * @return true if this is a virtual method and overrides an existing method.
         */
        @Override
        public boolean isOverride() {
            return allOverrides.contains(hostedMethod);
        }
    }

    /**
     * Implementation of the DebugLocationInfo API interface that allows line number and local var
     * info to be passed to an ObjectFile when generation of debug info is enabled.
     */
    private class NativeImageDebugLocationInfo implements DebugLocationInfo {
        private final int bci;
        private final ResolvedJavaMethod method;
        private final int lo;
        private final int hi;
        private Path cachePath;
        private Path fullFilePath;
        private DebugLocationInfo callersLocationInfo;
        private List<DebugLocalInfo> localInfoList;

        NativeImageDebugLocationInfo(FrameNode frameNode) {
            this(frameNode, null);
        }

        NativeImageDebugLocationInfo(FrameNode frameNode, NativeImageDebugLocationInfo callersLocationInfo) {
            BytecodePosition bcpos = frameNode.frame;
            this.bci = bcpos.getBCI();
            this.method = bcpos.getMethod();
            this.lo = frameNode.getStartPos();
            this.hi = frameNode.getEndPos() + 1;
            this.cachePath = SubstrateOptions.getDebugInfoSourceCacheRoot();
            this.callersLocationInfo = callersLocationInfo;
            computeFullFilePath();
            this.localInfoList = initLocalInfoList(bcpos);
        }

        private List<DebugLocalInfo> initLocalInfoList(BytecodePosition bcpos) {
            if (!(bcpos instanceof BytecodeFrame)) {
                return null;
            }

            BytecodeFrame frame = (BytecodeFrame) bcpos;
            if (frame.numLocals == 0) {
                return null;
            }
            Local[] localsBySlot = getLocalsBySlot();
            if (localsBySlot == null) {
                // TODO perhaps try synthesising locals info here
                return null;
            }
            int count = Integer.min(localsBySlot.length, frame.numLocals);
            ArrayList<DebugLocalInfo> localInfos = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                JavaValue value = frame.getLocalValue(i);
                JavaKind kind = frame.getLocalValueKind(i);
                Local l = localsBySlot[i];
                String name;
                ResolvedJavaType type;
                if (l != null) {
                    name = l.getName();
                    type = l.getType().resolve(method.getDeclaringClass());
                } else {
                    name = "_" + i;
                    type = null;
                }
                localInfos.add(new NativeImageDebugLocalInfo(name, value, kind, type));
            }
            return localInfos;
        }

        private Local[] getLocalsBySlot() {
            LocalVariableTable lvt = method.getLocalVariableTable();
            Local[] nonEmptySortedLocals = null;
            if (lvt != null) {
                Local[] locals = lvt.getLocals();
                if (locals != null && locals.length > 0) {
                    nonEmptySortedLocals = Arrays.copyOf(locals, locals.length);
                    Arrays.sort(nonEmptySortedLocals, (Local l1, Local l2) -> l1.getSlot() - l2.getSlot());
                }
            }
            return nonEmptySortedLocals;
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
        public ResolvedJavaType ownerType() {
            ResolvedJavaType result;
            if (method instanceof HostedMethod) {
                result = getDeclaringClass((HostedMethod) method, true);
            } else {
                result = method.getDeclaringClass();
            }
            if (result instanceof WrappedJavaType) {
                result = ((WrappedJavaType) result).getWrapped();
            }
            return result;
        }

        @Override
        public String name() {
            ResolvedJavaMethod targetMethod = method;
            while (targetMethod instanceof WrappedJavaMethod) {
                targetMethod = ((WrappedJavaMethod) targetMethod).getWrapped();
            }
            if (targetMethod instanceof SubstitutionMethod) {
                targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
            } else if (targetMethod instanceof CustomSubstitutionMethod) {
                targetMethod = ((CustomSubstitutionMethod) targetMethod).getOriginal();
            }
            String name = targetMethod.getName();
            if (name.equals("<init>")) {
                if (method instanceof HostedMethod) {
                    name = getDeclaringClass((HostedMethod) method, true).toJavaName();
                    if (name.indexOf('.') >= 0) {
                        name = name.substring(name.lastIndexOf('.') + 1);
                    }
                    if (name.indexOf('$') >= 0) {
                        name = name.substring(name.lastIndexOf('$') + 1);
                    }
                } else {
                    name = targetMethod.format("%h");
                    if (name.indexOf('$') >= 0) {
                        name = name.substring(name.lastIndexOf('$') + 1);
                    }
                }
            }
            return name;
        }

        @Override
        public String valueType() {
            return method.format("%R");
        }

        @Override
        public String symbolNameForMethod() {
            return NativeImage.localSymbolNameForMethod(method);
        }

        @Override
        public boolean isDeoptTarget() {
            if (method instanceof HostedMethod) {
                return ((HostedMethod) method).isDeoptTarget();
            }
            return name().endsWith(HostedMethod.METHOD_NAME_DEOPT_SUFFIX);
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
            if (lineNumberTable != null && bci >= 0) {
                return lineNumberTable.getLineNumber(bci);
            }
            return -1;
        }

        @Override
        public List<String> paramTypes() {
            Signature signature = method.getSignature();
            int parameterCount = signature.getParameterCount(false);
            List<String> paramTypes = new ArrayList<>(parameterCount);
            for (int i = 0; i < parameterCount; i++) {
                JavaType parameterType = signature.getParameterType(i, null);
                paramTypes.add(toJavaName(parameterType));
            }
            return paramTypes;
        }

        @Override
        public List<String> paramNames() {
            /* Can only provide blank names for now. */
            Signature signature = method.getSignature();
            int parameterCount = signature.getParameterCount(false);
            List<String> paramNames = new ArrayList<>(parameterCount);
            for (int i = 0; i < parameterCount; i++) {
                paramNames.add("");
            }
            return paramNames;
        }

        @Override
        public int modifiers() {
            return method.getModifiers();
        }

        @Override
        public DebugLocationInfo getCaller() {
            return callersLocationInfo;
        }

        @Override
        public Stream<DebugLocalInfo> localsProvider() {
            if (localInfoList != null) {
                return localInfoList.stream();
            } else {
                return Stream.empty();
            }
        }

        @SuppressWarnings("try")
        private void computeFullFilePath() {
            ResolvedJavaType declaringClass;
            // if we have a HostedMethod then deal with substitutions
            if (method instanceof HostedMethod) {
                declaringClass = getDeclaringClass((HostedMethod) method, false);
            } else {
                declaringClass = method.getDeclaringClass();
            }
            Class<?> clazz = null;
            if (declaringClass instanceof OriginalClassProvider) {
                clazz = ((OriginalClassProvider) declaringClass).getJavaClass();
            }
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugCodeInfo", declaringClass)) {
                fullFilePath = sourceManager.findAndCacheSource(declaringClass, clazz, debugContext);
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @Override
        public boolean isConstructor() {
            return method.isConstructor();
        }

        @Override
        public boolean isVirtual() {
            return method instanceof HostedMethod && ((HostedMethod) method).hasVTableIndex();
        }

        @Override
        public boolean isOverride() {
            return method instanceof HostedMethod && allOverrides.contains(method);
        }

        @Override
        public int vtableOffset() {
            /* TODO - convert index to offset (+ sizeof DynamicHub) */
            return isVirtual() ? ((HostedMethod) method).getVTableIndex() : -1;
        }
    }

    public class NativeImageDebugLocalInfo implements DebugLocalInfo {
        private final String name;
        private ResolvedJavaType type;
        private final JavaValue value;
        private final JavaKind kind;
        private LocalKind localKind;

        NativeImageDebugLocalInfo(String name, JavaValue value, JavaKind kind, ResolvedJavaType type) {
            this.name = name;
            this.value = value;
            this.kind = kind;
            this.type = type;
            if (value instanceof RegisterValue) {
                this.localKind = LocalKind.REGISTER;
            } else if (value instanceof StackSlot) {
                this.localKind = LocalKind.STACKSLOT;
            } else if (value instanceof Constant) {
                this.localKind = LocalKind.CONSTANT;
            } else {
                this.localKind = LocalKind.UNDEFINED;
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String typeName() {
            if (type != null) {
                return toJavaName(type);
            }
            if (kind == JavaKind.Object) {
                return "java.lang.Object";
            } else {
                return kind.getJavaName();
            }
        }

        @Override
        public String valueString() {
            switch (localKind) {
                case REGISTER:
                    return "reg[" + regIndex() + "]";
                case STACKSLOT:
                    return "stack[" + stackSlot() + "]";
                case CONSTANT:
                    return (constantValue() != null ? constantValue().toValueString() : "null");
                default:
                    return "-";
            }
        }

        @Override
        public LocalKind localKind() {
            return null;
        }

        @Override
        public int regIndex() {
            return ((RegisterValue) value).getRegister().encoding();
        }

        @Override
        public int stackSlot() {
            return ((StackSlot) value).getRawOffset();
        }

        @Override
        public Constant constantValue() {
            return null;
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
        String typeName;
        String provenance;

        @SuppressWarnings("try")
        @Override
        public void debugContext(Consumer<DebugContext> action) {
            try (DebugContext.Scope s = debugContext.scope("DebugDataInfo", provenance)) {
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
            typeName = hostedClass.toJavaName();
        }

        /* Accessors. */
        @Override
        public String getProvenance() {
            return provenance;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        @Override
        public String getPartition() {
            return partition.getName() + "{" + partition.getSize() + "}@" + partition.getStartOffset();
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public long getAddress() {
            return address;
        }

        @Override
        public long getSize() {
            return size;
        }
    }

    private boolean acceptObjectInfo(ObjectInfo objectInfo) {
        /* This condition rejects filler partition objects. */
        return (objectInfo.getPartition().getStartOffset() > 0);
    }

    private DebugDataInfo createDebugDataInfo(ObjectInfo objectInfo) {
        return new NativeImageDebugDataInfo(objectInfo);
    }
}
