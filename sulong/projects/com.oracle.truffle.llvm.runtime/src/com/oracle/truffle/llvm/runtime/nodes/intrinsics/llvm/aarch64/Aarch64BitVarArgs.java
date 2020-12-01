package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64;

class Aarch64BitVarArgs {
    // see https://static.docs.arm.com/100986/0000/abi_sve_aapcs64_100986_0000_00_en.pdf

    public static final int OVERFLOW_ARG_AREA = 0;
    public static final int GP_SAVE_AREA = 16;
    public static final int FP_SAVE_AREA = 32;
    public static final int GP_OFFSET = 40;
    public static final int FP_OFFSET = 44;

// public static final int OVERFLOW_ARG_AREA = 0;
// public static final int GP_SAVE_AREA = 8;
// public static final int FP_SAVE_AREA = 16;
// public static final int GP_OFFSET = 24;
// public static final int FP_OFFSET = 28;

    public static final int GP_LIMIT = 64;
    public static final int GP_STEP = 8;
    public static final int FP_LIMIT = 128;
    public static final int FP_STEP = 16;

    public static final int STACK_STEP = 8;

}
