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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("deprecation")
final class LegacyToLibraryNode extends Node {

    private static final int LIMIT = 5;

    final Message message;

    @Child private InteropLibrary interop;
    @Child private InteropAccessNode legacyUnbox;
    @Child private InteropAccessNode legacyIsBoxed;
    @Child private InteropAccessNode legacyToNative;
    @Child private InteropAccessNode legacyRemove;

    private LegacyToLibraryNode(Message message) {
        this.message = message;
        this.interop = insert(InteropLibrary.getFactory().createDispatched(LIMIT));
        this.legacyUnbox = insert(InteropAccessNode.create(Message.UNBOX));
        this.legacyIsBoxed = insert(InteropAccessNode.create(Message.IS_BOXED));
        this.legacyToNative = insert(InteropAccessNode.create(Message.TO_NATIVE));
        this.legacyRemove = insert(InteropAccessNode.create(Message.REMOVE));
    }

    static final class AdoptRootNode extends RootNode {

        AdoptRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            assert false;
            return null;
        }

        LegacyToLibraryNode insertAccess(LegacyToLibraryNode node) {
            return insert(node);
        }
    }

    static LegacyToLibraryNode create(Message message) {
        if (message instanceof KnownMessage) {
            /*
             * Cached Truffle libraries need to be adopted. This was not necessary for legacy to
             * library interop nodes, therefore we create a dummy root node and adopt it. This
             * overhead will go away as soon as all languages migrated.
             */
            return new AdoptRootNode().insertAccess(new LegacyToLibraryNode(message));
        }
        throw new IllegalArgumentException();
    }

    Object sendRead(TruffleObject receiver, Object identifier) throws UnknownIdentifierException, UnsupportedMessageException {
        if (identifier instanceof String) {
            return interop.readMember(receiver, (String) identifier);
        } else if (identifier instanceof Number) {
            try {
                final long index = asLongIndex(identifier);
                return interop.readArrayElement(receiver, index);
            } catch (InvalidArrayIndexException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(e.getInvalidIndex()));
            }
        } else {
            CompilerDirectives.transferToInterpreter();
            if (identifier instanceof TruffleObject) {
                // since we cannot pass through TruffleObjects as identifiers for messages anymore
                // we need to unbox it to a primitive to be compatible. This case don't need to be
                // fast.
                CompilerDirectives.transferToInterpreter();
                TruffleObject identifierTO = (TruffleObject) identifier;
                if (LibraryToLegacy.sendIsBoxed(legacyIsBoxed, identifierTO)) {
                    try {
                        return sendRead(receiver, LibraryToLegacy.sendUnbox(legacyUnbox, identifierTO));
                    } catch (UnsupportedMessageException e) {
                    }
                }
            }
            throw UnsupportedMessageException.create();
        }
    }

    private static long asLongIndex(Object identifier) {
        if (identifier instanceof Integer) {
            return (int) identifier;
        } else if (identifier instanceof Long) {
            return (long) identifier;
        } else {
            return boundaryToLong(identifier);
        }
    }

    void sendWrite(TruffleObject receiver, Object identifier, Object value)
                    throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        if (identifier instanceof String) {
            interop.writeMember(receiver, (String) identifier, value);
        } else if (identifier instanceof Number) {
            try {
                final long index = asLongIndex(identifier);
                interop.writeArrayElement(receiver, index, value);
            } catch (InvalidArrayIndexException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(e.getInvalidIndex()));
            }
        } else {
            CompilerDirectives.transferToInterpreter();
            if (identifier instanceof TruffleObject) {
                // since we cannot pass through TruffleObjects as identifiers for messages anymore
                // we need to unbox it to a primitive to be compatible. This case don't need to be
                // fast.
                CompilerDirectives.transferToInterpreter();
                TruffleObject identifierTO = (TruffleObject) identifier;
                if (LibraryToLegacy.sendIsBoxed(legacyIsBoxed, identifierTO)) {
                    try {
                        sendWrite(receiver, LibraryToLegacy.sendUnbox(legacyUnbox, identifierTO), value);
                        return;
                    } catch (UnsupportedMessageException e) {
                    }
                }
            }
            throw UnsupportedMessageException.create();
        }
    }

    boolean sendRemove(TruffleObject receiver, Object identifier)
                    throws UnknownIdentifierException, UnsupportedMessageException {
        if (identifier instanceof String) {
            if (receiver.getForeignAccess() != null) {
                return LibraryToLegacy.sendRemove(legacyRemove, receiver, identifier);
            }

            interop.removeMember(receiver, (String) identifier);
            return true;
        } else if (identifier instanceof Number) {
            try {
                if (receiver.getForeignAccess() != null) {
                    return LibraryToLegacy.sendRemove(legacyRemove, receiver, identifier);
                }
                final long index = asLongIndex(identifier);
                interop.removeArrayElement(receiver, index);
                return true;
            } catch (InvalidArrayIndexException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(String.valueOf(e.getInvalidIndex()));
            }
        } else {
            CompilerDirectives.transferToInterpreter();
            if (identifier instanceof TruffleObject) {
                // since we cannot pass through TruffleObjects as identifiers for messages anymore
                // we need to unbox it to a primitive to be compatible. This case don't need to be
                // fast.
                CompilerDirectives.transferToInterpreter();
                TruffleObject identifierTO = (TruffleObject) identifier;
                if (LibraryToLegacy.sendIsBoxed(legacyIsBoxed, identifierTO)) {
                    try {
                        return sendRemove(receiver, LibraryToLegacy.sendUnbox(legacyUnbox, identifierTO));
                    } catch (UnsupportedMessageException e) {
                    }
                }
            }
            throw UnsupportedMessageException.create();
        }
    }

    Object sendUnbox(TruffleObject receiver) throws UnsupportedMessageException {
        if (receiver.getForeignAccess() != null) {
            // if not yet migrated
            return LibraryToLegacy.sendUnbox(legacyUnbox, receiver);
        }
        if (interop.isNumber(receiver)) {
            // there is the hope that is this order causes the least compatibility trouble
            // because int and double are very commonly supported types
            if (interop.fitsInByte(receiver)) {
                return interop.asByte(receiver);
            } else if (interop.fitsInShort(receiver)) {
                return interop.asShort(receiver);
            } else if (interop.fitsInInt(receiver)) {
                return interop.asInt(receiver);
            } else if (interop.fitsInLong(receiver)) {
                return interop.asLong(receiver);
            } else if (interop.fitsInFloat(receiver)) {
                return interop.asFloat(receiver);
            } else if (interop.fitsInDouble(receiver)) {
                return interop.asDouble(receiver);
            }
        } else if (interop.isString(receiver)) {
            return interop.asString(receiver);
        } else if (interop.isBoolean(receiver)) {
            return interop.asBoolean(receiver);
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    boolean sendIsPointer(TruffleObject receiver) {
        return interop.isPointer(receiver);
    }

    long sendAsPointer(TruffleObject receiver) throws UnsupportedMessageException {
        return interop.asPointer(receiver);
    }

    Object sendToNative(TruffleObject receiver) throws UnsupportedMessageException {
        if (receiver.getForeignAccess() != null) {
            return LibraryToLegacy.sendToNative(legacyToNative, receiver);
        }
        interop.toNative(receiver);
        return receiver;
    }

    Object sendExecute(TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return interop.execute(receiver, arguments);
    }

    boolean sendIsExecutable(TruffleObject receiver) {
        return interop.isExecutable(receiver);
    }

    boolean sendIsInstantiable(TruffleObject receiver) {
        return interop.isInstantiable(receiver);
    }

    Object sendInvoke(TruffleObject receiver, String identifier, Object... arguments)
                    throws UnsupportedTypeException, ArityException, UnknownIdentifierException, UnsupportedMessageException {
        return interop.invokeMember(receiver, identifier, arguments);
    }

    Object sendNew(TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        return interop.instantiate(receiver, arguments);
    }

    boolean sendIsNull(TruffleObject receiver) {
        return interop.isNull(receiver);
    }

    boolean sendHasSize(TruffleObject receiver) {
        return interop.hasArrayElements(receiver);
    }

    Object sendGetSize(TruffleObject receiver) throws UnsupportedMessageException {
        // we use int because current implementations expect that.
        return (int) interop.getArraySize(receiver);
    }

    boolean sendIsBoxed(TruffleObject receiver) {
        if (interop.isNumber(receiver)) {
            if (interop.fitsInLong(receiver)) {
                return true;
            } else if (interop.fitsInDouble(receiver)) {
                return true;
            }
            return false;
        } else if (interop.isString(receiver)) {
            return true;
        } else if (interop.isBoolean(receiver)) {
            return true;
        }
        return false;
    }

    int sendKeyInfo(TruffleObject receiver, Object key) {
        int keyInfo = KeyInfo.NONE;
        if (key instanceof String) {
            String identifier = ((String) key);
            if (interop.isMemberReadable(receiver, identifier)) {
                keyInfo |= KeyInfo.READABLE;
            }
            if (interop.isMemberModifiable(receiver, identifier)) {
                keyInfo |= KeyInfo.MODIFIABLE;
            }
            if (interop.isMemberInsertable(receiver, identifier)) {
                keyInfo |= KeyInfo.INSERTABLE;
            }
            if (interop.isMemberRemovable(receiver, identifier)) {
                keyInfo |= KeyInfo.REMOVABLE;
            }
            if (interop.isMemberInvocable(receiver, identifier)) {
                keyInfo |= KeyInfo.INVOCABLE;
            }
            if (interop.isMemberInternal(receiver, identifier)) {
                keyInfo |= KeyInfo.INTERNAL;
            }
            if (interop.hasMemberReadSideEffects(receiver, identifier)) {
                keyInfo |= KeyInfo.READ_SIDE_EFFECTS;
            }
            if (interop.hasMemberWriteSideEffects(receiver, identifier)) {
                keyInfo |= KeyInfo.WRITE_SIDE_EFFECTS;
            }
        } else if (key instanceof Number) {
            final long index = asLongIndex(key);
            if (key instanceof Float || key instanceof Double) {
                if (index != ((Number) key).doubleValue()) {
                    return KeyInfo.NONE;
                }
            }

            if (interop.isArrayElementReadable(receiver, index)) {
                keyInfo |= KeyInfo.READABLE;
            }
            if (interop.isArrayElementModifiable(receiver, index)) {
                keyInfo |= KeyInfo.MODIFIABLE;
            }
            if (interop.isArrayElementInsertable(receiver, index)) {
                keyInfo |= KeyInfo.INSERTABLE;
            }
            if (interop.isArrayElementRemovable(receiver, index)) {
                keyInfo |= KeyInfo.REMOVABLE;
            }
        } else if (key instanceof TruffleObject) {
            CompilerDirectives.transferToInterpreter();
            if (key instanceof TruffleObject) {
                // since we cannot pass through TruffleObjects as identifiers for messages anymore
                // we need to unbox it to a primitive to be compatible. This case don't need to be
                // fast.
                CompilerDirectives.transferToInterpreter();
                TruffleObject identifierTO = (TruffleObject) key;
                if (LibraryToLegacy.sendIsBoxed(legacyIsBoxed, identifierTO)) {
                    try {
                        return sendKeyInfo(receiver, LibraryToLegacy.sendUnbox(legacyUnbox, identifierTO));
                    } catch (UnsupportedMessageException e) {
                    }
                }
            }
        }
        return keyInfo;
    }

    @TruffleBoundary
    private static long boundaryToLong(Object key) {
        return ((Number) key).longValue();
    }

    boolean sendHasKeys(TruffleObject receiver) {
        return interop.hasMembers(receiver);
    }

    TruffleObject sendKeys(TruffleObject receiver) throws UnsupportedMessageException {
        return (TruffleObject) interop.getMembers(receiver);
    }

    TruffleObject sendKeys(TruffleObject receiver, boolean includeInternal) throws UnsupportedMessageException {
        return (TruffleObject) interop.getMembers(receiver, includeInternal);
    }

    Object send(TruffleObject receiver, Object[] a) throws InteropException {
        if (message instanceof KnownMessage) {
            switch (message.hashCode()) {
                case Read.HASH:
                    Object id = a.length >= 1 ? a[0] : null;
                    return sendRead(receiver, id);
                case Write.HASH:
                    id = a.length >= 1 ? a[0] : null;
                    Object value = a.length >= 2 ? a[1] : null;
                    sendWrite(receiver, id, value);
                    return a[1];
                case Remove.HASH:
                    id = a.length >= 1 ? a[0] : null;
                    return sendRemove(receiver, id);
                case KeyInfoMsg.HASH:
                    id = a.length >= 1 ? a[0] : null;
                    return sendKeyInfo(receiver, id);
                case Invoke.HASH:
                    id = a.length >= 1 ? a[0] : null;
                    Object[] args;
                    if (a.length >= 2) {
                        args = new Object[a.length - 1];
                        System.arraycopy(a, 1, args, 0, args.length);
                    } else {
                        args = new Object[0];
                    }
                    return sendInvoke(receiver, (String) id, args);
                case HasKeys.HASH:
                    return sendHasKeys(receiver);
                case Keys.HASH:
                    return sendKeys(receiver);
                case Unbox.HASH:
                    return sendUnbox(receiver);
                case IsBoxed.HASH:
                    return sendIsBoxed(receiver);
                case HasSize.HASH:
                    return sendHasSize(receiver);
                case GetSize.HASH:
                    return sendGetSize(receiver);
                case Execute.HASH:
                    return sendExecute(receiver, a);
                case IsExecutable.HASH:
                    return sendIsExecutable(receiver);
                case New.HASH:
                    return sendNew(receiver, a);
                case IsInstantiable.HASH:
                    return sendIsInstantiable(receiver);
                case IsPointer.HASH:
                    return sendIsPointer(receiver);
                case AsPointer.HASH:
                    return sendAsPointer(receiver);
                case ToNative.HASH:
                    return sendToNative(receiver);
                case IsNull.HASH:
                    return sendIsNull(receiver);
            }
        }
        // TODO allow sending custom messages

        throw UnsupportedMessageException.create();
    }

}
