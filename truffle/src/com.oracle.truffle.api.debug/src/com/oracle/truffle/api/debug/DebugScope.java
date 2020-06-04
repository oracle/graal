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
package com.oracle.truffle.api.debug;

import java.util.Iterator;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Representation of guest language scope at the current suspension point. It contains a set of
 * declared and valid variables as well as arguments, if any. The scope is only valid as long as the
 * associated {@link DebugStackFrame frame} is valid.
 *
 * @see DebugStackFrame#getScope()
 * @since 0.26
 */
public final class DebugScope {

    private final Scope scope;
    private final Iterator<Scope> iterator;
    private final DebuggerSession session;
    private final SuspendedEvent event;
    private final Frame frame;
    private final RootNode root;
    private final LanguageInfo language;
    private DebugScope parent;
    private ValuePropertiesCollection variables;

    DebugScope(Scope scope, Iterator<Scope> iterator, DebuggerSession session,
                    SuspendedEvent event, Frame frame, RootNode root) {
        this(scope, iterator, session, event, frame, root, null);
    }

    DebugScope(Scope scope, Iterator<Scope> iterator, DebuggerSession session,
                    LanguageInfo language) {
        this(scope, iterator, session, null, null, null, language);
    }

    private DebugScope(Scope scope, Iterator<Scope> iterator, DebuggerSession session,
                    SuspendedEvent event, Frame frame, RootNode root,
                    LanguageInfo language) {
        this.scope = scope;
        this.iterator = iterator;
        this.session = session;
        this.event = event;
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
        return scope.getName();
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
            if (parent == null && iterator.hasNext()) {
                parent = new DebugScope(iterator.next(), iterator, session, event, frame, root, language);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(session, ex, language, null, true, null);
        }
        return parent;
    }

    /**
     * Test if this scope represents the function scope at the frame it was
     * {@link DebugStackFrame#getScope() obtained from}. {@link #getArguments() arguments} of
     * function scope represent arguments of the appropriate function.
     *
     * @since 0.26
     */
    public boolean isFunctionScope() {
        return root != null && root.equals(scope.getNode());
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
            Node node = scope.getNode();
            if (node != null) {
                return session.resolveSection(node);
            } else {
                return null;
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(session, ex, language, null, true, null);
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
     */
    public Iterable<DebugValue> getArguments() throws DebugException {
        verifyValidState();
        try {
            Object argumentsObj = scope.getArguments();
            if (argumentsObj != null) {
                ValuePropertiesCollection properties = DebugValue.getProperties(argumentsObj, session, getLanguage(), this);
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
            throw new DebugException(session, ex, language, null, true, null);
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
            Object receiver = scope.getReceiver();
            if (receiver != null) {
                receiverValue = new DebugValue.HeapValue(session, getLanguage(), scope.getReceiverName(), receiver);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(session, ex, language, null, true, null);
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
            Object function = scope.getRootInstance();
            if (function != null) {
                functionValue = new DebugValue.HeapValue(session, getLanguage(), root.getName(), function);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(session, ex, language, null, true, null);
        }
        return functionValue;
    }

    /**
     * Get local variables declared in this scope, valid at the current suspension point. Call this
     * method on {@link #getParent() parent}, to get values of variables declared in parent scope,
     * if any.
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
                Object variablesObj = scope.getVariables();
                variables = DebugValue.getProperties(variablesObj, session, getLanguage(), this);
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(session, ex, language, null, true, null);
        }
        return variables;
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
}
