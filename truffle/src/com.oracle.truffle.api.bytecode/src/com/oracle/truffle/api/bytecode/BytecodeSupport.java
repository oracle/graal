package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Contains code to support Truffle operation interpreters. This code should not be referenced
 * directly by language implementations.
 *
 * @since XXX
 */
public class BytecodeSupport {
    private static final int MAX_PROFILE_COUNT = 0x3fffffff;

    private BytecodeSupport() {
        // no instances
    }

    public static int[] allocateBranchProfiles(int numProfiles) {
        // Representation: [t1, f1, t2, f2, ..., tn, fn]
        return new int[numProfiles * 2];
    }

    public static boolean profileBranch(int[] branchProfiles, int profileIndex, boolean condition) {
        int t = branchProfiles[profileIndex * 2];
        int f = branchProfiles[profileIndex * 2 + 1];
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
                if (t < MAX_PROFILE_COUNT) {
                    branchProfiles[profileIndex * 2] = t + 1;
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
                if (t < MAX_PROFILE_COUNT) {
                    branchProfiles[profileIndex * 2 + 1] = f + 1;
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
