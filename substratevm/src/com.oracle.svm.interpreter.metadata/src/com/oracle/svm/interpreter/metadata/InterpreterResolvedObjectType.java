/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.core.BuildPhaseProvider.AfterAnalysis;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.snippets.OpenTypeWorldDispatchTableSnippets;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.espresso.classfile.ClassfileParser;
import com.oracle.svm.espresso.classfile.ClassfileStream;
import com.oracle.svm.espresso.classfile.ParserException;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.classfile.descriptors.ValidationException;
import com.oracle.svm.espresso.shared.meta.TypeAccess;
import com.oracle.svm.espresso.shared.vtable.TableEntry;
import com.oracle.svm.espresso.shared.vtable.VTable;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The interpreter's representation for all reference types: normal classes, interfaces, and array
 * classes. Primitive types are represented by {@link InterpreterResolvedPrimitiveType}.
 */
public class InterpreterResolvedObjectType extends InterpreterResolvedJavaType {
    // Important note: This is, in general, NOT equal to `getHub().getModifiers()`.
    private final char modifiers;
    private final InterpreterResolvedJavaType componentType;
    private final InterpreterResolvedObjectType superclass;
    private final InterpreterResolvedObjectType[] interfaces;
    // Internal class names from the PermittedSubclasses attribute, or null for non-sealed types.
    private final Symbol<Name>[] permittedSubclassNames;
    private InterpreterResolvedJavaMethod[] declaredMethods;
    protected InterpreterResolvedJavaField[] declaredFields;
    private int afterFieldsOffset;

    // Populated after analysis.
    private InterpreterConstantPool constantPool;

    @Platforms(Platform.HOSTED_ONLY.class) private ResolvedJavaType originalType;

    private final String sourceFileName;

    /**
     * Holds the interpreter-side dispatch table for this type.
     * <p>
     * <h2>Classes</h2>
     * For non-interface classes, this dispatch table consists of 4 parts:
     * <ul>
     * <li>The inherited superclass vtable. Entries in this part may be overridden by this class'
     * declared method if applicable.</li>
     * <li>The {@link VTable#isVirtualEntry(TableEntry) virtual} declared methods of the current
     * class. Some of these declared methods may not be appended, if they override a method in the
     * super's table that has equivalent access control.</li>
     * <li>The implicit interface methods, which are methods declared in any superinterface (or their
     * superinterfaces) that are not implemented by this class or its superclasses.</li>
     * <li>The concatenated interface tables.</li>
     * </ul>
     * <p>
     * The dispatch table for non-interface classes therefore has this shape:
     *
     * <pre>
     * index:  0        superLen      mirandaMethodsStart   classVtableLength      vtable.length
     *         |           |                   |                     |                   |
     *         v           v                   v                     v                   v
     *         +-----------+-------------------+---------------------+-------------------+
     *         | super's   | holder's declared | implicit interface  | concatenated      |
     *         | class     | methods appended  | methods appended    | itables           |
     *         | vtable    | to class vtable   | to class vtable     |                   |
     *         +-----------+-------------------+---------------------+-------------------+
     *
     * superLen = holder.getSuperClass().getClassVtableLength()
     * </pre>
     * <p>
     * Note: Entries in the mirandas or the itables may be {@code failing} methods if the selection
     * logic should fail. These are synthetic internal methods that we create to represent such
     * failures, and are methods that immediately throw. Though such methods advertise this holder as
     * their declaring class, they do not appear in the declared methods array.
     * <p>
     * <h2>Interfaces</h2>
     * For interfaces, the table holds the interface dispatch table prototype rather than a class
     * vtable plus itables layout.
     * <p>
     * Here is its shape:
     *
     * <pre>
     * index:  0                                    vtable.length
     *         |                                          |
     *         v                                          v
     *         +------------------------------------------+
     *         | holder's declared | failing implicit     |
     *         | methods appended  | interface methods    |
     *         | to table          | appended to table    |
     *         +------------------------------------------+
     *
     * classVtableLength == 0
     * mirandaMethodsStart == UNKNOWN
     * </pre>
     * <p>
     * Note: Interfaces do not expose the concept of implicit interface methods, but a selection
     * conflict may still arise from their superinterfaces. In this particular case, like for the
     * concrete class case, we create a synthetic internal method that we add to the declared methods
     * and the dispatch table prototype.
     * <p>
     * Unlike for concrete classes, such failing methods do appear in the declared methods of
     * interfaces, such that they can be found and selected for {@code INVOKESPECIAL} call sites.
     * These entries are however marked as {@link InterpreterResolvedJavaMethod#isInternal()
     * internal}, such that they cannot be reflected upon.
     */
    public static class VTableHolder extends AbstractList<InterpreterResolvedJavaMethod> {
        public static final int UNKNOWN = -1;

