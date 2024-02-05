/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

import static com.oracle.svm.hosted.c.info.AccessorInfo.AccessorKind.ADDRESS;
import static com.oracle.svm.hosted.c.info.AccessorInfo.AccessorKind.GETTER;
import static com.oracle.svm.hosted.c.info.AccessorInfo.AccessorKind.SETTER;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.graal.code.SubstrateCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.InjectedFieldsType;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.target.Backend;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Abstract base class for implementation of DebugInfoProvider API providing a suite of useful
 * static and instance methods useful to provider implementations.
 */
public abstract class NativeImageDebugInfoProviderBase {
    protected final NativeImageHeap heap;
    protected final NativeImageCodeCache codeCache;
    protected final NativeLibraries nativeLibs;
    protected final RuntimeConfiguration runtimeConfiguration;
    protected final boolean useHeapBase;
    protected final int compressShift;
    protected final int referenceSize;
    protected final int pointerSize;
    protected final int referenceAlignment;
    protected final int primitiveStartOffset;
    protected final int referenceStartOffset;
    protected final int tagsMask;

    protected final HostedType hubType;
    protected final HostedType wordBaseType;
    final HashMap<JavaKind, HostedType> javaKindToHostedType;
    private final Path cachePath = SubstrateOptions.getDebugInfoSourceCacheRoot();

    public NativeImageDebugInfoProviderBase(NativeImageCodeCache codeCache, NativeImageHeap heap, NativeLibraries nativeLibs, HostedMetaAccess metaAccess, RuntimeConfiguration runtimeConfiguration) {
        this.heap = heap;
        this.codeCache = codeCache;
        this.nativeLibs = nativeLibs;
        this.runtimeConfiguration = runtimeConfiguration;
        this.hubType = metaAccess.lookupJavaType(Class.class);
        this.wordBaseType = metaAccess.lookupJavaType(WordBase.class);
        this.pointerSize = ConfigurationValues.getTarget().wordSize;
        ObjectHeader objectHeader = Heap.getHeap().getObjectHeader();
        NativeImageHeap.ObjectInfo primitiveFields = heap.getObjectInfo(StaticFieldsSupport.getStaticPrimitiveFields());
        NativeImageHeap.ObjectInfo objectFields = heap.getObjectInfo(StaticFieldsSupport.getStaticObjectFields());
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
        this.referenceAlignment = getObjectLayout().getAlignment();
        /* Offsets need to be adjusted relative to the heap base plus partition-specific offset. */
        primitiveStartOffset = (int) primitiveFields.getOffset();
        referenceStartOffset = (int) objectFields.getOffset();
        javaKindToHostedType = initJavaKindToHostedTypes(metaAccess);
    }

    /*
     * HostedType wraps an AnalysisType and both HostedType and AnalysisType punt calls to
     * getSourceFilename to the wrapped class so for consistency we need to do type names and path
     * lookup relative to the doubly unwrapped HostedType.
     *
     * However, note that the result of the unwrap on the AnalysisType may be a SubstitutionType
     * which wraps both an original type and the annotated type that substitutes it. Unwrapping
     * normally returns the AnnotatedType which we need to use to resolve the file name. However, we
     * frequently (but not always) need to use the original to name the owning type to ensure that
     * names found in method param and return types resolve correctly.
     *
     * Likewise, unwrapping of an AnalysisMethod or AnalysisField may encounter a SubstitutionMethod
     * or SubstitutionField. It may also encounter a SubstitutionType when unwrapping the owner type
     * is unwrapped. In those cases also we may need to use the substituted metadata rather than the
     * substitution to ensure that names resolve correctly.
     *
     * The following static routines provide logic to perform unwrapping and, where necessary,
     * traversal from the various substitution metadata instance to the corresponding substituted
     * metadata instance.
     */

    protected static ResolvedJavaType getDeclaringClass(HostedType hostedType, boolean wantOriginal) {
        // unwrap to the underlying class either the original or target class
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
        ResolvedJavaMethod javaMethod = NativeImageDebugInfoProviderBase.getAnnotatedOrOriginal(hostedMethod);
        return javaMethod.getDeclaringClass();
    }

