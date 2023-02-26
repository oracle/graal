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

import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange.Type.CONTRACT;
import static com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange.Type.EXTEND;

import java.lang.reflect.Modifier;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.core.UniqueShortNameProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CompilationResultFrameTree.Builder;
import com.oracle.svm.core.code.CompilationResultFrameTree.CallNode;
import com.oracle.svm.core.code.CompilationResultFrameTree.FrameNode;
import com.oracle.svm.core.code.CompilationResultFrameTree.Visitor;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.hosted.annotation.CustomSubstitutionType;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedInterface;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.InjectedFieldsType;
import com.oracle.svm.hosted.substitute.SubstitutionField;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.Value;

/**
 * Implementation of the DebugInfoProvider API interface that allows type, code and heap data info
 * to be passed to an ObjectFile when generation of debug info is enabled.
 */
class NativeImageDebugInfoProvider implements DebugInfoProvider {
    private final DebugContext debugContext;
    private final NativeImageCodeCache codeCache;
    private final NativeImageHeap heap;
    boolean useHeapBase;
    int compressShift;
    int tagsMask;
    int referenceSize;
    int pointerSize;
    int referenceAlignment;
    int primitiveStartOffset;
    int referenceStartOffset;
    private final Set<HostedMethod> allOverrides;
    HostedType wordBaseType;
    HostedType hubType;
    HashMap<JavaKind, HostedType> javaKindToHostedType;

    NativeImageDebugInfoProvider(DebugContext debugContext, NativeImageCodeCache codeCache, NativeImageHeap heap, HostedMetaAccess metaAccess) {
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
        wordBaseType = metaAccess.lookupJavaType(WordBase.class);
        hubType = metaAccess.lookupJavaType(Class.class);
        javaKindToHostedType = initJavaKindToHostedTypes(metaAccess);
    }

    private static HashMap<JavaKind, HostedType> initJavaKindToHostedTypes(HostedMetaAccess metaAccess) {
        HashMap<JavaKind, HostedType> map = new HashMap<>();
        for (JavaKind kind : JavaKind.values()) {
            Class<?> clazz;
            switch (kind) {
                case Illegal:
                    clazz = null;
                    break;
                case Object:
                    clazz = java.lang.Object.class;
                    break;
                default:
                    clazz = kind.toJavaClass();
            }
            HostedType javaType = clazz != null ? metaAccess.lookupJavaType(clazz) : null;
            map.put(kind, javaType);
        }
        return map;
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
        return codeCache.getOrderedCompilations().stream().map(pair -> new NativeImageDebugCodeInfo(pair.getLeft(), pair.getRight()));
    }

    @Override
    public Stream<DebugDataInfo> dataInfoProvider() {
        return heap.getObjects().stream().filter(this::acceptObjectInfo).map(this::createDebugDataInfo);
    }

    static ObjectLayout getObjectLayout() {
        return ConfigurationValues.getObjectLayout();
    }

