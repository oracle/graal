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

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;

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
        System.exit(0);
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
}
