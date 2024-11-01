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
package org.graalvm.nativebridge;

import org.graalvm.nativeimage.c.function.CEntryPoint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BooleanSupplier;

/**
 * Generates a factory class for a HotSpot to native bridge. For an annotated class, the processor
 * generates a {@code ClassGen} class with a {@code create(NativeIsolateConfig)} method. This method
 * spawns an isolate, creates an initial service within it and returns a foreign object referencing
 * the created initial service.
 * <p>
 * For more details, see the
 * <a href="http://github.com/oracle/graal/blob/master/compiler/docs/NativeBridgeProcessor.md">
 * native bridge tutorial</a>.
 * </p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface GenerateHotSpotToNativeFactory {

    /**
     * The native bridge {@link BinaryMarshaller}s configuration. The returned class must have an
     * accessible static {@code getInstance()} method returning a {@link MarshallerConfig} instance.
     * The returned {@link MarshallerConfig} instance is used for marshallers' lookup.
     */
    Class<?> marshallers();

    /**
     * Generates a factory method that spawns a {@link NativeIsolate} and creates an initial service
     * within it. The initial service class must either have an accessible no-argument constructor
     * or a static {@code getInstance()} method with no arguments that returns the initial service
     * instance.
     */
    Class<?> initialService();

    /**
     * If the supplier returns {@code true}, the bridge entry points are added automatically when
     * building a shared library. This means the bridge entry points are root methods for
     * compilation, and everything reachable from them is compiled too. The provided class must have
     * a nullary constructor, which is used to instantiate the class. The
     * {@link BooleanSupplier#getAsBoolean()}} function is called on the newly instantiated
     * instance.
     */
    Class<? extends BooleanSupplier> include() default CEntryPoint.AlwaysIncluded.class;

    /**
     * Customizes the creation and disposal of a native isolate for an initial service. The class
     * must either have an accessible no-argument constructor or a static {@code getInstance()}
     * method with no arguments that returns the {@code NativeIsolateHandler} instance.
     *
     * @see NativeIsolateHandler
     */
    Class<? extends NativeIsolateHandler> isolateHandler() default DefaultNativeIsolateHandler.class;
}
