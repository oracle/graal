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
package com.oracle.truffle.polyglot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.StandardFactory;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.DefaultScope.VariableNamesObject;

final class PolyglotLanguageBindings implements TruffleObject {

    final TruffleObject[] scopes;

    PolyglotLanguageBindings(Iterable<Scope> scopes) {
        List<TruffleObject> collectScopes = new ArrayList<>();
        Node hasKeysNode = Message.HAS_KEYS.createNode();
        for (Scope scope : scopes) {
            Object vars = scope.getVariables();
            if (!(vars instanceof TruffleObject)) {
                throw new AssertionError("Returned scope variables object must be a TruffleObject.");
            }
            if (!ForeignAccess.sendHasKeys(hasKeysNode, (TruffleObject) vars)) {
                throw new AssertionError("Returned scope variables object must return true for HAS_KEYS.");
            }
            collectScopes.add((TruffleObject) vars);
        }
        this.scopes = collectScopes.toArray(new TruffleObject[0]);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LanguageBindingsFactory.INSTANCE;
    }

    private static final class LanguageBindingsFactory implements StandardFactory {

        private static final ForeignAccess INSTANCE = ForeignAccess.create(PolyglotBindings.class, new LanguageBindingsFactory());

        @Override
        public CallTarget accessWrite() {
            return Truffle.getRuntime().createCallTarget(new WriteNode());
        }

