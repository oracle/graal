/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Encapsulates types of access to {@link TruffleObject}. If you want to expose your own objects to
 * foreign language implementations, you need to implement {@link TruffleObject} and its
 * {@link TruffleObject#getForeignAccess()} method. To create instance of <code>ForeignAccess</code>
 * , use one of the factory methods available in this class.
 *
 * @since 0.8 or earlier
 */
final class LibraryToLegacy {

    static Object send(InteropAccessNode foreignNode, TruffleObject receiver, Object... arguments) throws InteropException {
        try {
            return foreignNode.execute(receiver, arguments);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    static Object sendRead(InteropAccessNode readNode, TruffleObject receiver, Object identifier) throws UnknownIdentifierException, UnsupportedMessageException {
        try {
            return readNode.execute(receiver, identifier);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        } catch (UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static Object sendWrite(InteropAccessNode writeNode, TruffleObject receiver, Object identifier, Object value)
                    throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        try {
            return writeNode.execute(receiver, identifier, value);
        } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static boolean sendRemove(InteropAccessNode removeNode, TruffleObject receiver, Object identifier)
                    throws UnknownIdentifierException, UnsupportedMessageException {
        try {
            return (boolean) removeNode.execute(receiver, identifier);
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static Object sendUnbox(InteropAccessNode unboxNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            return unboxNode.execute(receiver);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static boolean sendIsPointer(InteropAccessNode isPointerNode, TruffleObject receiver) {
        try {
            return (boolean) isPointerNode.executeOrFalse(receiver);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static long sendAsPointer(InteropAccessNode asPointerNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            return (long) asPointerNode.execute(receiver);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    static Object sendToNative(InteropAccessNode toNativeNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            return toNativeNode.execute(receiver);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static Object sendExecute(InteropAccessNode executeNode, TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        try {
            return executeNode.execute(receiver, arguments);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static boolean sendIsExecutable(InteropAccessNode isExecutableNode, TruffleObject receiver) {
        try {
            return (boolean) isExecutableNode.executeOrFalse(receiver);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static boolean sendIsInstantiable(InteropAccessNode isInstantiableNode, TruffleObject receiver) {
        try {
            return (boolean) isInstantiableNode.executeOrFalse(receiver);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static Object sendInvoke(InteropAccessNode invokeNode, TruffleObject receiver, String identifier, Object... arguments)
                    throws UnsupportedTypeException, ArityException, UnknownIdentifierException, UnsupportedMessageException {
        try {
            return invokeNode.execute(receiver, identifier, arguments);
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static Object sendNew(InteropAccessNode newNode, TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        try {
            return newNode.execute(receiver, arguments);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static boolean sendIsNull(InteropAccessNode isNullNode, TruffleObject receiver) {
        try {
            return (boolean) isNullNode.executeOrFalse(receiver);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static boolean sendHasSize(InteropAccessNode hasSizeNode, TruffleObject receiver) {
        try {
            return (boolean) hasSizeNode.executeOrFalse(receiver);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static Object sendGetSize(InteropAccessNode getSizeNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            return getSizeNode.execute(receiver);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static boolean sendIsBoxed(InteropAccessNode isBoxedNode, TruffleObject receiver) {
        try {
            return (boolean) isBoxedNode.executeOrFalse(receiver);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    @SuppressWarnings("deprecation")
    static int sendKeyInfo(InteropAccessNode keyInfoNode, TruffleObject receiver, Object identifier) {
        try {
            return (Integer) keyInfoNode.execute(receiver, identifier);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            try {
                if (identifier instanceof String) {
                    InteropLibrary uncached = InteropLibrary.getFactory().getUncached();
                    Object keys = uncached.getMembers(receiver);
                    long size = uncached.getArraySize(keys);
                    for (long i = 0; i < size; i++) {
                        Object key = uncached.readArrayElement(keys, i);
                        // identifier must not be null
                        if (identifier.equals(key)) {
                            return 0b111;
                        }
                    }
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException uex) {
            }
            try {
                if (identifier instanceof Number) {
                    InteropLibrary uncached = InteropLibrary.getFactory().getUncached(receiver);
                    boolean hasSize = uncached.hasArrayElements(receiver);
                    if (hasSize) {
                        int id = ((Number) identifier).intValue();
                        if (id < 0 || id != ((Number) identifier).doubleValue()) {
                            // identifier is some wild double number
                            return 0;
                        }
                        long size = uncached.getArraySize(receiver);
                        if (id < size) {
                            return 0b111;
                        }
                    }
                }
            } catch (UnsupportedMessageException uex) {
            }
            return 0;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    @SuppressWarnings("deprecation")
    static boolean sendHasKeys(InteropAccessNode hasKeysNode, TruffleObject receiver) {
        try {
            return (boolean) hasKeysNode.execute(receiver);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            try {
                sendKeys(InteropAccessNode.getUncached(Message.KEYS), receiver, true);
                return true;
            } catch (UnsupportedMessageException uex) {
                return false;
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

    static TruffleObject sendKeys(InteropAccessNode keysNode, TruffleObject receiver, boolean includeInternal) throws UnsupportedMessageException {
        try {
            return (TruffleObject) send(keysNode, receiver, includeInternal);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw ex;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(AssertUtils.violationPost(receiver, e), e);
        }
    }

}
