/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.Serializable;
import java.lang.reflect.UndeclaredThrowableException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ControlFlowException;

/** Base class holding the direct guest state of generated proxies. */
@SuppressWarnings("serial") // Matches JDK proxies' Serializable marker, not serializable guest state.
public abstract class PolyglotGeneratedProxy implements Serializable {
    private static final long serialVersionUID = 1L;

    protected final Object languageContext;
    protected final Object receiver;
    /** Keeps the creator SDK context alive while the proxy is reachable. */
    @SuppressWarnings("unused") private final Object contextAnchor;
    private final CallTarget equalsTarget;
    private final CallTarget hashCodeTarget;
    private final CallTarget toStringTarget;

    protected PolyglotGeneratedProxy(Object languageContext, Object receiver, Object contextAnchor, CallTarget equalsTarget, CallTarget hashCodeTarget, CallTarget toStringTarget) {
        this.languageContext = languageContext;
        this.receiver = receiver;
        this.contextAnchor = contextAnchor;
        this.equalsTarget = equalsTarget;
        this.hashCodeTarget = hashCodeTarget;
        this.toStringTarget = toStringTarget;
    }

    /**
     * Signals that a guest member cannot implement a Java default method. The singleton
     * records neither Java nor guest stack traces and must never be mutated or escape the
     * proxy dispatch.
     */
    @SuppressWarnings("serial")
    public static final class DefaultMethodFallbackException extends ControlFlowException {
        public static final DefaultMethodFallbackException INSTANCE = new DefaultMethodFallbackException();

        private DefaultMethodFallbackException() {
        }
    }

    /**
     * Preserves the guest failure as primary when its Java fallback also fails.
     *
     * @param original the guest failure or the shared fallback signal
     * @param methodName the unavailable guest member
     * @param fallbackFailure the failure from the Java fallback
     * @return the call-specific failure with the fallback failure suppressed
     */
    @TruffleBoundary
    protected static RuntimeException fallbackFailure(RuntimeException original, String methodName, Exception fallbackFailure) {
        RuntimeException failure = original;
        if (original instanceof DefaultMethodFallbackException) {
            /*
             * Do not mutate the singleton or query the guest receiver: the Java default
             * may already have closed its context.
             */
            failure = new UnsupportedOperationException("Unsupported guest member '" + methodName + "'.");
        }
        failure.addSuppressed(fallbackFailure);
        return failure;
    }

    @Override
    public final boolean equals(Object obj) {
        if (equalsTarget != null) {
            try {
                return (boolean) invoke(equalsTarget, languageContext, receiver, obj);
            } catch (UnsupportedOperationException original) {
                try {
                    return PolyglotWrapper.equalsProxy(languageContext, receiver, obj);
                } catch (Exception fallbackFailure) {
                    throw fallbackFailure(original, "equals", fallbackFailure);
                }
            }
        }
        return PolyglotWrapper.equalsProxy(languageContext, receiver, obj);
    }

    @Override
    public final int hashCode() {
        if (hashCodeTarget != null) {
            try {
                return (int) invoke(hashCodeTarget, languageContext, receiver);
            } catch (UnsupportedOperationException original) {
                try {
                    return PolyglotWrapper.hashCode(languageContext, receiver);
                } catch (Exception fallbackFailure) {
                    throw fallbackFailure(original, "hashCode", fallbackFailure);
                }
            }
        }
        return PolyglotWrapper.hashCode(languageContext, receiver);
    }

    @Override
    public final String toString() {
        if (toStringTarget != null) {
            try {
                return (String) invoke(toStringTarget, languageContext, receiver);
            } catch (UnsupportedOperationException original) {
                try {
                    return PolyglotWrapper.toString(languageContext, receiver);
                } catch (Exception fallbackFailure) {
                    throw fallbackFailure(original, "toString", fallbackFailure);
                }
            }
        }
        return PolyglotWrapper.toString(languageContext, receiver);
    }

    private static Object invoke(CallTarget target, Object... arguments) {
        try {
            return target.call(null, arguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new UndeclaredThrowableException(t);
        }
    }
}
