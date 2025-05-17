/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import static com.oracle.truffle.espresso.vm.InterpreterToVM.instanceOf;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.IHashCodeNode;
import com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes;
import com.oracle.truffle.espresso.runtime.dispatch.messages.Shareable;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * BaseInterop (isNull, is/asString, meta-instance, identity, exceptions, toDisplayString) Support
 * Espresso and foreign objects and null.
 */
@GenerateInteropNodes
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@Shareable
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
        return !isNull(object) && object.isMirrorKlass();
    }

    @ExportMessage
    public static Object getMetaQualifiedName(StaticObject object,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (isMetaObject(object)) {
            return object.getMirrorKlass().getGuestTypeName();
        }
        error.enter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public static Object getMetaSimpleName(StaticObject object,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (isMetaObject(object)) {
            return object.getKlass().getMeta().java_lang_Class_getSimpleName.invokeDirectSpecial(object);
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

    @ExportMessage
    public static boolean hasMetaParents(StaticObject object) {
        if (object.isForeignObject()) {
            return false;
        }
        if (isMetaObject(object)) {
            Klass mirrorKlass = object.getMirrorKlass();
            if (mirrorKlass.isInterface()) {
                return mirrorKlass.getSuperInterfaces().length > 0;
            }
            return !mirrorKlass.isPrimitive() && mirrorKlass != object.getKlass().getMeta().java_lang_Object;
        }
        return false;
    }

    @ExportMessage
    public static Object getMetaParents(StaticObject object,
                    @Cached.Shared("error") @Cached BranchProfile error) throws UnsupportedMessageException {
        object.checkNotForeign();

        if (hasMetaParents(object)) {
            Klass klass = object.getMirrorKlass();
            StaticObject[] result;
            if (klass.isInterface()) {
                Klass[] superInterfaces = klass.getSuperInterfaces();
                result = new StaticObject[superInterfaces.length];

                for (int i = 0; i < superInterfaces.length; i++) {
                    result[i] = superInterfaces[i].mirror();
                }
            } else {
                Klass superClass = klass.getSuperKlass();
                Klass[] superInterfaces = klass.getSuperInterfaces();

                result = new StaticObject[superInterfaces.length + 1];
                // put the super class first in array
                result[0] = superClass.mirror();
                // then all interfaces
                for (int i = 0; i < superInterfaces.length; i++) {
                    result[i + 1] = superInterfaces[i].mirror();
                }
            }
            return StaticObject.wrap(result, object.getKlass().getMeta());
        }

        error.enter();
        throw UnsupportedMessageException.create();
    }

    // endregion ### Meta-objects

    // region ### Identity/hashCode

    @GenerateUncached
    abstract static class IsIdenticalOrUndefinedImplNode extends Node {
        public abstract TriState execute(StaticObject receiver, Object other);

        @Specialization
        public static TriState doStaticObject(StaticObject receiver, StaticObject other) {
            receiver.checkNotForeign();
            other.checkNotForeign();
            return receiver == other ? TriState.TRUE : TriState.FALSE;
        }

        @Fallback
        static TriState doOther(@SuppressWarnings("unused") StaticObject receiver, @SuppressWarnings("unused") Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    public static TriState isIdenticalOrUndefined(StaticObject receiver, Object other,
                    @Cached IsIdenticalOrUndefinedImplNode node) {
        return node.execute(receiver, other);
    }

    @ExportMessage
    public static int identityHashCode(StaticObject object,
                    @Cached IHashCodeNode iHashCodeNode) {
        object.checkNotForeign();
        if (StaticObject.isNull(object)) {
            return 0;
        }
        // Working with espresso objects here, guaranteed to have identity.
        return iHashCodeNode.execute(object);
    }

    // endregion ### Identity/hashCode

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
                return "Foreign receiver: null";
            }
            InteropLibrary interopLibrary = InteropLibrary.getUncached();
            try {
                EspressoLanguage language = object.getKlass().getContext().getLanguage();
                return "Foreign receiver: " + interopLibrary.asString(interopLibrary.toDisplayString(object.rawForeignObject(language), allowSideEffects));
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
            return meta.toHostString((StaticObject) meta.java_lang_Object_toString.invokeDirectVirtual(object));
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