    /**
     * Return the offset into the initial heap at which the object identified by constant is located
     * or -1 if the object is not present in the initial heap.
     *
     * @param constant must have JavaKind Object and must be non-null.
     *
     * @return the offset into the initial heap at which the object identified by constant is
     *         located or -1 if the object is not present in the initial heap.
     */
    public long objectOffset(JavaConstant constant) {
        assert constant.getJavaKind() == JavaKind.Object && !constant.isNull() : "invalid constant for object offset lookup";
        ObjectInfo objectInfo = heap.getConstantInfo(constant);
        if (objectInfo != null) {
            return objectInfo.getAddress();
        }
        return -1;
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
        ResolvedJavaMethod javaMethod = getAnnotatedOrOriginal(hostedMethod);
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

    private static ResolvedJavaMethod getAnnotatedOrOriginal(HostedMethod hostedMethod) {
        ResolvedJavaMethod javaMethod = hostedMethod.getWrapped().getWrapped();
        // This method is only used when identifying the modifiers or the declaring class
        // of a HostedMethod. Normally the method unwraps to the underlying JVMCI method
        // which is the one that provides bytecode to the compiler as well as, line numbers
        // and local info. If we unwrap to a SubstitutionMethod then we use the annotated
        // method, not the JVMCI method that the annotation refers to since that will be the
        // one providing the bytecode etc used by the compiler. If we unwrap to any other,
        // custom substitution method we simply use it rather than dereferencing to the
        // original. The difference is that the annotated method's bytecode will be used to
        // replace the original and the debugger needs to use it to identify the file and access
        // permissions. A custom substitution may exist alongside the original, as is the case
        // with some uses for reflection. So, we don't want to conflate the custom substituted
        // method and the original. In this latter case the method code will be synthesized without
        // reference to the bytecode of the original. Hence there is no associated file and the
        // permissions need to be determined from the custom substitution method itself.

        if (javaMethod instanceof SubstitutionMethod) {
            SubstitutionMethod substitutionMethod = (SubstitutionMethod) javaMethod;
            javaMethod = substitutionMethod.getAnnotated();
        }
        return javaMethod;
    }

    private static int getOriginalModifiers(HostedMethod hostedMethod) {
        return getAnnotatedOrOriginal(hostedMethod).getModifiers();
    }

    private final Path cachePath = SubstrateOptions.getDebugInfoSourceCacheRoot();

    private HostedType hostedTypeForKind(JavaKind kind) {
        return javaKindToHostedType.get(kind);
    }

    private abstract class NativeImageDebugFileInfo implements DebugFileInfo {
        private final Path fullFilePath;

        @SuppressWarnings("try")
        NativeImageDebugFileInfo(HostedType hostedType) {
            ResolvedJavaType javaType = getDeclaringClass(hostedType, false);
            Class<?> clazz = hostedType.getJavaClass();
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", hostedType)) {
                Path filePath = sourceManager.findAndCacheSource(javaType, clazz, debugContext);
                if (filePath == null && hostedType instanceof HostedInstanceClass) {
                    // conjure up an appropriate, unique file name to keep tools happy
                    // even though we cannot find a corresponding source
                    filePath = fullFilePathFromClassName((HostedInstanceClass) hostedType);
                }
                fullFilePath = filePath;
            } catch (Throwable e) {
                throw debugContext.handle(e);
            }
        }

        @SuppressWarnings("try")
        NativeImageDebugFileInfo(ResolvedJavaMethod method) {
            /*
             * Note that this constructor allows for any ResolvedJavaMethod, not just a
             * HostedMethod, because it needs to provide common behaviour for DebugMethodInfo,
             * DebugCodeInfo and DebugLocationInfo records. The former two are derived from a
             * HostedMethod while the latter may be derived from an arbitrary ResolvedJavaMethod.
             */
            ResolvedJavaType javaType;
            if (method instanceof HostedMethod) {
                javaType = getDeclaringClass((HostedMethod) method, false);
            } else {
                javaType = method.getDeclaringClass();
            }
            Class<?> clazz = null;
            if (javaType instanceof OriginalClassProvider) {
                clazz = ((OriginalClassProvider) javaType).getJavaClass();
            }
            SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
            try (DebugContext.Scope s = debugContext.scope("DebugFileInfo", javaType)) {
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

    private static Path fullFilePathFromClassName(HostedInstanceClass hostedInstanceClass) {
        String[] elements = hostedInstanceClass.toJavaName().split("\\.");
        int count = elements.length;
        String name = elements[count - 1];
        while (name.startsWith("$")) {
            name = name.substring(1);
        }
        if (name.contains("$")) {
            name = name.substring(0, name.indexOf('$'));
        }
        if (name.equals("")) {
            name = "_nofile_";
        }
        elements[count - 1] = name + ".java";
        return FileSystems.getDefault().getPath("", elements);
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
        public ResolvedJavaType idType() {
            // always use the original type for establishing identity
            return getOriginal(hostedType);
        }

        @Override
        public String typeName() {
            return toJavaName(hostedType);
        }

        @Override
        public long classOffset() {
            ObjectInfo objectInfo = heap.getObjectInfo(hostedType.getHub());
            if (objectInfo != null) {
                return objectInfo.getOffset();
            }
            return -1;
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

        void addField(String name, ResolvedJavaType valueType, int offset, @SuppressWarnings("hiding") int size) {
            NativeImageDebugHeaderFieldInfo fieldinfo = new NativeImageDebugHeaderFieldInfo(name, valueType, offset, size);
            fieldInfos.add(fieldinfo);
        }

        @Override
        public ResolvedJavaType idType() {
            // The header type is unique in that it does not have an associated ResolvedJavaType
            return null;
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
        public long classOffset() {
            return -1;
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
        private final ResolvedJavaType valueType;
        private final int offset;
        private final int size;
        private final int modifiers;

        NativeImageDebugHeaderFieldInfo(String name, ResolvedJavaType valueType, int offset, int size) {
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
        public ResolvedJavaType valueType() {
            if (valueType instanceof HostedType) {
                return getOriginal((HostedType) valueType);
            }
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
        int idHashOffset = getObjectLayout().getIdentityHashCodeOffset();
        int idHashSize = getObjectLayout().sizeInBytes(JavaKind.Int);
        int objHeaderSize = getObjectLayout().getMinimumInstanceObjectSize();

        /* We need array headers for all Java kinds */

        NativeImageHeaderTypeInfo objHeader = new NativeImageHeaderTypeInfo("_objhdr", objHeaderSize);
        objHeader.addField("hub", hubType, hubOffset, hubFieldSize);
        if (idHashOffset > 0) {
            objHeader.addField("idHash", javaKindToHostedType.get(JavaKind.Int), idHashOffset, idHashSize);
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
        public String loaderName() {

            return UniqueShortNameProvider.singleton().uniqueShortLoaderName(hostedType.getJavaClass().getClassLoader());
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
        public ResolvedJavaType superClass() {
            HostedClass superClass = hostedType.getSuperclass();
            /*
             * Unwrap the hosted type's super class to the original to provide the correct identity
             * type.
             */
            if (superClass != null) {
                return getOriginal(superClass);
            }
            return null;
        }

        @Override
        public Stream<ResolvedJavaType> interfaces() {
            // map through getOriginal so we can use the result as an id type
            return Arrays.stream(hostedType.getInterfaces()).map(interfaceType -> getOriginal(interfaceType));
        }

        private NativeImageDebugFieldInfo createDebugFieldInfo(HostedField field) {
            return new NativeImageDebugFieldInfo(field);
        }

        private NativeImageDebugFieldInfo createDebugStaticFieldInfo(ResolvedJavaField field) {
            return new NativeImageDebugFieldInfo((HostedField) field);
        }

        private NativeImageDebugMethodInfo createDebugMethodInfo(HostedMethod method) {
            return new NativeImageDebugMethodInfo(method);
        }

        private class NativeImageDebugFieldInfo extends NativeImageDebugFileInfo implements DebugFieldInfo {
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
            public ResolvedJavaType valueType() {
                return getOriginal(field.getType());
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

        private class NativeImageDebugMethodInfo extends NativeImageDebugHostedMethodInfo implements DebugMethodInfo {
            NativeImageDebugMethodInfo(HostedMethod hostedMethod) {
                super(hostedMethod);
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

            addField("len", javaKindToHostedType.get(JavaKind.Int), arrayLengthOffset, arrayLengthSize);
        }

        void addField(String name, ResolvedJavaType valueType, int offset, @SuppressWarnings("hiding") int size) {
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
        public ResolvedJavaType elementType() {
            HostedType elementType = arrayClass.getComponentType();
            return getOriginal(elementType);
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

    protected abstract class NativeImageDebugBaseMethodInfo extends NativeImageDebugFileInfo implements DebugMethodInfo {
        protected final ResolvedJavaMethod method;
        protected int line;
        protected final List<DebugLocalInfo> paramInfo;
        protected final DebugLocalInfo thisParamInfo;

        NativeImageDebugBaseMethodInfo(ResolvedJavaMethod m) {
            super(m);
            // We occasionally see an AnalysisMethod as input to this constructor.
            // That can happen if the points to analysis builds one into a node
            // source position when building the initial graph. The global
            // replacement that is supposed to ensure the compiler sees HostedXXX
            // types rather than AnalysisXXX types appears to skip translating
            // method references in node source positions. So, we do the translation
            // here just to make sure we use a HostedMethod wherever possible.
            method = promoteAnalysisToHosted(m);
            LineNumberTable lineNumberTable = method.getLineNumberTable();
            line = (lineNumberTable != null ? lineNumberTable.getLineNumber(0) : 0);
            this.paramInfo = createParamInfo(method, line);
            // We use the target modifiers to decide where to install any first param
            // even though we may have added it according to whether method is static.
            // That's because in a few special cases method is static but the original
            // DebugFrameLocals
            // from which it is derived is an instance method. This appears to happen
            // when a C function pointer masquerades as a method. Whatever parameters
            // we pass through need to match the definition of the original.
            if (Modifier.isStatic(modifiers())) {
                this.thisParamInfo = null;
            } else {
                this.thisParamInfo = paramInfo.remove(0);
            }
        }

        private ResolvedJavaMethod promoteAnalysisToHosted(ResolvedJavaMethod m) {
            if (m instanceof AnalysisMethod) {
                return heap.getUniverse().lookup(m);
            }
            if (!(m instanceof HostedMethod)) {
                debugContext.log(DebugContext.DETAILED_LEVEL, "Method is neither Hosted nor Analysis : %s.%s%s", m.getDeclaringClass().getName(), m.getName(),
                                m.getSignature().toMethodDescriptor());
            }
            return m;
        }

        private ResolvedJavaMethod originalMethod() {
            // unwrap to an original method as far as we can
            ResolvedJavaMethod targetMethod = method;
            while (targetMethod instanceof WrappedJavaMethod) {
                targetMethod = ((WrappedJavaMethod) targetMethod).getWrapped();
            }
            // if we hit a substitution then we can translate to the original
            // for identity otherwise we use whatever we unwrapped to.
            if (targetMethod instanceof SubstitutionMethod) {
                targetMethod = ((SubstitutionMethod) targetMethod).getOriginal();
            }
            return targetMethod;
        }

        /**
         * Return the unique type that owns this method.
         * <p/>
         * In the absence of substitutions the returned type result is simply the original JVMCI
         * implementation type that declares the associated Java method. Identifying this type may
         * involve unwrapping from Hosted universe to Analysis universe to the original JVMCI
         * universe.
         * <p/>
         *
         * In the case where the method itself is either an (annotated) substitution or declared by
         * a class that is a (annotated) substitution then the link from substitution to original is
         * also 'unwrapped' to arrive at the original type. In cases where a substituted method has
         * no original the class of the substitution is used, for want of anything better.
         * <p/>
         *
         * This unwrapping strategy avoids two possible ambiguities that would compromise use of the
         * returned value as a unique 'owner'. Firstly, if the same method is ever presented via
         * both its HostedMethod incarnation and its original ResolvedJavaMethod incarnation then
         * ownership will always be signalled via the original type. This ensures that views of the
         * method presented via the list of included HostedTypes and via the list of CompiledMethod
         * objects and their embedded substructure (such as inline caller hierarchies) are correctly
         * identified.
         * <p/>
         *
         * Secondly, when a substituted method or method of a substituted class is presented it ends
         * up being collated with methods of the original class rather than the class which actually
         * defines it, avoiding an artificial split of methods that belong to the same underlying
         * runtime type into two distinct types in the debuginfo model. As an example, this ensures
         * that all methods of substitution class DynamicHub are collated with methods of class
         * java.lang.Class, the latter being the type whose name gets written into the debug info
         * and gets used to name the method in the debugger. Note that this still allows the
         * method's line info to be associated with the correct file i.e. it does not compromise
         * breaking and stepping through the code.
         *
         * @return the unique type that owns this method
         */
        @Override
        public ResolvedJavaType ownerType() {
            ResolvedJavaType result;
            if (method instanceof HostedMethod) {
                result = getDeclaringClass((HostedMethod) method, true);
            } else {
                result = method.getDeclaringClass();
            }
            while (result instanceof WrappedJavaType) {
                result = ((WrappedJavaType) result).getWrapped();
            }
            return result;
        }

        @Override
        public ResolvedJavaMethod idMethod() {
            // translating to the original ensures we equate a
            // substituted method with the original in case we ever see both
            return originalMethod();
        }

        @Override
        public String name() {
            ResolvedJavaMethod targetMethod = originalMethod();
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
        public ResolvedJavaType valueType() {
            ResolvedJavaType resultType = (ResolvedJavaType) method.getSignature().getReturnType(null);
            if (resultType instanceof HostedType) {
                return getOriginal((HostedType) resultType);
            }
            return resultType;
        }

        @Override
        public DebugLocalInfo[] getParamInfo() {
            return paramInfo.toArray(new DebugLocalInfo[paramInfo.size()]);
        }

        @Override
        public DebugLocalInfo getThisParamInfo() {
            return thisParamInfo;
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
            return name().endsWith(HostedMethod.MULTI_METHOD_KEY_SEPARATOR);
        }

        @Override
        public int modifiers() {
            if (method instanceof HostedMethod) {
                return getOriginalModifiers((HostedMethod) method);
            }
            return method.getModifiers();
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

    private List<DebugLocalInfo> createParamInfo(ResolvedJavaMethod method, int line) {
        Signature signature = method.getSignature();
        int parameterCount = signature.getParameterCount(false);
        List<DebugLocalInfo> paramInfos = new ArrayList<>(parameterCount);
        LocalVariableTable table = method.getLocalVariableTable();
        int slot = 0;
        ResolvedJavaType ownerType = method.getDeclaringClass();
        if (!method.isStatic()) {
            JavaKind kind = ownerType.getJavaKind();
            JavaKind storageKind = isPseudoObjectType(ownerType, ownerType) ? JavaKind.Long : kind;
            assert kind == JavaKind.Object : "must be an object";
            paramInfos.add(new NativeImageDebugLocalValueInfo("this", storageKind, ownerType, slot, line));
            slot += kind.getSlotCount();
        }
        for (int i = 0; i < parameterCount; i++) {
            Local local = (table == null ? null : table.getLocal(slot, 0));
            String name = (local != null ? local.getName() : "__" + i);
            ResolvedJavaType paramType = (ResolvedJavaType) signature.getParameterType(i, null);
            JavaKind kind = paramType.getJavaKind();
            JavaKind storageKind = isPseudoObjectType(paramType, ownerType) ? JavaKind.Long : kind;
            paramInfos.add(new NativeImageDebugLocalValueInfo(name, storageKind, paramType, slot, line));
            slot += kind.getSlotCount();
        }
        return paramInfos;
    }

    /**
     * Identify a pseudo-object Java type which is used only to model a memory word, pointer or
     * foreign opaque type.
     * 
     * @param type the type to be tested
     * @param accessingType another type relative to which the first type may need to be resolved
     * @return true if the type is a pseudo object type
     */
    private boolean isPseudoObjectType(JavaType type, ResolvedJavaType accessingType) {
        ResolvedJavaType resolvedJavaType = type.resolve(accessingType);
        return (wordBaseType.isAssignableFrom(resolvedJavaType));
    }

    private static boolean isIntegralKindPromotion(JavaKind promoted, JavaKind original) {
        return (promoted == JavaKind.Int &&
                        (original == JavaKind.Boolean || original == JavaKind.Byte || original == JavaKind.Short || original == JavaKind.Char));
    }

    protected abstract class NativeImageDebugHostedMethodInfo extends NativeImageDebugBaseMethodInfo {
        protected final HostedMethod hostedMethod;

        NativeImageDebugHostedMethodInfo(HostedMethod method) {
            super(method);
            this.hostedMethod = method;
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public boolean isVirtual() {
            return hostedMethod.hasVTableIndex();
        }

        @Override
        public int vtableOffset() {
            /*
             * TODO - provide correct offset, not index. In Graal, the vtable is appended after the
             * dynamicHub object, so can't just multiply by sizeof(pointer).
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
     * Implementation of the DebugCodeInfo API interface that allows code info to be passed to an
     * ObjectFile when generation of debug info is enabled.
     */
    private class NativeImageDebugCodeInfo extends NativeImageDebugHostedMethodInfo implements DebugCodeInfo {
        private final CompilationResult compilation;

        NativeImageDebugCodeInfo(HostedMethod method, CompilationResult compilation) {
            super(method);
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
        public int addressLo() {
            return hostedMethod.getCodeAddressOffset();
        }

        @Override
        public int addressHi() {
            return hostedMethod.getCodeAddressOffset() + compilation.getTargetCodeSize();
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
            final boolean omitInline = SubstrateOptions.OmitInlinedMethodDebugLineInfo.getValue();
            int frameSize = getFrameSize();
            final Visitor visitor = (omitInline ? new TopLevelVisitor(locationInfos, frameSize) : new MultiLevelVisitor(locationInfos, frameSize));
            // arguments passed by visitor to apply are
            // NativeImageDebugLocationInfo caller location info
            // CallNode nodeToEmbed parent call node to convert to entry code leaf
            // NativeImageDebugLocationInfo leaf into which current leaf may be merged
            root.visitChildren(visitor, (Object) null, (Object) null, (Object) null);
            // try to add a location record for offset zero
            updateInitialLocation(locationInfos);
            return locationInfos.stream();
        }

        private int findMarkOffset(SubstrateMarkId markId) {
            for (CompilationResult.CodeMark mark : compilation.getMarks()) {
                if (mark.id.equals(markId)) {
                    return mark.pcOffset;
                }
            }
            return -1;
        }

        private void updateInitialLocation(List<DebugLocationInfo> locationInfos) {
            int prologueEnd = findMarkOffset(SubstrateMarkId.PROLOGUE_END);
            if (prologueEnd < 0) {
                // this is not a normal compiled method so give up
                return;
            }
            int stackDecrement = findMarkOffset(SubstrateMarkId.PROLOGUE_DECD_RSP);
            if (stackDecrement < 0) {
                // this is not a normal compiled method so give up
                return;
            }
            // if there are any location info records then the first one will be for
            // a nop which follows the stack decrement, stack range check and pushes
            // of arguments into the stack frame.
            //
            // We can construct synthetic location info covering the first instruction
            // based on the method arguments and the calling convention and that will
            // normally be valid right up to the nop. In exceptional cases a call
            // might pass arguments on the stack, in which case the stack decrement will
            // invalidate the original stack locations. Providing location info for that
            // case requires adding two locations, one for initial instruction that does
            // the stack decrement and another for the range up to the nop. They will
            // be essentially the same but the stack locations will be adjusted to account
            // for the different value of the stack pointer.

            if (locationInfos.isEmpty()) {
                // this is not a normal compiled method so give up
                return;
            }
            NativeImageDebugLocationInfo firstLocation = (NativeImageDebugLocationInfo) locationInfos.get(0);
            int firstLocationOffset = firstLocation.addressLo();

            if (firstLocationOffset == 0) {
                // this is not a normal compiled method so give up
                return;
            }
            if (firstLocationOffset < prologueEnd) {
                // this is not a normal compiled method so give up
                return;
            }
            // create a synthetic location record including details of passed arguments
            ParamLocationProducer locProducer = new ParamLocationProducer(method);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Add synthetic Location Info : %s (0, %d)", method.getName(), firstLocationOffset - 1);
            NativeImageDebugLocationInfo locationInfo = new NativeImageDebugLocationInfo(method, firstLocationOffset, locProducer);
            // if the prologue extends beyond the stack extend and uses the stack then the info
            // needs
            // splitting at the extend point with the stack offsets adjusted in the new info
            if (locProducer.usesStack() && firstLocationOffset > stackDecrement) {
                int adjustment = adjustFrameSize(getFrameSize());
                NativeImageDebugLocationInfo splitLocationInfo = locationInfo.split(stackDecrement, adjustment);
                debugContext.log(DebugContext.DETAILED_LEVEL, "Split synthetic Location Info : %s (%d, %d) (%d, %d)", locationInfo.name(), 0,
                                locationInfo.addressLo() - 1, locationInfo.addressLo(), locationInfo.addressHi() - 1);
                locationInfos.add(0, splitLocationInfo);
            }
            locationInfos.add(0, locationInfo);
        }

        // indices for arguments passed to SingleLevelVisitor::apply

        protected static final int CALLER_INFO = 0;
        protected static final int PARENT_NODE_TO_EMBED = 1;
        protected static final int LAST_LEAF_INFO = 2;

        private abstract class SingleLevelVisitor implements Visitor {

            protected final List<DebugLocationInfo> locationInfos;
            protected final int frameSize;

            SingleLevelVisitor(List<DebugLocationInfo> locationInfos, int frameSize) {
                this.locationInfos = locationInfos;
                this.frameSize = frameSize;
            }

            public NativeImageDebugLocationInfo process(FrameNode node, NativeImageDebugLocationInfo callerInfo) {
                NativeImageDebugLocationInfo locationInfo;
                if (node instanceof CallNode) {
                    // this node represents an inline call range so
                    // add a locationinfo to cover the range of the call
                    locationInfo = createCallLocationInfo((CallNode) node, callerInfo, frameSize);
                } else if (isBadLeaf(node, callerInfo)) {
                    locationInfo = createBadLeafLocationInfo(node, callerInfo, frameSize);
                } else {
                    // this is leaf method code so add details of its range
                    locationInfo = createLeafLocationInfo(node, callerInfo, frameSize);
                }
                return locationInfo;
            }
        }

        private class TopLevelVisitor extends SingleLevelVisitor {
            TopLevelVisitor(List<DebugLocationInfo> locationInfos, int frameSize) {
                super(locationInfos, frameSize);
            }

            @Override
            public void apply(FrameNode node, Object... args) {
                if (skipNode(node)) {
                    // this is a bogus wrapper so skip it and transform the wrapped node instead
                    node.visitChildren(this, args);
                } else {
                    NativeImageDebugLocationInfo locationInfo = process(node, null);
                    if (node instanceof CallNode) {
                        locationInfos.add(locationInfo);
                        // erase last leaf (if present) since there is an intervening call range
                        invalidateMerge(args);
                    } else {
                        locationInfo = tryMerge(locationInfo, args);
                        if (locationInfo != null) {
                            locationInfos.add(locationInfo);
                        }
                    }
                }
            }
        }

        public class MultiLevelVisitor extends SingleLevelVisitor {
            MultiLevelVisitor(List<DebugLocationInfo> locationInfos, int frameSize) {
                super(locationInfos, frameSize);
            }

            @Override
            public void apply(FrameNode node, Object... args) {
                if (skipNode(node)) {
                    // this is a bogus wrapper so skip it and transform the wrapped node instead
                    node.visitChildren(this, args);
                } else {
                    NativeImageDebugLocationInfo callerInfo = (NativeImageDebugLocationInfo) args[CALLER_INFO];
                    CallNode nodeToEmbed = (CallNode) args[PARENT_NODE_TO_EMBED];
                    if (nodeToEmbed != null) {
                        if (embedWithChildren(nodeToEmbed, node)) {
                            // embed a leaf range for the method start that was included in the
                            // parent CallNode
                            // its end range is determined by the start of the first node at this
                            // level
                            NativeImageDebugLocationInfo embeddedLocationInfo = createEmbeddedParentLocationInfo(nodeToEmbed, node, callerInfo, frameSize);
                            locationInfos.add(embeddedLocationInfo);
                            // since this is a leaf node we can merge later leafs into it
                            initMerge(embeddedLocationInfo, args);
                        }
                        // reset args so we only embed the parent node before the first node at
                        // this level
                        args[PARENT_NODE_TO_EMBED] = nodeToEmbed = null;
                    }
                    NativeImageDebugLocationInfo locationInfo = process(node, callerInfo);
                    if (node instanceof CallNode) {
                        CallNode callNode = (CallNode) node;
                        locationInfos.add(locationInfo);
                        // erase last leaf (if present) since there is an intervening call range
                        invalidateMerge(args);
                        if (hasChildren(callNode)) {
                            // a call node may include an initial leaf range for the call that must
                            // be
                            // embedded under the newly created location info so pass it as an
                            // argument
                            callNode.visitChildren(this, locationInfo, callNode, (Object) null);
                        } else {
                            // we need to embed a leaf node for the whole call range
                            locationInfo = createEmbeddedParentLocationInfo(callNode, null, locationInfo, frameSize);
                            locationInfos.add(locationInfo);
                        }
                    } else {
                        locationInfo = tryMerge(locationInfo, args);
                        if (locationInfo != null) {
                            locationInfos.add(locationInfo);
                        }
                    }
                }
            }
        }

        /**
         * Report whether a call node has any children.
         * 
         * @param callNode the node to check
         * @return true if it has any children otherwise false.
         */
        private boolean hasChildren(CallNode callNode) {
            Object[] result = new Object[]{false};
            callNode.visitChildren(new Visitor() {
                @Override
                public void apply(FrameNode node, Object... args) {
                    args[0] = true;
                }
            }, result);
            return (boolean) result[0];
        }

        /**
         * Create a location info record for a leaf subrange.
         * 
         * @param node is a simple FrameNode
         * @return the newly created location info record
         */
        private NativeImageDebugLocationInfo createLeafLocationInfo(FrameNode node, NativeImageDebugLocationInfo callerInfo, int framesize) {
            assert !(node instanceof CallNode);
            NativeImageDebugLocationInfo locationInfo = new NativeImageDebugLocationInfo(node, callerInfo, framesize);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Create leaf Location Info : %s depth %d (%d, %d)", locationInfo.name(), locationInfo.depth(), locationInfo.addressLo(),
                            locationInfo.addressHi() - 1);
            return locationInfo;
        }

        /**
         * Create a location info record for a subrange that encloses an inline call.
         * 
         * @param callNode is the top level inlined call frame
         * @return the newly created location info record
         */
        private NativeImageDebugLocationInfo createCallLocationInfo(CallNode callNode, NativeImageDebugLocationInfo callerInfo, int framesize) {
            BytecodePosition callerPos = realCaller(callNode);
            NativeImageDebugLocationInfo locationInfo = new NativeImageDebugLocationInfo(callerPos, callNode.getStartPos(), callNode.getEndPos() + 1, callerInfo, framesize);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Create call Location Info : %s depth %d (%d, %d)", locationInfo.name(), locationInfo.depth(), locationInfo.addressLo(),
                            locationInfo.addressHi() - 1);
            return locationInfo;
        }

        /**
         * Create a location info record for the initial range associated with a parent call node
         * whose position and start are defined by that call node and whose end is determined by the
         * first child of the call node.
         * 
         * @param parentToEmbed a parent call node which has already been processed to create the
         *            caller location info
         * @param firstChild the first child of the call node
         * @param callerLocation the location info created to represent the range for the call
         * @return a location info to be embedded as the first child range of the caller location.
         */
        private NativeImageDebugLocationInfo createEmbeddedParentLocationInfo(CallNode parentToEmbed, FrameNode firstChild, NativeImageDebugLocationInfo callerLocation, int framesize) {
            BytecodePosition pos = parentToEmbed.frame;
            int startPos = parentToEmbed.getStartPos();
            int endPos = (firstChild != null ? firstChild.getStartPos() : parentToEmbed.getEndPos() + 1);
            NativeImageDebugLocationInfo locationInfo = new NativeImageDebugLocationInfo(pos, startPos, endPos, callerLocation, framesize);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Embed leaf Location Info : %s depth %d (%d, %d)", locationInfo.name(), locationInfo.depth(), locationInfo.addressLo(),
                            locationInfo.addressHi() - 1);
            return locationInfo;
        }

        private NativeImageDebugLocationInfo createBadLeafLocationInfo(FrameNode node, NativeImageDebugLocationInfo callerLocation, int framesize) {
            assert !(node instanceof CallNode) : "bad leaf location cannot be a call node!";
            assert callerLocation == null : "should only see bad leaf at top level!";
            BytecodePosition pos = node.frame;
            BytecodePosition callerPos = pos.getCaller();
            assert callerPos != null : "bad leaf must have a caller";
            assert callerPos.getCaller() == null : "bad leaf caller must be root method";
            int startPos = node.getStartPos();
            int endPos = node.getEndPos() + 1;
            NativeImageDebugLocationInfo locationInfo = new NativeImageDebugLocationInfo(callerPos, startPos, endPos, null, framesize);
            debugContext.log(DebugContext.DETAILED_LEVEL, "Embed leaf Location Info : %s depth %d (%d, %d)", locationInfo.name(), locationInfo.depth(), locationInfo.addressLo(),
                            locationInfo.addressHi() - 1);
            return locationInfo;
        }

        private boolean isBadLeaf(FrameNode node, NativeImageDebugLocationInfo callerLocation) {
            // Sometimes we see a leaf node marked as belonging to an inlined method
            // that sits directly under the root method rather than under a call node.
            // It needs replacing with a location info for the root method that covers
            // the relevant code range.
            if (callerLocation == null) {
                BytecodePosition pos = node.frame;
                BytecodePosition callerPos = pos.getCaller();
                if (callerPos != null && !callerPos.getMethod().equals(pos.getMethod())) {
                    if (callerPos.getCaller() == null) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Test whether a bytecode position represents a bogus frame added by the compiler when a
         * substitution or snippet call is injected.
         * 
         * @param pos the position to be tested
         * @return true if the frame is bogus otherwise false
         */
        private boolean skipPos(BytecodePosition pos) {
            return (pos.getBCI() == -1 && pos instanceof NodeSourcePosition && ((NodeSourcePosition) pos).isSubstitution());
        }

        /**
         * Skip caller nodes with bogus positions, as determined by
         * {@link #skipPos(BytecodePosition)}, returning first caller node position that is not
         * bogus.
         * 
         * @param node the node whose callers are to be traversed
         * @return the first non-bogus position in the caller chain.
         */
        private BytecodePosition realCaller(CallNode node) {
            BytecodePosition pos = node.frame.getCaller();
            while (skipPos(pos)) {
                pos = pos.getCaller();
            }
            return pos;
        }

        /**
         * Test whether the position associated with a child node should result in an entry in the
         * inline tree. The test is for a call node with a bogus position as determined by
         * {@link #skipPos(BytecodePosition)}.
         * 
         * @param node A node associated with a child frame in the compilation result frame tree.
         * @return True an entry should be included or false if it should be omitted.
         */
        private boolean skipNode(FrameNode node) {
            return node instanceof CallNode && skipPos(node.frame);
        }

        /*
         * Test whether the position associated with a call node frame should be embedded along with
         * the locations generated for the node's children. This is needed because call frames
         * include a valid source position that precedes the first child position.
         * 
         * @param node A node associated with a frame in the compilation result frame tree.
         * 
         * @return True if an inline frame should be included or false if it should be omitted.
         */

        /**
         * Test whether the position associated with a call node frame should be embedded along with
         * the locations generated for the node's children. This is needed because call frames may
         * include a valid source position that precedes the first child position.
         *
         * @param parent The call node whose children are currently being visited
         * @param firstChild The first child of that call node
         * @return true if the node should be embedded otherwise false
         */
        private boolean embedWithChildren(CallNode parent, FrameNode firstChild) {
            // we only need to insert a range for the caller if it fills a gap
            // at the start of the caller range before the first child
            if (parent.getStartPos() < firstChild.getStartPos()) {
                return true;
            }
            return false;
        }

        /**
         * Try merging a new location info for a leaf range into the location info for the last leaf
         * range added at this level.
         * 
         * @param newLeaf the new leaf location info
         * @param args the visitor argument vector used to pass parameters from one child visit to
         *            the next possibly including the last leaf
         * @return the new location info if it could not be merged or null to indicate that it was
         *         merged
         */
        private NativeImageDebugLocationInfo tryMerge(NativeImageDebugLocationInfo newLeaf, Object[] args) {
            // last leaf node added at this level is 3rd element of arg vector
            NativeImageDebugLocationInfo lastLeaf = (NativeImageDebugLocationInfo) args[LAST_LEAF_INFO];

            if (lastLeaf != null) {
                // try merging new leaf into last one
                lastLeaf = lastLeaf.merge(newLeaf);
                if (lastLeaf != null) {
                    // null return indicates new leaf has been merged into last leaf
                    return null;
                }
            }
            // update last leaf and return new leaf for addition to local info list
            args[LAST_LEAF_INFO] = newLeaf;
            return newLeaf;
        }

        /**
         * Set the last leaf node at the current level to the supplied leaf node.
         * 
         * @param lastLeaf the last leaf node created at this level
         * @param args the visitor argument vector used to pass parameters from one child visit to
         *            the next
         */
        private void initMerge(NativeImageDebugLocationInfo lastLeaf, Object[] args) {
            args[LAST_LEAF_INFO] = lastLeaf;
        }

        /**
         * Clear the last leaf node at the current level from the visitor arguments by setting the
         * arg vector entry to null.
         * 
         * @param args the visitor argument vector used to pass parameters from one child visit to
         *            the next
         */
        private void invalidateMerge(Object[] args) {
            args[LAST_LEAF_INFO] = null;
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
                if (mark.id.equals(SubstrateMarkId.PROLOGUE_DECD_RSP)) {
                    NativeImageDebugFrameSizeChange sizeChange = new NativeImageDebugFrameSizeChange(mark.pcOffset, EXTEND);
                    frameSizeChanges.add(sizeChange);
                    // } else if (mark.id.equals("PROLOGUE_END")) {
                    // can ignore these
                    // } else if (mark.id.equals("EPILOGUE_START")) {
                    // can ignore these
                } else if (mark.id.equals(SubstrateMarkId.EPILOGUE_INCD_RSP)) {
                    NativeImageDebugFrameSizeChange sizeChange = new NativeImageDebugFrameSizeChange(mark.pcOffset, CONTRACT);
                    frameSizeChanges.add(sizeChange);
                } else if (mark.id.equals(SubstrateMarkId.EPILOGUE_END) && mark.pcOffset < compilation.getTargetCodeSize()) {
                    /* There is code after this return point so notify a stack extend again. */
                    NativeImageDebugFrameSizeChange sizeChange = new NativeImageDebugFrameSizeChange(mark.pcOffset, EXTEND);
                    frameSizeChanges.add(sizeChange);
                }
            }
            return frameSizeChanges;
        }
    }

    /**
     * Implementation of the DebugLocationInfo API interface that allows line number and local var
     * info to be passed to an ObjectFile when generation of debug info is enabled.
     */
    private class NativeImageDebugLocationInfo extends NativeImageDebugBaseMethodInfo implements DebugLocationInfo {
        private final int bci;
        private int lo;
        private int hi;
        private DebugLocationInfo callersLocationInfo;
        private List<DebugLocalValueInfo> localInfoList;

        NativeImageDebugLocationInfo(FrameNode frameNode, NativeImageDebugLocationInfo callersLocationInfo, int framesize) {
            this(frameNode.frame, frameNode.getStartPos(), frameNode.getEndPos() + 1, callersLocationInfo, framesize);
        }

        NativeImageDebugLocationInfo(BytecodePosition bcpos, int lo, int hi, NativeImageDebugLocationInfo callersLocationInfo, int framesize) {
            super(bcpos.getMethod());
            this.bci = bcpos.getBCI();
            this.lo = lo;
            this.hi = hi;
            this.callersLocationInfo = callersLocationInfo;
            this.localInfoList = initLocalInfoList(bcpos, framesize);
        }

        // special constructor for synthetic lcoation info added at start of method
        NativeImageDebugLocationInfo(ResolvedJavaMethod method, int hi, ParamLocationProducer locProducer) {
            super(method);
            // bci is always 0 and lo is always 0.
            this.bci = 0;
            this.lo = 0;
            this.hi = hi;
            // this is always going to be a top-level leaf range.
            this.callersLocationInfo = null;
            // location info is synthesized off the method signature
            this.localInfoList = initSyntheticInfoList(locProducer);
        }

        NativeImageDebugLocationInfo(NativeImageDebugLocationInfo toSplit, int stackDecrement, int frameSize) {
            super(toSplit.method);
            this.lo = stackDecrement;
            this.hi = toSplit.hi;
            toSplit.hi = this.lo;
            this.bci = toSplit.bci;
            this.callersLocationInfo = toSplit.callersLocationInfo;
            this.localInfoList = new ArrayList<>(toSplit.localInfoList.size());
            for (DebugLocalValueInfo localInfo : toSplit.localInfoList) {
                if (localInfo.localKind() == DebugLocalValueInfo.LocalKind.STACKSLOT) {
                    int newSlot = localInfo.stackSlot() + frameSize;
                    NativeImageDebugLocalValue value = new NativeImageDebugStackValue(newSlot);
                    NativeImageDebugLocalValueInfo nativeLocalInfo = (NativeImageDebugLocalValueInfo) localInfo;
                    NativeImageDebugLocalValueInfo newLocalinfo = new NativeImageDebugLocalValueInfo(nativeLocalInfo.name,
                                    value,
                                    nativeLocalInfo.kind,
                                    nativeLocalInfo.type,
                                    nativeLocalInfo.slot,
                                    nativeLocalInfo.line);
                    localInfoList.add(newLocalinfo);
                } else {
                    localInfoList.add(localInfo);
                }
            }
        }

        private List<DebugLocalValueInfo> initLocalInfoList(BytecodePosition bcpos, int framesize) {
            if (!(bcpos instanceof BytecodeFrame)) {
                return null;
            }

            BytecodeFrame frame = (BytecodeFrame) bcpos;
            if (frame.numLocals == 0) {
                return null;
            }
            // deal with any inconsistencies in the layout of the frame locals
            // NativeImageDebugFrameInfo debugFrameInfo = new NativeImageDebugFrameInfo(frame);

            LineNumberTable lineNumberTable = frame.getMethod().getLineNumberTable();
            Local[] localsBySlot = getLocalsBySlot();
            if (localsBySlot == null) {
                return Collections.emptyList();
            }
            int count = Integer.min(localsBySlot.length, frame.numLocals);
            ArrayList<DebugLocalValueInfo> localInfos = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Local l = localsBySlot[i];
                if (l != null) {
                    // we have a local with a known name, type and slot
                    String name = l.getName();
                    ResolvedJavaType ownerType = method.getDeclaringClass();
                    ResolvedJavaType type = l.getType().resolve(ownerType);
                    JavaKind kind = type.getJavaKind();
                    int slot = l.getSlot();
                    debugContext.log(DebugContext.DETAILED_LEVEL, "locals[%d] %s type %s slot %d", i, name, type.getName(), slot);
                    JavaValue value = (slot < frame.numLocals ? frame.getLocalValue(slot) : Value.ILLEGAL);
                    JavaKind storageKind = (slot < frame.numLocals ? frame.getLocalValueKind(slot) : JavaKind.Illegal);
                    debugContext.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, storageKind);
                    int bciStart = l.getStartBCI();
                    int firstLine = (lineNumberTable != null ? lineNumberTable.getLineNumber(bciStart) : -1);
                    // only add the local if the kinds match
                    if ((storageKind == kind) ||
                                    isIntegralKindPromotion(storageKind, kind) ||
                                    (isPseudoObjectType(type, ownerType) && kind == JavaKind.Object && storageKind == JavaKind.Long)) {
                        localInfos.add(new NativeImageDebugLocalValueInfo(name, value, framesize, storageKind, type, slot, firstLine));
                    } else if (storageKind != JavaKind.Illegal) {
                        debugContext.log(DebugContext.DETAILED_LEVEL, "  value kind incompatible with var kind %s!", type.getJavaKind());
                    }
                }
            }
            return localInfos;
        }

        private List<DebugLocalValueInfo> initSyntheticInfoList(ParamLocationProducer locProducer) {
            Signature signature = method.getSignature();
            int parameterCount = signature.getParameterCount(false);
            ArrayList<DebugLocalValueInfo> localInfos = new ArrayList<>();
            LocalVariableTable table = method.getLocalVariableTable();
            LineNumberTable lineNumberTable = method.getLineNumberTable();
            int firstLine = (lineNumberTable != null ? lineNumberTable.getLineNumber(0) : -1);
            int slot = 0;
            int localIdx = 0;
            ResolvedJavaType ownerType = method.getDeclaringClass();
            if (!method.isStatic()) {
                String name = "this";
                JavaKind kind = ownerType.getJavaKind();
                JavaKind storageKind = isPseudoObjectType(ownerType, ownerType) ? JavaKind.Long : kind;
                assert kind == JavaKind.Object : "must be an object";
                NativeImageDebugLocalValue value = locProducer.nextLocation(kind);
                debugContext.log(DebugContext.DETAILED_LEVEL, "locals[%d] %s type %s slot %d", localIdx, name, ownerType.getName(), slot);
                debugContext.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, storageKind);
                localInfos.add(new NativeImageDebugLocalValueInfo(name, value, storageKind, ownerType, slot, firstLine));
                slot += storageKind.getSlotCount();
                localIdx++;
            }
            for (int i = 0; i < parameterCount; i++) {
                Local local = (table == null ? null : table.getLocal(slot, 0));
                String name = (local != null ? local.getName() : "__" + i);
                ResolvedJavaType paramType = (ResolvedJavaType) signature.getParameterType(i, ownerType);
                JavaKind kind = paramType.getJavaKind();
                JavaKind storageKind = isPseudoObjectType(paramType, ownerType) ? JavaKind.Long : kind;
                NativeImageDebugLocalValue value = locProducer.nextLocation(kind);
                debugContext.log(DebugContext.DETAILED_LEVEL, "locals[%d] %s type %s slot %d", localIdx, name, ownerType.getName(), slot);
                debugContext.log(DebugContext.DETAILED_LEVEL, "  =>  %s kind %s", value, storageKind);
                localInfos.add(new NativeImageDebugLocalValueInfo(name, value, storageKind, paramType, slot, firstLine));
                slot += storageKind.getSlotCount();
                localIdx++;
            }
            return localInfos;
        }

        private Local[] getLocalsBySlot() {
            LocalVariableTable lvt = method.getLocalVariableTable();
            Local[] nonEmptySortedLocals = null;
            if (lvt != null) {
                Local[] locals = lvt.getLocalsAt(bci);
                if (locals != null && locals.length > 0) {
                    nonEmptySortedLocals = Arrays.copyOf(locals, locals.length);
                    Arrays.sort(nonEmptySortedLocals, (Local l1, Local l2) -> l1.getSlot() - l2.getSlot());
                }
            }
            return nonEmptySortedLocals;
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
        public DebugLocationInfo getCaller() {
            return callersLocationInfo;
        }

        @Override
        public DebugLocalValueInfo[] getLocalValueInfo() {
            if (localInfoList != null) {
                return localInfoList.toArray(new DebugLocalValueInfo[localInfoList.size()]);
            } else {
                return EMPTY_LOCAL_VALUE_INFOS;
            }
        }

        public int depth() {
            int depth = 1;
            DebugLocationInfo caller = getCaller();
            while (caller != null) {
                depth++;
                caller = caller.getCaller();
            }
            return depth;
        }

        private int localsSize() {
            if (localInfoList != null) {
                return localInfoList.size();
            } else {
                return 0;
            }
        }

        /**
         * Merge the supplied leaf location info into this leaf location info if they have
         * contiguous ranges, the same method and line number and the same live local variables with
         * the same values.
         * 
         * @param that a leaf location info to be merged into this one
         * @return this leaf location info if the merge was performed otherwise null
         */
        NativeImageDebugLocationInfo merge(NativeImageDebugLocationInfo that) {
            assert callersLocationInfo == that.callersLocationInfo;
            assert depth() == that.depth() : "should only compare sibling ranges";
            assert this.hi <= that.lo : "later nodes should not overlap earlier ones";
            if (this.hi != that.lo) {
                return null;
            }
            if (!method.equals(that.method)) {
                return null;
            }
            if (line() != that.line()) {
                return null;
            }
            int size = localsSize();
            if (size != that.localsSize()) {
                return null;
            }
            for (int i = 0; i < size; i++) {
                NativeImageDebugLocalValueInfo thisLocal = (NativeImageDebugLocalValueInfo) localInfoList.get(i);
                NativeImageDebugLocalValueInfo thatLocal = (NativeImageDebugLocalValueInfo) that.localInfoList.get(i);
                if (!thisLocal.equals(thatLocal)) {
                    return null;
                }
            }
            debugContext.log(DebugContext.DETAILED_LEVEL, "Merge  leaf Location Info : %s depth %d (%d, %d) into (%d, %d)", that.name(), that.depth(), that.lo, that.hi - 1, this.lo, this.hi - 1);
            // merging just requires updating lo and hi range as everything else is equal
            this.hi = that.hi;

            return this;
        }

        public NativeImageDebugLocationInfo split(int stackDecrement, int adjustment) {
            // this should be for an initial range extending beyond the stack decrement
            assert lo == 0 && lo < stackDecrement && stackDecrement < hi : "invalid split request";
            return new NativeImageDebugLocationInfo(this, stackDecrement, adjustment);
        }

    }

    private static final DebugLocalValueInfo[] EMPTY_LOCAL_VALUE_INFOS = new DebugLocalValueInfo[0];

    static final Register[] AARCH64_GPREG = {
                    AArch64.r0,
                    AArch64.r1,
                    AArch64.r2,
                    AArch64.r3,
                    AArch64.r4,
                    AArch64.r5,
                    AArch64.r6,
                    AArch64.r7
    };
    static final Register[] AARCH64_FREG = {
                    AArch64.v0,
                    AArch64.v1,
                    AArch64.v2,
                    AArch64.v3,
                    AArch64.v4,
                    AArch64.v5,
                    AArch64.v6,
                    AArch64.v7
    };
    static final Register[] AMD64_GPREG_LINUX = {
                    AMD64.rdi,
                    AMD64.rsi,
                    AMD64.rdx,
                    AMD64.rcx,
                    AMD64.r8,
                    AMD64.r9
    };
    static final Register[] AMD64_FREG_LINUX = {
                    AMD64.xmm0,
                    AMD64.xmm1,
                    AMD64.xmm2,
                    AMD64.xmm3,
                    AMD64.xmm4,
                    AMD64.xmm5,
                    AMD64.xmm6,
                    AMD64.xmm7
    };
    static final Register[] AMD64_GPREG_WINDOWS = {
                    AMD64.rdx,
                    AMD64.r8,
                    AMD64.r9,
                    AMD64.rdi,
                    AMD64.rsi,
                    AMD64.rcx
    };
    static final Register[] AMD64_FREG_WINDOWS = {
                    AMD64.xmm0,
                    AMD64.xmm1,
                    AMD64.xmm2,
                    AMD64.xmm3
    };

    /**
     * adjustment in bytes added to offset for stack passed parameters on AMD64
     *
     * the value allows for a word offset from the unadjusted sp to allow for the stacked return
     * address and a second word offset to address the first stack passed parameter with index 0.
     */
    static final int AMD64_STACK_OFFSET = 16;

    /**
     * adjustment in bytes added to offset for stack passed parameters on AARCH64
     *
     * the value allows for a word offset from the unadjusted sp to address the first stack passed
     * parameter with index 0.
     */
    static final int AARCH64_STACK_OFFSET = 8;

    /**
     * Adjustment in bytes added to frame size when recomputing parameter stack offsets after stack
     * adjustment on AMD64.
     *
     * The value allows for the fact that the reported framesize includes the stacked return address
     * which has already been rolled into the offsets derived for the unadjusted sp.
     */
    static final int AMD64_FRAMESIZE_ADJUSTMENT = -8;

    /**
     * Adjustment in bytes added to frame size when recomputing parameter stack offsets after stack
     * adjustment on AARCH64.
     *
     * The value in this case is zero. Although the reported framesize for AArch64 includes the
     * pushed lr and fp registers these have not been rolled into the offsets derived for the
     * unadjusted sp.
     */
    static final int AARCH64_FRAMESIZE_ADJUSTMENT = 0;

    static int adjustFrameSize(int frameSize) {
        // make sure this is the right arch and os
        Architecture arch = ConfigurationValues.getTarget().arch;
        assert arch instanceof AMD64 || arch instanceof AArch64 : "unexpected architecture";
        OS os = OS.getCurrent();
        assert os == OS.LINUX || os == OS.WINDOWS : "unexpected os";
        int adjustment = frameSize;
        if (arch instanceof AMD64) {
            // reported amd64 frame size includes an extra 8 bytes for the stacked return address
            adjustment += AMD64_FRAMESIZE_ADJUSTMENT;
        } else {
            adjustment += AARCH64_FRAMESIZE_ADJUSTMENT;
        }
        return adjustment;
    }

    class ParamLocationProducer {
        Register[] gpregs;
        Register[] fregs;
        int nextGPRegIdx;
        int nextFPregIdx;
        int nextStackIdx;
        int stackParamCount;
        int stackOffset;

        ParamLocationProducer(ResolvedJavaMethod method) {
            Architecture arch = ConfigurationValues.getTarget().arch;
            assert arch instanceof AMD64 || arch instanceof AArch64 : "unexpected architecture";
            OS os = OS.getCurrent();
            assert os == OS.LINUX || os == OS.WINDOWS : "unexpected os";
            if (arch instanceof AArch64) {
                assert os == OS.LINUX : "unexpected os/architecture";
                gpregs = AARCH64_GPREG;
                fregs = AARCH64_FREG;
                stackOffset = AARCH64_STACK_OFFSET;
            } else {
                if (os == OS.LINUX) {
                    gpregs = AMD64_GPREG_LINUX;
                    fregs = AMD64_FREG_LINUX;
                } else {
                    gpregs = AMD64_GPREG_WINDOWS;
                    fregs = AMD64_FREG_WINDOWS;
                }
                stackOffset = AMD64_STACK_OFFSET;
            }
            nextGPRegIdx = 0;
            nextFPregIdx = 0;
            nextStackIdx = 0;
            stackParamCount = computeStackCount(method);
        }

        public NativeImageDebugLocalValue nextLocation(JavaKind kind) {
            switch (kind) {
                case Float:
                case Double:
                    return nextFloatingLocation();
                case Void:
                case Illegal:
                    assert false : "unexpected parameter kind in next location request";
                    return null;
                default:
                    return nextIntegerLocation();
            }
        }

        public NativeImageDebugLocalValue nextFloatingLocation() {
            if (nextFPregIdx < fregs.length) {
                return new NativeImageDebugRegisterValue(fregs[nextFPregIdx++].number);
            } else {
                return nextStackLocation();
            }
        }

        public NativeImageDebugLocalValue nextIntegerLocation() {
            if (nextGPRegIdx < gpregs.length) {
                return new NativeImageDebugRegisterValue(gpregs[nextGPRegIdx++].number);
            } else {
                return nextStackLocation();
            }
        }

        public NativeImageDebugLocalValue nextStackLocation() {
            // offset is computed relative to the undecremented stack pointer and includes an extra
            // offset to adjust for any intervening return address and frame pointer
            assert nextStackIdx < stackParamCount : "encountered too many stack params";
            int stackIdx = nextStackIdx++;
            return new NativeImageDebugStackValue((stackIdx * 8) + stackOffset);
        }

        public boolean usesStack() {
            return stackParamCount > 0;
        }

        private int computeStackCount(ResolvedJavaMethod method) {
            int numIntegerParams = 0;
            int numFloatingParams = 0;
            Signature signature = method.getSignature();
            int parameterCount = signature.getParameterCount(false);
            if (!method.isStatic()) {
                numIntegerParams++;
            }
            for (int i = 0; i < parameterCount; i++) {
                switch (signature.getParameterKind(i)) {
                    case Float:
                    case Double:
                        numFloatingParams++;
                        break;
                    case Void:
                    case Illegal:
                        assert false : "unexpected parameter kind in method sig";
                        break;
                    default:
                        numIntegerParams++;
                        break;
                }
            }
            int excessParams = 0;
            if (numIntegerParams > gpregs.length) {
                excessParams += (numIntegerParams - gpregs.length);
            }
            if (numFloatingParams > fregs.length) {
                excessParams += (numFloatingParams - fregs.length);
            }
            return excessParams;
        }
    }

    public class NativeImageDebugLocalValueInfo implements DebugLocalValueInfo {
        private final String name;
        private ResolvedJavaType type;
        private final NativeImageDebugLocalValue value;
        private final JavaKind kind;
        private int slot;
        private int line;
        private LocalKind localKind;

        NativeImageDebugLocalValueInfo(String name, JavaKind kind, ResolvedJavaType type, int slot, int line) {
            this(name, Value.ILLEGAL, 0, kind, type, slot, line);
        }

        NativeImageDebugLocalValueInfo(String name, JavaValue value, int framesize, JavaKind kind, ResolvedJavaType resolvedType, int slot, int line) {
            this.name = name;
            this.kind = kind;
            this.slot = slot;
            this.line = line;
            // if we don't have a type default it for the JavaKind
            // it may still end up null when kind is Undefined.
            this.type = (resolvedType != null ? resolvedType : hostedTypeForKind(kind));
            if (value instanceof RegisterValue) {
                this.localKind = LocalKind.REGISTER;
                this.value = new NativeImageDebugRegisterValue((RegisterValue) value);
            } else if (value instanceof StackSlot) {
                this.localKind = LocalKind.STACKSLOT;
                this.value = new NativeImageDebugStackValue((StackSlot) value, framesize);
            } else if (value instanceof JavaConstant) {
                JavaConstant constant = (JavaConstant) value;
                if (constant instanceof PrimitiveConstant || constant.isNull()) {
                    this.localKind = LocalKind.CONSTANT;
                    this.value = new NativeImageDebugConstantValue(constant);
                } else {
                    long heapOffset = objectOffset(constant);
                    if (heapOffset >= 0) {
                        this.localKind = LocalKind.CONSTANT;
                        this.value = new NativeImageDebugConstantValue(constant, heapOffset);
                    } else {
                        this.localKind = LocalKind.UNDEFINED;
                        this.value = null;
                    }
                }
            } else {
                this.localKind = LocalKind.UNDEFINED;
                this.value = null;
            }
        }

        NativeImageDebugLocalValueInfo(String name, NativeImageDebugLocalValue value, JavaKind kind, ResolvedJavaType type, int slot, int line) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.slot = slot;
            this.line = line;
            if (value == null) {
                this.localKind = LocalKind.UNDEFINED;
            } else if (value instanceof NativeImageDebugRegisterValue) {
                this.localKind = LocalKind.REGISTER;
            } else if (value instanceof NativeImageDebugStackValue) {
                this.localKind = LocalKind.STACKSLOT;
            } else if (value instanceof NativeImageDebugConstantValue) {
                this.localKind = LocalKind.CONSTANT;
            }
            this.value = value;
        }

        @Override
        public ResolvedJavaType valueType() {
            if (type != null && type instanceof HostedType) {
                return getOriginal((HostedType) type);
            }
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NativeImageDebugLocalValueInfo)) {
                return false;
            }
            NativeImageDebugLocalValueInfo that = (NativeImageDebugLocalValueInfo) o;
            // values need to have the same name
            if (!name.equals(that.name)) {
                return false;
            }
            // values need to be for the same line
            if (line != that.line) {
                return false;
            }
            // location kinds must match
            if (localKind != that.localKind) {
                return false;
            }
            // locations must match
            switch (localKind) {
                case REGISTER:
                case STACKSLOT:
                case CONSTANT:
                    return value.equals(that.value);
                default:
                    return true;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value) * 31 + line;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String typeName() {
            ResolvedJavaType valueType = valueType();
            return (valueType == null ? "" : valueType().toJavaName());
        }

        @Override
        public int slot() {
            return slot;
        }

        @Override
        public int slotCount() {
            return kind.getSlotCount();
        }

        @Override
        public JavaKind javaKind() {
            return kind;
        }

        @Override
        public int line() {
            return line;
        }

        @Override
        public String toString() {
            switch (localKind) {
                case REGISTER:
                    return "reg[" + regIndex() + "]";
                case STACKSLOT:
                    return "stack[" + stackSlot() + "]";
                case CONSTANT:
                    return "constant[" + (constantValue() != null ? constantValue().toValueString() : "null") + "]";
                default:
                    return "-";
            }
        }

        @Override
        public LocalKind localKind() {
            return localKind;
        }

        @Override
        public int regIndex() {
            return ((NativeImageDebugRegisterValue) value).getNumber();
        }

        @Override
        public int stackSlot() {
            return ((NativeImageDebugStackValue) value).getOffset();
        }

        @Override
        public long heapOffset() {
            return ((NativeImageDebugConstantValue) value).getHeapOffset();
        }

        @Override
        public JavaConstant constantValue() {
            return ((NativeImageDebugConstantValue) value).getConstant();
        }
    }

    public class NativeImageDebugLocalValue {
    }

    public class NativeImageDebugRegisterValue extends NativeImageDebugLocalValue {
        private int number;

        NativeImageDebugRegisterValue(RegisterValue value) {
            number = value.getRegister().number;
        }

        NativeImageDebugRegisterValue(int number) {
            this.number = number;
        }

        public int getNumber() {
            return number;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NativeImageDebugRegisterValue)) {
                return false;
            }
            NativeImageDebugRegisterValue that = (NativeImageDebugRegisterValue) o;
            return number == that.number;
        }

        @Override
        public int hashCode() {
            return number * 31;
        }
    }

    public class NativeImageDebugStackValue extends NativeImageDebugLocalValue {
        private int offset;

        NativeImageDebugStackValue(StackSlot value, int framesize) {
            offset = value.getOffset(framesize);
        }

        NativeImageDebugStackValue(int offset) {
            this.offset = offset;
        }

        public int getOffset() {
            return offset;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NativeImageDebugStackValue)) {
                return false;
            }
            NativeImageDebugStackValue that = (NativeImageDebugStackValue) o;
            return offset == that.offset;
        }

        @Override
        public int hashCode() {
            return offset * 31;
        }
    }

    public class NativeImageDebugConstantValue extends NativeImageDebugLocalValue {
        private JavaConstant value;
        private long heapoffset;

        NativeImageDebugConstantValue(JavaConstant value) {
            this(value, -1);
        }

        NativeImageDebugConstantValue(JavaConstant value, long heapoffset) {
            this.value = value;
            this.heapoffset = heapoffset;
        }

        public JavaConstant getConstant() {
            return value;
        }

        public long getHeapOffset() {
            return heapoffset;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NativeImageDebugConstantValue)) {
                return false;
            }
            NativeImageDebugConstantValue that = (NativeImageDebugConstantValue) o;
            return heapoffset == that.heapoffset && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value) * 31 + (int) heapoffset;
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
