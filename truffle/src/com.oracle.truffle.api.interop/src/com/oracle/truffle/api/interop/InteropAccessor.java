/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("deprecation")
final class InteropAccessor extends Accessor {

    static final InteropAccessor ACCESSOR = new InteropAccessor();

    private InteropAccessor() {
    }

    /*
     * Instantiated by Accessor.
     */
    static class InteropImpl extends InteropSupport {

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
    }

    static final class EmptyTruffleObject implements TruffleObject {
        static final EmptyTruffleObject INSTANCE = new EmptyTruffleObject();
    }
}
