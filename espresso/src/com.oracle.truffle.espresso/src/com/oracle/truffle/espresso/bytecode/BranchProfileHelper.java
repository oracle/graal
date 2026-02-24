/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.bytecode;

import static com.oracle.truffle.espresso.classfile.bytecode.BytecodeSwitch.getAlignedBci;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.isIfBytecode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.bytecode.Bytes;

public class BranchProfileHelper {
    public static final int[] EMPTY_INFOS = new int[0];

    /**
     * Creates and initializes branch infos for the given bytecode. Modifies code so that the
     * indices of the branch infos is stored within.
     *
     * @param code the bytecode of the method which is modified to store the indices of the branch
     *            infos.
     * @return an array with all branch infos (jump/default offsets) and initial profiles.
     */
    public static int[] initializeBranchInfos(byte[] code) {
        /*
         * Initialize branch and switch profiles with two passes:
         *
         * 1. Get the number of bytes needed to profile IF* and *SWITCH bytecodes.
         *
         * 2. Rewrite the jump offsets for IF* bytecodes and the default offsets of *SWITCH
         * bytecodes to store the index for the mapping array. The overwritten offsets are stored
         * within the mapping array. The profiles for IF* bytecodes are before the profiles of the
         * *SWITCH bytecodes in the array and have to be multiplied by the length of each entry
         * before accessing the profiles. This way we the indices for the IF* bytecodes are
         * guaranteed to fit inside an unsigned short. Given the variable length of *SWITCH infos,
         * their indices can directly be used for indexing.
         */
        BytecodeStream bs = new BytecodeStream(code);
        int curBCI = 0;
        int nrOfIfBytecodes = 0;
        int nrOfSwitchInfoSlots = 0;

        while (curBCI < bs.endBCI()) {
            int opCode = bs.opcode(curBCI);
            if (Bytecodes.isIfBytecode(opCode)) {
                nrOfIfBytecodes++;
            }
            if (opCode == TABLESWITCH || opCode == LOOKUPSWITCH) {
                nrOfSwitchInfoSlots += ENTRIES_BEFORE_CASE_PROFILES + BytecodeSwitch.get(opCode).numberOfCases(bs, curBCI);
            }
            curBCI = bs.nextBCI(curBCI);
        }

        int nrOfSlots = nrOfIfBytecodes * IF_INFO_LENGTH + nrOfSwitchInfoSlots;
        if (nrOfSlots == 0) {
            return EMPTY_INFOS;
        }
        int[] profileInfos = new int[nrOfSlots];

        curBCI = 0;
        int ifIndex = 0;
        /*
         * The infos for IF* bytecodes are stored before the ones for *SWITCH bytecodes. This way,
         * we only need to store the index of the entry and not the absolute offset, as their length
         * is fixed anyway. This guarantees that the index fits in an unsigned short.
         */
        int switchIndex = nrOfIfBytecodes * IF_INFO_LENGTH;
        while (curBCI < bs.endBCI() && (ifIndex < nrOfIfBytecodes || switchIndex < nrOfSlots)) {
            int opCode = bs.opcode(curBCI);
            if (isIfBytecode(opCode)) {
                // write jump offset to branch infos
                int jumpOffset = Bytes.beS2(code, curBCI + 1);
                profileInfos[ifIndex * IF_INFO_LENGTH + JUMP_OFFSET] = jumpOffset;

                // write branch info index to code
                Bytes.beU2(code, curBCI + 1, ifIndex);

                ifIndex++;
            }
            if (opCode == TABLESWITCH || opCode == LOOKUPSWITCH) {
                BytecodeSwitch switchHelper = BytecodeSwitch.get(opCode);

                // write default offset to switch infos
                profileInfos[switchIndex + DEFAULT_OFFSET] = switchHelper.defaultOffset(bs, curBCI);
                // write switch index to code
                Bytes.beS4(code, getAlignedBci(curBCI), switchIndex);

                switchIndex += ENTRIES_BEFORE_CASE_PROFILES + switchHelper.numberOfCases(bs, curBCI);
            }
            curBCI = bs.nextBCI(curBCI);
        }

        return profileInfos;
    }

