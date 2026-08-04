/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.priorityinline.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.Truffle;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.IndirectNode;
import jdk.graal.compiler.truffle.host.HostInliningPhase;

public class PriorityInliningBytecodeInterpreterTest extends PriorityInliningTest {

    @Test
    public void testInterpreterSwitchPolicy() {
        Truffle.getRuntime(); // init runtime to allow BytecodeInterpreter heuristic to trigger

        byte[] ops = new byte[]{
                        0, 1, 2, 3, 4, 5, 0, 1, 2, 2, 1, 3, 0, 4, 6, 7, 8, 8, 8, 2, 1, 0, 7, 6, 8, 6, 8, 2, 3, 4, 2, 3, 1, 0, 9, 10, 11, 17, 12, 16, 14, 12, 13, 10, 19, 18, 17, 20, 21, 22, 27, 25, 26,
                        1, 2, 10, 1, 23, 22, 24, 29, 30, 22, 28, 27, 30, 31, 42, 41, 33, 44, 50, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50
        };

        OptionValues options = getInitialOptions().derive(PriorityInliningPhase.Options.PriorityInliningTuningPolicy, "BytecodeInterpreter").derive(HostInliningPhase.Options.TruffleHostInlining,
                        Boolean.FALSE);

        test(options, "interpreterSwitch", ops);

        CallTree callTree = getCallTreeAfterInlining("interpreterSwitch", options);
        Assert.assertTrue(callTree.root().getReadonlySubgraph().getNodeCount() > 1000);
        for (CallTreeNode child : callTree.root().children()) {
            final String fullName = child.targetMethod().format("%H.%n");
            if (child instanceof CutoffNode) {
                Assert.assertTrue("Cutoff must be createException: " + fullName, fullName.contains("createException"));
            } else if (child instanceof IndirectNode) {
                boolean validIndirect = fullName.contains("ArrayList") || fullName.contains("Object.hashCode");
                Assert.assertTrue("Unexpected indirect node: " + fullName, validIndirect);
            } else {
                Assert.fail("Unexpected node type found in the call tree: " + child);
            }
        }
    }

    @SuppressWarnings("unused")
    @BytecodeInterpreterSwitch
    public long interpreterSwitch(byte[] ops) {
        State state = new State();
        long checksum = 0;
        int i = 0;
        while (i < ops.length) {
            switch (ops[i++]) {
                case 0:
                    checksum += op0(state, ops[i++]);
                    break;
                case 1:
                    checksum += op1(state, ops[i++]);
                    break;
                case 2:
                    checksum += op2(state, ops[i++]);
                    break;
                case 3:
                    checksum += op3(state, ops[i++]);
                    break;
                case 4:
                    checksum += op4(state, ops[i++]);
                    break;
                case 5:
                    checksum += op5(state, ops[i++]);
                    break;
                case 6:
                    checksum += op6(state, ops[i++]);
                    break;
                case 7:
                    checksum += op7(state, ops[i++]);
                    break;
                case 8:
                    checksum += op8(state);
                    break;
                case 9:
                    checksum += op9(state);
                    break;
                case 10:
                    checksum += opgen(10, state);
                    break;
                case 11:
                    checksum += opgen(11, state);
                    break;
                case 12:
                    checksum += opgen(12, state);
                    break;
                case 13:
                    checksum += opgen(13, state);
                    break;
                case 14:
                    checksum += opgen(14, state);
                    break;
                case 15:
                    checksum += opgen(15, state);
                    break;
                case 16:
                    checksum += opgen(16, state);
                    break;
                case 17:
                    checksum += opgen(17, state);
                    break;
                case 18:
                    checksum += opgen(18, state);
                    break;
                case 19:
                    checksum += opgen(19, state);
                    break;
                case 20:
                    checksum += op20(state);
                    break;
                case 21:
                    checksum += op21(state);
                    break;
                case 22:
                    checksum += op22(state);
                    break;
                case 23:
                    checksum += op23(state);
                    break;
                case 24:
                    checksum += op24(state);
                    break;
                case 25:
                    checksum += op25(state);
                    break;
                case 26:
                    checksum += op26(state);
                    break;
                case 27:
                    checksum += op27(state);
                    break;
                case 28:
                    checksum += op28(state);
                    break;
                case 29:
                    checksum += op29(state);
                    break;
                case 30:
                    checksum += op30(state);
                    break;
                case 31:
                    checksum += op31(state);
                    break;
                case 32:
                    checksum += op32(state);
                    break;
                case 33:
                    checksum += op33(state);
                    break;
                case 34:
                    checksum += op34(state);
                    break;
                case 35:
                    checksum += op35(state);
                    break;
                case 36:
                    checksum += op36(state);
                    break;
                case 37:
                    checksum += op37(state);
                    break;
                case 38:
                    checksum += op38(state);
                    break;
                case 39:
                    checksum += op39(state);
                    break;
                case 40:
                    checksum += op40(state);
                    break;
                case 41:
                    checksum += op41(state);
                    break;
                case 42:
                    checksum += op42(state);
                    break;
                case 43:
                    checksum += op43(state);
                    break;
                case 44:
                    checksum += op44(state);
                    break;
                case 45:
                    checksum += op45(state);
                    break;
                case 46:
                    checksum += op46(state);
                    break;
                case 47:
                    checksum += op47(state);
                    break;
                case 48:
                    checksum += op48(state);
                    break;
                case 49:
                    checksum += op49(state);
                    break;
                case 50:
                    checksum += op50(state);
                    break;
            }
        }
        return checksum;
    }

