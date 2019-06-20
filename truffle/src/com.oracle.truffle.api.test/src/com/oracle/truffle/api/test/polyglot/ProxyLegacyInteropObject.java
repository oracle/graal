/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import java.util.Arrays;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Helper class for tests to simplify the declaration of interop objects.
 */
@SuppressWarnings({"unused", "deprecation"})
public abstract class ProxyLegacyInteropObject implements TruffleObject {

    public Object execute(Object[] args) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof ProxyLegacyInteropObject;
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
        throw UnsupportedMessageException.create();
    }

    public int getSize() throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public Object unbox() throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();

    }

    public Object read(String key) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public Object read(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public Object write(String key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public Object write(Number key, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public boolean remove(String key) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public boolean remove(Number key) throws UnsupportedMessageException, UnknownIdentifierException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public Object invoke(String key, Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public Object newInstance(Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public Object keys() throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public int keyInfo(String key) {
        return com.oracle.truffle.api.interop.KeyInfo.NONE;
    }

    public int keyInfo(Number key) {
        return com.oracle.truffle.api.interop.KeyInfo.NONE;
    }

    public Object message(com.oracle.truffle.api.interop.Message message, Object[] arguments) {
        return null;
    }

    public Object toNative() throws UnsupportedMessageException {
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    public com.oracle.truffle.api.interop.ForeignAccess getForeignAccess() {
        return TestObjectFactory.INSTANCE;
    }

    private static final class TestObjectFactory implements com.oracle.truffle.api.interop.ForeignAccess.StandardFactory {

        private static final com.oracle.truffle.api.interop.ForeignAccess INSTANCE = com.oracle.truffle.api.interop.ForeignAccess.create(ProxyLegacyInteropObject.class, new TestObjectFactory());

        private abstract static class BaseNode extends RootNode {

            protected BaseNode() {
                super(null);
            }

            @Override
            public final Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                try {
                    return executeImpl((ProxyLegacyInteropObject) args[0], args);
                } catch (InteropException e) {
                    throw e.raise();
                }
            }

            protected abstract Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException;

        }

        private static CallTarget create(BaseNode baseNode) {
            return Truffle.getRuntime().createCallTarget(baseNode);
        }

        public CallTarget accessExecute(int argumentsLength) {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.execute(Arrays.copyOfRange(arguments, 1, arguments.length));
                }
            });
        }

        public CallTarget accessAsPointer() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.asPointer();
                }
            });
        }

        public CallTarget accessGetSize() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.getSize();
                }
            });
        }

        public CallTarget accessHasKeys() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.hasKeys();
                }
            });
        }

        public CallTarget accessIsNull() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isNull();
                }
            });
        }

        public CallTarget accessIsExecutable() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isExecutable();
                }
            });
        }

        public CallTarget accessIsInstantiable() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isInstantiable();
                }
            });
        }

        public CallTarget accessIsBoxed() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isBoxed();
                }
            });
        }

        public CallTarget accessHasSize() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.hasSize();
                }
            });
        }

        public CallTarget accessUnbox() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.unbox();
                }
            });
        }

        public CallTarget accessRead() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    if (key instanceof Number) {
                        return receiver.read((Number) key);
                    } else if (key instanceof String) {
                        return receiver.read((String) key);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.create();
                    }
                }
            });
        }

        public CallTarget accessWrite() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    Object value = arguments[2];
                    if (key instanceof Number) {
                        return receiver.write((Number) key, value);
                    } else if (key instanceof String) {
                        return receiver.write((String) key, value);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.create();
                    }
                }
            });
        }

        public CallTarget accessRemove() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    if (key instanceof Number) {
                        return receiver.remove((Number) key);
                    } else if (key instanceof String) {
                        return receiver.remove((String) key);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.create();
                    }
                }
            });
        }

        public CallTarget accessInvoke(int argumentsLength) {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    if (key instanceof String) {
                        return receiver.invoke((String) key, Arrays.copyOfRange(arguments, 2, arguments.length));
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.create();
                    }
                }
            });
        }

        public CallTarget accessNew(int argumentsLength) {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.newInstance(Arrays.copyOfRange(arguments, 1, arguments.length));
                }
            });
        }

        public CallTarget accessKeys() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.keys();
                }
            });
        }

        public CallTarget accessKeyInfo() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    Object key = arguments[1];
                    if (key instanceof Number) {
                        return receiver.keyInfo((Number) key);
                    } else if (key instanceof String) {
                        return receiver.keyInfo((String) key);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnsupportedMessageException.create();
                    }
                }
            });
        }

        public CallTarget accessIsPointer() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.isPointer();
                }
            });
        }

        public CallTarget accessToNative() {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.toNative();
                }
            });
        }

        public CallTarget accessMessage(com.oracle.truffle.api.interop.Message unknown) {
            return create(new BaseNode() {
                @Override
                protected Object executeImpl(ProxyLegacyInteropObject receiver, Object[] arguments) throws InteropException {
                    return receiver.message(unknown, arguments);
                }
            });
        }
    }
}
