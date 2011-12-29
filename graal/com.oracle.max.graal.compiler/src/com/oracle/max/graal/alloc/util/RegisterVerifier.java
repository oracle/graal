/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.alloc.util;

import static com.oracle.max.graal.alloc.util.ValueUtil.*;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.LIRInstruction.ValueProcedure;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public final class RegisterVerifier {
    private final FrameMap frameMap;
    private final RiRegisterConfig registerConfig;

    /**
     * All blocks that must be processed.
     */
    private final List<LIRBlock> workList;

    /**
     * Saved information of previous check.
     * <br>
     * State mapping: mapping from registers and stack slots ({@link CiRegister} and {@link Integer} stack slot offsets) to the
     * value that is currently contained in there ({@link Location} for operands that were variables; {@link CiRegisterValue} or
     * {@link CiStackSlot} for operands that used fixed registers or stack slots).
     */
    private final Map<Object, CiValue>[] blockStates;

    private void addToWorkList(LIRBlock block) {
        if (!workList.contains(block)) {
            workList.add(block);
        }
    }

    private Map<Object, CiValue> stateFor(LIRBlock block) {
        return blockStates[block.blockID()];
    }

    private void setStateFor(LIRBlock block, Map<Object, CiValue> savedState) {
        blockStates[block.blockID()] = savedState;
    }

    private static Map<Object, CiValue> copy(Map<Object, CiValue> inputState) {
        return new HashMap<>(inputState);
    }

    public static boolean verify(LIR lir, CiCallingConvention incomingArguments, FrameMap frameMap, RiRegisterConfig registerConfig) {
        RegisterVerifier verifier = new RegisterVerifier(lir, frameMap, registerConfig);
        verifier.verify(lir.startBlock(), incomingArguments);
        return true;
    }

    @SuppressWarnings("unchecked")
    private RegisterVerifier(LIR lir, FrameMap frameMap, RiRegisterConfig registerConfig) {
        this.frameMap = frameMap;
        this.registerConfig = registerConfig;
        this.workList = new LinkedList<>();
        this.blockStates = new Map[lir.linearScanOrder().size()];
    }

    private Map<Object, CiValue> curInputState;

    private void verify(LIRBlock startBlock, CiCallingConvention incomingArguments) {
        ValueProcedure useProc =    new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return use(value); } };
        ValueProcedure tempProc =   new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return temp(value); } };
        ValueProcedure outputProc = new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return output(value); } };

        curInputState = new HashMap<>();
        for (CiValue value : incomingArguments.locations) {
            curInputState.put(key(value), value);
        }
        setStateFor(startBlock, curInputState);
        addToWorkList(startBlock);

        trace(1, "==== start verify register allocation ====");
        do {
            LIRBlock block = workList.remove(0);
            assert block.phis == null : "phi functions must have been resolved with moves";

            // Must copy state because it is modified.
            curInputState = copy(stateFor(block));
            trace(1, "start block %s  loop %d depth %d", block, block.loopIndex(), block.loopDepth());
            traceState();

            for (LIRInstruction op : block.lir()) {
                trace(2, "  op %d %s", op.id(), op);

                op.forEachInput(useProc);
                if (op.hasCall()) {
                    invalidateRegisters();
                }
                op.forEachAlive(useProc);
                op.forEachState(useProc);
                op.forEachTemp(tempProc);
                op.forEachOutput(outputProc);
            }

            for (LIRBlock succ : block.getLIRSuccessors()) {
                processSuccessor(succ);
            }

            trace(1, "end block %s", block);
        } while (!workList.isEmpty());
        trace(1, "==== end verify register allocation ====");
    }

    private void processSuccessor(LIRBlock succ) {
        Map<Object, CiValue> savedState = stateFor(succ);
        if (savedState == null) {
            // Block was not processed before, so set initial inputState.
            trace(2, "  successor %s: initial visit", succ);
            setStateFor(succ, copy(curInputState));
            addToWorkList(succ);

        } else {
            // This block was already processed before.
            // Check if new inputState is consistent with savedState.
            trace(2, "  successor %s: state present", succ);
            Iterator<Map.Entry<Object, CiValue>> iter = savedState.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Object, CiValue> entry = iter.next();
                CiValue savedValue = entry.getValue();
                CiValue inputValue = curInputState.get(entry.getKey());

                if (savedValue != inputValue) {
                    // Current inputState and previous savedState assume a different value in this register.
                    // Assume that this register is invalid and remove it from the saved state.
                    trace(2, "    invalididating %s because it is inconsistent with %s", savedValue, inputValue);
                    iter.remove();
                    // Must re-visit this block.
                    addToWorkList(succ);
                }
            }
        }
    }

    private void invalidateRegisters() {
        // Invalidate all caller save registers at calls.
        Iterator<Object> iter = curInputState.keySet().iterator();
        while (iter.hasNext()) {
            Object value1 = iter.next();
            if (value1 instanceof CiRegister && registerConfig.getAttributesMap()[((CiRegister) value1).number].isCallerSave) {
                trace(2, "    remove caller save register %s", value1);
                iter.remove();
            }
        }
    }

    /**
     * Gets the mapping key for a value. The key should be as narrow as possible, e.g., it should not
     * include the kind of the value because we do not want to distinguish between the same register with
     * different kinds.
     */
    private Object key(CiValue value) {
        if (isLocation(value)) {
            return key(asLocation(value).location);
        } else if (isRegister(value)) {
            return asRegister(value);
        } else if (isStackSlot(value)) {
            return Integer.valueOf(frameMap.offsetForStackSlot(asStackSlot(value)));
        } else {
            throw Util.shouldNotReachHere();
        }
    }

    private boolean isIgnoredRegister(CiValue value) {
        return isRegister(value) && !registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable;
    }

    private CiValue use(CiValue value) {
        if (!isConstant(value) && value != CiValue.IllegalValue && !isIgnoredRegister(value)) {
            CiValue actual = curInputState.get(key(value));
            if (value != actual) {
                TTY.println("!! Error in register allocation: %s != %s for key %s", value, actual, key(value));
                traceState();
                throw Util.shouldNotReachHere();
            }
        }
        return value;
    }

    private CiValue temp(CiValue value) {
        trace(2, "    temp %s -> remove key %s", value, key(value));
        curInputState.remove(key(value));
        return value;
    }

    private CiValue output(CiValue value) {
        trace(2, "    output %s -> set key %s", value, key(value));
        curInputState.put(key(value), value);
        return value;
    }


    private void traceState() {
        if (GraalOptions.TraceRegisterAllocationLevel >= 2) {
            ArrayList<Object> keys = new ArrayList<>(curInputState.keySet());
            Collections.sort(keys, new Comparator<Object>() {
                @Override
                public int compare(Object o1, Object o2) {
                    if (o1 instanceof CiRegister) {
                        if (o2 instanceof CiRegister) {
                            return ((CiRegister) o1).number - ((CiRegister) o2).number;
                        } else {
                            return -1;
                        }
                    } else {
                        if (o2 instanceof CiRegister) {
                            return 1;
                        } else {
                            return ((Integer) o1).intValue() - ((Integer) o2).intValue();
                        }
                    }
                }
            });

            TTY.print("    state: ");
            for (Object key : keys) {
                TTY.print("%s=%s  ", key, curInputState.get(key));
            }
            TTY.println();
        }
    }

    private static void trace(int level, String format, Object...args) {
        if (GraalOptions.TraceRegisterAllocationLevel >= level) {
            TTY.println(format, args);
        }
    }
}
