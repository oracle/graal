/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * 
 */
public final class GuestAllocator implements ContextAccess {
    private final EspressoContext context;
    private final EspressoLanguage lang;

    public GuestAllocator(EspressoContext context) {
        this.context = context;
        this.lang = context.getLanguage();
    }

    public StaticObject createNew(ObjectKlass klass) {
        assert klass != null; // May be
        klass.safeInitialize();
        StaticObject newObj = klass.getLinkedKlass().getShape(false).getFactory().create(klass);
        initInstanceFields(newObj, klass);
        return trackAllocation(klass, newObj);
    }

    // Shallow copy.
    public StaticObject copy(StaticObject toCopy) {
        if (StaticObject.isNull(toCopy)) {
            return toCopy;
        }
        toCopy.checkNotForeign();
        StaticObject obj;
        if (toCopy.getKlass().isArray()) {
            obj = wrapArrayAs((ArrayKlass) toCopy.getKlass(), toCopy.cloneWrappedArray(lang));
        } else {
            try {
                // Call `this.clone()` rather than `super.clone()` to execute the `clone()` methods
                // of generated subtypes.
                obj = (StaticObject) toCopy.clone();
            } catch (CloneNotSupportedException e) {
                throw EspressoError.shouldNotReachHere(e);
            }
        }
        return trackAllocation(obj.getKlass(), obj);
    }

    public StaticObject createClass(Klass klass) {
        assert klass != null;
        CompilerAsserts.neverPartOfCompilation();
        ObjectKlass guestClass = klass.getMeta().java_lang_Class;
        StaticObject newObj = guestClass.getLinkedKlass().getShape(false).getFactory().create(guestClass);
        initInstanceFields(newObj, guestClass);
        klass.getMeta().java_lang_Class_classLoader.setObject(newObj, klass.getDefiningClassLoader());
        if (klass.getContext().getJavaVersion().modulesEnabled()) {
            setModule(newObj, klass);
        }
        if (klass.isArray() && klass.getMeta().java_lang_Class_componentType != null) {
            klass.getMeta().java_lang_Class_componentType.setObject(newObj, ((ArrayKlass) klass).getComponentType().mirror());
        }
        // Will be overriden if necessary, but should be initialized to non-host null.
        klass.getMeta().HIDDEN_PROTECTION_DOMAIN.setHiddenObject(newObj, StaticObject.NULL);
        // Final hidden field assignment
        klass.getMeta().HIDDEN_MIRROR_KLASS.setHiddenObject(newObj, klass);
        return trackAllocation(klass, newObj);
    }

    public StaticObject createStatics(ObjectKlass klass) {
        assert klass != null;
        CompilerAsserts.neverPartOfCompilation();
        StaticObject newObj = klass.getLinkedKlass().getShape(true).getFactory().create(klass);
        initInitialStaticFields(newObj, klass);
        return trackAllocation(klass, newObj);
    }

    public StaticObject createNewPrimitiveArray(Klass klass, int length) {
        assert klass.isPrimitive();
        return createNewPrimitiveArray(klass.getJavaKind(), length);
    }

    public StaticObject createNewPrimitiveArray(JavaKind kind, int length) {
        assert kind.isPrimitive();
        return createNewPrimitiveArray((byte) kind.getBasicType(), length);
    }

