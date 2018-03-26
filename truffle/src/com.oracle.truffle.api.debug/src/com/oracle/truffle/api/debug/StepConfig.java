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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.EventContext;

/**
 * Represents a debugger step configuration. A debugger step is defined by it's depth and a set of
 * additional properties. The step depth is specified by a usage of one of the appropriate prepare
 * methods on {@link SuspendedEvent}. The set of additional step properties is defined by this
 * class. These are a step count and a list of {@link SourceElement source elements} the step can
 * suspend at.
 * <p>
 * The rules of stepping through the {@link SourceElement source elements} are following:
 * <ul>
 * <li>{@link SourceElement#STATEMENT}: steps suspend {@link SuspendAnchor#BEFORE before} guest
 * language statements, unless the step returns to a calling location, in which case they suspend
 * {@link SuspendAnchor#AFTER after}. Step depth is interpreted based on pushed stack frames.</li>
 * <li>{@link SourceElement#EXPRESSION}: steps suspend {@link SuspendAnchor#BEFORE before} and
 * {@link SuspendAnchor#AFTER after} guest language expressions. Step depth is interpreted based on
 * both pushed stack frames and expression nesting hierarchy.</li>
 * </ul>
 *
 * @since 0.33
 */
public final class StepConfig {

    private static final StepConfig EMPTY = new StepConfig(null, 0);

    private final Set<SourceElement> sourceElements;
    private final int stepCount;

    StepConfig(Set<SourceElement> sourceElements, int count) {
        this.sourceElements = sourceElements;
        this.stepCount = count;
    }

    /**
     * Create a new step configuration builder.
     *
     * @since 0.33
     */
    public static Builder newBuilder() {
        return EMPTY.new Builder();
    }

    /**
     * Get a set of {@link SourceElement}s that are enabled for the step. It must be a subset of
     * {@link SourceElement}s enabled in {@link DebuggerSession} which the step is prepared for. It
     * can return <code>null</code>, in which case all source elements enabled in the debugger
     * session are also enabled for the step.
     *
     * @return a set of source elements, or <code>null</code>
     */
    Set<SourceElement> getSourceElements() {
        return sourceElements;
    }

    private static boolean preferredAnchorMatches(SourceElement element, SuspendAnchor anchor) {
        switch (element) {
            case STATEMENT:
                return SuspendAnchor.BEFORE == anchor;
            case EXPRESSION:
                return true;
            default:
                throw new IllegalStateException(element.name());
        }
    }

    boolean matches(DebuggerSession session, EventContext context, SuspendAnchor anchor) {
        Set<SourceElement> elements = sourceElements;
        if (elements == null) {
            elements = session.getSourceElements();
        }
        for (SourceElement se : elements) {
            if (context.hasTag(se.getTag()) && preferredAnchorMatches(se, anchor)) {
                return true;
            }
        }
        return false;
    }

    boolean containsSourceElement(DebuggerSession session, SourceElement sourceElement) {
        Set<SourceElement> elements = sourceElements;
        if (elements == null) {
            elements = session.getSourceElements();
        }
        return elements.contains(sourceElement);
    }

    /**
     * Get the step count. It specifies the number of times the step repeats itself before it
     * suspends the execution.
     */
    int getCount() {
        return stepCount;
    }

    /**
     * Builder of {@link StepConfig}.
     *
     * @see StepConfig#newBuilder()
     * @since 0.33
     */
    public final class Builder {

        private Set<SourceElement> stepElements;
        private int stepCount = -1;

        private Builder() {
        }

        /**
         * Provide a list of {@link SourceElement}s that are enabled for the step. It must be a
         * subset of {@link SourceElement}s enabled in {@link DebuggerSession} which the step is
         * prepared for. At least one source element needs to be provided and can only be invoked
         * once per builder. When not called, by default all source elements enabled in the debugger
         * session are also enabled for the step.
         *
         * @param elements a non-empty list of source elements
         * @since 0.33
         */
        public Builder sourceElements(SourceElement... elements) {
            if (this.stepElements != null) {
                throw new IllegalStateException("Step source elements can only be set once per the builder.");
            }
            if (elements.length == 0) {
                throw new IllegalArgumentException("At least one source element needs to be provided.");
            }
            if (elements.length == 1) {
                stepElements = Collections.singleton(elements[0]);
            } else {
                stepElements = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(elements)));
            }
            return this;
        }

        /**
         * Provide the step count. It specifies the number of times the step repeats itself before
         * it suspends the execution. Can only be invoked once per builder.
         *
         * @throws IllegalArgumentException if {@code count <= 0}
         * @since 0.33
         */
        public Builder count(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("Step count must be > 0");
            }
            if (this.stepCount > 0) {
                throw new IllegalStateException("Step count can only be set once per the builder.");
            }
            this.stepCount = count;
            return this;
        }

        /**
         * Create a {@link StepConfig step configuration} from this builder.
         *
         * @since 0.33
         */
        public StepConfig build() {
            if (stepCount < 0) {
                stepCount = 1;
            }
            return new StepConfig(stepElements, stepCount);
        }
    }
}
