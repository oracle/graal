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

package com.oracle.truffle.espresso.runtime.dispatch;

import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

/**
 * BaseInterop (isNull, is/asString, meta-instance, identity, exceptions, toDisplayString) Support
 * Espresso and foreign objects and null.
 */
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class BaseInterop {
    @ExportMessage
    public static boolean isNull(StaticObject object) {
        return StaticObject.isNull(object);
    }

    @ExportMessage
    public static boolean isString(StaticObject object) {
        return StaticObject.notNull(object) && object.getKlass() == object.getKlass().getMeta().java_lang_String;
    }

    @ExportMessage
    public static String asString(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (!isString(object)) {
            throw UnsupportedMessageException.create();
        }
        return Meta.toHostStringStatic(object);
    }

    // region ### Meta-objects

    @ExportMessage
    public static boolean isMetaObject(StaticObject object) {
        object.checkNotForeign();
        return !isNull(object) && object.getKlass() == object.getKlass().getMeta().java_lang_Class;
    }

    @ExportMessage
    public static Object getMetaQualifiedName(StaticObject object,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (isMetaObject(object)) {
            return object.getMirrorKlass().getTypeName();
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public static Object getMetaSimpleName(StaticObject object,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (isMetaObject(object)) {
            return object.getKlass().getMeta().java_lang_Class_getSimpleName.invokeDirect(object);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public static boolean isMetaInstance(StaticObject object, Object instance,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (isMetaObject(object)) {
            return instance instanceof StaticObject && instanceOf((StaticObject) instance, object.getMirrorKlass());
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public static boolean hasMetaObject(StaticObject object) {
        if (object.isForeignObject()) {
            return false;
        }
        return !isNull(object);
    }

    @ExportMessage
    public static Object getMetaObject(StaticObject object,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (hasMetaObject(object)) {
            return object.getKlass().mirror();
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Meta-objects

    // region ### Identity/hashCode

    @ExportMessage
    public static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doStaticObject(StaticObject receiver, StaticObject other) {
            receiver.checkNotForeign();
            other.checkNotForeign();
            return receiver == other ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(@SuppressWarnings("unused") StaticObject receiver, @SuppressWarnings("unused") Object other) {
            receiver.checkNotForeign();
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    public static int identityHashCode(StaticObject object,
                    @CachedLibrary("object") InteropLibrary thisLibrary, @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (thisLibrary.hasIdentity(object)) {
            return VM.JVM_IHashCode(object, null /*- path where language is needed is never reached through here. */);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Identity/hashCode

    // region ### Exceptions

    @ExportMessage
    public static boolean isException(StaticObject object) {
        object.checkNotForeign();
        return !isNull(object) && instanceOf(object, object.getKlass().getMeta().java_lang_Throwable);
    }

    @ExportMessage
    public static RuntimeException throwException(StaticObject object,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (isException(object)) {
            throw object.getKlass().getMeta().throwException(object);
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Exceptions

    // region ### Language/DisplayString

    @SuppressWarnings("unused")
    @ExportMessage
    public static boolean hasLanguage(StaticObject object) {
        return true;
    }

    @SuppressWarnings("unused")
    @ExportMessage
    public static Class<? extends TruffleLanguage<?>> getLanguage(StaticObject object) {
        return EspressoLanguage.class;
    }

    @ExportMessage
    @TruffleBoundary
    public static Object toDisplayString(StaticObject object, boolean allowSideEffects) {
        if (object.isForeignObject()) {
            if (object.getKlass() == null) {
                return "Foreign object: null";
            }
            InteropLibrary interopLibrary = InteropLibrary.getUncached();
            try {
                EspressoLanguage language = object.getKlass().getContext().getLanguage();
                return "Foreign object: " + interopLibrary.asString(interopLibrary.toDisplayString(object.rawForeignObject(language), allowSideEffects));
            } catch (UnsupportedMessageException e) {
                throw EspressoError.shouldNotReachHere("Interop library failed to convert display string to string");
            }
        }
        if (StaticObject.isNull(object)) {
            return "NULL";
        }
        Klass thisKlass = object.getKlass();
        Meta meta = thisKlass.getMeta();
        if (allowSideEffects) {
            // Call guest toString.
            int toStringIndex = meta.java_lang_Object_toString.getVTableIndex();
            Method toString = thisKlass.vtableLookup(toStringIndex);
            return meta.toHostString((StaticObject) toString.invokeDirect(object));
        }

        // Handle some special instances without side effects.
        if (thisKlass == meta.java_lang_Class) {
            return "class " + thisKlass.getTypeAsString();
        }
        if (thisKlass == meta.java_lang_String) {
            return meta.toHostString(object);
        }
        return thisKlass.getTypeAsString() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    // endregion ### Language/DisplayString
}
