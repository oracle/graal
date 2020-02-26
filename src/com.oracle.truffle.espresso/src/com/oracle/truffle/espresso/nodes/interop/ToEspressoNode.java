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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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

    @Specialization(guards = "cachedKlass == primitiveKlass", limit = "LIMIT")
    Object doPrimitive(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached("primitiveKlass") PrimitiveKlass cachedKlass,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        switch (cachedKlass.getJavaKind()) {
            case Boolean:
                if (interop.isBoolean(value)) {
                    return interop.asBoolean(value);
                }
                break;
            case Byte:
                if (interop.fitsInByte(value)) {
                    return interop.asByte(value);
                }
                break;
            case Short:
                if (interop.fitsInShort(value)) {
                    return interop.asShort(value);
                }
                break;
            case Char:
                if (interop.isString(value)) {
                    String str = interop.asString(value);
                    if (str.length() == 1) {
                        return str.charAt(0);
                    }
                }
                break;
            case Int:
                if (interop.fitsInInt(value)) {
                    return interop.asInt(value);
                }
                break;
            case Float:
                if (interop.fitsInFloat(value)) {
                    return interop.asFloat(value);
                }
                break;
            case Long:
                if (interop.fitsInLong(value)) {
                    return interop.asLong(value);
                }
                break;
            case Double:
                if (interop.fitsInDouble(value)) {
                    return interop.asDouble(value);
                }
                break;
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @TruffleBoundary
    @Specialization(replaces = "doPrimitive")
    Object doPrimitiveUncached(Object value, PrimitiveKlass primitiveKlass) throws UnsupportedMessageException, UnsupportedTypeException {
        return doPrimitive(value, primitiveKlass, InteropLibrary.getFactory().getUncached(), primitiveKlass, BranchProfile.getUncached());
    }

    static boolean isString(Klass klass) {
        return klass.getMeta().java_lang_String.equals(klass);
    }

    @Specialization(guards = "isString(stringKlass)")
    Object doString(Object value,
                    ObjectKlass stringKlass,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException, UnsupportedTypeException {
        if (interop.isNull(value)) {
            return StaticObject.NULL;
        } else if (interop.isString(value)) {
            String str = interop.asString(value);
            return stringKlass.getMeta().toGuestString(str);
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, stringKlass.getTypeAsString());
    }

    @TruffleBoundary
    @Specialization(guards = "isString(stringKlass)", replaces = "doString")
    Object doStringUncached(Object value,
                    ObjectKlass stringKlass) throws UnsupportedMessageException, UnsupportedTypeException {
        return doString(value, stringKlass, InteropLibrary.getFactory().getUncached(), BranchProfile.getUncached());
    }

    static boolean isStringArray(Klass klass) {
        return klass.getMeta().java_lang_String.array().equals(klass);
    }

    // TODO(peterssen): Remove, temporary workaround for passing arguments to main.
    @SuppressWarnings("unused")
    @Specialization(guards = {"isStringArray(stringArrayKlass)", "interop.hasArrayElements(value)"})
    Object doStringArraySlow(Object value,
                    ArrayKlass stringArrayKlass,
                    @CachedLibrary(limit = "0") InteropLibrary interop,
                    @Cached ToEspressoNode toEspressoNode)
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
                        return (StaticObject) toEspressoNode.execute(elem, jlString);
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

    @Specialization
    Object doEspressoObject(StaticObject value, Klass klass, @Cached BranchProfile exceptionProfile)
                    throws UnsupportedTypeException {
        if (StaticObject.isNull(value)) {
            return value;
        } else {
            if (InterpreterToVM.instanceOf(value, klass)) {
                return value;
            }
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
    }

    @Specialization(guards = {"!klass.isPrimitive()", "!isString(klass)", "!isStringArray(klass)"})
    Object doForeignNull(Object value, Klass klass, @CachedLibrary(limit = "LIMIT") InteropLibrary interop, @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
        if (interop.isNull(value)) {
            return StaticObject.NULL; // coercion to Espresso null.
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
    }

    @Specialization
    Object doUnsupported(Object value, Klass klass) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
    }

    @SuppressWarnings("unchecked")
    private static <T extends RuntimeException> boolean rethrow(Throwable e) throws T {
        throw (T) e;
    }
}
