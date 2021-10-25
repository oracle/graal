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
package micro.benchmarks;

import java.lang.reflect.Array;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

public class TypecheckBenchmark extends BenchmarkBase {

    static class A {

    }

    static class B {

    }

    static class C {

    }

    static class AA {

    }

    static class AA1 extends AA {

    }

    static class AA2 extends AA1 {

    }

    static class BB {

    }

    static class BB1 extends BB {

    }

    static class BB2 extends BB1 {

    }

    static class CC {

    }

    static class CC1 extends CC {

    }

    static class CC2 extends CC1 {

    }

    @State(Scope.Benchmark)
    public static class ThreadState {

        private static final int N = 100000;

        final Random r = new Random(17);

        final C c = new C();
        final B b = new B();
        final A a = new A();
        final CC2 cc2 = new CC2();
        final CC1 cc1 = new CC1();
        final CC cc = new CC();
        final BB2 bb2 = new BB2();
        final BB1 bb1 = new BB1();
        final BB bb = new BB();
        final AA2 aa2 = new AA2();
        final AA1 aa1 = new AA1();
        final AA aa = new AA();
        Random r1 = new Random(31);

        int magic = 14;

        Object[] aas = createAAs();
        Class<?>[] classes = createClasses(N);
        Object[] object = createObjects(N);

        private Object[] createAAs() {
            Object[] source = new Object[N];
            for (int i = 0; i < N; i++) {
                if (i == magic) {
                    source[i] = new CC();
                }
                int rand = r.nextInt(3);
                if (rand <= 1) {
                    source[i] = new AA2();
                } else {
                    source[i] = new AA1();
                }
            }
            return source;
        }

        private Class<?>[] createClasses(int n2) {
            Class<?>[] ccs = new Class<?>[n2];
            for (int i = 0; i < N; i++) {
                ccs[i] = nextClass();
            }
            return ccs;
        }

        private Object[] createObjects(int n2) {
            Object[] o = new Object[n2];
            for (int i = 0; i < N; i++) {
                o[i] = nextElement();
            }
            return o;
        }

        private Object nextElement() {
            int rand = r.nextInt(12);

            if (rand == 10) {
                return aa2;
            } else if (rand == 11) {
                return bb2;
            } else if (rand == 12) {
                return cc2;
            } else if (rand == 9) {
                return c;
            } else if (rand == 8) {
                return b;
            } else if (rand == 7) {
                return a;
            } else if (rand == 6) {
                return cc1;
            } else if (rand == 5) {
                return cc;
            } else if (rand == 4) {
                return bb1;
            } else if (rand == 3) {
                return bb;
            } else if (rand == 2) {
                return aa1;
            }
            return aa;
        }

        private Class<?> nextClass() {
            int rand = r1.nextInt(10);

            if (rand == 9) {
                return C.class;
            } else if (rand == 8) {
                return B.class;
            } else if (rand == 7) {
                return A.class;
            } else if (rand == 6) {
                return CC1.class;
            } else if (rand == 5) {
                return CC.class;
            } else if (rand == 4) {
                return BB1.class;
            } else if (rand == 3) {
                return BB.class;
            } else if (rand == 2) {
                return AA1.class;
            }
            return AA.class;
        }
    }

    @Benchmark
    public void benchArrayStore(ThreadState state) {
        Object[] target = (Object[]) Array.newInstance(state.aas[0].getClass().getSuperclass(), state.aas.length);
        for (int i = 0; i < state.aas.length; i++) {
            if (i == state.magic) {
                continue;
            }
            target[i] = state.aas[i];
        }
    }

    @Benchmark
    public int repetitiveTypeCheckExact(ThreadState state) {
        int res = 0;
        for (int i = 0; i < state.object.length; i++) {
            Object o = state.object[i];
            if (o instanceof A) {
                res++;
            } else if (o instanceof B) {
                res += 2;
            } else if (o instanceof C) {
                res += 4;
            }
        }
        return res;
    }

