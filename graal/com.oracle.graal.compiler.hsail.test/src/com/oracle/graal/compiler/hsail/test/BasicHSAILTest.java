/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.hsail.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.compiler.target.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.gpu.*;
import com.oracle.graal.hotspot.hsail.*;
import com.oracle.graal.hsail.*;

/**
 * Test class for small Java methods compiled to HSAIL kernels.
 */
public class BasicHSAILTest extends GraalCompilerTest {

    public BasicHSAILTest() {
        super(HSAIL.class);
    }

    public void testAdd() {
        test("testAddSnippet");
    }

    public static int testAddSnippet(int a) {
        return a * a;
    }

    public void testArrayConstantIndex() {
        test("testArrayReturnFirstElement");
    }

    public void testArrayVariableIndex() {
        test("testArrayReturnIthElement");
    }

    public void testArrayMultiplyConstant() {
        test("testArrayMultiplyZero");
    }

    public void testArrayMultiplyVar() {
        test("testArrayMultiplyGid");
    }

    public void testArrayMisc() {
        test("testArrayLocalVariable");
    }

    public void testArrayLoopVar() {
        test("testArrayMultiplyGidLoop");
    }

    void setupPalette(int[] in) {
        for (int i = 0; i < in.length; i++) {
            in[i] = i;
        }
    }

    public void testNBody() {
        test("nBodySpill");
    }

    public void testArrayMandel() {
        final int width = 768;
        final int height = width;
        int loopiterations = 1;
        int counter = 0;
        final int range = width * height;
        int[] rgb = new int[range];
        int[] palette = new int[range];
        setupPalette(palette);
        while (counter < loopiterations) {
            for (int gid = 0; gid < range; gid++) {
                testMandelSimple(rgb, palette, -1.0f, 0.0f, 3f, gid);
            }
            counter++;
        }
        test("testMandelSimple");
    }

    public void testDanglingElse() {
        test("danglingElse");
    }

    public void testIntSquaresTernary() {
        test("intSquaresTernary");
    }

    public void testDanglingElse2() {
        test("danglingElse2");
    }

    public void testDanglingElse3() {
        test("danglingElse3");
    }

    public void testSimpleIf() {
        test("simpleIf");
    }

    public void testParams11() {
        test("testParams1");
    }

    public void testParams21() {
        test("testParams2");
    }

    public void testParams31() {
        test("testParams3");
    }

    public void testAssignment1() {
        test("testAssignment");
    }

    public void testArithmetic1() {
        test("testArithmetic");
    }

    public void testSimpleWhile1() {
        test("testSimpleWhile");
    }

    public void testComplexWhile1() {
        test("testComplexWhile");
    }

    public void testSquaresThree() {
        test("testMulThreeArrays");
    }

    @Test
    public void testCondMoves() {
        test("testMinI");
        test("testMinF");
    }

    public int testMinI(int a, int b) {
        return (a < b ? 1 : 2);
    }

    public float testMinF(int a, int b) {
        return (a < b ? 1.0f : 2.0f);
    }

    public static void testMulThreeArrays(int[] out, int[] ina, int[] inb, int gid) {
        out[gid] = ina[gid] * inb[gid];
    }

    public static int testArrayMultiplyZero(int[] array1, int[] array2) {
        return array1[0] = array2[0] * array2[0];
    }

    public static int testArrayMultiplyGid(int[] array1, int[] array2, int gid) {
        return array1[gid] = array2[gid] * array2[gid];
    }

    public static float testParams3(float c, float d, float e) {
        return c + d + e;
    }

    public static int testAssignment() {
        final int width = 768;
        final int height = 768;
        final int maxIterations = 64;
        return width * height * maxIterations;
    }

    public static int testSimpleWhile(int i) {
        int count = 0;
        int j = 0;
        final int maxIterations = 64;
        while (count < maxIterations) {
            j += count * i;
            count++;
        }
        return j;
    }

    public static void testComplexWhile() {
        float lx = 1;
        float ly = 2;
        float zx = lx;
        float zy = ly;
        float newzx = 0f;
        final int maxIterations = 64;
        int count = 0;
        while (count < maxIterations && zx * zx + zy * zy < 8) {
            newzx = zx * zx - zy * zy + lx;
            zy = 2 * zx * zy + ly;
            zx = newzx;
            count++;
        }
    }

    public static void testMandel(int[] rgb, int[] pallette, float xoffset, float yoffset, float scale, int gid) {
        final int width = 768;
        final int height = 768;
        final int maxIterations = 64;
        float lx = (((gid % width * scale) - ((scale / 2) * width)) / width) + xoffset;
        float ly = (((gid / width * scale) - ((scale / 2) * height)) / height) + yoffset;
        int count = 0;
        float zx = lx;
        float zy = ly;
        float newzx = 0f;
        /**
         * Iterate until the algorithm converges or until maxIterations are reached.
         */
        while (count < maxIterations && zx * zx + zy * zy < 8) {
            newzx = zx * zx - zy * zy + lx;
            zy = 2 * zx * zy + ly;
            zx = newzx;
            count++;
        }
        rgb[gid] = pallette[count];
    }

