/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Representation of guest language scope at the current suspension point, or a top scope. It
 * contains a set of declared and valid variables, if any. The scope associated with a
 * {@link DebugStackFrame frame} is only valid as long as the associated {@link DebugStackFrame
 * frame} is valid and methods on such scope need to be called on the frame's thread.
 *
 * @see DebugStackFrame#getScope()
 * @see DebuggerSession#getTopScope(String)
 * @since 0.26
 */
public final class DebugScope {

    private static final InteropLibrary INTEROP = InteropLibrary.getUncached();
    private static final NodeLibrary NODE = NodeLibrary.getUncached();

    private final Object scope;
    private final DebuggerSession session;
    private final SuspendedEvent event;
    private final Node node;
    private final Frame frame;
    private final RootNode root;
    private final LanguageInfo language;
    private DebugScope parent;
    private ValuePropertiesCollection variables;

    DebugScope(Object scope, DebuggerSession session,
                    SuspendedEvent event, Node node, Frame frame, RootNode root) {
        this(scope, session, event, node, frame, root, null);
    }

    DebugScope(Object scope, DebuggerSession session,
                    LanguageInfo language) {
        this(scope, session, null, null, null, null, language);
    }

    private DebugScope(Object scope, DebuggerSession session,
                    SuspendedEvent event, Node node, Frame frame, RootNode root,
                    LanguageInfo language) {
        this.scope = scope;
        this.session = session;
        this.event = event;
        this.node = node;
        this.frame = frame;
        this.root = root;
        this.language = language;
    }

