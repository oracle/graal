/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.debug.DebugValue.HeapValue;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
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
    private StackTraceElement stackTrace;

    DebugStackTraceElement(DebuggerSession session, TruffleStackTraceElement traceElement) {
        this.session = session;
        this.traceElement = traceElement;
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
        RootNode root = findCurrentRoot();
        if (root == null) {
            return true;
        }
        return root.isInternal();
    }

    /**
     * A description of the trace element. If the language does not provide such a description then
     * <code>null</code> is returned.
     *
     * @since 19.0
     */
    public String getName() {
        RootNode root = findCurrentRoot();
        if (root == null) {
            return null;
        }
        try {
            return root.getName();
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw new DebugException(session, ex, root.getLanguageInfo(), null, true, null);
        }
    }

    private String getName0() {
        RootNode root = findCurrentRoot();
        if (root == null) {
            return null;
        }
        try {
            return root.getName();
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            return null;
        }
    }

    /**
     * Returns the source section location of this trace element. The source section is
     * <code>null</code> if the source location is not available.
     *
     * @since 19.0
     */
    public SourceSection getSourceSection() {
        Node node = traceElement.getLocation();
        if (node != null) {
            return session.resolveSection(node);
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
        Node node = traceElement.getLocation();
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
        Iterable<Scope> scopes = session.getDebugger().getEnv().findLocalScopes(node, frame);
        Iterator<Scope> it = scopes.iterator();
        if (!it.hasNext()) {
            return null;
        }
        return new DebugScope(it.next(), it, session, null, frame, root);
    }

    DebugValue wrapHeapValue(Object result) {
        LanguageInfo language = getLanguage();
        return new HeapValue(session, language, null, result);
    }

    LanguageInfo getLanguage() {
        LanguageInfo language;
        RootNode root = findCurrentRoot();
        if (root != null) {
            language = root.getLanguageInfo();
        } else {
            language = null;
        }
        return language;
    }

    RootNode findCurrentRoot() {
        Node node = traceElement.getLocation();
        if (node != null) {
            return node.getRootNode();
        }
        RootCallTarget target = traceElement.getTarget();
        return target.getRootNode();
    }

    StackTraceElement toTraceElement() {
        if (stackTrace == null) {
            LanguageInfo language = getLanguage();
            String declaringClass = language != null ? "<" + language.getId() + ">" : "<unknown>";
            String methodName = getName0();
            if (methodName == null) {
                methodName = "";
            }
            SourceSection sourceLocation = getSourceSection();
            String fileName = sourceLocation != null ? sourceLocation.getSource().getName() : "Unknown";
            int startLine = sourceLocation != null ? sourceLocation.getStartLine() : -1;
            stackTrace = new StackTraceElement(declaringClass, methodName, fileName, startLine);
        }
        return stackTrace;
    }
}
