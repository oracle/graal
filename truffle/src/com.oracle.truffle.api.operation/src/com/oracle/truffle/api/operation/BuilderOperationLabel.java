package com.oracle.truffle.api.operation;

import java.util.ArrayList;

import com.oracle.truffle.api.memory.ByteArraySupport;

// not public api
public class BuilderOperationLabel extends OperationLabel {
    private boolean marked = false;
    private ArrayList<Integer> toBackfill;
    private boolean hasValue = false;
    private int value = 0;

    public void resolve(byte[] bc, int labelValue) {
        assert !hasValue;
        hasValue = true;
        value = labelValue;

        if (toBackfill != null) {
            for (int bci : toBackfill) {
                putDestination(bc, bci, value);
            }
            toBackfill = null;
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