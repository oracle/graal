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
 * Compact dispatch fixture that exposes abstract, interface, and final-receiver call sites.
 */
public class StandaloneDispatchPrecisionCase {

    /**
     * Entry point that exercises every dispatch form covered by the test.
     */
    public static void main(String[] args) {
        publish(dispatchAbstract(new A()));
        publish(dispatchAbstract(new B()));
        publish(dispatchInterface(new A()));
        publish(dispatchInterface(new B()));
        publish(dispatchFinal(new FinalWorker()));
    }

    /**
     * Virtual dispatch through an abstract base type.
     */
    public static Result dispatchAbstract(AbstractWorker worker) {
        return worker.work();
    }

    /**
     * Virtual dispatch through an interface type.
     */
    public static Result dispatchInterface(Worker worker) {
        return worker.work();
    }

    /**
     * Dispatch through a final receiver type that should resolve to a single callee.
     */
    public static Result dispatchFinal(FinalWorker worker) {
        return worker.work();
    }

    private static void publish(@SuppressWarnings("unused") Result result) {
    }

    /**
     * Common result supertype for the dispatch cases.
     */
    public interface Result {
    }

    /**
     * Common callable contract used by both abstract and interface dispatch.
     */
    public interface Worker {
        Result work();
    }

    /**
     * Abstract base class for the primary dispatch hierarchy.
     */
    public abstract static class AbstractWorker implements Worker {
    }

    /**
     * First concrete dispatch target.
     */
    public static final class A extends AbstractWorker {
        @Override
        public Result work() {
            return new ResultA();
        }
    }

    /**
     * Second concrete dispatch target.
     */
    public static final class B extends AbstractWorker {
        @Override
        public Result work() {
            return new ResultB();
        }
    }

    /**
     * Final receiver used to check single-callee dispatch.
     */
    public static final class FinalWorker implements Worker {
        @Override
        public Result work() {
            return new ResultC();
        }
    }

    /**
     * First concrete result type.
     */
    public static final class ResultA implements Result {
    }

    /**
     * Second concrete result type.
     */
    public static final class ResultB implements Result {
    }

    /**
     * Result type produced only by the final-dispatch path.
     */
    public static final class ResultC implements Result {
    }
}
