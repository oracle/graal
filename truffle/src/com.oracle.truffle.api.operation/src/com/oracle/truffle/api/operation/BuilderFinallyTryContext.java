package com.oracle.truffle.api.operation;

import java.util.ArrayList;

class BuilderFinallyTryContext {
    final BuilderFinallyTryContext prev;
    final byte[] bc;
    final int bci;
    final ArrayList<BuilderExceptionHandler> exceptionHandlers;
    final ArrayList<BuilderLabelFill> labelFills;
    final ArrayList<BuilderOperationLabel> labels;
    final int curStack;
    final int maxStack;

    BuilderFinallyTryContext(BuilderFinallyTryContext prev, byte[] bc, int bci, ArrayList<BuilderExceptionHandler> exceptionHandlers, ArrayList<BuilderLabelFill> labelFills,
                    ArrayList<BuilderOperationLabel> labels, int curStack, int maxStack) {
        this.prev = prev;
        this.bc = bc;
        this.bci = bci;
        this.exceptionHandlers = exceptionHandlers;
        this.labelFills = labelFills;
        this.labels = labels;
        this.curStack = curStack;
        this.maxStack = maxStack;
    }

    byte[] handlerBc;
    ArrayList<BuilderExceptionHandler> handlerHandlers;
    public ArrayList<BuilderLabelFill> handlerLabelFills = new ArrayList<>();
    public ArrayList<Integer> relocationOffsets = new ArrayList<>();
    public int handlerMaxStack;

    boolean finalized() {
        return handlerBc != null;
    }
}