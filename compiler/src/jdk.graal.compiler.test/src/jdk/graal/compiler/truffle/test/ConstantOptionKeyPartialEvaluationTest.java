/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import jdk.graal.compiler.truffle.test.nodes.AbstractTestNode;
import jdk.graal.compiler.truffle.test.nodes.RootTestNode;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.ConstantOptionKey;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public class ConstantOptionKeyPartialEvaluationTest extends PartialEvaluationTest {

    @BeforeClass
    public static void checkSystemProperty() {
        if (ImageInfo.inImageRuntimeCode()) {
            assertTrue(
                            "Test requires -Dpolyglot.ConstantOptionKeyPartialEvaluationLanguage.ConstantOption1=true is set during native-image build", //
                            ConstantOptionKeyPartialEvaluationLanguage.ConstantOption1.getConstantValue());
            assertFalse("Test requires that -Dpolyglot.ConstantOptionKeyPartialEvaluationLanguage.ConstantOption2 is NOT set during native-image build", //
                            ConstantOptionKeyPartialEvaluationLanguage.ConstantOption2.getConstantValue());
        } else {
            assertTrue(
                            "Test requires -Dpolyglot.ConstantOptionKeyPartialEvaluationLanguage.ConstantOption1=true", //
                            Boolean.getBoolean("polyglot.ConstantOptionKeyPartialEvaluationLanguage.ConstantOption1"));
            assertNull("Test requires that -Dpolyglot.ConstantOptionKeyPartialEvaluationLanguage.ConstantOption2 is NOT set", //
                            System.getProperty("polyglot.ConstantOptionKeyPartialEvaluationLanguage.ConstantOption2"));
        }
    }

    @Test
    public void testSetValueFolded() {
        setupContext(Context.newBuilder(ConstantOptionKeyPartialEvaluationLanguage.ID).build());
        getContext().initialize(ConstantOptionKeyPartialEvaluationLanguage.ID);
        FrameDescriptor fd = new FrameDescriptor();
        RootTestNode rootNode = new RootTestNode(fd, "UseSetConstantOptionNode", new UseSetConstantOptionNode());
        assertPartialEvalEquals(ConstantOptionKeyPartialEvaluationTest::constant42, rootNode);
    }

    @Test
    public void testDefaultValueFolded() {
        setupContext(Context.newBuilder(ConstantOptionKeyPartialEvaluationLanguage.ID).build());
        getContext().initialize(ConstantOptionKeyPartialEvaluationLanguage.ID);
        FrameDescriptor fd = new FrameDescriptor();
        RootTestNode rootNode = new RootTestNode(fd, "UseUnSetConstantOptionNode", new UseUnSetConstantOptionNode());
        assertPartialEvalEquals(ConstantOptionKeyPartialEvaluationTest::constant0, rootNode);
    }

    public static Object constant42() {
        return 42;
    }

    public static Object constant0() {
        return 0;
    }

    @TruffleLanguage.Registration(id = ConstantOptionKeyPartialEvaluationLanguage.ID, name = ConstantOptionKeyPartialEvaluationLanguage.ID)
    static final class ConstantOptionKeyPartialEvaluationLanguage extends TruffleLanguage<TruffleLanguage.Env> {

        static final String ID = "ConstantOptionKeyPartialEvaluationLanguage";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Constant test option", constant = true)//
        static final ConstantOptionKey<Boolean> ConstantOption1 = new ConstantOptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Constant test option", constant = true)//
        static final ConstantOptionKey<Boolean> ConstantOption2 = new ConstantOptionKey<>(false);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ConstantOptionKeyPartialEvaluationLanguageOptionDescriptors();
        }
    }

    static final class UseSetConstantOptionNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            if (ConstantOptionKeyPartialEvaluationLanguage.ConstantOption1.getConstantValue()) {
                return 42;
            } else {
                return 0;
            }
        }
    }

    static final class UseUnSetConstantOptionNode extends AbstractTestNode {

        @Override
        public int execute(VirtualFrame frame) {
            if (ConstantOptionKeyPartialEvaluationLanguage.ConstantOption2.getConstantValue()) {
                return 42;
            } else {
                return 0;
            }
        }
    }
}
