/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.wrapper;

import java.time.Duration;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceDispatch;

import com.oracle.truffle.api.interop.TruffleObject;

public class HostContextDispatch extends AbstractContextDispatch {

    final APIAccess api;
    final HostPolyglotDispatch polyglot;
    final HostEntryPoint hostToGuest;

    protected HostContextDispatch(HostPolyglotDispatch impl) {
        super(impl);
        this.polyglot = impl;
        this.api = polyglot.getAPIAccess();
        this.hostToGuest = impl.getHostToGuest();
    }

    @Override
    public void setAPI(Object receiver, Object key) {
    }

    @Override
    public void close(Object receiver, boolean cancelIfExecuting) {

    }

    @Override
    public boolean initializeLanguage(Object receiver, String languageId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object eval(Object receiver, String language, Object source) {
        HostContext context = (HostContext) receiver;
        APIAccess apiAccess = polyglot.getAPIAccess();
        AbstractSourceDispatch sourceDispatch = apiAccess.getSourceDispatch(source);
        Object sourceImpl = apiAccess.getSourceReceiver(source);
        String languageId = sourceDispatch.getLanguage(sourceImpl);
        String characters = sourceDispatch.getCharacters(sourceImpl).toString();

        long remoteValue = hostToGuest.remoteEval(context.remoteContext, languageId, characters);
        return context.localContext.asValue(new HostGuestValue(hostToGuest, context.remoteContext, remoteValue));
    }

    @Override
    public Object asValue(Object receiver, Object hostValue) {
        HostContext context = ((HostContext) receiver);
        if (hostValue instanceof TruffleObject) {
            throw new UnsupportedOperationException("TruffleObject not supported for remote contexts.");
        }
        Value localValue = context.localContext.asValue(hostValue);
        return localValue;
    }

    @Override
    public Value getBindings(Object receiver, String language) {
        HostContext context = ((HostContext) receiver);
        long valueId = hostToGuest.remoteGetBindings(context.remoteContext, language);
        return context.localContext.asValue(new HostGuestValue(hostToGuest, context.remoteContext, valueId));
    }

    @Override
    public Object parse(Object receiver, String language, Object source) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean interrupt(Object receiver, Duration timeout) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void explicitEnter(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void explicitLeave(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Value getPolyglotBindings(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetLimits(Object receiver) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void safepoint(Object receiver) {
        throw new UnsupportedOperationException();
    }

}
