/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
