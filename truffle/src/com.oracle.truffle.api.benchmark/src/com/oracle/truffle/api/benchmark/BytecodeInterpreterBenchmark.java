/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.benchmark;

import static com.oracle.truffle.api.benchmark.TruffleBenchmark.Defaults.ITERATION_TIME;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

import sun.misc.Unsafe;

@Warmup(iterations = 15, time = ITERATION_TIME)
@Measurement(iterations = 5, time = ITERATION_TIME)
public class BytecodeInterpreterBenchmark extends TruffleBenchmark {

    @State(Scope.Thread)
    public static class BenchmarkState {

        final BytecodeNode bytecodeNode;
        final FrameDescriptor dynamicDescriptor;
        final FrameDescriptor staticDescriptor;
        final VirtualFrame dynamicTestFrame;
        final VirtualFrame staticTestFrame;

        final Object[] singleArg = new Object[]{42};
        {
            // call the method to initialize classes
            // init truffle runtime
            new RootNode(null) {

                @Override
                public Object execute(VirtualFrame frame) {
                    return null;
                }

            }.getCallTarget();

            int locals = 2;
            int stack = 2;
            /**
             * Sample application:
             *
             * <pre>
             * i = Short.MAX_VALUE + Short.MAX_VALUE;
             * sum = 0;
             * while (i > 0) {
             *     i--;
             *     sum = sum + 42;
             * }
             * return sum;
             * </pre>
             */
            short[] ops = new short[]{
                            /* 00 */ BytecodeNode.CONST, Short.MAX_VALUE,
                            /* 02 */ BytecodeNode.CONST, Short.MAX_VALUE,
                            /* 04 */ BytecodeNode.ADD,
                            /* 05 */ BytecodeNode.LOCAL_WRITE0, 0, // i = MAX
                            /* 07 */ BytecodeNode.CONST, 0,
                            /* 09 */ BytecodeNode.LOCAL_WRITE1, 1, // sum = 0

                            /* 11 */ BytecodeNode.LOCAL_READ0, 0, // i
                            /* 13 */ BytecodeNode.JUMP_IF_ZERO, 31, // i == 0

                            /* 15 */ BytecodeNode.LOCAL_READ0, 0,
                            /* 17 */ BytecodeNode.CONST, -1,
                            /* 19 */ BytecodeNode.ADD,
                            /* 20 */ BytecodeNode.LOCAL_WRITE0, 0, // i = i - 1

                            /* 22 */ BytecodeNode.LOCAL_READ1, 1,
                            /* 24 */ BytecodeNode.CONST, 42,
                            /* 26 */ BytecodeNode.ADD,
                            /* 27 */ BytecodeNode.LOCAL_WRITE1, 1, // sum = sum + 42

                            /* 29 */ BytecodeNode.JUMP, 11,

                            // return
                            /* 31 */ BytecodeNode.LOCAL_READ1, 1, // sum
                            BytecodeNode.BOUNDARY,
                            /* 34 */ BytecodeNode.RETURN, 0,
            };
            short[] regOps = new short[]{
                            // variables/registers: 0:i, 1:sum, 2:tmp, 3:tmp
                            // CONST a b => vars[a] <- b
                            // ADD a b c => vars[c] <- vars[a] + vars[b]
                            // etc.

                            // i = Short.MAX_VALUE + Short.MAX_VALUE;
                            /* 00 */ BytecodeNode.CONST, 2, Short.MAX_VALUE,
                            /* 03 */ BytecodeNode.CONST, 3, Short.MAX_VALUE,
                            /* 06 */ BytecodeNode.ADD, 2, 3, 0,

                            /* 10 */ BytecodeNode.CONST, 1, 0, // sum = 0

                            /* 13 */ BytecodeNode.JUMP_IF_ZERO, 0, 32, // i == 0

                            // i--;
                            /* 16 */ BytecodeNode.CONST, 2, -1,
                            /* 19 */ BytecodeNode.ADD, 0, 2, 0,

                            // sum = sum + 42;
                            /* 23 */ BytecodeNode.CONST, 2, 42,
                            /* 26 */ BytecodeNode.ADD, 1, 2, 1,

                            /* 30 */ BytecodeNode.JUMP, 13,

                            // return
                            /* 32 */ BytecodeNode.BOUNDARY,
                            /* 33 */ BytecodeNode.RETURN, 1,
            };
            short[] regOps2 = new short[]{
                            // Extreme case: no registers spilling to the locals array
                            // Optimized the loop to be only 3 bytecodes
                            // registers mapping: 0:i, 1:sum, 2:tmp, 3:tmp

                            // i = Short.MAX_VALUE + Short.MAX_VALUE;
                            /* 00 */ BytecodeNode.CONST, 0, Short.MAX_VALUE,
                            /* 03 */ BytecodeNode.CONST, 1, Short.MAX_VALUE,
                            /* 06 */ BytecodeNode.ADD, 0, 0, 1,

                            /* 10 */ BytecodeNode.CONST, 1, 0, // sum = 0

                            // put the constants -1 and 42 to temp registers
                            /* 13 */ BytecodeNode.CONST, 2, -1,
                            /* 16 */ BytecodeNode.CONST, 3, 42,

                            /* 19 */ BytecodeNode.JUMP, 29,

                            // i--;,
                            /* 21 */ BytecodeNode.ADD, 0, 0, 2,

                            // sum = sum + 42;
                            /* 25 */ BytecodeNode.ADD, 1, 1, 3,

                            /* 29 */ BytecodeNode.JUMP_IF_NOT_ZERO, 0, 21, // while (i < 0)

                            // return
                            /* 32 */ BytecodeNode.BOUNDARY,
                            /* 33 */ BytecodeNode.RETURN, 1,
            };

            var b = FrameDescriptor.newBuilder();
            b.addSlots(locals, FrameSlotKind.Int);
            b.addSlots(stack, FrameSlotKind.Illegal);
            dynamicDescriptor = b.build();
            dynamicTestFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], dynamicDescriptor);
            bytecodeNode = new BytecodeNode(ops, regOps, regOps2, locals, stack);