    /**
     * Get a human readable name of this scope.
     *
     * @since 0.26
     */
    public String getName() {
        try {
            return INTEROP.asString(INTEROP.toDisplayString(scope));
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, language, node, true, null);
        }
    }

    /**
     * Get a parent scope.
     *
     * @return the parent scope, or <code>null</code>.
     * @throws DebugException when guest language code throws an exception
     * @since 0.26
     */
    public DebugScope getParent() throws DebugException {
        verifyValidState();
        try {
            if (parent == null && INTEROP.hasScopeParent(scope)) {
                parent = new DebugScope(INTEROP.getScopeParent(scope), session, event, node, frame, root, language);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, language);
        }
        return parent;
    }

    /**
     * Test if this scope represents the function scope at the frame it was
     * {@link DebugStackFrame#getScope() obtained from}.
     *
     * @since 0.26
     */
    public boolean isFunctionScope() {
        SourceSection rootSourceSection = getRootSourceSection();
        try {
            return rootSourceSection != null && INTEROP.hasSourceLocation(scope) && rootSourceSection.equals(INTEROP.getSourceLocation(scope));
        } catch (UnsupportedMessageException e) {
            return false;
        }
    }

    private SourceSection getRootSourceSection() {
        if (root == null) {
            return null;
        }
        SourceSection rootSourceSection = root.getSourceSection();
        if (rootSourceSection == null) {
            SourceSection[] rootSection = new SourceSection[]{null};
            root.accept(new NodeVisitor() {
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
            rootSourceSection = rootSection[0];
        }
        return rootSourceSection;
    }

    /**
     * Get a source section representing this scope. Please note that while this scope does not
     * provide variables that are valid only after the suspension point, the source section can
     * actually span after the suspension point.
     *
     * @return the source section, or <code>null</code> when not available.
     * @throws DebugException when guest language code throws an exception
     * @since 0.29
     */
    public SourceSection getSourceSection() throws DebugException {
        try {
            if (!INTEROP.hasSourceLocation(scope)) {
                return null;
            }
            SourceSection location = INTEROP.getSourceLocation(scope);
            if (location != null) {
                return session.resolveSection(location);
            } else {
                return null;
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, language);
        }
    }

    /**
     * Get arguments of this scope. If this scope is a {@link #isFunctionScope() function} scope,
     * function arguments are returned.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @return an iterable of arguments, or <code>null</code> when this scope does not have a
     *         concept of arguments.
     * @throws DebugException when guest language code throws an exception
     * @since 0.26
     * @deprecated since 20.3 Use {@link #getDeclaredValues()} on the {@link SourceElement#ROOT}.
     */
    @Deprecated(since = "20.3")
    public Iterable<DebugValue> getArguments() throws DebugException {
        verifyValidState();
        if (node == null) {
            return null;
        }
        try {
            Node argNode = node;
            while (argNode != null && (!(argNode instanceof InstrumentableNode) || !((InstrumentableNode) argNode).hasTag(StandardTags.RootTag.class))) {
                argNode = argNode.getParent();
            }
            if (argNode == null || !NODE.hasScope(argNode, frame)) {
                return null;
            }
            Object argumentsObj;
            try {
                argumentsObj = NODE.getScope(argNode, frame, true);
                if (INTEROP.hasScopeParent(argumentsObj)) {
                    argumentsObj = new SubtractedVariables(argumentsObj, INTEROP.getScopeParent(argumentsObj));
                }
            } catch (UnsupportedMessageException e) {
                return null;
            }
            if (argumentsObj != null) {
                String receiverName = null;
                if (NODE.hasReceiverMember(argNode, frame)) {
                    receiverName = INTEROP.asString(NODE.getReceiverMember(argNode, frame));
                }
                ValuePropertiesCollection properties = DebugValue.getProperties(argumentsObj, receiverName, session, getLanguage(), this);
                if (properties != null) {
                    return properties;
                }
                if (ValueInteropList.INTEROP.hasArrayElements(argumentsObj)) {
                    return new ValueInteropList(session, getLanguage(), argumentsObj);
                }
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, language);
        }
        return null;
    }

    /**
     * Get value that represents the receiver object of this scope. The receiver object is
     * represented as <code>this</code> in Java or JavaScript and <code>self</code> in Ruby, for
     * instance.
     * <p>
     * The returned value has a name that represents the receiver in the guest language. The scope
     * that {@link #isFunctionScope() represents the function} provide receiver object, if there is
     * one, other scopes do not provide it, unless they override it.
     *
     * @return value that represents the receiver, or <code>null</code> when there is no receiver
     *         object
     * @since 19.0
     */
    public DebugValue getReceiver() {
        verifyValidState();
        DebugValue receiverValue = null;
        try {
            if (node == null || !NODE.hasReceiverMember(node, frame)) {
                return null;
            }
            String name = INTEROP.asString(NODE.getReceiverMember(node, frame));
            if (!INTEROP.isMemberReadable(scope, name) || !isDeclaredInScope(name)) {
                return null;
            }
            receiverValue = new DebugValue.ObjectMemberValue(session, getLanguage(), this, scope, name);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, language);
        }
        return receiverValue;
    }

    /**
     * Get value that represents root instance of this scope. The value is an instance of guest
     * language representation of the root node of this scope, e.g. a guest language function.
     *
     * @return the root instance value, or <code>null</code> when no such value exists.
     * @since 19.3.0
     */
    public DebugValue getRootInstance() {
        verifyValidState();
        DebugValue functionValue = null;
        try {
            if (node == null || !NODE.hasRootInstance(node, frame)) {
                return null;
            }
            Object function = NODE.hasRootInstance(node, frame);
            if (function != null) {
                String name;
                if (INTEROP.hasExecutableName(function)) {
                    name = INTEROP.asString(INTEROP.getExecutableName(function));
                } else {
                    name = root.getName();
                }
                functionValue = new DebugValue.HeapValue(session, getLanguage(), name, function);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, language);
        }
        return functionValue;
    }

    /**
     * Get local variables declared in this scope, valid at the current suspension point. Call this
     * method on {@link #getParent() parent}, to get values of variables declared in parent scope,
     * if any. The declared values do not contain a {@link #getReceiver() receiver}.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 0.26
     */
    public Iterable<DebugValue> getDeclaredValues() throws DebugException {
        return getVariables();
    }

    /**
     * Get a local variable declared in this scope by name. Call this method on {@link #getParent()
     * parent}, to get value of a variable declared in parent scope, if any.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @return a value of requested name, or <code>null</code> when no such value was found.
     * @throws DebugException when guest language code throws an exception
     * @since 0.26
     */
    public DebugValue getDeclaredValue(String name) throws DebugException {
        return getVariables().get(name);
    }

    RootNode getRoot() {
        return root;
    }

    private ValuePropertiesCollection getVariables() {
        verifyValidState();
        try {
            if (variables == null) {
                Object scopeParent = null;
                if (INTEROP.hasScopeParent(scope)) {
                    try {
                        scopeParent = INTEROP.getScopeParent(scope);
                    } catch (UnsupportedMessageException ex) {
                        throw CompilerDirectives.shouldNotReachHere(ex);
                    }
                }
                Object variablesObj;
                if (scopeParent != null) {
                    variablesObj = new SubtractedVariables(scope, scopeParent);
                } else {
                    variablesObj = scope;
                }
                String receiverName = null;
                if (node != null && NODE.hasReceiverMember(node, frame)) {
                    receiverName = INTEROP.asString(NODE.getReceiverMember(node, frame));
                }
                variables = DebugValue.getProperties(variablesObj, receiverName, session, getLanguage(), this);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, language);
        }
        return variables;
    }

    private boolean isDeclaredInScope(String name) {
        Object scopeParent = null;
        if (INTEROP.hasScopeParent(scope)) {
            try {
                scopeParent = INTEROP.getScopeParent(scope);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        if (scopeParent == null) {
            return true;
        }
        return new SubtractedVariables(scope, scopeParent).isMemberReadable(name);
    }

    /**
     * Converts the value to a DebugValue, or returns <code>null</code> if the requesting language
     * class does not match the root node guest language.
     *
     * This method is permitted only if the guest language class is available. This is the case if
     * you want to utilize the Debugger API directly from within a guest language, or if you are an
     * instrument bound/dependent on a specific language.
     *
     * This method is opposite to {@link DebugValue#getRawValue(Class)} where the raw guest language
     * value is obtained from the DebugValue.
     *
     * Note that the <code>rawValue</code> must be a valid Interop value. If not, the method throws
     * IllegalArgumentException.
     *
     * @param languageClass the Truffle language class for a given guest language
     * @param rawValue the raw value
     * @return the wrapped DebugValue
     * @throws IllegalArgumentException when <code>rawValue</code> is not an Interop value
     * @since 21.1
     */
    public DebugValue convertRawValue(Class<? extends TruffleLanguage<?>> languageClass, Object rawValue) {
        Objects.requireNonNull(languageClass);
        RootNode rootNode = getRoot();
        if (rootNode == null) {
            return null;
        }
        // make sure rawValue is a valid Interop value
        if (!InteropLibrary.isValidValue(rawValue)) {
            throw new IllegalArgumentException("raw value is not an Interop value");
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> truffleLanguage = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return truffleLanguage != null && truffleLanguage.getClass() == languageClass ? new DebugValue.HeapValue(session, null, rawValue) : null;
    }

    LanguageInfo getLanguage() {
        if (root != null) {
            return root.getLanguageInfo();
        } else {
            return language;
        }
    }

    void verifyValidState() {
        if (event != null) {
            event.verifyValidState(false);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class SubtractedVariables implements TruffleObject {

        private final Object allVariables;
        private final InteropLibrary allLibrary;
        private final Object removedVariables;
        private final InteropLibrary removedLibrary;

        SubtractedVariables(Object allVariables, Object removedVariables) {
            this.allVariables = allVariables;
            this.allLibrary = InteropLibrary.getUncached(allVariables);
            this.removedVariables = removedVariables;
            this.removedLibrary = InteropLibrary.getUncached(removedVariables);
        }

        @ExportMessage
        @TruffleBoundary
        final boolean hasMembers() {
            return allLibrary.hasMembers(allVariables) && removedLibrary.hasMembers(removedVariables);
        }

        @ExportMessage
        @TruffleBoundary
        final Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return new SubtractedKeys(allLibrary.getMembers(allVariables, includeInternal), removedLibrary.getMembers(removedVariables, includeInternal));
        }

        @ExportMessage
        @TruffleBoundary
        final boolean isMemberReadable(String member) {
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
                    String m = INTEROP.asString(membersLibrary.readArrayElement(members, i));
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
        final boolean hasMemberReadSideEffects(String member) {
            return isMemberReadable(member) && allLibrary.hasMemberReadSideEffects(allVariables, member);
        }

        @ExportMessage
        final boolean hasMemberWriteSideEffects(String member) {
            return isMemberModifiable(member) && allLibrary.hasMemberWriteSideEffects(allVariables, member);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class SubtractedKeys implements TruffleObject {

        private final Object allKeys;
        private final long allSize;
        private final long removedSize;

        SubtractedKeys(Object allKeys, Object removedKeys) throws UnsupportedMessageException {
            this.allKeys = allKeys;
            this.allSize = INTEROP.getArraySize(allKeys);
            this.removedSize = INTEROP.getArraySize(removedKeys);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return allSize - removedSize;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException, UnsupportedMessageException {
            if (0 <= index && index < getArraySize()) {
                return INTEROP.readArrayElement(allKeys, index);
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            if (0 <= index && index < getArraySize()) {
                return INTEROP.isArrayElementReadable(allKeys, index);
            } else {
                return false;
            }
        }
    }

}