    private static int op0(State state, byte x) {
        return 400 + x + state.counter++;
    }

    private static int op1(State state, byte x) {
        state.statistic += 0.01;
        return state.stack.size() * x;
    }

    private static int op2(State state, byte x) {
        return (int) (state.statistic * x);
    }

    private static int op3(State state, byte x) {
        state.stack.add(x);
        return state.stack.size() * x;
    }

    private static int op4(State state, byte x) {
        if (state.stack.isEmpty()) {
            return x;
        }
        return state.stack.get(state.stack.size() - 1);
    }

    private static int op5(State state, byte x) {
        if (state.stack.isEmpty()) {
            throw createException(x);
        }
        return 0;
    }

    private static int op6(State state, byte x) {
        if (state.stack.isEmpty()) {
            return x;
        }
        state.stack.remove(state.stack.size() - 1);
        return state.stack.size();
    }

    private static int op7(State state, byte x) {
        state.counter++;
        return (int) (state.statistic + x);
    }

    private static int op8(State state) {
        state.counter++;
        state.statistic += 0.25;
        return (int) state.statistic;
    }

    private static int op9(State state) {
        state.statistic -= 0.01;
        state.counter++;
        return state.counter;
    }

    private static int opgen(int index, State state) {
        state.counter++;
        switch ((index + state.counter) % 10 + 10) {
            case 10:
                return state.counter;
            case 11:
                state.stack.add(index);
                return state.stack.size();
            case 12:
                state.stack.clear();
                return state.counter;
            case 13:
                return (int) (2 * state.statistic);
            case 14:
                return state.stack.isEmpty() ? 1 : 0;
            case 15:
                state.stack.add(17);
                return state.stack.size();
            case 16:
                state.counter--;
                state.statistic += 0.15;
                return state.stack.isEmpty() ? -1 : state.stack.get(0);
            case 17:
                state.counter += 2;
                return 11;
            case 18:
                return (int) state.statistic * state.counter;
            case 19:
                return (int) (state.statistic + state.counter + state.stack.size());
            default:
                throw createException((byte) index);
        }
    }

    private static int op20(State state) {
        state.stack.add(20);
        state.stack.add(state.counter);
        return state.stack.hashCode();
    }

    private static int op21(State state) {
        if (state.stack.size() >= 2) {
            state.stack.remove(state.stack.size() - 1);
            state.stack.remove(state.stack.size() - 1);
        }
        return state.stack.size();
    }

