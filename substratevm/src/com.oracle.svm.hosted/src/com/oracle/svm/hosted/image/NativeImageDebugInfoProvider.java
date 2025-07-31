/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.RawPointerTo;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.EnumClassEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.ForeignStructTypeEntry;
import com.oracle.objectfile.debugentry.InterfaceClassEntry;
import com.oracle.objectfile.debugentry.LoaderEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PointerToTypeEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UniqueShortNameProvider;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.debug.BFDNameProvider;
import com.oracle.svm.core.debug.SharedDebugInfoProvider;
import com.oracle.svm.core.debug.SubstrateDebugTypeEntrySupport;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
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
 * to be passed to an ObjectFile when generation of debug info is enabled at native image build
 * time.
 */
class NativeImageDebugInfoProvider extends SharedDebugInfoProvider {
    private final NativeImageHeap heap;
    private final NativeImageCodeCache codeCache;
    private final NativeLibraries nativeLibs;
    private final SourceManager debugInfoSourceManager;

    private final int primitiveStartOffset;
    private final int referenceStartOffset;
    private final Set<HostedMethod> allOverrides;

    NativeImageDebugInfoProvider(DebugContext debug, NativeImageCodeCache codeCache, NativeImageHeap heap, NativeLibraries nativeLibs, HostedMetaAccess metaAccess,
                    RuntimeConfiguration runtimeConfiguration) {
        super(debug, runtimeConfiguration, metaAccess);
        this.heap = heap;
        this.codeCache = codeCache;
        this.nativeLibs = nativeLibs;
        this.debugInfoSourceManager = new SourceManager();

        /* Offsets need to be adjusted relative to the heap base plus partition-specific offset. */
        NativeImageHeap.ObjectInfo primitiveFields = heap.getObjectInfo(StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields());
        NativeImageHeap.ObjectInfo objectFields = heap.getObjectInfo(StaticFieldsSupport.getCurrentLayerStaticObjectFields());
        primitiveStartOffset = (int) primitiveFields.getOffset();
        referenceStartOffset = (int) objectFields.getOffset();

        /* Calculate the set of all HostedMethods that are overrides. */
        allOverrides = heap.hUniverse.getMethods().stream()
                        .filter(HostedMethod::hasVTableIndex)
                        .flatMap(m -> Arrays.stream(m.getImplementations())
                                        .filter(Predicate.not(m::equals)))
                        .collect(Collectors.toSet());
    }

    @SuppressWarnings("unused")
    private static ResolvedJavaType getOriginal(ResolvedJavaType type) {
        /*
         * Unwrap then traverse through substitutions to the original. We don't want to get the
         * original type of LambdaSubstitutionType to keep the stable name.
         */
        ResolvedJavaType targetType = type;
        while (targetType instanceof WrappedJavaType wrappedJavaType) {
            targetType = wrappedJavaType.getWrapped();
        }

        if (targetType instanceof SubstitutionType substitutionType) {
            targetType = substitutionType.getOriginal();
        } else if (targetType instanceof InjectedFieldsType injectedFieldsType) {
            targetType = injectedFieldsType.getOriginal();
        }

        return targetType;
    }

    @SuppressWarnings("unused")
    private static ResolvedJavaMethod getAnnotatedOrOriginal(ResolvedJavaMethod method) {
        ResolvedJavaMethod targetMethod = method;
        while (targetMethod instanceof WrappedJavaMethod wrappedJavaMethod) {
            targetMethod = wrappedJavaMethod.getWrapped();
        }
        /*
         * This method is only used when identifying the modifiers or the declaring class of a
         * HostedMethod. Normally the method unwraps to the underlying JVMCI method which is the one
         * that provides bytecode to the compiler as well as, line numbers and local info. If we
         * unwrap to a SubstitutionMethod then we use the annotated method, not the JVMCI method
         * that the annotation refers to since that will be the one providing the bytecode etc used
         * by the compiler. If we unwrap to any other, custom substitution method we simply use it
         * rather than dereferencing to the original. The difference is that the annotated method's
         * bytecode will be used to replace the original and the debugger needs to use it to
         * identify the file and access permissions. A custom substitution may exist alongside the
         * original, as is the case with some uses for reflection. So, we don't want to conflate the
         * custom substituted method and the original. In this latter case the method code will be
         * synthesized without reference to the bytecode of the original. Hence, there is no
         * associated file and the permissions need to be determined from the custom substitution
         * method itself.
         */

        if (targetMethod instanceof SubstitutionMethod substitutionMethod) {
            targetMethod = substitutionMethod.getAnnotated();
        }

        return targetMethod;
    }

