/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm;

public final class WasmArguments {

    public static final int RUNTIME_ARGUMENT_COUNT = 1;

    private static final int MODULE_INSTANCE_ARGUMENT_INDEX = 0;

    private WasmArguments() {
    }

    public static Object[] createEmpty(int formalArgumentCount) {
        return new Object[RUNTIME_ARGUMENT_COUNT + formalArgumentCount];
    }

    public static Object[] create(Object instance, Object... formalArguments) {
        Object[] arguments = new Object[RUNTIME_ARGUMENT_COUNT + formalArguments.length];
        arguments[MODULE_INSTANCE_ARGUMENT_INDEX] = instance;
        setArguments(arguments, 0, formalArguments);
        return arguments;
    }

    public static int getArgumentCount(Object[] arguments) {
        return arguments.length - RUNTIME_ARGUMENT_COUNT;
    }

    public static Object getArgument(Object[] arguments, int index) {
        return arguments[index + RUNTIME_ARGUMENT_COUNT];
    }

    public static void setArgument(Object[] arguments, int index, Object value) {
        arguments[index + RUNTIME_ARGUMENT_COUNT] = value;
    }

    public static Object[] getArguments(Object[] arguments) {
        Object[] userArguments = new Object[arguments.length - RUNTIME_ARGUMENT_COUNT];
        System.arraycopy(arguments, RUNTIME_ARGUMENT_COUNT, userArguments, 0, userArguments.length);
        return userArguments;
    }

    public static void setArguments(Object[] arguments, int index, Object[] formalArguments) {
        System.arraycopy(formalArguments, 0, arguments, RUNTIME_ARGUMENT_COUNT + index, formalArguments.length);
    }

    public static WasmInstance getModuleInstance(Object[] arguments) {
        return (WasmInstance) arguments[MODULE_INSTANCE_ARGUMENT_INDEX];
    }

    public static void setModuleInstance(Object[] arguments, WasmInstance instance) {
        arguments[MODULE_INSTANCE_ARGUMENT_INDEX] = instance;
    }

    public static boolean isValid(Object[] arguments) {
        return arguments.length >= RUNTIME_ARGUMENT_COUNT && arguments[0] instanceof WasmInstance;
    }
}
