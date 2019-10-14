/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.management;

import java.util.List;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import static org.graalvm.polyglot.management.Management.IMPL;

/**
 * An execution event object passed to an execution listener consumer. Execution event instances
 * remain valid until the engine is closed. Values and returned exceptions will only remain valid
 * until the context was closed.
 *
 * @see ExecutionListener For further details.
 * @since 19.0
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
     * @since 19.0
     */
    public SourceSection getLocation() {
        return IMPL.getExecutionEventLocation(impl);
    }

    /**
     * Returns the root name or <code>null</code> if no name is available. The root name may also be
     * available for events caused by expressions and statements. In this case the name of the
     * containing root will be returned.
     *
     * @since 19.0
     */
    public String getRootName() {
        return IMPL.getExecutionEventRootName(impl);
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
     * @since 19.0
     */
    public List<Value> getInputValues() {
        return IMPL.getExecutionEventInputValues(impl);
    }

    /**
     * Returns the return value of this source location after it was executed. This method returns
     * <code>null</code> if return value collection is not
     * {@link ExecutionListener.Builder#collectReturnValue(boolean) enabled}. Return values are
     * available in {@link ExecutionListener.Builder#onReturn(java.util.function.Consumer) OnReturn}
     * events. The returned value is allowed to escape the event consumer and remain valid until the
     * context is closed.
     *
     * @since 19.0
     */
    public Value getReturnValue() {
        return IMPL.getExecutionEventReturnValue(impl);
    }

    /**
     * Returns the exception of this source location after it was executed. This method returns
     * <code>null</code> if exception collection is not
     * {@link ExecutionListener.Builder#collectExceptions(boolean) enabled}. Exceptions are only
     * available in {@link ExecutionListener.Builder#onReturn(java.util.function.Consumer) OnReturn}
     * events if an exception was thrown when the location was executed. The returned value is
     * allowed to escape the event consumer and remains valid until the context is closed.
     *
     * @since 19.0
     */
    public PolyglotException getException() {
        return IMPL.getExecutionEventException(impl);
    }

    /**
     * Returns <code>true</code> if the source location is marked as expression, else
     * <code>false</code>. The collection of expression events may be enabled by calling
     * {@link ExecutionListener.Builder#expressions(boolean)}.
     *
     * @since 19.0
     */
    public boolean isExpression() {
        return IMPL.isExecutionEventExpression(impl);
    }

    /**
     * Returns <code>true</code> if the source location is marked as a statement, else
     * <code>false</code>. The collection of statement events may be enabled by calling
     * {@link ExecutionListener.Builder#statements(boolean)}.
     *
     * @since 19.0
     */
    public boolean isStatement() {
        return IMPL.isExecutionEventStatement(impl);
    }

    /**
     * Returns <code>true</code> if the source location is marked as a root of a function, method or
     * closure, else <code>false</code>. The collection of root events may be enabled by calling
     * {@link ExecutionListener.Builder#roots(boolean)}.
     *
     * @since 19.0
     */
    public boolean isRoot() {
        return IMPL.isExecutionEventRoot(impl);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
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
