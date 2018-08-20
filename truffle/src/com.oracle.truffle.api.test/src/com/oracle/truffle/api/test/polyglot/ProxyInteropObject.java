/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.util.Arrays;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.StandardFactory;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxySPITest.TestFunction;

/**
 * Helper class for tests to simplify the declaration of interop objects.
 */
@SuppressWarnings("unused")
public abstract class ProxyInteropObject implements TruffleObject {

    public Object execute(Object[] args) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.EXECUTE);
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof ProxyInteropObject;
    }

    public boolean hasKeys() {
        return false;
    }

    public boolean hasSize() {
        return false;
    }

    public boolean isNull() {
        return false;
    }

    public boolean isBoxed() {
        return false;
    }

    public boolean isPointer() {
        return false;
    }

    public boolean isExecutable() {
        return false;
    }

    public boolean isInstantiable() {
        return false;
    }

    public long asPointer() throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.AS_POINTER);
    }

    public int getSize() {
        return 0;
    }

    public Object unbox() throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.UNBOX);

    }

    public Object read(String key) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.READ);
    }

    public Object read(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.READ);
    }

    public Object write(String key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.WRITE);
    }

    public Object write(Number key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.WRITE);
    }

    public boolean remove(String key) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.REMOVE);
    }

    public boolean remove(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.REMOVE);
    }

    public Object invoke(String key, Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.INVOKE);
    }

    public Object newInstance(Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.NEW);
    }

    public Object keys() throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.KEYS);
    }

    public int keyInfo(String key) {
        return KeyInfo.NONE;
    }

    public int keyInfo(Number key) {
        return KeyInfo.NONE;
    }

    public Object message(Message message, Object[] arguments) {
        return null;
    }

    public Object toNative() throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.KEYS);
    }

    public ForeignAccess getForeignAccess() {
        return TestObjectFactory.INSTANCE;
    }

    private static final class TestObjectFactory implements StandardFactory {

        private static final ForeignAccess INSTANCE = ForeignAccess.create(ProxyInteropObject.class, new TestObjectFactory());

        private abstract static class BaseNode extends RootNode {

            protected BaseNode() {
                super(null);
            }

            @Override
            public final Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                try {
                    return executeImpl((ProxyInteropObject) args[0], args);
                } catch (InteropException e) {
                    throw e.raise();
                }
            }

            protected abstract Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException;

        }

        private static CallTarget create(BaseNode baseNode) {
            return Truffle.getRuntime().createCallTarget(baseNode);
        }

        public CallTarget accessExecute(int argumentsLength) {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.execute(Arrays.copyOfRange(arguments, 1, arguments.length));
                }
            });
        }

        public CallTarget accessAsPointer() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.asPointer();
                }
            });
        }

        public CallTarget accessGetSize() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.getSize();
                }
            });
        }

        public CallTarget accessHasKeys() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.hasKeys();
                }
            });
        }

        public CallTarget accessIsNull() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isNull();
                }
            });
        }

        public CallTarget accessIsExecutable() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isExecutable();
                }
            });
        }

        public CallTarget accessIsInstantiable() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isInstantiable();
                }
            });
        }

        public CallTarget accessIsBoxed() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isBoxed();
                }
            });
        }

        public CallTarget accessHasSize() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.hasSize();
                }
            });
        }

        public CallTarget accessUnbox() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.unbox();
                }
            });
        }

        public CallTarget accessRead() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    if (key instanceof Number) {
                        return receiver.read((Number) key);
                    } else if (key instanceof String) {
                        return receiver.read((String) key);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.raise(Message.READ);
                    }
                }
            });
        }

        public CallTarget accessWrite() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    Object value = arguments[2];
                    if (key instanceof Number) {
                        return receiver.write((Number) key, value);
                    } else if (key instanceof String) {
                        return receiver.write((String) key, value);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.raise(Message.WRITE);
                    }
                }
            });
        }

        public CallTarget accessRemove() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    if (key instanceof Number) {
                        return receiver.remove((Number) key);
                    } else if (key instanceof String) {
                        return receiver.remove((String) key);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.raise(Message.REMOVE);
                    }
                }
            });
        }

        public CallTarget accessInvoke(int argumentsLength) {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    if (key instanceof String) {
                        return receiver.invoke((String) key, Arrays.copyOfRange(arguments, 2, arguments.length));
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.raise(Message.INVOKE);
                    }
                }
            });
        }

        public CallTarget accessNew(int argumentsLength) {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.newInstance(Arrays.copyOfRange(arguments, 1, arguments.length));
                }
            });
        }

        public CallTarget accessKeys() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.keys();
                }
            });
        }

        public CallTarget accessKeyInfo() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    if (key instanceof Number) {
                        return receiver.keyInfo((Number) key);
                    } else if (key instanceof String) {
                        return receiver.keyInfo((String) key);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.raise(Message.REMOVE);
                    }
                }
            });
        }

        public CallTarget accessIsPointer() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isPointer();
                }
            });
        }

        public CallTarget accessToNative() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.toNative();
                }
            });
        }

        public CallTarget accessMessage(Message unknown) {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.message(unknown, arguments);
                }
            });
        }
    }
}
