/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import static com.oracle.svm.core.hub.registry.AbstractRuntimeClassRegistry.UNINITIALIZED_DECLARING_CLASS_SENTINEL;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.MN_CALLER_SENSITIVE;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.MN_IS_CONSTRUCTOR;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.MN_IS_FIELD;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.MN_IS_METHOD;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.MN_REFERENCE_KIND_SHIFT;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_getField;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_getStatic;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeInterface;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeSpecial;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeStatic;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeVirtual;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_newInvokeSpecial;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_putField;
import static com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives_Constants.REF_putStatic;
import static com.oracle.svm.espresso.classfile.Constants.ACC_SUPER;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ACC_WRITTEN_FLAGS;
import static com.oracle.svm.espresso.shared.meta.SignaturePolymorphicIntrinsic.InvokeGeneric;
import static com.oracle.svm.interpreter.InterpreterStubSection.getCremaStubForVTableIndex;

import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.constraints.UnsupportedPlatformException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubTypeCheckUtil;
import com.oracle.svm.core.hub.DynamicHubTypeCheckUtil.TypeCheckData;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.hub.RuntimeDynamicHubMetadata;
import com.oracle.svm.core.hub.RuntimeReflectionMetadata;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.hub.registry.AbstractClassRegistry;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.hub.registry.TypeIDs;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.classfile.Constants;
import com.oracle.svm.espresso.classfile.JavaKind;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.espresso.classfile.ParserField;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.ParserMethod;
import com.oracle.svm.espresso.classfile.attributes.Attribute;
import com.oracle.svm.espresso.classfile.attributes.ConstantValueAttribute;
import com.oracle.svm.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.svm.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.svm.espresso.classfile.attributes.RecordAttribute;
import com.oracle.svm.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.svm.espresso.classfile.attributes.SourceFileAttribute;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.shared.meta.MethodHandleIntrinsics;
import com.oracle.svm.espresso.shared.meta.SignaturePolymorphicIntrinsic;
import com.oracle.svm.espresso.shared.resolver.CallSiteType;
import com.oracle.svm.espresso.shared.resolver.ResolvedCall;
import com.oracle.svm.espresso.shared.vtable.MethodTableException;
import com.oracle.svm.espresso.shared.vtable.PartialMethod;
import com.oracle.svm.espresso.shared.vtable.PartialType;
import com.oracle.svm.espresso.shared.vtable.Tables;
import com.oracle.svm.espresso.shared.vtable.VTable;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.interpreter.fieldlayout.FieldLayout;
import com.oracle.svm.interpreter.metadata.CremaResolvedJavaFieldImpl;
import com.oracle.svm.interpreter.metadata.CremaResolvedJavaMethodImpl;
import com.oracle.svm.interpreter.metadata.CremaResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CremaSupportImpl implements CremaSupport {
    private final MethodHandleIntrinsics<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> methodHandleIntrinsics = new MethodHandleIntrinsics<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public ResolvedJavaType createInterpreterType(DynamicHub hub, ResolvedJavaType type) {
        BuildTimeInterpreterUniverse btiUniverse = BuildTimeInterpreterUniverse.singleton();
        AnalysisType analysisType = (AnalysisType) type;
        AnalysisUniverse analysisUniverse = analysisType.getUniverse();

        /* query type from universe, maybe already exists (due to method creation) */
        InterpreterResolvedJavaType interpreterType = btiUniverse.getOrCreateType(analysisType);

        ResolvedJavaMethod[] declaredMethods = interpreterType.getDeclaredMethods(false);
        assert declaredMethods == null || declaredMethods == InterpreterResolvedJavaMethod.EMPTY_ARRAY : "should only be set once";

        if (analysisType.isPrimitive()) {
            return interpreterType;
        }

        List<InterpreterResolvedJavaMethod> methods = buildInterpreterMethods(analysisType, analysisUniverse, btiUniverse);
        List<InterpreterResolvedJavaField> fields = buildInterpreterFields(analysisType, analysisUniverse, btiUniverse);

        ((InterpreterResolvedObjectType) interpreterType).setDeclaredMethods(methods.toArray(InterpreterResolvedJavaMethod.EMPTY_ARRAY));
        ((InterpreterResolvedObjectType) interpreterType).setDeclaredFields(fields.toArray(InterpreterResolvedJavaField.EMPTY_ARRAY));

        return interpreterType;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static List<InterpreterResolvedJavaMethod> buildInterpreterMethods(AnalysisType analysisType, AnalysisUniverse analysisUniverse, BuildTimeInterpreterUniverse btiUniverse) {
        List<InterpreterResolvedJavaMethod> methods = new ArrayList<>();

        // add declared methods
        for (ResolvedJavaMethod wrappedMethod : analysisType.getWrapped().getDeclaredMethods(false)) {
            addSupportedElements(btiUniverse, analysisUniverse, methods, wrappedMethod);
        }
        // add declared constructors
        for (ResolvedJavaMethod wrappedMethod : analysisType.getWrapped().getDeclaredConstructors(false)) {
            addSupportedElements(btiUniverse, analysisUniverse, methods, wrappedMethod);
        }

        return methods;
    }

    private static void addSupportedElements(BuildTimeInterpreterUniverse btiUniverse, AnalysisUniverse analysisUniverse, List<InterpreterResolvedJavaMethod> methods,
                    ResolvedJavaMethod wrappedMethod) {
        if (!analysisUniverse.hostVM().platformSupported(wrappedMethod)) {
            /* ignore e.g. hosted methods */
            return;
        }

        AnalysisMethod analysisMethod;
        try {
            analysisMethod = analysisUniverse.lookup(wrappedMethod);
        } catch (DeletedElementException e) {
            /* deleted via substitution */
            return;
        } catch (UnsupportedPlatformException e) {
            /* Method has hosted type in signature */
            return;
        }
        InterpreterResolvedJavaMethod method = btiUniverse.getOrCreateMethod(analysisMethod);
        if (!method.isAbstract()) {
            method.setNativeEntryPoint(new MethodPointer(analysisMethod));
        }
        methods.add(method);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static List<InterpreterResolvedJavaField> buildInterpreterFields(AnalysisType analysisType, AnalysisUniverse analysisUniverse, BuildTimeInterpreterUniverse btiUniverse) {
        List<InterpreterResolvedJavaField> fields = new ArrayList<>();
        buildInterpreterFieldsFromArray(analysisUniverse, btiUniverse, analysisType.getWrapped().getInstanceFields(false), fields);
        buildInterpreterFieldsFromArray(analysisUniverse, btiUniverse, analysisType.getWrapped().getStaticFields(), fields);
        return fields;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void buildInterpreterFieldsFromArray(AnalysisUniverse analysisUniverse, BuildTimeInterpreterUniverse btiUniverse, ResolvedJavaField[] declaredFields,
                    List<InterpreterResolvedJavaField> fields) {
        for (ResolvedJavaField wrappedField : declaredFields) {
            if (wrappedField.isInternal()) {
                /* ignore internal fields */
                continue;
            }
            if (!analysisUniverse.hostVM().platformSupported(wrappedField)) {
                /* ignore e.g. hosted fields */
                continue;
            }
            if (wrappedField.getType() instanceof ResolvedJavaType resolvedFieldType && !analysisUniverse.hostVM().platformSupported(resolvedFieldType)) {
                /* ignore fields with unsupported types */
                continue;
            }
            AnalysisField analysisField;
            try {
                analysisField = analysisUniverse.lookup(wrappedField);
            } catch (DeletedElementException e) {
                /* deleted */
                continue;
            }
            InterpreterResolvedJavaField field = btiUniverse.getOrCreateField(analysisField);
            fields.add(field);
        }
    }

    @Override
    public DynamicHub createHub(ParserKlass parsed, ClassDefinitionInfo info, int typeID, String externalName, Module module, ClassLoader classLoader, Class<?> superClass,
                    Class<?>[] superInterfaces) {
        String simpleBinaryName = getSimpleBinaryName(parsed);
        String sourceFile = getSourceFile(parsed);
        // The declaring class must be computed lazily
        Object declaringClass = UNINITIALIZED_DECLARING_CLASS_SENTINEL;
        String classSignature = getClassSignature(parsed);
        boolean isValueBased = (parsed.getFlags() & Constants.ACC_VALUE_BASED) != 0;
        int modifiers = getClassModifiers(parsed);

        /*
         * The TypeCheckBuilder considers interface arrays as interfaces. Since we are dealing with
         * loading from class files, interface arrays need not be considered.
         */
        boolean isInterface = Modifier.isInterface(modifiers);
        boolean isRecord = Modifier.isFinal(modifiers) && superClass == Record.class && parsed.getAttribute(RecordAttribute.NAME) != null;
        // GR-62320 This should be set based on build-time and run-time arguments.
        boolean assertionsEnabled = true;
        boolean isSealed = isSealed(parsed);
        boolean declaresDefaultMethods = isInterface && declaresDefaultMethods(parsed);
        boolean hasDefaultMethods = declaresDefaultMethods || hasInheritedDefaultMethods(superClass, superInterfaces);
        boolean isLambdaFormHidden = false;
        boolean isProxyClass = false;
        short hubFlags = DynamicHub.makeFlags(false, isInterface, info.isHidden(), isRecord, assertionsEnabled, hasDefaultMethods, declaresDefaultMethods, isSealed, false, isLambdaFormHidden, false,
                        isProxyClass);

        Object interfacesEncoding = getInterfaceEncodings(superInterfaces);

        Class<?>[] transitiveSuperInterfaces = getSortedTransitiveSuperInterfaces(superClass, superInterfaces);
        AbstractCremaDispatchTable dispatchTable = createDispatchTable(parsed, superClass, transitiveSuperInterfaces);

        /*
         * Compute the type check slots depending on the kind of type
         * @formatter:off
         * ## Instance types
         * [Object.id, Super1.id, ..., Current.id, I1.id, off1, I2.id, off2, ...]
         * - display with all super classes from Object to self (included)
         * - followed by transitive interfaces (ordered by type id)
         * - each interface is followed by its itable offset
         * ## Interface types
         * a) Without interface hashing
         * [Object.id, I1.id, bad, I2.id, bad]
         * - display with Object
         * - followed by transitive interfaces (ordered by type id, including self)
         * - using 0xBADD0D1DL as interface starting index
         * b) With interface hashing
         * - Interfaces with interfaceIDs <= THRESHOLD are covered in per-type hash tables.
         *   hashTableEntry = interfaceID < 16 | iTableOffset
         * - Interfaces with interfaceIDs > THRESHOLD are covered by the type check slot array above.
         * @formatter:on
         */
        DynamicHub superHub = DynamicHub.fromClass(superClass);
        int interfaceID = isInterface ? TypeIDs.singleton().nextInterfaceId() : DynamicHub.NO_INTERFACE_ID;
        short numInterfacesTypes = (short) transitiveSuperInterfaces.length;
        short numClassTypes;
        short typeIDDepth;
        if (isInterface) {
            assert superHub.getNumClassTypes() == 1;
            typeIDDepth = -1;
            numClassTypes = 1;
        } else {
            int intDepth = superHub.getTypeIDDepth() + 1;
            int intNumClassTypes = superHub.getNumClassTypes() + 1;
            VMError.guarantee(intDepth == (short) intDepth, "Type depth overflow");
            VMError.guarantee(intNumClassTypes == (short) intNumClassTypes, "Num class types overflow");
            typeIDDepth = (short) intDepth;
            numClassTypes = (short) intNumClassTypes;
        }

        /* Compute type check data, which might be based on interface hashing. */
        DynamicHubTypeCheckUtil.TypeCheckData typeCheckData = computeTypeCheckData(typeID, isInterface, numClassTypes, numInterfacesTypes, superHub, dispatchTable, transitiveSuperInterfaces);

        int[] openTypeWorldTypeCheckSlots = typeCheckData.openTypeWorldTypeCheckSlots();
        int[] openTypeWorldInterfaceHashTable = typeCheckData.openTypeWorldInterfaceHashTable();
        int openTypeWorldInterfaceHashParam = typeCheckData.openTypeWorldInterfaceHashParam();
        // number of interfaces which are not covered by hashing and need to be iterated
        short numIterableInterfaces = typeCheckData.numIterableInterfaces();

        int afterFieldsOffset;
        if (isInterface) {
            afterFieldsOffset = 0;
        } else {
            afterFieldsOffset = dispatchTable.afterFieldsOffset();
        }

        /* Allocate DynamicHub. */
        int hubNumVTableEntries = dispatchTable.vtableLength();
        DynamicHub hub = DynamicHub.allocate(externalName, superHub, interfacesEncoding, null,
                        sourceFile, modifiers, hubFlags, classLoader, simpleBinaryName, module, declaringClass, classSignature,
                        typeID, interfaceID,
                        hasClassInitializer(parsed), numClassTypes, typeIDDepth, numIterableInterfaces, openTypeWorldTypeCheckSlots, openTypeWorldInterfaceHashTable, openTypeWorldInterfaceHashParam,
                        hubNumVTableEntries,
                        dispatchTable.getDeclaredInstanceReferenceFieldOffsets(), afterFieldsOffset, isValueBased, info);

        /* Allocate Crema type. */
        assert superHub == DynamicHub.fromClass(dispatchTable.superType());
        InterpreterResolvedObjectType superType = (InterpreterResolvedObjectType) superHub.getInterpreterType();
        InterpreterResolvedObjectType[] interfaces = getInterpreterInterfaces(hub);

        InterpreterResolvedJavaType componentType = null;
        DynamicHub componentHub = hub.getComponentHub();
        if (componentHub != null) {
            componentType = (InterpreterResolvedJavaType) componentHub.getInterpreterType();
        }
        CremaResolvedObjectType thisType = InterpreterResolvedObjectType.createForCrema(
                        dispatchTable.getParserKlass(),
                        hub.getModifiers(),
                        componentType, superType, interfaces,
                        DynamicHub.toClass(hub),
                        dispatchTable.layout.getStaticReferenceFieldCount(), dispatchTable.layout.getStaticPrimitiveFieldSize());

        ParserKlass parserKlass = dispatchTable.partialType.parserKlass;
        thisType.setConstantPool(new RuntimeInterpreterConstantPool(thisType, parserKlass));

        dispatchTable.registerClass(thisType);

        /* Set methods and vtable. */
        thisType.setDeclaredMethods(dispatchTable.declaredMethods());

        InterpreterResolvedJavaMethod[] completeVTable = dispatchTable.cremaVTable(transitiveSuperInterfaces).toArray(InterpreterResolvedJavaMethod.EMPTY_ARRAY);
        assert completeVTable.length == hubNumVTableEntries;
        thisType.setVtable(completeVTable);
        fillVTable(hub, completeVTable);

        // Fields
        ParserField[] fields = dispatchTable.getParserKlass().getFields();
        CremaResolvedJavaFieldImpl[] declaredFields = fields.length == 0 ? CremaResolvedJavaFieldImpl.EMPTY_ARRAY : new CremaResolvedJavaFieldImpl[fields.length];
        for (int j = 0; j < fields.length; j++) {
            ParserField f = fields[j];
            declaredFields[j] = CremaResolvedJavaFieldImpl.createAtRuntime(thisType, f, dispatchTable.layout.getOffset(j));
        }
        thisType.setAfterFieldsOffset(dispatchTable.layout().afterInstanceFieldsOffset());
        thisType.setDeclaredFields(declaredFields);

        initStaticFields(thisType, dispatchTable.getParserKlass().getFields());

        // Done
        hub.setInterpreterType(thisType);

        hub.getCompanion().setHubMetadata(new RuntimeDynamicHubMetadata(thisType));
        hub.getCompanion().setReflectionMetadata(new RuntimeReflectionMetadata(thisType));

        return hub;
    }

    private static InterpreterResolvedObjectType[] getInterpreterInterfaces(DynamicHub hub) {
        InterpreterResolvedObjectType[] interfaces = new InterpreterResolvedObjectType[hub.getInterfaces().length];
        for (int i = 0; i < interfaces.length; i++) {
            interfaces[i] = (InterpreterResolvedObjectType) hub.getInterfaces()[i].getInterpreterType();
        }
        return interfaces;
    }

    private static Object getInterfaceEncodings(Class<?>[] superInterfaces) {
        Object interfacesEncoding = null;
        if (superInterfaces.length == 1) {
            interfacesEncoding = DynamicHub.fromClass(superInterfaces[0]);
        } else if (superInterfaces.length > 1) {
            DynamicHub[] superHubs = new DynamicHub[superInterfaces.length];
            for (int i = 0; i < superHubs.length; ++i) {
                superHubs[i] = DynamicHub.fromClass(superInterfaces[i]);
            }
            interfacesEncoding = superHubs;
        }
        return interfacesEncoding;
    }

    private static Class<?>[] getSortedTransitiveSuperInterfaces(Class<?> superClass, Class<?>[] superInterfaces) {
        HashSet<Class<?>> map = new HashSet<>();
        Class<?> current = superClass;
        while (current != null) {
            for (Class<?> interfaceClass : current.getInterfaces()) {
                collectInterfaces(interfaceClass, map);
            }
            current = current.getSuperclass();
        }
        for (Class<?> interfaceClass : superInterfaces) {
            collectInterfaces(interfaceClass, map);
        }

        Class<?>[] result = map.toArray(new Class<?>[0]);
        Arrays.sort(result, Comparator.comparing(c -> DynamicHub.fromClass(c).getInterfaceID()));
        return result;
    }

    private static void collectInterfaces(Class<?> interfaceClass, HashSet<Class<?>> result) {
        // note that this is and must be called only _after_ class circularity detection
        if (result.add(interfaceClass)) {
            for (Class<?> superInterface : interfaceClass.getInterfaces()) {
                collectInterfaces(superInterface, result);
            }
        }
    }

    private static TypeCheckData computeTypeCheckData(int typeID, boolean typeIsInterface, short numClassTypes, short numInterfacesTypes, DynamicHub superHub,
                    AbstractCremaDispatchTable dispatchTable, Class<?>[] transitiveSuperInterfaces) {
        /*
         * The dispatch table will look like:
         * @formatter:off
         * [vtable..., itable(I1)..., itable(I2)...]
         *             ^ idx1         ^ idx2
         * @formatter:on
         * First compute idx* in iTableStartingIndices
         */
        int dispatchTableLength = dispatchTable.vtableLength();
        int[] iTableStartingIndices = new int[transitiveSuperInterfaces.length];
        for (int i = 0; i < transitiveSuperInterfaces.length; i++) {
            Class<?> iface = transitiveSuperInterfaces[i];
            iTableStartingIndices[i] = dispatchTableLength;
            dispatchTableLength += dispatchTable.itableLength(iface);
        }

        int[] interfaceIDs = new int[numInterfacesTypes];
        for (int i = 0; i < numInterfacesTypes; i++) {
            interfaceIDs[i] = DynamicHub.fromClass(transitiveSuperInterfaces[i]).getInterfaceID();
        }

        int[] typeHierarchy = new int[numClassTypes];
        System.arraycopy(superHub.getOpenTypeWorldTypeCheckSlots(), 0, typeHierarchy, 0, superHub.getNumClassTypes());

        if (!typeIsInterface) {
            // typeID is not yet in the type hierarchy derived from the super type.
            typeHierarchy[numClassTypes - 1] = typeID;
        }

        long vTableBaseOffset = KnownOffsets.singleton().getVTableBaseOffset();
        long vTableEntrySize = KnownOffsets.singleton().getVTableEntrySize();

        return DynamicHubTypeCheckUtil.computeOpenTypeWorldTypeCheckData(!typeIsInterface, typeHierarchy, interfaceIDs, iTableStartingIndices, vTableBaseOffset, vTableEntrySize);
    }

    private static void fillVTable(DynamicHub hub, InterpreterResolvedJavaMethod[] vtable) {
        long vTableBaseOffset = KnownOffsets.singleton().getVTableBaseOffset();
        long vTableEntrySize = KnownOffsets.singleton().getVTableEntrySize();
        int i = 0;
        for (InterpreterResolvedJavaMethod method : vtable) {
            long offset = vTableBaseOffset + i * vTableEntrySize;
            WordBase entry;
            if (method.hasNativeEntryPoint()) {
                entry = method.getNativeEntryPoint();
            } else {
                entry = getCremaStubForVTableIndex(i);
            }
            Word.objectToUntrackedPointer(hub).writeWord(Math.toIntExact(offset), entry);
            i++;
        }
    }

    private static AbstractCremaDispatchTable createDispatchTable(ParserKlass parsed, Class<?> superClass, Class<?>[] transitiveSuperInterfaces) {
        CremaPartialType partialType = new CremaPartialType(parsed, superClass, transitiveSuperInterfaces);
        try {
            if (Modifier.isInterface(parsed.getFlags())) {
                return new CremaInterfaceDispatchTableImpl(partialType);
            } else {
                /*
                 * GR-70607: once we handle vtable indicies better in crema we should enable
                 * mirandas.
                 */
                boolean addMirandas = false;
                var tables = VTable.create(partialType, false, false, addMirandas);
                return new CremaInstanceDispatchTableImpl(tables, partialType);
            }
        } catch (MethodTableException e) {
            throw new IncompatibleClassChangeError(e.getMessage());
        }
    }

    private static void initStaticFields(CremaResolvedObjectType type, ParserField[] fields) {
        // GR-61367: Currently done eagerly, but should be done during linking.
        InterpreterResolvedJavaField[] declaredFields = type.getDeclaredFields();
        for (int i = 0; i < declaredFields.length; i++) {
            InterpreterResolvedJavaField resolvedField = declaredFields[i];
            ParserField parsedField = fields[i];
            assert resolvedField.getSymbolicName() == parsedField.getName();
            assert resolvedField.isStatic() == parsedField.isStatic();
            if (resolvedField.isStatic()) {
                ConstantValueAttribute cva = null;
                for (Attribute attribute : parsedField.getAttributes()) {
                    if (attribute.getName() == ConstantValueAttribute.NAME) {
                        assert attribute instanceof ConstantValueAttribute;
                        cva = (ConstantValueAttribute) attribute;
                        break;
                    }
                }
                if (cva != null) {
                    int constantValueIndex = cva.getConstantValueIndex();
                    switch (parsedField.getKind()) {
                        case Boolean: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.INTEGER;
                            boolean c = type.getConstantPool().intAt(constantValueIndex) != 0;
                            InterpreterToVM.setFieldBoolean(c, type.getStaticStorage(true, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                        case Byte: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.INTEGER;
                            byte c = (byte) type.getConstantPool().intAt(constantValueIndex);
                            InterpreterToVM.setFieldByte(c, type.getStaticStorage(true, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                        case Short: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.INTEGER;
                            short c = (short) type.getConstantPool().intAt(constantValueIndex);
                            InterpreterToVM.setFieldShort(c, type.getStaticStorage(true, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                        case Char: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.INTEGER;
                            char c = (char) type.getConstantPool().intAt(constantValueIndex);
                            InterpreterToVM.setFieldChar(c, type.getStaticStorage(true, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                        case Int: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.INTEGER;
                            int c = type.getConstantPool().intAt(constantValueIndex);
                            InterpreterToVM.setFieldInt(c, type.getStaticStorage(true, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                        case Float: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.FLOAT;
                            float c = type.getConstantPool().floatAt(constantValueIndex);
                            InterpreterToVM.setFieldFloat(c, type.getStaticStorage(true, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                        case Long: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.LONG;
                            long c = type.getConstantPool().longAt(constantValueIndex);
                            InterpreterToVM.setFieldLong(c, type.getStaticStorage(true, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                        case Double: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.DOUBLE;
                            double c = type.getConstantPool().doubleAt(constantValueIndex);
                            InterpreterToVM.setFieldDouble(c, type.getStaticStorage(true, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                        case Object: {
                            assert type.getConstantPool().tagAt(constantValueIndex) == ConstantPool.Tag.STRING;
                            String c = type.getConstantPool().resolveStringAt(constantValueIndex);
                            InterpreterToVM.setFieldObject(c, type.getStaticStorage(false, resolvedField.getInstalledLayerNum()), resolvedField);
                            break;
                        }
                    }
                }
            }
        }
    }

    private static String getSimpleBinaryName(ParserKlass parsed) {
        InnerClassesAttribute innerClassesAttribute = (InnerClassesAttribute) parsed.getAttribute(InnerClassesAttribute.NAME);
        if (innerClassesAttribute == null) {
            return null;
        }
        ParserConstantPool pool = parsed.getConstantPool();
        for (int i = 0; i < innerClassesAttribute.entryCount(); i++) {
            InnerClassesAttribute.Entry entry = innerClassesAttribute.entryAt(i);
            int innerClassIndex = entry.innerClassIndex;
            if (innerClassIndex != 0) {
                if (pool.className(innerClassIndex) == parsed.getName()) {
                    if (entry.innerNameIndex == 0) {
                        break;
                    } else {
                        Symbol<?> innerName = pool.utf8At(entry.innerNameIndex, "inner class name");
                        return innerName.toString();
                    }
                }
            }
        }
        return null;
    }

    private static String getSourceFile(ParserKlass parsed) {
        String sourceFile = null;
        SourceFileAttribute sourceFileAttribute = (SourceFileAttribute) parsed.getAttribute(ParserSymbols.ParserNames.SourceFile);
        if (sourceFileAttribute != null) {
            sourceFile = parsed.getConstantPool().utf8At(sourceFileAttribute.getSourceFileIndex()).toString();
        }
        return sourceFile;
    }

    private static String getClassSignature(ParserKlass parsed) {
        String sourceFile = null;
        SignatureAttribute signatureAttribute = (SignatureAttribute) parsed.getAttribute(ParserSymbols.ParserNames.Signature);
        if (signatureAttribute != null) {
            sourceFile = parsed.getConstantPool().utf8At(signatureAttribute.getSignatureIndex()).toString();
        }
        return sourceFile;
    }

    private static boolean hasClassInitializer(ParserKlass parsed) {
        for (ParserMethod method : parsed.getMethods()) {
            if (method.getName() == ParserSymbols.ParserNames._clinit_) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaresDefaultMethods(ParserKlass parsed) {
        for (ParserMethod method : parsed.getMethods()) {
            int flags = method.getFlags();
            if (!Modifier.isAbstract(flags) && !Modifier.isStatic(flags)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSealed(ParserKlass parsed) {
        PermittedSubclassesAttribute permittedSubclasses = (PermittedSubclassesAttribute) parsed.getAttribute(PermittedSubclassesAttribute.NAME);
        return permittedSubclasses != null && permittedSubclasses.getClasses().length > 0;
    }

    private static boolean hasInheritedDefaultMethods(Class<?> superClass, Class<?>[] superInterfaces) {
        if (DynamicHub.fromClass(superClass).hasDefaultMethods()) {
            return true;
        }
        for (Class<?> superInterface : superInterfaces) {
            if (DynamicHub.fromClass(superInterface).hasDefaultMethods()) {
                return true;
            }
        }
        return false;
    }

    private static int getClassModifiers(ParserKlass parsed) {
        int modifiers = parsed.getFlags();
        InnerClassesAttribute innerClassesAttribute = (InnerClassesAttribute) parsed.getAttribute(InnerClassesAttribute.NAME);
        if (innerClassesAttribute != null) {
            ParserConstantPool pool = parsed.getConstantPool();
            for (int i = 0; i < innerClassesAttribute.entryCount(); i++) {
                InnerClassesAttribute.Entry entry = innerClassesAttribute.entryAt(i);
                if (entry.innerClassIndex != 0) {
                    Symbol<Name> innerClassName = pool.className(entry.innerClassIndex);
                    if (innerClassName.equals(parsed.getName())) {
                        modifiers = entry.innerClassAccessFlags;
                        break;
                    }
                }
            }
        }
        return modifiers & ~ACC_SUPER & JVM_ACC_WRITTEN_FLAGS;
    }

    static final class CremaPartialType implements PartialType<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> {
        private final Class<?> superClass;
        private final ParserKlass parserKlass;
        private final List<CremaPartialMethod> declared;
        private final List<InterpreterResolvedJavaMethod> parentTable;
        private final EconomicMap<InterpreterResolvedJavaType, List<InterpreterResolvedJavaMethod>> interfacesData = EconomicMap.create(Equivalence.IDENTITY);

        private InterpreterResolvedObjectType thisJavaType;

        @SuppressWarnings("this-escape")
        CremaPartialType(ParserKlass parsed, Class<?> superClass, Class<?>[] superInterfaces) {
            this.superClass = superClass;
            this.parserKlass = parsed;
            parentTable = computeParentTable(superClass);

            for (Class<?> intf : superInterfaces) {
                DynamicHub intfHub = DynamicHub.fromClass(intf);
                InterpreterResolvedObjectType interpreterType = (InterpreterResolvedObjectType) intfHub.getInterpreterType();
                // "vtable" contains the interface table prototype for interfaces
                interfacesData.put(interpreterType, Arrays.asList(interpreterType.getVtable()));
            }

            declared = new ArrayList<>();
            for (ParserMethod m : parsed.getMethods()) {
                declared.add(new CremaPartialMethod(this, m));
            }

        }

        private static List<InterpreterResolvedJavaMethod> computeParentTable(Class<?> superClass) {
            DynamicHub superHub = DynamicHub.fromClass(superClass);
            InterpreterResolvedObjectType superType = (InterpreterResolvedObjectType) superHub.getInterpreterType();
            InterpreterResolvedJavaMethod[] superVTableMirror = superType.getVtable();
            // Computes the size of the parent's vtable, without the trailing itables.
            long vTableEntrySize = KnownOffsets.singleton().getVTableEntrySize();
            long minOffset = superVTableMirror.length * vTableEntrySize;
            int[] typeSlots = superHub.getOpenTypeWorldTypeCheckSlots();
            for (int i = superHub.getNumClassTypes(); i < typeSlots.length; i += 2) {
                minOffset = Math.min(minOffset, typeSlots[i + 1]);
            }
            int superTableLen = Math.toIntExact(minOffset / vTableEntrySize);
            InterpreterResolvedJavaMethod[] superTable = Arrays.copyOf(superVTableMirror, superTableLen);
            return Arrays.asList(superTable);
        }

        @Override
        public List<InterpreterResolvedJavaMethod> getParentTable() {
            return parentTable;
        }

        @Override
        public EconomicMap<InterpreterResolvedJavaType, List<InterpreterResolvedJavaMethod>> getInterfacesData() {
            return interfacesData;
        }

        @Override
        public List<CremaPartialMethod> getDeclaredMethodsList() {
            return declared;
        }

        @Override
        public boolean sameRuntimePackage(InterpreterResolvedJavaType otherType) {
            // GR-62339 runtime packages
            return false;
        }

        @Override
        public Symbol<Name> getSymbolicName() {
            return parserKlass.getName();
        }

        public InterpreterResolvedObjectType getThisJavaType() {
            return thisJavaType;
        }

        @Override
        public String toString() {
            return "CremaPartialType<" + getSymbolicName() + ">";
        }
    }

    static final class CremaPartialMethod implements PartialMethod<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> {
        private final CremaPartialType partialType;
        private final ParserMethod m;
        private int vtableIndex = -1;
        private int itableIndex = -1;

        InterpreterResolvedJavaMethod resolved;

        CremaPartialMethod(CremaPartialType partialType, ParserMethod m) {
            this.partialType = partialType;
            this.m = m;
        }

        @Override
        public CremaPartialMethod withVTableIndex(int index) {
            assert vtableIndex == -1;
            this.vtableIndex = index;
            return this;
        }

        public CremaPartialMethod withITableIndex(int index) {
            assert itableIndex == -1;
            this.itableIndex = index;
            return this;
        }

        @Override
        public boolean isConstructor() {
            return m.getName() == ParserSymbols.ParserNames._init_;
        }

        @Override
        public boolean isClassInitializer() {
            return m.getName() == ParserSymbols.ParserNames._clinit_;
        }

        @Override
        public int getModifiers() {
            return m.getFlags();
        }

        @Override
        public Symbol<Name> getSymbolicName() {
            return m.getName();
        }

        @Override
        public Symbol<Signature> getSymbolicSignature() {
            return m.getSignature();
        }

        @Override
        public InterpreterResolvedJavaMethod asMethodAccess() {
            if (resolved != null) {
                return resolved;
            }
            int dispatchIndex = InterpreterResolvedJavaMethod.VTBL_NO_ENTRY;
            if (vtableIndex != -1) {
                assert itableIndex == -1;
                dispatchIndex = vtableIndex;
            } else if (itableIndex != -1) {
                dispatchIndex = itableIndex;
            }
            resolved = CremaResolvedJavaMethodImpl.create(partialType.getThisJavaType(), m, dispatchIndex);
            return resolved;
        }
    }

    private abstract static class AbstractCremaDispatchTable {
        protected final CremaPartialType partialType;
        private final FieldLayout layout;

        AbstractCremaDispatchTable(CremaPartialType partialType) {
            this.partialType = partialType;
            this.layout = FieldLayout.build(getParserKlass().getFields(), getSuperResolvedType().getAfterFieldsOffset());
        }

        public abstract int vtableLength();

        public abstract int itableLength(Class<?> iface);

        public final void registerClass(InterpreterResolvedObjectType thisType) {
            partialType.thisJavaType = thisType;
        }

        public int afterFieldsOffset() {
            return layout.afterInstanceFieldsOffset();
        }

        public int[] getDeclaredInstanceReferenceFieldOffsets() {
            return layout().getReferenceFieldsOffsets();
        }

        public final ParserKlass getParserKlass() {
            return partialType.parserKlass;
        }

        public final FieldLayout layout() {
            return layout;
        }

        public final Class<?> superType() {
            return partialType.superClass;
        }

        public final InterpreterResolvedObjectType getSuperResolvedType() {
            return (InterpreterResolvedObjectType) DynamicHub.fromClass(superType()).getInterpreterType();
        }

        public final InterpreterResolvedJavaMethod[] declaredMethods() {
            InterpreterResolvedJavaMethod[] result = new CremaResolvedJavaMethodImpl[partialType.getDeclaredMethodsList().size()];
            int i = 0;
            for (var m : partialType.getDeclaredMethodsList()) {
                result[i] = m.asMethodAccess();
                i++;
            }
            return result;
        }

        protected static List<InterpreterResolvedJavaMethod> toSimpleTable(List<PartialMethod<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField>> table) {
            List<InterpreterResolvedJavaMethod> result = new ArrayList<>();
            for (var pm : table) {
                result.add(pm.asMethodAccess());
            }
            return result;
        }

        public abstract List<InterpreterResolvedJavaMethod> cremaVTable(Class<?>[] interfaces);
    }

    private static final class CremaInterfaceDispatchTableImpl extends AbstractCremaDispatchTable {

        CremaInterfaceDispatchTableImpl(CremaPartialType partialType) {
            super(partialType);
        }

        @Override
        public List<InterpreterResolvedJavaMethod> cremaVTable(Class<?>[] interfaces) {
            List<InterpreterResolvedJavaMethod> itable = new ArrayList<>();
            for (CremaPartialMethod method : partialType.getDeclaredMethodsList()) {
                if (VTable.isVirtualEntry(method)) {
                    itable.add(method.withITableIndex(itable.size()).asMethodAccess());
                }
            }
            return itable;
        }

        @Override
        public int vtableLength() {
            return 0;
        }

        @Override
        public int itableLength(Class<?> iface) {
            return 0;
        }
    }

    private static final class CremaInstanceDispatchTableImpl extends AbstractCremaDispatchTable {
        private final Tables<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> table;

        CremaInstanceDispatchTableImpl(Tables<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> table, CremaPartialType partialType) {
            super(partialType);
            this.table = table;
        }

        @Override
        public List<InterpreterResolvedJavaMethod> cremaVTable(Class<?>[] interfaces) {
            List<InterpreterResolvedJavaMethod> vtable = toSimpleTable(table.getVtable());
            List<InterpreterResolvedJavaMethod> result = new ArrayList<>(vtable);
            for (Class<?> intf : interfaces) {
                List<InterpreterResolvedJavaMethod> itable = toSimpleTable(getItableFor(intf));
                result.addAll(itable);
            }
            return result;
        }

        @Override
        public int vtableLength() {
            return table.getVtable().size();
        }

        @Override
        public int itableLength(Class<?> iface) {
            return getItableFor(iface).size();
        }

        private List<PartialMethod<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField>> getItableFor(Class<?> iface) {
            return table.getItables().get((InterpreterResolvedJavaType) DynamicHub.fromClass(iface).getInterpreterType());
        }
    }

    @Override
    public Class<?> toClass(ResolvedJavaType resolvedJavaType) {
        /*
         * A resolved java type, at runtime, will always have a Java class. Hence, the below will
         * never throw the implicit NPE as checked by getJavaClass().
         */
        return ((InterpreterResolvedJavaType) resolvedJavaType).getJavaClass();
    }

    @Override
    public Class<?> resolveOrThrow(Symbol<Type> type, ResolvedJavaType accessingClass) {
        int arrayDimensions = TypeSymbols.getArrayDimensions(type);
        Symbol<Type> elementalType;
        if (arrayDimensions == 0) {
            elementalType = type;
        } else {
            elementalType = SymbolsSupport.getTypes().getOrCreateValidType(type.subSequence(arrayDimensions));
        }
        try {
            Class<?> result = loadClass(elementalType, (InterpreterResolvedJavaType) accessingClass);
            if (result == null) {
                throw new NoClassDefFoundError(elementalType.toString());
            }
            if (arrayDimensions > 0) {
                while (arrayDimensions-- > 0) {
                    result = DynamicHub.toClass(DynamicHub.fromClass(result).arrayType());
                }
            }
            return result;
        } catch (ClassNotFoundException e) {
            NoClassDefFoundError error = new NoClassDefFoundError(elementalType.toString());
            error.initCause(e);
            throw error;
        }
    }

    @Override
    public Class<?> resolveOrNull(Symbol<Type> type, ResolvedJavaType accessingClass) {
        int arrayDimensions = TypeSymbols.getArrayDimensions(type);
        Symbol<Type> elementalType;
        if (arrayDimensions == 0) {
            elementalType = type;
        } else {
            elementalType = SymbolsSupport.getTypes().getOrCreateValidType(type.subSequence(arrayDimensions));
        }
        try {
            Class<?> result = loadClass(elementalType, (InterpreterResolvedJavaType) accessingClass);
            if (result == null) {
                return null;
            }
            if (arrayDimensions > 0) {
                while (arrayDimensions-- > 0) {
                    result = DynamicHub.toClass(DynamicHub.fromClass(result).arrayType());
                }
            }
            return result;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class<?> loadClass(Symbol<Type> type, InterpreterResolvedJavaType accessingClass) throws ClassNotFoundException {
        JavaKind kind = TypeSymbols.getJavaKind(type);
        return switch (kind) {
            case Object -> {
                AbstractClassRegistry registry = ClassRegistries.singleton().getRegistry(accessingClass.getJavaClass().getClassLoader());
                yield registry.loadClass(type);
            }
            case Boolean -> boolean.class;
            case Byte -> byte.class;
            case Short -> short.class;
            case Char -> char.class;
            case Int -> int.class;
            case Long -> long.class;
            case Float -> float.class;
            case Double -> double.class;
            case Void -> void.class;
            default -> throw VMError.shouldNotReachHere(kind.toString());
        };
    }

    @Override
    public Class<?> findLoadedClass(Symbol<Type> type, ResolvedJavaType accessingClass) {
        AbstractClassRegistry registry = ClassRegistries.singleton().getRegistry(((InterpreterResolvedJavaType) accessingClass).getJavaClass().getClassLoader());
        return registry.findLoadedClass(type);
    }

    @Override
    public Object getStaticStorage(Class<?> cls, boolean primitives, int layerNum) {
        return ((InterpreterResolvedObjectType) DynamicHub.fromClass(cls).getInterpreterType()).getStaticStorage(primitives, layerNum);
    }

    @Override
    public Object getStaticStorage(ResolvedJavaField resolved) {
        InterpreterResolvedJavaField interpreterField = (InterpreterResolvedJavaField) resolved;
        return interpreterField.getDeclaringClass().getStaticStorage(resolved.getType().getJavaKind().isPrimitive(), interpreterField.getInstalledLayerNum());
    }

    @Override
    public Object execute(ResolvedJavaMethod targetMethod, Object[] args) {
        return Interpreter.execute((InterpreterResolvedJavaMethod) targetMethod, args);
    }

    @Override
    public Object allocateInstance(ResolvedJavaType type) {
        return InterpreterToVM.createNewReference((InterpreterResolvedJavaType) type);
    }

    @Override
    public ResolvedJavaMethod findMethodHandleIntrinsic(ResolvedJavaMethod signaturePolymorphicMethod, Symbol<Signature> signature) {
        return methodHandleIntrinsics.findIntrinsic((InterpreterResolvedJavaMethod) signaturePolymorphicMethod, signature, CremaRuntimeAccess.getInstance());
    }

    @Override
    public Target_java_lang_invoke_MemberName resolveMemberName(Target_java_lang_invoke_MemberName mn, Class<?> caller) {
        if (mn.resolved != null) {
            return mn;
        }
        Class<?> declaringClass = mn.clazz;
        Object type = mn.type;
        String name = mn.name;
        Symbol<Name> symbolicName = SymbolsSupport.getNames().lookup(name);
        if (symbolicName == null) {
            if (mn.isField()) {
                throw new NoSuchFieldError(name);
            } else {
                assert mn.isInvocable();
                throw new NoSuchMethodError(name);
            }
        }
        if (declaringClass.isPrimitive()) {
            return null;
        }
        InterpreterResolvedObjectType holder;
        if (declaringClass.isArray()) {
            holder = (InterpreterResolvedObjectType) DynamicHub.fromClass(Object.class).getInterpreterType();
        } else {
            holder = (InterpreterResolvedObjectType) DynamicHub.fromClass(declaringClass).getInterpreterType();
        }
        ByteSequence desc = asDescriptor(type);
        boolean doAccessChecks = false;
        // No constraints check on MemberName
        boolean doConstraintsChecks = false;
        InterpreterResolvedJavaType accessingType = null;
        if (caller != null && !caller.isPrimitive()) {
            accessingType = (InterpreterResolvedJavaType) DynamicHub.fromClass(caller).getInterpreterType();
        }
        int refKind = mn.getReferenceKind();
        if (mn.isField()) {
            Symbol<Type> t = SymbolsSupport.getTypes().lookupValidType(desc);
            if (t == null) {
                throw new NoSuchFieldError(name);
            }
            InterpreterResolvedJavaField field = CremaLinkResolver.resolveFieldSymbolOrThrow(CremaRuntimeAccess.getInstance(), accessingType, symbolicName, t, holder, doAccessChecks,
                            doConstraintsChecks);
            plantResolvedField(mn, field, refKind);
            return mn;
        }
        if (mn.isConstructor()) {
            if (symbolicName != ParserSymbols.ParserNames._init_) {
                throw new LinkageError();
            }
            refKind = REF_invokeSpecial;
        } else {
            VMError.guarantee(mn.isMethod());
        }
        SignaturePolymorphicIntrinsic mhMethodId = getSignaturePolymorphicIntrinsicID(holder, refKind, symbolicName);

        if (mhMethodId == InvokeGeneric) {
            // Can not resolve InvokeGeneric, as we would miss the invoker and appendix.
            throw new InternalError();
        }
        Symbol<Signature> sig = lookupSignature(desc, mhMethodId);
        InterpreterResolvedJavaMethod m = CremaLinkResolver.resolveMethodSymbol(CremaRuntimeAccess.getInstance(), accessingType, symbolicName, sig, holder, holder.isInterface(), doAccessChecks,
                        doConstraintsChecks);
        var resolvedCall = CremaLinkResolver.resolveCallSiteOrThrow(CremaRuntimeAccess.getInstance(),
                        accessingType, m, callSiteFromRefKind(refKind), holder);
        plantResolvedMethod(mn, resolvedCall);
        return mn;
    }

    private static void plantResolvedMethod(Target_java_lang_invoke_MemberName mn,
                    ResolvedCall<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> resolvedCall) {
        int methodFlags = getMethodFlags(resolvedCall);
        InterpreterResolvedJavaMethod target = resolvedCall.getResolvedMethod();
        mn.resolved = target;
        mn.flags = methodFlags;
        mn.clazz = target.getDeclaringClass().getJavaClass();
    }

    private static int getMethodFlags(ResolvedCall<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> resolvedCall) {
        InterpreterResolvedJavaMethod resolvedMethod = resolvedCall.getResolvedMethod();
        int flags = resolvedMethod.getModifiers();
        if (resolvedMethod.isCallerSensitive()) {
            flags |= MN_CALLER_SENSITIVE;
        }
        if (resolvedMethod.isConstructor() || resolvedMethod.isClassInitializer()) {
            flags |= MN_IS_CONSTRUCTOR;
            flags |= (REF_newInvokeSpecial << MN_REFERENCE_KIND_SHIFT);
            return flags;
        }
        flags |= MN_IS_METHOD;
        switch (resolvedCall.getCallKind()) {
            case STATIC:
                flags |= (REF_invokeStatic << MN_REFERENCE_KIND_SHIFT);
                break;
            case DIRECT:
                flags |= (REF_invokeSpecial << MN_REFERENCE_KIND_SHIFT);
                break;
            case VTABLE_LOOKUP:
                flags |= (REF_invokeVirtual << MN_REFERENCE_KIND_SHIFT);
                break;
            case ITABLE_LOOKUP:
                flags |= (REF_invokeInterface << MN_REFERENCE_KIND_SHIFT);
                break;
        }
        return flags;
    }

    private static void plantResolvedField(Target_java_lang_invoke_MemberName mn, InterpreterResolvedJavaField field, int refKind) {
        mn.resolved = field;
        mn.flags = getFieldFlags(refKind, field);
        mn.clazz = field.getDeclaringClass().getJavaClass();
    }

    private static int getFieldFlags(int refKind, InterpreterResolvedJavaField field) {
        int res = field.getModifiers();
        boolean isSetter = (refKind <= REF_putStatic) && !(refKind <= REF_getStatic);
        res |= MN_IS_FIELD | ((field.isStatic() ? REF_getStatic : REF_getField) << MN_REFERENCE_KIND_SHIFT);
        if (isSetter) {
            res += ((REF_putField - REF_getField) << MN_REFERENCE_KIND_SHIFT);
        }
        return res;
    }

    private static Symbol<Signature> lookupSignature(ByteSequence desc, SignaturePolymorphicIntrinsic iid) {
        Symbol<Signature> signature;
        if (iid != null) {
            signature = SymbolsSupport.getSignatures().getOrCreateValidSignature(desc);
        } else {
            signature = SymbolsSupport.getSignatures().lookupValidSignature(desc);
        }
        if (signature == null) {
            throw new NoSuchMethodError();
        }
        return signature;
    }

    private static CallSiteType callSiteFromRefKind(int refKind) {
        if (refKind == REF_invokeVirtual) {
            return CallSiteType.Virtual;
        }
        if (refKind == REF_invokeStatic) {
            return CallSiteType.Static;
        }
        if (refKind == REF_invokeSpecial || refKind == REF_newInvokeSpecial) {
            return CallSiteType.Special;
        }
        if (refKind == REF_invokeInterface) {
            return CallSiteType.Interface;
        }
        throw VMError.shouldNotReachHere("refKind: " + refKind);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+36/src/hotspot/share/prims/methodHandles.cpp#L735-L749")
    private static SignaturePolymorphicIntrinsic getSignaturePolymorphicIntrinsicID(InterpreterResolvedObjectType resolutionKlass, int refKind, Symbol<Name> name) {
        SignaturePolymorphicIntrinsic mhMethodId = null;
        if (ParserKlass.isSignaturePolymorphicHolderType(resolutionKlass.getSymbolicType())) {
            if (refKind == REF_invokeVirtual ||
                            refKind == REF_invokeSpecial ||
                            refKind == REF_invokeStatic) {
                SignaturePolymorphicIntrinsic iid = SignaturePolymorphicIntrinsic.getId(name, resolutionKlass);
                if (iid != null &&
                                ((refKind == REF_invokeStatic) == (iid.isStaticSignaturePolymorphic()))) {
                    mhMethodId = iid;
                }
            }
        }
        return mhMethodId;
    }

    private static ByteSequence asDescriptor(Object type) {
        return switch (type) {
            case MethodType mt -> methodTypeAsSignature(mt);
            case Class<?> c -> typeAsDescriptor(c);
            case String s -> ByteSequence.create(s);
            default -> throw VMError.shouldNotReachHere(type.getClass().toString());
        };
    }

    private static Symbol<Type> typeAsDescriptor(Class<?> c) {
        return ((InterpreterResolvedJavaType) DynamicHub.fromClass(c).getInterpreterType()).getSymbolicType();
    }

    private static ByteSequence methodTypeAsSignature(MethodType mt) {
        Class<?> returnType = mt.returnType();
        int len = 2;
        for (int i = 0; i < mt.parameterCount(); i++) {
            Class<?> parameterType = mt.parameterType(i);
            len += typeAsDescriptor(parameterType).length();
        }
        Symbol<Type> returnDescriptor = typeAsDescriptor(returnType);
        len += returnDescriptor.length();
        byte[] bytes = new byte[len];
        int pos = 0;
        bytes[pos++] = '(';
        for (int i = 0; i < mt.parameterCount(); i++) {
            Class<?> parameterType = mt.parameterType(i);
            Symbol<Type> paramType = typeAsDescriptor(parameterType);
            paramType.writeTo(bytes, pos);
            pos += paramType.length();
        }
        bytes[pos++] = ')';
        returnDescriptor.writeTo(bytes, pos);
        pos += returnDescriptor.length();
        assert pos == bytes.length;
        return ByteSequence.wrap(bytes);
    }

    @Override
    public Object invokeBasic(Target_java_lang_invoke_MemberName memberName, Object methodHandle, Object[] args) {
        // This is AOT-compiled code calling MethodHandle.invokeBasic
        InterpreterResolvedJavaMethod vmentry = InterpreterResolvedJavaMethod.fromMemberName(memberName);
        Object[] basicArgs = new Object[args.length + 1];
        basicArgs[0] = methodHandle;
        System.arraycopy(args, 0, basicArgs, 1, args.length);
        logIntrinsic("[from compiled] invokeBasic ", vmentry, basicArgs);
        try {
            return InterpreterToVM.dispatchInvocation(vmentry, basicArgs, false, false, false, false, true);
        } catch (SemanticJavaException e) {
            throw uncheckedThrow(e.getCause());
        }
    }

    @Override
    public Object linkToVirtual(Object[] args) {
        // This is AOT-compiled code calling MethodHandle.linkToVirtual
        Target_java_lang_invoke_MemberName mnTarget = (Target_java_lang_invoke_MemberName) args[args.length - 1];
        InterpreterResolvedJavaMethod target = InterpreterResolvedJavaMethod.fromMemberName(mnTarget);
        Object[] basicArgs = Arrays.copyOf(args, args.length - 1);
        logIntrinsic("[from compiled] linkToVirtual ", target, basicArgs);
        try {
            return InterpreterToVM.dispatchInvocation(target, basicArgs, true, false, false, false, true);
        } catch (SemanticJavaException e) {
            throw uncheckedThrow(e.getCause());
        }
    }

    @Override
    public Object linkToStatic(Object[] args) {
        // This is AOT-compiled code calling MethodHandle.linkToStatic
        Target_java_lang_invoke_MemberName mnTarget = (Target_java_lang_invoke_MemberName) args[args.length - 1];
        InterpreterResolvedJavaMethod target = InterpreterResolvedJavaMethod.fromMemberName(mnTarget);
        Object[] basicArgs = Arrays.copyOf(args, args.length - 1);
        logIntrinsic("[from compiled] linkToStatic ", target, basicArgs);
        try {
            return InterpreterToVM.dispatchInvocation(target, basicArgs, false, false, false, false, true);
        } catch (SemanticJavaException e) {
            throw uncheckedThrow(e.getCause());
        }
    }

    @Override
    public Object linkToSpecial(Object[] args) {
        // This is AOT-compiled code calling MethodHandle.linkToSpecial
        Target_java_lang_invoke_MemberName mnTarget = (Target_java_lang_invoke_MemberName) args[args.length - 1];
        InterpreterResolvedJavaMethod target = InterpreterResolvedJavaMethod.fromMemberName(mnTarget);
        Object[] basicArgs = Arrays.copyOf(args, args.length - 1);
        logIntrinsic("[from compiled] linkToSpecial ", target, basicArgs);
        try {
            return InterpreterToVM.dispatchInvocation(target, basicArgs, false, false, false, false, true);
        } catch (SemanticJavaException e) {
            throw uncheckedThrow(e.getCause());
        }
    }

    @Override
    public Object linkToInterface(Object[] args) {
        // This is AOT-compiled code calling MethodHandle.linkToInterface
        Target_java_lang_invoke_MemberName mnTarget = (Target_java_lang_invoke_MemberName) args[args.length - 1];
        InterpreterResolvedJavaMethod target = InterpreterResolvedJavaMethod.fromMemberName(mnTarget);
        Object[] basicArgs = Arrays.copyOf(args, args.length - 1);
        logIntrinsic("[from compiled] linkToInterface ", target, basicArgs);
        try {
            return InterpreterToVM.dispatchInvocation(target, basicArgs, true, false, false, true, true);
        } catch (SemanticJavaException e) {
            throw uncheckedThrow(e.getCause());
        }
    }

    private static void logIntrinsic(String value, InterpreterResolvedJavaMethod vmentry, Object[] basicArgs) {
        if (!InterpreterOptions.InterpreterTraceSupport.getValue() || !InterpreterOptions.InterpreterTrace.getValue()) {
            return;
        }
        Log.log().string(value).string(vmentry.toString()).string(", args=");
        for (int i = 0; i < basicArgs.length; i++) {
            Object arg = basicArgs[i];
            if (arg == null) {
                Log.log().string("null");
            } else {
                Log.log().string(arg.getClass().getName());
            }
            if (i < basicArgs.length - 1) {
                Log.log().string(", ");
            }
        }
        Log.log().newline();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException uncheckedThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
