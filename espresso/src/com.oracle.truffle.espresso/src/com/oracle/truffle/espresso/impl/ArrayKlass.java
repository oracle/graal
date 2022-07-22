/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.impl;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_ABSTRACT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PRIVATE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PROTECTED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PUBLIC;
import static com.oracle.truffle.espresso.meta.EspressoError.cat;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;
import com.oracle.truffle.espresso.impl.PackageTable.PackageEntry;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

public final class ArrayKlass extends Klass {

    private final Klass componentType;
    private final Klass elementalType;
    private final int dimension;

    @CompilationFinal private Assumption redefineAssumption;
    @CompilationFinal private HierarchyInfo hierarchyInfo;

    ArrayKlass(Klass componentType) {
        super(componentType.getContext(),
                        null, // TODO(peterssen): Internal, , or / name?
                        componentType.getTypes().arrayOf(componentType.getType()),
                        // Arrays (of static inner class) may have protected access.
                        (componentType.getElementalType().getModifiers() & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED)) | ACC_FINAL | ACC_ABSTRACT);
        EspressoError.guarantee(componentType.getJavaKind() != JavaKind.Void, "Invalid void[] class.");
        this.componentType = componentType;
        this.elementalType = componentType.getElementalType();
        this.dimension = Types.getArrayDimensions(getType());
        this.redefineAssumption = componentType.getRedefineAssumption();
        assert getMeta().java_lang_Class != null;
        initializeEspressoClass();
    }

    @Override
    public ObjectKlass getSuperKlass() {
        return getMeta().java_lang_Object;
    }

    @Override
    public ObjectKlass[] getSuperInterfaces() {
        return getMeta().ARRAY_SUPERINTERFACES;
    }

    @Override
    public int getClassModifiers() {
        // Arrays (of static inner class) may have protected access.
        return (getElementalType().getClassModifiers() & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED)) | ACC_FINAL | ACC_ABSTRACT;
    }

    @Override
    public Klass getElementalType() {
        return elementalType;
    }

    public Klass getComponentType() {
        return componentType;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public Klass getEnclosingType() {
        return null;
    }

    @Override
    public Method[] getDeclaredConstructors() {
        return Method.EMPTY_ARRAY;
    }

    @Override
    public Method[] getDeclaredMethods() {
        return Method.EMPTY_ARRAY;
    }

    @Override
    public MethodRef[] getDeclaredMethodRefs() {
        return Method.EMPTY_VERSION_ARRAY;
    }

    @Override
    public Method.MethodVersion[] getDeclaredMethodVersions() {
        return Method.EMPTY_VERSION_ARRAY;
    }

    @Override
    public Field[] getDeclaredFields() {
        return Field.EMPTY_ARRAY;
    }

    @Override
    public Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass, LookupMode mode) {
        KLASS_LOOKUP_METHOD_COUNT.inc();
        return getSuperKlass().lookupMethod(methodName, signature, accessingKlass, mode);
    }

    @Override
    public @JavaType(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return elementalType.getDefiningClassLoader();
    }

    @Override
    public ConstantPool getConstantPool() {
        return getElementalType().getConstantPool();
    }

    public int getDimension() {
        return dimension;
    }

    boolean arrayTypeChecks(ArrayKlass other) {
        assert isArray();
        int thisDim = getDimension();
        int otherDim = other.getDimension();
        if (otherDim > thisDim) {
            Klass thisElemental = this.getElementalType();
            return thisElemental == getMeta().java_lang_Object || thisElemental == getMeta().java_io_Serializable || thisElemental == getMeta().java_lang_Cloneable;
        } else if (thisDim == otherDim) {
            Klass klass = getElementalType();
            Klass other1 = other.getElementalType();
            if (klass == other1) {
                return true;
            }
            if (klass.isPrimitive() || other1.isPrimitive()) {
                // Reference equality is enough within the same context.
                assert klass.getContext() == other1.getContext();
                return klass == other1;
            }
            if (klass.isInterface()) {
                return klass.checkInterfaceSubclassing(other1);
            }
            int depth = klass.getHierarchyDepth();
            return other1.getHierarchyDepth() >= depth && other1.getSuperTypes()[depth] == klass;
        } else {
            assert thisDim > otherDim;
            return false;
        }
    }

    @Override
    public ModuleEntry module() {
        return getElementalType().module();
    }

    @Override
    public PackageEntry packageEntry() {
        return getElementalType().packageEntry();
    }

    @Override
    public String getNameAsString() {
        return "[" + componentType.getNameAsString();
    }

    @Override
    public String getExternalName() {
        String base = super.getExternalName();
        if (getElementalType().isAnonymous()) {
            return fixupAnonymousExternalName(base);
        }
        if (getElementalType().isHidden()) {
            return convertHidden(base);
        }
        return base;
    }

    @TruffleBoundary
    private String fixupAnonymousExternalName(String base) {
        return base.replace(";", cat("/", getElementalType().getId(), ";"));
    }

    // index 0 is Object, index hierarchyDepth is this
    @Override
    protected Klass[] getSuperTypes() {
        return getHierarchyInfo().supertypesWithSelfCache;
    }

    @Override
    protected int getHierarchyDepth() {
        return getHierarchyInfo().hierarchyDepth;
    }

    @Override
    protected ObjectKlass.KlassVersion[] getTransitiveInterfacesList() {
        return getHierarchyInfo().transitiveInterfaceCache;
    }

    private HierarchyInfo getHierarchyInfo() {
        HierarchyInfo info = hierarchyInfo;
        if (info == null || !redefineAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            info = hierarchyInfo = updateHierarchyInfo();
            redefineAssumption = getRedefineAssumption();
        }
        return info;
    }

    private HierarchyInfo updateHierarchyInfo() {
        int depth = getArraySuperType().getHierarchyDepth() + 1;
        Klass[] supertypes;
        Klass[] superKlassTypes = getArraySuperType().getSuperTypes();
        supertypes = new Klass[superKlassTypes.length + 1];
        assert supertypes.length == depth + 1;
        supertypes[depth] = this;
        System.arraycopy(superKlassTypes, 0, supertypes, 0, depth);

        ObjectKlass.KlassVersion[] transitiveInterfaces;
        ObjectKlass[] superItfs = getSuperInterfaces();
        transitiveInterfaces = new ObjectKlass.KlassVersion[superItfs.length];
        for (int i = 0; i < superItfs.length; i++) {
            transitiveInterfaces[i] = superItfs[i].getKlassVersion();
        }

        return new HierarchyInfo(supertypes, depth, transitiveInterfaces);
    }

    private Klass getArraySuperType() {
        if (this == getMeta().java_lang_Object.array() || componentType.isPrimitive()) {
            return getMeta().java_lang_Object;
        }
        return componentType.getSupertype().array();
    }

    @Override
    public Assumption getRedefineAssumption() {
        return componentType.getRedefineAssumption();
    }
}
