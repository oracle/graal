/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.dsl.Specialization;

/**
 * The cached library annotation allows to use Truffle Libraries in parameters for
 * {@link Specialization} or {@link ExportMessage} annotated methods of exported libraries or nodes.
 *
 * The cached library annotation can be used for internal and external dispatch. Internal dispatch
 * performs the lookup of the target export once per specialization instance and verifies its
 * {@link Library#accepts(Object) acceptance} automatically. This allows to avoid the repeated
 * lookups. This is also the recommended way of using Truffle Libraries by default:
 *
 * <p>
 * <h3>Internal Dispatch</h3>
 *
 *
 * <ul>
 * <li>Internally dispatched:
 * <li>Externally dispatched:
 * </ul>
 *
 * <b>Basic Usage Example:</b>
 *
 * <pre>
 * &#64;NodeChild
 * &#64;NodeChild
 * abstract static class ArrayReadNode extends ExpressionNode {
 *     &#64;Specialization(guards = "arrays.isArray(array)", limit = "2")
 *     int doDefault(Object array, int index,
 *                     &#64;CachedLibrary("array") ArrayLibrary arrays) {
 *         return arrays.read(array, index);
 *     }
 * }
 * </pre>
 *
 *
 *
 * Note that the ArrayLibrary class is reused from the {@link GenerateLibrary} javadoc.
 *
 *
 * Note that the following use is equivalent to
 *
 *
 *
 *
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER})
public @interface CachedLibrary {

    String value() default "";

    String limit() default "";

}
