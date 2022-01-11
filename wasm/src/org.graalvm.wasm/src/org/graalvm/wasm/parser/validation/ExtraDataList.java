package org.graalvm.wasm.parser.validation;

public class ExtraDataList {

    public static final int IF_TARGET_OFFSET = 0;
    public static final int IF_EXTRA_TARGET_OFFSET = 1;
    public static final int IF_CONDITION_PROFILE = 2;
    public static final int IF_LENGTH = 3;

    public static final int ELSE_TARGET_OFFSET = 0;
    public static final int ELSE_EXTRA_TARGET_OFFSET = 1;
    public static final int ELSE_LENGTH = 2;

    /**
     * Conditional branches (if false jumps, br_if):
     * 
     * <code>
     * | target | extraTarget | stackInfo | conditionProfile |
     * </code>
     * 
     * Target is the branch target in the data array. ExtraTarget is the branch target in the extra
     * data array. StackSize is the size of the value stack after the branch. ReturnLength is the
     * number of return types. //TODO: ConditionProfile represents the condition profile value.
     *
     * Stack info | 8 bit returnLength | 24 bit stack size |
     */
    public static final int CONDITIONAL_BRANCH_TARGET_OFFSET = 0;
    public static final int CONDITIONAL_BRANCH_EXTRA_TARGET_OFFSET = 1;
    public static final int CONDITIONAL_BRANCH_STACK_INFO = 2;
    public static final int CONDITIONAL_BRANCH_CONDITION_PROFILE = 3;
    public static final int CONDITIONAL_BRANCH_LENGTH = 4;

    public static final int STACK_INFO_RETURN_LENGTH_SHIFT = 24;
    public static final int STACK_INFO_STACK_SIZE_MASK = 0x00FF_FFFF;

    /**
     * Unconditional branches (br):
     * 
     * <code>
     * | target | extraTarget | stackInfo |
     * </code>
     * 
     * Target is the branch target in the data array. ExtraTarget is the branch target in the extra
     * data array. StackSize is the size of the value stack after the branch. ReturnLength is the
     * number of return types.
     */
    public static final int UNCONDITIONAL_BRANCH_TARGET_OFFSET = 0;
    public static final int UNCONDITIONAL_BRANCH_EXTRA_TARGET_OFFSET = 1;
    public static final int UNCONDITIONAL_BRANCH_STACK_INFO = 2;
    public static final int UNCONDITIONAL_BRANCH_LENGTH = 3;

    /**
     * Branch tables (br_table):
     * 
     * <code>
     * | size | entry | ... | entry |
     * </code>
     * 
     * ReturnLength is the number of return types. Size is the number of entries. Entry is an
     * unconditional branch entry.
     */
    public static final int BRANCH_TABLE_SIZE = 0;
    public static final int BRANCH_TABLE_ENTRY_OFFSET = 1;
    public static final int BRANCH_TABLE_ENTRY_LENGTH = UNCONDITIONAL_BRANCH_LENGTH;

    public static final int INDIRECT_CALL_CHILD_OFFSET = 0;
    public static final int INDIRECT_CALL_CONDITION_OFFSET = 1;
    public static final int INDIRECT_CALL_LENGTH = 2;

    public static final int DIRECT_CALL_CHILD_OFFSET = 0;
    public static final int DIRECT_CALL_LENGTH = 1;

    private static final int[] EMPTY_EXTRA_DATA = new int[0];

    private int[] extraData;

    private int extraDataCount;

    public ExtraDataList() {
        this.extraData = new int[4];
        this.extraDataCount = 0;
    }

    public int addIfLocation() {
        ensureExtraDataSize(IF_LENGTH);
        // initializes condition profile to 0.
        int location = extraDataCount;
        extraDataCount += IF_LENGTH;
        return location;
    }

    public void setIfTarget(int location, int target, int extraTarget) {
        extraData[location + IF_TARGET_OFFSET] = target;
        extraData[location + IF_EXTRA_TARGET_OFFSET] = extraTarget;
    }

    public int addElseLocation() {
        ensureExtraDataSize(ELSE_LENGTH);
        int location = extraDataCount;
        extraDataCount += ELSE_LENGTH;
        return location;
    }

