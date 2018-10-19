package com.oracle.truffle.espresso.bytecode;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.MethodInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface FrameOperandStack {

    List<FrameSlotKind> KIND_VALUES = Collections.unmodifiableList(Arrays.asList(FrameSlotKind.values()));

    int stackIndex(final VirtualFrame frame);

    void popVoid(final VirtualFrame frame, int slots);

    void pushObject(final VirtualFrame frame, Object value);

    void pushInt(final VirtualFrame frame, int value);

    void pushLong(final VirtualFrame frame, long value);

    void pushFloat(final VirtualFrame frame, float value);

    void pushDouble(final VirtualFrame frame, double value);

    Object popObject(final VirtualFrame frame);

    int popInt(final VirtualFrame frame);

    float popFloat(final VirtualFrame frame);

    long popLong(final VirtualFrame frame);

    double popDouble(final VirtualFrame frame);

    void dup1(final VirtualFrame frame);

    void swapSingle(final VirtualFrame frame);

    void dupx1(final VirtualFrame frame);

    void dupx2(final VirtualFrame frame);

    void dup2(final VirtualFrame frame);

    void dup2x1(final VirtualFrame frame);

    void dup2x2(final VirtualFrame frame);

    void clear(final VirtualFrame frame);

    Object peekReceiver(final VirtualFrame frame, MethodInfo method);
}
