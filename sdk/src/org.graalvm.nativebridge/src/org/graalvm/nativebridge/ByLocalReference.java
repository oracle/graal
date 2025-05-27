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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the native bridge processor should marshal the annotated method parameter or
 * method return type as a reference to a local object.
 *
 * <p>
 * Behavior details:
 * </p>
 * <ul>
 * <li><strong>For method parameters:</strong> A raw representation of the object is created, such
 * as by using {@link ReferenceHandles#create(Object)}. This representation is sent to the target
 * isolate, where the DSL creates a proxy object for it.</li>
 * <li><strong>For method return values:</strong> The proxy object in the target isolate is unboxed
 * to its raw value. This value is then sent back to the caller, where the DSL resolves it to a
 * local object, for instance, using {@link ReferenceHandles#resolve(long, Class)}.</li>
 * </ul>
 *
 * <p>
 * <strong>Note:</strong> When the {@link #value()} attribute is set to {@code Peer.class}, the
 * processor marshals the object directly as a {@link Peer} rather than as a {@link ForeignObject}.
 * In this case, the type of the corresponding method parameter or return value must be
 * {@link Object}.
 * </p>
 *
 * @see ByRemoteReference
 * @see AlwaysByLocalReference
 * @see AlwaysByRemoteReference
 * @since 25.0
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface ByLocalReference {

    /**
     * The class to instantiate for a foreign handle.
     * <p>
     * <ul>
     * <li>If the bridged type is class or interface, the class must implement
     * {@link ForeignObject}.</li>
     * <li>If the bridged type has a custom dispatch, the class must be the dispatch class with a
     * {@link CustomDispatchFactory factory}.</li>
     * <li>If this attribute is set to {@code Peer.class}, the processor will marshal the object
     * directly as a {@link Peer} instance, bypassing {@link ForeignObject}
     * wrapping/unwrapping.</li>
     * </ul>
     * </p>
     */
    Class<?> value();

    /**
     * For classes with a custom dispatch, when set to {@code true} the foreign object is translated
     * by a custom receiver accessor before it's passed to the target method.
     *
     * @see CustomReceiverAccessor
     * @see CustomDispatchAccessor
     */
    boolean useCustomReceiverAccessor() default false;
}
