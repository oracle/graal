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

package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.interop.LookupAndInvokeKnownMethodNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropNodes;
import com.oracle.truffle.espresso.substitutions.Collect;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@SuppressWarnings("truffle-abstract-export") // TODO GR-44080 Adopt BigInteger Interop
public class IteratorInterop extends EspressoInterop {
    @ExportMessage
    public static boolean isIterator(@SuppressWarnings("unused") StaticObject receiver) {
        return true;
    }

    @ExportMessage
    public static boolean hasIteratorNextElement(StaticObject receiver,
                    @Bind("getMeta().java_util_Iterator_hasNext") Method hasNext,
                    @Cached LookupAndInvokeKnownMethodNode lookupAndInvoke) {
        return (boolean) lookupAndInvoke.execute(receiver, hasNext);
    }

    @ExportMessage
    static Object getIteratorNextElement(StaticObject receiver,
                    @Bind("getMeta().java_util_Iterator_next") Method next,
                    @Cached LookupAndInvokeKnownMethodNode lookupAndInvoke) throws StopIterationException {
        try {
            return lookupAndInvoke.execute(receiver, next);
        } catch (EspressoException e) {
            if (InterpreterToVM.instanceOf(e.getGuestException(), receiver.getKlass().getMeta().java_util_NoSuchElementException)) {
                throw StopIterationException.create(e);
            }
            throw e;
        }
    }

    @Collect(value = InteropNodes.class, getter = "getInstance")
    public static class Nodes extends InteropNodes {

        private static final InteropNodes INSTANCE = new Nodes();

        public static InteropNodes getInstance() {
            return INSTANCE;
        }

        public Nodes() {
            super(IteratorInterop.class, EspressoInterop.Nodes.getInstance());
        }

        public void registerMessages(Class<?> cls) {
            InteropMessageFactory.register(cls, "isIterator", IteratorInteropFactory.NodesFactory.IsIteratorNodeGen::create);
            InteropMessageFactory.register(cls, "hasIteratorNextElement", IteratorInteropFactory.NodesFactory.HasIteratorNextElementNodeGen::create);
            InteropMessageFactory.register(cls, "getIteratorNextElement", IteratorInteropFactory.NodesFactory.GetIteratorNextElementNodeGen::create);
        }

        abstract static class IsIteratorNode extends InteropMessage.IsIterator {
            @Specialization
            static boolean isIterator(StaticObject receiver) {
                return IteratorInterop.isIterator(receiver);
            }
        }

        abstract static class HasIteratorNextElementNode extends InteropMessage.HasIteratorNextElement {
            @Specialization
            static boolean hasIteratorNextElement(StaticObject receiver,
                            @Bind("getMeta().java_util_Iterator_hasNext") Method hasNext,
                            @Cached LookupAndInvokeKnownMethodNode lookupAndInvoke) {
                return IteratorInterop.hasIteratorNextElement(receiver, hasNext, lookupAndInvoke);
            }
        }

        abstract static class GetIteratorNextElementNode extends InteropMessage.GetIteratorNextElement {
            @Specialization
            static Object getIteratorNextElement(StaticObject receiver,
                            @Bind("getMeta().java_util_Iterator_next") Method next,
                            @Cached LookupAndInvokeKnownMethodNode lookupAndInvoke) throws StopIterationException {
                return IteratorInterop.getIteratorNextElement(receiver, next, lookupAndInvoke);
            }
        }
    }
}
