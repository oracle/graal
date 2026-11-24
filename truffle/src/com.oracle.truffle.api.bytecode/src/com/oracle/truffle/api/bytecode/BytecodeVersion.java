/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

/**
 * Bytecode DSL behaviour versions selectable through {@link GenerateBytecode#version()}.
 * Versions distinguish breaking behaviour changes that require migration of language
 * implementations. They do not identify Truffle releases or version internal implementation
 * details, optimizations, or performance changes.
 *
 * @since 25.7
 */
public enum BytecodeVersion {

    /**
     * Original Bytecode DSL behaviour on initial release. Support will be removed in release 25.8.
     *
     * @since 25.7
     */
    V1,

    /**
     * Distinguishes illegal locals from valid null values in the {@link BytecodeNode} local APIs.
     * <ul>
     * <li>{@link BytecodeNode#getLocalValue(int, Frame, int)} returns {@link BytecodeNode#INVALID}
     * for an illegal slot instead of {@code null}. Java {@code null} continues to represent a
     * null value.</li>
     * <li>{@link BytecodeNode#setLocalValue(int, Frame, int, Object)} marks a slot illegal when
     * passed {@code INVALID} instead of storing {@code null}. Passing {@code null} continues to
     * store a null value.</li>
     * <li>The bulk getters and setters use the same representation. Both
     * {@code BytecodeNode.copyLocalValues} overloads preserve illegal slots and null values.</li>
     * </ul>
     * To migrate, replace comparisons against {@code null} that detect illegal locals with
     * comparisons against {@code BytecodeNode.INVALID}. Keep comparisons against {@code null}
     * that detect null values. Pass {@code INVALID} when you intend to mark a slot illegal.
     * Ordinary bytecode local loads and stores and {@link LocalAccessor} retain their behaviour.
     *
     * @see BytecodeNode#INVALID
     * @see BytecodeNode#getLocalValues(int, Frame)
     * @see BytecodeNode#setLocalValues(int, Frame, Object[])
     * @see BytecodeNode#copyLocalValues(int, Frame, Frame)
     * @since 25.7
     */
    V2,

    /**
     * The newest supported Bytecode DSL behaviour version. Using this constant explicitly opts
     * into breaking behaviour changes when recompiling against a newer Truffle release.
     * This constant should not be used in production language implementations; select an explicit
     * version instead.
     *
     * @see GenerateBytecode#version()
     * @since 25.7
     */
    LATEST;
}
