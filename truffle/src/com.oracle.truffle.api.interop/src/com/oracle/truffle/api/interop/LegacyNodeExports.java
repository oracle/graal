/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Iterator;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Default implementation of {@link NodeLibrary}.
 */
@SuppressWarnings("deprecation")
@ExportLibrary(value = NodeLibrary.class, receiverType = Node.class)
final class LegacyNodeExports {

    @ExportMessage
    @TruffleBoundary
    static boolean hasScope(Node node, Frame frame) {
        Iterator<com.oracle.truffle.api.Scope> scopes = findLocalScopes(node, frame).iterator();
        if (!scopes.hasNext()) {
            return false;
        }
        return InteropAccessor.ACCESSOR.engineSupport().legacyScopesHasScope(node, scopes);
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("unchecked")
    static Object getScope(Node node, Frame frame, @SuppressWarnings("unused") boolean nodeEnter) throws UnsupportedMessageException {
        Iterator<com.oracle.truffle.api.Scope> scopes = findLocalScopes(node, frame).iterator();
        if (!scopes.hasNext()) {
            throw UnsupportedMessageException.create();
        }
        TruffleLanguage.Env env = findEnv(node);
        TruffleLanguage<?> spi = InteropAccessor.ACCESSOR.languageSupport().getLanguage(env);
        Object scope = InteropAccessor.ACCESSOR.engineSupport().legacyScopes2ScopeObject(node, scopes, (Class<? extends TruffleLanguage<?>>) spi.getClass());
        if (scope == null) {
            throw UnsupportedMessageException.create();
        }
        return scope;
    }

    @ExportMessage
    @TruffleBoundary
    static boolean hasReceiverMember(Node node, Frame frame) {
        Iterable<com.oracle.truffle.api.Scope> scopes = findLocalScopes(node, frame);
        for (com.oracle.truffle.api.Scope scope : scopes) {
            String name = scope.getReceiverName();
            if (name != null) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    static String getReceiverMember(Node node, Frame frame) throws UnsupportedMessageException {
        Iterable<com.oracle.truffle.api.Scope> scopes = findLocalScopes(node, frame);
        for (com.oracle.truffle.api.Scope scope : scopes) {
            String name = scope.getReceiverName();
            if (name != null) {
                return name;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    static boolean hasRootInstance(Node node, Frame frame) {
        Iterable<com.oracle.truffle.api.Scope> scopes = findLocalScopes(node, frame);
        for (com.oracle.truffle.api.Scope scope : scopes) {
            Object instance = scope.getRootInstance();
            if (instance != null) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    static Object getRootInstance(Node node, Frame frame) throws UnsupportedMessageException {
        Iterable<com.oracle.truffle.api.Scope> scopes = findLocalScopes(node, frame);
        for (com.oracle.truffle.api.Scope scope : scopes) {
            Object instance = scope.getRootInstance();
            if (instance != null) {
                return instance;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    static Object getView(Node node, Frame frame, Object value) {
        TruffleLanguage.Env env = findEnv(node);
        if (env == null) {
            return value;
        } else {
            return InteropAccessor.ACCESSOR.languageSupport().getLegacyScopedView(env, node, frame, value);
        }
    }

    private static Iterable<com.oracle.truffle.api.Scope> findLocalScopes(Node node, Frame frame) {
        CompilerAsserts.neverPartOfCompilation();
        TruffleLanguage.Env env = findEnv(node);
        if (env == null) {
            return Collections.emptyList();
        } else {
            return InteropAccessor.ACCESSOR.languageSupport().findLegacyLocalScopes(env, node, frame);
        }
    }

    private static TruffleLanguage.Env findEnv(Node node) {
        CompilerAsserts.neverPartOfCompilation();
        RootNode root = node.getRootNode();
        if (root == null || root.getLanguageInfo() == null) {
            return null;
        }
        return InteropAccessor.ACCESSOR.engineSupport().getEnvForInstrument(root.getLanguageInfo());
    }

    static int findScopeCount(Iterable<com.oracle.truffle.api.Scope> scopes) {
        Iterator<com.oracle.truffle.api.Scope> it = scopes.iterator();
        int count = 0;
        while (it.hasNext()) {
            count++;
            it.next();
        }
        return count;
    }

    static com.oracle.truffle.api.Scope findScope(Iterable<com.oracle.truffle.api.Scope> scopes, int scopeNumber) {
        Iterator<com.oracle.truffle.api.Scope> it = scopes.iterator();
        int count = 0;
        while (it.hasNext()) {
            com.oracle.truffle.api.Scope scope = it.next();
            if (count == scopeNumber) {
                return scope;
            }
            count++;
        }
        throw new AssertionError("wrong scope number: " + scopeNumber);
    }

}
