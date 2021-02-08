/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A default frame slot based implementation of variables contained in the (default) frame scope.
 */
@SuppressWarnings("deprecation")
abstract class LegacyDefaultScope implements Iterable<com.oracle.truffle.api.Scope> {

    private List<com.oracle.truffle.api.Scope> scopeList;

    @Override
    public Iterator<com.oracle.truffle.api.Scope> iterator() {
        List<com.oracle.truffle.api.Scope> list = scopeList;
        if (list == null) {
            scopeList = list = Collections.singletonList(createScope());
        }
        return list.iterator();
    }

    abstract com.oracle.truffle.api.Scope createScope();

    static Iterable<com.oracle.truffle.api.Scope> topScope(Object global) {
        return new LegacyDefaultScope() {
            @Override
            com.oracle.truffle.api.Scope createScope() {
                Object globalObject;
                if (global != null && InteropLibrary.getUncached().hasMembers(global)) {
                    globalObject = global;
                } else {
                    globalObject = new EmptyGlobalBindings();
                }
                return com.oracle.truffle.api.Scope.newBuilder("global", globalObject).build();
            }
        };
    }

    static Iterable<com.oracle.truffle.api.Scope> lexicalScope(Node node, Frame frame, Class<? extends TruffleLanguage<?>> language) {
        return new LegacyDefaultScope() {
            @Override
            com.oracle.truffle.api.Scope createScope() {
                RootNode root = node.getRootNode();
                String name = root.getName();
                if (name == null) {
                    name = "local";
                }
                return com.oracle.truffle.api.Scope.newBuilder(name, DefaultScope.getVariables(root, frame, language)).node(root).arguments(
                                DefaultScope.getArguments((frame != null) ? frame.getArguments() : new Object[0], language)).build();
            }
        };
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class EmptyGlobalBindings implements TruffleObject {

        EmptyGlobalBindings() {
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object readMember(String member) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(member);
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return DefaultScope.VariableNamesObject.EMPTY;
        }

        @ExportMessage
        boolean isMemberReadable(@SuppressWarnings("unused") String member) {
            return false;
        }
    }
}
