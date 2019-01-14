/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IfNodeCanonicalizationsTest extends JTTTest {

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getDeclaringClass().getUnqualifiedName().equals(IfNodeCanonicalizationsTest.class.getSimpleName()) && method.getName().startsWith("compare")) {
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }

    @Parameter(value = 0) public int x;
    @Parameter(value = 1) public int y;

    public static int compare0(int a, int b) {
        return (a < b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare1(int a, int b) {
        return (a < b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare2(int a, int b) {
        return (a < b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare3(int a, int b) {
        return (a < b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare4(int a, int b) {
        return (a < b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare5(int a, int b) {
        return (a < b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare6(int a, int b) {
        return (a < b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare7(int a, int b) {
        return (a < b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare8(int a, int b) {
        return (a < b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare9(int a, int b) {
        return (a < b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare10(int a, int b) {
        return (a < b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare11(int a, int b) {
        return (a < b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare12(int a, int b) {
        return (a > b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare13(int a, int b) {
        return (a > b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare14(int a, int b) {
        return (a > b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare15(int a, int b) {
        return (a > b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare16(int a, int b) {
        return (a > b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare17(int a, int b) {
        return (a > b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare18(int a, int b) {
        return (a > b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare19(int a, int b) {
        return (a > b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare20(int a, int b) {
        return (a > b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare21(int a, int b) {
        return (a > b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare22(int a, int b) {
        return (a > b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare23(int a, int b) {
        return (a > b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare24(int a, int b) {
        return (a <= b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare25(int a, int b) {
        return (a <= b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare26(int a, int b) {
        return (a <= b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare27(int a, int b) {
        return (a <= b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare28(int a, int b) {
        return (a <= b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare29(int a, int b) {
        return (a <= b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare30(int a, int b) {
        return (a <= b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare31(int a, int b) {
        return (a <= b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare32(int a, int b) {
        return (a <= b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare33(int a, int b) {
        return (a <= b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare34(int a, int b) {
        return (a <= b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare35(int a, int b) {
        return (a <= b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare36(int a, int b) {
        return (a >= b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare37(int a, int b) {
        return (a >= b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare38(int a, int b) {
        return (a >= b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare39(int a, int b) {
        return (a >= b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare40(int a, int b) {
        return (a >= b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare41(int a, int b) {
        return (a >= b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare42(int a, int b) {
        return (a >= b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare43(int a, int b) {
        return (a >= b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare44(int a, int b) {
        return (a >= b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare45(int a, int b) {
        return (a >= b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare46(int a, int b) {
        return (a >= b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare47(int a, int b) {
        return (a >= b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare48(int a, int b) {
        return (a == b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare49(int a, int b) {
        return (a == b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare50(int a, int b) {
        return (a == b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare51(int a, int b) {
        return (a == b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare52(int a, int b) {
        return (a == b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare53(int a, int b) {
        return (a == b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare54(int a, int b) {
        return (a == b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare55(int a, int b) {
        return (a == b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare56(int a, int b) {
        return (a == b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare57(int a, int b) {
        return (a == b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare58(int a, int b) {
        return (a == b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare59(int a, int b) {
        return (a == b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare60(int a, int b) {
        return (a != b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare61(int a, int b) {
        return (a != b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare62(int a, int b) {
        return (a != b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare63(int a, int b) {
        return (a != b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare64(int a, int b) {
        return (a != b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare65(int a, int b) {
        return (a != b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare66(int a, int b) {
        return (a != b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare67(int a, int b) {
        return (a != b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare68(int a, int b) {
        return (a != b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare69(int a, int b) {
        return (a != b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare70(int a, int b) {
        return (a != b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare71(int a, int b) {
        return (a != b) ? ((a != b) ? 1 : 2) : 3;
    }

    @Test
    public void run0() {
        runTest("compare0", x, y);
    }

    @Test
    public void run1() {
        runTest("compare1", x, y);
    }

    @Test
    public void run2() {
        runTest("compare2", x, y);
    }

    @Test
    public void run3() {
        runTest("compare3", x, y);
    }

    @Test
    public void run4() {
        runTest("compare4", x, y);
    }

    @Test
    public void run5() {
        runTest("compare5", x, y);
    }

    @Test
    public void run6() {
        runTest("compare6", x, y);
    }

    @Test
    public void run7() {
        runTest("compare7", x, y);
    }

    @Test
    public void run8() {
        runTest("compare8", x, y);
    }

    @Test
    public void run9() {
        runTest("compare9", x, y);
    }

    @Test
    public void run10() {
        runTest("compare10", x, y);
    }

    @Test
    public void run11() {
        runTest("compare11", x, y);
    }

    @Test
    public void run12() {
        runTest("compare12", x, y);
    }

    @Test
    public void run13() {
        runTest("compare13", x, y);
    }

    @Test
    public void run14() {
        runTest("compare14", x, y);
    }

    @Test
    public void run15() {
        runTest("compare15", x, y);
    }

    @Test
    public void run16() {
        runTest("compare16", x, y);
    }

    @Test
    public void run17() {
        runTest("compare17", x, y);
    }

    @Test
    public void run18() {
        runTest("compare18", x, y);
    }

    @Test
    public void run19() {
        runTest("compare19", x, y);
    }

    @Test
    public void run20() {
        runTest("compare20", x, y);
    }

    @Test
    public void run21() {
        runTest("compare21", x, y);
    }

    @Test
    public void run22() {
        runTest("compare22", x, y);
    }

    @Test
    public void run23() {
        runTest("compare23", x, y);
    }

    @Test
    public void run24() {
        runTest("compare24", x, y);
    }

    @Test
    public void run25() {
        runTest("compare25", x, y);
    }

    @Test
    public void run26() {
        runTest("compare26", x, y);
    }

    @Test
    public void run27() {
        runTest("compare27", x, y);
    }

    @Test
    public void run28() {
        runTest("compare28", x, y);
    }

    @Test
    public void run29() {
        runTest("compare29", x, y);
    }

    @Test
    public void run30() {
        runTest("compare30", x, y);
    }

    @Test
    public void run31() {
        runTest("compare31", x, y);
    }

    @Test
    public void run32() {
        runTest("compare32", x, y);
    }

    @Test
    public void run33() {
        runTest("compare33", x, y);
    }

    @Test
    public void run34() {
        runTest("compare34", x, y);
    }

    @Test
    public void run35() {
        runTest("compare35", x, y);
    }

    @Test
    public void run36() {
        runTest("compare36", x, y);
    }

    @Test
    public void run37() {
        runTest("compare37", x, y);
    }

    @Test
    public void run38() {
        runTest("compare38", x, y);
    }

    @Test
    public void run39() {
        runTest("compare39", x, y);
    }

    @Test
    public void run40() {
        runTest("compare40", x, y);
    }

    @Test
    public void run41() {
        runTest("compare41", x, y);
    }

    @Test
    public void run42() {
        runTest("compare42", x, y);
    }

    @Test
    public void run43() {
        runTest("compare43", x, y);
    }

    @Test
    public void run44() {
        runTest("compare44", x, y);
    }

    @Test
    public void run45() {
        runTest("compare45", x, y);
    }

    @Test
    public void run46() {
        runTest("compare46", x, y);
    }

    @Test
    public void run47() {
        runTest("compare47", x, y);
    }

    @Test
    public void run48() {
        runTest("compare48", x, y);
    }

    @Test
    public void run49() {
        runTest("compare49", x, y);
    }

    @Test
    public void run50() {
        runTest("compare50", x, y);
    }

    @Test
    public void run51() {
        runTest("compare51", x, y);
    }

    @Test
    public void run52() {
        runTest("compare52", x, y);
    }

    @Test
    public void run53() {
        runTest("compare53", x, y);
    }

    @Test
    public void run54() {
        runTest("compare54", x, y);
    }

    @Test
    public void run55() {
        runTest("compare55", x, y);
    }

    @Test
    public void run56() {
        runTest("compare56", x, y);
    }

    @Test
    public void run57() {
        runTest("compare57", x, y);
    }

    @Test
    public void run58() {
        runTest("compare58", x, y);
    }

    @Test
    public void run59() {
        runTest("compare59", x, y);
    }

    @Test
    public void run60() {
        runTest("compare60", x, y);
    }

    @Test
    public void run61() {
        runTest("compare61", x, y);
    }

    @Test
    public void run62() {
        runTest("compare62", x, y);
    }

    @Test
    public void run63() {
        runTest("compare63", x, y);
    }

    @Test
    public void run64() {
        runTest("compare64", x, y);
    }

    @Test
    public void run65() {
        runTest("compare65", x, y);
    }

    @Test
    public void run66() {
        runTest("compare66", x, y);
    }

    @Test
    public void run67() {
        runTest("compare67", x, y);
    }

    @Test
    public void run68() {
        runTest("compare68", x, y);
    }

    @Test
    public void run69() {
        runTest("compare69", x, y);
    }

    @Test
    public void run70() {
        runTest("compare70", x, y);
    }

    @Test
    public void run71() {
        runTest("compare71", x, y);
    }

    @Parameters(name = "{0}, {1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        addTest(tests, 0, 0);
        addTest(tests, 1, 0);
        addTest(tests, 0, 1);
        return tests;
    }

    private static void addTest(ArrayList<Object[]> tests, int x, int b) {
        tests.add(new Object[]{x, b});
    }
}
