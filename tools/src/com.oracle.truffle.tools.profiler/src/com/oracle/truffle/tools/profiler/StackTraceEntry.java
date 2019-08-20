/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler;

import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * An entry in a stack trace, as returned by {@link CPUSampler#takeSample()}. Each entry represents
 * a single element on the stack that is currently being executed. Stack trace entries may represent
 * a root, expression or statement execution. The frame at the top of the stack represents the
 * execution point at which the stack trace was generated.
 *
 * @see CPUSampler#takeSample()
 * @since 19.0
 */
public final class StackTraceEntry {

    /*
     * Unknown is used when it is used as part of a payload.
     */
    static final byte STATE_UNKNOWN = 0;
    static final byte STATE_INTERPRETED = 1;
    static final byte STATE_COMPILED = 2;
    static final byte STATE_COMPILATION_ROOT = 3;

    private final SourceSection sourceSection;
    private final String rootName;
    private final Set<Class<?>> tags;
    private final Node instrumentedNode;
    private final byte state;
    private volatile StackTraceElement stackTraceElement;

    StackTraceEntry(Instrumenter instrumenter, EventContext context, byte state) {
        this.tags = instrumenter.queryTags(context.getInstrumentedNode());
        this.sourceSection = context.getInstrumentedSourceSection();
        this.instrumentedNode = context.getInstrumentedNode();
        this.rootName = extractRootName(instrumentedNode);
        this.state = state;
    }

    StackTraceEntry(StackTraceEntry location, byte state) {
        this.sourceSection = location.sourceSection;
        this.instrumentedNode = location.instrumentedNode;
        this.rootName = location.rootName;
        this.tags = location.tags;
        this.stackTraceElement = location.stackTraceElement;
        this.state = state;
    }

    StackTraceEntry(Instrumenter instrumenter, Node node, byte state) {
        this.tags = instrumenter.queryTags(node);
        this.sourceSection = node.getSourceSection();
        this.instrumentedNode = node;
        this.rootName = extractRootName(instrumentedNode);
        this.state = state;
    }

    /**
     * Returns <code>true</code> if this stack entry was executed in compiled mode at the time when
     * the stack trace was captured, else <code>false</code>.
     *
     * @since 19.0
     */
    public boolean isCompiled() {
        return state == STATE_COMPILED || state == STATE_COMPILATION_ROOT;
    }

    /**
     * Returns <code>true</code> if this stack entry was executed in interpreted mode at the time
     * when the stack trace was captured, else <code>false</code>.
     *
     * @since 19.0
     */
    public boolean isInterpreted() {
        return state == STATE_INTERPRETED;
    }

    /**
     * Returns <code>true</code> if this stack entry was executed in compiled mode and was inlined
     * in a parent stack entry at the time when the stack trace was captured, else
     * <code>false</code>.
     *
     * @since 19.0
     */
    public boolean isInlined() {
        return state == STATE_COMPILED;
    }

    /**
     * Returns the source section of the stack trace entry.
     *
     * @since 19.0
     */
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    /**
     * Returns the name of the root node. For elements that don't represent a guest language root
     * like statements and expressions this returns the name of the enclosing root.
     *
     * @see RootNode#getName()
     * @since 19.0
     */
    public String getRootName() {
        return rootName;
    }

    /**
     * Returns a set tags a stack location marked with. Common tags are {@link RootTag root},
     * {@link StatementTag statement} and {@link ExpressionTag expression}. Whether statement or
     * expression stack trace entries appear depends on the configured
     * {@link CPUSampler#setFilter(com.oracle.truffle.api.instrumentation.SourceSectionFilter)
     * filter}. Never <code>null</code>.
     *
     * @see Instrumenter#queryTags(Node)
     * @since 19.0
     */
    public Set<Class<?>> getTags() {
        return tags;
    }

    /**
     * Converts the stack trace entry to a Java stack trace element. No guarantees are provided
     * about the format of the stack trace element. The format of the stack trace element may change
     * without notice.
     *
     * @since 19.0
     */
    public StackTraceElement toStackTraceElement() {
        /*
         * This should be in sync with the behavior of PolyglotException.StackTrace#toHost().
         */
        StackTraceElement stack = this.stackTraceElement;
        if (stack != null) {
            return stack;
        }
        LanguageInfo languageInfo = getInstrumentedNode().getRootNode().getLanguageInfo();
        String declaringClass;
        if (languageInfo != null) {
            declaringClass = languageInfo.getId();
        } else {
            declaringClass = "";
        }
        SourceSection sourceLocation = getSourceSection();
        String methodName = rootName == null ? "" : rootName;
        if (!tags.contains(StandardTags.RootTag.class)) {
            // non-root nodes needs to specify where in the method the element is
            methodName += "~" + formatIndices(sourceSection, true);
        }
        // root nodes don't need formatted indices in the file name
        String fileName = formatFileName();
        int startLine = sourceLocation != null ? sourceLocation.getStartLine() : -1;
        return this.stackTraceElement = new StackTraceElement(declaringClass, methodName, fileName, startLine);
    }

    // custom version of SourceSection#getShortDescription
    private String formatFileName() {
        if (sourceSection == null) {
            return "<Unknown>";
        }
        Source source = sourceSection.getSource();
        if (source == null) {
            // TODO the source == null branch can be removed if the deprecated
            // SourceSection#createUnavailable has be removed.
            return "<Unknown>";
        } else if (source.getPath() == null) {
            return source.getName();
        } else {
            return source.getPath();
        }
    }

    private static String formatIndices(SourceSection sourceSection, boolean needsColumnSpecifier) {
        StringBuilder b = new StringBuilder();
        boolean singleLine = sourceSection.getStartLine() == sourceSection.getEndLine();
        if (singleLine) {
            b.append(sourceSection.getStartLine());
        } else {
            b.append(sourceSection.getStartLine()).append("-").append(sourceSection.getEndLine());
        }
        if (needsColumnSpecifier) {
            b.append(":");
            if (sourceSection.getCharLength() <= 1) {
                b.append(sourceSection.getCharIndex());
            } else {
                b.append(sourceSection.getCharIndex()).append("-").append(sourceSection.getCharIndex() + sourceSection.getCharLength() - 1);
            }
        }
        return b.toString();
    }

    private static String extractRootName(Node instrumentedNode) {
        RootNode rootNode = instrumentedNode.getRootNode();
        if (rootNode != null) {
            if (rootNode.getName() == null) {
                return rootNode.toString();
            } else {
                return rootNode.getName();
            }
        } else {
            return "<Unknown>";
        }
    }

    Node getInstrumentedNode() {
        return instrumentedNode;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public int hashCode() {
        return 31 * (31 + rootName.hashCode()) + sourceSection.hashCode();
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StackTraceEntry)) {
            return false;
        }
        StackTraceEntry other = (StackTraceEntry) obj;
        return Objects.equals(sourceSection, other.sourceSection) && Objects.equals(rootName, other.rootName);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String toString() {
        String s = "";
        switch (state) {
            case STATE_UNKNOWN:
                s = "";
                break;
            case STATE_COMPILATION_ROOT:
                s = ", Interpreted";
                break;
            case STATE_COMPILED:
                s = ", Compiled";
                break;
            case STATE_INTERPRETED:
                s = ", Interpreted";
                break;
        }
        return "StackLocation [rootName=" + rootName + ", tags=" + tags + ", sourceSection=" + sourceSection + s + "]";
    }

}