            b = FrameDescriptor.newBuilder();
            b.addSlots(locals, FrameSlotKind.Static);
            b.addSlots(stack, FrameSlotKind.Static);
            staticDescriptor = b.build();
            staticTestFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], staticDescriptor);
        }

        @TearDown
        public void tearDown() {
        }
    }

    // This should show HotSpot JVM bytecode interpreter performance on an equivalent Java program
    @Benchmark
    @BytecodeInterpreterSwitch
    public void baseline(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeBaseline(null);
    }

    @Benchmark
    @BytecodeInterpreterSwitch
    public void unsafe(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeUnsafe(null);
    }

    // This shows theoretical lower limit for any bytecode dispatch optimizations using the "unsafe"
    // bytecode interpretation approach
    @Benchmark
    @BytecodeInterpreterSwitch
    public void dispatchBaselineUnsafe(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeDisptachBaselineUnsafe(null);
    }

    // This shows theoretical lower limit for any bytecode dispatch optimizations using the
    // "virtualFrame" bytecode interpretation approach
    @Benchmark
    @BytecodeInterpreterSwitch
    public void dispatchBaselineVirtualFrame(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeDispatchBaselineVirtualFrame(state.dynamicTestFrame, state.dynamicDescriptor);
    }

    @Benchmark
    @BytecodeInterpreterSwitch
    public void virtualFrame(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeVirtualFrame(state.dynamicTestFrame, state.dynamicDescriptor);
    }

    @Benchmark
    @BytecodeInterpreterSwitch
    public void nonVirtual(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeNonVirtual(state.dynamicTestFrame);
    }

    @Benchmark
    @BytecodeInterpreterSwitch
    public void readStatic(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeReadStatic(state.staticTestFrame);
    }

    // Approach using 2 bytecode loops: generic and integer top-of-stack
    @Benchmark
    @BytecodeInterpreterSwitch
    public void readStaticTOS(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeReadStaticTOS(state.staticTestFrame);
    }

    // Approach that uses internal temporary variables and no operand stack
    @Benchmark
    @BytecodeInterpreterSwitch
    public void readStaticRegisters(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeReadStaticRegisters(state.staticTestFrame);
    }

    @Benchmark
    @BytecodeInterpreterSwitch
    public void readStaticRealRegisters(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeReadStaticRealRegisters(state.staticTestFrame);
    }

    static short unsafeRead(short[] array, int index) {
        return UNSAFE.getShort(array, ARRAY_SHORT_BASE_OFFSET + (index * ARRAY_SHORT_INDEX_SCALE));
    }

    static int unsafeRead(int[] array, int index) {
        return UNSAFE.getInt(array, ARRAY_INT_BASE_OFFSET + (index * ARRAY_INT_INDEX_SCALE));
    }

    static void unsafeWrite(int[] array, int index, int value) {
        UNSAFE.putInt(array, ARRAY_INT_BASE_OFFSET + (index * ARRAY_INT_INDEX_SCALE), value);
    }

    private static final Unsafe UNSAFE = getUnsafe();

    static final long ARRAY_INT_BASE_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
    static final long ARRAY_SHORT_BASE_OFFSET = UNSAFE.arrayBaseOffset(short[].class);
    static final long ARRAY_INT_INDEX_SCALE = UNSAFE.arrayIndexScale(int[].class);
    static final long ARRAY_SHORT_INDEX_SCALE = UNSAFE.arrayIndexScale(short[].class);

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    static final class BytecodeNode extends Node {

        static final short ADD = 1;
        static final short CONST = 2;
        static final short LOCAL_READ = 3;
        static final short LOCAL_READ0 = 4;
        static final short LOCAL_READ1 = 5;
        static final short LOCAL_WRITE = 6;
        static final short LOCAL_WRITE0 = 7;
        static final short LOCAL_WRITE1 = 8;
        static final short JUMP = 9;
        static final short JUMP_IF_ZERO = 10;
        static final short RETURN = 11;
        static final short BOUNDARY = 12;
        static final short JUMP_IF_NOT_ZERO = 13;

        @CompilationFinal(dimensions = 1) final short[] ops;
        @CompilationFinal(dimensions = 1) final short[] regOps;
        @CompilationFinal(dimensions = 1) final short[] regOps2;
        final int locals;
        final int stackSize;

        BytecodeNode(short[] ops, short[] regOps, short[] regOps2, int locals, int stack) {
            this.ops = ops;
            this.regOps = regOps;
            this.regOps2 = regOps2;
            this.locals = locals;
            this.stackSize = stack;
        }

        static final int MAX_LOCALS = 10;

        static Map<String, String> map = new HashMap<>();

        @TruffleBoundary
        @CompilerControl(Mode.DONT_INLINE)
        static void boundary(@SuppressWarnings("unused") MaterializedFrame f) {
            // just have some complicated code here
            map.put("s", "s");
        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public int executeUnsafe(@SuppressWarnings("unused") VirtualFrame f) {
            // VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[0],
            // descriptor);
            final int maxLocals = locals + 2;
            int[] stack = new int[maxLocals];
            short[] bc = this.ops;
            int sp = locals;
            int bci = 0;
            if (sp > maxLocals) {
                throw new ArrayIndexOutOfBoundsException();
            }

            int localIndex;
            while (true) {

                // String i = (frame.getTag(0) == FrameSlotKind.Int.tag) ? (" i " + frame.getInt(0))
                // : "";
                // String sum = (frame.getTag(1) == FrameSlotKind.Int.tag) ? (" sum " +
                // frame.getInt(1)) : "";
                // System.out.println(bci + i + sum);

                switch (unsafeRead(bc, bci)) {
                    case BOUNDARY:
                        boundary(null);
                        bci += 1;
                        break;
                    case LOCAL_READ:
                    case LOCAL_READ0:
                    case LOCAL_READ1:
                        localIndex = unsafeRead(bc, bci + 1);
                        unsafeWrite(stack, sp, unsafeRead(stack, localIndex));
                        sp += 1;
                        bci += 2;
                        break;
                    case LOCAL_WRITE:
                    case LOCAL_WRITE0:
                    case LOCAL_WRITE1:
                        localIndex = unsafeRead(bc, bci + 1);
                        unsafeWrite(stack, localIndex, unsafeRead(stack, sp - 1));
                        sp -= 1;
                        bci += 2;
                        break;
                    case ADD:
                        unsafeWrite(stack, sp - 2, unsafeRead(stack, sp - 1) + unsafeRead(stack, sp - 2));
                        sp -= 1;
                        bci += 1;
                        break;
                    case CONST:
                        unsafeWrite(stack, sp, unsafeRead(bc, bci + 1));
                        sp += 1;
                        bci += 2;
                        break;
                    case JUMP:
                        bci = unsafeRead(bc, bci + 1);
                        break;
                    case JUMP_IF_ZERO:
                        if (unsafeRead(stack, sp - 1) == 0) {
                            bci = unsafeRead(bc, bci + 1);
                        } else {
                            bci += 2;
                        }
                        sp -= 1;
                        break;
                    case RETURN:
                        return unsafeRead(stack, sp - 1);
                    default:
                        // propagates transferToInterpeter from within the call
                        throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }

        @BytecodeInterpreterSwitch
        public int executeDisptachBaselineUnsafe(@SuppressWarnings("unused") VirtualFrame f) {
            final int maxLocals = locals + 2;
            int[] stack = new int[maxLocals];
            short[] bc = this.ops;
            int sp = locals;
            int bci = 0;
            if (sp > maxLocals) {
                throw new ArrayIndexOutOfBoundsException();
            }

            // 00 BytecodeNode.CONST, Short.MAX_VALUE,
            unsafeWrite(stack, sp, unsafeRead(bc, bci + 1));
            sp += 1;
            bci += 2;

            // 02 BytecodeNode.CONST, Short.MAX_VALUE,
            unsafeWrite(stack, sp, unsafeRead(bc, bci + 1));
            sp += 1;
            bci += 2;

            // 04 BytecodeNode.ADD
            unsafeWrite(stack, sp - 2, unsafeRead(stack, sp - 1) + unsafeRead(stack, sp - 2));
            sp -= 1;
            bci += 1;

            // 05 BytecodeNode.LOCAL_WRITE0, 0, // i = MAX
            short localIndex = unsafeRead(bc, bci + 1);
            unsafeWrite(stack, localIndex, unsafeRead(stack, sp - 1));
            sp -= 1;
            bci += 2;

            // 07 BytecodeNode.CONST, 0,
            unsafeWrite(stack, sp, unsafeRead(bc, bci + 1));
            sp += 1;
            bci += 2;

            // 09 BytecodeNode.LOCAL_WRITE1, 1, // sum = 0
            localIndex = unsafeRead(bc, bci + 1);
            unsafeWrite(stack, localIndex, unsafeRead(stack, sp - 1));
            sp -= 1;
            bci += 2;

            while (true) {
                // 11 BytecodeNode.LOCAL_READ0, 0, // i
                localIndex = unsafeRead(bc, bci + 1);
                unsafeWrite(stack, sp, unsafeRead(stack, localIndex));
                sp += 1;
                bci += 2;

                // 13 BytecodeNode.JUMP_IF_ZERO, 31, // i == 0
                if (unsafeRead(stack, sp - 1) == 0) {
                    bci = unsafeRead(bc, bci + 1);
                    break;
                } else {
                    bci += 2;
                }
                sp -= 1;

                // 15 BytecodeNode.LOCAL_READ0, 0,
                localIndex = unsafeRead(bc, bci + 1);
                unsafeWrite(stack, sp, unsafeRead(stack, localIndex));
                sp += 1;
                bci += 2;

                // 17 BytecodeNode.CONST, -1,
                unsafeWrite(stack, sp, unsafeRead(bc, bci + 1));
                sp += 1;
                bci += 2;

                // 19 BytecodeNode.ADD,
                unsafeWrite(stack, sp - 2, unsafeRead(stack, sp - 1) + unsafeRead(stack, sp - 2));
                sp -= 1;
                bci += 1;

                // 20 BytecodeNode.LOCAL_WRITE0, 0, // i = i - 1
                localIndex = unsafeRead(bc, bci + 1);
                unsafeWrite(stack, localIndex, unsafeRead(stack, sp - 1));
                sp -= 1;
                bci += 2;

                // 22 BytecodeNode.LOCAL_READ1, 1,
                localIndex = unsafeRead(bc, bci + 1);
                unsafeWrite(stack, sp, unsafeRead(stack, localIndex));
                sp += 1;
                bci += 2;

                // 24 BytecodeNode.CONST, 42,
                unsafeWrite(stack, sp, unsafeRead(bc, bci + 1));
                sp += 1;
                bci += 2;

                // 26 BytecodeNode.ADD,
                unsafeWrite(stack, sp - 2, unsafeRead(stack, sp - 1) + unsafeRead(stack, sp - 2));
                sp -= 1;
                bci += 1;

                // 27 BytecodeNode.LOCAL_WRITE1, 1, // sum = sum + 42
                localIndex = unsafeRead(bc, bci + 1);
                unsafeWrite(stack, localIndex, unsafeRead(stack, sp - 1));
                sp -= 1;
                bci += 2;

                // 29 BytecodeNode.JUMP, 11,
                bci = 11;
            }

            // return
            // 31 BytecodeNode.LOCAL_READ1, 1, // sum
            localIndex = unsafeRead(bc, bci + 1);
            unsafeWrite(stack, sp, unsafeRead(stack, localIndex));
            sp += 1;
            bci += 2;

            // BytecodeNode.BOUNDARY,
            boundary(null);
            bci += 1;

            // 34 BytecodeNode.RETURN, 0,
            return unsafeRead(stack, sp - 1);
        }

        // Execute with
        // -X:CompileCommand=exclude,com/oracle/truffle/api/benchmark/BytecodeInterpreterBenchmark$BytecodeNode,executeBaseline
        // to get HostSpot interpreter as the baseline
        @CompilerControl(Mode.EXCLUDE)
        public int executeBaseline(@SuppressWarnings("unused") VirtualFrame f) {
            int i = Short.MAX_VALUE + Short.MAX_VALUE;
            int sum = 0;
            while (i > 0) {
                i--;
                sum = sum + 42;
                i--;
            }
            boundary(null);
            return sum;
        }

        @BytecodeInterpreterSwitch
        public int executeDispatchBaselineVirtualFrame(VirtualFrame f, FrameDescriptor dynamicDescriptor) {
            VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], dynamicDescriptor);
            short[] bc = this.ops;
            int sp = locals;
            int bci = 0;

            // 00 BytecodeNode.CONST, Short.MAX_VALUE,
            frame.setInt(sp, bc[bci + 1]);
            sp += 1;
            bci += 2;

            // 02 BytecodeNode.CONST, Short.MAX_VALUE,
            frame.setInt(sp, bc[bci + 1]);
            sp += 1;
            bci += 2;

            // 04 BytecodeNode.ADD,
            frame.setInt(sp - 2, frame.getInt(sp - 1) + frame.getInt(sp - 2));
            sp -= 1;
            bci += 1;

            // 05 BytecodeNode.LOCAL_WRITE0, 0, // i = MAX
            short localIndex = bc[bci + 1];
            frame.setInt(localIndex, frame.getInt(sp - 1));
            sp -= 1;
            bci += 2;

            // 07 BytecodeNode.CONST, 0,
            frame.setInt(sp, bc[bci + 1]);
            sp += 1;
            bci += 2;

            // 09 BytecodeNode.LOCAL_WRITE1, 1, // sum = 0
            localIndex = bc[bci + 1];
            frame.setInt(localIndex, frame.getInt(sp - 1));
            sp -= 1;
            bci += 2;

            while (true) {
                // 11 BytecodeNode.LOCAL_READ0, 0, // i
                localIndex = bc[bci + 1];
                frame.setInt(sp, frame.getInt(localIndex));
                sp += 1;
                bci += 2;

                // 13 BytecodeNode.JUMP_IF_ZERO, 31, // i == 0
                if (frame.getInt(sp - 1) == 0) {
                    bci = bc[bci + 1];
                    break;
                } else {
                    bci += 2;
                }
                sp -= 1;

                // 15 BytecodeNode.LOCAL_READ0, 0,
                localIndex = bc[bci + 1];
                frame.setInt(sp, frame.getInt(localIndex));
                sp += 1;
                bci += 2;

                // 17 BytecodeNode.CONST, -1,
                frame.setInt(sp, bc[bci + 1]);
                sp += 1;
                bci += 2;

                // 19 BytecodeNode.ADD,
                frame.setInt(sp - 2, frame.getInt(sp - 1) + frame.getInt(sp - 2));
                sp -= 1;
                bci += 1;

                // 20 BytecodeNode.LOCAL_WRITE0, 0, // i = i - 1
                localIndex = bc[bci + 1];
                frame.setInt(localIndex, frame.getInt(sp - 1));
                sp -= 1;
                bci += 2;

                // 22 BytecodeNode.LOCAL_READ1, 1,
                localIndex = bc[bci + 1];
                frame.setInt(sp, frame.getInt(localIndex));
                sp += 1;
                bci += 2;

                // 24 BytecodeNode.CONST, 42,
                frame.setInt(sp, bc[bci + 1]);
                sp += 1;
                bci += 2;

                // 26 BytecodeNode.ADD,
                frame.setInt(sp - 2, frame.getInt(sp - 1) + frame.getInt(sp - 2));
                sp -= 1;
                bci += 1;

                // 27 BytecodeNode.LOCAL_WRITE1, 1, // sum = sum + 42
                localIndex = bc[bci + 1];
                frame.setInt(localIndex, frame.getInt(sp - 1));
                sp -= 1;
                bci += 2;

                // 29 BytecodeNode.JUMP, 11,
                bci = 11;
            }

            // return
            // 31 BytecodeNode.LOCAL_READ1, 1, // sum
            localIndex = bc[bci + 1];
            frame.setInt(sp, frame.getInt(localIndex));
            sp += 1;
            bci += 2;

            // BytecodeNode.BOUNDARY,
            boundary(f.materialize());
            bci += 1;

            // 34 BytecodeNode.RETURN, 0,
            return frame.getInt(sp - 1);
        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public int executeVirtualFrame(VirtualFrame f, FrameDescriptor dynamicDescriptor) {
            VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], dynamicDescriptor);
            short[] bc = this.ops;
            int sp = locals;
            int bci = 0;

            int localIndex;
            while (true) {

                switch (bc[bci]) {
                    case BOUNDARY:
                        boundary(f.materialize());
                        bci += 1;
                        break;
                    case LOCAL_READ:
                    case LOCAL_READ0:
                    case LOCAL_READ1:
                        localIndex = bc[bci + 1];
                        frame.setInt(sp, frame.getInt(localIndex));
                        sp += 1;
                        bci += 2;
                        break;
                    case LOCAL_WRITE:
                    case LOCAL_WRITE0:
                    case LOCAL_WRITE1:
                        localIndex = bc[bci + 1];
                        frame.setInt(localIndex, frame.getInt(sp - 1));
                        sp -= 1;
                        bci += 2;
                        break;
                    case ADD:
                        frame.setInt(sp - 2, frame.getInt(sp - 1) + frame.getInt(sp - 2));
                        sp -= 1;
                        bci += 1;
                        break;
                    case CONST:
                        frame.setInt(sp, bc[bci + 1]);
                        sp += 1;
                        bci += 2;

                        break;
                    case JUMP:
                        bci = bc[bci + 1];
                        break;
                    case JUMP_IF_ZERO:
                        if (frame.getInt(sp - 1) == 0) {
                            bci = bc[bci + 1];
                        } else {
                            bci += 2;
                        }
                        sp -= 1;
                        break;
                    case RETURN:
                        return frame.getInt(sp - 1);
                    default:
                        // propagates transferToInterpeter from within the call
                        throw CompilerDirectives.shouldNotReachHere();
                }
            }

        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public int executeNonVirtual(VirtualFrame f) {
            VirtualFrame frame = f;
            short[] bc = this.ops;
            int sp = locals;
            int bci = 0;

            int localIndex;
            while (true) {

                switch (bc[bci]) {
                    case BOUNDARY:
                        boundary(null);
                        bci += 1;
                        break;
                    case LOCAL_READ:
                    case LOCAL_READ0:
                    case LOCAL_READ1:
                        localIndex = bc[bci + 1];
                        frame.setInt(sp, frame.getInt(localIndex));
                        sp += 1;
                        bci += 2;
                        break;
                    case LOCAL_WRITE:
                    case LOCAL_WRITE0:
                    case LOCAL_WRITE1:
                        localIndex = bc[bci + 1];
                        frame.setInt(localIndex, frame.getInt(sp - 1));
                        sp -= 1;
                        bci += 2;
                        break;
                    case ADD:
                        frame.setInt(sp - 2, frame.getInt(sp - 1) + frame.getInt(sp - 2));
                        sp -= 1;
                        bci += 1;
                        break;
                    case CONST:
                        frame.setInt(sp, bc[bci + 1]);
                        sp += 1;
                        bci += 2;

                        break;
                    case JUMP:
                        bci = bc[bci + 1];
                        break;
                    case JUMP_IF_ZERO:
                        if (frame.getInt(sp - 1) == 0) {
                            bci = bc[bci + 1];
                        } else {
                            bci += 2;
                        }
                        sp -= 1;
                        break;
                    case RETURN:
                        return frame.getInt(sp - 1);
                    default:
                        // propagates transferToInterpeter from within the call
                        throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public int executeReadStatic(VirtualFrame f) {
            VirtualFrame frame = f;
            short[] bc = this.ops;
            int sp = locals;
            int bci = 0;

            int localIndex;
            while (true) {
                switch (bc[bci]) {
                    case BOUNDARY:
                        boundary(f.materialize());
                        bci += 1;
                        break;
                    case LOCAL_READ:
                    case LOCAL_READ0:
                    case LOCAL_READ1:
                        localIndex = bc[bci + 1];
                        frame.setIntStatic(sp, frame.getIntStatic(localIndex));
                        sp += 1;
                        bci += 2;
                        break;
                    case LOCAL_WRITE:
                    case LOCAL_WRITE0:
                    case LOCAL_WRITE1:
                        localIndex = bc[bci + 1];
                        frame.setIntStatic(localIndex, frame.getIntStatic(sp - 1));
                        sp -= 1;
                        bci += 2;
                        break;
                    case ADD:
                        frame.setIntStatic(sp - 2, frame.getIntStatic(sp - 1) + frame.getIntStatic(sp - 2));
                        sp -= 1;
                        bci += 1;
                        break;
                    case CONST:
                        frame.setIntStatic(sp, bc[bci + 1]);
                        sp += 1;
                        bci += 2;
                        break;
                    case JUMP:
                        bci = bc[bci + 1];
                        break;
                    case JUMP_IF_ZERO:
                        if (frame.getIntStatic(sp - 1) == 0) {
                            bci = bc[bci + 1];
                        } else {
                            bci += 2;
                        }
                        sp -= 1;
                        break;
                    case RETURN:
                        return frame.getIntStatic(sp - 1);
                    default:
                        // propagates transferToInterpeter from within the call
                        throw CompilerDirectives.shouldNotReachHere();
                }
            }

        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public int executeReadStaticTOS(VirtualFrame f) {
            VirtualFrame frame = f;
            short[] bc = this.ops;
            int sp = locals;
            int bci = 0;
            int tos = 0;

            int localIndex;
            while (true) {
                // This loops switches between generic bytecode loop and integer TOS bytecode loop

                // NO TOS
                bciLoop: while (true) {
                    switch (bc[bci]) {
                        case BOUNDARY:
                            boundary(f.materialize());
                            bci += 1;
                            break;
                        case LOCAL_READ:
                        case LOCAL_READ0:
                        case LOCAL_READ1:
                            localIndex = bc[bci + 1];
                            tos = frame.getIntStatic(localIndex);
                            bci += 2;
                            break bciLoop; // goto int TOS
                        case LOCAL_WRITE:
                        case LOCAL_WRITE0:
                        case LOCAL_WRITE1:
                            localIndex = bc[bci + 1];
                            frame.setIntStatic(localIndex, frame.getIntStatic(sp - 1));
                            sp -= 1;
                            bci += 2;
                            break;
                        case ADD:
                            tos = frame.getIntStatic(sp - 1) + frame.getIntStatic(sp - 2);
                            sp -= 2;
                            bci += 1;
                            break bciLoop; // goto int TOS
                        case CONST:
                            tos = bc[bci + 1];
                            bci += 2;
                            break bciLoop; // goto int TOS
                        case JUMP:
                            bci = bc[bci + 1];
                            break;
                        case JUMP_IF_ZERO:
                            if (frame.getIntStatic(sp - 1) == 0) {
                                bci = bc[bci + 1];
                            } else {
                                bci += 2;
                            }
                            sp -= 1;
                            break;
                        case RETURN:
                            return frame.getIntStatic(sp - 1);
                        default:
                            // propagates transferToInterpeter from within the call
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                }

                // integer TOS
                itosLoop: while (true) {
                    switch (bc[bci]) {
                        case BOUNDARY:
                            boundary(f.materialize());
                            bci += 1;
                            break;
                        case LOCAL_READ:
                        case LOCAL_READ0:
                        case LOCAL_READ1:
                            localIndex = bc[bci + 1];
                            frame.setIntStatic(sp, tos);
                            tos = frame.getIntStatic(localIndex);
                            sp += 1;
                            bci += 2;
                            break;
                        case LOCAL_WRITE:
                        case LOCAL_WRITE0:
                        case LOCAL_WRITE1:
                            localIndex = bc[bci + 1];
                            frame.setIntStatic(localIndex, tos);
                            bci += 2;
                            break itosLoop;
                        case ADD:
                            tos = tos + frame.getIntStatic(sp - 1);
                            sp -= 1;
                            bci += 1;
                            break;
                        case CONST:
                            frame.setIntStatic(sp, tos);
                            tos = bc[bci + 1];
                            sp += 1;
                            bci += 2;
                            break;
                        case JUMP:
                            bci = bc[bci + 1];
                            break;
                        case JUMP_IF_ZERO:
                            if (tos == 0) {
                                bci = bc[bci + 1];
                            } else {
                                bci += 2;
                            }
                            break itosLoop;
                        case RETURN:
                            return tos;
                        default:
                            // propagates transferToInterpeter from within the call
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                }
            }
        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public int executeReadStaticRegisters(VirtualFrame f) {
            VirtualFrame frame = f;
            short[] bc = this.regOps;
            int bci = 0;

            int localIndex;
            int localIndex2;
            int resultIndex;
            int value;
            while (true) {
                switch (bc[bci]) {
                    case BOUNDARY:
                        boundary(f.materialize());
                        bci += 1;
                        break;
                    case LOCAL_WRITE:
                    case LOCAL_WRITE0:
                    case LOCAL_WRITE1:
                        // Not used in the benchmark, but for completeness
                        localIndex = bc[bci + 1];
                        value = bc[bci + 1];
                        frame.setIntStatic(localIndex, value);
                        bci += 3;
                        break;
                    case ADD:
                        localIndex = bc[bci + 1];
                        localIndex2 = bc[bci + 2];
                        resultIndex = bc[bci + 3];
                        frame.setIntStatic(resultIndex, frame.getIntStatic(localIndex) + frame.getIntStatic(localIndex2));
                        bci += 4;
                        break;
                    case CONST:
                        localIndex = bc[bci + 1];
                        value = bc[bci + 2];
                        frame.setIntStatic(localIndex, value);
                        bci += 3;
                        break;
                    case JUMP:
                        bci = bc[bci + 1];
                        break;
                    case JUMP_IF_ZERO:
                        value = bc[bci + 1];
                        if (frame.getIntStatic(value) == 0) {
                            bci = bc[bci + 2];
                        } else {
                            bci += 3;
                        }
                        break;
                    case RETURN:
                        return frame.getIntStatic(bc[bci + 1]);
                    default:
                        // propagates transferToInterpeter from within the call
                        throw CompilerDirectives.shouldNotReachHere();
                }
            }

        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public int executeReadStaticRealRegisters(VirtualFrame f) {
            // Assumption: there are 4 registers, indexed from 0 to 3

            VirtualFrame frame = f;
            short[] bc = this.regOps2;
            int bci = 0;

            int reg0 = 0;
            int reg1 = 0;
            int reg2 = 0;
            int reg3 = 0;

            int localIndex;
            int value;
            int value2;
            int regIndex;
            int regIndex1;
            int regIndex2;
            while (true) {
                switch (bc[bci]) {
                    case BOUNDARY:
                        boundary(f.materialize());
                        bci += 1;
                        break;
                    case LOCAL_WRITE:
                    case LOCAL_WRITE0:
                    case LOCAL_WRITE1:
                        // Not used in the benchmark, but for completeness
                        localIndex = bc[bci + 1];
                        regIndex = bc[bci + 1];
                        value = switch (regIndex) {
                            case 0 -> reg0;
                            case 1 -> reg1;
                            case 2 -> reg2;
                            case 3 -> reg3;
                            default -> throw CompilerDirectives.shouldNotReachHere();
                        };
                        frame.setIntStatic(localIndex, value);
                        bci += 3;
                        break;
                    case LOCAL_READ:
                    case LOCAL_READ0:
                    case LOCAL_READ1:
                        // Not used in the benchmark, but for completeness
                        localIndex = bc[bci + 1];
                        regIndex = bc[bci + 1];
                        switch (regIndex) {
                            case 0 -> reg0 = frame.getIntStatic(localIndex);
                            case 1 -> reg1 = frame.getIntStatic(localIndex);
                            case 2 -> reg2 = frame.getIntStatic(localIndex);
                            case 3 -> reg3 = frame.getIntStatic(localIndex);
                            default -> throw CompilerDirectives.shouldNotReachHere();
                        }
                        ;
                        bci += 3;
                        break;
                    case ADD:
                        regIndex = bc[bci + 1];
                        regIndex1 = bc[bci + 2];
                        regIndex2 = bc[bci + 3];
                        value = switch (regIndex1) {
                            case 0 -> reg0;
                            case 1 -> reg1;
                            case 2 -> reg2;
                            case 3 -> reg3;
                            default -> throw CompilerDirectives.shouldNotReachHere();
                        };
                        value2 = switch (regIndex2) {
                            case 0 -> reg0;
                            case 1 -> reg1;
                            case 2 -> reg2;
                            case 3 -> reg3;
                            default -> throw CompilerDirectives.shouldNotReachHere();
                        };
                        switch (regIndex) {
                            case 0 -> reg0 = value + value2;
                            case 1 -> reg1 = value + value2;
                            case 2 -> reg2 = value + value2;
                            case 3 -> reg3 = value + value2;
                        }
                        bci += 4;
                        break;
                    case CONST:
                        regIndex = bc[bci + 1];
                        value = bc[bci + 2];
                        switch (regIndex) {
                            case 0 -> reg0 = value;
                            case 1 -> reg1 = value;
                            case 2 -> reg2 = value;
                            case 3 -> reg3 = value;
                        }
                        bci += 3;
                        break;
                    case JUMP:
                        bci = bc[bci + 1];
                        break;
                    case JUMP_IF_NOT_ZERO:
                        regIndex = bc[bci + 1];
                        value = switch (regIndex) {
                            case 0 -> reg0;
                            case 1 -> reg1;
                            case 2 -> reg2;
                            case 3 -> reg3;
                            default -> throw CompilerDirectives.shouldNotReachHere();
                        };
                        if (value != 0) {
                            bci = bc[bci + 2];
                        } else {
                            bci += 3;
                        }
                        break;
                    case RETURN:
                        regIndex = bc[bci + 1];
                        return switch (regIndex) {
                            case 0 -> reg0;
                            case 1 -> reg1;
                            case 2 -> reg2;
                            case 3 -> reg3;
                            default -> throw CompilerDirectives.shouldNotReachHere();
                        };
                    default:
                        // propagates transferToInterpeter from within the call
                        throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }

    }

}
