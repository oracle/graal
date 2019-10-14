/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.struct;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.IntUnaryOperator;

import org.graalvm.word.PointerBase;

/**
 * Denotes Java interface that represents C memory, but without a {@link CStruct C struct}
 * definition. The interface must extend {@link PointerBase}, i.e., it is a word type. There is
 * never a Java class that implements the interface.
 * <p>
 * Field accesses are done via interface methods that are annotated with {@link RawField}. All calls
 * of the interface methods are replaced with the appropriate memory operations.
 * <p>
 * The layout and size of the structure is inferred from the fields defined with {@link RawField}.
 * All fields are aligned according to the field size, i.e., 8-byte types are aligned at 8-byte
 * boundaries. It is currently not possible to influence the layout of fields. However, it is
 * possible to reserve extra space at the end of the structure by specifying a
 * {@link RawStructure#sizeProvider}.
 *
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RawStructure {

    /**
     * Class of a function that computes the size of the structure. The input argument of the
     * function is the size computed based on the layout of the {@link RawField fields} of the
     * structure. The returned value must not be smaller than that provided argument.
     * <p>
     * By default, the size computed based on the layout is used.
     * <p>
     * The provided class must have a no-argument constructor.
     *
     * @since 19.2
     */
    Class<? extends IntUnaryOperator> sizeProvider() default IntUnaryOperator.class;
}
