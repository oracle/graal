/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a trace element in the guest language stack trace. A guest language trace element
 * consists of a {@link #getName() name}, the current {@link #getSourceSection() source location}
 * and {@link #getScope() scopes} containing local variables and arguments.
 * <p>
 * The difference between this class and {@link DebugStackFrame} is the limited life-time of
 * {@link DebugStackFrame}, that is associated with a {@link SuspendedEvent}.
 *
 * @see DebugException#getDebugStackTrace()
 * @since 19.0
 */
public final class DebugStackTraceElement {

    private final DebuggerSession session;
    final TruffleStackTraceElement traceElement;
    private final StackTraceElement hostTraceElement;
    private StackTraceElement stackTraceElement;

    DebugStackTraceElement(DebuggerSession session, TruffleStackTraceElement traceElement) {
        this.session = session;
        this.traceElement = traceElement;
        this.hostTraceElement = null;
    }

    DebugStackTraceElement(DebuggerSession session, StackTraceElement hostTraceElement) {
        this.session = session;
        this.traceElement = null;
        this.hostTraceElement = hostTraceElement;
    }

    /**
     * Returns whether this trace element is a language implementation artifact.
     * <p>
     * The decision to mark a method as <em>internal</em> is language-specific, reflects judgments
     * about tool usability, and is subject to change.
     *
     * @since 19.0
     */
    public boolean isInternal() {
        if (isHost()) {
            return false;
        }
        RootNode root = findCurrentRoot();
        if (root == null) {
            return true;
        }
        return root.isInternal();
    }

    /**
     * Returns <code>true</code> if this element is a host element. Host elements provide
     * {@link #getHostTraceElement() stack trace element}, have no {@link #getScope() scope}, and no
     * {@link #getSourceSection() source section}.
     * <p>
     * Host elements are provided only when {@link DebuggerSession#setShowHostStackFrames(boolean)
     * host info} is set to <code>true</code>.
     *
     * @since 20.3
     * @see DebuggerSession#setShowHostStackFrames(boolean)
     */
    public boolean isHost() {
        return hostTraceElement != null;
    }

    /**
     * Provides a host element. Returns the host stack trace element if and only if this is
     * {@link #isHost() host} element.
     *
     * @return the host stack trace element, or <code>null</code> when not a host element.
     * @since 20.3
     * @see #isHost()
     */
    public StackTraceElement getHostTraceElement() {
        return hostTraceElement;
    }

    /**
     * A description of the trace element. If the language does not provide such a description then
     * <code>null</code> is returned.
     *
     * @since 19.0
     */
    public String getName() {
        if (hostTraceElement != null) {
            return hostTraceElement.getClassName() + '.' + hostTraceElement.getMethodName();
        }
        try {
            Object guestObject = traceElement.getGuestObject();
            return getExecutableName(guestObject);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            RootNode root = findCurrentRoot();
            LanguageInfo languageInfo = root != null ? root.getLanguageInfo() : null;
            throw DebugException.create(session, ex, languageInfo);
        }
    }

    /**
     * Returns the source section location of this trace element. The source section is
     * <code>null</code> if the source location is not available.
     *
     * @since 19.0
     */
    public SourceSection getSourceSection() {
        try {
            return getSourceSectionImpl();
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            RootNode root = findCurrentRoot();
            LanguageInfo languageInfo = root != null ? root.getLanguageInfo() : null;
            throw DebugException.create(session, ex, languageInfo);
        }
    }

    private SourceSection getSourceSectionImpl() {
        if (isHost()) {
            return null;
        }
        Object guestObject = traceElement.getGuestObject();
        SourceSection sc = null;
        if (InteropLibrary.getUncached().hasSourceLocation(guestObject)) {
            try {
                sc = InteropLibrary.getUncached().getSourceLocation(guestObject);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        if (sc != null) {
            return session.resolveSection(sc);
        }
        return null;
    }

    /**
     * Get the current inner-most scope. The scope might not provide valid information if the
     * execution path diverges from this trace element.
     *
     * @return the scope, or <code>null</code> when no language is associated with this frame
     *         location, or when no local scope exists.
     * @since 19.0
     */
    public DebugScope getScope() {
        if (isHost()) {
            return null;
        }
        Node node = traceElement.getInstrumentableLocation();
        if (node == null) {
            return null;
        }
        RootNode root = node.getRootNode();
        if (root.getLanguageInfo() == null) {
            // no language, no scopes
            return null;
        }
        Frame elementFrame = traceElement.getFrame();
        MaterializedFrame frame = (elementFrame != null) ? elementFrame.materialize() : null;
        if (!NodeLibrary.getUncached().hasScope(node, frame)) {
            return null;
        }
        try {
            Object scope = NodeLibrary.getUncached().getScope(node, frame, true);
            return new DebugScope(scope, session, null, node, frame, root);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private LanguageInfo getLanguage() {
        if (isHost()) {
            return null;
        }
        LanguageInfo language;
        RootNode root = findCurrentRoot();
        if (root != null) {
            language = root.getLanguageInfo();
        } else {
            language = null;
        }
        return language;
    }

    private RootNode findCurrentRoot() {
        if (isHost()) {
            return null;
        }
        Node node = traceElement.getLocation();
        if (node != null) {
            return node.getRootNode();
        }
        RootCallTarget target = traceElement.getTarget();
        return target.getRootNode();
    }

    StackTraceElement toTraceElement() {
        if (stackTraceElement == null) {
            if (hostTraceElement != null) {
                stackTraceElement = hostTraceElement;
            } else {
                LanguageInfo language = getLanguage();
                String methodName = null;
                String metaQualifiedName = null;
                SourceSection sourceLocation = null;
                try {
                    Object guestObject = traceElement.getGuestObject();
                    if (guestObject != null) {
                        methodName = getExecutableName(guestObject);
                        metaQualifiedName = getDeclaringMetaQualifiedName(guestObject);
                    }
                    sourceLocation = getSourceSectionImpl();
                } catch (ThreadDeath | AssertionError td) {
                    throw td;
                } catch (Throwable ex) {
                    if (InteropLibrary.getUncached().isException(ex)) {
                        // Should not throw additional guest exceptions
                        // while creating a DebugException.
                        methodName = "Error in generating method name: " + ex.getLocalizedMessage();
                    } else {
                        throw ex;
                    }
                }
                StringBuilder declaringClass = new StringBuilder();
                if (language != null) {
                    declaringClass.append("<").append(language.getId()).append(">");
                    if (metaQualifiedName != null) {
                        declaringClass.append(" ");
                    }
                }
                if (metaQualifiedName != null) {
                    declaringClass.append(metaQualifiedName);
                }

                if (methodName == null) {
                    methodName = "";
                }

                String fileName = sourceLocation != null ? sourceLocation.getSource().getName() : "Unknown";
                int startLine = sourceLocation != null ? sourceLocation.getStartLine() : -1;
                stackTraceElement = new StackTraceElement(declaringClass.toString(), methodName, fileName, startLine);
            }
        }
        return stackTraceElement;
    }

    private static String getExecutableName(Object guestObject) {
        if (InteropLibrary.getUncached().hasExecutableName(guestObject)) {
            try {
                return InteropLibrary.getUncached().asString(InteropLibrary.getUncached().getExecutableName(guestObject));
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        return null;
    }

    private static String getDeclaringMetaQualifiedName(Object guestObject) {
        if (InteropLibrary.getUncached().hasDeclaringMetaObject(guestObject)) {
            try {
                Object hostObject = InteropLibrary.getUncached().getDeclaringMetaObject(guestObject);
                return InteropLibrary.getUncached().asString(InteropLibrary.getUncached().getMetaQualifiedName(hostObject));
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        return null;
    }
}
