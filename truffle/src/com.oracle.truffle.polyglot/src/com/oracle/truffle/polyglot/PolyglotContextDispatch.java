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
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextDispatch;

final class PolyglotContextDispatch extends AbstractContextDispatch {

    protected PolyglotContextDispatch(PolyglotImpl impl) {
        super(impl);
    }

    @Override
    public boolean initializeLanguage(Object receiver, String languageId) {
        return ((PolyglotContextImpl) receiver).initializeLanguage(languageId);
    }

    @Override
    public Value eval(Object receiver, String language, Source source) {
        return ((PolyglotContextImpl) receiver).eval(language, source);
    }

    @Override
    public Value parse(Object receiver, String language, Source source) {
        return ((PolyglotContextImpl) receiver).parse(language, source);
    }

    @Override
    public void close(Object receiver, boolean cancelIfExecuting) {
        ((PolyglotContextImpl) receiver).close(cancelIfExecuting);
    }

    @Override
    public boolean interrupt(Object receiver, Duration timeout) {
        return ((PolyglotContextImpl) receiver).interrupt(timeout);
    }

    @Override
    public Value asValue(Object receiver, Object hostValue) {
        return ((PolyglotContextImpl) receiver).asValue(hostValue);
    }

    @Override
    public void explicitEnter(Object receiver) {
        ((PolyglotContextImpl) receiver).explicitEnter();
    }

    @Override
    public void explicitLeave(Object receiver) {
        ((PolyglotContextImpl) receiver).explicitLeave();
    }

    @Override
    public Value getBindings(Object receiver, String language) {
        return ((PolyglotContextImpl) receiver).getBindings(language);
    }

    @Override
    public Value getPolyglotBindings(Object receiver) {
        return ((PolyglotContextImpl) receiver).getPolyglotBindings();
    }

    @Override
    public void resetLimits(Object receiver) {
        ((PolyglotContextImpl) receiver).resetLimits();
    }

    @Override
    public void safepoint(Object receiver) {
        ((PolyglotContextImpl) receiver).safepoint();
    }

    @Override
    public void setAPI(Object receiver, Context context) {
        ((PolyglotContextImpl) receiver).api = context;
    }

}
