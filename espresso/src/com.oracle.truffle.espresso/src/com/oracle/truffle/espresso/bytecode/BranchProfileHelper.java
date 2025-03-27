package com.oracle.truffle.espresso.bytecode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeSwitch;
import com.oracle.truffle.espresso.classfile.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.bytecode.Bytes;

import static com.oracle.truffle.espresso.classfile.bytecode.BytecodeSwitch.getAlignedBci;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.classfile.bytecode.Bytecodes.isIfBytecode;

public class BranchProfileHelper {
    public static final byte[] EMPTY_INFOS = new byte[0];

    /**
     * Creates and initializes branch infos for the given bytecode. Modifies code so that the
     * indices of the branch infos is stored within.
     *
     * @param bs the bytecode stream of the method to profile the branches of.
     * @param code the bytecode of the method which is modified to store the indices of the branch
     *            infos.
     * @param originalCode the original bytecode of the method.
     * @return an array with all branch infos (jump/default offsets) and initial profiles.
     */
    public static byte[] initializeBranchInfos(BytecodeStream bs, byte[] code, byte[] originalCode) {
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
         * guaranteed to fit inside an unsigned short. Since the variable length of *SWITCH infos,
         * their indices can directly be used for indexing.
         */
        int curBCI = 0;
        int nrOfIfBytecodes = 0;
        int nrOfSwitchBytes = 0;
        while (curBCI < originalCode.length) {
            int opCode = bs.opcode(curBCI);
            if (Bytecodes.isIfBytecode(opCode)) {
                nrOfIfBytecodes++;
            }
            if (opCode == TABLESWITCH || opCode == LOOKUPSWITCH) {
                nrOfSwitchBytes += (ENTRIES_BEFORE_CASE_PROFILES + BytecodeSwitch.get(opCode).numberOfCases(bs, curBCI)) * BYTES_PER_ENTRY;
            }
            curBCI = bs.nextBCI(curBCI);
        }

        int nrOfBytes = nrOfIfBytecodes * IF_INFO_LENGTH + nrOfSwitchBytes;
        if (nrOfBytes == 0) {
            return EMPTY_INFOS;
        }
        byte[] profileInfos = new byte[nrOfBytes];

        curBCI = 0;
        int ifIndex = 0;
        // The infos for IF* bytecodes are stored before the ones for *SWITCH bytecodes. This way,
        // we only need to store the index of the entry and not the absolute offset, as their length
        // is fixed anyway. This guarantees that the index fits in an unsigned short.
        int switchIndex = nrOfIfBytecodes * IF_INFO_LENGTH;
        while (curBCI < originalCode.length && (ifIndex < nrOfIfBytecodes || switchIndex < nrOfBytes)) {
            int opCode = bs.opcode(curBCI);
            if (isIfBytecode(opCode)) {
                // write jump offset to branch infos
                int jumpOffset = Bytes.beS2(code, curBCI + 1);
                Bytes.beS2(profileInfos, ifIndex * IF_INFO_LENGTH + JUMP_OFFSET, jumpOffset);

                // write branch info index to code
                Bytes.beU2(code, curBCI + 1, ifIndex);

                ifIndex++;
            }
            if (opCode == TABLESWITCH || opCode == LOOKUPSWITCH) {
                BytecodeSwitch switchHelper = BytecodeSwitch.get(opCode);

                // write default offset to switch infos
                Bytes.beS4(profileInfos, switchIndex, switchHelper.defaultOffset(bs, curBCI));
                // write switch index to code
                Bytes.beS4(code, getAlignedBci(curBCI), switchIndex);

                switchIndex += (ENTRIES_BEFORE_CASE_PROFILES + switchHelper.numberOfCases(bs, curBCI)) * BYTES_PER_ENTRY;
            }
            curBCI = bs.nextBCI(curBCI);
        }

        return profileInfos;
    }

    // region IF* profiling

