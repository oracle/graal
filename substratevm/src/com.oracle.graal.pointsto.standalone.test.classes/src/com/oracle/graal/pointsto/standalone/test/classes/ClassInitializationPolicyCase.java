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

package com.oracle.graal.pointsto.standalone.test.classes;

/**
 * Fixtures for standalone class-initialization policy tests.
 */
public final class ClassInitializationPolicyCase {
    private ClassInitializationPolicyCase() {
    }

    public interface Worker {
        Result work();
    }

    public interface Result {
    }

    public static final class FallbackWorker implements Worker {
        @Override
        public Result work() {
            return new FallbackResult();
        }
    }

    public static final class PreciseWorker implements Worker {
        @Override
        public Result work() {
            return new PreciseResult();
        }
    }

    public static final class FallbackResult implements Result {
    }

    public static final class PreciseResult implements Result {
    }

    public static final class BuildTimePrecisionCase {
        private static Worker worker;

        static {
            worker = new FallbackWorker();
            worker = new PreciseWorker();
        }

        private BuildTimePrecisionCase() {
        }

        public static Result entry() {
            return worker.work();
        }
    }

    public static final class RuntimeOnlyPrecisionCase {
        private static Worker worker;

        static {
            worker = new FallbackWorker();
            worker = new PreciseWorker();
        }

        private RuntimeOnlyPrecisionCase() {
        }

        public static Result entry() {
            return worker.work();
        }
    }

    public static final class FailingInitializationCase {
        static {
            if (!Boolean.getBoolean("com.oracle.graal.pointsto.standalone.test.allowFailingInitializationCase")) {
                throw new RuntimeException("Expected standalone class-initialization test failure.");
            }
        }

        private FailingInitializationCase() {
        }

        public static Object entry() {
            return null;
        }
    }
}
