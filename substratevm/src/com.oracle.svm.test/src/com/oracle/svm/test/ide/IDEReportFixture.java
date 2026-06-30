/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.ide;

import java.util.Locale;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.test.notide.IDEReportFilteredOut;

public final class IDEReportFixture {
    private static final String CONSTANT_FIELD = "fixture-constant";

    private IDEReportFixture() {
    }

    public static void main(String[] args) {
        var mode = args.length == 0 ? "default" : args[0];
        var service = createService(mode);
        var result = service.value(mode);
        var devirtualizedResult = singleDispatch(new SinglePrimaryService(), mode);
        var mixedResult = mixedDispatch(selectMixedService(mode), mode);
        var reflectedName = resolveReflectionTargetName();
        var unreachableResult = branchWithUnreachablePath(mode.length(), true);
        var filteredOutResult = IDEReportFilteredOut.touch(unreachableResult);
        System.out.println(message(args) + ":" + result + ":" + reflectedName + ":" + devirtualizedResult + ":" + mixedResult + ":" + unreachableResult + ":" + filteredOutResult + ":" +
                        CONSTANT_FIELD);
    }

    static String message(String[] args) {
        if (args.length == 0) {
            return "IDE report fixture";
        }
        return String.join(" ", args);
    }

    private static Service createService(String mode) {
        if (mode.isEmpty()) {
            return new SecondaryService();
        }
        return new PrimaryService();
    }

    @NeverInline("Exercise IDE report devirtualization.")
    private static Object singleDispatch(SingleService service, String mode) {
        try {
            return service.singleValue(mode);
        } catch (RuntimeException e) {
            return e.getClass().getName();
        }
    }

    private static MixedService selectMixedService(String mode) {
        return mode.isEmpty() ? new MixedConstantService() : new MixedObjectService();
    }

    @NeverInline("Exercise sound aggregation of multiple return outcomes.")
    private static Object mixedDispatch(MixedService service, String mode) {
        return service.mixedValue(mode);
    }

    private static String resolveReflectionTargetName() {
        try {
            var target = Class.forName("com.oracle.svm.test.ide.IDEReportFixture$ReflectionTarget");
            var loader = target.getClassLoader();
            return target.getName() + ":" + (loader == null ? "bootstrap" : "application") + ":" + ReflectionTarget.marker();
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static int branchWithUnreachablePath(int input, boolean flag) {
        var value = normalize(input);
        if (flag) {
            return value + 1;
        }
        return unreachableOnly(value);
    }

    private static int normalize(int value) {
        return value < 0 ? -value : value;
    }

    private static int unreachableOnly(int value) {
        return value * 17;
    }

    private interface Service {
        Object value(String mode);
    }

    private interface SingleService {
        Object singleValue(String mode);
    }

    private interface MixedService {
        Object mixedValue(String mode);
    }

    private static final class PrimaryService implements Service {
        @Override
        public Object value(String mode) {
            return new ReturnPayload(mode.length(), CONSTANT_FIELD);
        }
    }

    private static final class SecondaryService implements Service {
        @Override
        public Object value(String mode) {
            return "unused:" + mode;
        }
    }

    private static final class SinglePrimaryService implements SingleService {
        @Override
        @NeverInline("Exercise IDE report devirtualization.")
        public Object singleValue(String mode) {
            return mode.toUpperCase(Locale.ROOT);
        }
    }

    private static final class MixedConstantService implements MixedService {
        @Override
        public Object mixedValue(String mode) {
            return "only-one-callee-returns-this";
        }
    }

    private static final class MixedObjectService implements MixedService {
        @Override
        public Object mixedValue(String mode) {
            return new ReturnPayload(mode.length(), CONSTANT_FIELD);
        }
    }

    private record ReturnPayload(int size, String label) {
    }

    private static final class ReflectionTarget {
        private ReflectionTarget() {
        }

        static String marker() {
            return "reflected";
        }
    }
}
