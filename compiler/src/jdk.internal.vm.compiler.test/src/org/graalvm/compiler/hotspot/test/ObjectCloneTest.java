/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.util.ArrayList;
import java.util.Optional;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.replacements.ObjectCloneNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.virtual.phases.ea.FinalPartialEscapePhase;
import org.junit.Assert;
import org.junit.Test;

/**
 * Exercise intrinsification of {@link Object#clone}.
 */
public class ObjectCloneTest extends GraalCompilerTest {

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites suites = super.createSuites(opts);
        var pos = suites.getHighTier().findPhase(FinalPartialEscapePhase.class);
        pos.previous();
        pos.add(new BasePhase<HighTierContext>() {
            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            public CharSequence getName() {
                return "CheckObjectCloneIntrinsification";
            }

            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                int clones = graph.getNodes().filter(ObjectCloneNode.class).count();
                Assert.assertEquals("number of intrinsified Object.clone() calls in test snippet", 1, clones);
            }
        });
        return suites;
    }

    @BytecodeParserNeverInline
    public static Object cloneArray(int[] array) {
        return array.clone();
    }

    private static final class ArrayHolder<T> {
        T[] array;

        private ArrayHolder(T[] array) {
            this.array = array;
        }
    }

    public static Object[] cloneGenericObjectArray(ArrayHolder<Object> holder) {
        return holder.array.clone();
    }

    public static Number[] cloneDynamicObjectArray(ArrayHolder<Number> holder) {
        return holder.array.clone();
    }

    public static Integer[] cloneConcreteObjectArray(ArrayHolder<Integer> holder) {
        return holder.array.clone();
    }

    @BytecodeParserNeverInline
    public static <T> T[] cloneArrayGeneric(T[] array) {
        return array.clone();
    }

    public static Object cloneList(ArrayList<?> list) {
        return list.clone();
    }

    static class ObjectCloneable implements Cloneable {
        int field;

        @Override
        protected Object clone() throws CloneNotSupportedException {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
    }

    static class CloneableSubclass extends ObjectCloneable {

    }

    /*
     * This test checks that the ObjectCloneNode doesn't accidentally inject non-nullness into the
     * graph which is later removed.
     */
    public static Object notCloneable(ObjectCloneable cloneable) throws CloneNotSupportedException {
        ObjectCloneable clone = (ObjectCloneable) cloneable.clone();
        return clone.getClass();
    }

    @Test
    public void testNotIntrinsified() throws Throwable {
        test("notCloneable", new CloneableSubclass());
    }

    @Test
    public void testArray() throws Throwable {
        test("cloneArray", new int[]{1, 2, 4, 3});
    }

    @Test
    public void testGenericObjectArray() throws Throwable {
        test("cloneGenericObjectArray", new ArrayHolder<>(new Integer[]{1, 2, 4, 3}));
    }

    @Test
    public void testDynamicObjectArray() throws Throwable {
        test("cloneDynamicObjectArray", new ArrayHolder<>(new Number[]{1, 2, 4, 3}));
    }

    @Test
    public void testConcreteObjectArray() throws Throwable {
        test("cloneConcreteObjectArray", new ArrayHolder<>(new Integer[]{1, 2, 3, 4}));
    }

    @Test
    public void testList() throws Throwable {
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            list.add(i);
        }
        test("cloneList", list);
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        return super.editGraphBuilderConfiguration(conf.withNodeSourcePosition(true));
    }

    static final int[] ARRAY = new int[]{1, 2, 4, 3};

    public static int[] cloneConstantArray() {
        return ARRAY.clone();
    }

    @Test
    public void testCloneConstantArray() {
        test("cloneConstantArray");
    }

    public static Object cloneArrayWithImpreciseStamp(int[] inputArray, int count) {
        int[] array = inputArray;
        for (int j = 0; j < count; j++) {
            for (int i = 0; i < j; i++) {
                if (i > 3) {
                    array = new int[i];
                    array[i - 1] = i;
                }
                GraalDirectives.controlFlowAnchor();
            }
            GraalDirectives.controlFlowAnchor();
        }
        return array.clone();
    }

    @Test
    public void testCloneArrayWithImpreciseStamp() {
        test("cloneArrayWithImpreciseStamp", ARRAY, ARRAY.length);
    }

    public static Object cloneArrayWithImpreciseStampInlined(int[] inputArray, int count) {
        int[] array = inputArray;
        for (int j = 0; j < count; j++) {
            for (int i = 0; i < j; i++) {
                if (i > 3) {
                    array = new int[i];
                    array[i - 1] = i;
                }
                GraalDirectives.controlFlowAnchor();
            }
            GraalDirectives.controlFlowAnchor();
        }
        return cloneArray(array);
    }

    @Test
    public void testCloneArrayWithImpreciseStampInlined() {
        test("cloneArrayWithImpreciseStampInlined", ARRAY, ARRAY.length);
    }

    public static Object cloneArrayWithImpreciseStampInlinedGeneric(Integer[] inputArray, int count) {
        Integer[] array = inputArray;
        for (int j = 0; j < count; j++) {
            for (int i = 0; i < j; i++) {
                if (i > 3) {
                    array = new Integer[i];
                    array[i - 1] = i;
                }
                GraalDirectives.controlFlowAnchor();
            }
            GraalDirectives.controlFlowAnchor();
        }
        return cloneArrayGeneric(array);
    }

    @Test
    public void testCloneArrayWithImpreciseStampInlinedGeneric() {
        test("cloneArrayWithImpreciseStampInlinedGeneric", new Integer[]{1, 2, 3, 4}, ARRAY.length);
    }
}