    @Override
    public String cachePath() {
        return SubstrateOptions.getDebugInfoSourceCacheRoot().toString();
    }

    /**
     * Logs information of {@link NativeImageHeap.ObjectInfo ObjectInfo}.
     *
     * @param data the data info to process
     */
    @Override
    @SuppressWarnings("try")
    protected void installDataInfo(Object data) {
        // log ObjectInfo data
        if (debug.isLogEnabled(DebugContext.INFO_LEVEL) && data instanceof NativeImageHeap.ObjectInfo objectInfo) {
            try (DebugContext.Scope s = debug.scope("DebugDataInfo")) {
                long offset = objectInfo.getOffset();
                long size = objectInfo.getSize();
                String typeName = objectInfo.getClazz().toJavaName();
                ImageHeapPartition partition = objectInfo.getPartition();

                debug.log(DebugContext.INFO_LEVEL, "Data: offset 0x%x size 0x%x type %s partition %s{%d}@%d provenance %s ", offset, size, typeName, partition.getName(), partition.getSize(),
                                partition.getStartOffset(), objectInfo);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } else {
            super.installDataInfo(data);
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

    /**
     * Fetch the typedef name of an element info for {@link StructInfo structs} or
     * {@link PointerToInfo pointer types}. Otherwise, we use the name of the element info.
     *
     * @param elementInfo the given element info
     * @return the typedef name
     */
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

    /**
     * Checks if a foreign type is a pointer type with {@link NativeLibraries}.
     *
     * @param type the given foreign type
     * @return true if the type is a foreign pointer type, otherwise false
     */
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

    /**
     * Creates a stream all types from the hosted universe.
     *
     * @return a stream of type in the hosted universe
     */
    @Override
    protected Stream<SharedType> typeInfo() {
        // null represents the header type
        return heap.hUniverse.getTypes().stream().map(type -> type);
    }

    /**
     * Creates a stream of all compilations with the corresponding hosted methods from the native
     * image code cache.
     *
     * @return a stream of compilations
     */
    @Override
    protected Stream<Pair<SharedMethod, CompilationResult>> codeInfo() {
        return codeCache.getOrderedCompilations().stream().map(pair -> Pair.create(pair.getLeft(), pair.getRight()));
    }

    /**
     * Creates a stream of all {@link NativeImageHeap.ObjectInfo objects} in the native image heap.
     *
     * @return a stream of native image heap objects.
     */
    @Override
    protected Stream<Object> dataInfo() {
        return heap.getObjects().stream().map(obj -> obj);
    }

    @Override
    protected long getCodeOffset(SharedMethod method) {
        assert method instanceof HostedMethod;
        return ((HostedMethod) method).getCodeAddressOffset();
    }

    /**
     * Processes type entries for {@link HostedType hosted types}.
     *
     * <p>
     * We need to process fields of {@link StructureTypeEntry structured types} after creating it to
     * make sure that it is available for the field types. Otherwise, this would create a cycle for
     * the type lookup.
     *
     * <p>
     * For a {@link ClassEntry} we also need to process interfaces and methods for the same reason
     * with the fields for structured types.
     *
     * @param type the {@code SharedType} of the type entry
     * @param typeEntry the {@code TypeEntry} to process
     */
    @Override
    protected void processTypeEntry(SharedType type, TypeEntry typeEntry) {
        assert type instanceof HostedType;
        HostedType hostedType = (HostedType) type;

        if (typeEntry instanceof StructureTypeEntry structureTypeEntry) {
            if (typeEntry instanceof ArrayTypeEntry arrayTypeEntry) {
                processArrayFields(hostedType, arrayTypeEntry);
            } else if (typeEntry instanceof ForeignStructTypeEntry foreignStructTypeEntry) {
                processForeignTypeFields(hostedType, foreignStructTypeEntry);
            } else {
                processFieldEntries(hostedType, structureTypeEntry);
            }

            if (typeEntry instanceof ClassEntry classEntry) {
                processInterfaces(hostedType, classEntry);
                processMethods(hostedType, classEntry);
            }
        }
    }

    /**
     * For processing methods of a type, we iterate over all its declared methods and lookup the
     * corresponding {@link MethodEntry} objects. This ensures that all declared methods of a type
     * are installed.
     *
     * @param type the given type
     * @param classEntry the type's {@code ClassEntry}
     */
    private void processMethods(HostedType type, ClassEntry classEntry) {
        for (HostedMethod method : type.getAllDeclaredMethods()) {
            MethodEntry methodEntry = lookupMethodEntry(method);

            if (debug.isLogEnabled()) {
                debug.log("typename %s adding %s method %s %s(%s)%n", classEntry.getTypeName(), methodEntry.getModifiersString(), methodEntry.getValueType().getTypeName(), methodEntry.getMethodName(),
                                formatParams(methodEntry.getParams()));
            }
        }
    }

    private static String formatParams(List<LocalEntry> paramInfo) {
        if (paramInfo.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
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

    /**
     * Produce a method name of a {@code HostedMethod} for the debug info.
     *
     * @param method method to produce a name for
     * @return method name for the debug info
     */
    @Override
    protected String getMethodName(SharedMethod method) {
        String name;
        if (method instanceof HostedMethod hostedMethod) {
            name = hostedMethod.getName();
            // replace <init> (method name of a constructor) with the class name
            if (hostedMethod.isConstructor()) {
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

    /**
     * Fetch a methods symbol produced by the {@link BFDNameProvider}.
     *
     * @param method method to get the symbol name for
     * @return symbol name of the method
     */
    @Override
    public String getSymbolName(SharedMethod method) {
        return NativeImage.localSymbolNameForMethod(method);
    }

    /**
     * Process interfaces from the hosted type and add the class entry as an implementor. This
     * ensures all interfaces are installed as debug entries.
     *
     * @param type the given type
     * @param classEntry the {@code ClassEntry} of the type
     */
    private void processInterfaces(HostedType type, ClassEntry classEntry) {
        for (HostedType interfaceType : type.getInterfaces()) {
            TypeEntry entry = lookupTypeEntry(interfaceType);
            if (entry instanceof InterfaceClassEntry interfaceClassEntry) {
                if (debug.isLogEnabled()) {
                    debug.log("typename %s adding interface %s%n", classEntry.getTypeName(), interfaceType.toJavaName());
                }
                interfaceClassEntry.addImplementor(classEntry);
            }
        }
    }

    /**
     * For arrays, we add a synthetic field for their length. This ensures that the length can be
     * exposed in the object files debug info.
     *
     * @param type the given array type
     * @param arrayTypeEntry the {@code ArrayTypeEntry} of the type
     */
    private void processArrayFields(HostedType type, ArrayTypeEntry arrayTypeEntry) {
        JavaKind arrayKind = type.getBaseType().getJavaKind();
        int headerSize = getObjectLayout().getArrayBaseOffset(arrayKind);
        int arrayLengthOffset = getObjectLayout().getArrayLengthOffset();
        int arrayLengthSize = getObjectLayout().sizeInBytes(JavaKind.Int);
        assert arrayLengthOffset + arrayLengthSize <= headerSize;
        arrayTypeEntry.addField(createSyntheticFieldEntry("len", arrayTypeEntry, (HostedType) metaAccess.lookupJavaType(JavaKind.Int.toJavaClass()), arrayLengthOffset, arrayLengthSize));
    }

    /**
     * Process {@link StructFieldInfo fields} for {@link StructInfo foreign structs}. Fields are
     * ordered by offset and added as {@link FieldEntry field entries} to the foreign type entry.
     *
     * @param type the given type
     * @param foreignStructTypeEntry the {@code ForeignStructTypeEntry} of the type
     */
    private void processForeignTypeFields(HostedType type, ForeignStructTypeEntry foreignStructTypeEntry) {
        ElementInfo elementInfo = nativeLibs.findElementInfo(type);
        if (elementInfo instanceof StructInfo) {
            elementInfo.getChildren().stream().filter(NativeImageDebugInfoProvider::isTypedField)
                            .map(StructFieldInfo.class::cast)
                            .sorted(Comparator.comparingInt(field -> field.getOffsetInfo().getProperty()))
                            .forEach(field -> {
                                HostedType fieldType = getFieldType(field);
                                FieldEntry fieldEntry = createFieldEntry(null, field.getName(), foreignStructTypeEntry, fieldType, field.getOffsetInfo().getProperty(),
                                                field.getSizeInBytes(), fieldTypeIsEmbedded(field), 0);
                                foreignStructTypeEntry.addField(fieldEntry);
                            });
        }
    }

    /**
     * Processes instance fields and static fields of hosted types to and adds {@link FieldEntry
     * field entries} to the structured type entry.
     *
     * @param type the given type
     * @param structureTypeEntry the {@code StructuredTypeEntry} of the type
     */
    private void processFieldEntries(HostedType type, StructureTypeEntry structureTypeEntry) {
        for (HostedField field : type.getInstanceFields(false)) {
            structureTypeEntry.addField(createFieldEntry(field, structureTypeEntry));
        }

        for (ResolvedJavaField field : type.getStaticFields()) {
            assert field instanceof HostedField;
            structureTypeEntry.addField(createFieldEntry((HostedField) field, structureTypeEntry));
        }
    }

    /**
     * Creates a new field entry for a hosted field.
     *
     * @param field the given field
     * @param ownerType the structured type owning the hosted field
     * @return a {@code FieldEntry} representing the hosted field
     */
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

    /**
     * Creates an unprocessed type entry. The type entry contains all static information, which is
     * its name, size, classOffset, loader entry and type signatures. For {@link ClassEntry} types,
     * it also contains the superclass {@code ClassEntry}.
     *
     * <p>
     * The returned type entry does not hold information on its fields, methods, and interfaces
     * implementors. This information is patched later in {@link #processTypeEntry}. This is done to
     * avoid cycles in the type entry lookup.
     *
     * @param type the {@code SharedType} to process
     * @return an unprocessed type entry
     */
    @Override
    protected TypeEntry createTypeEntry(SharedType type) {
        assert type instanceof HostedType;
        HostedType hostedType = (HostedType) type;

        String typeName = hostedType.toJavaName();
        int size = getTypeSize(hostedType);
        long classOffset = getClassOffset(hostedType);
        LoaderEntry loaderEntry = lookupLoaderEntry(hostedType);
        String loaderName = loaderEntry.loaderId();
        long typeSignature = getTypeSignature(typeName + loaderName);
        long compressedTypeSignature = useHeapBase ? getTypeSignature(INDIRECT_PREFIX + typeName + loaderName) : typeSignature;

        if (hostedType.isPrimitive()) {
            JavaKind kind = hostedType.getStorageKind();
            if (debug.isLogEnabled()) {
                debug.log("typename %s (%d bits)%n", typeName, kind == JavaKind.Void ? 0 : kind.getBitCount());
            }
            return new PrimitiveTypeEntry(typeName, size, classOffset, typeSignature, kind);
        } else {
            /*
             * this is a structured type (array or class entry), or a foreign type entry (uses the
             * layout signature even for not structured types)
             */
            long layoutTypeSignature = getTypeSignature(LAYOUT_PREFIX + typeName + loaderName);
            if (hostedType.isArray()) {
                TypeEntry elementTypeEntry = lookupTypeEntry(hostedType.getComponentType());
                if (debug.isLogEnabled()) {
                    debug.log("typename %s element type %s base size %d length offset %d%n", typeName, elementTypeEntry.getTypeName(),
                                    getObjectLayout().getArrayBaseOffset(hostedType.getComponentType().getStorageKind()), getObjectLayout().getArrayLengthOffset());
                }
                return new ArrayTypeEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                layoutTypeSignature, elementTypeEntry, loaderEntry);
            } else {
                // this is a class entry or a foreign type entry
                ClassEntry superClass = hostedType.getSuperclass() == null ? null : (ClassEntry) lookupTypeEntry(hostedType.getSuperclass());

                if (debug.isLogEnabled() && superClass != null) {
                    debug.log("typename %s adding super %s%n", typeName, superClass.getTypeName());
                }

                FileEntry fileEntry = lookupFileEntry(hostedType);
                if (isForeignWordType(hostedType)) {
                    /*
                     * A foreign type is either a generic word type, struct type, or a pointer.
                     */
                    if (debug.isLogEnabled()) {
                        logForeignTypeInfo(hostedType);
                    }
                    /*
                     * Foreign types are already produced after analysis to place them into the
                     * image if needed for run-time debug info generation. Fetch type entry from the
                     * image singleton.
                     */
                    TypeEntry foreignTypeEntry = SubstrateDebugTypeEntrySupport.singleton().getTypeEntry(typeSignature);

                    // update class offset if the class object is in the heap
                    foreignTypeEntry.setClassOffset(classOffset);
                    if (foreignTypeEntry instanceof PointerToTypeEntry pointerToTypeEntry && pointerToTypeEntry.getPointerTo() == null) {
                        // fix-up void pointers
                        pointerToTypeEntry.setPointerTo(lookupTypeEntry(voidType));
                    }

                    return foreignTypeEntry;
                } else if (hostedType.isEnum()) {
                    // Fetch typedef name for c enum types and skip the generic 'int' typedef name.
                    String typedefName = "";
                    ElementInfo elementInfo = nativeLibs.findElementInfo(hostedType);
                    if (elementInfo instanceof EnumInfo enumInfo && !enumInfo.getName().equals("int")) {
                        typedefName = enumInfo.getName();
                    }
                    return new EnumClassEntry(typeName, size, classOffset, typeSignature, compressedTypeSignature,
                                    layoutTypeSignature, superClass, fileEntry, loaderEntry, typedefName);
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

    /**
     * Processes a word base analysis type into a type entry for use during debug info generation.
     * Creates other type entries recursively if needed.
     *
     * @param nativeLibs The NativeLibraries used to fetch element infos.
     * @param metaAccess The AnalysisMetaAccess used to lookup referenced analysis types.
     * @param type The type to produce a debug info type entry for.
     * @return The type entry for the given type.
     */
    public static TypeEntry processElementInfo(NativeLibraries nativeLibs, AnalysisMetaAccess metaAccess, AnalysisType type) {
        assert nativeLibs.isWordBase(type);

        ElementInfo elementInfo = nativeLibs.findElementInfo(type);
        assert elementInfo == null || elementInfo.getAnnotatedElement() instanceof ResolvedJavaType;

        SizableInfo.ElementKind elementKind = elementInfo instanceof SizableInfo ? ((SizableInfo) elementInfo).getKind() : null;

        String typeName = type.toJavaName();
        int size = elementSize(elementInfo);
        // We need the loader name here to match the type signature generated later for looking up
        // type entries.
        String loaderName = UniqueShortNameProvider.singleton().uniqueShortLoaderName(type.getJavaClass().getClassLoader());
        long typeSignature = getTypeSignature(typeName + loaderName);

        // Reuse already created type entries.
        TypeEntry existingTypeEntry = SubstrateDebugTypeEntrySupport.singleton().getTypeEntry(typeSignature);
        if (existingTypeEntry != null) {
            return existingTypeEntry;
        }

        switch (elementInfo) {
            case PointerToInfo pointerToInfo -> {
                /*
                 * This must be a pointer. If the target type is known use it to declare the pointer
                 * type, otherwise default to 'void *'
                 */
                TypeEntry pointerToEntry = null;
                if (elementKind == SizableInfo.ElementKind.POINTER) {
                    /*
                     * any target type for the pointer will be defined by a CPointerTo or
                     * RawPointerTo annotation
                     */
                    AnalysisType pointerTo = null;
                    CPointerTo cPointerTo = type.getAnnotation(CPointerTo.class);
                    if (cPointerTo != null) {
                        pointerTo = metaAccess.lookupJavaType(cPointerTo.value());
                    }
                    RawPointerTo rawPointerTo = type.getAnnotation(RawPointerTo.class);
                    if (rawPointerTo != null) {
                        pointerTo = metaAccess.lookupJavaType(rawPointerTo.value());
                    }

                    pointerToEntry = processElementInfo(nativeLibs, metaAccess, pointerTo);
                } else if (elementKind == SizableInfo.ElementKind.INTEGER || elementKind == SizableInfo.ElementKind.FLOAT) {
                    pointerToEntry = createNativePrimitiveType(pointerToInfo);
                }

                if (pointerToEntry != null) {
                    // store pointer to entry in an image singleton
                    SubstrateDebugTypeEntrySupport.singleton().addTypeEntry(pointerToEntry);
                }

                // pointer to entry may be null, e.g. if no element info was found for the pointed
                // to type
                // we create the type entry from the corresponding hosted type later
                return new PointerToTypeEntry(typeName, size, -1, typeSignature, pointerToEntry);
            }
            case StructInfo structInfo -> {
                long layoutTypeSignature = getTypeSignature(LAYOUT_PREFIX + typeName + loaderName);
                ForeignStructTypeEntry parentEntry = null;
                for (AnalysisType interfaceType : type.getInterfaces()) {
                    ElementInfo otherInfo = nativeLibs.findElementInfo(interfaceType);
                    if (otherInfo instanceof StructInfo) {
                        TypeEntry otherTypeEntry = processElementInfo(nativeLibs, metaAccess, interfaceType);
                        assert otherTypeEntry instanceof ForeignStructTypeEntry;
                        parentEntry = (ForeignStructTypeEntry) otherTypeEntry;
                        // store parent entry in an image singleton
                        SubstrateDebugTypeEntrySupport.singleton().addTypeEntry(parentEntry);
                    }
                }

                String typedefName = typedefName(structInfo);
                if (typedefName == null) {
                    // create a synthetic typedef name from the types name
                    typedefName = "_" + typeName;
                }
                if (typedefName.startsWith("struct ")) {
                    // remove the struct keyword from the typename to provide uniform type names for
                    // struct types
                    typedefName = typedefName.substring("struct ".length());
                }

                // typedefName is the name of the c struct the Java type annotates
                // no field processing here, this is handled later
                return new ForeignStructTypeEntry(typeName, size, -1, typeSignature, layoutTypeSignature, typedefName, parentEntry);
            }
            case null, default -> {
                /*
                 * EnumInfo should not reach here because it is no word base type. Create a pointer
                 * to a generic word type or void.
                 */
                size = ConfigurationValues.getTarget().wordSize;
                TypeEntry pointerToEntry = null;

                // create a generic word type as base type or a void* if it is a pointer type
                if (!nativeLibs.isPointerBase(type)) {
                    int genericWordSize = ConfigurationValues.getTarget().wordSize;
                    int genericWordBits = genericWordSize * 8;
                    String genericWordName = "uint" + genericWordBits + "_t";
                    long genericWordTypeSignature = getTypeSignature(genericWordName);
                    pointerToEntry = new PrimitiveTypeEntry(genericWordName, genericWordSize, -1, genericWordTypeSignature, genericWordBits, true, false, true);
                    SubstrateDebugTypeEntrySupport.singleton().addTypeEntry(pointerToEntry);
                }

                return new PointerToTypeEntry(typeName, size, -1, typeSignature, pointerToEntry);
            }
        }
    }

    private static TypeEntry createNativePrimitiveType(PointerToInfo pointerToInfo) {
        // process pointer to info into primitive types
        assert pointerToInfo.getKind() == SizableInfo.ElementKind.INTEGER || pointerToInfo.getKind() == SizableInfo.ElementKind.FLOAT;
        String typeName = pointerToInfo.getName();
        int classOffset = -1;
        // Use the foreign prefix to make sure foreign primitives get a type signature
        // different from Java primitives.
        // e.g. Java char vs C char
        long typeSignature = getTypeSignature(FOREIGN_PREFIX + typeName);
        int size = pointerToInfo.getSizeInBytes();
        int bitCount = size * 8;
        boolean isNumericInteger = pointerToInfo.getKind() == SizableInfo.ElementKind.INTEGER;
        boolean isNumericFloat = pointerToInfo.getKind() == SizableInfo.ElementKind.FLOAT;
        boolean isUnsigned = pointerToInfo.isUnsigned();

        return new PrimitiveTypeEntry(typeName, size, classOffset, typeSignature, bitCount, isNumericInteger, isNumericFloat, isUnsigned);
    }

    /* The following methods provide some logging for foreign type entries. */
    private void logForeignTypeInfo(HostedType hostedType) {
        if (!isForeignPointerType(hostedType)) {
            // foreign non-pointer word types never have element info
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

    /**
     * Lookup the file entry of a {@code ResolvedJavaType}.
     *
     * <p>
     * First tries to find the source file using the {@link SourceManager}. If no file was found, a
     * file name is generated from the full class name.
     *
     * @param type the given {@code ResolvedJavaType}
     * @return the {@code FileEntry} for the type
     */
    @Override
    @SuppressWarnings("try")
    public FileEntry lookupFileEntry(ResolvedJavaType type) {
        Class<?> clazz = OriginalClassProvider.getJavaClass(type);
        try (DebugContext.Scope s = debug.scope("DebugFileInfo", type)) {
            Path filePath = debugInfoSourceManager.findAndCacheSource(type, clazz, debug);
            if (filePath != null) {
                return lookupFileEntry(filePath);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        // fallback to default file entry lookup
        return super.lookupFileEntry(type);
    }

    /**
     * Lookup a {@code FileEntry} for a {@code HostedMethod}. For a {@link SubstitutionMethod}, this
     * uses the source file of the annotated method.
     *
     * @param method the given {@code ResolvedJavaMethod}
     * @return the {@code FilEntry} for the method
     */
    @Override
    public FileEntry lookupFileEntry(ResolvedJavaMethod method) {
        ResolvedJavaMethod targetMethod = method;
        if (targetMethod instanceof HostedMethod hostedMethod && hostedMethod.getWrapped().getWrapped() instanceof SubstitutionMethod substitutionMethod) {
            // we always want to look up the file of the annotated method
            targetMethod = substitutionMethod.getAnnotated();
        }
        return super.lookupFileEntry(targetMethod);
    }

    private static int getTypeSize(HostedType type) {
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
    @Override
    public long objectOffset(JavaConstant constant) {
        assert constant.getJavaKind() == JavaKind.Object && !constant.isNull() : "invalid constant for object offset lookup";
        NativeImageHeap.ObjectInfo objectInfo = heap.getConstantInfo(constant);
        if (objectInfo != null) {
            return objectInfo.getOffset();
        }
        return -1;
    }
}
