/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.tools.chromeinspector.objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.TriState;

/**
 * A base class for objects returned by Inspector module.
 */
@ExportLibrary(InteropLibrary.class)
abstract class AbstractInspectorObject implements TruffleObject {

    static final int LIMIT = 3;

    protected AbstractInspectorObject() {
    }

    @ExportMessage
    protected abstract Object getMemberObjects();

    protected abstract boolean isField(String name);

    protected abstract boolean isMethod(String name);

    protected abstract Object getFieldValueOrNull(String name);

    protected abstract Object invokeMethod(String name, Object member, Object[] arguments) throws UnsupportedTypeException, UnknownMemberException, ArityException, UnsupportedMessageException;

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean hasMembers() {
        return true;
    }

    static String getString(InteropLibrary library, Object object) {
        String name;
        try {
            name = library.asString(object);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
        return name;
    }

    @ExportMessage
    static class IsMemberReadable {

        @Specialization
        static boolean isReadable(AbstractInspectorObject receiver, FieldMember field) {
            return receiver.isField(field.getName());
        }

        @Specialization
        static boolean isReadable(AbstractInspectorObject receiver, MethodMember method) {
            return receiver.isMethod(method.getName());
        }

        @Specialization(guards = {"memberLibrary.isString(memberString)"})
        static boolean isReadable(AbstractInspectorObject receiver, Object memberString,
                        @Cached.Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) {
            String name = getString(memberLibrary, memberString);
            return receiver.isField(name) || receiver.isMethod(name);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean isReadable(AbstractInspectorObject receiver, Object unknownMember) {
            return false;
        }
    }

    @ExportMessage
    static class IsMemberInvocable {

        @Specialization
        static boolean isInvocable(AbstractInspectorObject receiver, MethodMember method) {
            return receiver.isMethod(method.getName());
        }

        @Specialization(guards = {"memberLibrary.isString(memberString)"})
        static boolean isInvocable(AbstractInspectorObject receiver, Object memberString,
                        @Cached.Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) {
            String name = getString(memberLibrary, memberString);
            return receiver.isMethod(name);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean isReadable(AbstractInspectorObject receiver, Object unknownMember) {
            return false;
        }
    }

    @ExportMessage
    static class ReadMember {

        @Specialization
        static Object read(AbstractInspectorObject receiver, FieldMember field) throws UnknownMemberException {
            String name = field.getName();
            Object value = receiver.getFieldValueOrNull(name);
            if (value == null) {
                return value;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnknownMemberException.create(field);
            }
        }

        @Specialization
        static Object read(AbstractInspectorObject receiver, MethodMember method) {
            return receiver.createMethodExecutable(method);
        }

        @Specialization(guards = {"memberLibrary.isString(memberString)"})
        static Object read(AbstractInspectorObject receiver, Object memberString,
                        @Cached.Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException {
            String name = getString(memberLibrary, memberString);
            Object value = receiver.getFieldValueOrNull(name);
            if (value == null) {
                value = receiver.getMethodExecutable(name, memberString);
            }
            return value;
        }

        @Fallback
        static Object read(@SuppressWarnings("unused") AbstractInspectorObject receiver, Object unknownMember) throws UnknownMemberException {
            CompilerDirectives.transferToInterpreter();
            throw UnknownMemberException.create(unknownMember);
        }
    }

    @TruffleBoundary
    private TruffleObject getMethodExecutable(String name, Object member) throws UnknownMemberException {
        if (isMethod(name)) {
            return createMethodExecutable(member);
        } else {
            throw UnknownMemberException.create(member);
        }
    }

    @ExportMessage
    static class InvokeMember {

        @Specialization(guards = {"receiver.isMethod(method.getName())"})
        static Object invoke(AbstractInspectorObject receiver, MethodMember method, Object[] arguments)
                        throws UnsupportedTypeException, UnknownMemberException, ArityException, UnsupportedMessageException {
            return receiver.invokeMethod(method.getName(), method, arguments);
        }

        @Specialization(guards = {"memberLibrary.isString(member)"})
        static Object invoke(AbstractInspectorObject receiver, Object member, Object[] arguments,
                        @Cached.Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary)
                        throws UnsupportedTypeException, UnknownMemberException, ArityException, UnsupportedMessageException {
            String name = getString(memberLibrary, member);
            return receiver.invokeMethod(name, member, arguments);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object invoke(AbstractInspectorObject receiver, Object unknownMember, Object[] arguments) throws UnknownMemberException {
            CompilerDirectives.transferToInterpreter();
            throw UnknownMemberException.create(unknownMember);
        }
    }

    @ExportMessage
    protected boolean isInstantiable() {
        return false;
    }

    @SuppressWarnings("unused")
    @ExportMessage
    protected Object instantiate(Object[] arguments) throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    TriState isIdenticalOrUndefined(Object other) {
        if (getClass() == other.getClass()) {
            return TriState.valueOf(this == other);
        } else {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    int identityHashCode() {
        return hashCode();
    }

    @TruffleBoundary
    private TruffleObject createMethodExecutable(Object member) {
        return new MethodExecutable(this, member);
    }

    @ExportLibrary(InteropLibrary.class)
    abstract static sealed class AbstractMember implements TruffleObject permits FieldMember, MethodMember {

        private final String name;

        AbstractMember(String name) {
            this.name = name;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMember() {
            return true;
        }

        @ExportMessage
        Object getMemberSimpleName() {
            return name;
        }

        @ExportMessage
        Object getMemberQualifiedName() {
            return name;
        }

        @ExportMessage
        boolean isMemberKindField() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMethod() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMetaObject() {
            return false;
        }

        String getName() {
            return name;
        }
    }

    static final class FieldMember extends AbstractMember {

        FieldMember(String name) {
            super(name);
        }

        @Override
        boolean isMemberKindField() {
            return true;
        }
    }

    static final class MethodMember extends AbstractMember {

        MethodMember(String name) {
            super(name);
        }

        @Override
        boolean isMemberKindMethod() {
            return true;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MethodExecutable implements TruffleObject {

        final AbstractInspectorObject inspector;
        private final Object member;

        MethodExecutable(AbstractInspectorObject inspector, Object member) {
            this.inspector = inspector;
            this.member = member;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments, @CachedLibrary("this.inspector") InteropLibrary interop)
                        throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            try {
                return interop.invokeMember(inspector, member, arguments);
            } catch (UnknownMemberException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
            }
        }
    }
}
