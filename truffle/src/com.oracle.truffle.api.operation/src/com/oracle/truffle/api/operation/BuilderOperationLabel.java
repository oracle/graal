package com.oracle.truffle.api.operation;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.memory.ByteArraySupport;

// not public api
public abstract class BuilderOperationLabel extends OperationLabel {
    private boolean marked = false;
    private ArrayList<Integer> targetBci = new ArrayList<>();
    private ArrayList<Integer> targets = new ArrayList<>();
    private ArrayList<BuilderOperationData> targetDataList = new ArrayList<>();

    private boolean hasValue = false;

    private int value = 0;
    private BuilderOperationData valueData;

    public void resolve(byte[] bc, int labelValue, BuilderOperationData data) {
        assert !hasValue;

        hasValue = true;
        value = labelValue;
        valueData = data;

        List<Integer> leaveList = new ArrayList<>();
        List<BuilderOperationData> leaveDataList = new ArrayList<>();

        for (int i = 0; i < targets.size(); i++) {
            int target = targets.get(i);
            BuilderOperationData targetData = targetDataList.get(i);

            createBranch(labelValue, data, target, targetData, leaveList, leaveDataList);
        }

        for (int j = 0; j < leaveList.size(); j++) {
            createLeaveCode(leaveList.get(j), leaveDataList.get(j));
        }
    }

    protected abstract void createLeaveCode(int bci, BuilderOperationData data);

    private final void createBranch(int from, BuilderOperationData fromData, int to, BuilderOperationData toData, List<Integer> leaveCodeLocation, List<BuilderOperationData> leaveData) {
        if (fromData.depth < toData.depth) {
            throw new IllegalStateException("illegal jump to deeper operation");
        }

        BuilderOperationData current = fromData;
        for (int i = fromData.depth; i > toData.depth; i--) {
            leaveCodeLocation.add(from);
            leaveData.add(current);

            current = current.parent;
        }

        if (current != toData) {
            throw new IllegalStateException("illegal jump to non-parent operation");
        }
    }

    /**
     * 01234| -----> |56789 <------> length ^ from offset
     */
    public void relocate(byte[] bc, int from, int length) {
        for (int i = 0; i < targets.size(); i++) {
            if (targetBci.get(i) >= from) {
                targetBci.set(i, targetBci.get(i) + length);
            }
            if (targets.get(i) >= from) {
                targets.set(i, targets.get(i) + length);
            }
        }

        if (hasValue) {
            if (value >= from) {
                value += length;
            }

            for (int i = 0; i < targets.size(); i++) {
                putDestination(bc, targets.get(i), value);
            }

        }
    }

    public void setMarked() {
        assert !marked;
        marked = true;
    }

    public void putValue(byte[] bc, int bci) {
        if (hasValue) {
            putDestination(bc, bci, value);
        } else {
            if (toBackfill == null)
                toBackfill = new ArrayList<>();
            toBackfill.add(bci);
        }
    }

    private static void putDestination(byte[] bc, int bci, int value) {
        ByteArraySupport.littleEndian().putShort(bc, bci, (short) value);
    }
}