/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * Can be applied to {@link Specialization}- or {@link Fallback}-annotated methods to specify
 * whether they require a bytecode index update in the frame. This allows the DSL to update the
 * bytecode index only for a given specialization instead of the entire operation. In practice, this
 * means that the bytecode index is updated just before the specialization is executed.
 * <p>
 * This annotation only has an effect if {@link GenerateBytecode#storeBytecodeIndexInFrame()} is set
 * to <code>true</code> or if the {@link GenerateBytecode#enableUncachedInterpreter() uncached
 * interpreter tier} is enabled and the parent operation has {@link Operation#storeBytecodeIndex()}
 * set to <code>false</code>.
 * <p>
 * Usage example:
 *
 * <pre>
 * // Disable bytecode index updates at the operation level
 * &#64;Operation(storeBytecodeIndex = false)
 * static final class Abs {
 *
 *     &#64;Specialization(guards = "v &gt;= 0")
 *     public static long doGreaterZero(long v) {
 *         return v;
 *     }
 *
 *     &#64;Specialization(guards = "v &lt; 0")
 *     public static long doLessThanZero(long v) {
 *         return -v;
 *     }
 *
 *     // Update the bytecode index only for this specialization
 *     &#64;Specialization
 *     &#64;StoreBytecodeIndex
 *     public static long doCallAbs(DynamicObject v) {
 *         // ... guest language call ...
 *     }
 * }
 * </pre>
 *
 * @see Operation#storeBytecodeIndex()
 * @since 26.0
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD})
public @interface StoreBytecodeIndex {

}
