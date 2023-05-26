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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropNodes;
import com.oracle.truffle.espresso.substitutions.Collect;
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
        return !isNull(object) && object.isMirrorKlass();
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
    @ExportMessage
    public static TriState isIdenticalOrUndefined(StaticObject receiver, Object other,
                    @Cached Nodes.IsIdenticalOrUndefinedNode message) {
        return message.execute(receiver, other);
    }

    @ExportMessage
    public static int identityHashCode(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        // Working with espresso objects here, guaranteed to have identity.
        return VM.JVM_IHashCode(object, null /*- path where language is needed is never reached through here. */);
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
    public static Object toDisplayString(StaticObject object, boolean allowSideEffects,
                    @Cached Nodes.ToDisplayStringNode node) {
        return node.execute(object, allowSideEffects);
    }

    // endregion ### Language/DisplayString

    @SuppressWarnings("unused")
    @Collect(value = InteropNodes.class, getter = "getInstance")
    public static class Nodes extends InteropNodes {

        private static final InteropNodes INSTANCE = new Nodes();

        public static InteropNodes getInstance() {
            return INSTANCE;
        }

        public Nodes() {
            super(BaseInterop.class, null);
        }

        @Override
        public void registerMessages(Class<?> cls) {
            InteropMessageFactory.register(cls, "isNull", BaseInteropFactory.NodesFactory.IsNullNodeGen::create);
            InteropMessageFactory.register(cls, "isString", BaseInteropFactory.NodesFactory.IsStringNodeGen::create);
            InteropMessageFactory.register(cls, "asString", BaseInteropFactory.NodesFactory.AsStringNodeGen::create);
            InteropMessageFactory.register(cls, "isMetaObject", BaseInteropFactory.NodesFactory.IsMetaObjectNodeGen::create);
            InteropMessageFactory.register(cls, "getMetaQualifiedName", BaseInteropFactory.NodesFactory.GetMetaQualifiedNameNodeGen::create);
            InteropMessageFactory.register(cls, "getMetaSimpleName", BaseInteropFactory.NodesFactory.GetMetaSimpleNameNodeGen::create);
            InteropMessageFactory.register(cls, "isMetaInstance", BaseInteropFactory.NodesFactory.IsMetaInstanceNodeGen::create);
            InteropMessageFactory.register(cls, "hasMetaObject", BaseInteropFactory.NodesFactory.HasMetaObjectNodeGen::create);
            InteropMessageFactory.register(cls, "getMetaObject", BaseInteropFactory.NodesFactory.GetMetaObjectNodeGen::create);
            InteropMessageFactory.register(cls, "hasMetaParents", BaseInteropFactory.NodesFactory.HasMetaParentsNodeGen::create);
            InteropMessageFactory.register(cls, "getMetaParents", BaseInteropFactory.NodesFactory.GetMetaParentsNodeGen::create);
            InteropMessageFactory.register(cls, "isIdenticalOrUndefined", BaseInteropFactory.NodesFactory.IsIdenticalOrUndefinedNodeGen::create);
            InteropMessageFactory.register(cls, "identityHashCode", BaseInteropFactory.NodesFactory.IdentityHashCodeNodeGen::create);
            InteropMessageFactory.register(cls, "hasLanguage", BaseInteropFactory.NodesFactory.HasLanguageNodeGen::create);
            InteropMessageFactory.register(cls, "getLanguage", BaseInteropFactory.NodesFactory.GetLanguageNodeGen::create);
            InteropMessageFactory.register(cls, "toDisplayString", BaseInteropFactory.NodesFactory.ToDisplayStringNodeGen::create);
        }

        abstract static class IsNullNode extends InteropMessage.IsNull {
            @Specialization
            boolean isNull(StaticObject receiver) {
                return BaseInterop.isNull(receiver);
            }
        }

        abstract static class IsStringNode extends InteropMessage.IsString {
            @Specialization
            boolean isString(StaticObject receiver) {
                return BaseInterop.isString(receiver);
            }
        }

        abstract static class AsStringNode extends InteropMessage.AsString {
            @Specialization
            String asString(StaticObject receiver) throws UnsupportedMessageException {
                return BaseInterop.asString(receiver);
            }
        }

        abstract static class IsMetaObjectNode extends InteropMessage.IsMetaObject {
            @Specialization
            boolean isMetaObject(StaticObject receiver) {
                return BaseInterop.isMetaObject(receiver);
            }
        }

        abstract static class GetMetaQualifiedNameNode extends InteropMessage.GetMetaQualifiedName {
            @Specialization
            Object getMetaQualifiedName(StaticObject receiver, @Cached BranchProfile error) throws UnsupportedMessageException {
                return BaseInterop.getMetaQualifiedName(receiver, error);
            }
        }

        abstract static class GetMetaSimpleNameNode extends InteropMessage.GetMetaSimpleName {
            @Specialization
            Object getMetaSimpleName(StaticObject receiver, @Cached BranchProfile error) throws UnsupportedMessageException {
                return BaseInterop.getMetaSimpleName(receiver, error);
            }
        }

        abstract static class IsMetaInstanceNode extends InteropMessage.IsMetaInstance {
            @Specialization
            boolean isMetaInstance(StaticObject receiver, Object instance,
                            @Cached BranchProfile error) throws UnsupportedMessageException {
                return BaseInterop.isMetaInstance(receiver, instance, error);
            }
        }

        abstract static class HasMetaObjectNode extends InteropMessage.HasMetaObject {
            @Specialization
            boolean hasMetaObject(StaticObject receiver) {
                return BaseInterop.hasMetaObject(receiver);
            }
        }

        abstract static class GetMetaObjectNode extends InteropMessage.GetMetaObject {
            @Specialization
            Object getMetaObject(StaticObject receiver, @Cached BranchProfile error) throws UnsupportedMessageException {
                return BaseInterop.getMetaObject(receiver, error);
            }
        }

        abstract static class HasMetaParentsNode extends InteropMessage.HasMetaParents {
            @Specialization
            public static boolean hasMetaParents(StaticObject receiver) {
                return BaseInterop.hasMetaParents(receiver);
            }
        }

        abstract static class GetMetaParentsNode extends InteropMessage.GetMetaParents {
            @Specialization
            public static Object getMetaParents(StaticObject receiver,
                            @Cached BranchProfile error) throws UnsupportedMessageException {
                return BaseInterop.getMetaParents(receiver, error);
            }
        }

        abstract static class IsIdenticalOrUndefinedNode extends InteropMessage.IsIdenticalOrUndefined {
            @Specialization
            public static TriState doStaticObject(StaticObject receiver, StaticObject other) {
                receiver.checkNotForeign();
                other.checkNotForeign();
                return receiver == other ? TriState.TRUE : TriState.FALSE;
            }

            @Fallback
            static TriState doOther(@SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object other) {
                return TriState.UNDEFINED;
            }
        }

        abstract static class IdentityHashCodeNode extends InteropMessage.IdentityHashCode {
            @Specialization
            static int identityHashCode(StaticObject receiver) throws UnsupportedMessageException {
                return BaseInterop.identityHashCode(receiver);
            }
        }

        abstract static class HasLanguageNode extends InteropMessage.HasLanguage {
            @Specialization
            static boolean hasLanguage(StaticObject receiver) {
                return BaseInterop.hasLanguage(receiver);
            }
        }

        abstract static class GetLanguageNode extends InteropMessage.GetLanguage {
            @Specialization
            static Class<? extends TruffleLanguage<?>> getLanguage(StaticObject receiver) {
                return BaseInterop.getLanguage(receiver);
            }
        }

        abstract static class ToDisplayStringNode extends InteropMessage.ToDisplayString {
            @Specialization
            static Object doStaticObject(StaticObject receiver, boolean allowSideEffects) {
                if (receiver.isForeignObject()) {
                    if (receiver.getKlass() == null) {
                        return "Foreign receiver: null";
                    }
                    InteropLibrary interopLibrary = InteropLibrary.getUncached();
                    try {
                        EspressoLanguage language = receiver.getKlass().getContext().getLanguage();
                        return "Foreign receiver: " + interopLibrary.asString(interopLibrary.toDisplayString(receiver.rawForeignObject(language), allowSideEffects));
                    } catch (UnsupportedMessageException e) {
                        throw EspressoError.shouldNotReachHere("Interop library failed to convert display string to string");
                    }
                }
                if (StaticObject.isNull(receiver)) {
                    return "NULL";
                }
                Klass thisKlass = receiver.getKlass();
                Meta meta = thisKlass.getMeta();
                if (allowSideEffects) {
                    // Call guest toString.
                    int toStringIndex = meta.java_lang_Object_toString.getVTableIndex();
                    Method toString = thisKlass.vtableLookup(toStringIndex);
                    return meta.toHostString((StaticObject) toString.invokeDirect(receiver));
                }

                // Handle some special instances without side effects.
                if (thisKlass == meta.java_lang_Class) {
                    return "class " + thisKlass.getTypeAsString();
                }
                if (thisKlass == meta.java_lang_String) {
                    return meta.toHostString(receiver);
                }
                return thisKlass.getTypeAsString() + "@" + Integer.toHexString(System.identityHashCode(receiver));
            }
        }
    }
}
