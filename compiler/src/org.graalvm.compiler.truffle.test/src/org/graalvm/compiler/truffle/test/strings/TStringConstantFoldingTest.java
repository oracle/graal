/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.strings;

import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.TruffleStringStableFieldProviderProvider;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.strings.TruffleString;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TStringConstantFoldingTest extends TStringTest {

    final TruffleString a = TruffleString.fromByteArrayUncached(new byte[]{'a', 'b', 'c', 'd', 'e'}, UTF_8);
    final TruffleString b = TruffleString.fromByteArrayUncached(new byte[]{'a', 'b', 'c', 'd', 'e'}, UTF_8);
    final Object[] constantArgs = new Object[]{a, b};

    final KnownTruffleTypes knownTruffleTypes = new KnownTruffleTypes(getMetaAccess());

    @Override
    protected void before(ResolvedJavaMethod method) {
        TruffleStringStableFieldProviderProvider.initialize(getMetaAccess(), knownTruffleTypes);
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        addConstantParameterBinding(conf, constantArgs);
        return super.editGraphBuilderConfiguration(conf);
    }

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean ignoreForceCompile, boolean ignoreInstallAsDefault, OptionValues options) {
        return super.getCode(installedCodeOwner, graph, true, false, options);
    }

    @Test
    public void testRegionEquals() {
        test("regionEquals", a, b);
    }

    static boolean regionEquals(TruffleString a, TruffleString b) {
        return a.regionEqualByteIndexUncached(0, b, 0, a.byteLength(UTF_8), UTF_8);
    }

    @Test
    public void testEquals() {
        test("tsEquals", a, b);
    }

    static boolean tsEquals(TruffleString a, TruffleString b) {
        return a.equals(b);
    }

    @Test
    public void testCompareTo() {
        test("compareTo", a, b);
    }

    static int compareTo(TruffleString a, TruffleString b) {
        return a.compareBytesUncached(b, UTF_8);
    }

    @Test
    public void testIndexOf() {
        test("indexOf", a, b);
    }

    static int indexOf(TruffleString a, @SuppressWarnings("unused") TruffleString b) {
        return a.byteIndexOfCodePointUncached('b', 0, a.byteLength(UTF_8), UTF_8);
    }

    @Test
    public void testIndexOfSubstring() {
        test("indexOfSubstring", a, b);
    }

    static int indexOfSubstring(TruffleString a, TruffleString b) {
        return a.byteIndexOfStringUncached(b, 0, a.byteLength(UTF_8), UTF_8);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (getTarget().arch instanceof AMD64) {
            if (Math.max(GraalOptions.ArrayRegionEqualsConstantLimit.getValue(graph.getOptions()), GraalOptions.StringIndexOfConstantLimit.getValue(graph.getOptions())) >= a.byteLength(UTF_8)) {
                assertConstantReturn(graph);
            }
        }
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext graphBuilderContext, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getDeclaringClass().getName().startsWith("Lcom/oracle/truffle/api/strings/") &&
                        method.getAnnotationsByType(CompilerDirectives.TruffleBoundary.class).length == 0 &&
                        !(method.getDeclaringClass().getUnqualifiedName().equals("TStringOps") && method.getName().startsWith("run"))) {
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
        }
        return super.bytecodeParserShouldInlineInvoke(graphBuilderContext, method, args);
    }
}