    @Benchmark
    public int repetitiveTypeCheckSubclasses(ThreadState state) {
        int res = 0;
        for (int i = 0; i < state.object.length; i++) {
            Object o = state.object[i];
            if (o == null) {
                return 0;
            }
            if (o instanceof AA1) {
                res += 7;
            } else if (o instanceof BB1) {
                res += 5;
            } else if (o instanceof CC1) {
                res += 3;
            } else if (o instanceof AA) {
                res++;
            } else if (o instanceof BB) {
                res += 2;
            } else if (o instanceof CC) {
                res += 4;
            }
        }
        return res;
    }

    @Benchmark
    public int repetitiveClassIsAssignable(ThreadState state) {
        int res = 0;
        for (int i = 0; i < state.object.length; i++) {
            Object o = state.object[i];
            if (o == null) {
                return 0;
            }
            if (AA1.class.isAssignableFrom(o.getClass())) {
                res += 7;
            } else if (BB1.class.isAssignableFrom(o.getClass())) {
                res += 3;
            } else if (CC1.class.isAssignableFrom(o.getClass())) {
                res += 5;
            } else if (AA.class.isAssignableFrom(o.getClass())) {
                res++;
            } else if (BB.class.isAssignableFrom(o.getClass())) {
                res += 2;
            } else if (CC.class.isAssignableFrom(o.getClass())) {
                res += 4;
            }
        }
        return res;
    }

    @Benchmark
    public int repetitiveInstanceOfDynamic(ThreadState state) {
        int res = 0;
        for (int i = 0; i < state.object.length / 8; i += 8) {
            Object o1 = state.object[i];
            Object o2 = state.object[i + 1];
            Object o3 = state.object[i + 2];
            Object o4 = state.object[i + 3];
            Object o5 = state.object[i + 4];
            Object o6 = state.object[i + 5];
            Object o7 = state.object[i + 6];
            Object o8 = state.object[i + 7];
            Class<?> c = state.classes[i];

            if (c.isInstance(o1)) {
                res++;
            } else if (c.isInstance(o2)) {
                res++;
            } else if (c.isInstance(o3)) {
                res++;
            } else if (c.isInstance(o4)) {
                res++;
            } else if (c.isInstance(o5)) {
                res++;
            } else if (c.isInstance(o6)) {
                res++;
            } else if (c.isInstance(o7)) {
                res++;
            } else if (c.isInstance(o8)) {
                res++;
            }
        }

        return res;
    }

    @Benchmark
    public boolean classIsAssignableFromShouldFold(ThreadState state) {
        return AA.class.isAssignableFrom(state.aa1.getClass());
    }

    @Benchmark
    public boolean classIsInstanceShouldFold(ThreadState state) {
        return AA.class.isInstance(state.aa1);
    }

    @Benchmark
    public boolean instanceOfClassShouldFold(ThreadState state) {
        return state.aa1 instanceof AA;
    }

    @Benchmark
    public int classIsAssignableFrom(ThreadState state) {
        int res = 0;
        Object[] objects = state.object;
        for (int i = 0; i < objects.length; i++) {
            if (classIsAssignableFrom(objects[i])) {
                res++;
            }
        }
        return res;
    }

    boolean classIsAssignableFrom(Object obj) {
        return AA1.class.isAssignableFrom(obj.getClass());
    }

    @Benchmark
    public int classIsInstance(ThreadState state) {
        int res = 0;
        Object[] objects = state.object;
        for (int i = 0; i < objects.length; i++) {
            if (classIsInstance(objects[i])) {
                res++;
            }
        }
        return res;
    }

    boolean classIsInstance(Object obj) {
        return AA1.class.isInstance(obj);
    }

    @Benchmark
    public int instanceOfClass(ThreadState state) {
        int res = 0;
        Object[] objects = state.object;
        for (int i = 0; i < objects.length; i++) {
            if (instanceOfClass(objects[i])) {
                res++;
            }
        }
        return res;
    }

    boolean instanceOfClass(Object obj) {
        return obj instanceof AA1;
    }
}