    // The branch infos contain a jump offset (2 bytes) and integer profiles (2 x 4 bytes) for each
    // IF* opcode. This is the layout per opcode:
    // S2 jump offset
    // S4 true hits
    // S4 false hits
    public static final int JUMP_OFFSET = 0;
    private static final int TRUE_OFFSET = 2;
    private static final int FALSE_OFFSET = 6;
    public static final int IF_INFO_LENGTH = 10;

    private static final int MAX_PROFILE_VALUE = Integer.MAX_VALUE;

    public static boolean profileBranch(BytecodeStream bs, byte[] branchInfos, int curBCI, boolean value) {
        int index = getBranchInfoIndex(bs, curBCI);
        int t = getTrueProfile(branchInfos, index);
        int f = getFalseProfile(branchInfos, index);
        boolean val = value;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < MAX_PROFILE_VALUE) {
                    t++;
                } else {
                    f = ((f >>> 1) + (f & 0x1));
                    t = (MAX_PROFILE_VALUE >>> 1) + 1;
                    registerFalseProfileHit(branchInfos, index, f);
                }
                registerTrueProfileHit(branchInfos, index, t);
                return val;
            } else {
                if (f == 0) {
                    // Make this branch fold during PE
                    val = true;
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (CompilerDirectives.inInterpreter()) {
                if (f < MAX_PROFILE_VALUE) {
                    f++;
                } else {
                    t = ((t >>> 1) + (t & 0x1));
                    f = (MAX_PROFILE_VALUE >>> 1) + 1;
                    registerTrueProfileHit(branchInfos, index, t);
                }
                registerFalseProfileHit(branchInfos, index, f);
                return val;
            } else {
                if (t == 0) {
                    // Make this branch fold during PE
                    val = false;
                }
            }
        }
        return CompilerDirectives.injectBranchProbability((double) t / ((double) t + (double) f), val);
    }

    public static int readBranchDest(BytecodeStream bs, byte[] branchInfos, int curBci) {
        int opcode = bs.opcode(curBci);
        if (Bytecodes.isIfBytecode(opcode)) {
            int index = getBranchInfoIndex(bs, curBci);
            return curBci + Bytes.beS2(branchInfos, index + JUMP_OFFSET);
        } else {
            return bs.readBranchDest(curBci);
        }
    }

    private static int getBranchInfoIndex(BytecodeStream bs, int curBCI) {
        // Since we only store the index of the entry, we have to multiply the index with the length of each entry.
        return bs.readUShort(curBCI) * IF_INFO_LENGTH;
    }

    private static int getTrueProfile(byte[] branchInfos, int branchInfoIndex) {
        return Bytes.beS4(branchInfos, branchInfoIndex + TRUE_OFFSET);
    }

    private static int getFalseProfile(byte[] branchInfos, int branchInfoIndex) {
        return Bytes.beS4(branchInfos, branchInfoIndex + FALSE_OFFSET);
    }

    private static void registerTrueProfileHit(byte[] branchInfos, int branchInfoIndex, int val) {
        Bytes.beS4(branchInfos, branchInfoIndex + TRUE_OFFSET, val);
    }

    private static void registerFalseProfileHit(byte[] branchInfos, int branchInfoIndex, int val) {
        Bytes.beS4(branchInfos, branchInfoIndex + FALSE_OFFSET, val);
    }

    // endregion IF* profiling

    // region *SWITCH profiling

    // The switch infos contain a default offset (4 bytes), a default hit counter (4 bytes) and hit
    // counters for each case (each 4 bytes). This is the layout per opcode:
    // S4 default offset
    // S4 default hit counter
    // S4 case 1 hit counter
    // S4 ...
    public static final int DEFAULT_OFFSET = 0;
    public static final int BYTES_PER_ENTRY = 4;
    public static final int DEFAULT_HITS_OFFSET = BYTES_PER_ENTRY;
    public static final int ENTRIES_BEFORE_CASE_PROFILES = 2;
    public static final int CASE_HITS_OFFSET = BYTES_PER_ENTRY * ENTRIES_BEFORE_CASE_PROFILES;

    public static int getSwitchInfoIndex(BytecodeStream bs, int curBCI) {
        // The stored index can directly be used for indexing.
        return bs.readInt(getAlignedBci(curBCI));
    }

    public static void registerProfileHit(byte[] branchInfos, int branchInfoIndex, int nrOfCases, int index) {
        CompilerAsserts.neverPartOfCompilation();
        int profileIndex = getProfileIndex(branchInfoIndex, index);
        int profile = Bytes.beS4(branchInfos, profileIndex);

        if (profile == MAX_PROFILE_VALUE) {
            halveCounters(branchInfos, branchInfoIndex, nrOfCases);
        }

        profile = Bytes.beS4(branchInfos, profileIndex);
        assert profile < MAX_PROFILE_VALUE;
        Bytes.beS4(branchInfos, profileIndex, profile + 1);
    }

    public static void registerDefaultHit(byte[] branchInfos, int branchInfoIndex, int nrOfCases) {
        registerProfileHit(branchInfos, branchInfoIndex, nrOfCases, -1);
    }

    public static void halveCounters(byte[] branchInfos, int branchInfoIndex, int nrOfCases) {
        CompilerAsserts.neverPartOfCompilation();
        for (int i = -1; i < nrOfCases; i++) {
            int profile = getProfileHits(branchInfos, branchInfoIndex, i);
            Bytes.beS4(branchInfos, getProfileIndex(branchInfoIndex, i), (profile >>> 1) + (profile & 1));
        }
    }

    public static boolean injectSwitchProfile(int profileHits, long predecessorHits, long totalHits, boolean condition) {
        boolean val = condition;
        if (val) {
            if (profileHits == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (profileHits == totalHits) {
                // Make this branch fold during PE
                val = true;
            }
        } else {
            if (profileHits == totalHits) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (profileHits == 0) {
                // If the profile is 0 there is no need for the calculation below. Additionally, the
                // predecessor probability may be 1 which could lead to a division by 0 later.
                return CompilerDirectives.injectBranchProbability(0.0, false);
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
        return CompilerDirectives.injectBranchProbability(Math.min(adjustedProbability, 1), val);
    }

    private static int getProfileIndex(int branchInfoIndex, int index) {
        return branchInfoIndex + CASE_HITS_OFFSET + index * BYTES_PER_ENTRY;
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static long getTotalHits(byte[] branchInfos, int branchInfoIndex, int nrOfCases) {
        long totalHits = 0;
        for (int i = -1; i < nrOfCases; i++) {
            totalHits += getProfileHits(branchInfos, branchInfoIndex, i);
        }
        return totalHits;
    }

    public static int getDefaultHits(byte[] branchInfos, int branchInfoIndex) {
        return Bytes.beS4(branchInfos, branchInfoIndex + DEFAULT_HITS_OFFSET);
    }

    public static int getProfileHits(byte[] branchInfos, int branchInfoIndex, int index) {
        int profileIndex = getProfileIndex(branchInfoIndex, index);
        return Bytes.beS4(branchInfos, profileIndex);
    }

    public static int getTargetBCI(byte[] branchInfos, int curBCI, int branchInfoIndex) {
        return curBCI + Bytes.beS4(branchInfos, branchInfoIndex + DEFAULT_OFFSET);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public static byte[] copySwitchInfos(byte[] switchInfos, int switchIndex, int nrOfCases) {
        byte[] copy = new byte[CASE_HITS_OFFSET + nrOfCases * BYTES_PER_ENTRY];
        // Do not use System.arraycopy here as it messes up compilation in some cases.
        for (int i = 0; i < copy.length; i++) {
            copy[i] = switchInfos[switchIndex + i];
        }
        CompilerDirectives.ensureVirtualized(copy);
        return copy;
    }

    // endregion *SWITCH profiling
}