        @UnknownObjectField(availability = AfterAnalysis.class) //
        public InterpreterResolvedObjectType holder;
        @UnknownObjectField(availability = AfterAnalysis.class) //
        public InterpreterResolvedJavaMethod[] vtable;

        public int classVtableLength;
        public int mirandaMethodsStart;

        public VTableHolder(InterpreterResolvedObjectType holder, InterpreterResolvedJavaMethod[] vtable, int classVtableLength, int mirandaMethodsStart) {
            this.holder = holder;
            this.vtable = vtable;
            this.classVtableLength = classVtableLength;
            this.mirandaMethodsStart = mirandaMethodsStart;
        }

        @Override
        public InterpreterResolvedJavaMethod get(int index) {
            return vtable[index];
        }

        @Override
        public int size() {
            return vtable.length;
        }

        public List<InterpreterResolvedJavaMethod> getImplicitInterfaceMethodsList() {
            if (mirandaMethodsStart == UNKNOWN) {
                return null;
            }
            return subList(mirandaMethodsStart, classVtableLength);
        }
    }

    @UnknownObjectField(availability = AfterAnalysis.class) //
    private VTableHolder vtableHolder = null;

    // Debugger side constructor, class is an opaque JavaConstant.
    private InterpreterResolvedObjectType(Symbol<Type> type, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool,
                    JavaConstant clazzConstant,
                    boolean isWordType, String sourceFileName, Symbol<Name>[] permittedSubclassNames) {
        super(type, clazzConstant, isWordType);
        assert (char) modifiers == modifiers;
        this.modifiers = (char) modifiers;
        this.componentType = componentType;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.permittedSubclassNames = permittedSubclassNames;
        this.constantPool = constantPool;
        this.sourceFileName = sourceFileName;
    }

