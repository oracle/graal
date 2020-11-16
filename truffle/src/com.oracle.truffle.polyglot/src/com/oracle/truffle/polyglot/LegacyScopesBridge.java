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
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CompilerAsserts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.IntValueProfile;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings("deprecation")
final class LegacyScopesBridge {

    private static final InteropLibrary INTEROP = InteropLibrary.getUncached();

    private LegacyScopesBridge() {
    }

    static Iterable<com.oracle.truffle.api.Scope> findLibraryLocalScopesToLegacy(Node node, Frame frame) {
        NodeLibrary nodeLibrary = NodeLibrary.getUncached(node);
        if (!nodeLibrary.hasScope(node, frame)) {
            return Collections.emptyList();
        }
        Object scopeObject;
        try {
            scopeObject = nodeLibrary.getScope(node, frame, true);
        } catch (UnsupportedMessageException ex) {
            return Collections.emptyList();
        }
        String receiverName = getReceiverName(nodeLibrary, node, frame);
        SourceSection rootSection = node.getRootNode().getSourceSection();
        ArrayList<com.oracle.truffle.api.Scope> scopes = new ArrayList<>(5);
        while (scopeObject != null) {
            InteropLibrary scopeInterop = InteropLibrary.getUncached(scopeObject);
            Object scopeParent = null;
            if (scopeInterop.hasScopeParent(scopeObject)) {
                try {
                    scopeParent = scopeInterop.getScopeParent(scopeObject);
                } catch (UnsupportedMessageException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
            Object variables;
            Object variablesWithReceiver;
            if (scopeParent != null) {
                variablesWithReceiver = new SubtractedVariables(scopeObject, scopeParent, null);
                if (receiverName != null) {
                    variables = new SubtractedVariables(scopeObject, scopeParent, receiverName);
                } else {
                    variables = variablesWithReceiver;
                }
            } else {
                variablesWithReceiver = scopeObject;
                if (receiverName != null) {
                    variables = new NoReceiverVariables(scopeObject, receiverName);
                } else {
                    variables = scopeObject;
                }
            }
            String name;
            try {
                name = InteropLibrary.getUncached().asString(scopeInterop.toDisplayString(scopeObject));
            } catch (UnsupportedMessageException ex) {
                assert false : String.format("Scope %s does not support toDisplayString()", scopeObject);
                name = "local";
            }
            com.oracle.truffle.api.Scope.Builder scopeBuilder;
            scopeBuilder = com.oracle.truffle.api.Scope.newBuilder(name, variables);
            SourceSection sourceLocation = null;
            if (scopeInterop.hasSourceLocation(scopeObject)) {
                try {
                    sourceLocation = scopeInterop.getSourceLocation(scopeObject);
                } catch (UnsupportedMessageException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
            if (sourceLocation != null) {
                if (sourceLocation.equals(rootSection)) {
                    // The "function" scope
                    buildFunctionScope(node, frame, nodeLibrary, scopeBuilder, variablesWithReceiver);
                } else {
                    // find the node of the SourceSection
                    Node scopeNode = node;
                    while (scopeNode != null) {
                        if (scopeNode instanceof InstrumentableNode && sourceLocation.equals(scopeNode.getSourceSection())) {
                            scopeBuilder.node(scopeNode);
                            break;
                        }
                        scopeNode = scopeNode.getParent();
                    }
                }
            }
            scopes.add(scopeBuilder.build());
            scopeObject = scopeParent;
        }
        return scopes;
    }

    private static String getReceiverName(NodeLibrary nodeLibrary, Node node, Frame frame) {
        if (nodeLibrary.hasReceiverMember(node, frame)) {
            try {
                return INTEROP.asString(nodeLibrary.getReceiverMember(node, frame));
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        return null;
    }

    private static void buildFunctionScope(Node node, Frame frame, NodeLibrary nodeLibrary, com.oracle.truffle.api.Scope.Builder scopeBuilder, Object variablesWithReceiver) {
        scopeBuilder.node(node.getRootNode());
        Object arguments = getArguments(node, frame);
        if (arguments != null) {
            scopeBuilder.arguments(arguments);
        }
        if (nodeLibrary.hasReceiverMember(node, frame)) {
            try {
                String receiverName = InteropLibrary.getUncached().asString(nodeLibrary.getReceiverMember(node, frame));
                Object receiverValue = null;
                if (InteropLibrary.getUncached().isMemberReadable(variablesWithReceiver, receiverName)) {
                    receiverValue = InteropLibrary.getUncached().readMember(variablesWithReceiver, receiverName);
                }
                scopeBuilder.receiver(receiverName, receiverValue);
            } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        if (nodeLibrary.hasRootInstance(node, frame)) {
            try {
                Object rootInstance = nodeLibrary.getRootInstance(node, frame);
                if (rootInstance != null) {
                    scopeBuilder.rootInstance(rootInstance);
                }
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

    static Iterable<com.oracle.truffle.api.Scope> topScopesToLegacy(Object scopeObjectOriginal) {
        ArrayList<com.oracle.truffle.api.Scope> scopes = new ArrayList<>(3);
        Object scopeObject = scopeObjectOriginal;
        while (scopeObject != null) {
            InteropLibrary scopeInterop = InteropLibrary.getUncached(scopeObject);
            Object scopeParent = null;
            if (scopeInterop.hasScopeParent(scopeObject)) {
                try {
                    scopeParent = scopeInterop.getScopeParent(scopeObject);
                } catch (UnsupportedMessageException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
            }
            Object variables;
            if (scopeParent != null) {
                variables = new SubtractedVariables(scopeObject, scopeParent, null);
            } else {
                variables = scopeObject;
            }
            String name;
            try {
                name = InteropLibrary.getUncached().asString(scopeInterop.toDisplayString(scopeObject));
            } catch (UnsupportedMessageException ex) {
                name = "global";
            }
            com.oracle.truffle.api.Scope.Builder scopeBuilder;
            scopeBuilder = com.oracle.truffle.api.Scope.newBuilder(name, variables);
            scopes.add(scopeBuilder.build());
            scopeObject = scopeParent;
        }
        return scopes;
    }

    private static Object getArguments(Node node, Frame frame) {
        Node n = node;
        while (n != null && (!(n instanceof InstrumentableNode) || !((InstrumentableNode) n).hasTag(StandardTags.RootTag.class))) {
            n = n.getParent();
        }
        if (n == null || !NodeLibrary.getUncached().hasScope(n, frame)) {
            return null;
        }
        try {
            Object argScope = NodeLibrary.getUncached().getScope(n, frame, true);
            String receiverName = getReceiverName(NodeLibrary.getUncached(), n, frame);
            if (InteropLibrary.getUncached().hasScopeParent(argScope)) {
                argScope = new SubtractedVariables(argScope, InteropLibrary.getUncached().getScopeParent(argScope), receiverName);
            } else if (receiverName != null) {
                argScope = new NoReceiverVariables(argScope, receiverName);
            }
            return argScope;
        } catch (UnsupportedMessageException e) {
            return null;
        }
    }

    static boolean legacyScopesHasScope(NodeInterface node, Iterator<com.oracle.truffle.api.Scope> legacyScopes) {
        assert legacyScopes.hasNext();
        if (node instanceof InstrumentableNode && ((InstrumentableNode) node).hasTag(StandardTags.RootTag.class)) {
            while (legacyScopes.hasNext()) {
                com.oracle.truffle.api.Scope scope = legacyScopes.next();
                if (scope.getNode() == null || scope.getNode() instanceof RootNode) {
                    return scope.getArguments() != null;
                }
            }
        }
        return true;
    }

    static Object legacyScopes2ScopeObject(NodeInterface node, Iterator<com.oracle.truffle.api.Scope> legacyScopes, Class<? extends TruffleLanguage<?>> language) {
        if (!legacyScopes.hasNext()) {
            return new EmptyObject(language);
        }
        CompilerAsserts.neverPartOfCompilation();
        ArrayList<com.oracle.truffle.api.Scope> scopesList = new ArrayList<>(5);
        if (node instanceof InstrumentableNode && ((InstrumentableNode) node).hasTag(StandardTags.RootTag.class)) {
            // Provide the arguments
            while (legacyScopes.hasNext()) {
                com.oracle.truffle.api.Scope scope = legacyScopes.next();
                scopesList.add(scope);
                if (scope.getNode() == null || scope.getNode() instanceof RootNode) {
                    Object argumentsObj = scope.getArguments();
                    if (argumentsObj == null) {
                        return null;
                    }
                    return new MergedScopes(new com.oracle.truffle.api.Scope[]{scope}, new Object[]{argumentsObj}, language);
                }
            }
        } else {
            while (legacyScopes.hasNext()) {
                scopesList.add(legacyScopes.next());
            }
        }
        com.oracle.truffle.api.Scope[] scopes = scopesList.toArray(new com.oracle.truffle.api.Scope[scopesList.size()]);
        Object[] variables = new Object[scopes.length];
        for (int i = 0; i < scopes.length; i++) {
            variables[i] = scopes[i].getVariables();
        }
        return new MergedScopes(scopes, variables, language);
    }

    @ExportLibrary(InteropLibrary.class)
    static class EmptyObject implements TruffleObject {

        private final Class<? extends TruffleLanguage<?>> language;

        EmptyObject(Class<? extends TruffleLanguage<?>> language) {
            this.language = language;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return language;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new EmptyKeys();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "empty";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class EmptyKeys implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        long getArraySize() {
            return 0;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean isArrayElementReadable(@SuppressWarnings("unused") long index) {
            return false;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            throw InvalidArrayIndexException.create(index);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class NoReceiverVariables implements TruffleObject {

        private final Object allVariables;
        private final InteropLibrary allLibrary;
        private final String receiverName;

        NoReceiverVariables(Object allVariables, String receiverName) {
            this.allVariables = allVariables;
            this.allLibrary = InteropLibrary.getUncached(allVariables);
            assert receiverName != null;
            this.receiverName = receiverName;
        }

        @ExportMessage
        @TruffleBoundary
        final boolean hasMembers() {
            return allLibrary.hasMembers(allVariables);
        }

        @ExportMessage
        @TruffleBoundary
        final Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return new SubtractedReceiver(allLibrary.getMembers(allVariables, includeInternal));
        }

        @ExportMessage
        @TruffleBoundary
        final boolean isMemberReadable(String member) {
            if (receiverName.equals(member)) {
                return false;
            }
            return allLibrary.isMemberReadable(allVariables, member);
        }

        @ExportMessage
        @TruffleBoundary
        final Object readMember(String member) throws UnknownIdentifierException, UnsupportedMessageException {
            if (isMemberReadable(member)) {
                return allLibrary.readMember(allVariables, member);
            } else {
                throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        @TruffleBoundary
        final boolean isMemberModifiable(String member) {
            if (receiverName.equals(member)) {
                return false;
            }
            return allLibrary.isMemberModifiable(allVariables, member);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        final void writeMember(String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            if (isMemberModifiable(member)) {
                allLibrary.writeMember(allVariables, member, value);
            } else {
                throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        @TruffleBoundary
        final boolean hasMemberReadSideEffects(String member) {
            return isMemberReadable(member) && allLibrary.hasMemberReadSideEffects(allVariables, member);
        }

        @ExportMessage
        @TruffleBoundary
        final boolean hasMemberWriteSideEffects(String member) {
            return isMemberModifiable(member) && allLibrary.hasMemberWriteSideEffects(allVariables, member);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class SubtractedReceiver implements TruffleObject {

        private final Object allKeys;
        private final long allSize;

        SubtractedReceiver(Object allKeys) throws UnsupportedMessageException {
            this.allKeys = allKeys;
            this.allSize = INTEROP.getArraySize(allKeys);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return allSize - 1;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interop) throws InvalidArrayIndexException, UnsupportedMessageException {
            if (0 <= index && index < getArraySize()) {
                return interop.readArrayElement(allKeys, index);
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index,
                        @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interop) {
            if (0 <= index && index < getArraySize()) {
                return interop.isArrayElementReadable(allKeys, index);
            } else {
                return false;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class SubtractedVariables implements TruffleObject {

        private final Object allVariables;
        private final InteropLibrary allLibrary;
        private final Object removedVariables;
        private final InteropLibrary removedLibrary;
        private final String receiverName;

        SubtractedVariables(Object allVariables, Object removedVariables, String receiverName) {
            this.allVariables = allVariables;
            this.allLibrary = InteropLibrary.getUncached(allVariables);
            this.removedVariables = removedVariables;
            this.removedLibrary = InteropLibrary.getUncached(removedVariables);
            this.receiverName = receiverName;
        }

        @ExportMessage
        @TruffleBoundary
        final boolean hasMembers() {
            return allLibrary.hasMembers(allVariables) && removedLibrary.hasMembers(removedVariables);
        }

        @ExportMessage
        @TruffleBoundary
        final Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return new SubtractedKeys(allLibrary.getMembers(allVariables, includeInternal), removedLibrary.getMembers(removedVariables, includeInternal), receiverName);
        }

        @ExportMessage
        @TruffleBoundary
        final boolean isMemberReadable(String member) {
            if (receiverName != null && receiverName.equals(member)) {
                return false;
            }
            if (!allLibrary.isMemberReadable(allVariables, member)) {
                return false;
            }
            if (!removedLibrary.isMemberReadable(removedVariables, member)) {
                return true;
            }
            // Test if it's among subtracted members:
            return isAmongMembers(member);
        }

        private boolean isAmongMembers(String member) {
            try {
                Object members = getMembers(true);
                InteropLibrary membersLibrary = InteropLibrary.getUncached(members);
                long n = membersLibrary.getArraySize(members);
                for (long i = 0; i < n; i++) {
                    String m = InteropLibrary.getUncached().asString(membersLibrary.readArrayElement(members, i));
                    if (member.equals(m)) {
                        return true;
                    }
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        final Object readMember(String member) throws UnknownIdentifierException, UnsupportedMessageException {
            if (isMemberReadable(member)) {
                return allLibrary.readMember(allVariables, member);
            } else {
                throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        @TruffleBoundary
        final boolean isMemberModifiable(String member) {
            if (receiverName != null && receiverName.equals(member)) {
                return false;
            }
            if (!allLibrary.isMemberModifiable(allVariables, member)) {
                return false;
            }
            if (!removedLibrary.isMemberModifiable(removedVariables, member)) {
                return true;
            }
            // If it's among members, it might be modifiable
            return isAmongMembers(member);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        final void writeMember(String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            if (isMemberModifiable(member)) {
                allLibrary.writeMember(allVariables, member, value);
            } else {
                throw UnknownIdentifierException.create(member);
            }
        }

        @ExportMessage
        @TruffleBoundary
        final boolean hasMemberReadSideEffects(String member) {
            return isMemberReadable(member) && allLibrary.hasMemberReadSideEffects(allVariables, member);
        }

        @ExportMessage
        @TruffleBoundary
        final boolean hasMemberWriteSideEffects(String member) {
            return isMemberModifiable(member) && allLibrary.hasMemberWriteSideEffects(allVariables, member);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class SubtractedKeys implements TruffleObject {

        private final Object allKeys;
        private final long allSize;
        private final long removedSize;
        private final long size;

        SubtractedKeys(Object allKeys, Object removedKeys, String receiverNameOrig) throws UnsupportedMessageException {
            this.allKeys = allKeys;
            this.allSize = INTEROP.getArraySize(allKeys);
            this.removedSize = INTEROP.getArraySize(removedKeys);
            String receiverName = receiverNameOrig;
            if (receiverName != null) {
                long receiverIndex = allSize - removedSize - 1;
                if (receiverIndex < 0) {
                    receiverName = null;
                } else {
                    try {
                        if (!receiverName.equals(InteropLibrary.getUncached().readArrayElement(allKeys, receiverIndex))) {
                            // The receiver is not actually present in variables
                            receiverName = null;
                        }
                    } catch (InteropException e) {
                        CompilerDirectives.shouldNotReachHere(e);
                    }
                }
            }
            this.size = allSize - removedSize - (receiverName != null ? 1 : 0);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return size;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interop) throws InvalidArrayIndexException, UnsupportedMessageException {
            if (0 <= index && index < getArraySize()) {
                return interop.readArrayElement(allKeys, index);
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index,
                        @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interop) {
            if (0 <= index && index < getArraySize()) {
                return interop.isArrayElementReadable(allKeys, index);
            } else {
                return false;
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MergedScopes implements TruffleObject {

        static final int LIMIT = 5;

        private final com.oracle.truffle.api.Scope[] scopes;
        private final Object[] variables;
        private final int scopeIndex;
        private final String receiverName;
        private final Class<? extends TruffleLanguage<?>> language;
        private volatile SourceSection cachedSourceLocation;
        private volatile boolean hasCachedSourceLocation = false;

        MergedScopes(com.oracle.truffle.api.Scope[] scopes, Object[] variables, Class<? extends TruffleLanguage<?>> language) {
            this(scopes, variables, 0, language);
        }

        private MergedScopes(com.oracle.truffle.api.Scope[] scopes, Object[] variables, int scopeIndex, Class<? extends TruffleLanguage<?>> language) {
            this.scopes = scopes;
            this.variables = variables;
            this.scopeIndex = scopeIndex;
            String aReceiverName = null;
            if (scopeIndex == 0) {
                for (int i = 0; i < scopes.length; i++) {
                    if (scopes[i].getReceiverName() != null) {
                        aReceiverName = scopes[i].getReceiverName();
                        break;
                    }
                }
            }
            this.receiverName = aReceiverName;
            this.language = language;
        }

        private Object getReceiverValue() {
            for (int i = scopeIndex; i < scopes.length; i++) {
                if (scopes[i].getReceiverName() != null) {
                    return scopes[i].getReceiver();
                }
            }
            return null;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return language;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return scopes[scopeIndex].getName();
        }

        @ExportMessage
        boolean hasSourceLocation() {
            Node node = scopes[scopeIndex].getNode();
            if (node == null) {
                return false;
            }
            CompilerDirectives.transferToInterpreter();
            return getSourceSection(node) != null;
        }

        @ExportMessage
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            Node node = scopes[scopeIndex].getNode();
            if (node != null) {
                CompilerDirectives.transferToInterpreter();
                SourceSection section = getSourceSection(node);
                if (section != null) {
                    return section;
                } else {
                    throw UnsupportedMessageException.create();
                }
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        private SourceSection getSourceSection(Node node) {
            assert CompilerDirectives.inInterpreter();
            if (hasCachedSourceLocation) {
                return cachedSourceLocation;
            }
            SourceSection section = null;
            if (node instanceof RootNode && node.getSourceSection() == null) {
                SourceSection[] rootSection = new SourceSection[]{null};
                node.accept(new NodeVisitor() {
                    @Override
                    public boolean visit(Node n) {
                        if (n instanceof InstrumentableNode) {
                            InstrumentableNode inode = (InstrumentableNode) n;
                            if (inode.isInstrumentable() && inode.hasTag(StandardTags.RootTag.class)) {
                                rootSection[0] = n.getSourceSection();
                                return false;
                            }
                        }
                        return true;
                    }
                });
                section = rootSection[0];
            } else {
                section = node.getSourceSection();
            }
            cachedSourceLocation = section;
            hasCachedSourceLocation = true;
            return section;
        }

        @ExportMessage
        boolean hasScopeParent() {
            return scopeIndex < (scopes.length - 1);
        }

        @ExportMessage
        Object getScopeParent() throws UnsupportedMessageException {
            if (scopeIndex < (scopes.length - 1)) {
                return new MergedScopes(scopes, variables, scopeIndex + 1, language);
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        @ExportMessage
        boolean hasMembers(@Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
            int length = lengthProfile.profile(scopes.length);
            for (int i = scopeIndex; i < length; i++) {
                Object vars = this.variables[i];
                if (interop.hasMembers(vars)) {
                    return true;
                }
            }
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws UnsupportedMessageException {
            int length = scopes.length;
            Object[] keys = new Object[length - scopeIndex];
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                keys[i - scopeIndex] = interop.getMembers(scope);
            }
            return new MergedVarNames(keys, receiverName);
        }

        @ExportMessage
        boolean isMemberReadable(String member,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
            if (member.equals(receiverName)) {
                return getReceiverValue() != null;
            }
            int length = lengthProfile.profile(scopes.length);
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberReadable(scope, member)) {
                    return true;
                }
            }
            return false;
        }

        @ExportMessage
        Object readMember(String member,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) throws UnknownIdentifierException, UnsupportedMessageException {
            if (member.equals(receiverName)) {
                return getReceiverValue();
            }
            int length = lengthProfile.profile(scopes.length);
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberReadable(scope, member)) {
                    return interop.readMember(scope, member);
                }
            }
            throw UnknownIdentifierException.create(member);
        }

        @ExportMessage
        boolean isMemberModifiable(String member,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
            if (member.equals(receiverName)) {
                return false;
            }
            int length = lengthProfile.profile(scopes.length);
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberModifiable(scope, member)) {
                    return true;
                }
            }
            return false;
        }

        @ExportMessage
        boolean isMemberInsertable(String member,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
            if (member.equals(receiverName)) {
                return false;
            }
            int length = lengthProfile.profile(scopes.length);
            boolean wasInsertable = false;
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberExisting(scope, member)) {
                    return false;
                }
                if (interop.isMemberInsertable(scope, member)) {
                    wasInsertable = true;
                }
            }
            return wasInsertable;
        }

        @ExportMessage
        boolean hasMemberReadSideEffects(String member,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
            if (member.equals(receiverName)) {
                return false;
            }
            int length = lengthProfile.profile(scopes.length);
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberReadable(scope, member)) {
                    return interop.hasMemberReadSideEffects(scope, member);
                }
            }
            return false;
        }

        @ExportMessage
        boolean hasMemberWriteSideEffects(String member,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
            if (member.equals(receiverName)) {
                return false;
            }
            int length = lengthProfile.profile(scopes.length);
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberWritable(scope, member)) {
                    return interop.hasMemberWriteSideEffects(scope, member);
                }
            }
            return false;
        }

        @ExportMessage
        void writeMember(String member, Object value,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile)
                        throws UnknownIdentifierException, UnsupportedMessageException, UnsupportedTypeException {
            if (member.equals(receiverName)) {
                throw UnsupportedMessageException.create();
            }
            int length = lengthProfile.profile(scopes.length);
            Object firstInsertableScope = null;
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberExisting(scope, member)) {
                    // existed therefore it cannot be insertable any more
                    if (interop.isMemberModifiable(scope, member)) {
                        interop.writeMember(scope, member, value);
                        return;
                    } else {
                        // we cannot modify nor insert
                        throw UnsupportedMessageException.create();
                    }
                }
                if (interop.isMemberInsertable(scope, member) && firstInsertableScope == null) {
                    firstInsertableScope = scope;
                }
            }

            if (firstInsertableScope != null) {
                interop.writeMember(firstInsertableScope, member, value);
                return;
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        boolean isMemberRemovable(String member,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) {
            if (member.equals(receiverName)) {
                return false;
            }
            int length = lengthProfile.profile(scopes.length);
            for (int i = scopeIndex; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberRemovable(scope, member)) {
                    return true;
                } else if (interop.isMemberExisting(scope, member)) {
                    return false;
                }
            }
            return false;
        }

        @ExportMessage
        void removeMember(String member,
                        @Shared("interop") @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Shared("lenghtProfile") @Cached("createIdentityProfile()") IntValueProfile lengthProfile) throws UnsupportedMessageException, UnknownIdentifierException {
            if (member.equals(receiverName)) {
                throw UnsupportedMessageException.create();
            }
            int length = lengthProfile.profile(scopes.length);
            for (int i = 0; i < length; i++) {
                Object scope = this.variables[i];
                if (interop.isMemberRemovable(scope, member)) {
                    interop.removeMember(scope, member);
                    return;
                } else if (interop.isMemberExisting(scope, member)) {
                    break;
                }
            }
            throw UnsupportedMessageException.create();
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class MergedVarNames implements TruffleObject {

        private final Object[] keys;
        private final long[] size;
        private final String receiverName;

        private MergedVarNames(Object[] keys, String receiverName) throws UnsupportedMessageException {
            this.keys = keys;
            this.receiverName = receiverName;
            size = new long[keys.length];
            long s = 0L;
            InteropLibrary interop = InteropLibrary.getUncached();
            for (int i = 0; i < keys.length; i++) {
                s += interop.getArraySize(keys[i]);
                if (i == 0 && receiverName != null) {
                    s++;
                }
                size[i] = s;
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return size[size.length - 1];
        }

        @ExportMessage
        boolean isArrayElementReadable(long index,
                        @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop) {
            if (index >= 0) {
                for (int i = 0; i < keys.length; i++) {
                    if (index < size[i]) {
                        if (receiverName != null && i == 0 && index == size[0] - 1) {
                            return true; // the receiver
                        }
                        long start = (i == 0) ? 0 : size[i - 1];
                        return interop.isArrayElementReadable(keys[i], index - start);
                    }
                }
            }
            return false;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Shared("interop") @CachedLibrary(limit = "5") InteropLibrary interop) throws InvalidArrayIndexException, UnsupportedMessageException {
            if (index >= 0) {
                for (int i = 0; i < keys.length; i++) {
                    if (index < size[i]) {
                        if (receiverName != null && i == 0 && index == size[0] - 1) {
                            return receiverName; // the receiver
                        }
                        long start = (i == 0) ? 0 : size[i - 1];
                        return interop.readArrayElement(keys[i], index - start);
                    }
                }
            }
            throw InvalidArrayIndexException.create(index);
        }

    }
}
