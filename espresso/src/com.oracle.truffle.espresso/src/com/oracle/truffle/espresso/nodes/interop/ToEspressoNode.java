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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@GenerateUncached
public abstract class ToEspressoNode extends Node {
    static final int LIMIT = 2;

    public abstract Object execute(Object value, Klass targetType) throws UnsupportedTypeException;

    @Specialization(guards = "cachedKlass == primitiveKlass", limit = "LIMIT")
    Object doPrimitive(Object value,
                    PrimitiveKlass primitiveKlass,
                    @CachedLibrary("value") InteropLibrary interop,
                    @Cached("primitiveKlass") PrimitiveKlass cachedKlass,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
        try {
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
                case Void:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("Unexpected cast to void");
            }
        } catch (UnsupportedMessageException e) {
            exceptionProfile.enter();
            throw EspressoError.shouldNotReachHere("Contract violation: if fitsIn{type} returns true, as{type} must succeed.");
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @TruffleBoundary
    @Specialization(replaces = "doPrimitive")
    Object doPrimitiveUncached(Object value, PrimitiveKlass primitiveKlass) throws UnsupportedTypeException {
        return doPrimitive(value, primitiveKlass, InteropLibrary.getFactory().getUncached(value), primitiveKlass, BranchProfile.getUncached());
    }

    @Specialization(guards = "!klass.isPrimitive()")
    Object doEspresso(StaticObject value,
                    Klass klass,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
        // TODO(peterssen): Use a node for the instanceof check.
        if (StaticObject.isNull(value) || InterpreterToVM.instanceOf(value, klass)) {
            return value; // pass through, NULL coercion not needed.
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
    }

    static boolean isStaticObject(Object obj) {
        return obj instanceof StaticObject;
    }

    static boolean isString(Klass klass) {
        return klass.getMeta().java_lang_String.equals(klass);
    }

    static boolean isStringCompatible(Klass klass) {
        return klass.isAssignableFrom(klass.getMeta().java_lang_String);
    }

    static boolean isStringArray(Klass klass) {
        return klass.getMeta().java_lang_String.array().equals(klass);
    }

    static boolean isForeignException(Klass klass) {
        Meta meta = klass.getMeta();
        return meta.polyglot != null /* polyglot enabled */ && meta.polyglot.ForeignException.equals(klass);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isStaticObject(value)", "interop.isNull(value)", "!klass.isPrimitive()"})
    Object doForeignNull(Object value, Klass klass, @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
    }

    @Specialization(guards = {"isStringCompatible(klass)"})
    Object doHostString(String value, ObjectKlass klass) {
        return klass.getMeta().toGuestString(value);
    }

    @Specialization(guards = {"!isStaticObject(value)", "!interop.isNull(value)", "isString(klass)"})
    Object doString(Object value,
                    ObjectKlass klass,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile)
                    throws UnsupportedTypeException {
        if (interop.isString(value)) {
            try {
                return klass.getMeta().toGuestString(interop.asString(value));
            } catch (UnsupportedMessageException e) {
                exceptionProfile.enter();
                throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
            }
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
    }

    @Specialization(guards = {"!isStaticObject(value)", "!interop.isNull(value)", "isForeignException(klass)"})
    Object doForeignException(Object value, ObjectKlass klass,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached InitCheck initCheck) throws UnsupportedTypeException {
        if (!interop.isException(value)) {
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s", klass.getNameAsString()));
        }
        initCheck.execute(klass);
        return StaticObject.createForeignException(klass.getMeta(), value, interop);
    }

    @Specialization(guards = {"!isStaticObject(value)", "!interop.isNull(value)", "!isString(klass)", "!isForeignException(klass)", "!klass.isAbstract()"})
    Object doForeignClass(Object value, ObjectKlass klass,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached BranchProfile errorProfile,
                    @Cached InitCheck initCheck) throws UnsupportedTypeException {
        try {
            checkHasAllFieldsOrThrow(value, klass, interop, EspressoContext.get(this).getMeta());
        } catch (ClassCastException e) {
            errorProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", klass.getNameAsString(), e.getMessage()));
        }
        initCheck.execute(klass);
        return StaticObject.createForeign(EspressoLanguage.get(this), klass, value, interop);
    }

/*
 * TODO(goltsova): split this into abstract classes and interfaces once casting to interfaces is
 * supported
 */
    @Specialization(guards = {"!isStaticObject(value)", "!interop.isNull(value)", "klass.isAbstract() || klass.isInterface()"})
    Object doForeignAbstract(Object value, ObjectKlass klass,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
    }

    @Specialization(guards = {"!isStaticObject(value)", "!interop.isNull(value)"})
    Object doForeignArray(Object value, ArrayKlass klass,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached BranchProfile errorProfile) throws UnsupportedTypeException {
        Meta meta = EspressoContext.get(this).getMeta();
        // Buffer-like can be casted to byte[] only.
        // Array-like can be casted to *[].
        if ((klass == meta._byte_array && interop.hasBufferElements(value)) || interop.hasArrayElements(value)) {
            return StaticObject.createForeign(EspressoLanguage.get(this), klass, value, interop);
        }
        errorProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, "Cannot cast a non-array value to an array type");

    }

    public static void checkHasAllFieldsOrThrow(Object value, ObjectKlass klass, InteropLibrary interopLibrary, Meta meta) {
        /*
         * For boxed types a .value member is not required if there's a direct conversion via
         * interop as* methods.
         */
        if (meta.isBoxed(klass)) {
            try {
                if ((klass == meta.java_lang_Integer && interopLibrary.fitsInInt(value)) ||
                                (klass == meta.java_lang_Long && interopLibrary.fitsInLong(value)) ||
                                (klass == meta.java_lang_Float && interopLibrary.fitsInFloat(value)) ||
                                (klass == meta.java_lang_Double && interopLibrary.fitsInDouble(value)) ||
                                (klass == meta.java_lang_Boolean && interopLibrary.isBoolean(value)) ||
                                (klass == meta.java_lang_Short && interopLibrary.fitsInShort(value)) ||
                                (klass == meta.java_lang_Byte && interopLibrary.fitsInByte(value)) ||
                                (klass == meta.java_lang_Character && interopLibrary.isString(value) && interopLibrary.asString(value).length() == 1)) {
                    return;
                }
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        for (Field f : klass.getDeclaredFields()) {
            if (!f.isStatic() && !interopLibrary.isMemberExisting(value, f.getNameAsString())) {
                throw new ClassCastException("Missing field: " + f.getNameAsString());
            }
        }
        if (klass.getSuperClass() != null) {
            checkHasAllFieldsOrThrow(value, klass.getSuperKlass(), interopLibrary, meta);
        }
    }
}