    /*
     * @formatter:off
     *
     * region IF* profiling
     * 
     * The branch infos contain a jump offset (an int) and integer profiles (2 x int) for each IF*
     * opcode. This is the layout per opcode:
     * int jump offset
     * int true hits
     * int false hits
     *
     * @formatter:on
     */
    private static final int JUMP_OFFSET = 0;
    private static final int TRUE_OFFSET = 1;
    private static final int FALSE_OFFSET = 2;
    private static final int IF_INFO_LENGTH = 3;

    private static final int MAX_PROFILE_VALUE = Integer.MAX_VALUE;

    private static void increaseTrueBranchHit(int[] branchInfos, int baseIndex, int t) {
        CompilerAsserts.neverPartOfCompilation();
        if (t < MAX_PROFILE_VALUE) {
            branchInfos[baseIndex + TRUE_OFFSET] = t + 1;
        }
    }

    private static void increaseFalseBranchHit(int[] branchInfos, int baseIndex, int f) {
        CompilerAsserts.neverPartOfCompilation();
        if (f < MAX_PROFILE_VALUE) {
            branchInfos[baseIndex + FALSE_OFFSET] = f + 1;
        }
    }

    @SuppressWarnings("cast")
    public static boolean profileBranch(BytecodeStream bs, int[] branchInfos, int curBCI, boolean value) {
        int baseIndex = getBranchInfoBaseIndex(bs, curBCI);
        int t = getTrueProfile(branchInfos, baseIndex);
        int f = getFalseProfile(branchInfos, baseIndex);
        if (CompilerDirectives.inInterpreter()) {
            if (value) {
                increaseTrueBranchHit(branchInfos, baseIndex, t);
            } else {
                increaseFalseBranchHit(branchInfos, baseIndex, f);
            }
            return value;
        } else {
            CompilerAsserts.partialEvaluationConstant(t);
            CompilerAsserts.partialEvaluationConstant(f);
            if (t + f > 0) {
                if (t == 0) {
                    if (value) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        increaseTrueBranchHit(branchInfos, baseIndex, t);
                        return true;
                    } else {
                        return false;
                    }
                }
                if (f == 0) {
                    if (value) {
                        return true;
                    } else {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        increaseFalseBranchHit(branchInfos, baseIndex, f);
                        return false;
                    }
                }
                return CompilerDirectives.injectBranchProbability(((double) t) / ((double) t + (double) f), value);
            } else {
                return value;
            }
        }
    }

    public static int readBranchDest(BytecodeStream bs, int[] branchInfos, int curBci) {
        int opcode = bs.opcode(curBci);
        if (Bytecodes.isIfBytecode(opcode)) {
            int index = getBranchInfoBaseIndex(bs, curBci);
            return curBci + branchInfos[index + JUMP_OFFSET];
        } else {
            return bs.readBranchDest(curBci);
        }
    }

    private static int getBranchInfoBaseIndex(BytecodeStream bs, int curBCI) {
        /*
         * Since we only store the index of the entry, we have to multiply the index with the length
         * of each entry.
         */
        return bs.readUShort(curBCI) * IF_INFO_LENGTH;
    }

    private static int getTrueProfile(int[] branchInfos, int branchInfoIndex) {
        return branchInfos[branchInfoIndex + TRUE_OFFSET];
    }

    private static int getFalseProfile(int[] branchInfos, int branchInfoIndex) {
        return branchInfos[branchInfoIndex + FALSE_OFFSET];
    }

    // endregion IF* profiling

    /*
     * @formatter:off
     *
     * region *SWITCH profiling
     *
     * The switch infos contain a default jump offset (int), a default hit counter (int) and hit counters
     * for each case (an int for each of them). This is the layout per opcode:
     * int default jump offset
     * int default hit counter
     * int case 0 hit counter
     * int case 1 hit counter
     * ...
     *
     * @formatter:on
     */
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_HIT_CASE_INDEX = -1;
    private static final int ENTRIES_BEFORE_CASE_PROFILES = 2;

    public static int getSwitchInfoBaseIndex(BytecodeStream bs, int curBCI) {
        // The stored index can directly be used for indexing.
        return bs.readInt(getAlignedBci(curBCI));
    }

    private static int getSwitchProfileIndex(int baseIndex, int index) {
        return baseIndex + ENTRIES_BEFORE_CASE_PROFILES + index;
    }

    public static void registerProfileHit(int[] branchInfos, BytecodeStream bs, int curBCI, int index) {
        CompilerAsserts.neverPartOfCompilation();
        int baseIndex = getSwitchInfoBaseIndex(bs, curBCI);
        int profileCounterIndex = getSwitchProfileIndex(baseIndex, index);
        int profile = branchInfos[profileCounterIndex];

        if (profile < MAX_PROFILE_VALUE) {
            branchInfos[profileCounterIndex] = profile + 1;
        }
    }

    public static void registerDefaultHit(int[] branchInfos, BytecodeStream bs, int curBCI) {
        registerProfileHit(branchInfos, bs, curBCI, DEFAULT_HIT_CASE_INDEX);
    }

    public static boolean injectSwitchProfile(int profileHits, long predecessorHits, long totalHits, boolean condition) {
        CompilerAsserts.partialEvaluationConstant(profileHits);
        CompilerAsserts.partialEvaluationConstant(totalHits);

        if (totalHits == 0) {
            return condition;
        }

        if (profileHits == 0) {
            if (condition) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return true;
            } else {
                return false;
            }
        }

        if (profileHits == totalHits) {
            if (condition) {
                return true;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return false;
            }
        }

        /*
         * The probabilities gathered by profiling are independent of each other. Since we are
         * injecting probabilities into a cascade of if statements, we need to adjust them. The
         * injected probability should indicate the probability that this if statement will be
         * entered given that the previous ones have not been entered. To do that, we keep track of
         * the probability that the preceding if statements have been entered and adjust this
         * statement's probability accordingly. When the compiler decides to generate an
         * IntegerSwitch node from the cascade of if statements, it converts the probabilities back
         * to independent ones.
         */
        double probability = (double) profileHits / Math.max(profileHits, totalHits);
        double predecessorProbability = (double) predecessorHits / Math.max(predecessorHits, totalHits);
        double adjustedProbability = probability / (1 - predecessorProbability);
        return CompilerDirectives.injectBranchProbability(Math.min(adjustedProbability, 1), condition);
    }

    public static int getProfileHits(int[] branchInfos, BytecodeStream bs, int curBCI, int index) {
        int baseIndex = getSwitchInfoBaseIndex(bs, curBCI);
        int profileIndex = getSwitchProfileIndex(baseIndex, index);
        return branchInfos[profileIndex];
    }

    public static int getDefaultHits(int[] branchInfos, BytecodeStream bs, int curBCI) {
        return getProfileHits(branchInfos, bs, curBCI, DEFAULT_HIT_CASE_INDEX);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static long getTotalHits(int[] branchInfos, BytecodeStream bs, int curBCI, int nrOfCases) {
        long totalHits = 0;
        for (int i = DEFAULT_HIT_CASE_INDEX; i < nrOfCases; i++) {
            totalHits += getProfileHits(branchInfos, bs, curBCI, i);
        }
        return totalHits;
    }

    public static int getDefaultTargetBCI(int[] branchInfos, BytecodeStream bs, int curBCI) {
        int baseIndex = getSwitchInfoBaseIndex(bs, curBCI);
        return curBCI + branchInfos[baseIndex + DEFAULT_OFFSET];
    }

    // endregion *SWITCH profiling
}