    // Interpreter side constructor.
    protected InterpreterResolvedObjectType(Symbol<Type> type, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    boolean isWordType, Symbol<Name>[] permittedSubclassNames) {
        super(type, javaClass, isWordType);
        assert isWordType == WordBase.class.isAssignableFrom(javaClass);
        assert (char) modifiers == modifiers;
        this.modifiers = (char) modifiers;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.permittedSubclassNames = permittedSubclassNames;
        this.componentType = componentType;
        this.constantPool = constantPool;
        this.sourceFileName = DynamicHub.fromClass(javaClass).getSourceFileName();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private InterpreterResolvedObjectType(ResolvedJavaType originalType, Symbol<Type> type, int modifiers, InterpreterResolvedJavaType componentType,
                    InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    String sourceFileName, Symbol<Name>[] permittedSubclassNames) {
        super(type, javaClass);
        assert (char) modifiers == modifiers;
        this.originalType = originalType;
        this.modifiers = (char) modifiers;
        this.componentType = componentType;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.permittedSubclassNames = permittedSubclassNames;
        this.constantPool = constantPool;
        this.sourceFileName = sourceFileName;
    }

    @Override
    public final String getSourceFileName() {
        return sourceFileName;
    }

    // Only used for BuildTimeInterpreterUniverse.
    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedObjectType createAtBuildTime(ResolvedJavaType originalType, String name, int modifiers, InterpreterResolvedJavaType componentType,
                    InterpreterResolvedObjectType superclass, InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    String sourceFileName) {
        Symbol<Type> type = CremaTypeAccess.jvmciNameToType(name);
        return new InterpreterResolvedObjectType(originalType, type, modifiers, componentType, superclass, interfaces, constantPool, javaClass, sourceFileName,
                        permittedSubclassNames(originalType, javaClass));
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType createForInterpreter(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass, boolean isWordType) {
        return createForInterpreter(name, modifiers, componentType, superclass, interfaces, constantPool, javaClass, isWordType, null);
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType createForInterpreter(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass, boolean isWordType, Symbol<Name>[] permittedSubclassNames) {
        Symbol<Type> type = CremaTypeAccess.jvmciNameToType(name);
        return new InterpreterResolvedObjectType(type, modifiers, componentType, superclass, interfaces, constantPool, javaClass, isWordType, permittedSubclassNames);
    }

    public static CremaResolvedObjectType createForCrema(ParserKlass parserKlass, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, Class<?> javaClass,
                    int staticReferenceFields, int staticPrimitiveFieldsSize) {
        return new CremaResolvedObjectType(parserKlass, componentType, superclass, interfaces, null, javaClass, false, staticReferenceFields, staticPrimitiveFieldsSize);
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType createWithOpaqueClass(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    JavaConstant clazzConstant,
                    boolean isWordType,
                    String sourceFileName) {
        return createWithOpaqueClass(name, modifiers, componentType, superclass, interfaces, constantPool, clazzConstant, isWordType, sourceFileName, null);
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType createWithOpaqueClass(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    JavaConstant clazzConstant,
                    boolean isWordType,
                    String sourceFileName, Symbol<Name>[] permittedSubclassNames) {
        Symbol<Type> type = CremaTypeAccess.jvmciNameToType(name);
        return new InterpreterResolvedObjectType(type, modifiers, componentType, superclass, interfaces, constantPool, clazzConstant, isWordType,
                        sourceFileName, permittedSubclassNames);
    }

    public final void setConstantPool(InterpreterConstantPool constantPool) {
        VMError.guarantee(this == constantPool.getHolder());
        this.constantPool = MetadataUtil.requireNonNull(constantPool);
    }

    @Override
    public final InterpreterConstantPool getConstantPool() {
        assert !isArray();
        return constantPool;
    }

    @Override
    public InterpreterResolvedJavaType resolveClassConstantInPool(int cpi) {
        throw VMError.unimplemented("should be unneeded for AOT types.");
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final ResolvedJavaType getOriginalType() {
        return originalType;
    }

    /**
     * Important note: This is, in general, NOT equal to {@link DynamicHub#getModifiers()}.
     * <p>
     * When working with JVM-side modifiers, always use this method rather than
     * {@link DynamicHub#getModifiers()}.
     *
     * @see DynamicHub#getModifiers()
     */
    @Override
    public final int getModifiers() {
        return modifiers;
    }

    @Override
    public final InterpreterResolvedJavaType getComponentType() {
        return componentType;
    }

    @Override
    public final boolean isHidden() {
        if (clazz == null) {
            throw VMError.unimplemented("isHidden with no class");
        } else {
            return clazz.isHidden();
        }
    }

    @Override
    public List<JavaType> getPermittedSubclasses() {
        if (isArray()) {
            return null;
        }
        if (permittedSubclassNames == null) {
            return null;
        }
        if (permittedSubclassNames.length == 0) {
            return List.of();
        }
        ArrayList<JavaType> list = new ArrayList<>(permittedSubclassNames.length);
        for (Symbol<Name> permittedSubclassName : permittedSubclassNames) {
            Symbol<Type> permittedSubclassType = SymbolsSupport.getTypes().getOrCreateValidType(TypeSymbols.nameToType(permittedSubclassName));
            list.add(CremaMethodAccess.toJavaType(permittedSubclassType));
        }
        return list;
    }

    public final boolean hasPermittedSubclasses() {
        return permittedSubclassNames != null;
    }

    public final boolean declaresPermittedSubclass(Symbol<Name> internalName) {
        if (permittedSubclassNames == null) {
            return false;
        }
        for (Symbol<Name> permittedSubclassName : permittedSubclassNames) {
            if (permittedSubclassName == internalName) {
                return true;
            }
        }
        return false;
    }

    public final Symbol<Name>[] getPermittedSubclassNames() {
        return permittedSubclassNames;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Symbol<Name>[] permittedSubclassNames(ResolvedJavaType originalType, Class<?> javaClass) {
        Symbol<Name>[] parsedNames = permittedSubclassNames(javaClass);
        if (parsedNames != null) {
            return parsedNames;
        }

        ResolvedJavaType sourceType = OriginalClassProvider.getOriginalType(originalType);
        List<? extends JavaType> permittedSubclasses = sourceType.getPermittedSubclasses();
        if (permittedSubclasses == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Symbol<Name>[] result = (Symbol<Name>[]) new Symbol<?>[permittedSubclasses.size()];
        for (int i = 0; i < result.length; i++) {
            // Store the class-file name so build-time and Crema-loaded sealed classes use the same symbolic representation.
            String classFileName = permittedSubclasses.get(i).toClassName().replace('.', '/');
            result[i] = SymbolsSupport.getNames().getOrCreate(ByteSequence.create(classFileName));
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Symbol<Name>[] permittedSubclassNames(Class<?> javaClass) {
        String classFileName = javaClass.getName().replace('.', '/') + ".class";
        try (InputStream stream = javaClass.getClassLoader() == null ? ClassLoader.getSystemResourceAsStream(classFileName) : javaClass.getClassLoader().getResourceAsStream(classFileName)) {
            if (stream == null) {
                return null;
            }
            // Read the class-file attribute directly so permitted subclasses stay symbolic.
            ParserKlass parsed = ClassfileParser.parse(ClassRegistries.currentLayer(), new ClassfileStream(stream.readAllBytes(), null), false, javaClass.getClassLoader() == null, null,
                            javaClass.isHidden(), true, false);
            return permittedSubclassNames(parsed);
        } catch (IOException | ValidationException | ParserException e) {
            throw VMError.shouldNotReachHere("Cannot parse class file for " + javaClass.getName(), e);
        }
    }

    private static Symbol<Name>[] permittedSubclassNames(ParserKlass parserKlass) {
        PermittedSubclassesAttribute permittedSubclasses = parserKlass.getAttribute(PermittedSubclassesAttribute.NAME, PermittedSubclassesAttribute.class);
        if (permittedSubclasses == null) {
            return null;
        }
        char[] classes = permittedSubclasses.getClasses();
        @SuppressWarnings("unchecked")
        Symbol<Name>[] result = (Symbol<Name>[]) new Symbol<?>[classes.length];
        for (int i = 0; i < classes.length; i++) {
            result[i] = parserKlass.getConstantPool().className(classes[i]);
        }
        return result;
    }

    @Override
    public List<? extends ResolvedJavaRecordComponent> getRecordComponents() {
        if (isArray()) {
            return null;
        }
        // Crema only ever creates InterpreterResolvedObjectTypes for arrays (see
        // createForInterpreter). At build time, InterpreterResolvedObjectTypes are
        // created for AOT classes when RuntimeClassLoading is enabled. However,
        // for these classes, Class.getRecordComponents routes to
        // ImageReflectionMetadata.getRecordComponents which does not end up here.
        throw VMError.shouldNotReachHere("getRecordComponents: class file attributes for " + toClassName() + " not available at runtime");
    }

    @Override
    public final JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    /**
     * Returns the super class according to the contract of
     * {@link ResolvedJavaType#getSuperclass()}.
     * <p>
     * Note that this is different from {@link #getSuperClass()} for interface and array types.
     */
    @Override
    public final InterpreterResolvedObjectType getSuperclass() {
        return this.superclass;
    }

    @Override
    public final InterpreterResolvedObjectType[] getInterfaces() {
        return this.interfaces;
    }

    @Override
    public final boolean isAssignableFrom(ResolvedJavaType other) {
        if (other instanceof InterpreterResolvedObjectType o) {
            return isSubTypeOf(this, o);
        }
        return false;
    }

    public Object getStaticStorage(boolean primitives, int layerNum) {
        assert layerNum != MultiLayeredImageSingleton.NONSTATIC_FIELD_LAYER_NUMBER : "Requesting static storage for a non-static field: " + layerNum;
        if (primitives) {
            return StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(layerNum);
        } else {
            return StaticFieldsSupport.getStaticObjectFieldsAtRuntime(layerNum);
        }
    }

    private static boolean isSubTypeOf(InterpreterResolvedObjectType superType, InterpreterResolvedObjectType subType) {
        if (subType.equals(superType)) {
            return true;
        }
        if (subType.superclass != null) {
            if (isSubTypeOf(superType, subType.superclass)) {
                return true;
            }
        }
        for (InterpreterResolvedObjectType interf : subType.interfaces) {
            if (isSubTypeOf(superType, interf)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the virtual dispatch table. For interfaces this returns the interface dispatch table
     * prototype.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public final InterpreterResolvedJavaMethod[] getVtable() {
        if (vtableHolder == null) {
            return null;
        }
        return vtableHolder.vtable;
    }

    public final void setVtable(InterpreterResolvedJavaMethod[] vtable, int classVtableLength) {
        setVtable(vtable, classVtableLength, VTableHolder.UNKNOWN);
    }

    public final void setVtable(InterpreterResolvedJavaMethod[] vtable, int classVtableLength, int mirandaMethodsStart) {
        // The stored table may include interface dispatch tail entries beyond the class vtable.
        VMError.guarantee(classVtableLength >= 0 && classVtableLength <= vtable.length, "Invalid class vtable length");
        VMError.guarantee((mirandaMethodsStart >= 0 && mirandaMethodsStart <= classVtableLength) || mirandaMethodsStart == VTableHolder.UNKNOWN,
                        "Invalid miranda methods range");
        this.vtableHolder = new VTableHolder(this, vtable, classVtableLength, mirandaMethodsStart);
    }

    public final int getClassVtableLength() {
        if (vtableHolder == null) {
            return 0;
        }
        return vtableHolder.classVtableLength;
    }

    @Override
    public final InterpreterResolvedJavaMethod lookupVTableEntry(int vtableIndex) {
        InterpreterResolvedJavaMethod[] vtable = getVtable();
        assert vtable != null;
        if (vtableIndex >= vtable.length) {
            return null;
        }
        return vtable[vtableIndex];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final VTableHolder getVtableHolder() {
        assert !isArray();
        return vtableHolder;
    }

    @Override
    public final InterpreterResolvedJavaMethod[] getDeclaredMethods(boolean link) {
        if (link) {
            link();
        }
        return declaredMethods;
    }

    public InterpreterResolvedJavaField[] getDeclaredFields() {
        return declaredFields;
    }

    public final void setDeclaredMethods(InterpreterResolvedJavaMethod[] declaredMethods) {
        this.declaredMethods = declaredMethods;
    }

    public final void setDeclaredFields(InterpreterResolvedJavaField[] declaredFields) {
        this.declaredFields = declaredFields;
    }

    public final void setAfterFieldsOffset(int afterFieldsOffset) {
        this.afterFieldsOffset = afterFieldsOffset;
    }

    public final int getAfterFieldsOffset() {
        return afterFieldsOffset;
    }

    @Override
    public InterpreterResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        // Collect non-static fields declared in this class
        int thisClazzFieldCount = 0;
        for (InterpreterResolvedJavaField f : declaredFields) {
            if (!f.isStatic()) {
                thisClazzFieldCount++;
            }
        }

        InterpreterResolvedJavaField[] thisClazzFields;
        if (thisClazzFieldCount == 0) {
            thisClazzFields = InterpreterResolvedJavaField.EMPTY_ARRAY;
        } else {
            thisClazzFields = new InterpreterResolvedJavaField[thisClazzFieldCount];
            int idx = 0;
            for (InterpreterResolvedJavaField f : declaredFields) {
                if (!f.isStatic()) {
                    thisClazzFields[idx++] = f;
                }
            }
        }

        // If not including superclasses or no superclass, return thisClazzFields
        if (!includeSuperclasses || superclass == null) {
            return thisClazzFields;
        }

        // Merge with superclass instance fields: superclass first, preserving declared order
        InterpreterResolvedJavaField[] parent = superclass.getInstanceFields(true);
        if (parent.length == 0) {
            return thisClazzFields;
        }
        if (thisClazzFields.length == 0) {
            return parent;
        }
        InterpreterResolvedJavaField[] result = Arrays.copyOf(parent, parent.length + thisClazzFields.length);
        System.arraycopy(thisClazzFields, 0, result, parent.length, thisClazzFields.length);
        return result;
    }

    @Override
    public InterpreterResolvedJavaField[] getStaticFields() {
        InterpreterResolvedJavaField[] declared = this.declaredFields;
        if (declared == null || declared.length == 0) {
            return InterpreterResolvedJavaField.EMPTY_ARRAY;
        }
        int thisClazzStaticFieldCount = 0;
        for (InterpreterResolvedJavaField f : declared) {
            if (f.isStatic()) {
                thisClazzStaticFieldCount++;
            }
        }
        if (thisClazzStaticFieldCount == 0) {
            return InterpreterResolvedJavaField.EMPTY_ARRAY;
        }
        InterpreterResolvedJavaField[] thisClazzStaticFields = new InterpreterResolvedJavaField[thisClazzStaticFieldCount];
        int idx = 0;
        for (InterpreterResolvedJavaField f : declared) {
            if (f.isStatic()) {
                thisClazzStaticFields[idx++] = f;
            }
        }
        return thisClazzStaticFields;
    }

    @Override
    public InterpreterResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        if (offset < 0) {
            return null;
        }
        // Search all instance fields including superclasses
        InterpreterResolvedJavaField[] fields = getInstanceFields(true);
        for (InterpreterResolvedJavaField f : fields) {
            // Compare offsets (stored as int at build time but passed as long here)
            if (f.getOffset() == offset) {
                // If an expected kind is provided, enforce it
                if (expectedKind == null || expectedKind == f.getJavaKind()) {
                    return f;
                }
            }
        }
        return null;
    }

    @Override
    public final String getJavaName() {
        if (clazz != null) {
            return clazz.getName();
        }
        throw VMError.unimplemented("getJavaName");
    }

    @Override
    public final InterpreterResolvedJavaType findLeastCommonAncestor(InterpreterResolvedJavaType other) {
        assert !isPrimitive() && !other.isPrimitive();
        assert !isInterface() && !other.isInterface();
        assert !isArray() && !other.isArray();
        InterpreterResolvedObjectType t1 = this;
        InterpreterResolvedObjectType t2 = (InterpreterResolvedObjectType) other;
        while (true) {
            if (t1.isAssignableFrom(t2)) {
                return t1;
            }
            if (t2.isAssignableFrom(t1)) {
                return t2;
            }
            t1 = t1.getSuperclass();
            t2 = t2.getSuperclass();
        }
    }

    /**
     * Returns the super class according to the contract of {@link TypeAccess#getSuperClass()}.
     * <p>
     * Note that this is different from {@link #getSuperclass()} for interface and array types.
     */
    @Override
    public final InterpreterResolvedObjectType getSuperClass() {
        if (isInterface() || isArray()) {
            return (InterpreterResolvedObjectType) DynamicHub.fromClass(Object.class).getInterpreterType();
        }
        return this.superclass;
    }

    @Override
    public final List<InterpreterResolvedJavaType> getSuperInterfacesList() {
        return Arrays.asList(getInterfaces());
    }

    @Override
    public List<InterpreterResolvedJavaMethod> getDeclaredMethodsList() {
        return Arrays.asList(declaredMethods);
    }

    @Override
    public List<InterpreterResolvedJavaMethod> getImplicitInterfaceMethodsList() {
        if (vtableHolder == null) {
            return null;
        }
        return vtableHolder.getImplicitInterfaceMethodsList();
    }

    @Override
    public final Symbol<Name> getSymbolicRuntimePackage() {
        ByteSequence hostPkgName = TypeSymbols.getRuntimePackage(getSymbolicType());
        return SymbolsSupport.getNames().getOrCreate(hostPkgName);
    }

    @Override
    public final InterpreterResolvedJavaField lookupField(Symbol<Name> name, Symbol<Type> type) {
        for (InterpreterResolvedJavaField field : this.declaredFields) {
            if (name.equals(field.getSymbolicName()) && type.equals(field.getSymbolicType())) {
                return field;
            }
        }
        for (InterpreterResolvedJavaType superInterface : getInterfaces()) {
            InterpreterResolvedJavaField result = superInterface.lookupField(name, type);
            if (result != null) {
                return result;
            }
        }
        if (getSuperclass() != null) {
            return getSuperclass().lookupField(name, type);
        }
        return null;
    }

    /**
     * Not the {@linkplain CremaResolvedObjectType#getNestHost() nest host}, but the "Host type" of
     * VM-anonymous classes, no longer in use in Java 17+, so return null. Requested by the
     * {@link com.oracle.svm.espresso.shared.verifier.Verifier verifier}.
     */
    @Override
    public final InterpreterResolvedJavaType getHostType() {
        return null;
    }

    public final void imposeLoadingConstraints() {
        if (!isInterface() && getSuperClass() != null) {
            InterpreterResolvedJavaMethod[] thisTable = getVtable();
            InterpreterResolvedJavaMethod[] superTable = getSuperClass().getVtable();
            if (thisTable == null || superTable == null) {
                throw VMError.shouldNotReachHere("Uninitialized interpreter VTable during loading constraints recording.");
            }
            int superTableEnd = getSuperClass().getClassVtableLength();
            // Iterate our vtable to impose constraints between us and our superclass if necessary.
            for (int i = 0; i < superTableEnd; i++) {
                InterpreterResolvedJavaMethod entry = thisTable[i];
                if (entry.getDeclaringClass() == this) {
                    // If we override a method in super's table, enforce constraint to ensure we
                    // agree on the types.
                    entry.checkLoadingConstraints(getClassLoader(), superTable[i].getDeclaringClass().getClassLoader());
                }
            }

            // Iterate the itables to impose constraints.
            for (InterpreterResolvedObjectType superInterface : computeTransitiveInterfaceList()) {
                VMError.guarantee(superInterface.getVtable() != null);
                int start = determineITableStartingIndex(superInterface);
                for (int i = 0; i < superInterface.getVtable().length; i++) {
                    InterpreterResolvedJavaMethod thisMethod = thisTable[start + i];
                    // Ensure this class and its interfaces agree on the types for their related
                    // methods.
                    if (thisMethod.getDeclaringClass() == this) {
                        thisMethod.checkLoadingConstraints(getClassLoader(), superInterface.getClassLoader());
                    } else {
                        thisMethod.checkLoadingConstraints(superInterface.getClassLoader(), thisMethod.getDeclaringClass().getClassLoader());
                        thisMethod.checkLoadingConstraints(getClassLoader(), thisMethod.getDeclaringClass().getClassLoader());
                    }
                }
            }
        }
    }

    public int determineITableStartingIndex(InterpreterResolvedObjectType seedInterface) {
        /*
         * iTableStartingOffset includes the initial offset to the vtable array and describes an
         * offset (not index)
         */
        long iTableStartingOffset = OpenTypeWorldDispatchTableSnippets.determineITableStartingOffset(getHub(), seedInterface.getHub().getInterfaceID());

        int vtableBaseOffset = KnownOffsets.singleton().getVTableBaseOffset();
        int vtableEntrySize = KnownOffsets.singleton().getVTableEntrySize();

        return (int) (iTableStartingOffset - vtableBaseOffset) / vtableEntrySize;
    }

    private Iterable<InterpreterResolvedObjectType> computeTransitiveInterfaceList() {
        EconomicSet<InterpreterResolvedObjectType> set = EconomicSet.create();
        InterpreterResolvedObjectType current = this;
        while (current != null) {
            for (InterpreterResolvedObjectType interfaceClass : current.getInterfaces()) {
                collectInterfaces(interfaceClass, set);
            }
            current = current.getSuperclass();
        }
        for (InterpreterResolvedObjectType interfaceClass : getInterfaces()) {
            collectInterfaces(interfaceClass, set);
        }
        return set;
    }

    private static void collectInterfaces(InterpreterResolvedObjectType interfaceClass, EconomicSet<InterpreterResolvedObjectType> result) {
        if (result.add(interfaceClass)) {
            for (InterpreterResolvedObjectType superInterface : interfaceClass.getInterfaces()) {
                collectInterfaces(superInterface, result);
            }
        }
    }

}
