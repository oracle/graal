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
package jdk.graal.compiler.loop.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;

import jdk.graal.compiler.vector.phases.VectorLoopUtility;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/**
 * Port of closed/test/hotspot/jtreg/compiler/c2/rangeChecks/TestRangeCheckElimination.java to
 * Graal.
 */
public class RangeCheckEliminationTest extends GraalCompilerTest {

    public static final boolean LOG = false;

    static final int MIN_BOUNDS = -10;
    static final int MAX_BOUNDS = 10;

    enum BoolTest {
        // The naming refers to the condition that holds while the main loop is executed.
        // I.e. the negation of the test in the predicate.
        LE {
            @Override
            public BiPredicate<Integer, Integer> toBiPredicate() {
                return (a, b) -> (a > b);
            }
        },
        GE {
            @Override
            public BiPredicate<Integer, Integer> toBiPredicate() {
                return (a, b) -> (a < b);
            }
        },
        LT {
            @Override
            public BiPredicate<Integer, Integer> toBiPredicate() {
                return (a, b) -> (a >= b);
            }
        },
        GT {
            @Override
            public BiPredicate<Integer, Integer> toBiPredicate() {
                return (a, b) -> (a <= b);
            }
        };

        public abstract BiPredicate<Integer, Integer> toBiPredicate();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Test {
        BoolTest test();

        int stride();

        int scale();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TestSimpleArrayAccess {
        int stride();

        int scale();

        boolean largeArray() default false;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TestArrayAccess {
        int stride();

        int scale();

        boolean largeArray() default false;
    }

    // LE cases

    @Test(test = BoolTest.LE, stride = 1, scale = 1)
    public static int testLE1(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 1, scale = -1)
    public static int testLE2(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (-i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -1, scale = 1)
    public static int testLE3(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -1, scale = -1)
    public static int testLE4(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (-i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 2, scale = 1)
    public static int testLE5(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 2, scale = -1)
    public static int testLE6(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (-i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -2, scale = 1)
    public static int testLE7(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -2, scale = -1)
    public static int testLE8(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (-i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 2, scale = 2)
    public static int testLE9(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (2 * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 2, scale = -2)
    public static int testLE10(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (-2 * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -2, scale = 2)
    public static int testLE11(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (2 * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -2, scale = -2)
    public static int testLE12(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (-2 * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 1, scale = 2)
    public static int testLE13(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (2 * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 1, scale = -2)
    public static int testLE14(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (-2 * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -1, scale = 2)
    public static int testLE15(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (2 * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -1, scale = -2)
    public static int testLE16(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (-2 * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 1, scale = Integer.MAX_VALUE)
    public static int testLE17(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (Integer.MAX_VALUE * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = 1, scale = Integer.MIN_VALUE)
    public static int testLE18(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (Integer.MIN_VALUE * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -1, scale = Integer.MAX_VALUE)
    public static int testLE19(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (Integer.MAX_VALUE * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LE, stride = -1, scale = Integer.MIN_VALUE)
    public static int testLE20(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (Integer.MIN_VALUE * i + off) > limit) {
                break;
            }
        }
        return i;
    }

    // Unrolled version of testLE1
    @Test(test = BoolTest.LE, stride = 1, scale = 1)
    public static int testLE21(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 4) {
            if (i >= threshold && (i + off) > limit) {
                return i;
            }
            if (i + 1 >= threshold && ((i + 1) + off) > limit) {
                return i + 1;
            }
            if (i + 2 >= threshold && ((i + 2) + off) > limit) {
                return i + 2;
            }
            if (i + 3 >= threshold && ((i + 3) + off) > limit) {
                return i + 3;
            }
        }
        return i;
    }

    // Unrolled version of testLE2
    @Test(test = BoolTest.LE, stride = 1, scale = -1)
    public static int testLE22(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 4) {
            if (i >= threshold && (-i + off) > limit) {
                return i;
            }
            if (i + 1 >= threshold && (-(i + 1) + off) > limit) {
                return i + 1;
            }
            if (i + 2 >= threshold && (-(i + 2) + off) > limit) {
                return i + 2;
            }
            if (i + 3 >= threshold && (-(i + 3) + off) > limit) {
                return i + 3;
            }
        }
        return i;
    }

    // Unrolled version of testLE3
    @Test(test = BoolTest.LE, stride = -1, scale = 1)
    public static int testLE23(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 4) {
            if (i <= threshold && (i + off) > limit) {
                return i;
            }
            if (i - 1 <= threshold && ((i - 1) + off) > limit) {
                return i - 1;
            }
            if (i - 2 <= threshold && ((i - 2) + off) > limit) {
                return i - 2;
            }
            if (i - 3 <= threshold && ((i - 3) + off) > limit) {
                return i - 3;
            }
        }
        return i;
    }

    // Unrolled version of testLE4
    @Test(test = BoolTest.LE, stride = -1, scale = -1)
    public static int testLE24(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 4) {
            if (i <= threshold && (-i + off) > limit) {
                return i;
            }
            if (i - 1 <= threshold && (-(i - 1) + off) > limit) {
                return i - 1;
            }
            if (i - 2 <= threshold && (-(i - 2) + off) > limit) {
                return i - 2;
            }
            if (i - 3 <= threshold && (-(i - 3) + off) > limit) {
                return i - 3;
            }
        }
        return i;
    }

    // GE cases

    @Test(test = BoolTest.GE, stride = 1, scale = 1)
    public static int testGE1(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 1, scale = -1)
    public static int testGE2(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (-i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -1, scale = 1)
    public static int testGE3(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -1, scale = -1)
    public static int testGE4(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (-i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 2, scale = 1)
    public static int testGE5(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 2, scale = -1)
    public static int testGE6(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (-i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -2, scale = 1)
    public static int testGE7(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -2, scale = -1)
    public static int testGE8(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (-i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 2, scale = 2)
    public static int testGE9(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (2 * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 2, scale = -2)
    public static int testGE10(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (-2 * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -2, scale = 2)
    public static int testGE11(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (2 * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -2, scale = -2)
    public static int testGE12(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (-2 * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 1, scale = 2)
    public static int testGE13(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (2 * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 1, scale = -2)
    public static int testGE14(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (-2 * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -1, scale = 2)
    public static int testGE15(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (2 * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -1, scale = -2)
    public static int testGE16(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (-2 * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 1, scale = Integer.MAX_VALUE)
    public static int testGE17(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (Integer.MAX_VALUE * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = 1, scale = Integer.MIN_VALUE)
    public static int testGE18(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (Integer.MIN_VALUE * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -1, scale = Integer.MAX_VALUE)
    public static int testGE19(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (Integer.MAX_VALUE * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GE, stride = -1, scale = Integer.MIN_VALUE)
    public static int testGE20(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (Integer.MIN_VALUE * i + off) < limit) {
                break;
            }
        }
        return i;
    }

    // Unrolled version of testGE1
    @Test(test = BoolTest.GE, stride = 1, scale = 1)
    public static int testGE21(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 4) {
            if (i >= threshold && (i + off) < limit) {
                return i;
            }
            if (i + 1 >= threshold && ((i + 1) + off) < limit) {
                return i + 1;
            }
            if (i + 2 >= threshold && ((i + 2) + off) < limit) {
                return i + 2;
            }
            if (i + 3 >= threshold && ((i + 3) + off) < limit) {
                return i + 3;
            }
        }
        return i;
    }

    // Unrolled version of testGE2
    @Test(test = BoolTest.GE, stride = 1, scale = -1)
    public static int testGE22(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 4) {
            if (i >= threshold && (-i + off) < limit) {
                return i;
            }
            if (i + 1 >= threshold && (-(i + 1) + off) < limit) {
                return i + 1;
            }
            if (i + 2 >= threshold && (-(i + 2) + off) < limit) {
                return i + 2;
            }
            if (i + 3 >= threshold && (-(i + 3) + off) < limit) {
                return i + 3;
            }
        }
        return i;
    }

    // Unrolled version of testGE3
    @Test(test = BoolTest.GE, stride = -1, scale = 1)
    public static int testGE23(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 4) {
            if (i <= threshold && (i + off) < limit) {
                return i;
            }
            if (i - 1 <= threshold && ((i - 1) + off) < limit) {
                return i - 1;
            }
            if (i - 2 <= threshold && ((i - 2) + off) < limit) {
                return i - 2;
            }
            if (i - 3 <= threshold && ((i - 3) + off) < limit) {
                return i - 3;
            }
        }
        return i;
    }

    // Unrolled version of testGE4
    @Test(test = BoolTest.GE, stride = -1, scale = -1)
    public static int testGE24(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 4) {
            if (i <= threshold && (-i + off) < limit) {
                return i;
            }
            if (i - 1 <= threshold && (-(i - 1) + off) < limit) {
                return i - 1;
            }
            if (i - 2 <= threshold && (-(i - 2) + off) < limit) {
                return i - 2;
            }
            if (i - 3 <= threshold && (-(i - 3) + off) < limit) {
                return i - 3;
            }
        }
        return i;
    }

    // LT cases

    @Test(test = BoolTest.LT, stride = 1, scale = 1)
    public static int testLT1(int off, int limit, int threshold) {
        // Range check elimination is applied to (i + off) >= limit
        // Positive stride*scale: the affine function is increasing,
        // the pre-loop checks for underflow and the post-loop for overflow.

        // Pre-loop:
        // Execute while scale*i+off < low_limit (= -max_int)
        // i < (low_limit-off)/scale
        //
        // With scale=1, off=1
        // i < MIN(MAX(-max_int-1, -9), 10) = MIN(MAX(min_int, -9), 10) = -9 (1 iteration)
        //
        // With scale=1, off=-1
        // i < MIN(MAX(-max_int+1, -9), 10) = -9 (1 iteration)

        // Main-loop:
        // Execute while scale*i+off < limit
        // i < (limit-off)/scale
        //
        // With scale=1, off=1, limit=min_int:
        // i < MIN(-2147483648-1, 10) = MIN( **UNDERFLOW TO MAX_INT** , 10) = 10 (20 iterations)
        // -> FAIL: Main loop is executed starting with i=-9 although (-9 + 1) >= min_int!
        //
        // With scale=1, off=-1, limit=max_int:
        // i < MIN(2147483647-(-1), 10) = MIN( **OVERFLOW TO MIN_INT** , 10) = min_int (0
        // iterations)
        // -> Main loop is not executed although it could be.

        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 1, scale = -1)
    public static int testLT2(int off, int limit, int threshold) {
        // Range check elimination is applied to (-i + off) >= limit
        // Negative stride*scale: the affine function is decreasing,
        // the pre-loop checks for overflow and the post-loop for underflow.

        // Pre-loop:
        // Execute while scale*i+off >= limit
        // i < (limit-(off+1))/scale
        //
        // With scale=-1, off=3, limit=min_int:
        // i < MIN(MAX((min_int-(3+1))/-1, -9), 10) = MIN(MAX( **UNDERFLOW TO -2147483644**, -9),
        // 10) = -9 (1 iteration)
        //
        // With scale=-1, off=-3, limit=max_int:
        // i < MIN(MAX((max_int-(-3+1))/-1, -9), 10) = MIN(MAX( **UNDERFLOW TO -2147483645**, -9,
        // 10) = -9 (1 iteration)

        // Main-loop:
        // Execute while scale*i+off >= low_limit (=-max_int)
        // i < (low_limit-(off+1))/scale
        //
        // With scale=-1, off=3
        // i < MIN((-max_int-(3+1))/-1, 10) = MIN(max_int, 10) = 10 (20 iterations)
        // (positive offset is replaced by 0)
        // -> FAIL: Main loop is executed starting with i=-9 although (-(-9) + 1) >= min_int!
        //
        // With scale=-1, off=-3
        // i < MIN((-max_int-(-3+1))/-1, 10) = MIN(2147483645, 10) = 10 (20 iterations)
        // -> Main loop is executed.
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (-i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -1, scale = 1)
    public static int testLT3(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -1, scale = -1)
    public static int testLT4(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (-i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 2, scale = 1)
    public static int testLT5(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 2, scale = -1)
    public static int testLT6(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (-i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -2, scale = 1)
    public static int testLT7(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -2, scale = -1)
    public static int testLT8(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (-i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 2, scale = 2)
    public static int testLT9(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (2 * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 2, scale = -2)
    public static int testLT10(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (-2 * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -2, scale = 2)
    public static int testLT11(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (2 * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -2, scale = -2)
    public static int testLT12(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (-2 * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 1, scale = 2)
    public static int testLT13(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (2 * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 1, scale = -2)
    public static int testLT14(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (-2 * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -1, scale = 2)
    public static int testLT15(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (2 * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -1, scale = -2)
    public static int testLT16(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (-2 * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 1, scale = Integer.MAX_VALUE)
    public static int testLT17(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (Integer.MAX_VALUE * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = 1, scale = Integer.MIN_VALUE)
    public static int testLT18(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (Integer.MIN_VALUE * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -1, scale = Integer.MAX_VALUE)
    public static int testLT19(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (Integer.MAX_VALUE * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.LT, stride = -1, scale = Integer.MIN_VALUE)
    public static int testLT20(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (Integer.MIN_VALUE * i + off) >= limit) {
                break;
            }
        }
        return i;
    }

    // Unrolled version of testLT1
    @Test(test = BoolTest.LT, stride = 1, scale = 1)
    public static int testLT21(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 4) {
            if (i >= threshold && (i + off) >= limit) {
                return i;
            }
            if (i + 1 >= threshold && ((i + 1) + off) >= limit) {
                return i + 1;
            }
            if (i + 2 >= threshold && ((i + 2) + off) >= limit) {
                return i + 2;
            }
            if (i + 3 >= threshold && ((i + 3) + off) >= limit) {
                return i + 3;
            }
        }
        return i;
    }

    // Unrolled version of testLT2
    @Test(test = BoolTest.LT, stride = 1, scale = -1)
    public static int testLT22(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 4) {
            if (i >= threshold && (-i + off) >= limit) {
                return i;
            }
            if (i + 1 >= threshold && (-(i + 1) + off) >= limit) {
                return i + 1;
            }
            if (i + 2 >= threshold && (-(i + 2) + off) >= limit) {
                return i + 2;
            }
            if (i + 3 >= threshold && (-(i + 3) + off) >= limit) {
                return i + 3;
            }
        }
        return i;
    }

    // Unrolled version of testLT3
    @Test(test = BoolTest.LT, stride = -1, scale = 1)
    public static int testLT23(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 4) {
            if (i <= threshold && (i + off) >= limit) {
                return i;
            }
            if (i - 1 <= threshold && ((i - 1) + off) >= limit) {
                return i - 1;
            }
            if (i - 2 <= threshold && ((i - 2) + off) >= limit) {
                return i - 2;
            }
            if (i - 3 <= threshold && ((i - 3) + off) >= limit) {
                return i - 3;
            }
        }
        return i;
    }

    // Unrolled version of testLT4
    @Test(test = BoolTest.LT, stride = -1, scale = -1)
    public static int testLT24(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 4) {
            if (i <= threshold && (-i + off) >= limit) {
                return i;
            }
            if (i - 1 <= threshold && (-(i - 1) + off) >= limit) {
                return i - 1;
            }
            if (i - 2 <= threshold && (-(i - 2) + off) >= limit) {
                return i - 2;
            }
            if (i - 3 <= threshold && (-(i - 3) + off) >= limit) {
                return i - 3;
            }
        }
        return i;
    }

    // GT cases

    @Test(test = BoolTest.GT, stride = 1, scale = 1)
    public static int testGT1(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 1, scale = -1)
    public static int testGT2(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (-i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -1, scale = 1)
    public static int testGT3(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -1, scale = -1)
    public static int testGT4(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (-i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 2, scale = 1)
    public static int testGT5(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 2, scale = -1)
    public static int testGT6(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (-i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -2, scale = 1)
    public static int testGT7(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -2, scale = -1)
    public static int testGT8(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (-i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 2, scale = 2)
    public static int testGT9(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (2 * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 2, scale = -2)
    public static int testGT10(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 2) {
            if (i >= threshold && (-2 * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -2, scale = 2)
    public static int testGT11(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (2 * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -2, scale = -2)
    public static int testGT12(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 2) {
            if (i <= threshold && (-2 * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 1, scale = 2)
    public static int testGT13(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (2 * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 1, scale = -2)
    public static int testGT14(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (-2 * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -1, scale = 2)
    public static int testGT15(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (2 * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -1, scale = -2)
    public static int testGT16(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (-2 * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 1, scale = Integer.MAX_VALUE)
    public static int testGT17(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (Integer.MAX_VALUE * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = 1, scale = Integer.MIN_VALUE)
    public static int testGT18(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; ++i) {
            if (i >= threshold && (Integer.MIN_VALUE * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -1, scale = Integer.MAX_VALUE)
    public static int testGT19(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (Integer.MAX_VALUE * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    @Test(test = BoolTest.GT, stride = -1, scale = Integer.MIN_VALUE)
    public static int testGT20(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; --i) {
            if (i <= threshold && (Integer.MIN_VALUE * i + off) <= limit) {
                break;
            }
        }
        return i;
    }

    // Unrolled version of testGT1
    @Test(test = BoolTest.GT, stride = 1, scale = 1)
    public static int testGT21(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 4) {
            if (i >= threshold && (i + off) <= limit) {
                return i;
            }
            if (i + 1 >= threshold && ((i + 1) + off) <= limit) {
                return i + 1;
            }
            if (i + 2 >= threshold && ((i + 2) + off) <= limit) {
                return i + 2;
            }
            if (i + 3 >= threshold && ((i + 3) + off) <= limit) {
                return i + 3;
            }
        }
        return i;
    }

    // Unrolled version of testGT2
    @Test(test = BoolTest.GT, stride = 1, scale = -1)
    public static int testGT22(int off, int limit, int threshold) {
        int i = -10;
        for (; i < 10; i = i + 4) {
            if (i >= threshold && (-i + off) <= limit) {
                return i;
            }
            if (i + 1 >= threshold && (-(i + 1) + off) <= limit) {
                return i + 1;
            }
            if (i + 2 >= threshold && (-(i + 2) + off) <= limit) {
                return i + 2;
            }
            if (i + 3 >= threshold && (-(i + 3) + off) <= limit) {
                return i + 3;
            }
        }
        return i;
    }

    // Unrolled version of testGT3
    @Test(test = BoolTest.GT, stride = -1, scale = 1)
    public static int testGT23(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 4) {
            if (i <= threshold && (i + off) <= limit) {
                return i;
            }
            if (i - 1 <= threshold && ((i - 1) + off) <= limit) {
                return i - 1;
            }
            if (i - 2 <= threshold && ((i - 2) + off) <= limit) {
                return i - 2;
            }
            if (i - 3 <= threshold && ((i - 3) + off) <= limit) {
                return i - 3;
            }
        }
        return i;
    }

    // Unrolled version of testGT4
    @Test(test = BoolTest.GT, stride = -1, scale = -1)
    public static int testGT24(int off, int limit, int threshold) {
        int i = 10;
        for (; i > -10; i = i - 4) {
            if (i <= threshold && (-i + off) <= limit) {
                return i;
            }
            if (i - 1 <= threshold && (-(i - 1) + off) <= limit) {
                return i - 1;
            }
            if (i - 2 <= threshold && (-(i - 2) + off) <= limit) {
                return i - 2;
            }
            if (i - 3 <= threshold && (-(i - 3) + off) <= limit) {
                return i - 3;
            }
        }
        return i;
    }

    // Range check cases with array access
    // static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8; // Fails in some configurations, use
    // a lower value for now
    static final int MAX_ARRAY_SIZE = 1_000_000;
    static int[] array = new int[10];
    static byte[] largeArray = new byte[MAX_ARRAY_SIZE];
    static int lastArrayIdx = 0;

    @TestSimpleArrayAccess(stride = 1, scale = 2)
    public static int testSimpleArrayAccess1(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = 2 * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = -2)
    public static int testSimpleArrayAccess2(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = -2 * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = -2)
    public static int testSimpleArrayAccess3(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = -2 * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = 2)
    public static int testSimpleArrayAccess4(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = 2 * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = 1)
    public static int testSimpleArrayAccess5(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = -1)
    public static int testSimpleArrayAccess6(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = -i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = -1)
    public static int testSimpleArrayAccess7(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = -i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = 1)
    public static int testSimpleArrayAccess8(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 2, scale = 1)
    public static int testSimpleArrayAccess9(int off) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -2, scale = -1)
    public static int testSimpleArrayAccess10(int off) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = -i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 2, scale = -1)
    public static int testSimpleArrayAccess11(int off) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = -i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -2, scale = 1)
    public static int testSimpleArrayAccess12(int off) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 2, scale = 2)
    public static int testSimpleArrayAccess13(int off) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = 2 * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -2, scale = -2)
    public static int testSimpleArrayAccess14(int off) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = -2 * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 2, scale = -2)
    public static int testSimpleArrayAccess15(int off) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = -2 * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -2, scale = 2)
    public static int testSimpleArrayAccess16(int off) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = 2 * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = Integer.MAX_VALUE)
    public static int testSimpleArrayAccess17(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = Integer.MAX_VALUE * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = Integer.MIN_VALUE)
    public static int testSimpleArrayAccess18(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = Integer.MIN_VALUE * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = Integer.MAX_VALUE)
    public static int testSimpleArrayAccess19(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = Integer.MAX_VALUE * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = Integer.MIN_VALUE)
    public static int testSimpleArrayAccess20(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = Integer.MIN_VALUE * i + off;
            res += array[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    // Tests with large array

    @TestSimpleArrayAccess(stride = 1, scale = 2, largeArray = true)
    public static int testSimpleArrayAccessLarge1(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = 2 * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = -2, largeArray = true)
    public static int testSimpleArrayAccessLarge2(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = -2 * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = -2, largeArray = true)
    public static int testSimpleArrayAccessLarge3(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = -2 * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = 2, largeArray = true)
    public static int testSimpleArrayAccessLarge4(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = 2 * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = 1, largeArray = true)
    public static int testSimpleArrayAccessLarge5(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = -1, largeArray = true)
    public static int testSimpleArrayAccessLarge6(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = -i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = -1, largeArray = true)
    public static int testSimpleArrayAccessLarge7(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = -i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = 1, largeArray = true)
    public static int testSimpleArrayAccessLarge8(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 2, scale = 1, largeArray = true)
    public static int testSimpleArrayAccessLarge9(int off) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -2, scale = -1, largeArray = true)
    public static int testSimpleArrayAccessLarge10(int off) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = -i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 2, scale = -1, largeArray = true)
    public static int testSimpleArrayAccessLarge11(int off) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = -i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -2, scale = 1, largeArray = true)
    public static int testSimpleArrayAccessLarge12(int off) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 2, scale = 2, largeArray = true)
    public static int testSimpleArrayAccessLarge13(int off) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = 2 * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -2, scale = -2, largeArray = true)
    public static int testSimpleArrayAccessLarge14(int off) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = -2 * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 2, scale = -2, largeArray = true)
    public static int testSimpleArrayAccessLarge15(int off) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = -2 * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -2, scale = 2, largeArray = true)
    public static int testSimpleArrayAccessLarge16(int off) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = 2 * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = Integer.MAX_VALUE, largeArray = true)
    public static int testSimpleArrayAccessLarge17(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = Integer.MAX_VALUE * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = 1, scale = Integer.MIN_VALUE, largeArray = true)
    public static int testSimpleArrayAccessLarge18(int off) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = Integer.MIN_VALUE * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = Integer.MAX_VALUE, largeArray = true)
    public static int testSimpleArrayAccessLarge19(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = Integer.MAX_VALUE * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    @TestSimpleArrayAccess(stride = -1, scale = Integer.MIN_VALUE, largeArray = true)
    public static int testSimpleArrayAccessLarge20(int off) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = Integer.MIN_VALUE * i + off;
            res += largeArray[idx];
            lastArrayIdx = idx;
        }
        return res;
    }

    // Cases with multiple checks

    @TestArrayAccess(stride = 1, scale = 2)
    public static int testArrayAccess1(int off, int threshold) {
        // Pre-loop limit = (0-off)/2
        // With off=-3: limit = 3/2 = 1.5 but integer divison returns 1
        // leading to an unchecked array access at idx = 2*1 - 3 = -1 in the main loop.
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = 2 * i + off;
            if (i >= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = -2)
    public static int testArrayAccess2(int off, int threshold) {
        // Pre-loop limit = (0-off)/-2
        // With off=-3: limit = 3/-2 = -1.5 but integer divison returns -1
        // leading to an unchecked array access at idx = -2*-1 - 3 = -1 in the main loop.
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = -2 * i + off;
            if (i <= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = -2)
    public static int testArrayAccess3(int off, int threshold) {
        // Pre-loop limit = (range-(off+1))/-2
        // With range=10 and off=12: limit = -3/-2 = 1.5 but integer divison returns 1
        // leading to an unchecked array access at idx = -2*1 + 12 = 10 in the main loop.
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = -2 * i + off;
            if (i >= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = 2)
    public static int testArrayAccess4(int off, int threshold) {
        // Pre-loop limit = (range-(off+1))/2
        // With range=10 and off=12: limit = -3/2 = -1.5 but integer divison returns -1
        // leading to an unchecked array access at idx = 2*-1 + 12 = 10 in the main loop.
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = 2 * i + off;
            if (i <= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = 1)
    public static int testArrayAccess5(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = i + off;
            if (i >= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = -1)
    public static int testArrayAccess6(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = -i + off;
            if (i <= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = -1)
    public static int testArrayAccess7(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = -i + off;
            if (i >= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = 1)
    public static int testArrayAccess8(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = i + off;
            if (i <= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 2, scale = 1)
    public static int testArrayAccess9(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = i + off;
            if (i >= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -2, scale = -1)
    public static int testArrayAccess10(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = -i + off;
            if (i <= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 2, scale = -1)
    public static int testArrayAccess11(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = -i + off;
            if (i >= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -2, scale = 1)
    public static int testArrayAccess12(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = i + off;
            if (i <= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 2, scale = 2)
    public static int testArrayAccess13(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = 2 * i + off;
            if (i >= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -2, scale = -2)
    public static int testArrayAccess14(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = -2 * i + off;
            if (i <= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 2, scale = -2)
    public static int testArrayAccess15(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = -2 * i + off;
            if (i >= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -2, scale = 2)
    public static int testArrayAccess16(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = 2 * i + off;
            if (i <= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = Integer.MAX_VALUE)
    public static int testArrayAccess17(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = Integer.MAX_VALUE * i + off;
            if (i >= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = Integer.MIN_VALUE)
    public static int testArrayAccess18(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = Integer.MIN_VALUE * i + off;
            if (i >= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = Integer.MAX_VALUE)
    public static int testArrayAccess19(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = Integer.MAX_VALUE * i + off;
            if (i <= threshold && idx >= array.length) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = Integer.MIN_VALUE)
    public static int testArrayAccess20(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = Integer.MIN_VALUE * i + off;
            if (i <= threshold && idx < 0) {
                res += array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    // Tests with large array

    @TestArrayAccess(stride = 1, scale = 2, largeArray = true)
    public static int testArrayAccessLarge1(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = 2 * i + off;
            if (i >= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = -2, largeArray = true)
    public static int testArrayAccessLarge2(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = -2 * i + off;
            if (i <= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = -2, largeArray = true)
    public static int testArrayAccessLarge3(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = -2 * i + off;
            if (i >= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = 2, largeArray = true)
    public static int testArrayAccessLarge4(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = 2 * i + off;
            if (i <= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = 1, largeArray = true)
    public static int testArrayAccessLarge5(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = i + off;
            if (i >= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = -1, largeArray = true)
    public static int testArrayAccessLarge6(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = -i + off;
            if (i <= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = -1, largeArray = true)
    public static int testArrayAccessLarge7(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = -i + off;
            if (i >= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = 1, largeArray = true)
    public static int testArrayAccessLarge8(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = i + off;
            if (i <= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 2, scale = 1, largeArray = true)
    public static int testArrayAccessLarge9(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = i + off;
            if (i >= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -2, scale = -1, largeArray = true)
    public static int testArrayAccessLarge10(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = -i + off;
            if (i <= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 2, scale = -1, largeArray = true)
    public static int testArrayAccessLarge11(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = -i + off;
            if (i >= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -2, scale = 1, largeArray = true)
    public static int testArrayAccessLarge12(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = i + off;
            if (i <= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 2, scale = 2, largeArray = true)
    public static int testArrayAccessLarge13(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = 2 * i + off;
            if (i >= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -2, scale = -2, largeArray = true)
    public static int testArrayAccessLarge14(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = -2 * i + off;
            if (i <= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 2, scale = -2, largeArray = true)
    public static int testArrayAccessLarge15(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; i = i + 2) {
            int idx = -2 * i + off;
            if (i >= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -2, scale = 2, largeArray = true)
    public static int testArrayAccessLarge16(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; i = i - 2) {
            int idx = 2 * i + off;
            if (i <= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = Integer.MAX_VALUE, largeArray = true)
    public static int testArrayAccessLarge17(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = Integer.MAX_VALUE * i + off;
            if (i >= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = 1, scale = Integer.MIN_VALUE, largeArray = true)
    public static int testArrayAccessLarge18(int off, int threshold) {
        int res = 0;
        for (int i = -10; i < 10; ++i) {
            int idx = Integer.MIN_VALUE * i + off;
            if (i >= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = Integer.MAX_VALUE, largeArray = true)
    public static int testArrayAccessLarge19(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = Integer.MAX_VALUE * i + off;
            if (i <= threshold && idx >= largeArray.length) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    @TestArrayAccess(stride = -1, scale = Integer.MIN_VALUE, largeArray = true)
    public static int testArrayAccessLarge20(int off, int threshold) {
        int res = 0;
        for (int i = 10; i > -10; --i) {
            int idx = Integer.MIN_VALUE * i + off;
            if (i <= threshold && idx < 0) {
                res += largeArray[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    // Original test case provided by an external reporter
    static int s1;
    static int s2;
    static int s3;

    public static int testOriginal() {
        int var1 = 429455733 - s2;
        s2 = ~var1;
        s3 = s1 - var1;
        int i1 = 0;
        while ((i1++ < 10) && ((s2 <= s1) || (s2 <= s3))) {
            var1 *= s1 + (--s2);
            s1 ^= s1 + var1;
        }
        return 0;
    }

    public static int getExpectedResult(BiPredicate<Integer, Integer> p, int stride, int scale, int off, int limit, int threshold) {

        int i = 0;
        if (stride > 0) {
            for (i = -10; i < 10; i = i + stride) {
                if (i >= threshold && p.test((scale * i + off), limit)) {
                    break;
                }
            }
        } else {
            for (i = 10; i > -10; i = i + stride) {
                if (i <= threshold && p.test((scale * i + off), limit)) {
                    break;
                }
            }
        }
        return i;
    }

    public static int getSimpleArrayAccessResult(int stride, int scale, int off, boolean useLargeArray) {
        int res = 0;
        if (stride > 0) {
            for (int i = -10; i < 10; i = i + stride) {
                int idx = scale * i + off;
                res += useLargeArray ? largeArray[idx] : array[idx];
                lastArrayIdx = idx;
            }
        } else {
            for (int i = 10; i > -10; i = i + stride) {
                int idx = scale * i + off;
                res += useLargeArray ? largeArray[idx] : array[idx];
                lastArrayIdx = idx;
            }
        }
        return res;
    }

    public static int getArrayAccessResult(int stride, int scale, int off, int threshold, boolean useLargeArray) {
        int res = 0;
        if (stride > 0) {
            for (int i = -10; i < 10; i = i + stride) {
                int idx = scale * i + off;
                if (i >= threshold && ((scale < 0) ? (idx >= (useLargeArray ? largeArray.length : array.length)) : (idx < 0))) {
                    res += useLargeArray ? largeArray[idx] : array[idx];
                    lastArrayIdx = idx;
                }
            }
        } else {
            for (int i = 10; i > -10; i = i + stride) {
                int idx = scale * i + off;
                if (i <= threshold && ((scale > 0) ? (idx >= (useLargeArray ? largeArray.length : array.length)) : (idx < 0))) {
                    res += useLargeArray ? largeArray[idx] : array[idx];
                    lastArrayIdx = idx;
                }
            }
        }
        return res;
    }

    @BeforeClass
    public static void beforeClass() {
        // Original test case provided by an external reporter
        for (int i = 0; i < 100_000; ++i) {
            testOriginal();
        }
    }

    @Before
    public void beforeTest() {
        /*
         * Resetting all profiles here as god as we can to try to get a fresh start.
         */
        Method[] resultMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(x -> x.getName().equals("testOriginalCompile")).toArray(Method[]::new);
        Method[] testMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(m -> m.isAnnotationPresent(Test.class)).toArray(Method[]::new);
        Method[] testSimpleArrayAccessMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(m -> m.isAnnotationPresent(TestSimpleArrayAccess.class)).toArray(
                        Method[]::new);
        Method[] testArrayAccessMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(m -> m.isAnnotationPresent(TestArrayAccess.class)).toArray(Method[]::new);

        reprofile(resultMethods);
        reprofile(testMethods);
        reprofile(testSimpleArrayAccessMethods);
        reprofile(testArrayAccessMethods);
    }

    private void reprofile(Method[] resultMethods) {
        for (Method m : resultMethods) {
            asResolvedJavaMethod(m).reprofile();
        }
    }

    static int s1Compile;
    static int s2Compile;
    static int s3Compile;

    public static int testOriginalCompile() {
        int var1 = 429455733 - s2Compile;
        s2Compile = ~var1;
        s3Compile = s1Compile - var1;
        int i1 = 0;
        while ((i1++ < 10) && ((s2Compile <= s1Compile) || (s2Compile <= s3Compile))) {
            var1 *= s1Compile + (--s2Compile);
            s1Compile ^= s1Compile + var1;
        }
        return 0;
    }

    @org.junit.Test
    public void testOriginalTest() throws InvalidInstalledCodeException {
        for (int i = 0; i < 100_000; ++i) {
            testOriginalCompile();
        }
        original(getInitialOptions());
        original(getALotOptions());
        original(getNoVect());
        original(getLoopOpt());
        original(getOptionsNoFullUnroll());
        original(getOptionsNoFullUnrolLAllLoop());
    }

    private void original(OptionValues opt) throws InvalidInstalledCodeException {
        Method[] resultMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(x -> x.getName().equals("testOriginalCompile")).toArray(Method[]::new);
        assert resultMethods.length == 1;
        InstalledCode ic = getCode(asResolvedJavaMethod(resultMethods[0]), null, true, false, opt);

        s1Compile = 0;
        s2Compile = 0;
        s3Compile = 0;
        for (int i = 0; i < 100_000; ++i) {
            ic.executeVarargs();
        }

        Assert.assertEquals(s1, s1Compile);
        Assert.assertEquals(s2, s2Compile);
        Assert.assertEquals(s3, s3Compile);

        Assert.assertEquals(s1 + s2 + s3, 298460337);
        Assert.assertEquals(s1Compile + s2Compile + s3Compile, 298460337);
    }

    OptionValues getALotOptions() {
        OptionValues optALot = new OptionValues(getInitialOptions(), AggressivePartialUnrollPhase.Options.ForceUnroll, true, DuplicationOptions.DuplicateALot, true,
                        VectorLoopUtility.Options.RespectVectorization, false);
        return optALot;
    }

    OptionValues getNoVect() {
        OptionValues optALot = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false);
        return optALot;
    }

    OptionValues getLoopOpt() {
        OptionValues optALot = new OptionValues(getInitialOptions(), LoopPolicies.Options.PeelALot, true);
        return optALot;
    }

    OptionValues getOptionsNoFullUnroll() {
        OptionValues optALot = new OptionValues(getInitialOptions(), GraalOptions.FullUnroll, false);
        return optALot;
    }

    OptionValues getOptionsNoFullUnrolLAllLoop() {
        OptionValues optALot = new OptionValues(getOptionsNoFullUnroll(), AggressivePartialUnrollPhase.Options.ForceUnroll, true, DuplicationOptions.DuplicateALot, true,
                        VectorLoopUtility.Options.RespectVectorization, false);
        return optALot;
    }

    @org.junit.Test
    public void runTestMethod0() throws IllegalAccessException, InvocationTargetException {
        runTestMethodsBody(getInitialOptions());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestMethod1() throws IllegalAccessException, InvocationTargetException {
        runTestMethodsBody(getALotOptions());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestMethod2() throws IllegalAccessException, InvocationTargetException {
        runTestMethodsBody(getNoVect());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestMethod3() throws IllegalAccessException, InvocationTargetException {
        runTestMethodsBody(getLoopOpt());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestMethod4() throws IllegalAccessException, InvocationTargetException {
        runTestMethodsBody(getOptionsNoFullUnroll());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestMethod5() throws IllegalAccessException, InvocationTargetException {
        runTestMethodsBody(getOptionsNoFullUnrolLAllLoop());
    }

    private void runTestMethodsBody(OptionValues opt) throws IllegalAccessException, InvocationTargetException {
        // Compute test values for offset and limit
        ArrayList<Integer> values = new ArrayList<>();
        ArrayList<Integer> thresholds = new ArrayList<>();
        Random random = getRandomInstance();
        for (int i = MIN_BOUNDS; i <= MAX_BOUNDS; ++i) {
            thresholds.add(i);
            values.add(i);
            // Add some values around min and max int
            int value = Integer.MAX_VALUE + i;
            values.add(value);
            // Add some randomness
            values.add(random.nextInt());
        }
        // Get test methods from corresponding annotation
        Method[] testMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(m -> m.isAnnotationPresent(Test.class)).toArray(Method[]::new);
        Method[] resultMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(x -> x.getName().startsWith("get") && x.getName().endsWith("Result")).toArray(
                        Method[]::new);

        // first run it, get profiles, C2 can compile it we dont care

        int warmupIterations = 2;
        testTestMethods(values, thresholds, testMethods, warmupIterations, null, null);

        // now force compile all of them, and run methods
        Map<Method, CPair> testMethodsICs = compile(opt, testMethods);
        Map<Method, CPair> checkMethodsICs = compile(opt, resultMethods);

        // now run them with our code

        class CheckMethodsAndReCompile implements Checker {

            @SuppressWarnings("unchecked")
            @Override
            public void check(Method m) {
                for (Map<Method, CPair> codes : Arrays.asList(testMethodsICs, checkMethodsICs)) {
                    if (codes.containsKey(m)) {
                        CPair pair = codes.get(m);
                        // always recompile deopted check methods
                        if ((m.equals(pair.method) || codes == checkMethodsICs) && !pair.ic.isValid()) {
                            if (LOG) {
                                TTY.printf("%s was deoptimized, recompiling...%n", pair.ic);
                            }
                            pair.ic = getCode(asResolvedJavaMethod(pair.method), null, true, true, opt);
                        }
                    }
                }
            }
        }

        testTestMethods(values, thresholds, testMethods, warmupIterations, new CheckMethodsAndReCompile(), testMethodsICs);

        if ((s1 + s2 + s3) != 298460337) {
            throw new RuntimeException("Original test failed: " + (s1 + s2 + s3));
        }
    }

    @org.junit.Test
    public void runTestSimpleArray0() {
        runSimpleArrayTestsBody(getInitialOptions());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestSimpleArray1() {
        runSimpleArrayTestsBody(getALotOptions());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestSimpleArray2() {
        runSimpleArrayTestsBody(getNoVect());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestSimpleArray3() {
        runSimpleArrayTestsBody(getLoopOpt());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestSimpleArray4() {
        runSimpleArrayTestsBody(getOptionsNoFullUnroll());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestSimpleArray5() {
        runSimpleArrayTestsBody(getOptionsNoFullUnrolLAllLoop());
    }

    private void runSimpleArrayTestsBody(OptionValues opt) {
        // Compute test values for offset and limit
        ArrayList<Integer> values = new ArrayList<>();
        ArrayList<Integer> thresholds = new ArrayList<>();
        Random random = getRandomInstance();
        for (int i = MIN_BOUNDS; i <= MAX_BOUNDS; ++i) {
            thresholds.add(i);
            values.add(i);
            // Add some values around min and max int
            int value = Integer.MAX_VALUE + i;
            values.add(value);
            // Add some randomness
            values.add(random.nextInt());
        }
        // Get test methods from corresponding annotation
        Method[] testSimpleArrayAccessMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(m -> m.isAnnotationPresent(TestSimpleArrayAccess.class)).toArray(
                        Method[]::new);
        Method[] resultMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(x -> x.getName().startsWith("get") && x.getName().endsWith("Result")).toArray(
                        Method[]::new);

        // first run it, get profiles, C2 can compile it we dont care

        int warmupIterations = 200;
        testSimpleArrayAccessMethods(values, testSimpleArrayAccessMethods, warmupIterations, null, null);

        // now force compile all of them, and run methods
        Map<Method, CPair> testSimpleArrayAccessMethodsICs = compile(opt, testSimpleArrayAccessMethods);
        Map<Method, CPair> checkMethodsICs = compile(opt, resultMethods);

        // now run them with our code
        class CheckMethodsAndReCompile implements Checker {

            @SuppressWarnings("unchecked")
            @Override
            public void check(Method m) {
                for (Map<Method, CPair> codes : Arrays.asList(testSimpleArrayAccessMethodsICs, checkMethodsICs)) {
                    if (codes.containsKey(m)) {
                        CPair pair = codes.get(m);
                        // always recompile deopted check methods
                        if ((m.equals(pair.method) || codes == checkMethodsICs) && !pair.ic.isValid()) {
                            if (LOG) {
                                TTY.printf("%s was deoptimized, recompiling...%n", pair.ic);
                            }
                            pair.ic = getCode(asResolvedJavaMethod(pair.method), null, true, true, opt);
                        }
                    }
                }
            }
        }
        testSimpleArrayAccessMethods(values, testSimpleArrayAccessMethods, warmupIterations, new CheckMethodsAndReCompile(), testSimpleArrayAccessMethodsICs);
        if ((s1 + s2 + s3) != 298460337) {
            throw new RuntimeException("Original test failed: " + (s1 + s2 + s3));
        }
    }

    @org.junit.Test
    public void runTestArrayAccess0() {
        reunTestArrayAccessBody(getInitialOptions());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestArrayAccess1() {
        reunTestArrayAccessBody(getALotOptions());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestArrayAccess2() {
        reunTestArrayAccessBody(getNoVect());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestArrayAccess3() {
        reunTestArrayAccessBody(getLoopOpt());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestArrayAccess4() {
        reunTestArrayAccessBody(getOptionsNoFullUnroll());
    }

    @org.junit.Test
    @Ignore("GR-54338:Can be ran on demand if necessary")
    public void runTestArrayAccess5() {
        reunTestArrayAccessBody(getOptionsNoFullUnrolLAllLoop());
    }

    private void reunTestArrayAccessBody(OptionValues opt) {
        // Compute test values for offset and limit
        ArrayList<Integer> values = new ArrayList<>();
        ArrayList<Integer> thresholds = new ArrayList<>();
        Random random = getRandomInstance();
        for (int i = MIN_BOUNDS; i <= MAX_BOUNDS; ++i) {
            thresholds.add(i);
            values.add(i);
            // Add some values around min and max int
            int value = Integer.MAX_VALUE + i;
            values.add(value);
            // Add some randomness
            values.add(random.nextInt());
        }
        // Get test methods from corresponding annotation
        Method[] testArrayAccessMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(m -> m.isAnnotationPresent(TestArrayAccess.class)).toArray(Method[]::new);
        Method[] resultMethods = Arrays.stream(RangeCheckEliminationTest.class.getMethods()).filter(x -> x.getName().startsWith("get") && x.getName().endsWith("Result")).toArray(
                        Method[]::new);

        // first run it, get profiles, C2 can compile it we dont care
        testArrayAccessMethods(values, thresholds, testArrayAccessMethods, null, null);

        // now force compile all of them, and run methods
        Map<Method, CPair> testArrayAccessMethodsICs = compile(opt, testArrayAccessMethods);
        Map<Method, CPair> checkMethodsICs = compile(opt, resultMethods);

        // now run them with our code
        class CheckMethodsAndReCompile implements Checker {

            @SuppressWarnings("unchecked")
            @Override
            public void check(Method m) {
                for (Map<Method, CPair> codes : Arrays.asList(testArrayAccessMethodsICs, checkMethodsICs)) {
                    if (codes.containsKey(m)) {
                        CPair pair = codes.get(m);
                        // always recompile deopted check methods
                        if ((m.equals(pair.method) || codes == checkMethodsICs) && !pair.ic.isValid()) {
                            if (LOG) {
                                TTY.printf("%s was deoptimized, recompiling...%n", pair.ic);
                            }
                            pair.ic = getCode(asResolvedJavaMethod(pair.method), null, true, true, opt);
                        }
                    }
                }
            }
        }

        testArrayAccessMethods(values, thresholds, testArrayAccessMethods, new CheckMethodsAndReCompile(), testArrayAccessMethodsICs);

        if ((s1 + s2 + s3) != 298460337) {
            throw new RuntimeException("Original test failed: " + (s1 + s2 + s3));
        }
    }

    interface Checker {
        void check(Method m);
    }

    static class CPair {
        InstalledCode ic;
        Method method;

        CPair(InstalledCode ic, Method method) {
            this.ic = ic;
            this.method = method;
        }
    }

    private Map<Method, CPair> compile(OptionValues opt, Method... testMethods) {
        Map<Method, CPair> codes = new EconomicHashMap<>();
        for (int i = 0; i < testMethods.length; i++) {
            Method m = testMethods[i];
            codes.put(m, new CPair(getCode(asResolvedJavaMethod(m), null, true, true, opt), m));
        }
        return codes;
    }

    private static void testArrayAccessMethods(ArrayList<Integer> values, ArrayList<Integer> thresholds, Method[] testArrayAccessMethods, Checker checker, Map<Method, CPair> compiledCode) {
        int warmupIterations;
        warmupIterations = 10;
        for (int i = 0; i <= warmupIterations; ++i) {
            for (Method m : testArrayAccessMethods) {
                TestArrayAccess t = m.getAnnotation(TestArrayAccess.class);
                for (int off : values) {
                    for (int threshold : thresholds) {
                        int result = 0;
                        boolean exception = false;
                        lastArrayIdx = -42;
                        try {
                            if (compiledCode != null && compiledCode.containsKey(m)) {
                                try {
                                    checker.check(m);
                                    result = (int) compiledCode.get(m).ic.executeVarargs(off, threshold);
                                } catch (InvalidInstalledCodeException e) {
                                    throw GraalError.shouldNotReachHere(e);
                                }
                            } else {
                                result = (int) m.invoke(null, off, threshold);
                            }
                        } catch (Exception e) {
                            exception = true;
                        }
                        int savedLastArrayIdx = lastArrayIdx;
                        if (i == warmupIterations || compiledCode != null/*
                                                                          * with graal always check
                                                                          */) {
                            // Warmup is over, check results
                            int expResult = 0;
                            boolean expException = false;
                            lastArrayIdx = -42;
                            try {
                                expResult = getArrayAccessResult(t.stride(), t.scale(), off, threshold, t.largeArray());
                            } catch (Exception e) {
                                expException = true;
                            }
                            assert expException == exception || expResult == result || lastArrayIdx == savedLastArrayIdx : m + " failed with off=" + off + ", threshold= " + threshold +
                                            ". Expected exception=" + expException + ", result=" + expResult + ", lastArrayIdx=" +
                                            lastArrayIdx +
                                            " but got exception=" + exception + ", result=" + result + ", lastArrayIdx=" + savedLastArrayIdx + ".";
                        }
                    }
                }
            }
        }
    }

    private static void testSimpleArrayAccessMethods(ArrayList<Integer> values, Method[] testSimpleArrayAccessMethods, int warmupIterations, Checker checker, Map<Method, CPair> compiledCode) {

        for (int i = 0; i <= warmupIterations; ++i) {
            for (Method m : testSimpleArrayAccessMethods) {
                TestSimpleArrayAccess t = m.getAnnotation(TestSimpleArrayAccess.class);
                for (int off : values) {
                    int result = 0;
                    boolean exception = false;
                    lastArrayIdx = -42;
                    try {
                        if (compiledCode != null && compiledCode.containsKey(m)) {
                            try {
                                checker.check(m);
                                result = (int) compiledCode.get(m).ic.executeVarargs(off);
                            } catch (InvalidInstalledCodeException e) {
                                throw GraalError.shouldNotReachHere(e);
                            }
                        } else {
                            result = (int) m.invoke(null, off);
                        }
                    } catch (Exception e) {
                        exception = true;
                    }
                    int savedLastArrayIdx = lastArrayIdx;
                    if (i == warmupIterations || compiledCode != null/*
                                                                      * with graal always check
                                                                      */) {
                        // Warmup is over, check results
                        int expResult = 0;
                        boolean expException = false;
                        lastArrayIdx = -42;
                        try {
                            expResult = getSimpleArrayAccessResult(t.stride(), t.scale(), off, t.largeArray());
                        } catch (Exception e) {
                            expException = true;
                        }
                        assert expException == exception || expResult == result || lastArrayIdx == savedLastArrayIdx : m + " failed with off=" + off + ". Expected exception=" + expException +
                                        ", result=" + expResult + ", lastArrayIdx=" + lastArrayIdx +
                                        " but got exception=" + exception + ", result=" + result + ", lastArrayIdx=" + savedLastArrayIdx + ".";
                    }
                }
            }
        }
    }

    private static void testTestMethods(ArrayList<Integer> values, ArrayList<Integer> thresholds, Method[] testMethods, int warmupIterations, Checker checker, Map<Method, CPair> compiledCode)
                    throws IllegalAccessException, InvocationTargetException {
        for (int i = 0; i <= warmupIterations; ++i) {
            for (Method m : testMethods) {
                Test t = m.getAnnotation(Test.class);
                for (int off : values) {
                    for (int limit : values) {
                        for (int threshold : thresholds) {
                            int result = 0;
                            if (compiledCode != null && compiledCode.containsKey(m)) {
                                try {
                                    checker.check(m);
                                    result = (int) compiledCode.get(m).ic.executeVarargs(off, limit, threshold);
                                } catch (InvalidInstalledCodeException e) {
                                    throw GraalError.shouldNotReachHere(e);
                                }
                            } else {
                                result = (int) m.invoke(null, off, limit, threshold);
                            }
                            if (i == warmupIterations || compiledCode != null/*
                                                                              * with graal always
                                                                              * check
                                                                              */) {
                                // Warmup is over, check results
                                int exp = getExpectedResult(t.test().toBiPredicate(), t.stride(), t.scale(), off, limit, threshold);
                                assert result == exp : m + " failed with off=" + off + ", limit=" + limit + ", threshold=" + threshold + ". Expected " + exp + " but got " + result + ".";
                            }
                        }
                    }
                }
            }
        }
    }

}
