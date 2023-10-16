/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Handles conversions of (potentially) foreign objects to primitive types.
 */
@NodeInfo(shortName = "Convert value to primitive")
public abstract class ToPrimitive extends ToEspressoNode {

    @NodeInfo(shortName = "To boolean")
    @GenerateUncached
    public abstract static class ToBoolean extends ToPrimitive {

        protected static final int LIMIT = 2;

        @Specialization
        boolean doHost(Boolean value) {
            return value;
        }

        @Specialization
        boolean doEspresso(StaticObject value,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            if (value != null && !StaticObject.isNull(value) && value.getKlass() == getMeta().java_lang_Boolean) {
                return (boolean) getMeta().java_lang_Boolean_value.get(value);
            }
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to boolean"));
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostBoolean(value)",
                        "interop.isBoolean(value)"
        })
        boolean doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return interop.asBoolean(value);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if isBoolean returns true, asBoolean must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "boolean");
        }

        static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        static boolean isHostBoolean(Object value) {
            return value instanceof Boolean;
        }
    }

    @NodeInfo(shortName = "To int")
    @GenerateUncached
    public abstract static class ToInt extends ToPrimitive {

        protected static final int LIMIT = 2;

        @Specialization
        int doHost(Integer value) {
            return value;
        }

        @Specialization
        int doEspresso(StaticObject value,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            if (value != null && !StaticObject.isNull(value) && value.getKlass() == getMeta().java_lang_Integer) {
                return (int) getMeta().java_lang_Integer_value.get(value);
            }
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to int"));
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostInteger(value)",
                        "interop.fitsInInt(value)"
        })
        int doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return interop.asInt(value);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsInInt returns true, asInt must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "int");
        }

        static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        static boolean isHostInteger(Object value) {
            return value instanceof Integer;
        }
    }

    @NodeInfo(shortName = "To byte")
    @GenerateUncached
    public abstract static class ToByte extends ToPrimitive {

        protected static final int LIMIT = 2;

        @Specialization
        byte doHost(Byte value) {
            return value;
        }

        @Specialization
        byte doEspresso(StaticObject value,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            if (value != null && !StaticObject.isNull(value) && value.getKlass() == getMeta().java_lang_Byte) {
                return (byte) getMeta().java_lang_Byte_value.get(value);
            }
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to byte"));
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostByte(value)",
                        "interop.fitsInByte(value)"
        })
        byte doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return interop.asByte(value);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsInByte returns true, asByte must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "byte");
        }

        static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        static boolean isHostByte(Object value) {
            return value instanceof Byte;
        }
    }

    @NodeInfo(shortName = "To short")
    @GenerateUncached
    public abstract static class ToShort extends ToPrimitive {

        protected static final int LIMIT = 2;

        @Specialization
        short doHost(Short value) {
            return value;
        }

        @Specialization
        short doEspresso(StaticObject value,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            if (value != null && !StaticObject.isNull(value) && value.getKlass() == getMeta().java_lang_Short) {
                return (short) getMeta().java_lang_Short_value.get(value);
            }
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to short"));
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostShort(value)",
                        "interop.fitsInShort(value)"
        })
        short doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return interop.asShort(value);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsInShort returns true, asShort must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "short");
        }

        static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        static boolean isHostShort(Object value) {
            return value instanceof Short;
        }
    }

    @NodeInfo(shortName = "To char")
    @GenerateUncached
    public abstract static class ToChar extends ToPrimitive {

        protected static final int LIMIT = 2;

        @Specialization
        char doHost(Character value) {
            return value;
        }

        @Specialization
        char doEspresso(StaticObject value,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            if (value != null && !StaticObject.isNull(value) && value.getKlass() == getMeta().java_lang_Character) {
                return (char) getMeta().java_lang_Character_value.get(value);
            }
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to char"));
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostCharacter(value)",
                        "interop.isString(value)"
        })
        char doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            try {
                String s = interop.asString(value);
                if (s.length() == 1) {
                    return s.charAt(0);
                }
                exceptionProfile.enter();
                throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", s, " to char"));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsInInt returns true, asInt must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "char");
        }

        static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        static boolean isHostCharacter(Object value) {
            return value instanceof Character;
        }
    }

    @NodeInfo(shortName = "To long")
    @GenerateUncached
    public abstract static class ToLong extends ToPrimitive {

        protected static final int LIMIT = 2;

        @Specialization
        long doHost(Long value) {
            return value;
        }

        @Specialization
        long doEspresso(StaticObject value,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            if (value != null && !StaticObject.isNull(value) && value.getKlass() == getMeta().java_lang_Long) {
                return (long) getMeta().java_lang_Long_value.get(value);
            }
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to long"));
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostLong(value)",
                        "interop.fitsInLong(value)"
        })
        long doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return interop.asLong(value);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsInLong returns true, asLong must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "long");
        }

        static boolean isHostLong(Object value) {
            return value instanceof Long;
        }

        static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }
    }

    @NodeInfo(shortName = "To float")
    @GenerateUncached
    public abstract static class ToFloat extends ToPrimitive {

        protected static final int LIMIT = 2;

        @Specialization
        float doHost(Float value) {
            return value;
        }

        @Specialization
        float doEspresso(StaticObject value,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            if (value != null && !StaticObject.isNull(value) && value.getKlass() == getMeta().java_lang_Float) {
                return (float) getMeta().java_lang_Float_value.get(value);
            }
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to float"));
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostFloat(value)",
                        "interop.fitsInFloat(value)"
        })
        float doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return interop.asFloat(value);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsInFloat returns true, asFloat must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "float");
        }

        static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        static boolean isHostFloat(Object value) {
            return value instanceof Float;
        }
    }

    @NodeInfo(shortName = "To double")
    @GenerateUncached
    public abstract static class ToDouble extends ToPrimitive {

        protected static final int LIMIT = 2;

        @Specialization
        double doHost(Double value) {
            return value;
        }

        @Specialization
        double doEspresso(StaticObject value,
                        @Cached BranchProfile exceptionProfile) throws UnsupportedTypeException {
            if (value != null && !StaticObject.isNull(value) && value.getKlass() == getMeta().java_lang_Double) {
                return (double) getMeta().java_lang_Double_value.get(value);
            }
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, EspressoError.cat("Cannot cast ", value, " to double"));
        }

        @Specialization(guards = {
                        "!isStaticObject(value)",
                        "!isHostDouble(value)",
                        "interop.fitsInDouble(value)"
        })
        double doForeign(Object value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            try {
                return interop.asDouble(value);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Contract violation: if fitsInDouble returns true, asDouble must succeed.");
            }
        }

        @Fallback
        StaticObject doUnsupportedType(Object value) throws UnsupportedTypeException {
            throw UnsupportedTypeException.create(new Object[]{value}, "double");
        }

        static boolean isStaticObject(Object value) {
            return value instanceof StaticObject;
        }

        static boolean isHostDouble(Object value) {
            return value instanceof Double;
        }
    }
}
