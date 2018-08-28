/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
 * @since 1.0
 */
public final class DebugStackTraceElement {

    private final Debugger debugger;
    final TruffleStackTraceElement traceElement;
    private StackTraceElement stackTrace;

    DebugStackTraceElement(Debugger debugger, TruffleStackTraceElement traceElement) {
        this.debugger = debugger;
        this.traceElement = traceElement;
    }

    /**
     * Returns whether this trace element is a language implementation artifact.
     * <p>
     * The decision to mark a method as <em>internal</em> is language-specific, reflects judgments
     * about tool usability, and is subject to change.
     *
     * @since 1.0
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
     * @since 1.0
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
            throw new DebugException(debugger, ex, root.getLanguageInfo(), null, true, null);
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
     * @since 1.0
     */
    public SourceSection getSourceSection() {
        Node node = traceElement.getLocation();
        if (node != null) {
            return node.getEncapsulatingSourceSection();
        }
        return null;
    }

    /**
     * Get the current inner-most scope. The scope might not provide valid information if the
     * execution path diverges from this trace element.
     *
     * @return the scope, or <code>null</code> when no language is associated with this frame
     *         location, or when no local scope exists.
     * @since 1.0
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
        Iterable<Scope> scopes = debugger.getEnv().findLocalScopes(node, frame);
        Iterator<Scope> it = scopes.iterator();
        if (!it.hasNext()) {
            return null;
        }
        return new DebugScope(it.next(), it, debugger, null, frame, root);
    }

    DebugValue wrapHeapValue(Object result) {
        LanguageInfo language = getLanguage();
        return new HeapValue(debugger, language, null, result);
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
