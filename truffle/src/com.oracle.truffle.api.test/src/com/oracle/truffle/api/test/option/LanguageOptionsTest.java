/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.option;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LanguageOptionsTest {

    @Test
    public void testReadOptionsFromSystemProperties() {
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, ReadOptionsFromSystemPropertiesLanguage.class, "", "unset");
        }
        String optionName = "polyglot." + TestUtils.getDefaultLanguageId(ReadOptionsFromSystemPropertiesLanguage.class) + ".Option";
        System.setProperty(optionName, "test");
        try {
            try (Context context = Context.newBuilder().allowExperimentalOptions(true).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(context, ReadOptionsFromSystemPropertiesLanguage.class, "", "test");
            }
            try (Engine engine = Engine.newBuilder().allowExperimentalOptions(true).useSystemProperties(false).option("engine.WarnInterpreterOnly", "false").build();
                            Context context = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(context, ReadOptionsFromSystemPropertiesLanguage.class, "", "unset");
            }
        } finally {
            System.getProperties().remove(optionName);
        }
    }

    @TruffleLanguage.Registration
    public static final class ReadOptionsFromSystemPropertiesLanguage extends AbstractExecutableTestLanguage {

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Test option") static final OptionKey<String> Option = new OptionKey<>("unset");

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String expectedOptionValue = (String) contextArguments[0];
            assertEquals(expectedOptionValue, env.getOptions().get(Option));
            return null;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ReadOptionsFromSystemPropertiesLanguageOptionDescriptors();
        }
    }
}
