/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
