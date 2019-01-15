/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.types.SignatureDescriptor;

/**
 * JVM operand stack. Underlying storage is implementation specific e.g. boxed array, primitive +
 * reference storage, on-frame...
 */
public interface OperandStack {

    List<JavaKind> KIND_VALUES = Collections.unmodifiableList(Arrays.asList(JavaKind.values()));

    void popVoid(int slots);

    void pushObject(StaticObject value);

    void pushReturnAddress(int bci);

    void pushInt(int value);

    void pushLong(long value);

    void pushFloat(float value);

    void pushDouble(double value);

    StaticObject popObject();

    Object popReturnAddressOrObject();

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

    StaticObject peekReceiver(MethodInfo method);

    @ExplodeLoop
    default Object[] popArguments(boolean hasReceiver, SignatureDescriptor signature) {
        int argCount = signature.getParameterCount(false);

        int extraParam = hasReceiver ? 1 : 0;
        Object[] arguments = new Object[argCount + extraParam];

        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(signature);
        CompilerAsserts.partialEvaluationConstant(hasReceiver);

        for (int i = argCount - 1; i >= 0; --i) {
            JavaKind expectedKind = signature.getParameterKind(i);
            // @formatter:off
            // Checkstyle: stop
            switch (expectedKind) {
                case Boolean : arguments[i + extraParam] = (popInt() != 0);  break;
                case Byte    : arguments[i + extraParam] = (byte) popInt();  break;
                case Short   : arguments[i + extraParam] = (short) popInt(); break;
                case Char    : arguments[i + extraParam] = (char) popInt();  break;
                case Int     : arguments[i + extraParam] = popInt();         break;
                case Float   : arguments[i + extraParam] = popFloat();       break;
                case Long    : arguments[i + extraParam] = popLong();        break;
                case Double  : arguments[i + extraParam] = popDouble();      break;
                case Object  : arguments[i + extraParam] = popObject();      break;
                case Void    : // fall through
                case Illegal :
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
            // Checkstyle: resume
        }
        if (hasReceiver) {
            arguments[0] = popObject();
        }
        return arguments;
    }

    /**
     * Push a value to the stack. This method follows the JVM spec, where sub-word types (< int) are
     * always treated as int.
     *
     * @param value value to push
     * @param kind kind to push
     */
    default void pushKind(Object value, JavaKind kind) {
        // @formatter:off
        // Checkstyle: stop
        switch (kind) {
            case Boolean : pushInt(((boolean) value) ? 1 : 0); break;
            case Byte    : pushInt((byte) value);              break;
            case Short   : pushInt((short) value);             break;
            case Char    : pushInt((char) value);              break;
            case Int     : pushInt((int) value);               break;
            case Float   : pushFloat((float) value);           break;
            case Long    : pushLong((long) value);             break;
            case Double  : pushDouble((double) value);         break;
            case Object  : pushObject((StaticObject) value);   break;
            case Void    : /* ignore */                        break;
            default :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }
}