        @Override
        public CallTarget accessIsBoxed() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.FALSE));
        }

        @Override
        public CallTarget accessUnbox() {
            return null;
        }

        @Override
        public CallTarget accessRead() {
            return Truffle.getRuntime().createCallTarget(new ReadNode());
        }

        @Override
        public CallTarget accessRemove() {
            return Truffle.getRuntime().createCallTarget(new RemoveNode());
        }

        @Override
        public CallTarget accessIsInstantiable() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.FALSE));
        }

        @Override
        public CallTarget accessNew(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessHasKeys() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(Boolean.TRUE));
        }

        @Override
        public CallTarget accessKeys() {
            return Truffle.getRuntime().createCallTarget(new KeysNode());
        }

        @Override
        public CallTarget accessKeyInfo() {
            return Truffle.getRuntime().createCallTarget(new KeyInfoNode());
        }

        @Override
        public CallTarget accessIsNull() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
        }

        @Override
        public CallTarget accessIsExecutable() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
        }

        @Override
        public CallTarget accessInvoke(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessHasSize() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
        }

        @Override
        public CallTarget accessGetSize() {
            return null;
        }

        @Override
        public CallTarget accessExecute(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessIsPointer() {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
        }

        @Override
        public CallTarget accessAsPointer() {
            return null;
        }

        @Override
        public CallTarget accessToNative() {
            return null;
        }

        @Override
        public CallTarget accessMessage(Message unknown) {
            return null;
        }

        private abstract static class BaseNode extends RootNode {

            protected BaseNode() {
                super(null);
            }

            @CompilationFinal boolean seenException = false;
            @CompilationFinal int scopesLength = -1; // uninitialized

            @Override
            public final Object execute(VirtualFrame frame) {
                Object[] arguments = frame.getArguments();
                PolyglotLanguageBindings bindings = (PolyglotLanguageBindings) arguments[0];
                TruffleObject[] scopes = bindings.scopes;
                int length = profileLength(scopes.length);
                try {
                    return execute(scopes, length, arguments, 1);
                } catch (InteropException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw e.raise();
                }
            }

            private int profileLength(int length) {
                if (scopesLength != -2) {
                    if (scopesLength == length) {
                        return scopesLength;
                    } else if (scopesLength == -1) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        scopesLength = length;
                    }
                }
                return length;
            }

            protected static final String expectIdentifier(Object[] arguments, int offset, Message message) {
                Object key = arguments[offset];
                if (!(key instanceof String)) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedMessageException.raise(message);
                }
                return (String) key;
            }

            /**
             * For single constant length we can often directly read from the scop object instead
             * iterating all of them.
             */
            protected final boolean isConstantSingleLength() {
                return scopesLength == 1;
            }

            abstract Object execute(TruffleObject[] scopes, int length, Object[] arguments, int offset) throws InteropException;

        }

        private static class ReadNode extends BaseNode {

            @Child Node readNode = Message.READ.createNode();
            @Child Node keyInfoNode = Message.KEY_INFO.createNode();

            @Override
            Object execute(TruffleObject[] scopes, int length, Object[] arguments, int offset) throws UnknownIdentifierException, UnsupportedMessageException {
                String identifier = expectIdentifier(arguments, offset, Message.READ);
                if (isConstantSingleLength()) {
                    return ForeignAccess.sendRead(readNode, scopes[0], identifier);
                } else if (length > 0) {
                    for (int i = 0; i < length; i++) {
                        TruffleObject scopeObject = scopes[i];
                        if (KeyInfo.isReadable(ForeignAccess.sendKeyInfo(keyInfoNode, scopeObject, identifier))) {
                            return ForeignAccess.sendRead(readNode, scopeObject, identifier);
                        }
                    }
                }
                throw UnknownIdentifierException.raise(identifier);
            }

        }

        private static class WriteNode extends BaseNode {

            @Child Node writeNode = Message.WRITE.createNode();
            @Child Node keyInfoNode = Message.KEY_INFO.createNode();

            @Override
            Object execute(TruffleObject[] scopes, int length, Object[] arguments, int offset) throws InteropException {
                String identifier = expectIdentifier(arguments, offset, Message.WRITE);
                Object value = arguments[offset + 1];
                if (isConstantSingleLength()) {
                    return ForeignAccess.sendWrite(writeNode, scopes[0], identifier, value);
                } else if (length > 0) {
                    for (int i = 0; i < length; i++) {
                        TruffleObject scopeObject = scopes[i];
                        if (KeyInfo.isWritable(ForeignAccess.sendKeyInfo(keyInfoNode, scopeObject, identifier))) {
                            return ForeignAccess.sendWrite(writeNode, scopeObject, identifier, value);
                        }
                    }
                }
                throw UnsupportedMessageException.raise(Message.WRITE);
            }

        }

        private static class RemoveNode extends BaseNode {

            @Child private Node removeNode = Message.REMOVE.createNode();
            @Child private Node keyInfoNode = Message.KEY_INFO.createNode();

            @Override
            Object execute(TruffleObject[] scopes, int length, Object[] arguments, int offset) throws InteropException {
                String identifier = expectIdentifier(arguments, offset, Message.REMOVE);
                if (isConstantSingleLength()) {
                    return ForeignAccess.sendRemove(removeNode, scopes[0], identifier);
                } else if (length > 0) {
                    for (int i = 0; i < length; i++) {
                        TruffleObject scopeObject = scopes[i];
                        int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, scopeObject, identifier);
                        if (KeyInfo.isRemovable(keyInfo)) {
                            return ForeignAccess.sendRemove(removeNode, scopeObject, identifier);
                        } else if (KeyInfo.isExisting(keyInfo)) {
                            CompilerDirectives.transferToInterpreter();
                            throw UnsupportedMessageException.raise(Message.REMOVE);
                        }
                    }
                }
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(identifier);
            }

        }

        private static class KeyInfoNode extends BaseNode {

            @Child private Node keyInfoNode = Message.KEY_INFO.createNode();

            @Override
            Object execute(TruffleObject[] scopes, int length, Object[] arguments, int offset) throws InteropException {
                String identifier = expectIdentifier(arguments, offset, Message.KEY_INFO);
                int keyInfo = KeyInfo.NONE;
                for (int i = 0; i < length; i++) {
                    TruffleObject scopeObject = scopes[i];
                    int currentInfo = ForeignAccess.sendKeyInfo(keyInfoNode, scopeObject, identifier);
                    if (KeyInfo.isExisting(currentInfo)) {
                        keyInfo = currentInfo;
                        break;
                    } else if (KeyInfo.isInsertable(currentInfo)) {
                        keyInfo = currentInfo;
                    }
                }
                return keyInfo;
            }

        }

        private static class KeysNode extends BaseNode {

            @Child private Node keysNode = Message.KEYS.createNode();
            @Child private Node readNode = Message.READ.createNode();
            @Child private Node getSizeNode = Message.GET_SIZE.createNode();

            @Override
            Object execute(TruffleObject[] scopes, int length, Object[] arguments, int offset) throws InteropException {
                if (length == 0) {
                    return VariableNamesObject.EMPTY;
                } else if (length == 1) {
                    return ForeignAccess.sendKeys(keysNode, scopes[0]);
                } else {
                    return collectKeys(scopes);
                }
            }

            @TruffleBoundary
            private Object collectKeys(TruffleObject[] scopes) throws UnsupportedMessageException {
                // unfortunately we cannot do much butter as scopes might have
                // overlapping keys. So we need to make the set unique.
                Set<String> keySet = new HashSet<>();
                for (TruffleObject scope : scopes) {
                    TruffleObject keys = ForeignAccess.sendKeys(keysNode, scope);
                    int size = ((Number) ForeignAccess.sendGetSize(getSizeNode, keys)).intValue();
                    for (int i = 0; i < size; i++) {
                        try {
                            keySet.add((String) ForeignAccess.sendRead(readNode, keys, i));
                        } catch (UnknownIdentifierException e) {
                        }
                    }
                }
                return new DefaultScope.VariableNamesObject(keySet);
            }

        }
    }
}
