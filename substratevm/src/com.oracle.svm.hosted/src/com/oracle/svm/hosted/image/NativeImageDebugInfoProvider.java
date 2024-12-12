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

import static com.oracle.svm.hosted.c.info.AccessorInfo.AccessorKind.ADDRESS;
import static com.oracle.svm.hosted.c.info.AccessorInfo.AccessorKind.GETTER;
import static com.oracle.svm.hosted.c.info.AccessorInfo.AccessorKind.SETTER;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.RawPointerTo;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.EnumClassEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.ForeignFloatTypeEntry;
import com.oracle.objectfile.debugentry.ForeignIntegerTypeEntry;
import com.oracle.objectfile.debugentry.ForeignPointerTypeEntry;
import com.oracle.objectfile.debugentry.ForeignStructTypeEntry;
import com.oracle.objectfile.debugentry.ForeignTypeEntry;
import com.oracle.objectfile.debugentry.ForeignWordTypeEntry;
import com.oracle.objectfile.debugentry.InterfaceClassEntry;
import com.oracle.objectfile.debugentry.LoaderEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.debug.SharedDebugInfoProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.PointerToInfo;
import com.oracle.svm.hosted.c.info.PropertyInfo;
import com.oracle.svm.hosted.c.info.RawStructureInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.hosted.image.sources.SourceManager;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedInterface;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.InjectedFieldsType;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.hosted.substitute.SubstitutionType;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implementation of the DebugInfoProvider API interface that allows type, code and heap data info
 * to be passed to an ObjectFile when generation of debug info is enabled.
 */
class NativeImageDebugInfoProvider extends SharedDebugInfoProvider {
    protected final NativeImageHeap heap;
    protected final NativeImageCodeCache codeCache;
    protected final NativeLibraries nativeLibs;

    protected final int primitiveStartOffset;
    protected final int referenceStartOffset;
    private final Set<HostedMethod> allOverrides;

    NativeImageDebugInfoProvider(DebugContext debug, NativeImageCodeCache codeCache, NativeImageHeap heap, NativeLibraries nativeLibs, HostedMetaAccess metaAccess,
                    RuntimeConfiguration runtimeConfiguration) {
        super(debug, runtimeConfiguration, metaAccess, SubstrateOptions.getDebugInfoSourceCacheRoot());
        this.heap = heap;
        this.codeCache = codeCache;
        this.nativeLibs = nativeLibs;

        /* Offsets need to be adjusted relative to the heap base plus partition-specific offset. */
        NativeImageHeap.ObjectInfo primitiveFields = heap.getObjectInfo(StaticFieldsSupport.getStaticPrimitiveFields());
        NativeImageHeap.ObjectInfo objectFields = heap.getObjectInfo(StaticFieldsSupport.getStaticObjectFields());
        primitiveStartOffset = (int) primitiveFields.getOffset();
        referenceStartOffset = (int) objectFields.getOffset();

        /* Calculate the set of all HostedMethods that are overrides. */
        allOverrides = heap.hUniverse.getMethods().stream()
                        .filter(HostedMethod::hasVTableIndex)
                        .flatMap(m -> Arrays.stream(m.getImplementations())
                                        .filter(Predicate.not(m::equals)))
                        .collect(Collectors.toSet());
    }

    private static ResolvedJavaType getOriginal(ResolvedJavaType type) {
        /*
         * unwrap then traverse through substitutions to the original. We don't want to get the
         * original type of LambdaSubstitutionType to keep the stable name
         */
        while (type instanceof WrappedJavaType wrappedJavaType) {
            type = wrappedJavaType.getWrapped();
        }

        if (type instanceof SubstitutionType substitutionType) {
            type = substitutionType.getOriginal();
        } else if (type instanceof InjectedFieldsType injectedFieldsType) {
            type = injectedFieldsType.getOriginal();
        }

        return type;
    }

    private static ResolvedJavaMethod getAnnotatedOrOriginal(ResolvedJavaMethod method) {
        while (method instanceof WrappedJavaMethod wrappedJavaMethod) {
            method = wrappedJavaMethod.getWrapped();
        }
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
        // reference to the bytecode of the original. Hence, there is no associated file and the
        // permissions need to be determined from the custom substitution method itself.

        if (method instanceof SubstitutionMethod substitutionMethod) {
            method = substitutionMethod.getAnnotated();
        }

        return method;
    }

