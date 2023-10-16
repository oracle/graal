/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.interop.LookupAndInvokeKnownMethodNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.InteropUtils;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes;
import com.oracle.truffle.espresso.runtime.dispatch.messages.Shareable;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@GenerateInteropNodes
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@SuppressWarnings("truffle-abstract-export") // TODO GR-44080 Adopt BigInteger Interop
public final class ListInterop extends IterableInterop {

    @ExportMessage
    @Shareable
    static boolean hasArrayElements(@SuppressWarnings("unused") StaticObject receiver) {
        return true;
    }

    @ExportMessage
    static long getArraySize(StaticObject receiver,
                    @Bind("getMeta().java_util_List_size") Method listSizeMethod,
                    @Cached.Shared("size") @Cached LookupAndInvokeKnownMethodNode size) {
        return (int) size.execute(receiver, listSizeMethod);
    }

    @ExportMessage
    static Object readArrayElement(StaticObject receiver, long index,
                    @Cached ListGet listGet,
                    @Bind("getMeta().java_util_List_size") Method listSizeMethod,
                    @Cached.Shared("size") @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached @Shared("error") BranchProfile error) throws InvalidArrayIndexException {
        if (!boundsCheck(receiver, index, listSizeMethod, size)) {
            error.enter();
            throw InvalidArrayIndexException.create(index);
        }
        return listGet.listGet(receiver, index, error);
    }

    @ExportMessage
    static void writeArrayElement(StaticObject receiver, long index, Object value,
                    @Cached ListSet listSet,
                    @Cached ListAdd listAdd,
                    @Bind("getMeta().java_util_List_size") Method listSizeMethod,
                    @Cached.Shared("size") @Cached LookupAndInvokeKnownMethodNode size,
                    @Cached @Shared("error") BranchProfile error) throws InvalidArrayIndexException, UnsupportedMessageException {
        int listSize = (int) size.execute(receiver, listSizeMethod);
        if (!boundsCheck(index, listSize)) {
            // append new element if allowed
            if (index == listSize) {
                listAdd.listAdd(receiver, value, error);
                return;
            } else {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }
        // replace existing element
        listSet.listSet(receiver, index, value, error);
    }

    @ExportMessage
    static boolean isArrayElementReadable(StaticObject receiver, long index,
                    @Bind("getMeta().java_util_List_size") Method listSizeMethod,
                    @Cached.Shared("size") @Cached LookupAndInvokeKnownMethodNode size) {
        return boundsCheck(receiver, index, listSizeMethod, size);
    }

    @ExportMessage
    static boolean isArrayElementModifiable(StaticObject receiver, long index,
                    @Bind("getMeta().java_util_List_size") Method listSizeMethod,
                    @Cached.Shared("size") @Cached LookupAndInvokeKnownMethodNode size) {
        return boundsCheck(receiver, index, listSizeMethod, size);
    }

    @ExportMessage
    static boolean isArrayElementInsertable(@SuppressWarnings("unused") StaticObject receiver, @SuppressWarnings("unused") long index) {
        // we can't easily determine is the guest list is modifiable or not,
        // so we return true here and let writeArrayElement fail in case the
        // associated guest method throws
        return true;
    }

    // TODO(GR-38619): isArrayElementRemovable

    private static boolean boundsCheck(StaticObject receiver, long index, Method listSize,
                    LookupAndInvokeKnownMethodNode size) {
        return boundsCheck(index, (int) size.execute(receiver, listSize));
    }

    private static boolean boundsCheck(long index, int size) {
        return 0 <= index && index < size;
    }

    @GenerateUncached
    abstract static class ListGet extends EspressoNode {
        static final int LIMIT = 3;

        public Object listGet(StaticObject receiver, long index, BranchProfile error) throws InvalidArrayIndexException {
            try {
                return InteropUtils.unwrap(getLanguage(), execute(receiver, (int) index), getMeta());
            } catch (EspressoException e) {
                error.enter();
                if (InterpreterToVM.instanceOf(e.getGuestException(), receiver.getKlass().getMeta().java_lang_IndexOutOfBoundsException)) {
                    throw InvalidArrayIndexException.create(index);
                }
                throw e; // unexpected exception
            }
        }

        protected abstract Object execute(StaticObject receiver, int index);

        @Specialization
        static Object doCached(StaticObject receiver, int index,
                        @Bind("getMeta().java_util_List_get") Method getMethod,
                        @Cached LookupAndInvokeKnownMethodNode lookupAndInvoke) {
            return lookupAndInvoke.execute(receiver, getMethod, new Object[]{index});
        }
    }

    @GenerateUncached
    abstract static class ListSet extends EspressoNode {
        static final int LIMIT = 3;

        public void listSet(StaticObject receiver, long index, Object value, BranchProfile error) throws InvalidArrayIndexException {
            try {
                execute(receiver, (int) index, value);
            } catch (EspressoException e) {
                error.enter();
                if (InterpreterToVM.instanceOf(e.getGuestException(), receiver.getKlass().getMeta().java_lang_IndexOutOfBoundsException)) {
                    throw InvalidArrayIndexException.create(index);
                }
                throw e; // unexpected exception
            } catch (ArityException | UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
            }
        }

        protected abstract void execute(StaticObject receiver, int index, Object value) throws ArityException, UnsupportedTypeException;

        @Specialization
        static void doCached(StaticObject receiver, int index, Object value,
                        @Bind("getMeta().java_util_List_set") Method setMethod,
                        @Cached LookupAndInvokeKnownMethodNode lookupAndInvoke) {
            lookupAndInvoke.execute(receiver, setMethod, new Object[]{index, value});
        }
    }

    @GenerateUncached
    abstract static class ListAdd extends EspressoNode {
        static final int LIMIT = 3;

        public void listAdd(StaticObject receiver, Object value, BranchProfile error) throws UnsupportedMessageException {
            try {
                execute(receiver, value);
            } catch (EspressoException e) {
                error.enter();
                if (InterpreterToVM.instanceOf(e.getGuestException(), receiver.getKlass().getMeta().java_lang_UnsupportedOperationException)) {
                    // the guest java code might throw UnsupportedOperationException in a variety of
                    // cases, including cases that are more about "index out of bounds", but since
                    // we cannot distinguish, we throw the more general UnsupportedMessageException
                    throw UnsupportedMessageException.create();
                }
                throw e; // unexpected exception
            } catch (ArityException | UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
            }
        }

        protected abstract void execute(StaticObject receiver, Object value) throws ArityException, UnsupportedTypeException;

        @Specialization
        static void doCached(StaticObject receiver, Object value,
                        @Bind("getMeta().java_util_List_add") Method addMethod,
                        @Cached LookupAndInvokeKnownMethodNode lookupAndInvoke) {
            lookupAndInvoke.execute(receiver, addMethod, new Object[]{value});
        }
    }
}
