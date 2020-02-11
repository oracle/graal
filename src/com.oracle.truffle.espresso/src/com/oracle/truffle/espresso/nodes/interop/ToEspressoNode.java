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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
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

    @Specialization(guards = "primitiveKlass.isPrimitive()", limit = "LIMIT")
    Object doPrimitive(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop) throws UnsupportedMessageException, UnsupportedTypeException {
        Symbol<Type> type = primitiveKlass.getType();
        if (interop.isNumber(value)) {
            if (type == Type._byte) {
                if (interop.fitsInByte(value)) {
                    return interop.asByte(value);
                }
            } else if (type == Type._short) {
                if (interop.fitsInShort(value)) {
                    return interop.asShort(value);
                }
            } else if (type == Type._int) {
                if (interop.fitsInInt(value)) {
                    return interop.asInt(value);
                }
            } else if (type == Type._long) {
                if (interop.fitsInLong(value)) {
                    return interop.asLong(value);
                }
            } else if (type == Type._float) {
                if (interop.fitsInFloat(value)) {
                    return interop.asFloat(value);
                }
            } else if (type == Type._double) {
                if (interop.fitsInDouble(value)) {
                    return interop.asDouble(value);
                }
            }
        } else if (type == Type._boolean) {
            if (interop.isBoolean(value)) {
                return interop.asBoolean(value);
            }
        } else if (type == Type._char) {
            if (interop.isString(value)) {
                String str = interop.asString(value);
                if (str.length() == 1) {
                    return str.charAt(0);
                }
            }
        }

        throw UnsupportedTypeException.create(new Object[]{value}, type.toString());
    }

    @Specialization(guards = "stringKlass.getMeta().java_lang_String.equals(stringKlass)", limit = "LIMIT")
    Object doString(Object value,
                    Klass stringKlass,
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
    @Specialization(guards = {"stringArrayKlass.getMeta().java_lang_String.array().equals(stringArrayKlass)", "interop.hasArrayElements(value)"}, limit = "LIMIT")
    Object doStringArray(Object value,
                    ArrayKlass stringArrayKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached(value = "createToEspresso(interop.getArraySize(value), false)", uncached = "createToEspresso(interop.getArraySize(value), true)") ToEspressoNode[] toEspressoNodes)
                    throws UnsupportedMessageException, UnsupportedTypeException {
        if (interop.isNull(value)) {
            return StaticObject.NULL;
        }
        if (interop.hasArrayElements(value)) {
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

        throw UnsupportedTypeException.create(new Object[]{value}, stringArrayKlass.getType().toString());
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

    @Specialization(replaces = {"doPrimitive", "doString", "doStringArray", "doGenericObject"})
    Object doUnsupported(Object value, Klass targetKlass) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{value}, targetKlass.getTypeAsString());
    }

    @SuppressWarnings("unchecked")
    private static <T extends RuntimeException> boolean rethrow(Throwable e) throws T {
        throw (T) e;
    }
}
