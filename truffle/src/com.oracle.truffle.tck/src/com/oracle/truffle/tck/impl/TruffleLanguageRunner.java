/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.impl;

import org.graalvm.polyglot.Engine;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;

public abstract class TruffleLanguageRunner extends BlockJUnit4ClassRunner {

    private final String languageId;

    private TruffleLanguageRunner(Class<?> klass, String languageId) throws InitializationError {
        super(klass);
        this.languageId = languageId;
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        if (skipOnMissingLanguage(languageId)) {
            notifier.fireTestIgnored(Description.createTestDescription(method.getType(), method.getName()));
        } else {
            super.runChild(method, notifier);
        }
    }

    private static boolean skipOnMissingLanguage(String languageId) {
        try (Engine engine = Engine.create()) {
            return !engine.getLanguages().containsKey(languageId);
        }
    }

    public static final class JavaScriptRunner extends TruffleLanguageRunner {

        public JavaScriptRunner(Class<?> klass) throws InitializationError {
            super(klass, "js");
        }
    }

    public static final class RubyRunner extends TruffleLanguageRunner {

        public RubyRunner(Class<?> klass) throws InitializationError {
            super(klass, "ruby");
        }
    }

    public static final class RRunner extends TruffleLanguageRunner {

        public RRunner(Class<?> klass) throws InitializationError {
            super(klass, "R");
        }
    }
}
