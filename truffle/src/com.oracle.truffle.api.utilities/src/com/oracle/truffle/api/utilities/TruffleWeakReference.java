/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Creates a new weak reference that is safe to be used in compiled code paths. If a weak reference
 * is {@link CompilerDirectives#isPartialEvaluationConstant(Object) PE constant} then the
 * {@link #get() referenced object} will be
 * {@link CompilerDirectives#isPartialEvaluationConstant(Object) PE constant} as well.
 * <p>
 * When a language is compiled using native-image then a closed world closure of all runtime
 * compiled methods is computed. That list of methods can rely on static type information only. If
 * the host application or any of the loaded libraries uses custom weak reference sub-classes that
 * were not designed for partial evaluation then these method will be listed as runtime compilable
 * methods, causing black listed method errors at native-image compilation time. To avoid this
 * problem use this custom weak reference subclass which is designed for PE if used as exact class
 * of the field.
 * <p>
 * <b>Wrong usage</b> of weak references in compiled code paths:
 *
 * <pre>
 * class MyNode extends Node {
 *
 *     &#64;CompilationFinal private WeakReference<Object> reference;
 *
 *     Object execute(Object arg) {
 *         if (reference == null) {
 *             CompilerDirectives.transferToInterpreterAndInvalidate();
 *             reference = new WeakReference<>(arg);
 *         }
 *         return reference.get();
 *     }
 *
 * }
 * </pre>
 *
 * <b>Correct usage</b> of weak references in compiled code paths :
 *
 * <pre>
 * static class MyNode extends Node {
 *
 *     &#64;CompilationFinal private TruffleWeakReference<Object> reference;
 *
 *     Object execute(Object arg) {
 *         if (reference == null) {
 *             CompilerDirectives.transferToInterpreterAndInvalidate();
 *             reference = new TruffleWeakReference(arg);
 *         }
 *         return reference.get();
 *     }
 *
 * }
 * </pre>
 *
 * @since 20.2
 */
public final class TruffleWeakReference<T> extends WeakReference<T> {

    /**
     * Creates a new Truffle weak reference that refers to the given object. The new reference is
     * not registered with any queue.
     *
     * @see WeakReference#WeakReference(Object)
     * @since 20.2
     */
    public TruffleWeakReference(T t) {
        super(t);
    }

    /**
     * Creates a new Truffle weak reference that refers to the given object and is registered with
     * the given queue.
     *
     * @see WeakReference#WeakReference(Object, ReferenceQueue)
     * @since 20.2
     */
    public TruffleWeakReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
    }

}
