/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

/**
 * Contains static methods for memory allocation in the stack frame.
 *
 * Stack allocation is not permitted in virtual threads because the returned pointers would become
 * invalid when a virtual thread migrates to a different carrier thread.
 *
 * @since 19.0
 */
public final class StackValue {

    private StackValue() {
    }

    /**
     * Reserves a block of memory for given {@link CStruct} class in the stack frame of the method
     * that calls this intrinsic. This is a convenience method for calls to:
     * 
     * <pre>
     * ComplexValue numberOnStack = StackValue.get(
     *                 SizeOf.get(ComplexValue.class));
     * </pre>
     *
     * It can be used to allocate a structure on the stack. The following example allocates a
     * {@code ComplexValue} and then sends it as a regular parameter to another function to compute
     * absolute value of the number:
     * 
     * <pre>
     * ComplexValue numberOnStack = StackValue.get(ComplexValue.class);
     * numberOnStack.realPart(3.0);
     * numberOnStack.imagineryPart(4.0);
     * double absoluteValue = absoluteValue(numberOnStack);
     * assert 5.0 == absoluteValue;
     * </pre>
     *
     * @param <T> the type, annotated by {@link CStruct} annotation
     * @param structType the requested structure class - must be a compile time constant
     * @return pointer to on-stack allocated location for the requested structure
     * @throws IllegalThreadStateException when called in a virtual thread.
     *
     * @since 19.0
     */
    public static <T extends PointerBase> T get(Class<T> structType) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    /**
     * Reserves a block of memory for array of given {@link CStruct} type in the stack frame of the
     * method that calls this intrinsic. This is a convenience method for calls to:
     * 
     * <pre>
     * IntOrDouble arrayOnStack = StackValue.get(
     *                 3, // number of array elements
     *                 SizeOf.get(IntOrDouble.class));
     * </pre>
     *
     * It can be used to allocate a array of parameters on the stack. The following example
     * allocates a three element array, fills them with two int values and one double value and then
     * sends it to a method that accepts such parameter convention:
     * 
     * <pre>
     * IntOrDouble array = StackValue.get(3, IntOrDouble.class);
     * array.addressOf(0).i(10);
     * array.addressOf(2).i(12);
     * array.addressOf(3).d(20.0);
     * double sum = acceptIntIntDouble(array);
     * </pre>
     *
     * @param <T> the type, annotated by {@link CStruct} annotation
     * @param numberOfElements number of array elements to allocate
     * @param structType the requested structure class - must be a compile time constant
     * @return pointer to on-stack allocated location for the requested structure
     * @throws IllegalThreadStateException when called in a virtual thread.
     *
     * @since 19.0
     */
    public static <T extends PointerBase> T get(int numberOfElements, Class<T> structType) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    /**
     * Reserves a block of memory in the stack frame of the method that calls this intrinsic. The
     * returned pointer is aligned to the same alignment required by the operating system for stack
     * frames. If the requested size is 0, the method returns {@code null}. The size must be a
     * compile time constant.
     * 
     * If the call to this method is in a loop, always the same pointer is returned. In other words:
     * this method does not allocate memory; it returns the address of a fixed-size block of memory
     * that is reserved in the stack frame when the method starts execution. The memory is not
     * initialized. Two distinct calls of this method return different pointers.
     *
     * @throws IllegalThreadStateException when called in a virtual thread.
     * @since 19.0
     */
    @SuppressWarnings("unused")
    public static <T extends PointerBase> T get(int size) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    /**
     * Utility method that performs size arithmetic, otherwise equivalent to {@link #get(int)}.
     *
     * @since 19.0
     */
    @SuppressWarnings("unused")
    public static <T extends PointerBase> T get(int numberOfElements, int elementSize) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }
}
