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

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Contains utilities for using {@link Library libraries}.
 *
 * @see Library for further details.
 * @since 1.0
 */
public final class Libraries {

    private Libraries() {
        // no instances
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static <T extends Library> T getUncachedDispatch(Class<T> libraryClass) {
        return ResolvedLibrary.lookup(libraryClass).getUncachedDispatch();
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static <T extends Library> T createCachedDispatch(Class<T> libraryClass, int limit) {
        return ResolvedLibrary.lookup(libraryClass).createCachedDispatch(limit);
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static <T extends Library> T getUncached(Class<T> libraryClass, Object receiver) {
        return ResolvedLibrary.lookup(libraryClass).getUncached(receiver);
    }

    /**
     * Creates a new cached library given a libraryClass and a receiver. The returned library
     * implementation only works with the provided receiver or for other receivers that are
     * {@link Library#accepts(Object) accepted} by the returned library. This method is rarely used
     * directly. Use the {@link CachedLibrary} annotation in specializations instead.
     * <p>
     * Calling this method is short-hand for:
     * <code>{@link ResolvedLibrary#lookup(Class) resolve(libraryClass)}.{@link ResolvedLibrary#createCached(Object) createCached(receiver)} </code>.
     *
     * @see ResolvedLibrary#createCached(Object)
     * @see CachedLibrary
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public static <T extends Library> T createCached(Class<T> libraryClass, Object receiver) {
        return ResolvedLibrary.lookup(libraryClass).createCached(receiver);
    }

    @TruffleBoundary
    public static List<Message> getMessages(Class<? extends Library> libraryClass) {
        return ResolvedLibrary.lookup(libraryClass).getMessages();
    }

}
