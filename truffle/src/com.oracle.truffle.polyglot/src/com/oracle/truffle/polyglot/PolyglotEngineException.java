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
package com.oracle.truffle.polyglot;

import org.graalvm.polyglot.Context;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;

/**
 * Represents an expected user exception caused by the polyglot engine. It is wrapped such that it
 * can be differentiated from internal errors. This class is not supposed to be visible to the
 * outside world and must always be unwrapped when shown to the user, which may be the embedder, the
 * language or the instrument.
 * <p>
 * This wrapper is necessary such that code from the polyglot engine can be used from the embedder
 * and the language/instrument side at the same time. The usefulness of this wrapper is best
 * explained using an example:
 *
 * <p>
 * Example 1:
 * <ol>
 * <li>The embedder calls {@link Context#initialize(String) initialization} with a language.
 * <li>During {@link TruffleLanguage#initializeContext context initialization} a language calls into
 * {@link Env#parsePublic(com.oracle.truffle.api.source.Source, String...)} with a language that it
 * does not have access to.
 * <li>The engine throws a {@link PolyglotEngineException#illegalArgument(String) illegal argument}
 * error
 * <li>At the language API boundary for
 * {@link Env#parsePublic(com.oracle.truffle.api.source.Source, String...)} the exception is
 * unwrapped using {@link PolyglotImpl#engineToLanguageException(Throwable)}.
 * <li>The language does not handle the exception and forwards the illegal argument exception to the
 * engine.
 * <li>At the embedder API boundary for {@link PolyglotContextImpl#initializeLanguage(String)
 * initialization} the exception is wrapped into an PolylgotException designated as internal error.
 * This is good behavior, as the embedder cannot fix this error and should report a bug.
 * </ol>
 * Example 2:
 * <ol>
 * <li>The embedder calls {@link Context#initialize(String) initialization} with a language the
 * embedder has no access to.
 * <li>The engine throws a {@link PolyglotEngineException#illegalArgument(String) illegal argument}
 * error.
 * <li>At the embedder API boundary for {@link Context#initialize(String) initialization} the engine
 * exception is unwrapped using
 * {@link PolyglotImpl#guestToHostException(PolyglotLanguageContext, Throwable)} and the embedder
 * sees an {@link IllegalArgumentException}. This is expected as the embedder must change their code
 * to fix this problem.
 * </ol>
 * Note that both examples use the same code in
 * {@link PolyglotLanguageContext#checkAccess(PolyglotLanguage)} to implement this behavior. The
 * polyglot engine exception wrapper makes this possible. If we would, for example, use special a
 * exception type (e.g. a subclass of {@link IllegalArgumentException}) instead of a wrapper for
 * Example 1 then it would see an {@link IllegalArgumentException} instead of an internal
 * PolylgotException.
 */
@SuppressWarnings("serial")
final class PolyglotEngineException extends RuntimeException {

    final RuntimeException e;
    final boolean closingContext;

    private PolyglotEngineException(RuntimeException e) {
        this(e, false);
    }

    private PolyglotEngineException(RuntimeException e, boolean closingContext) {
        super(null, e);
        this.e = e;
        this.closingContext = closingContext;
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    static void rethrow(Throwable e) {
        if (e instanceof PolyglotEngineException) {
            throw ((PolyglotEngineException) e).e;
        }
    }

    static PolyglotEngineException illegalArgument(IllegalArgumentException e) {
        return new PolyglotEngineException(e);
    }

    static PolyglotEngineException illegalArgument(String message) {
        return new PolyglotEngineException(new IllegalArgumentException(message));
    }

    static PolyglotEngineException illegalState(String message) {
        return new PolyglotEngineException(new IllegalStateException(message));
    }

    static PolyglotEngineException illegalState(String message, boolean closingContext) {
        return new PolyglotEngineException(new IllegalStateException(message), closingContext);
    }

    static PolyglotEngineException nullPointer(String message) {
        return new PolyglotEngineException(new NullPointerException(message));
    }

    static PolyglotEngineException unsupported(String message) {
        return new PolyglotEngineException(new UnsupportedOperationException(message));
    }

    static PolyglotEngineException unsupported(String message, Throwable cause) {
        return new PolyglotEngineException(new UnsupportedOperationException(message, cause));
    }

    static PolyglotEngineException classCast(String message) {
        return new PolyglotEngineException(new ClassCastException(message));
    }

    static PolyglotEngineException arrayIndexOutOfBounds(String message) {
        return new PolyglotEngineException(new ArrayIndexOutOfBoundsException(message));
    }

}
