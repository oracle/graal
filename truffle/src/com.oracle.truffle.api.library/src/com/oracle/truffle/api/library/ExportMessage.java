/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.dsl.Specialization;

/**
 * Exports the annotated method or class as library message. The annotation can only be applied to
 * method or classes with an enclosing class annotated by {@link ExportLibrary}. Exported messages
 * are inherited to subclasses of the enclosing class. If they are redeclared in the sub-class then
 * the semantics of the overridden message is replaced by the semantics of the sub-class. A class
 * and a method cannot be exported at the same time for a single message and enclosing class.
 *
 * @see ExportLibrary For usage examples.
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(ExportMessage.Repeat.class)
public @interface ExportMessage {

    /**
     * Returns the message simple name to export. If not specified, the exported message name is
     * inherited from the enclosing method name or class. In case of an exported class the first
     * letter is automatically translated to lower-case. The name attribute should be specified if
     * the exported message does not match the exported method or class name or if multiple messages
     * need to be exported for a method or class.
     *
     * @since 19.0
     */
    String name() default "";

    /**
     * Returns the library to export by this method or class. Automatically selected if the name of
     * the message is unique. Needs to be specified if the name is not unique when implementing
     * multiple libraries.
     *
     * @since 19.0
     */
    Class<? extends Library> library() default Library.class;

    /***
     * Specifies the limit of an exported message. The limit specifies the number of specialized
     * instances of {@link CachedLibrary cached library} should be used until the library rewrites
     * itself to an uncached case.
     *
     * @see Specialization#limit()
     * @see CachedLibrary
     * @since 19.0
     */
    String limit() default "";

    /***
     * @since 19.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface Repeat {
        /***
         * @since 19.0
         */
        ExportMessage[] value();

    }

    /***
     * Explicitly ignores warning messages originating from the {@link ExportLibrary} annotation.
     *
     * @since 19.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface Ignore {
    }
}
