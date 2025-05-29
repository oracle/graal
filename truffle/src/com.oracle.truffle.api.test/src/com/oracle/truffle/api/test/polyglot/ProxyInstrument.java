/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.util.function.Consumer;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument.Initialize;

/**
 * Reusable language for testing that allows wrap all methods.
 */
@TruffleInstrument.Registration(id = ProxyInstrument.ID, name = ProxyInstrument.ID, version = "1.0", services = Initialize.class)
public class ProxyInstrument extends TruffleInstrument {

    public static final String ID = "proxyInstrument";

    public interface Initialize {

        Env getEnv();
    }

    private static volatile ProxyInstrument delegate = new ProxyInstrument();
    static {
        delegate.wrapper = false;
    }
    private boolean wrapper = true;
    protected ProxyInstrument instrument;
    private Consumer<Env> onCreate;

    public static <T extends ProxyInstrument> T setDelegate(T delegate) {
        ProxyInstrument prevInstrument = ProxyInstrument.delegate != null ? ProxyInstrument.delegate.instrument : null;
        ((ProxyInstrument) delegate).wrapper = false;
        ProxyInstrument.delegate = delegate;
        delegate.instrument = prevInstrument;
        return delegate;
    }

    public void setOnCreate(Consumer<Env> onCreate) {
        this.onCreate = onCreate;
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        if (wrapper) {
            delegate.instrument = this;
            return delegate.getOptionDescriptors();
        }
        return super.getOptionDescriptors();
    }

    @Override
    protected OptionDescriptors getContextOptionDescriptors() {
        if (wrapper) {
            delegate.instrument = this;
            return delegate.getContextOptionDescriptors();
        }
        return super.getContextOptionDescriptors();
    }

    @Override
    protected void onCreate(Env env) {
        env.registerService(new Initialize() {
            public Env getEnv() {
                return env;
            }
        });
        if (wrapper) {
            delegate.instrument = this;
            delegate.onCreate(env);
        }
        if (onCreate != null) {
            onCreate.accept(env);
        }
    }

    @Override
    protected void onDispose(Env env) {
        if (wrapper) {
            delegate.instrument = this;
            delegate.onDispose(env);
        }
    }

    @Override
    protected void onFinalize(Env env) {
        if (wrapper) {
            delegate.instrument = this;
            delegate.onFinalize(env);
        }
    }

    public static ProxyInstrument getCurrent() {
        return delegate;
    }

    public static TruffleInstrument.Env findEnv(Context c) {
        return findEnv(c.getEngine());
    }

    public static TruffleInstrument.Env findEnv(Engine e) {
        return e.getInstruments().get(ProxyInstrument.ID).lookup(ProxyInstrument.Initialize.class).getEnv();
    }

}
