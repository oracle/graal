/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.interop.AssertUtils.assertString;
import static com.oracle.truffle.api.interop.AssertUtils.validScope;
import static com.oracle.truffle.api.interop.AssertUtils.violationInvariant;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Node Library provides access to guest language information associated with a particular
 * {@link Node} location. The receiver of this library must be an
 * {@link com.oracle.truffle.api.instrumentation.InstrumentableNode#isInstrumentable()
 * instrumentable} {@link Node} that has a {@link Node#getRootNode() root node}.
 *
 * @since 20.3
 */
@DefaultExport(DefaultNodeExports.class)
@GenerateLibrary(assertions = NodeLibrary.Asserts.class, receiverType = Node.class)
public abstract class NodeLibrary extends Library {

    static final LibraryFactory<NodeLibrary> FACTORY = LibraryFactory.resolve(NodeLibrary.class);

    /**
     * @since 20.3
     */
    protected NodeLibrary() {
    }

    /**
     * Returns <code>true</code>, if the node is in a scope containing local variables, else
     * <code>false</code>.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getScope(Object, Frame, boolean)} must be implemented.
     *
     * @param node a Node, never <code>null</code>
     * @param frame a {@link Frame}, can be <code>null</code> in case of lexical access.
     * @see #getScope(Object, Frame, boolean)
     * @since 20.3
     */
    @Abstract(ifExported = "getScope")
    public boolean hasScope(Object node, Frame frame) {
        return false;
    }

    /**
     * Get an object representing a local scope containing variables visible at the node location.
     * The returned object must respond <code>true</code> to {@link InteropLibrary#isScope(Object)}.
     * The scope object exposes all locally visible variables as flattened
     * {@link InteropLibrary#getMembers(Object) members}, including all parent scopes (for example
     * block, closure, lexical or dynamic scopes). Local scopes are associated with a {@link Frame}.
     * See {@link InteropLibrary#isScope(Object)} for details.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #hasScope(Object, Frame)} must be implemented.
     * <p>
     * A returned scope is only valid while the regular guest language execution on this thread is
     * suspended. If the guest execution is continued e.g. if a new instrumentation event occurs,
     * the scope is invalid and should be discarded.
     *
     * @param node a Node, never <code>null</code>
     * @param frame a {@link Frame}, can be <code>null</code> in case of lexical access.
     * @param nodeEnter when <code>true</code>, the location that determines scope variables is at
     *            the enter to the node execution, else <code>false</code>. Use <code>true</code>
     *            when called e.g. by instruments from
     *            {@link com.oracle.truffle.api.instrumentation.ExecutionEventNode#onEnter(VirtualFrame)}
     *            and <code>false</code> when called e.g. from
     *            {@link com.oracle.truffle.api.instrumentation.ExecutionEventNode#onReturnValue(VirtualFrame, Object)}.
     *            This argument helps to determine visible variables in case that some variables are
     *            added during the node execution.
     * @return An interop object with {@link InteropLibrary#isScope(Object)} being true.
     * @throws UnsupportedMessageException if and only if {@link #hasScope(Object, Frame)} returns
     *             <code>false</code> for the same node and frame.
     * @since 20.3
     */
    @Abstract(ifExported = {"hasScope", "getReceiverMember"})
    @SuppressWarnings("unchecked")
    public Object getScope(Object node, Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code>, if there is a named receiver object, else <code>false</code>.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getReceiverMember(Object, Frame)} must be implemented.
     *
     * @param node a Node, never <code>null</code>
     * @param frame a {@link Frame}, can be <code>null</code> in case of lexical access.
     * @see #getReceiverMember(Object, Frame)
     * @since 20.3
     */
    @Abstract(ifExported = {"getReceiverMember"})
    public boolean hasReceiverMember(Object node, Frame frame) {
        return false;
    }

    /**
     * Returns an object that represents the receiver name when available. The returned value is
     * guaranteed to return true for {@link InteropLibrary#isString(Object)}. Examples for receiver
     * names are <code>this</code> in Java or </code>self</code> in Ruby or Python. The value of the
     * receiver object can be retrieved from the {@link #getScope(Object, Frame, boolean) scope} by
     * converting the member to a string using {@link InteropLibrary#isString(Object)}. The return
     * value of this method is an Object intentionally to allow the member to have additional
     * interop traits.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #hasReceiverMember(Object, Frame)} must be implemented.
     *
     * @param node a Node, never <code>null</code>
     * @param frame a {@link Frame}, can be <code>null</code> in case of lexical access.
     * @return an {@link InteropLibrary#isString(Object) interop string object}.
     * @throws UnsupportedMessageException if and only if {@link #hasReceiverMember(Object, Frame)}
     *             returns <code>false</code> for the same node and frame.
     * @see #hasReceiverMember(Object, Frame)
     * @see #getScope(Object, Frame, boolean)
     * @since 20.3
     */
    @Abstract(ifExported = "hasReceiverMember")
    public Object getReceiverMember(Object node, Frame frame) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Returns <code>true</code>, if a current instance of the guest language representation of the
     * root node (e.g. the enclosing guest language function) is available, else <code>false</code>.
     * <p>
     * Guest languages will likely require a context lookup to resolve the root instance object.
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #getRootInstance(Object, Frame)} must be implemented.
     *
     * @param node a Node, never <code>null</code>
     * @param frame a {@link Frame}, can be <code>null</code> in case of lexical access, in which
     *            case the instance would likely not be available.
     * @see #getRootInstance(Object, Frame)
     * @since 20.3
     */
    @Abstract(ifExported = "getRootInstance")
    public boolean hasRootInstance(Object node, Frame frame) {
        return false;
    }

    /**
     * Returns the current instance of guest language representation of the root node (e.g. the
     * enclosing guest language function), when available.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link #hasRootInstance(Object, Frame)} must be implemented.
     *
     * @param node a Node, never <code>null</code>
     * @param frame a {@link Frame}, can be <code>null</code> in case of lexical access, in which
     *            case the instance would likely not be available.
     * @return the root instance value which must be an {@link InteropLibrary interop} and
     *         {@link InteropLibrary#isExecutable(Object) executable} value.
     * @throws UnsupportedMessageException if and only if {@link #hasRootInstance(Object, Frame)}
     *             returns <code>false</code> for the same node and frame.
     * @see #hasRootInstance(Object, Frame)
     * @since 20.3
     */
    @Abstract(ifExported = "hasRootInstance")
    public Object getRootInstance(Object node, Frame frame) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Wraps the value to filter or add scoping specific information for values associated with the
     * current language and location in the code. Allows the language to augment the perspective
     * tools have on values depending on location and frame. This may be useful to apply local
     * specific visibility rules. By default this method does return the passed value, not applying
     * any scope information to the value. If a language does not implement any scoping and/or has
     * not concept of visibility then this method typically can stay without an implementation. A
     * typical implementation of this method may do the following:
     * <ul>
     * <li>Apply visibility and scoping rules to the value hiding or removing members from the
     * object.
     * <li>Add or remove implicit members that are only available within this source location.
     * </ul>
     * <p>
     * This method is only invoked with values that are associated with the current
     * {@link InteropLibrary#getLanguage(Object) language}. For values without language the
     * {@link TruffleLanguage#getLanguageView(Object, Object) language view} is requested first
     * before the scoped view is requested. If this method needs an implementation then
     * {@link TruffleLanguage#getLanguageView(Object, Object)} should be implemented as well.
     * <p>
     * Scoped views may be implemented in a very similar way to
     * {@link TruffleLanguage#getLanguageView(Object, Object) language views}. Please refer to the
     * examples from this method.
     *
     * @param node the current source location. Guaranteed to be a node from a {@link RootNode}
     *            associated with this language. Never <code>null</code>.
     * @param frame the current active frame. Guaranteed to be a frame from a {@link RootNode}
     *            associated with this language. Never <code>null</code>.
     * @param value the value to provide scope information for. Never <code>null</code>. Always
     *            associated with this language.
     * @see TruffleLanguage#getLanguageView(Object, Object)
     * @since 20.3
     */
    public Object getView(Object node, Frame frame, Object value) {
        return value;
    }

    /**
     * Returns the library factory for the node library. Short-cut for
     * {@link LibraryFactory#resolve(Class) ResolvedLibrary.resolve(NodeLibrary.class)}.
     *
     * @see LibraryFactory#resolve(Class)
     * @since 20.3
     */
    public static LibraryFactory<NodeLibrary> getFactory() {
        return FACTORY;
    }

    /**
     * Returns the uncached automatically dispatched version of the node library. This is a
     * short-cut for calling <code>NodeLibrary.getFactory().getUncached()</code>.
     *
     * @see LibraryFactory#getUncached()
     * @since 20.3
     */
    public static NodeLibrary getUncached() {
        return getFactory().getUncached();
    }

    /**
     * Returns the uncached manually dispatched version of the node library. This is a short-cut for
     * calling <code>NodeLibrary.getFactory().getUncached(v)</code>.
     *
     * @see LibraryFactory#getUncached(Object)
     * @since 20.3
     */
    public static NodeLibrary getUncached(Object node) {
        return getFactory().getUncached(node);
    }

    static class Asserts extends NodeLibrary {

        @Child private NodeLibrary delegate;
        private final boolean isDefaultDelegate;

        Asserts(NodeLibrary delegate) {
            this.delegate = delegate;
            this.isDefaultDelegate = delegate.getClass().getName().startsWith(DefaultNodeExports.class.getName());
        }

        @Override
        public boolean accepts(Object receiver) {
            assert receiver instanceof Node;
            return delegate.accepts(receiver);
        }

        @Override
        public boolean hasScope(Object node, Frame frame) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.hasScope(node, frame);
            }
            assert validReceiver(node);
            boolean result = delegate.hasScope(node, frame);
            if (result) {
                try {
                    assert validScope(delegate.getScope(node, frame, true));
                } catch (InteropException e) {
                    assert false : violationInvariant(node);
                } catch (Exception e) {
                }
            } else {
                try {
                    delegate.getScope(node, frame, true);
                    assert false : violationInvariant(node);
                } catch (UnsupportedMessageException e) {
                }
            }
            return result;
        }

        @Override
        public Object getScope(Object node, Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getScope(node, frame, nodeEnter);
            }
            assert validReceiver(node);
            boolean hadScope = delegate.hasScope(node, frame);
            try {
                Object scope = delegate.getScope(node, frame, nodeEnter);
                assert hadScope : violationInvariant(node);
                assert validScope(scope);
                return scope;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(node);
                assert !hadScope : violationInvariant(node);
                throw e;
            }
        }

        @Override
        public boolean hasReceiverMember(Object node, Frame frame) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.hasReceiverMember(node, frame);
            }
            assert validReceiver(node);
            boolean result = delegate.hasReceiverMember(node, frame);
            if (result) {
                try {
                    assert assertString(node, delegate.getReceiverMember(node, frame));
                } catch (InteropException | ClassCastException e) {
                    assert false : violationInvariant(node);
                } catch (Exception e) {
                }
            } else {
                try {
                    delegate.getReceiverMember(node, frame);
                    assert false : violationInvariant(node);
                } catch (UnsupportedMessageException e) {
                }
            }
            return result;
        }

        @Override
        public Object getReceiverMember(Object node, Frame frame) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getReceiverMember(node, frame);
            }
            boolean hadReceiver = delegate.hasReceiverMember(node, frame);
            try {
                Object name = delegate.getReceiverMember(node, frame);
                assert hadReceiver : violationInvariant(node);
                assert assertString(node, name);
                return name;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(node);
                assert !hadReceiver : violationInvariant(node);
                throw e;
            }
        }

        @Override
        public boolean hasRootInstance(Object node, Frame frame) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.hasRootInstance(node, frame);
            }
            assert validReceiver(node);
            boolean result = delegate.hasRootInstance(node, frame);
            if (result) {
                try {
                    Object instance = delegate.getRootInstance(node, frame);
                    assertValidRootInstance(instance);
                } catch (InteropException e) {
                    assert false : violationInvariant(node);
                } catch (Exception e) {
                }
            } else {
                try {
                    delegate.getRootInstance(node, frame);
                    assert false : violationInvariant(node);
                } catch (UnsupportedMessageException e) {
                }
            }
            return result;
        }

        @Override
        public Object getRootInstance(Object node, Frame frame) throws UnsupportedMessageException {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getRootInstance(node, frame);
            }
            assert validReceiver(node);
            boolean hadRootinstance = delegate.hasRootInstance(node, frame);
            try {
                Object instance = delegate.getRootInstance(node, frame);
                assert hadRootinstance : violationInvariant(node);
                assertValidRootInstance(instance);
                return instance;
            } catch (InteropException e) {
                assert e instanceof UnsupportedMessageException : violationInvariant(node);
                assert !hadRootinstance : violationInvariant(node);
                throw e;
            }
        }

        private static void assertValidRootInstance(Object instance) {
            assert InteropLibrary.isValidValue(instance) : violationInvariant(instance);
            assert InteropLibrary.getUncached().isExecutable(instance) : String.format("The root instance '%s' is not executable.", instance);
        }

        @Override
        public Object getView(Object node, Frame frame, Object value) {
            if (CompilerDirectives.inCompiledCode()) {
                return delegate.getView(node, frame, value);
            }
            assert validReceiver(node);
            assert InteropLibrary.isValidValue(value) : violationInvariant(value);
            Class<?> languageClass = validateLocationAndFrame((Node) node, frame, value);
            Object view = delegate.getView(node, frame, value);
            assert InteropLibrary.isValidValue(view) : violationInvariant(view);
            InteropLibrary lib = InteropLibrary.getUncached(view);
            try {
                assert lib.hasLanguage(view) &&
                                lib.getLanguage(view) == languageClass : String.format("The returned scoped view of language '%s' must return the class '%s' for InteropLibrary.getLanguage." +
                                                "Fix the implementation of %s.getView to resolve this.", languageClass.getTypeName(), languageClass.getTypeName(), node.getClass().getTypeName());
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            return view;
        }

        private static Class<?> validateLocationAndFrame(Node location, Frame frame, Object value) {
            InteropLibrary interop = InteropLibrary.getUncached(value);
            assert interop.hasLanguage(value) : String.format("The value '%s' is not associated with any language.", value);
            Class<? extends TruffleLanguage<?>> valueLanguage;
            try {
                valueLanguage = interop.getLanguage(value);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            RootNode rootNode = location.getRootNode();
            assert rootNode != null : String.format("The location '%s' does not have a RootNode.", location);
            LanguageInfo nodeLanguageInfo = rootNode.getLanguageInfo();
            assert nodeLanguageInfo != null : String.format("The location '%s' does not have a language associated.", location);
            Class<?> nodeLanguage = InteropAccessor.ACCESSOR.languageSupport().getSPI(InteropAccessor.ACCESSOR.engineSupport().getEnvForInstrument(nodeLanguageInfo)).getClass();
            assert nodeLanguage == valueLanguage : String.format("The value language '%s' must match the language of the location %s.", valueLanguage, nodeLanguage);
            assert frame != null : "Frame argument must not be null.";
            assert rootNode.getFrameDescriptor().equals(frame.getFrameDescriptor()) : String.format("The frame provided does not originate from the location. " +
                            "Expected frame descriptor '%s' but was '%s'.", rootNode.getFrameDescriptor(), frame.getFrameDescriptor());
            return nodeLanguage;
        }

        private boolean validReceiver(Object nodeObject) {
            if (nodeObject == null) {
                throw new NullPointerException("A non-null receiver is required.");
            }
            assert nodeObject instanceof Node : String.format("The node '%s' does not extend Node.", nodeObject);
            Node node = (Node) nodeObject;
            assert node.getRootNode() != null : String.format("The node '%s' does not have a RootNode.", node);
            assert InteropAccessor.ACCESSOR.instrumentSupport().isInstrumentable(node) || isDefaultDelegate : String.format(
                            "The node '%s' is not instrumentable. Implement InstrumentableNode and return true from isInstrumentable()", node);
            return true;
        }

    }

}
