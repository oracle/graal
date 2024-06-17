/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package hello;

// Checkstyle: stop

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.test.debug.CStructTests;

public class Hello {
    public abstract static class Greeter {
        static Greeter greeter(String[] args) {
            if (args.length == 0) {
                return new DefaultGreeter();
            } else if (args.length == 1) {
                return new NamedGreeter(args[0]);
            } else {
                throw new RuntimeException("zero or one args expected");
            }
        }

        public abstract void greet();
    }

    public static class DefaultGreeter extends Greeter {

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public void greet() {
            System.out.println("Hello, world!");
        }
    }

    public static class NamedGreeter extends Greeter {
        private String name;

        public NamedGreeter(String name) {
            this.name = name;
        }

        @Override
        public void greet() {
            System.out.println("Hello, " + name + "!");
        }
    }

    @NeverInline("For testing purposes")
    private static void noInlineFoo() {
        inlineMee();
    }

    @AlwaysInline("For testing purposes")
    private static void inlineCallChain() {
        inlineMee();
    }

    @AlwaysInline("For testing purposes")
    private static void inlineMee() {
        inlineMoo();
    }

    @AlwaysInline("For testing purposes")
    private static void inlineMoo() {
        System.out.println("This is a cow");
    }

    @NeverInline("For testing purposes")
    private static void noInlineThis() {
        inlineIs();
    }

    @AlwaysInline("For testing purposes")
    private static void inlineIs() {
        inlineA();
    }

    @AlwaysInline("For testing purposes")
    private static void inlineA() {
        noInlineTest();
    }

    @NeverInline("For testing purposes")
    private static void noInlineTest() {
        System.out.println("This is a test");
    }

    @AlwaysInline("For testing purposes")
    private static void inlineFrom() {
        //@formatter:off
        noInlineHere(5); inlineTailRecursion(1); // These two calls are purposely placed on the same line!!!
        //@formatter:on
        inlineHere(5);
        inlineTailRecursion(5);
    }

    @NeverInline("For testing purposes")
    private static void noInlineHere(int n) {
        inlineMixTo(n);
    }

    @AlwaysInline("For testing purposes")
    private static void inlineMixTo(int n) {
        if (n > 0) {
            noInlineHere(n - 1);
        }
        System.out.println("Recursive mixed calls!");
    }

    @AlwaysInline("For testing purposes")
    private static void inlineHere(int n) {
        inlineTo(n);
    }

    @AlwaysInline("For testing purposes")
    private static void inlineTo(int n) {
        if (n > 0) {
            inlineHere(n - 1);
        }
        System.out.println("Recursive inline calls!");
    }

    @AlwaysInline("For testing purposes")
    private static void inlineTailRecursion(int n) {
        if (n <= 0) {
            System.out.println("Recursive inline calls!");
            return;
        }
        inlineTailRecursion(n - 1);
    }

    @NeverInline("For testing purposes")
    private static void noInlineManyArgs(int i0, byte b1, short s2, char c3, boolean b4, int i5, int i6, long l7, int i8, long l9,
                    float f0, float f1, float f2, float f3, double d4, float f5, float f6, float f7, float f8, double d9, boolean b10, float f11) {
        System.out.println("i0 = " + i0);
        System.out.println("b1 = " + b1);
        System.out.println("s2 = " + s2);
        System.out.println("c3 = " + c3);
        System.out.println("b4 = " + b4);
        System.out.println("i5 = " + i5);
        System.out.println("i6 = " + i6);
        System.out.println("i7 = " + l7);
        System.out.println("i8 = " + i8);
        System.out.println("l9 = " + l9);
        System.out.println("f0 = " + f0);
        System.out.println("f1 = " + f1);
        System.out.println("f2 = " + f2);
        System.out.println("f3 = " + f3);
        System.out.println("d4 = " + d4);
        System.out.println("f5 = " + f5);
        System.out.println("f6 = " + f6);
        System.out.println("f7 = " + f7);
        System.out.println("f8 = " + f8);
        System.out.println("d9 = " + d9);
        System.out.println("b10 = " + b10);
        System.out.println("f11 = " + f11);
    }

    @NeverInline("For testing purposes")
    private static void noInlinePassConstants() {
        inlineReceiveConstants((byte) 1, 2, 3L, "stringtext", 4.0F, 5.0D);
    }

    @AlwaysInline("For testing purposes")
    private static void inlineReceiveConstants(byte b, int i, long l, String s, float f, double d) {
        long n = i * l;
        double q = f * d;
        String t = s + "!";
        System.out.println(String.format("b = %d\n", b));
        System.out.println(String.format("i = %d\n", i));
        System.out.println(String.format("l = %d\n", l));
        System.out.println(String.format("s = %s\n", s));
        System.out.println(String.format("f = %g\n", f));
        System.out.println(String.format("d = %g\n", d));
        System.out.println(String.format("n = %d\n", n));
        System.out.println(String.format("q = %g\n", q));
        System.out.println(String.format("t = %s\n", t));
    }

    private static java.util.function.Supplier<String> lambda = () -> {
        StringBuilder sb = new StringBuilder("lambda");
        sb.append(System.getProperty("never_optimize_away", "Text"));
        return sb.toString();
    };

    /* Add new methods above main */
    public static void main(String[] args) {
        Greeter greeter = Greeter.greeter(args);
        greeter.greet();
        /*-
         * Perform the following call chains
         *
         * main --no-inline--> noInlineFoo --inline--> inlineMee --inline--> inlineMoo
         * main --inline--> inlineCallChain --inline--> inlineMee --inline--> inlineMoo
         * main --no-inline--> noInlineThis --inline--> inlineIs --inline--> inlineA --no-inline--> noInlineTest
         * main --inline--> inlineFrom --no-inline--> noInlineHere --inline--> inlineMixTo --no-inline--+
         *                                                 ^                                            |
         *                                                 +-------------(rec call n-times)-------------+
         * main --inline--> inlineFrom --inline--> inlineHere --inline--> inlineTo --inline--+
         *                                             ^                                     |
         *                                             +---------(rec call n-times)----------+
         */
        noInlineFoo();
        inlineCallChain();
        noInlineThis();
        inlineFrom();
        noInlineManyArgs(0, (byte) 1, (short) 2, '3', true, 5, 6, 7, 8, 9,
                        0.0F, 1.125F, 2.25F, 3.375F, 4.5F, 5.625F, 6.75F, 7.875F, 9.0F, 10.125D, false, 12.375F);
        noInlinePassConstants();
        System.out.println(lambda.get());
        // create and manipulate some foreign types
        CStructTests.composite();
        CStructTests.weird();
        CStructTests.mixedArguments();
        System.exit(0);
    }
    /*
     * Keep main the last method in the file and add new methods right above it to avoid updating
     * all line numbers in testhello.py each time new methods are added
     */
}
