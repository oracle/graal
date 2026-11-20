/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.Assert;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchorNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class DFAnalysisBaseTest extends GraalCompilerTest {

    @Override
    protected Result test(OptionValues options, ResolvedJavaMethod method, Object receiver, Object... args) {
        return super.test(modifyOptions(options), method, receiver, args);
    }

    protected OptionValues modifyOptions(OptionValues current) {
        return current;
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    protected @interface DFATestSnippet {
        int loops() default -1;

        int conditionals() default -1;

        int ifs() default -1;

        int deopts() default -1;

        int anchors() default -1;

        int blackholes() default -1;

        String returns() default "";
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        AnnotationValue spec = AnnotationValueSupport.getAnnotationValue(graph.method(), DFATestSnippet.class);
        if (spec == null) {
            return;
        }
        int actualLoopBegins = 0;
        int actualConditionals = 0;
        int actualIfs = 0;
        int actualDeopts = 0;
        int actualAnchors = 0;
        int actualBlackholes = 0;
        String actualReturns = null;
        for (Node n : graph.getNodes()) {
            switch (n) {
                case LoopBeginNode ignore -> actualLoopBegins++;
                case ConditionalNode ignore -> actualConditionals++;
                case IfNode ignore -> actualIfs++;
                case AbstractDeoptimizeNode ignore -> actualDeopts++;
                case ControlFlowAnchorNode ignore -> actualAnchors++;
                case BlackholeNode ignore -> actualBlackholes++;
                case ReturnNode ret -> {
                    ValueNode val = ret.result();
                    if (val == null) {
                        actualReturns = "void";
                        continue;
                    }
                    String res = val.stamp(NodeView.DEFAULT).toString();
                    if (actualReturns == null) {
                        actualReturns = res;
                    } else {
                        if (!actualReturns.equals(res)) {
                            actualReturns = "different values";
                        }
                    }
                }
                default -> {
                    // do nothing for other nodes
                }
            }
        }
        if (actualReturns == null) {
            actualReturns = "no value";
        }

        conditionalAssertEquals(actualLoopBegins, "remaining loops", spec, "loops");
        conditionalAssertEquals(actualConditionals, "remaining conditionals", spec, "conditionals");
        conditionalAssertEquals(actualIfs, "remaining ifs", spec, "ifs");
        conditionalAssertEquals(actualDeopts, "remaining deoptimizations", spec, "deopts");
        conditionalAssertEquals(actualAnchors, "remaining control flow anchors", spec, "anchors");
        conditionalAssertEquals(actualBlackholes, "remaining black hole nodes", spec, "blackholes");
        conditionalAssertEquals(actualReturns, "return value", spec, "returns");
    }

    private static void conditionalAssertEquals(int actual, String msg, AnnotationValue spec, String annMethodName) {
        int result = spec.getInt(annMethodName);
        if (result >= 0) {
            Assert.assertEquals(msg, result, actual);
        }
    }

    private static void conditionalAssertEquals(String actual, String msg, AnnotationValue spec, String annMethodName) {
        String result = spec.getString(annMethodName);
        if (!"".equals(result)) {
            Assert.assertEquals(msg, result, actual);
        }
    }
}
