/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.ea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;
import jdk.vm.ci.code.InstalledCode;

public class FinalReadEliminationTest extends GraalCompilerTest {

    static class A {
        final int x;

        A(int x) {
            this.x = x;
        }
    }

    static volatile int accross;

    static int S;

    public static int snippetAccessVolatile1(A a) {
        int load1 = a.x;
        S = accross;
        int load2 = a.x;
        return load1 + load2;
    }

    @Test
    public void testAccrossVolatile01() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("snippetAccessVolatile1"), AllowAssumptions.NO);
        createCanonicalizerPhase().apply(g, getDefaultHighTierContext());
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(g, getDefaultHighTierContext());
        Assert.assertEquals(2, g.getNodes().filter(LoadFieldNode.class).filter(x -> !((LoadFieldNode) x).field().isVolatile()).count());
    }

    public static int snippetAccessVolatile2(A a) {
        int load1 = a.x;
        accross = load1;
        int load2 = a.x;
        return load1 + load2;
    }

    @Test
    public void testAccrossVolatile02() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("snippetAccessVolatile2"), AllowAssumptions.NO);
        createCanonicalizerPhase().apply(g, getDefaultHighTierContext());
        new ReadEliminationPhase(createCanonicalizerPhase()).apply(g, getDefaultHighTierContext());
        Assert.assertEquals(2, g.getNodes().filter(LoadFieldNode.class).filter(x -> !((LoadFieldNode) x).field().isVolatile()).count());
    }

    // Checkstyle: stop
    public static final boolean LOG = false;

    static class ThisEscaper {
        final int x;
        static volatile ThisEscaper volatileField1;

        ThisEscaper() {
            volatileField1 = this;
            while (!flag1) {
            }
            this.x = 42;
            flag = true;
        }
    }

    static volatile boolean flag;

    static volatile boolean flag1;

    // compile this method
    static void readerAction() {
        if (GraalDirectives.inCompiledCode()) {
            if (LOG) {
                GraalDirectives.log("Executing compiled code\n");
            }
        }
        while (ThisEscaper.volatileField1 == null) {
        }
        ThisEscaper localA = ThisEscaper.volatileField1;
        int t1 = localA.x; // = 0
        while (!flag) {
            /* loop until it changed */
        }
        int t2 = localA.x; // = 42

        if (t1 == t2) {
            throw new IllegalArgumentException("must be different values but is t1=" + t1 + " t2=" + t2);
        }
        if (t1 != 0 || t2 != 42) {
            throw new IllegalArgumentException("whaat t1=" + t1 + " t2=" + t2);
        }
        if (LOG) {
            System.out.printf("t1=%s t2=%s%n", t1, t2);
        }
    }

    static Runnable readerRunnable = new Runnable() {

        @Override
        public void run() {
            readerAction();
        }
    };
    static Runnable setRunnable = new Runnable() {

        @Override
        public void run() {
            try {
                if (LOG) {
                    System.out.println("Starting sleeping before setting flag1");
                }
                Thread.sleep(1000 * 2);
                if (LOG) {
                    System.out.println("Done sleeping before setting flag1");
                }
            } catch (InterruptedException e) {
                throw GraalError.shouldNotReachHere(e);
            }
            flag1 = true;
            if (LOG) {
                System.out.println("Done setting flag1");
            }
        }
    };

    @SuppressWarnings("unused")
    @Test
    public void testThreadsThisEscape() throws InterruptedException {
        // first do all compiles
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.OptConvertDeoptsToGuards, false);
        InstalledCode ic = getCode(getResolvedJavaMethod("readerAction"), null, true, true, opt);
        assert ic.isAlive();
        assert ic.isValid();

        // then run the program
        Thread readerThread = new Thread(readerRunnable);
        Thread setThread = new Thread(setRunnable);

        List<Throwable> t = Collections.synchronizedList(new ArrayList<Throwable>());

        Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread th, Throwable ex) {
                t.add(ex);
            }
        };

        readerThread.setUncaughtExceptionHandler(h);
        setThread.setUncaughtExceptionHandler(h);

        readerThread.start();
        setThread.start();

        // start them then allocate
        new ThisEscaper();

        readerThread.join();
        setThread.join();

        for (Throwable throwable : t) {
            throw GraalError.shouldNotReachHere(throwable);
        }
    }
    // Checkstyle: resume
}
