/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import jdk.graal.compiler.truffle.substitutions.TruffleInvocationPlugins;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.ArrayUtils;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ArrayUtilsRegionEqualsWithMaskConstantTest extends GraalCompilerTest {

    private static final char[] CHARS_UPPER = "Hello world!".toCharArray();
    private static final char[] CHARS_LOWER = "hello world!".toCharArray();
    private static final char[] CHARS_SUFFIX = "ello world!".toCharArray();

    private static final String STRING_UPPER = "Hello world!";
    private static final String STRING_LOWER = "hello world!";
    private static final String STRING_SUFFIX = "ello world!";

    // U+0151: latin small letter o with double acute
    private static final String STRING_UPPER_WIDE = "Hello w\u0151rld!";

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        TruffleInvocationPlugins.register(getBackend().getTarget().arch, invocationPlugins);
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static boolean equalShiftedSuffixSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(CHARS_UPPER, 1, CHARS_SUFFIX, 0, CHARS_SUFFIX.length, null);
    }

    public static boolean equalShiftedSuffixFlippedSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(CHARS_SUFFIX, 0, CHARS_UPPER, 1, CHARS_SUFFIX.length, null);
    }

    public static boolean unequalSuffixSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(CHARS_UPPER, 0, CHARS_SUFFIX, 0, CHARS_SUFFIX.length, null);
    }

    public static boolean equalUpperLowerSuffixSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(CHARS_UPPER, 1, CHARS_LOWER, 1, CHARS_UPPER.length - 1, null);
    }

    public static boolean unequalUpperLowerSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(CHARS_UPPER, 0, CHARS_LOWER, 0, CHARS_UPPER.length, null);
    }

    public static boolean equalUpperLowerWithMaskSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(CHARS_UPPER, 0, CHARS_LOWER, 0, CHARS_UPPER.length, CHARS_UPPER);
    }

    public static boolean unequalUpperLowerWithMaskSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(CHARS_UPPER, 0, CHARS_LOWER, 0, CHARS_UPPER.length, CHARS_LOWER);
    }

    public static boolean equalShiftedSuffixStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER, 1, STRING_SUFFIX, 0, STRING_SUFFIX.length(), null);
    }

    public static boolean equalShiftedSuffixFlippedStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_SUFFIX, 0, STRING_UPPER, 1, STRING_SUFFIX.length(), null);
    }

    public static boolean unequalSuffixStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER, 0, STRING_SUFFIX, 0, STRING_SUFFIX.length(), null);
    }

    public static boolean equalUpperLowerSuffixStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER, 1, STRING_LOWER, 1, STRING_UPPER.length() - 1, null);
    }

    public static boolean unequalUpperLowerStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER, 0, STRING_LOWER, 0, STRING_UPPER.length(), null);
    }

    public static boolean equalUpperLowerWithMaskStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER, 0, STRING_LOWER, 0, STRING_UPPER.length(), STRING_UPPER);
    }

    public static boolean unequalUpperLowerWithMaskStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER, 0, STRING_LOWER, 0, STRING_UPPER.length(), STRING_LOWER);
    }

    public static boolean equalCompactWidePrefixStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER, 0, STRING_UPPER_WIDE, 0, 7, null);
    }

    public static boolean equalWideCompactPrefixStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER_WIDE, 0, STRING_UPPER, 0, 7, null);
    }

    public static boolean unequalCompactWidePrefixStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER, 0, STRING_UPPER_WIDE, 0, 8, null);
    }

    public static boolean unequalWideCompactPrefixStringSnippet() {
        return ArrayUtils.regionEqualsWithOrMask(STRING_UPPER_WIDE, 0, STRING_UPPER, 0, 8, null);
    }

    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        return makeAllArraysStable(super.parseForCompile(method, compilationId, options));
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        Assert.assertTrue("array region equals nodes must fold away", graph.getNodes().filter(ArrayRegionEqualsNode.class).isEmpty());

        NodeIterable<ReturnNode> returns = graph.getNodes(ReturnNode.TYPE);
        Assert.assertEquals("number of return nodes", 1, returns.count());
        ValueNode returnValue = returns.first().result();
        Assert.assertTrue("return value must be constant, not " + returnValue, returnValue.isConstant());

        super.checkHighTierGraph(graph);
    }

    @Test
    public void testConstantArrays() {
        Assume.assumeTrue("array region equals with mask is AMD64-exclusive at the moment", getArchitecture() instanceof AMD64);
        test("equalShiftedSuffixSnippet");
        test("equalShiftedSuffixFlippedSnippet");
        test("unequalSuffixSnippet");
        test("equalUpperLowerSuffixSnippet");
        test("unequalUpperLowerSnippet");
        test("equalUpperLowerWithMaskSnippet");
        test("unequalUpperLowerWithMaskSnippet");
    }

    @Test
    public void testConstantStrings() {
        Assume.assumeTrue("array region equals with mask is AMD64-exclusive at the moment", getArchitecture() instanceof AMD64);
        test("equalShiftedSuffixStringSnippet");
        test("equalShiftedSuffixFlippedStringSnippet");
        test("unequalSuffixStringSnippet");
        test("equalUpperLowerSuffixStringSnippet");
        test("unequalUpperLowerStringSnippet");
        test("equalUpperLowerWithMaskStringSnippet");
        test("unequalUpperLowerWithMaskStringSnippet");
    }

    @Test
    public void testMixedConstantStrings() {
        Assume.assumeTrue("array region equals with mask is AMD64-exclusive at the moment", getArchitecture() instanceof AMD64);
        test("equalCompactWidePrefixStringSnippet");
        test("equalWideCompactPrefixStringSnippet");
        test("unequalCompactWidePrefixStringSnippet");
        test("unequalWideCompactPrefixStringSnippet");
    }
}
