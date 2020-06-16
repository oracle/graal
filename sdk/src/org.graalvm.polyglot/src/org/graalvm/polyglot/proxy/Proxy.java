/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.proxy;

import org.graalvm.polyglot.PolyglotException;

/**
 * Proxy interfaces allow to mimic guest language objects, arrays, executables, primitives and
 * native objects in Graal languages. Every Graal language will treat instances of proxies like an
 * object of that particular language. Multiple proxy interfaces can be implemented at the same
 * time. For example, it is useful to provide proxy values that are objects with members and arrays
 * at the same time.
 * <p>
 * Exceptions thrown by proxies are wrapped with a {@link PolyglotException} when the proxy is
 * invoked in a guest language. It is possible to unwrap the {@link PolyglotException} using
 * {@link PolyglotException#asHostException()}.
 * <p>
 * The interfaces {@link Proxy}, {@link ProxyArray}, {@link ProxyExecutable},
 * {@link ProxyInstantiable}, {@link ProxyNativeObject}, {@link ProxyObject} can be used in
 * combination with any other proxy interfaces.
 * <p>
 * The following proxy interface combinations are exclusive and throw an {@link AssertionError} if
 * used together:
 * <ul>
 * <li>{@link ProxyDuration}
 * <li>{@link ProxyInstant}, {@link ProxyDate}, {@link ProxyTime} or {@link ProxyTimeZone}.
 * </ul>
 *
 * The following proxy interface combinations are invalid and throw an {@link AssertionError} if
 * used:
 * <ul>
 * <li>If {@link ProxyTimeZone} and {@link ProxyDate} without {@link ProxyTime}
 * <li>If {@link ProxyTimeZone} and {@link ProxyTime} without {@link ProxyDate}.
 * </ul>
 *
 * @see ProxyArray to mimic arrays
 * @see ProxyObject to mimic objects with members
 * @see ProxyExecutable to mimic objects that can be executed
 * @see ProxyNativeObject to mimic native objects
 * @see ProxyDate to mimic date objects
 * @see ProxyTime to mimic time objects
 * @see ProxyTimeZone to mimic timezone objects
 * @see ProxyDuration to mimic duration objects
 * @see ProxyInstant to mimic timestamp objects
 *
 * @since 19.0
 */
public interface Proxy {

}
