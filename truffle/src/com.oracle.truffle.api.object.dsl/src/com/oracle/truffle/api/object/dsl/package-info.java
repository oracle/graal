/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

/*
 @ApiInfo(
 group="Truffle"
 )
 */
/**
 * Domain specific language for using the Truffle dynamic object storage model to create classic
 * class hierarchies.
 * <p>
 * The dynamic object storage model ({@link com.oracle.truffle.api.object}) is well suited to
 * supporting dynamic object models such as those found in Ruby and JavaScript. This domain specific
 * language makes it easier to also use the object storage model when you know at the time you are
 * writing your language what properties you will need, you want to be able to specify that
 * declaratively, and you want to be able to access the properties of the objects efficiently
 * without creating many nodes to do that.
 * <p>
 * A typical use-case of the object storage model DSL is to implement language built-in classes
 * which need internal implementation properties, as well as dynamic properties set by the language
 * user. Using the object storage model DSL allows the object storage model to be conveniently used
 * for both of these with just the one {@link com.oracle.truffle.api.object.DynamicObject} instance,
 * while providing an interface that is similar to if you were defining normal Java fields in a
 * separate object.
 * <p>
 * The object storage model DSL is used by creating interfaces annotated with
 * {@link com.oracle.truffle.api.object.dsl.Layout}.
 *
 * @see com.oracle.truffle.api.object.dsl.Layout
 * @since 0.8 or older
 */
package com.oracle.truffle.api.object.dsl;
