/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.counters;

import static com.oracle.max.cri.ci.CiValueUtil.*;

import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.Compiler;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.cri.ci.*;
import com.oracle.max.criutils.*;


public class MethodEntryCounters {
    protected static final class Counter implements Comparable<Counter> {
        protected static ArrayList<Counter> counters = new ArrayList<>();

        protected final String method;
        protected final long[] counts = new long[GraalOptions.MethodEntryCountersCallers * 2 + 2];

        protected long sortCount;

        protected Counter(RiResolvedMethod method) {
            this.method = CiUtil.format("%H.%n", method);
            counters.add(this);
        }

        @Override
        public int compareTo(Counter o) {
            return (int) (o.sortCount - sortCount);
        }
    }


    protected static class AMD64MethodEntryOp extends AMD64LIRInstruction {
        protected static int codeSize;

        protected final Counter counter;

        protected AMD64MethodEntryOp(Counter counter, RiValue counterArr, RiValue callerPc) {
            super("ENTRY_COUNTER", LIRInstruction.NO_OPERANDS, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, new RiValue[] {counterArr, callerPc});
            this.counter = counter;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
            int start = masm.codeBuffer.position();

            RiValue counterArr = temp(0);
            RiValue callerPc = temp(1);

            int off = Unsafe.getUnsafe().arrayBaseOffset(long[].class);
            int scale = Unsafe.getUnsafe().arrayIndexScale(long[].class);

            AMD64Move.move(tasm, masm, counterArr, RiConstant.forObject(counter.counts));
            AMD64Move.load(tasm, masm, callerPc, new CiAddress(RiKind.Long, AMD64.rbp.asValue(RiKind.Long), 8), null);

            Label done = new Label();
            for (int i = 0; i < counter.counts.length - 2; i += 2) {
                CiAddress counterPcAddr = new CiAddress(RiKind.Long, counterArr, i * scale + off);
                CiAddress counterValueAddr = new CiAddress(RiKind.Long, counterArr, (i + 1) * scale + off);

                Label skipClaim = new Label();
                masm.cmpq(counterPcAddr, 0);
                masm.jccb(ConditionFlag.notEqual, skipClaim);
                AMD64Move.store(tasm, masm, counterPcAddr, callerPc, null);
                masm.bind(skipClaim);

                Label skipInc = new Label();
                masm.cmpq(counterPcAddr, asRegister(callerPc));
                masm.jccb(ConditionFlag.notEqual, skipInc);
                masm.addq(counterValueAddr, 1);
                masm.jmp(done);
                masm.bind(skipInc);
            }

            CiAddress counterValueAddr = new CiAddress(RiKind.Long, counterArr, (counter.counts.length - 1) * scale + off);
            masm.addq(counterValueAddr, 1);
            masm.bind(done);

            int size = masm.codeBuffer.position() - start;
            assert codeSize == 0 || codeSize == size;
            codeSize = size;
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Temp && index == 0) {
                return EnumSet.of(OperandFlag.Register);
            } else if (mode == OperandMode.Temp && index == 1) {
                return EnumSet.of(OperandFlag.Register);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }


    public static void emitCounter(LIRGenerator gen, RiResolvedMethod method) {
        if (!GraalOptions.MethodEntryCounters) {
            return;
        }
        gen.append(new AMD64MethodEntryOp(new Counter(method), gen.newVariable(RiKind.Long), gen.newVariable(RiKind.Long)));
    }

    public static int getCodeSize() {
        if (!GraalOptions.MethodEntryCounters) {
            return 0;
        }
        return AMD64MethodEntryOp.codeSize;
    }


    public static void printCounters(Compiler compiler) {
        if (!GraalOptions.MethodEntryCounters) {
            return;
        }
        ArrayList<Counter> copy = new ArrayList<>(Counter.counters);
        long total = 0;
        for (Counter counter : copy) {
            long sum = 0;
            for (int i = 0; i < counter.counts.length; i += 2) {
                sum += counter.counts[i + 1];
            }
            counter.sortCount = sum;
            total += sum;
        }
        Collections.sort(copy);

        TTY.println();
        TTY.println("** Compiled method invocation counters **");
        for (Counter counter : copy) {
            TTY.println("%16d %5.2f%%  %s", counter.sortCount, (double) counter.sortCount / total * 100d, counter.method);

            if (counter.counts.length > 2) {
                for (int i = 0; i < counter.counts.length; i += 2) {
                    if (counter.counts[i] != 0 || counter.counts[i + 1] != 0) {
                        TTY.print("              %16d  %5.2f%%", counter.counts[i + 1], (double) counter.counts[i + 1] / counter.sortCount * 100d);
                        if (counter.counts[i] == 0) {
                            TTY.println("  [other callers]");
                        } else {
                            TTY.println("  %x  %s", counter.counts[i], compiler.getCompilerToVM().decodePC(counter.counts[i]));
                        }
                        counter.counts[i] = 0;
                        counter.counts[i + 1] = 0;
                    }
                }
            }
        }
        TTY.println("** Compiled method invocation counters **");
        TTY.println();
    }
}