    public StaticObject createNewPrimitiveArray(byte jvmPrimitiveType, int length) {
        Meta meta = getMeta();
        // @formatter:off
        switch (jvmPrimitiveType) {
            case 4  : return wrapArrayAs(meta._boolean_array, new byte[length]); // boolean[] are internally represented as byte[] with _boolean_array Klass
            case 5  : return StaticObject.wrap(new char[length], meta);
            case 6  : return StaticObject.wrap(new float[length], meta);
            case 7  : return StaticObject.wrap(new double[length], meta);
            case 8  : return StaticObject.wrap(new byte[length], meta);
            case 9  : return StaticObject.wrap(new short[length], meta);
            case 10 : return StaticObject.wrap(new int[length], meta);
            case 11 : return StaticObject.wrap(new long[length], meta);
            default :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
    }

    public StaticObject createNewReferenceArray(Klass componentKlass, int length) {
        assert length >= 0;
        assert !componentKlass.isPrimitive();
        StaticObject[] arr = new StaticObject[length];
        Arrays.fill(arr, StaticObject.NULL);
        return wrapArrayAs(componentKlass.getArrayClass(), arr);
    }
    
    public StaticObject createNewMultiArray(Klass component, int[] dimensions) {
        assert  dimensions != null && dimensions.length > 0;
        return createNewMultiArray(component, dimensions, 0);
    }

    // Use an explicit method to create array, avoids confusion.
    public StaticObject wrapArrayAs(ArrayKlass klass, Object array) {
        assert klass != null;
        assert array != null;
        assert !(array instanceof StaticObject);
        assert array.getClass().isArray();
        assert klass.getComponentType().isPrimitive() || array instanceof StaticObject[];
        StaticObject newObj = lang.getArrayShape().getFactory().create(klass);
        lang.getArrayProperty().setObject(newObj, array);
        return trackAllocation(klass, newObj);
    }

    /**
     * Wraps a foreign {@link InteropLibrary#isException(Object) exception} as a guest
     * ForeignException.
     */
    public @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/ForeignException;") StaticObject 
    createForeignException(
            Object foreignObject,
                    InteropLibrary interopLibrary) {
        Meta meta = getMeta();
        assert meta.polyglot != null;
        assert meta.getContext().Polyglot;
        assert interopLibrary.isException(foreignObject);
        assert !(foreignObject instanceof StaticObject);
        return createForeign(lang, meta.polyglot.ForeignException, foreignObject, interopLibrary);
    }

    public static StaticObject createForeign(
            EspressoLanguage lang, 
            Klass klass, 
            Object foreignObject, 
            InteropLibrary interopLibrary) {
        if (interopLibrary.isNull(foreignObject)) {
            return createForeignNull(lang, foreignObject);
        }
        return createForeign(lang, klass, foreignObject);
    }

    public static StaticObject createForeignNull(EspressoLanguage lang, Object foreignObject) {
        assert InteropLibrary.getUncached().isNull(foreignObject);
        return createForeign(lang, null, foreignObject);
    }

    private static void initInstanceFields(StaticObject obj, ObjectKlass thisKlass) {
        obj.checkNotForeign();
        if (CompilerDirectives.isPartialEvaluationConstant(thisKlass)) {
            initLoop(obj, thisKlass);
        } else {
            initLoopNoExplode(obj, thisKlass);
        }
    }

    @ExplodeLoop
    private static void initLoop(StaticObject obj, ObjectKlass thisKlass) {
        for (Field f : thisKlass.getFieldTable()) {
            assert !f.isStatic();
            if (!f.isHidden() && !f.isRemoved()) {
                if (f.getKind() == JavaKind.Object) {
                    f.setObject(obj, StaticObject.NULL);
                }
            }
        }
    }

    private static void initLoopNoExplode(StaticObject obj, ObjectKlass thisKlass) {
        for (Field f : thisKlass.getFieldTable()) {
            assert !f.isStatic();
            if (!f.isHidden() && !f.isRemoved()) {
                if (f.getKind() == JavaKind.Object) {
                    f.setObject(obj, StaticObject.NULL);
                }
            }
        }
    }

    private static void initInitialStaticFields(StaticObject obj, ObjectKlass thisKlass) {
        obj.checkNotForeign();
        if (CompilerDirectives.isPartialEvaluationConstant(thisKlass)) {
            staticInitLoop(obj, thisKlass);
        } else {
            staticInitLoopNoExplode(obj, thisKlass);
        }
    }

    @ExplodeLoop
    private static void staticInitLoop(StaticObject obj, ObjectKlass thisKlass) {
        for (Field f : thisKlass.getInitialStaticFields()) {
            assert f.isStatic();
            if (f.getKind() == JavaKind.Object && !f.isRemoved()) {
                if (f.isHidden()) { // extension field
                    f.setHiddenObject(obj, StaticObject.NULL);
                } else {
                    f.setObject(obj, StaticObject.NULL);
                }
            }
        }
    }

    private static void staticInitLoopNoExplode(StaticObject obj, ObjectKlass thisKlass) {
        for (Field f : thisKlass.getInitialStaticFields()) {
            assert f.isStatic();
            if (f.getKind() == JavaKind.Object && !f.isRemoved()) {
                if (f.isHidden()) { // extension field
                    f.setHiddenObject(obj, StaticObject.NULL);
                } else {
                    f.setObject(obj, StaticObject.NULL);
                }
            }
        }
    }

    private static StaticObject createForeign(EspressoLanguage lang, Klass klass, Object foreignObject) {
        assert foreignObject != null;
        StaticObject newObj = lang.getForeignShape().getFactory().create(klass, true);
        lang.getForeignProperty().setObject(newObj, foreignObject);
        if (klass != null) {
            klass.safeInitialize();
        }
        return trackForeignAllocation(klass, newObj);
    }

    private void setModule(StaticObject obj, Klass klass) {
        StaticObject module = klass.module().module();
        if (StaticObject.isNull(module)) {
            if (context.getRegistries().javaBaseDefined()) {
                context.getMeta().java_lang_Class_module.setObject(obj, klass.getRegistries().getJavaBaseModule().module());
            } else {
                context.getRegistries().addToFixupList(klass);
            }
        } else {
            context.getMeta().java_lang_Class_module.setObject(obj, module);
        }
    }

    private StaticObject createNewMultiArray(Klass component, int[] dimensions, int currentDimension) {
        assert  dimensions != null && dimensions.length > 0;
        int dimLength = dimensions[currentDimension];
        if (currentDimension == dimensions.length - 1) {
            if (component.isPrimitive()) {
                return createNewPrimitiveArray(component, dimLength);
            } else {
                return createNewReferenceArray(component, dimLength);
            }
        }
        StaticObject[] wrapped = new StaticObject[dimLength];
        assert component instanceof ArrayKlass;
        Klass downComponent = ((ArrayKlass) component).getComponentType();
        if (CompilerDirectives.isPartialEvaluationConstant(dimLength)) {
            initMultiArrayLoop(downComponent, wrapped, dimLength, dimensions, currentDimension);
        } else {
            initMultiArrayLoopNoExplode(downComponent, wrapped, dimLength, dimensions, currentDimension);
        }
        return wrapArrayAs(component.array(), wrapped);
    }

    @ExplodeLoop
    private void initMultiArrayLoop(Klass klass, StaticObject[] wrapped, int dimLength, int[] dimensions, int pos) {
        for (int i = 0; i < dimLength; i++) {
            wrapped[i] = createNewMultiArray(klass, dimensions, pos +1);
        }
    }

    private void initMultiArrayLoopNoExplode(Klass downComponent, StaticObject[] wrapped, int dimLength, int[] dimensions, int pos) {
        for (int i = 0; i < dimLength; i++) {
            wrapped[i] = createNewMultiArray(downComponent, dimensions, pos +1);
        }
    }

    private StaticObject trackAllocation(Klass klass, StaticObject obj) {
        if (klass == null || context == null) {
            return obj;
        }
        if (!CompilerDirectives.isPartialEvaluationConstant(context)) {
            return trackAllocationBoundary(context, obj);
        }
        return context.trackAllocation(obj);
    }
    
    private static StaticObject trackForeignAllocation(Klass klass, StaticObject obj) {
        if (klass == null) {
            return obj;
        }
        if (CompilerDirectives.isPartialEvaluationConstant(klass)) {
            return klass.getContext().trackAllocation(obj);
        } else {
            return trackAllocationBoundary(klass.getContext(), obj);
        }
    }
    
    @TruffleBoundary
    private static StaticObject trackAllocationBoundary(EspressoContext context, StaticObject obj) {
        return context.trackAllocation(obj);
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }
    
    public static final class AllocationChecks {
        private AllocationChecks() {}
        
        public static boolean canAllocateNewReference(Klass klass) {
            return (klass instanceof ObjectKlass) && !klass.isAbstract() && !klass.isInterface();
        }
        
        public static void checkCanAllocateNewReference(Meta meta, Klass klass) {
            if (!canAllocateNewReference(klass)) {
                throw meta.throwException(meta.java_lang_InstantiationException);
            }
        }
        
        public static boolean canAllocateNewArray(int size) {
            return size >= 0;
        }
        
        public static void checkCanAllocateArray(Meta meta, int size) {
            if (!canAllocateNewArray(size)) {
                throw meta.throwException(meta.java_lang_NegativeArraySizeException);
            }
        }
        
        public static boolean canAllocateMultiArray(Meta meta, Klass component, int[] dimensions) {
            if (invalidComponent(meta, component)) {
                return false;
            }
            if (invalidDimensionsArray(dimensions)) {
                return false;
            }
            if (invalidDimensions(dimensions)) {
                return false;
            }
            return true;
        }

        public static void checkCanAllocateMultiArray(Meta meta, Klass component, int[] dimensions) {
            if (invalidComponent(meta, component)) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            }
            if (invalidDimensionsArray(dimensions)) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            }
            if (invalidDimensions(dimensions)) {
                throw meta.throwException(meta.java_lang_NegativeArraySizeException);
            }
        }
        
        private static boolean invalidDimensionsArray(int[] dimensions) {
            return dimensions.length == 0 || dimensions.length > 255;
        }
        
        private static boolean invalidDimensions(int[] dimensions) {
            for (int dim : dimensions) {
                if (!canAllocateNewArray(dim)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean invalidComponent(Meta meta, Klass component) {
            return component == meta._void;
        }
    }
}