    private static int op22(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) + state.stack.remove(state.stack.size() - 2);
        }
        return -1000;
    }

    private static int op23(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) - state.stack.remove(state.stack.size() - 2);
        }
        return -1000;
    }

    private static int op24(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) * state.stack.remove(state.stack.size() - 2);
        }
        return -1000;
    }

    private static int op25(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) / state.stack.remove(state.stack.size() - 2);
        }
        return -1000;
    }

    private static int op26(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) % state.stack.remove(state.stack.size() - 2);
        }
        return -1000;
    }

    private static int op27(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) < state.stack.remove(state.stack.size() - 2) ? 1 : 0;
        }
        return -1000;
    }

    private static int op28(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) <= state.stack.remove(state.stack.size() - 2) ? 1 : 0;
        }
        return -1000;
    }

    private static int op29(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) & state.stack.remove(state.stack.size() - 2);
        }
        return -1000;
    }

    private static int op30(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) ^ state.stack.remove(state.stack.size() - 2);
        }
        return -1000;
    }

    private static int op31(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) | state.stack.remove(state.stack.size() - 2);
        }
        return -1000;
    }

    private static int op32(State state) {
        if (state.stack.size() >= 2) {
            return state.stack.remove(state.stack.size() - 1) != 0 ? state.stack.remove(state.stack.size() - 2) : 0;
        }
        return -1000;
    }

    private static int op33(State state) {
        if (state.stack.size() >= 1) {
            return -state.stack.remove(state.stack.size() - 1);
        }
        return -1000;
    }

    private static int op34(State state) {
        if (state.stack.size() >= 1) {
            return ~state.stack.remove(state.stack.size() - 1);
        }
        return -1000;
    }

    private static int op35(State state) {
        if (state.stack.size() >= 1) {
            return !(state.stack.remove(state.stack.size() - 1) == 0) ? 1 : 0;
        }
        return -1000;
    }

    private static int op36(State state) {
        if (state.stack.size() != 0) {
            state.stack.add(state.stack.get(state.stack.size() - 1));
            return 1;
        }
        return -1000;
    }

    private static int op37(State state) {
        int sum = 0;
        for (int i = 0; i < state.stack.size(); i++) {
            sum = state.stack.get(i);
        }
        return sum;
    }

    private static int op38(State state) {
        int sum = 0;
        for (int i = 0; i < state.counter; i++) {
            sum = (int) (state.statistic * state.stack.size());
        }
        return sum;
    }

    private static int op39(State state) {
        return (int) (state.counter + state.statistic * state.hashCode());
    }

    private static int op40(State state) {
        return (int) (state.hashCode() * state.statistic);
    }

    private static int op41(State state) {
        return (int) (state.counter * state.statistic * 41);
    }

    private static int op42(State state) {
        return (int) (state.counter * state.statistic * 42);
    }

    private static int op43(State state) {
        return (int) (state.counter * state.statistic * 43);
    }

    private static int op44(State state) {
        return (int) (state.counter * state.statistic * 44);
    }

    private static int op45(State state) {
        return (int) (state.counter * state.statistic * 45);
    }

    private static int op46(State state) {
        return (int) (state.counter * state.statistic * 46);
    }

    private static int op47(State state) {
        return (int) (state.counter * state.statistic * 47);
    }

    private static int op48(State state) {
        return (int) (state.counter * state.statistic * 48);
    }

    private static int op49(State state) {
        return (int) (state.counter * state.statistic * 49);
    }

    private static int op50(State state) {
        return state.stack.size() * 50;
    }

    @TruffleBoundary
    private static RuntimeException createException(byte x) {
        return new RuntimeException("Invalid stack: " + x);
    }

    private final class State {
        private int counter = 0;
        private double statistic = 0.0;
        // Checkstyle: stop
        private Stack stack = new Stack();
        // Checkstyle: resume

        @Override
        public int hashCode() {
            return Integer.hashCode(counter) ^ Double.hashCode(statistic) ^ stack.hashCode();
        }
    }

    class Stack {
        private int[] array;
        private int size;

        Stack() {
            this.clear();
        }

        int size() {
            return size;
        }

        boolean isEmpty() {
            return size() == 0;
        }

        void add(int x) {
            if (size == array.length) {
                int[] narray = new int[array.length * 2];
                System.arraycopy(array, 0, narray, 0, array.length);
                array = narray;
            }
            array[size] = x;
            size++;
        }

        int get(int index) {
            return array[index];
        }

        int remove(int index) {
            int result = array[index];
            for (int i = index + 1; i < size; i++) {
                array[i - 1] = array[i];
            }
            size--;
            return result;
        }

        void clear() {
            array = new int[4];
            size = 0;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            for (int i = 0; i < size; i++) {
                hash ^= i;
            }
            return size ^ hash;
        }
    }
}
