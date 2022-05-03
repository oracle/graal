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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Handles conversions of (potentially) foreign objects to Espresso types.
 */
@GenerateUncached
public abstract class ToEspressoNode extends EspressoNode {

    public static final int LIMIT = 2;

    // region Specialization predicates

    static boolean isStaticObject(Object obj) {
        return obj instanceof StaticObject;
    }

    static boolean isHostString(Object object) {
        return object instanceof String;
    }

    static boolean isString(Klass klass) {
        return klass.getMeta().java_lang_String.equals(klass);
    }

    static boolean isStringCompatible(Klass klass) {
        // Accept String superclasses and superinterfaces.
        return klass.isAssignableFrom(klass.getMeta().java_lang_String);
    }

    static boolean isByteArray(Klass klass) {
        return klass.getMeta()._byte_array.equals(klass);
    }

    static boolean isPrimitiveKlass(Klass klass) {
        return klass instanceof PrimitiveKlass;
    }

    static boolean isForeignException(Klass klass) {
        Meta meta = klass.getMeta();
        return meta.polyglot != null /* polyglot enabled */ && meta.polyglot.ForeignException.equals(klass);
    }

    static boolean isEspressoException(Object object) {
        return object instanceof EspressoException;
    }

    // endregion Specialization predicates

    public abstract Object execute(Object value, Klass targetType) throws UnsupportedTypeException;

    @Specialization
    Object doEspresso(StaticObject value, Klass klass,
                    @Cached BranchProfile exceptionProfile,
                    @Cached InstanceOf.Dynamic instanceOf) throws UnsupportedTypeException {
        if (StaticObject.isNull(value) || instanceOf.execute(value.getKlass(), klass)) {
            return value; // pass through, NULL coercion not needed.
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
    }

    @Specialization
    Object doPrimitive(Object value,
                    PrimitiveKlass primitiveKlass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
        try {
            // @formatter:off
            switch (primitiveKlass.getJavaKind()) {
                case Boolean : return interop.asBoolean(value);
                case Byte    : return interop.asByte(value);
                case Short   : return interop.asShort(value);
                case Int     : return interop.asInt(value);
                case Float   : return interop.asFloat(value);
                case Long    : return interop.asLong(value);
                case Double  : return interop.asDouble(value);
                case Char: {
                    String str = interop.asString(value);
                    if (str.length() == 1) {
                        return str.charAt(0);
                    }
                    break;
                }
                case Void:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere("Unexpected cast to void");
            }
            // @formatter:on
        } catch (UnsupportedMessageException e) {
            // fall-through
        }
        exceptionProfile.enter();
        throw UnsupportedTypeException.create(new Object[]{value}, primitiveKlass.getTypeAsString());
    }

    @Specialization
    Object doEspressoException(EspressoException value, ObjectKlass klass) throws UnsupportedTypeException {
        return execute(value.getGuestException(), klass);
    }

    @Specialization(guards = "isStringCompatible(klass)")
    Object doHostString(String value, ObjectKlass klass) {
        return klass.getMeta().toGuestString(value);
    }

    @Specialization(guards = {
                    "!isStaticObject(value)",
                    "interop.isNull(value)",
                    "!isPrimitiveKlass(klass)"
    })
    Object doForeignNull(Object value, @SuppressWarnings("unused") Klass klass,
                    @SuppressWarnings("unused") @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        return StaticObject.createForeignNull(EspressoLanguage.get(this), value);
    }

    @Specialization(guards = {
                    "isString(klass)",
                    "!isStaticObject(value)",
                    "interop.isString(value)",
                    "!isHostString(value)",
                    // !interop.isNull(value), // redundant
                    // "!isEspressoException(value)", // redundant
    })
    Object doForeignString(Object value, ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        try {
            String hostString = interop.asString(value);
            return klass.getMeta().toGuestString(hostString);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Contract violation: if isString returns true, asString must succeed.");
        }
    }

    @Specialization(guards = {
                    "isForeignException(klass)",
                    "!isStaticObject(value)",
                    "interop.isException(value)",
                    "!isEspressoException(value)",
                    // !interop.isNull(value), // redundant
                    // "!isHostString(value)", // redundant
    })
    Object doForeignException(Object value, ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached InitCheck initCheck) {
        initCheck.execute(klass);
        return StaticObject.createForeignException(klass.getMeta(), value, interop);
    }

    @Specialization(guards = {
                    "interop.hasArrayElements(value)",
                    "!isStaticObject(value)",
                    "!interop.isNull(value)",
                    "!isHostString(value)",
                    "!isEspressoException(value)"
    })
    Object doForeignArray(Object value, ArrayKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        return StaticObject.createForeign(EspressoLanguage.get(this), klass, value, interop);
    }

    @Specialization(guards = {
                    "isByteArray(klass)",
                    "!isStaticObject(value)",
                    "interop.hasBufferElements(value)",
                    "!interop.isNull(value)",
                    "!isHostString(value)",
                    "!isEspressoException(value)",
    })
    Object doForeignBuffer(Object value, ArrayKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
        return StaticObject.createForeign(EspressoLanguage.get(this), klass, value, interop);
    }

    @Specialization(guards = {
                    "!isStaticObject(value)",
                    "!interop.isNull(value)",
                    "!isHostString(value)",
                    "!isEspressoException(value)",
                    "!isForeignException(klass)",
                    "!klass.isAbstract()",
                    "!isString(klass)"
    })
    Object doForeignConcreteClassWrapper(Object value, ObjectKlass klass,
                    @Shared("value") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached BranchProfile errorProfile,
                    @Cached InitCheck initCheck) throws UnsupportedTypeException {
        // Skip expensive checks for java.lang.Object.
        if (!klass.isJavaLangObject()) {
            try {
                checkHasAllFieldsOrThrow(value, klass, interop, getMeta());
            } catch (ClassCastException e) {
                errorProfile.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.format("Could not cast foreign object to %s: ", klass.getNameAsString(), e.getMessage()));
            }
        }
        initCheck.execute(klass);
        return StaticObject.createForeign(getLanguage(), klass, value, interop);
    }

    @Fallback
    Object doUnsupportedType(Object value, Klass klass) throws UnsupportedTypeException {
        throw UnsupportedTypeException.create(new Object[]{value}, klass.getTypeAsString());
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
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
