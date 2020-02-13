/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.interop;

import java.util.function.IntFunction;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@GenerateUncached
abstract class ToEspressoNode extends Node {
    static final int LIMIT = 2;

    public abstract Object execute(Object value, Klass targetType) throws UnsupportedMessageException, UnsupportedTypeException;

    static ToEspressoNode[] createToEspresso(long argsLength, boolean uncached) {
        ToEspressoNode[] toEspresso = new ToEspressoNode[(int) argsLength];
        for (int i = 0; i < argsLength; i++) {
            toEspresso[i] = uncached
                            ? ToEspressoNodeGen.getUncached()
                            : ToEspressoNodeGen.create();
        }
        return toEspresso;
    }

    static boolean isBoolean(Symbol<Type> type) {
        return Type._boolean.equals(type);
    }

    static boolean isByte(Symbol<Type> type) {
        return Type._byte.equals(type);
    }

    static boolean isChar(Symbol<Type> type) {
        return Type._char.equals(type);
    }

    static boolean isShort(Symbol<Type> type) {
        return Type._short.equals(type);
    }

    static boolean isInt(Symbol<Type> type) {
        return Type._int.equals(type);
    }

    static boolean isFloat(Symbol<Type> type) {
        return Type._float.equals(type);
    }

    static boolean isLong(Symbol<Type> type) {
        return Type._long.equals(type);
    }

    static boolean isDouble(Symbol<Type> type) {
        return Type._double.equals(type);
    }

    @Specialization(guards = "isByte(primitiveKlass.getType())", limit = "LIMIT")
    byte doByte(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        assert primitiveKlass != null;
        if (interop.isNumber(value) && interop.fitsInByte(value)) {
            return interop.asByte(value);
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization(guards = "isShort(primitiveKlass.getType())", limit = "LIMIT")
    short doShort(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        assert primitiveKlass != null;
        if (interop.isNumber(value) && interop.fitsInShort(value)) {
            return interop.asShort(value);
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization(guards = "isInt(primitiveKlass.getType())", limit = "LIMIT")
    int doInt(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        assert primitiveKlass != null;
        if (interop.isNumber(value) && interop.fitsInInt(value)) {
            return interop.asInt(value);
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization(guards = "isFloat(primitiveKlass.getType())", limit = "LIMIT")
    float doFloat(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        assert primitiveKlass != null;
        if (interop.isNumber(value) && interop.fitsInFloat(value)) {
            return interop.asFloat(value);
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization(guards = "isDouble(primitiveKlass.getType())", limit = "LIMIT")
    double doDouble(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        assert primitiveKlass != null;
        if (interop.isNumber(value) && interop.fitsInDouble(value)) {
            return interop.asDouble(value);
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization(guards = "isLong(primitiveKlass.getType())", limit = "LIMIT")
    long doLong(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        assert primitiveKlass != null;
        if (interop.isNumber(value) && interop.fitsInLong(value)) {
            return interop.asLong(value);
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization(guards = "isBoolean(primitiveKlass.getType())", limit = "LIMIT")
    boolean doBoolean(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        assert primitiveKlass != null;
        if (interop.isBoolean(value)) {
            return interop.asBoolean(value);
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization(guards = "isChar(primitiveKlass.getType())", limit = "LIMIT")
    char doChar(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException, UnsupportedTypeException {
        assert primitiveKlass != null;
        if (interop.isString(value)) {
            String str = interop.asString(value);
            if (str.length() == 1) {
                return str.charAt(0);
            }
        }
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization(guards = "stringKlass.getMeta().java_lang_String.equals(stringKlass)", limit = "LIMIT")
    Object doString(Object value,
                    ObjectKlass stringKlass,
                    @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException, UnsupportedTypeException {
        if (interop.isString(value)) {
            String str = interop.asString(value);
            return stringKlass.getMeta().toGuestString(str);
        } else if (interop.isNull(value)) {
            return StaticObject.NULL;
        }
        throw UnsupportedTypeException.create(new Object[]{value}, stringKlass.getTypeAsString());
    }

    // TODO(peterssen): Remove, temporary workaround for passing arguments to main.
    @SuppressWarnings("unused")
    @Specialization(guards = {
                    "stringArrayKlass.getMeta().java_lang_String.array().equals(stringArrayKlass)",
                    "interop.hasArrayElements(value)"}, limit = "LIMIT")
    Object doStringArray(Object value,
                    ArrayKlass stringArrayKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached(value = "createToEspresso(interop.getArraySize(value), false)", uncached = "createToEspresso(interop.getArraySize(value), true)") ToEspressoNode[] toEspressoNodes)
                    throws UnsupportedMessageException, UnsupportedTypeException {
        if (interop.isNull(value)) {
            return StaticObject.NULL;
        }
        int length = (int) interop.getArraySize(value);
        final Klass jlString = stringArrayKlass.getComponentType();

        return jlString.allocateReferenceArray(length, new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int index) {

                if (interop.isArrayElementReadable(value, index)) {
                    try {
                        Object elem = interop.readArrayElement(value, index);
                        return (StaticObject) toEspressoNodes[index].execute(elem, jlString);
                    } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                        rethrow(UnsupportedTypeException.create(new Object[]{value}, stringArrayKlass.getTypeAsString()));
                    } catch (UnsupportedTypeException e) {
                        rethrow(e);
                    }
                }
                rethrow(UnsupportedTypeException.create(new Object[]{value}, stringArrayKlass.getTypeAsString()));
                throw EspressoError.shouldNotReachHere();
            }
        });
    }

    @Specialization(guards = "objectKlass.getJavaKind().isObject()", limit = "LIMIT")
    Object doGenericObject(Object value,
                    Klass objectKlass,
                    @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
        if (interop.isNull(value)) {
            return StaticObject.NULL;
        } else if (value instanceof StaticObject) {
            // TODO(peterssen): Espresso only supports StaticObject.
            if (InterpreterToVM.instanceOf((StaticObject) value, objectKlass)) {
                return value;
            }
        }

        throw UnsupportedTypeException.create(new Object[]{value}, objectKlass.getTypeAsString());
    }

    @Specialization
    Object doUnsupported(Object value, Klass targetKlass) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{value}, targetKlass.getTypeAsString());
    }

    @SuppressWarnings("unchecked")
    private static <T extends RuntimeException> boolean rethrow(Throwable e) throws T {
        throw (T) e;
    }
}
