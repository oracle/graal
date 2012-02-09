/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.max.cri.ci.CiValueUtil.*;
import static com.oracle.max.graal.alloc.util.LocationUtil.*;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.lir.*;
import com.oracle.max.graal.lir.LIRInstruction.*;
import com.oracle.max.graal.lir.cfg.*;

public final class RegisterVerifier {
    private final FrameMap frameMap;

    /**
     * All blocks that must be processed.
     */
    private final List<Block> workList;

    /**
     * Saved information of previous check.
     * <br>
     * State mapping: mapping from registers and stack slots ({@link CiRegister} and {@link Integer} stack slot offsets) to the
     * value that is currently contained in there ({@link Location} for operands that were variables; {@link CiRegisterValue} or
     * {@link CiStackSlot} for operands that used fixed registers or stack slots).
     */
    private final Map<Object, CiValue>[] blockStates;

    private void addToWorkList(Block block) {
        if (!workList.contains(block)) {
            workList.add(block);
        }
    }

    private Map<Object, CiValue> stateFor(Block block) {
        return blockStates[block.getId()];
    }

    private void setStateFor(Block block, Map<Object, CiValue> savedState) {
        blockStates[block.getId()] = savedState;
    }

    private static Map<Object, CiValue> copy(Map<Object, CiValue> inputState) {
        return new HashMap<>(inputState);
    }

    public static boolean verify(LIR lir, FrameMap frameMap) {
        RegisterVerifier verifier = new RegisterVerifier(lir, frameMap);
        verifier.verify(lir.cfg.getStartBlock());
        return true;
    }

    @SuppressWarnings("unchecked")
    private RegisterVerifier(LIR lir, FrameMap frameMap) {
        this.frameMap = frameMap;
        this.workList = new LinkedList<>();
        this.blockStates = new Map[lir.linearScanOrder().size()];
    }

    private Map<Object, CiValue> curInputState;

    private void verify(Block startBlock) {
        ValueProcedure useProc =    new ValueProcedure() { @Override public CiValue doValue(CiValue value, OperandMode mode, EnumSet<OperandFlag> flags) { return use(value, flags); } };
        ValueProcedure tempProc =   new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return temp(value); } };
        ValueProcedure outputProc = new ValueProcedure() { @Override public CiValue doValue(CiValue value) { return output(value); } };

        curInputState = new HashMap<>();
        setStateFor(startBlock, curInputState);
        addToWorkList(startBlock);

        Debug.log("==== start verify register allocation ====");
        do {
            Block block = workList.remove(0);

            // Must copy state because it is modified.
            curInputState = copy(stateFor(block));
            Debug.log("start block %s %s", block, block.getLoop());
            Debug.log(logCurrentState());

            for (LIRInstruction op : block.lir) {
                Debug.log("  op %d %s", op.id(), op);

                op.forEachInput(useProc);
                if (op.hasCall()) {
                    invalidateRegisters();
                }
                op.forEachAlive(useProc);
                op.forEachState(useProc);
                op.forEachTemp(tempProc);
                op.forEachOutput(outputProc);
            }

            for (Block succ : block.getSuccessors()) {
                processSuccessor(succ);
            }

            Debug.log("end block %s", block);
        } while (!workList.isEmpty());
        Debug.log("==== end verify register allocation ====");
    }

    private void processSuccessor(Block succ) {
        Map<Object, CiValue> savedState = stateFor(succ);
        if (savedState == null) {
            // Block was not processed before, so set initial inputState.
            Debug.log("  successor %s: initial visit", succ);
            setStateFor(succ, copy(curInputState));
            addToWorkList(succ);

        } else {
            // This block was already processed before.
            // Check if new inputState is consistent with savedState.
            Debug.log("  successor %s: state present", succ);
            Iterator<Map.Entry<Object, CiValue>> iter = savedState.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Object, CiValue> entry = iter.next();
                CiValue savedValue = entry.getValue();
                CiValue inputValue = curInputState.get(entry.getKey());

                if (savedValue != inputValue) {
                    // Current inputState and previous savedState assume a different value in this register.
                    // Assume that this register is invalid and remove it from the saved state.
                    Debug.log("    invalididating %s because it is inconsistent with %s", savedValue, inputValue);
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
            if (value1 instanceof CiRegister && frameMap.registerConfig.getAttributesMap()[((CiRegister) value1).number].isCallerSave) {
                Debug.log("    remove caller save register %s", value1);
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
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private boolean isIgnoredRegister(CiValue value) {
        return isRegister(value) && !frameMap.registerConfig.getAttributesMap()[asRegister(value).number].isAllocatable;
    }

    private CiValue use(CiValue value, EnumSet<OperandFlag> flags) {
        if (!isConstant(value) && value != CiValue.IllegalValue && !isIgnoredRegister(value)) {
            CiValue actual = curInputState.get(key(value));
            if (actual == null && flags.contains(OperandFlag.Uninitialized)) {
                // OK, since uninitialized values are allowed explicitly.
            } else if (value != actual) {
                Debug.log("Error in register allocation: %s != %s for key %s", value, actual, key(value));
                Debug.log(logCurrentState());
                throw GraalInternalError.shouldNotReachHere();
            }
        }
        return value;
    }

    private CiValue temp(CiValue value) {
        if (!isConstant(value) && value != CiValue.IllegalValue && !isIgnoredRegister(value)) {
            Debug.log("    temp %s -> remove key %s", value, key(value));
            curInputState.remove(key(value));
        }
        return value;
    }

    private CiValue output(CiValue value) {
        if (value != CiValue.IllegalValue && !isIgnoredRegister(value)) {
            Debug.log("    output %s -> set key %s", value, key(value));
            curInputState.put(key(value), value);
        }
        return value;
    }


    private String logCurrentState() {
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

        StringBuilder sb = new StringBuilder("    state: ");
        for (Object key : keys) {
            sb.append(key).append("=").append(curInputState.get(key)).append(" ");
        }
        return sb.toString();
    }
}
