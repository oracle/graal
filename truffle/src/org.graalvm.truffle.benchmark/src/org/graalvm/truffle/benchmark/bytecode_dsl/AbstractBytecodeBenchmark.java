/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.truffle.benchmark.bytecode_dsl;

import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.graalvm.truffle.benchmark.bytecode_dsl.specs.BenchmarkSpec;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.BenchmarkParams;

public abstract class AbstractBytecodeBenchmark extends TruffleBenchmark {

    private static final boolean CHECK_RESULTS = System.getProperty("CheckResults") != null;

    @Setup(Level.Trial)
    public void abstractSetup() {
        if (CHECK_RESULTS) {
            // Checkstyle: stop benchmark debug logging
            System.err.println(
                            "-DCheckResults was set. Output will be validated after each iteration if the benchmark invokes checkExpectedResult. This property should be unset when measuring actual benchmark numbers.");
            // Checkstyle: resume
        }
    }

    /**
     * Returns the name of the current benchmark spec class.
     */
    protected abstract String getBenchmarkSpecClassName();

    /**
     * Helper for checking the expected result, when result checking is enabled.
     */
    protected final void checkExpectedResult(Object result, String benchMethod) {
        if (CHECK_RESULTS) {
            BenchmarkSpec spec = getBenchmarkSpec();
            if (!spec.expectedResult().equals(result)) {
                throw new AssertionError(getBenchmarkSpecClassName() + ":" + benchMethod + " produced the wrong result. Received " + result + " but expected " + spec.expectedResult());
            }
        }
    }

    /**
     * Returns the current benchmark spec.
     */
    protected BenchmarkSpec getBenchmarkSpec() {
        String benchmarkSpecClassName = getBenchmarkSpecClassName();
        String fullClassName = String.join(".", AbstractBytecodeBenchmark.class.getPackageName(), "specs", benchmarkSpecClassName);
        try {
            return (BenchmarkSpec) Class.forName(fullClassName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException(String.format("Bad benchmark spec %s: class %s could not be reflectively instantiated.", benchmarkSpecClassName, fullClassName), ex);
        }
    }

    /**
     * Returns the name of the method currently being benchmarked by JMH.
     */
    protected String getBenchmarkMethod(BenchmarkParams params) {
        String[] parts = params.getBenchmark().split("\\.");
        return parts[parts.length - 1];
    }

}