    public static void testMandelSimple(int[] rgb, int[] pallette, float xoffset, float yoffset, float scale, int gid) {
        final int width = 768;
        final int height = width;
        final int maxIterations = 64;
        float lx = (((gid % width * scale) - ((scale / 2) * width)) / width) + xoffset;
        float ly = (((gid / width * scale) - ((scale / 2) * height)) / height) + yoffset;
        int count = 0;
        float zx = lx;
        float zy = ly;
        float newzx = 0f;
        /**
         * Iterate until the algorithm converges or until maxIterations are reached.
         */
        while (count < maxIterations && zx * zx + zy * zy < 8) {
            newzx = zx * zx - zy * zy + lx;
            zy = 2 * zx * zy + ly;
            zx = newzx;
            count++;
        }
        rgb[gid] = pallette[count];
    }

    public static void testMandel2(int[] rgb, int[] pallette, int xoffseti, int yoffseti, int scalei, int gid) {
        final int width = 768;
        final int height = 768;
        final int maxIterations = 64;
        float xoffset = xoffseti;
        float yoffset = yoffseti;
        float scale = scalei;
        float lx = (((gid % width * scale) - ((scale / 2) * width)) / width) + xoffset;
        float ly = (((gid / width * scale) - ((scale / 2) * height)) / height) + yoffset;
        int count = 0;
        float zx = lx;
        float zy = ly;
        float newzx = 0f;
        /**
         * Iterate until the algorithm converges or until maxIterations are reached.
         */
        while (count < maxIterations && zx * zx + zy * zy < 8) {
            newzx = zx * zx - zy * zy + lx;
            zy = 2 * zx * zy + ly;
            zx = newzx;
            count++;
        }
        rgb[gid] = pallette[count];
    }

    public static int testArrayLocalVariable(int gid, int[] array) {
        int foo = 198;
        return array[gid + foo];
    }

    public static int testArrayReturnFirstElement(int[] array) {
        return array[0];
    }

    public static int testArrayReturnIthElement(int i, int[] array) {
        return array[i];
    }

    public static void simpleIf(int[] out, int[] in, int gid) {
        if (gid > 9) {
            out[gid] = in[gid] * in[gid];
        }
    }

    public static int danglingElse(int a) {
        return (a > 5) ? (a + 7) : (a - 3);
    }

    public static int danglingElse2(int a, int b) {
        if (a > 5) {
            return (a + 7 * (b - 4 + a));
        } else {
            return (a - 3 + b * 3 * a + 5);
        }
    }

    public static int danglingElse3(int a, int b) {
        int val;
        if (a > 5) {
            val = (a + 7 * (b - 4 + a));
        } else {
            val = (a - 3 + b * 3 * a + 5);
        }
        return val + a;
    }

    public static void intSquaresTernary(int[] out, int[] in, int gid) {
        int val = in[gid] * in[gid];
        val = (val % 2 == 1 ? val + 1 : val);
        out[gid] = val;
    }

    @Override
    protected HSAILHotSpotBackend getBackend() {
        Backend backend = super.getBackend();
        Assume.assumeTrue(backend instanceof HSAILHotSpotBackend);
        return (HSAILHotSpotBackend) backend;
    }

    private void test(final String snippet) {
        try (Scope s = Debug.scope("HSAILCodeGen")) {
            Method method = getMethod(snippet);
            ExternalCompilationResult hsailCode = getBackend().compileKernel(getMetaAccess().lookupJavaMethod(method), false);
            Debug.log("HSAIL code generated for %s:%n%s", snippet, hsailCode.getCodeString());
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    public static void nBodySpill(float[] inxyz, float[] outxyz, float[] invxyz, float[] outvxyz, int gid) {
        final int bodies = 8;
        final float delT = .005f;
        final float espSqr = 1.0f;
        final float mass = 5f;
        final int count = bodies * 3;
        final int globalId = gid * 3;
        float accx = 0.f;
        float accy = 0.f;
        float accz = 0.f;
        for (int i = 0; i < count; i += 3) {
            final float dx = inxyz[i + 0] - inxyz[globalId + 0];
            final float dy = inxyz[i + 1] - inxyz[globalId + 1];
            final float dz = inxyz[i + 2] - inxyz[globalId + 2];
            final float invDist = (float) (1.0 / (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz) + espSqr)));
            accx += mass * invDist * invDist * invDist * dx;
            accy += mass * invDist * invDist * invDist * dy;
            accz += mass * invDist * invDist * invDist * dz;
        }
        accx *= delT;
        accy *= delT;
        accz *= delT;
        outxyz[globalId + 0] = inxyz[globalId + 0] + (invxyz[globalId + 0] * delT) + (accx * .5f * delT);
        outxyz[globalId + 1] = inxyz[globalId + 1] + (invxyz[globalId + 1] * delT) + (accy * .5f * delT);
        outxyz[globalId + 2] = inxyz[globalId + 2] + (invxyz[globalId + 2] * delT) + (accz * .5f * delT);
        outvxyz[globalId + 0] = invxyz[globalId + 0] + accx;
        outvxyz[globalId + 1] = invxyz[globalId + 1] + accy;
        outvxyz[globalId + 2] = invxyz[globalId + 2] + accz;
    }
}
