/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessage;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropNodes;
import com.oracle.truffle.espresso.substitutions.Collect;

@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
@SuppressWarnings("truffle-abstract-export") // TODO GR-44080 Adopt BigInteger Interop
public class ForeignExceptionInterop extends ThrowableInterop {

    public static Object getRawForeignObject(StaticObject object) {
        Meta meta = object.getKlass().getMeta();
        assert object.getKlass() == meta.polyglot.ForeignException;
        return meta.java_lang_Throwable_backtrace.getObject(object).rawForeignObject(object.getKlass().getContext().getLanguage());
    }

    @ExportMessage
    public static ExceptionType getExceptionType(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        Object rawForeignException = getRawForeignObject(object);
        return InteropLibrary.getUncached().getExceptionType(rawForeignException);
    }

    @ExportMessage
    public static boolean hasExceptionCause(StaticObject object) {
        object.checkNotForeign();
        Object rawForeignException = getRawForeignObject(object);
        return InteropLibrary.getUncached().hasExceptionCause(rawForeignException);
    }

    @ExportMessage
    public static Object getExceptionCause(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        return InteropLibrary.getUncached().getExceptionCause(getRawForeignObject(object));
    }

    @ExportMessage
    public static boolean hasExceptionMessage(StaticObject object) {
        object.checkNotForeign();
        return InteropLibrary.getUncached().hasExceptionMessage(getRawForeignObject(object));
    }

    @ExportMessage
    public static Object getExceptionMessage(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        return InteropLibrary.getUncached().getExceptionMessage(getRawForeignObject(object));
    }

    @ExportMessage
    public static boolean hasExceptionStackTrace(StaticObject object) {
        object.checkNotForeign();
        return InteropLibrary.getUncached().hasExceptionStackTrace(getRawForeignObject(object));
    }

    @ExportMessage
    public static Object getExceptionStackTrace(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        return InteropLibrary.getUncached().getExceptionStackTrace(getRawForeignObject(object));
    }

    @ExportMessage
    public static RuntimeException throwException(StaticObject object) {
        object.checkNotForeign();
        throw (RuntimeException) getRawForeignObject(object);
    }

    @Collect(value = InteropNodes.class, getter = "getInstance")
    public static class Nodes extends InteropNodes {

        private static final InteropNodes INSTANCE = new Nodes();

        public static InteropNodes getInstance() {
            return INSTANCE;
        }

        public Nodes() {
            super(ForeignExceptionInterop.class, ThrowableInterop.Nodes.getInstance());
        }

        public void registerMessages(Class<?> cls) {
            InteropMessageFactory.register(cls, "getExceptionTypeNode", ForeignExceptionInteropFactory.NodesFactory.GetExceptionTypeNodeGen::create);
            InteropMessageFactory.register(cls, "hasExceptionCause", ForeignExceptionInteropFactory.NodesFactory.HasExceptionCauseNodeGen::create);
            InteropMessageFactory.register(cls, "getExceptionCause", ForeignExceptionInteropFactory.NodesFactory.GetExceptionCauseNodeGen::create);
            InteropMessageFactory.register(cls, "hasExceptionMessage", ForeignExceptionInteropFactory.NodesFactory.HasExceptionMessageNodeGen::create);
            InteropMessageFactory.register(cls, "getExceptionMessage", ForeignExceptionInteropFactory.NodesFactory.GetExceptionMessageNodeGen::create);
            InteropMessageFactory.register(cls, "hasExceptionStackTrace", ForeignExceptionInteropFactory.NodesFactory.HasExceptionStackTraceNodeGen::create);
            InteropMessageFactory.register(cls, "getExceptionStackTrace", ForeignExceptionInteropFactory.NodesFactory.GetExceptionStackTraceNodeGen::create);
            InteropMessageFactory.register(cls, "throwException", ForeignExceptionInteropFactory.NodesFactory.ThrowExceptionNodeGen::create);
        }

        abstract static class GetExceptionTypeNode extends InteropMessage.GetExceptionType {
            @Specialization
            static ExceptionType getExceptionType(StaticObject receiver) throws UnsupportedMessageException {
                return ForeignExceptionInterop.getExceptionType(receiver);
            }
        }

        abstract static class HasExceptionCauseNode extends InteropMessage.HasExceptionCause {
            @Specialization
            public static boolean hasExceptionCause(StaticObject object) {
                return ForeignExceptionInterop.hasExceptionCause(object);
            }
        }

        abstract static class GetExceptionCauseNode extends InteropMessage.GetExceptionCause {
            @Specialization
            public static Object getExceptionCause(StaticObject object) throws UnsupportedMessageException {
                return ForeignExceptionInterop.getExceptionCause(object);
            }
        }

        abstract static class HasExceptionMessageNode extends InteropMessage.HasExceptionMessage {
            @Specialization
            public static boolean hasExceptionMessage(StaticObject object) {
                return ForeignExceptionInterop.hasExceptionMessage(object);
            }
        }

        abstract static class GetExceptionMessageNode extends InteropMessage.GetExceptionMessage {
            @Specialization
            public static Object getExceptionMessage(StaticObject object) throws UnsupportedMessageException {
                return ForeignExceptionInterop.getExceptionMessage(object);
            }
        }

        abstract static class HasExceptionStackTraceNode extends InteropMessage.HasExceptionStackTrace {
            @Specialization
            public static boolean hasExceptionStackTrace(StaticObject object) {
                return ForeignExceptionInterop.hasExceptionStackTrace(object);
            }
        }

        abstract static class GetExceptionStackTraceNode extends InteropMessage.GetExceptionStackTrace {
            @Specialization
            public static Object getExceptionStackTrace(StaticObject object) throws UnsupportedMessageException {
                return ForeignExceptionInterop.getExceptionStackTrace(object);
            }
        }

        abstract static class ThrowExceptionNode extends InteropMessage.ThrowException {
            @Specialization
            public static RuntimeException throwException(StaticObject object) {
                return ForeignExceptionInterop.throwException(object);
            }
        }
    }
}
