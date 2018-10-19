package com.oracle.truffle.espresso.bytecode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.espresso.impl.MethodInfo;

public interface OperandStack {

    List<FrameSlotKind> KIND_VALUES = Collections.unmodifiableList(Arrays.asList(FrameSlotKind.values()));

    int stackIndex();

    void popVoid(int slots);

    void pushObject(Object value);

    void pushInt(int value);

    void pushLong(long value);

    void pushFloat(float value);

    void pushDouble(double value);

    Object popObject();

    int popInt();

    float popFloat();

    long popLong();

    double popDouble();

    void dup1();

    void swapSingle();

    void dupx1();

    void dupx2();

    void dup2();

    void dup2x1();

    void dup2x2();

    void clear();

    Object peekReceiver(MethodInfo method);
}