    public void setElseTarget(int location, int target, int extraTarget) {
        extraData[location + ELSE_TARGET_OFFSET] = target;
        extraData[location + ELSE_EXTRA_TARGET_OFFSET] = extraTarget;
    }

    public int addConditionalBranchLocation() {
        ensureExtraDataSize(CONDITIONAL_BRANCH_LENGTH);
        // initializes condition profile with 0.
        int location = extraDataCount;
        extraDataCount += CONDITIONAL_BRANCH_LENGTH;
        return location;
    }

    public void setConditionalBranchTarget(int location, int target, int extraTarget, int stackSize, int returnLength) {
        extraData[location + CONDITIONAL_BRANCH_TARGET_OFFSET] = target;
        extraData[location + CONDITIONAL_BRANCH_EXTRA_TARGET_OFFSET] = extraTarget;
        extraData[location + CONDITIONAL_BRANCH_STACK_INFO] = (stackSize & STACK_INFO_STACK_SIZE_MASK) + (returnLength << STACK_INFO_RETURN_LENGTH_SHIFT);
    }

    public int addUnconditionalBranchLocation() {
        ensureExtraDataSize(UNCONDITIONAL_BRANCH_LENGTH);
        int location = extraDataCount;
        extraDataCount += UNCONDITIONAL_BRANCH_LENGTH;
        return location;
    }

    public void setUnconditionalBranchTarget(int location, int target, int extraTarget, int stackSize, int returnLength) {
        extraData[location + UNCONDITIONAL_BRANCH_TARGET_OFFSET] = target;
        extraData[location + UNCONDITIONAL_BRANCH_EXTRA_TARGET_OFFSET] = extraTarget;
        extraData[location + UNCONDITIONAL_BRANCH_STACK_INFO] = (stackSize & STACK_INFO_STACK_SIZE_MASK) + (returnLength << STACK_INFO_RETURN_LENGTH_SHIFT);
    }

    public int addBranchTableLocation(int size) {
        ensureExtraDataSize(BRANCH_TABLE_ENTRY_OFFSET + BRANCH_TABLE_ENTRY_LENGTH * size);
        extraData[extraDataCount + BRANCH_TABLE_SIZE] = size;
        int location = extraDataCount;
        extraDataCount += BRANCH_TABLE_ENTRY_OFFSET + BRANCH_TABLE_ENTRY_LENGTH * size;
        return location;
    }

    public int addBranchTableEntry(int location, int index) {
        return location + BRANCH_TABLE_ENTRY_OFFSET + index * BRANCH_TABLE_ENTRY_LENGTH;
    }

    public void addIndirectCall(int childOffset) {
        ensureExtraDataSize(INDIRECT_CALL_LENGTH);
        extraData[extraDataCount + INDIRECT_CALL_CHILD_OFFSET] = childOffset;
        // initializes condition profile with 0.
        extraDataCount += INDIRECT_CALL_LENGTH;
    }

    public void addCall(int childOffset) {
        ensureExtraDataSize(DIRECT_CALL_LENGTH);
        extraData[extraDataCount + DIRECT_CALL_CHILD_OFFSET] = childOffset;
        extraDataCount += DIRECT_CALL_LENGTH;
    }

    public int getLocation() {
        return extraDataCount;
    }

    public int[] getExtraDataArray() {
        int[] result = new int[extraDataCount];
        if (extraData == null) {
            return EMPTY_EXTRA_DATA;
        } else {
            System.arraycopy(extraData, 0, result, 0, extraDataCount);
            return result;
        }
    }

    private void ensureExtraDataSize(int requiredSize) {
        int nextSizeFactor = 0;
        while (extraDataCount + requiredSize >= extraData.length << nextSizeFactor) {
            nextSizeFactor++;
        }
        if (nextSizeFactor != 0) {
            int[] updatedExtraData = new int[extraData.length << nextSizeFactor];
            System.arraycopy(extraData, 0, updatedExtraData, 0, extraDataCount);
            extraData = updatedExtraData;
        }
    }
}