    @Override
    @SuppressWarnings("try")
    protected void handleDataInfo(Object data) {
        // log ObjectInfo data
        if (debug.isLogEnabled(DebugContext.INFO_LEVEL) && data instanceof NativeImageHeap.ObjectInfo objectInfo) {
            try (DebugContext.Scope s = debug.scope("DebugDataInfo")) {
                long offset = objectInfo.getOffset();
                long size = objectInfo.getSize();
                String typeName = objectInfo.getClazz().toJavaName();
                ImageHeapPartition partition = objectInfo.getPartition();
                String partitionName = partition.getName() + "{" + partition.getSize() + "}@" + partition.getStartOffset();
                String provenance = objectInfo.toString();

                debug.log(DebugContext.INFO_LEVEL, "Data: offset 0x%x size 0x%x type %s partition %s provenance %s ", offset, size, typeName, partitionName, provenance);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } else {
            super.handleDataInfo(data);
        }
    }

    private static int elementSize(ElementInfo elementInfo) {
        if (!(elementInfo instanceof SizableInfo) || elementInfo instanceof StructInfo structInfo && structInfo.isIncomplete()) {
            return 0;
        }
        return ((SizableInfo) elementInfo).getSizeInBytes();
    }

    private static String elementName(ElementInfo elementInfo) {
        if (elementInfo == null) {
            return "";
        } else {
            return elementInfo.getName();
        }
    }

    private static SizableInfo.ElementKind elementKind(SizableInfo sizableInfo) {
        return sizableInfo.getKind();
    }

    private static String typedefName(ElementInfo elementInfo) {
        String name = null;
        if (elementInfo != null) {
            if (elementInfo instanceof PointerToInfo) {
                name = ((PointerToInfo) elementInfo).getTypedefName();
            } else if (elementInfo instanceof StructInfo) {
                name = ((StructInfo) elementInfo).getTypedefName();
            }
            if (name == null) {
                name = elementInfo.getName();
            }
        }
        return name;
    }

    private boolean isForeignPointerType(HostedType type) {
        // unwrap because native libs operates on the analysis type universe
        return nativeLibs.isPointerBase(type.getWrapped());
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
            if (elt instanceof AccessorInfo accessorInfo) {
                if (accessorInfo.getAccessorKind() == GETTER) {
                    return heap.hUniverse.lookup(accessorInfo.getReturnType());
                }
            }
        }
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo accessorInfo) {
                if (accessorInfo.getAccessorKind() == SETTER) {
                    return heap.hUniverse.lookup(accessorInfo.getParameterType(0));
                }
            }
        }
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo accessorInfo) {
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
            if (elt instanceof AccessorInfo accessorInfo) {
                if (accessorInfo.getAccessorKind() == GETTER) {
                    return false;
                }
            }
        }
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo accessorInfo) {
                if (accessorInfo.getAccessorKind() == SETTER) {
                    return false;
                }
            }
        }
        for (ElementInfo elt : field.getChildren()) {
            if (elt instanceof AccessorInfo accessorInfo) {
                if (accessorInfo.getAccessorKind() == ADDRESS) {
                    return true;
                }
            }
        }
        throw VMError.shouldNotReachHere("Field %s must have a GETTER, SETTER, ADDRESS or OFFSET accessor".formatted(field));
    }

    @Override
    protected Stream<SharedType> typeInfo() {
        // null represents the header type
        return heap.hUniverse.getTypes().stream().map(type -> type);
    }

    @Override
    protected Stream<Pair<SharedMethod, CompilationResult>> codeInfo() {
        return codeCache.getOrderedCompilations().stream().map(pair -> Pair.create(pair.getLeft(), pair.getRight()));
    }

    @Override
    protected Stream<Object> dataInfo() {
        return heap.getObjects().stream().filter(obj -> obj.getPartition().getStartOffset() > 0).map(obj -> obj);
    }

    @Override
    protected long getCodeOffset(SharedMethod method) {
        assert method instanceof HostedMethod;
        return ((HostedMethod) method).getCodeAddressOffset();
    }

    @Override
    public MethodEntry lookupMethodEntry(SharedMethod method) {
        if (method instanceof HostedMethod hostedMethod && !hostedMethod.isOriginalMethod()) {
            method = hostedMethod.getMultiMethod(MultiMethod.ORIGINAL_METHOD);
        }
        return super.lookupMethodEntry(method);
    }

    @Override
    public CompiledMethodEntry lookupCompiledMethodEntry(MethodEntry methodEntry, SharedMethod method, CompilationResult compilation) {
        if (method instanceof HostedMethod hostedMethod && !hostedMethod.isOriginalMethod()) {
            // method = hostedMethod.getMultiMethod(MultiMethod.ORIGINAL_METHOD);
        }
        return super.lookupCompiledMethodEntry(methodEntry, method, compilation);
    }

    @Override
    protected void processTypeEntry(SharedType type, TypeEntry typeEntry) {
        assert type instanceof HostedType;
        HostedType hostedType = (HostedType) type;

        if (typeEntry instanceof StructureTypeEntry structureTypeEntry) {
            if (typeEntry instanceof ArrayTypeEntry arrayTypeEntry) {
                processArrayFields(hostedType, arrayTypeEntry);
            } else if (typeEntry instanceof ForeignTypeEntry foreignTypeEntry) {
                processForeignTypeFields(hostedType, foreignTypeEntry);
            } else {
                processFieldEntries(hostedType, structureTypeEntry);
            }

            if (typeEntry instanceof ClassEntry classEntry) {
                processInterfaces(hostedType, classEntry);
                processMethods(hostedType, classEntry);
            }
        }
    }

    private void processMethods(HostedType type, ClassEntry classEntry) {
        for (HostedMethod method : type.getAllDeclaredMethods()) {
            MethodEntry methodEntry = lookupMethodEntry(method);
            debug.log("typename %s adding %s method %s %s(%s)%n",
                            classEntry.getTypeName(), methodEntry.getModifiersString(), methodEntry.getValueType().getTypeName(), methodEntry.getMethodName(),
                            formatParams(methodEntry.getThisParam(), methodEntry.getParams()));
            classEntry.addMethod(methodEntry);
        }
    }

    private static String formatParams(LocalEntry thisParam, List<LocalEntry> paramInfo) {
        if (paramInfo.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (thisParam != null) {
            builder.append(thisParam.type().getTypeName());
            builder.append(' ');
            builder.append(thisParam.name());
        }
        for (LocalEntry param : paramInfo) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(param.type().getTypeName());
            builder.append(' ');
            builder.append(param.name());
        }

        return builder.toString();
    }

    @Override
    public String getMethodName(SharedMethod method) {
        String name;
        if (method instanceof HostedMethod hostedMethod) {
            name = hostedMethod.getName();
            if (name.equals("<init>")) {
                name = hostedMethod.getDeclaringClass().toJavaName();
                if (name.indexOf('.') >= 0) {
                    name = name.substring(name.lastIndexOf('.') + 1);
                }
                if (name.indexOf('$') >= 0) {
                    name = name.substring(name.lastIndexOf('$') + 1);
                }
            }
        } else {
            name = super.getMethodName(method);
        }
        return name;
    }

    @Override
    public boolean isOverride(SharedMethod method) {
        return method instanceof HostedMethod && allOverrides.contains(method);
    }

    @Override
    public boolean isVirtual(SharedMethod method) {
        return method instanceof HostedMethod hostedMethod && hostedMethod.hasVTableIndex();
    }

    @Override
    public String getSymbolName(SharedMethod method) {
        return NativeImage.localSymbolNameForMethod(method);
    }

    private void processInterfaces(HostedType type, ClassEntry classEntry) {
        for (HostedType interfaceType : type.getInterfaces()) {
            TypeEntry entry = lookupTypeEntry(interfaceType);
            if (entry instanceof InterfaceClassEntry interfaceClassEntry) {
                debug.log("typename %s adding interface %s%n", classEntry.getTypeName(), interfaceType.toJavaName());
                interfaceClassEntry.addImplementor(classEntry);
            } else {
                // don't model the interface relationship when the Java interface actually
                // identifies a
                // foreign type
                assert entry instanceof ForeignTypeEntry && classEntry instanceof ForeignTypeEntry;
            }
        }
    }

    private void processArrayFields(HostedType type, ArrayTypeEntry arrayTypeEntry) {
        JavaKind arrayKind = type.getBaseType().getJavaKind();
        int headerSize = getObjectLayout().getArrayBaseOffset(arrayKind);
        int arrayLengthOffset = getObjectLayout().getArrayLengthOffset();
        int arrayLengthSize = getObjectLayout().sizeInBytes(JavaKind.Int);
        assert arrayLengthOffset + arrayLengthSize <= headerSize;
        arrayTypeEntry.addField(createSyntheticFieldEntry("len", arrayTypeEntry, (HostedType) metaAccess.lookupJavaType(JavaKind.Int.toJavaClass()), arrayLengthOffset, arrayLengthSize));
    }

    private void processForeignTypeFields(HostedType type, ForeignTypeEntry foreignTypeEntry) {
        ElementInfo elementInfo = nativeLibs.findElementInfo(type);
        if (elementInfo instanceof StructInfo) {
            elementInfo.getChildren().stream().filter(NativeImageDebugInfoProvider::isTypedField)
                            .map(elt -> ((StructFieldInfo) elt))
                            .sorted(Comparator.comparingInt(field -> field.getOffsetInfo().getProperty()))
                            .forEach(field -> {
                                HostedType fieldType = getFieldType(field);
                                FieldEntry fieldEntry = createFieldEntry(foreignTypeEntry.getFileEntry(), field.getName(), foreignTypeEntry, fieldType, field.getOffsetInfo().getProperty(),
                                                field.getSizeInBytes(), fieldTypeIsEmbedded(field), 0);
                                foreignTypeEntry.addField(fieldEntry);
                            });
        }
    }

    private void processFieldEntries(HostedType type, StructureTypeEntry structureTypeEntry) {
        for (HostedField field : type.getInstanceFields(false)) {
            structureTypeEntry.addField(createFieldEntry(field, structureTypeEntry));
        }

        for (ResolvedJavaField field : type.getStaticFields()) {
            assert field instanceof HostedField;
            structureTypeEntry.addField(createFieldEntry((HostedField) field, structureTypeEntry));
        }
    }

    private FieldEntry createFieldEntry(HostedField field, StructureTypeEntry ownerType) {
        FileEntry fileEntry = lookupFileEntry(field);
        String fieldName = field.getName();
        HostedType valueType = field.getType();
        JavaKind storageKind = field.getType().getStorageKind();
        int size = getObjectLayout().sizeInBytes(storageKind);
        int modifiers = field.getModifiers();
        int offset = field.getLocation();
        /*
         * For static fields we need to add in the appropriate partition base but only if we have a
         * real offset
         */
        if (Modifier.isStatic(modifiers) && offset >= 0) {
            if (storageKind.isPrimitive()) {
                offset += primitiveStartOffset;
            } else {
                offset += referenceStartOffset;
            }
        }

        return createFieldEntry(fileEntry, fieldName, ownerType, valueType, offset, size, false, modifiers);
    }

    @Override
    protected TypeEntry createTypeEntry(SharedType type) {
        assert type instanceof HostedType;
        HostedType hostedType = (HostedType) type;

        String typeName = hostedType.toJavaName(); // stringTable.uniqueDebugString(idType.toJavaName());
        int size = getTypeSize(hostedType);
        long classOffset = getClassOffset(hostedType);
        LoaderEntry loaderEntry = lookupLoaderEntry(hostedType);
        String loaderName = loaderEntry == null ? uniqueNullStringEntry : loaderEntry.loaderId();
        long typeSignature = getTypeSignature(typeName + loaderName);
        long compressedTypeSignature = useHeapBase ? getTypeSignature(INDIRECT_PREFIX + typeName + loaderName) : typeSignature;

        if (hostedType.isPrimitive()) {
            JavaKind kind = hostedType.getStorageKind();
            debug.log("typename %s (%d bits)%n", typeName, kind == JavaKind.Void ? 0 : kind.getBitCount());
            return new PrimitiveTypeEntry(typeName, size, classOffset, typeSignature, kind);
        } else {
            // otherwise we have a structured type
            long layoutTypeSignature = getTypeSignature(LAYOUT_PREFIX + typeName + loaderName);
            if (hostedType.isArray()) {
                TypeEntry elementTypeEntry = lookupTypeEntry(hostedType.getComponentType());
                debug.log("typename %s element type %s base size %d length offset %d%n", typeName, elementTypeEntry.getTypeName(),
                                getObjectLayout().getArrayBaseOffset(hostedType.getComponentType().getStorageKind()), getObjectLayout().getArrayLengthOffset());
                return new ArrayTypeEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                layoutTypeSignature, elementTypeEntry, loaderEntry);
            } else {
                // otherwise this is a class entry
                ClassEntry superClass = hostedType.getSuperclass() == null ? null : (ClassEntry) lookupTypeEntry(hostedType.getSuperclass());

                if (debug.isLogEnabled() && superClass != null) {
                    debug.log("typename %s adding super %s%n", typeName, superClass.getTypeName());
                }

                FileEntry fileEntry = lookupFileEntry(hostedType);
                if (isForeignWordType(hostedType)) {
                    if (debug.isLogEnabled()) {
                        logForeignTypeInfo(hostedType);
                    }

                    ElementInfo elementInfo = nativeLibs.findElementInfo(hostedType);
                    SizableInfo.ElementKind elementKind = elementInfo instanceof SizableInfo ? ((SizableInfo) elementInfo).getKind() : null;
                    size = elementSize(elementInfo);

                    if (!isForeignPointerType(hostedType)) {
                        boolean isSigned = nativeLibs.isSigned(hostedType);
                        return new ForeignWordTypeEntry(typeName, size, classOffset, typeSignature, layoutTypeSignature,
                                        superClass, fileEntry, loaderEntry, isSigned);
                    } else if (elementInfo instanceof StructInfo) {
                        // look for the first interface that also has an associated StructInfo
                        String typedefName = typedefName(elementInfo); // stringTable.uniqueDebugString(typedefName(elementInfo));
                        ForeignStructTypeEntry parentEntry = null;
                        for (HostedInterface hostedInterface : hostedType.getInterfaces()) {
                            ElementInfo otherInfo = nativeLibs.findElementInfo(hostedInterface);
                            if (otherInfo instanceof StructInfo) {
                                parentEntry = (ForeignStructTypeEntry) lookupTypeEntry(hostedInterface);
                            }
                        }
                        return new ForeignStructTypeEntry(typeName, size, classOffset, typeSignature, layoutTypeSignature, superClass, fileEntry, loaderEntry, typedefName, parentEntry);
                    } else if (elementKind == SizableInfo.ElementKind.INTEGER) {
                        boolean isSigned = nativeLibs.isSigned(hostedType) || !((SizableInfo) elementInfo).isUnsigned();
                        return new ForeignIntegerTypeEntry(typeName, size, classOffset, typeSignature, layoutTypeSignature, superClass, fileEntry, loaderEntry, isSigned);
                    } else if (elementKind == SizableInfo.ElementKind.FLOAT) {
                        return new ForeignFloatTypeEntry(typeName, size, classOffset, typeSignature, layoutTypeSignature, superClass, fileEntry, loaderEntry);
                    } else {
                        // This must be a pointer. If the target type is known use it to declare the
                        // pointer
                        // type, otherwise default to 'void *'
                        TypeEntry pointerToEntry = null;
                        if (elementKind == SizableInfo.ElementKind.POINTER) {
                            // any target type for the pointer will be defined by a CPointerTo or
                            // RawPointerTo
                            // annotation
                            CPointerTo cPointerTo = type.getAnnotation(CPointerTo.class);
                            if (cPointerTo != null) {
                                HostedType pointerTo = heap.hMetaAccess.lookupJavaType(cPointerTo.value());
                                pointerToEntry = lookupTypeEntry(pointerTo);
                            }
                            RawPointerTo rawPointerTo = type.getAnnotation(RawPointerTo.class);
                            if (rawPointerTo != null) {
                                HostedType pointerTo = heap.hMetaAccess.lookupJavaType(rawPointerTo.value());
                                pointerToEntry = lookupTypeEntry(pointerTo);
                            }

                            if (pointerToEntry != null) {
                                debug.log("foreign type %s referent %s ", typeName, pointerToEntry.getTypeName());
                            } else {
                                debug.log("foreign type %s", typeName);
                            }
                        }

                        if (pointerToEntry == null) {
                            pointerToEntry = lookupTypeEntry(voidType);
                        }

                        if (pointerToEntry != null) {
                            /*
                             * Setting the layout type to the type we point to reuses an available
                             * type unit, so we do not have to write are separate type unit.
                             */
                            layoutTypeSignature = pointerToEntry.getTypeSignature();
                        }

                        return new ForeignPointerTypeEntry(typeName, size, classOffset, typeSignature, layoutTypeSignature, superClass, fileEntry, loaderEntry, pointerToEntry);
                    }
                } else if (hostedType.isEnum()) {
                    return new EnumClassEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                    layoutTypeSignature, superClass, fileEntry, loaderEntry);
                } else if (hostedType.isInstanceClass()) {
                    return new ClassEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                    layoutTypeSignature, superClass, fileEntry, loaderEntry);
                } else if (hostedType.isInterface()) {
                    return new InterfaceClassEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                    layoutTypeSignature, superClass, fileEntry, loaderEntry);
                } else {
                    throw new RuntimeException("Unknown type kind " + hostedType.getName());
                }
            }
        }
    }

    private void logForeignTypeInfo(HostedType hostedType) {
        if (!isForeignPointerType(hostedType)) {
            // non pointer type must be an interface because an instance needs to be pointed to
            assert hostedType.isInterface();
            // foreign word types never have element info
            debug.log(DebugContext.VERBOSE_LEVEL, "Foreign word type %s", hostedType.toJavaName());
        } else {
            ElementInfo elementInfo = nativeLibs.findElementInfo(hostedType);
            logForeignPointerType(hostedType, elementInfo);
        }
    }

    private void logForeignPointerType(HostedType hostedType, ElementInfo elementInfo) {
        if (elementInfo == null) {
            // can happen for a generic (void*) pointer or a class
            if (hostedType.isInterface()) {
                debug.log(DebugContext.VERBOSE_LEVEL, "Foreign pointer type %s", hostedType.toJavaName());
            } else {
                debug.log(DebugContext.VERBOSE_LEVEL, "Foreign pointer type %s (class)", hostedType.toJavaName());
            }
        } else if (elementInfo instanceof PointerToInfo) {
            logPointerToInfo(hostedType, (PointerToInfo) elementInfo);
        } else if (elementInfo instanceof StructInfo) {
            if (elementInfo instanceof RawStructureInfo) {
                logRawStructureInfo(hostedType, (RawStructureInfo) elementInfo);
            } else {
                logStructInfo(hostedType, (StructInfo) elementInfo);
            }
        }
    }

    private void logPointerToInfo(HostedType hostedType, PointerToInfo pointerToInfo) {
        debug.log(DebugContext.VERBOSE_LEVEL, "Foreign pointer type %s %s", hostedType.toJavaName(), elementKind(pointerToInfo));
        assert hostedType.isInterface();
        int size = elementSize(pointerToInfo);
        boolean isUnsigned = pointerToInfo.isUnsigned();
        String typedefName = pointerToInfo.getTypedefName();
        debug.log("element size = %d", size);
        debug.log("%s", (isUnsigned ? "<unsigned>" : "<signed>"));
        if (typedefName != null) {
            debug.log("typedefname = %s", typedefName);
        }
        dumpElementInfo(pointerToInfo);
    }

    private void logStructInfo(HostedType hostedType, StructInfo structInfo) {
        debug.log(DebugContext.VERBOSE_LEVEL, "Foreign struct type %s %s", hostedType.toJavaName(), elementKind(structInfo));
        assert hostedType.isInterface();
        boolean isIncomplete = structInfo.isIncomplete();
        if (isIncomplete) {
            debug.log("<incomplete>");
        } else {
            debug.log("complete : element size = %d", elementSize(structInfo));
        }
        String typedefName = structInfo.getTypedefName();
        if (typedefName != null) {
            debug.log("    typedefName = %s", typedefName);
        }
        dumpElementInfo(structInfo);
    }

    private void logRawStructureInfo(HostedType hostedType, RawStructureInfo rawStructureInfo) {
        debug.log(DebugContext.VERBOSE_LEVEL, "Foreign raw struct type %s %s", hostedType.toJavaName(), elementKind(rawStructureInfo));
        assert hostedType.isInterface();
        debug.log("element size = %d", elementSize(rawStructureInfo));
        String typedefName = rawStructureInfo.getTypedefName();
        if (typedefName != null) {
            debug.log("    typedefName = %s", typedefName);
        }
        dumpElementInfo(rawStructureInfo);
    }

    private void dumpElementInfo(ElementInfo elementInfo) {
        if (elementInfo != null) {
            debug.log("Element Info {%n%s}", formatElementInfo(elementInfo));
        } else {
            debug.log("Element Info {}");
        }
    }

    private static String formatElementInfo(ElementInfo elementInfo) {
        StringBuilder stringBuilder = new StringBuilder();
        formatElementInfo(elementInfo, stringBuilder, 0);
        return stringBuilder.toString();
    }

    private static void formatElementInfo(ElementInfo elementInfo, StringBuilder stringBuilder, int indent) {
        indentElementInfo(stringBuilder, indent);
        formatSingleElement(elementInfo, stringBuilder);
        List<ElementInfo> children = elementInfo.getChildren();
        if (children == null || children.isEmpty()) {
            stringBuilder.append("\n");
        } else {
            stringBuilder.append(" {\n");
            for (ElementInfo child : children) {
                formatElementInfo(child, stringBuilder, indent + 1);
            }
            indentElementInfo(stringBuilder, indent);
            stringBuilder.append("}\n");
        }
    }

    private static void formatSingleElement(ElementInfo elementInfo, StringBuilder stringBuilder) {
        stringBuilder.append(ClassUtil.getUnqualifiedName(elementInfo.getClass()));
        stringBuilder.append(" : ");
        stringBuilder.append(elementName(elementInfo));
        if (elementInfo instanceof PropertyInfo<?>) {
            stringBuilder.append(" = ");
            formatPropertyInfo((PropertyInfo<?>) elementInfo, stringBuilder);
        }
        if (elementInfo instanceof AccessorInfo) {
            stringBuilder.append(" ");
            stringBuilder.append(((AccessorInfo) elementInfo).getAccessorKind());
        }
    }

    private static <T> void formatPropertyInfo(PropertyInfo<T> propertyInfo, StringBuilder stringBuilder) {
        stringBuilder.append(propertyInfo.getProperty());
    }

    private static void indentElementInfo(StringBuilder stringBuilder, int indent) {
        stringBuilder.append("  ".repeat(Math.max(0, indent + 1)));
    }

    @Override
    @SuppressWarnings("try")
    public FileEntry lookupFileEntry(ResolvedJavaType type) {
        Class<?> clazz = OriginalClassProvider.getJavaClass(type);
        SourceManager sourceManager = ImageSingletons.lookup(SourceManager.class);
        try (DebugContext.Scope s = debug.scope("DebugFileInfo", type)) {
            Path filePath = sourceManager.findAndCacheSource(type, clazz, debug);
            if (filePath != null) {
                return lookupFileEntry(filePath);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        // fallback to default file entry lookup
        return super.lookupFileEntry(type);
    }

    @Override
    public FileEntry lookupFileEntry(ResolvedJavaMethod method) {
        if (method instanceof HostedMethod hostedMethod && hostedMethod.getWrapped().getWrapped() instanceof SubstitutionMethod substitutionMethod) {
            // we always want to look up the file of the annotated method
            method = substitutionMethod.getAnnotated();
        }
        return super.lookupFileEntry(method);
    }

    private int getTypeSize(HostedType type) {
        switch (type) {
            case HostedInstanceClass hostedInstanceClass -> {
                /* We know the actual instance size in bytes. */
                return hostedInstanceClass.getInstanceSize();
            }
            case HostedArrayClass hostedArrayClass -> {
                /* Use the size of header common to all arrays of this type. */
                return getObjectLayout().getArrayBaseOffset(hostedArrayClass.getComponentType().getStorageKind());
            }
            case HostedInterface hostedInterface -> {
                /* Use the size of the header common to all implementors. */
                return getObjectLayout().getFirstFieldOffset();
            }
            case HostedPrimitiveType hostedPrimitiveType -> {
                /* Use the number of bytes needed to store the value. */
                JavaKind javaKind = hostedPrimitiveType.getStorageKind();
                return javaKind == JavaKind.Void ? 0 : javaKind.getByteCount();
            }
            default -> {
                return 0;
            }
        }
    }

    private long getClassOffset(HostedType type) {
        /*
         * Only query the heap for reachable types. These are guaranteed to have been seen by the
         * analysis and to exist in the shadow heap.
         */
        if (type.getWrapped().isReachable()) {
            NativeImageHeap.ObjectInfo objectInfo = heap.getObjectInfo(type.getHub());
            if (objectInfo != null) {
                return objectInfo.getOffset();
            }
        }
        return -1;
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
}
