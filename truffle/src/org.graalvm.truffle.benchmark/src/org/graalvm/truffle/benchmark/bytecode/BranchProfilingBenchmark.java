/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode;

import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.graalvm.truffle.benchmark.bytecode.manual.AccessToken;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.bytecode.BytecodeDSLAccess;

@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
@OperationsPerInvocation(BranchProfilingBenchmark.ITERATIONS)
public class BranchProfilingBenchmark extends TruffleBenchmark {

    protected static final BytecodeDSLAccess ACCESS = BytecodeDSLAccess.lookup(AccessToken.PUBLIC_TOKEN, true);

    static final int PROFILES = 8;
    static final int ITERATIONS = 1048576; // trigger overflow for short
    static final int TRUE_MOD = 4;

    @Benchmark
    public void empty(New1State state, Blackhole hole) {
        for (int i = 0; i < ITERATIONS; i++) {
            hole.consume(empty(state.branchProfiles, i % PROFILES, i % TRUE_MOD == 0));
        }
    }

    @SuppressWarnings("unused")
    static boolean empty(int[] branchProfiles, int profileIndex, boolean condition) {
        return condition;
    }

    @State(Scope.Benchmark)
    public static class New1State {

        int[] branchProfiles;

        @Setup(Level.Iteration)
        public void setup() {
            branchProfiles = new int[PROFILES * 2];
        }
    }

    @Benchmark
    public void new1(New1State state, Blackhole hole) {
        for (int i = 0; i < ITERATIONS; i++) {
            hole.consume(new1Profile(state.branchProfiles, i % PROFILES, i % TRUE_MOD == 0));
        }
    }

    static boolean new1Profile(int[] branchProfiles, int profileIndex, boolean condition) {
        // this condition should get eliminated in the interpreter
        int t;
        int f;
        if (HostCompilerDirectives.inInterpreterFastPath()) {
            if (condition) {
                t = ACCESS.readInt(branchProfiles, profileIndex * 2);
                if (t == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                try {
                    t = Math.addExact(t, 1);
                } catch (ArithmeticException e) {
                    f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
                    f = (f & 0x1) + (f >> 1);
                    ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f);
                    t = Integer.MAX_VALUE >> 1;
                }
                ACCESS.writeInt(branchProfiles, profileIndex * 2, t);
            } else {
                f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
                if (f == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                try {
                    f = Math.addExact(f, 1);
                } catch (ArithmeticException e) {
                    t = ACCESS.readInt(branchProfiles, profileIndex * 2);
                    t = (t & 0x1) + (t >> 1);
                    ACCESS.writeInt(branchProfiles, profileIndex * 2, t);
                    f = Integer.MAX_VALUE >> 1;
                }
                ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f);
            }
            return condition;
        } else {
            t = ACCESS.readInt(branchProfiles, profileIndex * 2);
            f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
            if (condition) {
                if (t == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                if (f == 0) {
                    return true;
                }
            } else {
                if (f == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                if (t == 0) {
                    return false;
                }
            }
            return CompilerDirectives.injectBranchProbability((double) t / (double) (t + f), condition);
        }
    }

    @State(Scope.Benchmark)
    public static class New2State {
        int[] branchProfiles;

        @Setup(Level.Iteration)
        public void setup() {
            branchProfiles = new int[PROFILES];
        }
    }

    @Benchmark
    public void new2(New2State state, Blackhole hole) {
        for (int i = 0; i < ITERATIONS; i++) {
            hole.consume(new2Profile(state.branchProfiles, i % PROFILES, i % TRUE_MOD == 0));
        }
    }

    static boolean new2Profile(int[] branchProfiles, int profileIndex, boolean cond) {
        int value = ACCESS.readInt(branchProfiles, profileIndex);
        int t = value >> 16;
        int f = value & 0xFFFF;
        if (CompilerDirectives.inInterpreter()) {
            if (cond) {
                if (t == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                t++;
                if (t == 0xFFFF) {
                    // shift right but round up to at least 1
                    // necessary to avoid deopts for already executed paths
                    f = (f & 0x1) + (f >> 1);
                    t = 0x7FFF;
                }
                ACCESS.writeInt(branchProfiles, profileIndex, (t << 16) | f);
            } else {
                if (f == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                f++;
                if (f == 0xFFFF) {
                    t = (t & 0x1) + (t >> 1);
                    f = 0x7FFF;
                }
                ACCESS.writeInt(branchProfiles, profileIndex, (t << 16) | f);
            }
            return cond;
        } else {
            if ((cond && t == 0) || (!cond && f == 0)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (cond && f == 0) {
                return true; // propagate constant true
            } else if (!cond && t == 0) {
                return false; // propagate constant false
            } else {
                int sum = t + f;
                return CompilerDirectives.injectBranchProbability((double) t / (double) sum, cond);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class New3State {

        int[] branchProfiles;

        @Setup(Level.Iteration)
        public void setup() {
            branchProfiles = new int[PROFILES * 2];
        }

    }

    @Benchmark
    public void new3(New3State state, Blackhole hole) {
        for (int i = 0; i < ITERATIONS; i++) {
            hole.consume(new3Profile(state.branchProfiles, i % PROFILES, i % TRUE_MOD == 0));
        }
    }

    static boolean new3Profile(int[] branchProfiles, int profileIndex, boolean condition) {
        // this condition should get eliminated in the interpreter
        int t;
        int f;
        if (CompilerDirectives.inInterpreter()) {
            if (condition) {
                t = ACCESS.readInt(branchProfiles, profileIndex * 2);
                if (t == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                t++;
                if (t == Integer.MAX_VALUE) {
                    f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
                    f = (f & 0x1) + (f >> 1);
                    ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f);
                    t = Integer.MAX_VALUE >> 1;
                }
                ACCESS.writeInt(branchProfiles, profileIndex * 2, t);
            } else {
                f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
                if (f == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                f++;
                if (f == Integer.MAX_VALUE) {
                    t = ACCESS.readInt(branchProfiles, profileIndex * 2);
                    t = (t & 0x1) + (t >> 1);
                    ACCESS.writeInt(branchProfiles, profileIndex * 2, t);
                    f = Integer.MAX_VALUE >> 1;
                }
                ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f);
            }

            return condition;
        } else {
            t = ACCESS.readInt(branchProfiles, profileIndex * 2);
            f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
            if (condition && f == 0) {
                return true;
            } else if (!condition && t == 0) {
                return false;
            } else {
                int sum = t + f;
                return CompilerDirectives.injectBranchProbability((double) t / (double) sum, condition);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class OldState {

        int[] branchProfiles;

        @Setup(Level.Iteration)
        public void setup() {
            branchProfiles = new int[PROFILES * 2];
        }

    }

    @Benchmark
    public void old(OldState state, Blackhole hole) {
        for (int i = 0; i < ITERATIONS; i++) {
            hole.consume(oldProfile(state.branchProfiles, i % PROFILES, i % TRUE_MOD == 0));
        }
    }

    static boolean oldProfile(int[] branchProfiles, int profileIndex, boolean condition) {
        int t = ACCESS.readInt(branchProfiles, profileIndex * 2);
        int f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
        boolean val = condition;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                // Make this branch fold during PE
                val = true;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < Integer.MAX_VALUE) {
                    ACCESS.writeInt(branchProfiles, profileIndex * 2, t + 1);
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                // Make this branch fold during PE
                val = false;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (f < Integer.MAX_VALUE) {
                    ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f + 1);
                }
            }
        }
        if (CompilerDirectives.inInterpreter()) {
            // no branch probability calculation in the interpreter
            return val;
        } else {
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
        }
    }

}
