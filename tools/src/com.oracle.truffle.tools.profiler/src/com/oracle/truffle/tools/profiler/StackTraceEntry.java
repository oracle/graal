/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashSet;
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

    private static final Set<Class<?>> DEFAULT_TAGS;
    static {
        Set<Class<?>> tags = new HashSet<>();
        tags.add(RootTag.class);
        DEFAULT_TAGS = Collections.unmodifiableSet(tags);
    }

    private final SourceSection sourceSection;
    private final String rootName;
    private final Set<Class<?>> tags;
    private final Node instrumentedNode;
    private final int compilationTier;
    private final boolean compilationRoot;
    private volatile StackTraceElement stackTraceElement;
    private final boolean isSynthetic;

    StackTraceEntry(String rootName) {
        this.sourceSection = null;
        this.rootName = rootName;
        this.tags = DEFAULT_TAGS;
        this.instrumentedNode = null;
        this.stackTraceElement = null;
        compilationTier = 0;
        compilationRoot = true;
        isSynthetic = true;
    }

    StackTraceEntry(Instrumenter instrumenter, EventContext context, int compilationTier, boolean compilationRoot) {
        this.tags = instrumenter.queryTags(context.getInstrumentedNode());
        this.sourceSection = context.getInstrumentedSourceSection();
        this.instrumentedNode = context.getInstrumentedNode();
        this.rootName = extractRootName(instrumentedNode);
        this.compilationTier = compilationTier;
        this.compilationRoot = compilationRoot;
        this.isSynthetic = false;
    }

    StackTraceEntry(Set<Class<?>> tags, SourceSection sourceSection, RootNode root, Node node, int compilationTier, boolean compilationRoot) {
        this.tags = tags;
        this.sourceSection = sourceSection;
        this.instrumentedNode = node;
        this.rootName = extractRootName(root);
        this.compilationTier = compilationTier;
        this.compilationRoot = compilationRoot;
        this.isSynthetic = false;
    }

    StackTraceEntry(StackTraceEntry location, int compilationTier, boolean compilationRoot) {
        this.sourceSection = location.sourceSection;
        this.instrumentedNode = location.instrumentedNode;
        this.rootName = location.rootName;
        this.tags = location.tags;
        this.stackTraceElement = location.stackTraceElement;
        this.compilationTier = compilationTier;
        this.compilationRoot = compilationRoot;
        this.isSynthetic = false;
    }

    StackTraceEntry(Instrumenter instrumenter, Node node, int compilationTier, boolean compilationRoot) {
        this.tags = instrumenter.queryTags(node);
        this.sourceSection = node.getSourceSection();
        this.instrumentedNode = node;
        this.rootName = extractRootName(instrumentedNode);
        this.compilationTier = compilationTier;
        this.compilationRoot = compilationRoot;
        this.isSynthetic = false;
    }

    /**
     * @return with which tier was this entry compiled. Note: Tier 0 represents the interpreter.
     * @since 21.3.0
     */
    public int getTier() {
        return compilationTier;
    }

    /**
     * @return <code>true</code> if the entry was a compilation root, <code>false</code> if it was
     *         inlined. Interpreted enries are implicitly considered compilation roots.
     * @since 21.3.0
     */
    public boolean isCompilationRoot() {
        return compilationRoot;
    }

    /**
     * Returns <code>true</code> if this stack entry was executed in compiled mode at the time when
     * the stack trace was captured, else <code>false</code>.
     *
     * @deprecated Use {@link #getTier()}
     * @since 19.0
     */
    @Deprecated
    public boolean isCompiled() {
        return compilationTier > 0;
    }

    /**
     * Returns <code>true</code> if this stack entry was executed in interpreted mode at the time
     * when the stack trace was captured, else <code>false</code>.
     *
     * @deprecated Use {@link #getTier()}
     * @since 19.0
     */
    @Deprecated
    public boolean isInterpreted() {
        return compilationTier == 0;
    }

    /**
     * Returns <code>true</code> if this stack entry was executed in compiled mode and was inlined
     * in a parent stack entry at the time when the stack trace was captured, else
     * <code>false</code>.
     *
     * @deprecated Use {@link #getTier()}
     * @since 19.0
     */
    @Deprecated
    public boolean isInlined() {
        return !compilationRoot;
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
        String declaringClass = "";
        if (getInstrumentedNode() != null) {
            LanguageInfo languageInfo = getInstrumentedNode().getRootNode().getLanguageInfo();
            if (languageInfo != null) {
                declaringClass = languageInfo.getId();
            }
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
        if (source.getPath() == null) {
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
                return rootNode.getQualifiedName();
            }
        } else {
            return "<Unknown>";
        }
    }

    Node getInstrumentedNode() {
        return instrumentedNode;
    }

    boolean isSynthetic() {
        return isSynthetic;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public int hashCode() {
        return 31 * (31 + rootName.hashCode()) + (sourceSection != null ? sourceSection.hashCode() : 0);
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
        return "StackLocation [rootName=" + rootName + ", tags=" + tags + ", sourceSection=" + sourceSection + ", tier=" + compilationTier + ", root=" + compilationRoot + "]";
    }
}
