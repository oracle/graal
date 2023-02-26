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
             *     sum = sun + 42;
             *     i--;
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

            var b = FrameDescriptor.newBuilder();
            b.addSlots(locals, FrameSlotKind.Int);
            b.addSlots(stack, FrameSlotKind.Illegal);
            dynamicDescriptor = b.build();
            dynamicTestFrame = Truffle.getRuntime().createVirtualFrame(new Object[0], dynamicDescriptor);
            bytecodeNode = new BytecodeNode(ops, locals, stack);

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

    @Benchmark
    @BytecodeInterpreterSwitch
    public void unsafe(BenchmarkState state) throws Throwable {
        state.bytecodeNode.executeUnsafe(null);
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

        @CompilationFinal(dimensions = 1) final short[] ops;
        final int locals;
        final int stackSize;

        BytecodeNode(short[] ops, int locals, int stack) {
            this.ops = ops;
            this.locals = locals;
            this.stackSize = stack;
        }

        static final int MAX_LOCALS = 10;

        static Map<String, String> map = new HashMap<>();

        @TruffleBoundary
        @CompilerControl(Mode.DONT_INLINE)
        static void boundary(MaterializedFrame f) {
            // just have some complicated code here
            map.put("s", "s");
        }

        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public int executeUnsafe(VirtualFrame f) {
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

    }

}
