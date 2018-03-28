/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.sl.nodes.access.SLReadPropertyCacheNode;
import com.oracle.truffle.sl.nodes.access.SLReadPropertyCacheNodeGen;
import com.oracle.truffle.sl.nodes.access.SLWritePropertyCacheNode;
import com.oracle.truffle.sl.nodes.access.SLWritePropertyCacheNodeGen;
import com.oracle.truffle.sl.nodes.call.SLDispatchNode;
import com.oracle.truffle.sl.nodes.call.SLDispatchNodeGen;
import com.oracle.truffle.sl.nodes.interop.SLForeignToSLTypeNode;
import com.oracle.truffle.sl.nodes.interop.SLForeignToSLTypeNodeGen;

/**
 * The class containing all message resolution implementations of an SL object.
 */
@MessageResolution(receiverType = SLObjectType.class)
public class SLObjectMessageResolution {
    /*
     * An SL object resolves the WRITE message and maps it to an object property write access.
     */
    @Resolve(message = "WRITE")
    public abstract static class SLForeignWriteNode extends Node {

        @Child private SLWritePropertyCacheNode write = SLWritePropertyCacheNodeGen.create();
        @Child private SLForeignToSLTypeNode nameToSLType = SLForeignToSLTypeNodeGen.create();
        @Child private SLForeignToSLTypeNode valueToSLType = SLForeignToSLTypeNodeGen.create();

        public Object access(DynamicObject receiver, Object name, Object value) {
            Object convertedName = nameToSLType.executeConvert(name);
            Object convertedValue = valueToSLType.executeConvert(value);
            try {
                write.executeWrite(receiver, convertedName, convertedValue);
            } catch (SLUndefinedNameException undefinedName) {
                throw UnknownIdentifierException.raise(String.valueOf(convertedName));
            }
            return convertedValue;
        }
    }

    /*
     * An SL object resolves the READ message and maps it to an object property read access.
     */
    @Resolve(message = "READ")
    public abstract static class SLForeignReadNode extends Node {

        @Child private SLReadPropertyCacheNode read = SLReadPropertyCacheNodeGen.create();
        @Child private SLForeignToSLTypeNode nameToSLType = SLForeignToSLTypeNodeGen.create();

        public Object access(DynamicObject receiver, Object name) {
            Object convertedName = nameToSLType.executeConvert(name);
            Object result;
            try {
                result = read.executeRead(receiver, convertedName);
            } catch (SLUndefinedNameException undefinedName) {
                throw UnknownIdentifierException.raise(String.valueOf(convertedName));
            }
            return result;
        }
    }

    /*
     * An SL object resolves the REMOVE message and maps it to an object property delete access.
     */
    @Resolve(message = "REMOVE")
    public abstract static class SLForeignRemoveNode extends Node {

        @Child private SLForeignToSLTypeNode nameToSLType = SLForeignToSLTypeNodeGen.create();

        public Object access(DynamicObject receiver, Object name) {
            Object convertedName = nameToSLType.executeConvert(name);
            if (receiver.containsKey(convertedName)) {
                return receiver.delete(convertedName);
            } else {
                throw UnknownIdentifierException.raise(String.valueOf(convertedName));
            }
        }
    }

    /*
     * An SL object resolves the INVOKE message and maps it to an object property read access
     * followed by an function invocation. The object property must be an SL function object, which
     * is executed eventually.
     */
    @Resolve(message = "INVOKE")
    public abstract static class SLForeignInvokeNode extends Node {

        @Child private SLDispatchNode dispatch = SLDispatchNodeGen.create();

        public Object access(DynamicObject receiver, String name, Object[] arguments) {
            Object property = receiver.get(name);
            if (property instanceof SLFunction) {
                SLFunction function = (SLFunction) property;
                Object[] arr = new Object[arguments.length];
                // Before the arguments can be used by the SLFunction, they need to be converted to
                // SL
                // values.
                for (int i = 0; i < arguments.length; i++) {
                    arr[i] = SLContext.fromForeignValue(arguments[i]);
                }
                Object result = dispatch.executeDispatch(function, arr);
                return result;
            } else {
                throw UnknownIdentifierException.raise(name);
            }
        }
    }

    @Resolve(message = "HAS_KEYS")
    public abstract static class SLForeignHasPropertiesNode extends Node {

        @SuppressWarnings("unused")
        public Object access(DynamicObject receiver) {
            return true;
        }
    }

    @Resolve(message = "KEY_INFO")
    public abstract static class SLForeignPropertyInfoNode extends Node {

        public int access(DynamicObject receiver, Object name) {
            Object property = receiver.get(name);
            if (property == null) {
                return KeyInfo.INSERTABLE;
            } else if (property instanceof SLFunction) {
                return KeyInfo.READABLE | KeyInfo.REMOVABLE | KeyInfo.MODIFIABLE | KeyInfo.INVOCABLE;
            } else {
                return KeyInfo.READABLE | KeyInfo.REMOVABLE | KeyInfo.MODIFIABLE;
            }
        }
    }

    @Resolve(message = "KEYS")
    public abstract static class SLForeignPropertiesNode extends Node {
        public Object access(DynamicObject receiver) {
            return obtainKeys(receiver);
        }

        @CompilerDirectives.TruffleBoundary
        private static Object obtainKeys(DynamicObject receiver) {
            Object[] keys = receiver.getShape().getKeyList().toArray();
            return new KeysArray(keys);
        }
    }

    @MessageResolution(receiverType = KeysArray.class)
    static final class KeysArray implements TruffleObject {

        private final Object[] keys;

        KeysArray(Object[] keys) {
            this.keys = keys;
        }

        @Resolve(message = "HAS_SIZE")
        abstract static class HasSize extends Node {

            public Object access(@SuppressWarnings("unused") KeysArray receiver) {
                return true;
            }
        }

        @Resolve(message = "GET_SIZE")
        abstract static class GetSize extends Node {

            public Object access(KeysArray receiver) {
                return receiver.keys.length;
            }
        }

        @Resolve(message = "READ")
        abstract static class Read extends Node {

            public Object access(KeysArray receiver, int index) {
                try {
                    Object key = receiver.keys[index];
                    assert key instanceof String;
                    return key;
                } catch (IndexOutOfBoundsException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(String.valueOf(index));
                }
            }
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return KeysArrayForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject array) {
            return array instanceof KeysArray;
        }

    }

}
