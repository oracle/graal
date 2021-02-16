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
package com.oracle.truffle.api.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds and executes an expression at most once for every execution of the node. This is useful if
 * an expression cannot be repeated safely for a guard and its associated specialization method. For
 * example, if a read of a field is volatile two consecutive reads might not lead to the same
 * result. The name the extract parameter can be referred to in {@link Cached cached},
 * {@link Specialization#guards() guard}, {@link Specialization#limit() limit},
 * {@link Specialization#assumptions() assumption} expressions or the specialization body. The bind
 * expression may refer to dynamic parameters, previously declared bind or cached parameters and
 * {@link NodeField node field} declarations.
 * <p>
 * If a bind parameter only uses cached values, then it is considered also a cached value. If the
 * parameter uses any dynamic parameter then the extract parameter is considered also a dynamic
 * parameter.
 * <p>
 * Usage examples:
 *
 * <pre>
 * static class DynamicObject {
 *
 *     volatile Object storage;
 *
 *     DynamicObject(Object storage) {
 *         this.storage = storage;
 *     }
 * }
 *
 * abstract static class ExtractStorageNode extends Node {
 *
 *     abstract Object execute(Object arg0);
 *
 *     &#64;Specialization(guards = "storage == cachedStorage", limit = "3")
 *     Object s0(DynamicObject a0,
 *                     &#64;Bind("a0.storage") Object storage,
 *                     &#64;Cached("storage") Object cachedStorage) {
 *         // use storage in specialization and in guard.
 *         return a0;
 *     }
 * }
 * </pre>
 *
 *
 * @see Cached
 * @see Specialization
 * @since 20.2
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER})
public @interface Bind {

    /**
     * The extract expression.
     *
     * @see Bind
     * @since 20.2
     */
    String value();

}
