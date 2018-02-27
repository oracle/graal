/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.interop.ForeignAccess.StandardFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

class InteropAccessor extends Accessor {

    @Override
    protected InteropSupport interopSupport() {
        return new InteropSupport() {
            @Override
            public boolean canHandle(Object foreignAccess, Object receiver) {
                ForeignAccess fa = (ForeignAccess) foreignAccess;
                TruffleObject obj = (TruffleObject) receiver;
                return fa.canHandle(obj);
            }

            @Override
            public CallTarget canHandleTarget(Object access) {
                ForeignAccess fa = (ForeignAccess) access;
                return fa.checkLanguage();
            }

            @Override
            public boolean isTruffleObject(Object value) {
                return value instanceof TruffleObject;
            }

            @Override
            public void checkInteropType(Object result) {
                InteropAccessNode.checkInteropType(result);
            }

            @Override
            public Object createDefaultNodeObject(Node node) {
                return EmptyTruffleObject.INSTANCE;
            }

            @Override
            public boolean isValidNodeObject(Object obj) {
                if (obj instanceof TruffleObject) {
                    TruffleObject tObj = (TruffleObject) obj;
                    if (!ForeignAccess.sendHasKeys(Message.HAS_KEYS.createNode(), tObj)) {
                        throw new AssertionError("Invalid node object: must return true for the HAS_KEYS message.");
                    }
                    Object keys;
                    try {
                        keys = ForeignAccess.sendKeys(Message.KEYS.createNode(), tObj);
                    } catch (UnsupportedMessageException e) {
                        throw new AssertionError("Invalid node object: must support the KEYS message.", e);
                    }
                    if (!(keys instanceof TruffleObject)) {
                        throw new AssertionError("Invalid node object: the returned KEYS object must be a TruffleObject.");
                    }
                    TruffleObject tKeys = (TruffleObject) keys;

                    Node hasSize = Message.HAS_SIZE.createNode();
                    if (!ForeignAccess.sendHasSize(hasSize, tKeys)) {
                        throw new AssertionError("Invalid node object: the returned KEYS object must support HAS_SIZE.");
                    }
                    Node getSize = Message.GET_SIZE.createNode();

                    Number size;
                    try {
                        size = (Number) ForeignAccess.sendGetSize(getSize, tKeys);
                    } catch (UnsupportedMessageException e) {
                        throw new AssertionError("Invalid node object: the returned KEYS object must have a size.");
                    }
                    Node readKeyNode = Message.READ.createNode();
                    Node readElementNode = Message.READ.createNode();
                    Node keyInfoNode = Message.KEY_INFO.createNode();
                    long longValue = size.longValue();
                    for (long i = 0; i < longValue; i++) {
                        Object key;
                        try {
                            key = ForeignAccess.sendRead(readKeyNode, tKeys, i);
                        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                            throw new AssertionError("Invalid node object: the returned KEYS object must be readable at number identifier " + i);
                        }
                        if (!(key instanceof String)) {
                            throw new AssertionError("Invalid node object: the returned KEYS object must return a string at number identifier " + i + ". But was " + key.getClass().getName() + ".");
                        }
                        try {
                            ForeignAccess.sendRead(readElementNode, tObj, key);
                        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                            throw new AssertionError("Invalid node object: the returned KEYS element must be readable with identifier " + key);
                        }

                        int keyInfo = ForeignAccess.sendKeyInfo(keyInfoNode, tObj, key);
                        if (KeyInfo.isWritable(keyInfo)) {
                            throw new AssertionError("Invalid node object: The key " + key + " is marked as writable but node objects must not be writable.");
                        }
                    }
                    Node node = Message.HAS_SIZE.createNode();
                    if (ForeignAccess.sendHasSize(node, tObj)) {
                        throw new AssertionError("Invalid node object: the node object must not return true for HAS_SIZE.");
                    }

                    return true;
                } else {
                    throw new AssertionError("Invalid node object: Node objects must be of type TruffleObject.");
                }
            }
        };
    }

}

final class EmptyTruffleObject implements TruffleObject {

    static final EmptyTruffleObject INSTANCE = new EmptyTruffleObject();

    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(new StandardFactory() {

            public CallTarget accessWrite() {
                return null;
            }

            public CallTarget accessUnbox() {
                return null;
            }

            public CallTarget accessRead() {
                return null;
            }

            public CallTarget accessNew(int argumentsLength) {
                return null;
            }

            public CallTarget accessMessage(Message unknown) {
                return null;
            }

            public CallTarget accessHasKeys() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
            }

            public CallTarget accessKeys() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
            }

            public CallTarget accessKeyInfo() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(EmptyKeys.INSTANCE));
            }

            public CallTarget accessIsNull() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            }

            public CallTarget accessIsExecutable() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            }

            public CallTarget accessIsBoxed() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            }

            public CallTarget accessInvoke(int argumentsLength) {
                return null;
            }

            public CallTarget accessHasSize() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            }

            public CallTarget accessGetSize() {
                return null;
            }

            public CallTarget accessExecute(int argumentsLength) {
                return null;
            }
        }, null);
    }

}

final class EmptyKeys implements TruffleObject {

    static final EmptyKeys INSTANCE = new EmptyKeys();

    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(new StandardFactory() {

            public CallTarget accessWrite() {
                return null;
            }

            public CallTarget accessUnbox() {
                return null;
            }

            public CallTarget accessRead() {
                return null;
            }

            public CallTarget accessNew(int argumentsLength) {
                return null;
            }

            public CallTarget accessMessage(Message unknown) {
                return null;
            }

            public CallTarget accessHasKeys() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            }

            public CallTarget accessKeys() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
            }

            public CallTarget accessKeyInfo() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
            }

            public CallTarget accessIsNull() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            }

            public CallTarget accessIsExecutable() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            }

            public CallTarget accessIsBoxed() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            }

            public CallTarget accessInvoke(int argumentsLength) {
                return null;
            }

            public CallTarget accessHasSize() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
            }

            public CallTarget accessGetSize() {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(0));
            }

            public CallTarget accessExecute(int argumentsLength) {
                return null;
            }
        }, null);
    }

}