    @SuppressWarnings("unused")
    protected static ResolvedJavaType getDeclaringClass(HostedField hostedField, boolean wantOriginal) {
        /* for now fields are always reported as belonging to the original class */
        return getOriginal(hostedField.getDeclaringClass());
    }

    protected static ResolvedJavaType getOriginal(HostedType hostedType) {
        /* partially unwrap then traverse through substitutions to the original */
        ResolvedJavaType javaType = hostedType.getWrapped().getWrapped();
        if (javaType instanceof SubstitutionType) {
            return ((SubstitutionType) javaType).getOriginal();
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

    protected static int getOriginalModifiers(HostedMethod hostedMethod) {
        return NativeImageDebugInfoProviderBase.getAnnotatedOrOriginal(hostedMethod).getModifiers();
    }

    /*
     * GraalVM uses annotated interfaces to model foreign types. The following helpers support
     * detection and categorization of these types, whether presented as a JavaType or HostedType.
     */

    /**
     * Identify a Java type which is being used to model a foreign memory word or pointer type.
     *
     * @param type the type to be tested
     * @param accessingType another type relative to which the first type may need to be resolved
     * @return true if the type models a foreign memory word or pointer type
     */
    protected boolean isForeignWordType(JavaType type, ResolvedJavaType accessingType) {
        HostedType resolvedJavaType = (HostedType) type.resolve(accessingType);
        return isForeignWordType(resolvedJavaType);
    }

    /**
     * Identify a hosted type which is being used to model a foreign memory word or pointer type.
     *
     * @param hostedType the type to be tested
     * @return true if the type models a foreign memory word or pointer type
     */
    protected boolean isForeignWordType(HostedType hostedType) {
        // unwrap because native libs operates on the analysis type universe
        return nativeLibs.isWordBase(hostedType.getWrapped());
    }

    /**
     * Identify a hosted type which is being used to model a foreign pointer type.
     *
     * @param hostedType the type to be tested
     * @return true if the type models a foreign pointer type
     */
    protected boolean isForeignPointerType(HostedType hostedType) {
        // unwrap because native libs operates on the analysis type universe
        return nativeLibs.isPointerBase(hostedType.getWrapped());
    }

    /*
     * Foreign pointer types have associated element info which describes the target type. The
     * following helpers support querying of and access to this element info.
     */

    protected static boolean isTypedField(ElementInfo elementInfo) {
        if (elementInfo instanceof StructFieldInfo) {
            for (ElementInfo child : elementInfo.getChildren()) {
                if (child instanceof AccessorInfo) {
                    switch (((AccessorInfo) child).getAccessorKind()) {
                        case GETTER:
                        case SETTER:
                        case ADDRESS:
                            return true;
                    }
                }
            }
        }
        return false;
    }

    protected HostedType getFieldType(StructFieldInfo field) {
        // we should always have some sort of accessor, preferably a GETTER or a SETTER
        // but possibly an ADDRESS accessor
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo) {
                AccessorInfo accessorInfo = (AccessorInfo) elt;
                if (accessorInfo.getAccessorKind() == GETTER) {
                    return heap.hUniverse.lookup(accessorInfo.getReturnType());
                }
            }
        }
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo) {
                AccessorInfo accessorInfo = (AccessorInfo) elt;
                if (accessorInfo.getAccessorKind() == SETTER) {
                    return heap.hUniverse.lookup(accessorInfo.getParameterType(0));
                }
            }
        }
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo) {
                AccessorInfo accessorInfo = (AccessorInfo) elt;
                if (accessorInfo.getAccessorKind() == ADDRESS) {
                    return heap.hUniverse.lookup(accessorInfo.getReturnType());
                }
            }
        }
        assert false : "Field %s must have a GETTER, SETTER, ADDRESS or OFFSET accessor".formatted(field);
        // treat it as a word?
        // n.b. we want a hosted type not an analysis type
        return heap.hUniverse.lookup(wordBaseType);
    }

    protected static boolean fieldTypeIsEmbedded(StructFieldInfo field) {
        // we should always have some sort of accessor, preferably a GETTER or a SETTER
        // but possibly an ADDRESS
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo) {
                AccessorInfo accessorInfo = (AccessorInfo) elt;
                if (accessorInfo.getAccessorKind() == GETTER) {
                    return false;
                }
            }
        }
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo) {
                AccessorInfo accessorInfo = (AccessorInfo) elt;
                if (accessorInfo.getAccessorKind() == SETTER) {
                    return false;
                }
            }
        }
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo) {
                AccessorInfo accessorInfo = (AccessorInfo) elt;
                if (accessorInfo.getAccessorKind() == ADDRESS) {
                    return true;
                }
            }
        }
        throw VMError.shouldNotReachHere("Field %s must have a GETTER, SETTER, ADDRESS or OFFSET accessor".formatted(field));
    }

    protected static int elementSize(ElementInfo elementInfo) {
        if (elementInfo == null || !(elementInfo instanceof SizableInfo)) {
            return 0;
        }
        if (elementInfo instanceof StructInfo && ((StructInfo) elementInfo).isIncomplete()) {
            return 0;
        }
        Integer size = ((SizableInfo) elementInfo).getSizeInfo().getProperty();
        assert size != null;
        return size;
    }

    protected static String elementName(ElementInfo elementInfo) {
        if (elementInfo == null) {
            return "";
        } else {
            return elementInfo.getName();
        }
    }

    protected static SizableInfo.ElementKind elementKind(SizableInfo sizableInfo) {
        return sizableInfo.getKind();
    }

    /*
     * Debug info generation requires knowledge of a variety of parameters that determine object
     * sizes and layouts and the organization of the code and code cache. The following helper
     * methods provide access to this information.
     */

    static ObjectLayout getObjectLayout() {
        return ConfigurationValues.getObjectLayout();
    }

    public boolean useHeapBase() {
        return useHeapBase;
    }

    public int oopCompressShift() {
        return compressShift;
    }

    public int oopReferenceSize() {
        return referenceSize;
    }

    public int pointerSize() {
        return pointerSize;
    }

    public int oopAlignment() {
        return referenceAlignment;
    }

    public int oopTagsMask() {
        return tagsMask;
    }

    public int compiledCodeMax() {
        return codeCache.getCodeCacheSize();
    }

    /**
     * Return the offset into the initial heap at which the object identified by constant is located
     * or -1 if the object is not present in the initial heap.
     *
     * @param constant must have JavaKind Object and must be non-null.
     * @return the offset into the initial heap at which the object identified by constant is
     *         located or -1 if the object is not present in the initial heap.
     */
    public long objectOffset(JavaConstant constant) {
        assert constant.getJavaKind() == JavaKind.Object && !constant.isNull() : "invalid constant for object offset lookup";
        NativeImageHeap.ObjectInfo objectInfo = heap.getConstantInfo(constant);
        if (objectInfo != null) {
            return objectInfo.getOffset();
        }
        return -1;
    }

    protected HostedType hostedTypeForKind(JavaKind kind) {
        return javaKindToHostedType.get(kind);
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

    /**
     * Retrieve details of the native calling convention for a top level compiled method, including
     * details of which registers or stack slots are used to pass parameters.
     * 
     * @param method The method whose calling convention is required.
     * @return The calling convention for the method.
     */
    protected SubstrateCallingConvention getCallingConvention(HostedMethod method) {
        SubstrateCallingConventionKind callingConventionKind = method.getCallingConventionKind();
        HostedType declaringClass = method.getDeclaringClass();
        HostedType receiverType = method.isStatic() ? null : declaringClass;
        var signature = method.getSignature();
        final SubstrateCallingConventionType type;
        if (callingConventionKind.isCustom()) {
            type = method.getCustomCallingConventionType();
        } else {
            type = callingConventionKind.toType(false);
        }
        Backend backend = runtimeConfiguration.lookupBackend(method);
        RegisterConfig registerConfig = backend.getCodeCache().getRegisterConfig();
        assert registerConfig instanceof SubstrateRegisterConfig;
        return (SubstrateCallingConvention) registerConfig.getCallingConvention(type, signature.getReturnType(), signature.toParameterTypes(receiverType), backend);
    }

    /*
     * The following helpers aid construction of file paths for class source files.
     */

    protected static Path fullFilePathFromClassName(HostedType hostedInstanceClass) {
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

    public Path getCachePath() {
        return cachePath;
    }
}
