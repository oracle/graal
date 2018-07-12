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
package org.graalvm.polyglot.management;

import java.util.List;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import static org.graalvm.polyglot.management.ExecutionListener.IMPL;

/**
 * An execution event object passed to an execution listener consumer. Execution event instances
 * remain valid until the engine is closed. Values and returned exceptions will only remain valid
 * until the context was closed.
 *
 * @see ExecutionListener For further details.
 * @since 1.0
 */
public final class ExecutionEvent {

    private final Object impl;

    ExecutionEvent(Object impl) {
        this.impl = impl;
    }

    /**
     * Returns the source location of the event that was triggered or <code>null</code> if no
     * location source location is available.
     *
     * @since 1.0
     */
    public SourceSection getLocation() {
        return IMPL.getLocation(impl);
    }

    /**
     * Returns the root name or <code>null</code> if no name is available. The root name may also be
     * available for events caused by expressions and statements. In this case the name of the
     * containing root will be returned.
     *
     * @since 1.0
     */
    public String getRootName() {
        return IMPL.getRootName(impl);
    }

    /**
     * Returns the input values provided to execute this source location. This method returns
     * <code>null</code> if input value collection is not
     * {@link ExecutionListener.Builder#collectInputValues(boolean) enabled}. Input values are
     * available in {@link ExecutionListener.Builder#onReturn(java.util.function.Consumer) OnReturn}
     * events.The returned list may contain <code>null</code> values if input values were not
     * evaluated or if an exception occurred executing input values. The returned list is
     * unmodifiable. The returned input values may escape the event consumer and remain valid until
     * the context is closed.
     *
     * @since 1.0
     */
    public List<Value> getInputValues() {
        return IMPL.getInputValues(impl);
    }

    /**
     * Returns the return value of this source location after it was executed. This method returns
     * <code>null</code> if return value collection is not
     * {@link ExecutionListener.Builder#collectReturnValue(boolean) enabled}. Return values are
     * available in {@link ExecutionListener.Builder#onReturn(java.util.function.Consumer) OnReturn}
     * events. The returned value is allowed to escape the event consumer and remain valid until the
     * context is closed.
     *
     * @since 1.0
     */
    public Value getReturnValue() {
        return IMPL.getReturnValue(impl);
    }

    /**
     * Returns the exception of this source location after it was executed. This method returns
     * <code>null</code> if exception collection is not
     * {@link ExecutionListener.Builder#collectExceptions(boolean) enabled}. Exceptions are only
     * available in {@link ExecutionListener.Builder#onReturn(java.util.function.Consumer) OnReturn}
     * events if an exception was thrown when the location was executed. The returned value is
     * allowed to escape the event consumer and remains valid until the context is closed.
     *
     * @since 1.0
     */
    public PolyglotException getException() {
        return IMPL.getException(impl);
    }

    /**
     * Returns <code>true</code> if the source location is marked as expression, else
     * <code>false</code>. The collection of expression events may be enabled by calling
     * {@link ExecutionListener.Builder#expressions(boolean)}.
     *
     * @since 1.0
     */
    public boolean isExpression() {
        return IMPL.isExpression(impl);
    }

    /**
     * Returns <code>true</code> if the source location is marked as a statement, else
     * <code>false</code>. The collection of statement events may be enabled by calling
     * {@link ExecutionListener.Builder#statements(boolean)}.
     *
     * @since 1.0
     */
    public boolean isStatement() {
        return IMPL.isStatement(impl);
    }

    /**
     * Returns <code>true</code> if the source location is marked as a root of a function, method or
     * closure, else <code>false</code>. The collection of root events may be enabled by calling
     * {@link ExecutionListener.Builder#roots(boolean)}.
     *
     * @since 1.0
     */
    public boolean isRoot() {
        return IMPL.isRoot(impl);
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("ExecutionEvent[");
        if (isRoot()) {
            b.append("root").append(", ");
        }
        if (isStatement()) {
            b.append("statement").append(", ");
        }
        if (isExpression()) {
            b.append("expression").append(", ");
        }
        String rootName = getRootName();
        if (rootName != null) {
            b.append("rootName=").append(rootName).append(", ");
        }
        List<Value> inputValues = getInputValues();
        if (inputValues != null) {
            b.append("inputValues=").append(inputValues).append(", ");
        }
        Value returnValue = getReturnValue();
        if (returnValue != null) {
            b.append("returnValue=").append(returnValue).append(", ");
        }
        PolyglotException exception = getException();
        if (exception != null) {
            b.append("exception=").append(exception).append(", ");
        }
        b.append("location=").append(getLocation());
        b.append("]");
        return b.toString();
    }
}
