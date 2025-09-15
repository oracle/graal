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

import static com.oracle.svm.interpreter.InterpreterStubSection.getCremaStubForVTableIndex;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeDynamicHubMetadata;
import com.oracle.svm.core.hub.RuntimeReflectionMetadata;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.hub.registry.AbstractClassRegistry;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.espresso.classfile.ParserField;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.ParserMethod;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
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
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CremaSupportImpl implements CremaSupport {
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
        } catch (UnsupportedFeatureException e) {
            /* GR-69550: Method has hosted type in signature */
            return;
        }
        InterpreterResolvedJavaMethod method = btiUniverse.getOrCreateMethod(analysisMethod);
        method.setNativeEntryPoint(new MethodPointer(analysisMethod));
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
            if (!analysisUniverse.hostVM().platformSupported((AnnotatedElement) wrappedField.getType())) {
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
    public void fillDynamicHubInfo(DynamicHub hub, CremaDispatchTable dispatchTable, List<Class<?>> transitiveSuperInterfaces, int[] interfaceIndicies) {
        CremaDispatchTableImpl table = (CremaDispatchTableImpl) dispatchTable;

        assert hub.getSuperHub() == DynamicHub.fromClass(table.superType());
        InterpreterResolvedObjectType superType = (InterpreterResolvedObjectType) hub.getSuperHub().getInterpreterType();
        InterpreterResolvedObjectType[] interfaces = new InterpreterResolvedObjectType[hub.getInterfaces().length];
        for (int i = 0; i < interfaces.length; i++) {
            interfaces[i] = (InterpreterResolvedObjectType) hub.getInterfaces()[i].getInterpreterType();
        }

        InterpreterResolvedJavaType componentType = null;
        DynamicHub componentHub = hub.getComponentHub();
        if (componentHub != null) {
            componentType = (InterpreterResolvedJavaType) componentHub.getInterpreterType();
        }
        CremaResolvedObjectType thisType = InterpreterResolvedObjectType.createForCrema(
                        table.getParserKlass(),
                        hub.getModifiers(),
                        componentType, superType, interfaces,
                        DynamicHub.toClass(hub));

        ParserKlass parserKlass = table.partialType.parserKlass;
        thisType.setConstantPool(new RuntimeInterpreterConstantPool(thisType, parserKlass.getConstantPool()));

        table.registerClass(thisType);

        // Methods
        thisType.setDeclaredMethods(table.declaredMethods());

        List<InterpreterResolvedJavaMethod> completeTable = table.cremaVTable(transitiveSuperInterfaces);
        thisType.setVtable(completeTable.toArray(InterpreterResolvedJavaMethod.EMPTY_ARRAY));

        long vTableBaseOffset = KnownOffsets.singleton().getVTableBaseOffset();
        long vTableEntrySize = KnownOffsets.singleton().getVTableEntrySize();
        int i = 0;
        for (InterpreterResolvedJavaMethod method : completeTable) {
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

        // Fields
        ParserField[] fields = table.getParserKlass().getFields();
        CremaResolvedJavaFieldImpl[] declaredFields = fields.length == 0 ? CremaResolvedJavaFieldImpl.EMPTY_ARRAY : new CremaResolvedJavaFieldImpl[fields.length];
        for (int j = 0; j < fields.length; j++) {
            ParserField f = fields[j];
            declaredFields[j] = CremaResolvedJavaFieldImpl.createAtRuntime(thisType, f, table.layout.getOffset(j));
        }
        thisType.setAfterFieldsOffset(table.layout().afterInstanceFieldsOffset());
        thisType.setDeclaredFields(declaredFields);

        // Done
        hub.setInterpreterType(thisType);

        hub.getCompanion().setHubMetadata(new RuntimeDynamicHubMetadata(thisType));
        hub.getCompanion().setReflectionMetadata(new RuntimeReflectionMetadata(thisType));
    }

    @Override
    public CremaDispatchTable getDispatchTable(ParserKlass parsed, Class<?> superClass, List<Class<?>> transitiveSuperInterfaces) {
        CremaPartialType partialType = new CremaPartialType(parsed, superClass, transitiveSuperInterfaces);
        try {
            if (Modifier.isInterface(parsed.getFlags())) {
                return new CremaInterfaceDispatchTableImpl(partialType);
            } else {
                Tables<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> tables = VTable.create(partialType,
                                false,
                                false,
                                true);
                return new CremaInstanceDispatchTableImpl(tables, partialType);
            }
        } catch (MethodTableException e) {
            throw new IncompatibleClassChangeError(e.getMessage());
        }
    }

    static final class CremaPartialType implements PartialType<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> {
        private final Class<?> superClass;
        private final ParserKlass parserKlass;
        private final List<CremaPartialMethod> declared;
        private final List<InterpreterResolvedJavaMethod> parentTable;
        private final EconomicMap<InterpreterResolvedJavaType, List<InterpreterResolvedJavaMethod>> interfacesData = EconomicMap.create(Equivalence.IDENTITY);

        private InterpreterResolvedObjectType thisJavaType;

        @SuppressWarnings("this-escape")
        CremaPartialType(ParserKlass parsed, Class<?> superClass, List<Class<?>> superInterfaces) {
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

    private abstract static class CremaDispatchTableImpl implements CremaDispatchTable {
        protected final CremaPartialType partialType;
        private final FieldLayout layout;

        CremaDispatchTableImpl(CremaPartialType partialType) {
            this.partialType = partialType;
            this.layout = FieldLayout.build(getParserKlass().getFields(), getSuperResolvedType().getAfterFieldsOffset());
        }

        public final void registerClass(InterpreterResolvedObjectType thisType) {
            partialType.thisJavaType = thisType;
        }

        @Override
        public int afterFieldsOffset(int superAfterFieldsOffset) {
            return layout.afterInstanceFieldsOffset();
        }

        @Override
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

        public abstract List<InterpreterResolvedJavaMethod> cremaVTable(List<Class<?>> intfList);
    }

    private static final class CremaInterfaceDispatchTableImpl extends CremaDispatchTableImpl {

        CremaInterfaceDispatchTableImpl(CremaPartialType partialType) {
            super(partialType);
        }

        @Override
        public List<InterpreterResolvedJavaMethod> cremaVTable(List<Class<?>> intfList) {
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

    private static final class CremaInstanceDispatchTableImpl extends CremaDispatchTableImpl {
        private final Tables<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> table;

        CremaInstanceDispatchTableImpl(Tables<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> table, CremaPartialType partialType) {
            super(partialType);
            this.table = table;
        }

        @Override
        public List<InterpreterResolvedJavaMethod> cremaVTable(List<Class<?>> intfList) {
            List<InterpreterResolvedJavaMethod> vtable = toSimpleTable(table.getVtable());
            List<InterpreterResolvedJavaMethod> result = new ArrayList<>(vtable);
            for (Class<?> intf : intfList) {
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
    public int getAfterFieldsOffset(DynamicHub hub) {
        return ((InterpreterResolvedObjectType) hub.getInterpreterType()).getAfterFieldsOffset();
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
    public Class<?> resolveOrThrow(JavaType unresolvedJavaType, ResolvedJavaType accessingClass) {
        try {
            Class<?> result = loadClass(unresolvedJavaType, (InterpreterResolvedJavaType) accessingClass);
            if (result == null) {
                throw new NoClassDefFoundError(getTypeName(unresolvedJavaType));
            } else {
                return result;
            }
        } catch (ClassNotFoundException e) {
            NoClassDefFoundError error = new NoClassDefFoundError(getTypeName(unresolvedJavaType));
            error.initCause(e);
            throw error;
        }
    }

    private static String getTypeName(JavaType unresolvedJavaType) {
        assert unresolvedJavaType.getElementalType().getName().startsWith("L");

        String internalName = unresolvedJavaType.getElementalType().getName();
        return internalName.substring(1, internalName.length() - 1);
    }

    @Override
    public Class<?> resolveOrNull(JavaType unresolvedJavaType, ResolvedJavaType accessingClass) {
        try {
            return loadClass(unresolvedJavaType, (InterpreterResolvedJavaType) accessingClass);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Class<?> loadClass(JavaType unresolvedJavaType, InterpreterResolvedJavaType accessingClass) throws ClassNotFoundException {
        ByteSequence type = ByteSequence.create(unresolvedJavaType.getName());
        Symbol<Type> symbolicType = SymbolsSupport.getTypes().getOrCreateValidType(type);
        AbstractClassRegistry registry = ClassRegistries.singleton().getRegistry(accessingClass.getJavaClass().getClassLoader());
        return registry.loadClass(symbolicType);
    }

    @Override
    public Object execute(ResolvedJavaMethod targetMethod, Object[] args) {
        return Interpreter.execute((InterpreterResolvedJavaMethod) targetMethod, args);
    }

    @Override
    public Object newInstance(ResolvedJavaMethod targetMethod, Object[] args) {
        return Interpreter.newInstance((InterpreterResolvedJavaMethod) targetMethod, args);
    }
}
