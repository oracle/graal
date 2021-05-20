/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;

final class PolyglotContextDispatch extends AbstractContextImpl<PolyglotContextImpl> {

    static final PolyglotContextDispatch INSTANCE = new PolyglotContextDispatch(PolyglotImpl.getInstance());

    protected PolyglotContextDispatch(PolyglotImpl impl) {
        super(impl);
    }

    @Override
    public boolean initializeLanguage(PolyglotContextImpl receiver, String languageId) {
        return receiver.initializeLanguage(languageId);
    }

    @Override
    public Value eval(PolyglotContextImpl receiver, String language, Object sourceImpl) {
        return receiver.eval(language, sourceImpl);
    }

    @Override
    public Value parse(PolyglotContextImpl receiver, String language, Object sourceImpl) {
        return receiver.parse(language, sourceImpl);
    }

    @Override
    public Engine getEngineImpl(PolyglotContextImpl receiver, Context sourceContext) {
        return receiver.getEngineImpl(sourceContext);
    }

    @Override
    public void close(PolyglotContextImpl receiver, Context sourceContext, boolean interuptExecution) {
        receiver.close(sourceContext, interuptExecution);
    }

    @Override
    public boolean interrupt(PolyglotContextImpl receiver, Context sourceContext, Duration timeout) {
        return receiver.interrupt(sourceContext, timeout);
    }

    @Override
    public Value asValue(PolyglotContextImpl receiver, Object hostValue) {
        return receiver.asValue(hostValue);
    }

    @Override
    public void explicitEnter(PolyglotContextImpl receiver, Context sourceContext) {
        receiver.explicitEnter(sourceContext);
    }

    @Override
    public void explicitLeave(PolyglotContextImpl receiver, Context sourceContext) {
        receiver.explicitLeave(sourceContext);
    }

    @Override
    public Value getBindings(PolyglotContextImpl receiver, String language) {
        return receiver.getBindings(language);
    }

    @Override
    public Value getPolyglotBindings(PolyglotContextImpl receiver) {
        return receiver.getPolyglotBindings();
    }

    @Override
    public void resetLimits(PolyglotContextImpl receiver) {
        receiver.resetLimits();
    }

    @Override
    public void safepoint(PolyglotContextImpl receiver) {
        receiver.safepoint();
    }
}
