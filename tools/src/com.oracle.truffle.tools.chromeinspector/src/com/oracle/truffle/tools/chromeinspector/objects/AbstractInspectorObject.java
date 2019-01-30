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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

/**
 * A base class for objects returned by Inspector module.
 */
@MessageResolution(receiverType = AbstractInspectorObject.class)
abstract class AbstractInspectorObject implements TruffleObject {

    private static final int METHOD_KEY_INFO = KeyInfo.READABLE | KeyInfo.INVOCABLE;

    protected AbstractInspectorObject() {
    }

    @Override
    public final ForeignAccess getForeignAccess() {
        return AbstractInspectorObjectForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof AbstractInspectorObject;
    }

    protected abstract TruffleObject getKeys();

    protected abstract boolean isField(String name);

    protected abstract boolean isMethod(String name);

    protected abstract Object getFieldValueOrNull(String name);

    protected abstract Object invokeMethod(String name, Object[] arguments);

    protected boolean isInstantiable() {
        return false;
    }

    @SuppressWarnings("unused")
    protected Object createNew(Object[] arguments) {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.NEW);
    }

    private TruffleObject createMethodExecutable(String name) {
        CompilerAsserts.neverPartOfCompilation();
        return new MethodExecutable(this, name);
    }

    @Resolve(message = "HAS_KEYS")
    abstract static class InspectorHasKeysNode extends Node {

        @SuppressWarnings("unused")
        public Object access(AbstractInspectorObject inspector) {
            return true;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class InspectorKeysNode extends Node {

        public Object access(AbstractInspectorObject inspector) {
            return inspector.getKeys();
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class InspectorKeyInfoNode extends Node {

        public Object access(AbstractInspectorObject inspector, String name) {
            if (inspector.isField(name)) {
                return KeyInfo.READABLE;
            } else if (inspector.isMethod(name)) {
                return METHOD_KEY_INFO;
            } else {
                return 0;
            }
        }
    }

    @Resolve(message = "READ")
    abstract static class InspectorReadNode extends Node {

        public Object access(AbstractInspectorObject inspector, String name) {
            Object value = inspector.getFieldValueOrNull(name);
            if (value == null) {
                value = getMethodExecutable(inspector, name);
            }
            return value;
        }

        @TruffleBoundary
        TruffleObject getMethodExecutable(AbstractInspectorObject inspector, String name) {
            if (inspector.isMethod(name)) {
                return inspector.createMethodExecutable(name);
            } else {
                throw UnknownIdentifierException.raise(name);
            }
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class InspectorInvokeNode extends Node {

        public Object access(AbstractInspectorObject inspector, String name, Object[] arguments) {
            return inspector.invokeMethod(name, arguments);
        }
    }

    @Resolve(message = "IS_INSTANTIABLE")
    abstract static class InspectorIsInstantiableNode extends Node {

        public Object access(AbstractInspectorObject inspector) {
            return inspector.isInstantiable();
        }
    }

    @Resolve(message = "NEW")
    abstract static class InspectorNewNode extends Node {

        public Object access(AbstractInspectorObject inspector, Object[] arguments) {
            return inspector.createNew(arguments);
        }
    }

    @MessageResolution(receiverType = MethodExecutable.class)
    static final class MethodExecutable implements TruffleObject {

        private final AbstractInspectorObject inspector;
        private final String name;

        MethodExecutable(AbstractInspectorObject inspector, String name) {
            this.inspector = inspector;
            this.name = name;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return MethodExecutableForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof MethodExecutable;
        }

        @Resolve(message = "IS_EXECUTABLE")
        abstract static class IsExecutableNode extends Node {

            @SuppressWarnings("unused")
            public Object access(MethodExecutable exec) {
                return true;
            }
        }

        @Resolve(message = "EXECUTE")
        abstract static class ExecuteNode extends Node {

            @Child private Node invokeNode;

            public Object access(MethodExecutable exec, Object[] arguments) {
                if (invokeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    invokeNode = insert(Message.INVOKE.createNode());
                }
                try {
                    return ForeignAccess.sendInvoke(invokeNode, exec.inspector, exec.name, arguments);
                } catch (ArityException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw ArityException.raise(ex.getExpectedArity(), ex.getActualArity());
                } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedMessageException.raise(Message.EXECUTE);
                } catch (UnsupportedTypeException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedTypeException.raise(ex.getSuppliedValues());
                }
            }
        }
    }
}
