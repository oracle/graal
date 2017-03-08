/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import com.oracle.truffle.api.nodes.RootNode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

public final class TruffleRunner extends BlockJUnit4ClassRunner {

    private static final TruffleTestInvoker<?> truffleTestInvoker = TruffleTestInvoker.create();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Inject {

        Class<? extends RootNode> value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Warmup {

        int value();
    }

    public static final class ParametersFactory implements ParametersRunnerFactory {

        @Override
        public Runner createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
            return new ParameterizedRunner(test);
        }
    }

    private static final class ParameterizedRunner extends BlockJUnit4ClassRunnerWithParameters {

        ParameterizedRunner(TestWithParameters test) throws InitializationError {
            super(test);
        }

        @Override
        protected Statement methodInvoker(FrameworkMethod method, Object test) {
            Statement ret = truffleTestInvoker.createStatement(getName(), getTestClass(), method, test);
            if (ret == null) {
                ret = super.methodInvoker(method, test);
            }
            return ret;
        }

        @Override
        protected void validateTestMethods(List<Throwable> errors) {
            TruffleTestInvoker.validateTestMethods(getTestClass(), errors);
        }
    }

    public TruffleRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        Statement ret = truffleTestInvoker.createStatement(testName(method), getTestClass(), method, test);
        if (ret == null) {
            ret = super.methodInvoker(method, test);
        }
        return ret;
    }

    @Override
    protected void validateTestMethods(List<Throwable> errors) {
        TruffleTestInvoker.validateTestMethods(getTestClass(), errors);
    }
}
