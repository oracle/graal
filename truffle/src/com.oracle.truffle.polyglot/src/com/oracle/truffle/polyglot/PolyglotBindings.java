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

import java.util.Map;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.nodes.RootNode;

final class PolyglotBindings implements TruffleObject {

    // a bindings object for each language.
    private final PolyglotLanguageContext languageContext;
    // the bindings map that shared across a bindings object for each language context
    private final Map<String, Value> bindings;

    PolyglotBindings(PolyglotLanguageContext languageContext, Map<String, Value> bindings) {
        this.languageContext = languageContext;
        this.bindings = bindings;
    }

    public ForeignAccess getForeignAccess() {
        return PolyglotBindingsFactory.INSTANCE;
    }

    private static final class PolyglotBindingsFactory implements StandardFactory {

        private static final ForeignAccess INSTANCE = ForeignAccess.create(PolyglotBindings.class, new PolyglotBindingsFactory());

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

            @Override
            public final Object execute(VirtualFrame frame) {
                Object[] arguments = frame.getArguments();
                PolyglotBindings bindings = (PolyglotBindings) arguments[0];
                try {
                    return execute(bindings.languageContext, bindings.bindings, arguments, 1);
                } catch (InteropException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw e.raise();
                }
            }

            protected static final String expectIdentifier(Object[] arguments, int offset, Message message) {
                Object key = arguments[offset];
                if (!(key instanceof String)) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedMessageException.raise(message);
                }
                return (String) key;
            }

            abstract Object execute(PolyglotLanguageContext context, Map<String, Value> map, Object[] arguments, int offset) throws InteropException;

        }

        private static class ReadNode extends BaseNode {

            @Override
            @TruffleBoundary
            Object execute(PolyglotLanguageContext context, Map<String, Value> map, Object[] arguments, int offset) throws InteropException {
                String identifier = expectIdentifier(arguments, offset, Message.READ);
                Value value = map.get(identifier);
                if (value == null) {
                    CompilerDirectives.transferToInterpreter();
                    // legacy support
                    Value legacyValue = context.context.findLegacyExportedSymbol(identifier);
                    if (legacyValue != null) {
                        return context.getAPIAccess().getReceiver(legacyValue);
                    }
                    throw UnknownIdentifierException.raise(identifier);
                }
                return context.toGuestValue(value);
            }

        }

        private static class WriteNode extends BaseNode {

            @Override
            @TruffleBoundary
            Object execute(PolyglotLanguageContext context, Map<String, Value> map, Object[] arguments, int offset) throws InteropException {
                String identifier = expectIdentifier(arguments, offset, Message.WRITE);
                Object value = arguments[offset + 1];
                map.put(identifier, context.asValue(value));
                return value;
            }

        }

        private static class RemoveNode extends BaseNode {

            @Override
            @TruffleBoundary
            Object execute(PolyglotLanguageContext context, Map<String, Value> map, Object[] arguments, int offset) throws InteropException {
                String identifier = expectIdentifier(arguments, offset, Message.REMOVE);
                Value ret = map.remove(identifier);
                return ret != null;
            }
        }

        private static class KeysNode extends BaseNode {

            @Override
            @TruffleBoundary
            Object execute(PolyglotLanguageContext context, Map<String, Value> map, Object[] arguments, int offset) throws InteropException {
                return new DefaultScope.VariableNamesObject(map.keySet());
            }

        }

        private static class KeyInfoNode extends BaseNode {

            @Override
            @TruffleBoundary
            Object execute(PolyglotLanguageContext context, Map<String, Value> map, Object[] arguments, int offset) throws InteropException {
                String identifier = expectIdentifier(arguments, offset, Message.KEY_INFO);
                if (map.containsKey(identifier)) {
                    return KeyInfo.READABLE | KeyInfo.MODIFIABLE | KeyInfo.REMOVABLE;
                } else {
                    return KeyInfo.INSERTABLE;
                }
            }

        }

    }

}
