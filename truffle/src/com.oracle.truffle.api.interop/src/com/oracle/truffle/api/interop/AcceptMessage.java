/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.TruffleLanguage;

/**
 * @deprecated use {@link MessageResolution} and {@link Resolve} instead.
 *
 * @since 0.11
 */
@Deprecated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface AcceptMessage {
    /**
     * Identification of the {@link Message message} to accept. Well known messages include fields
     * of the {@link Message} class (e.g. <em>"READ"</em>, <em>"WRITE"</em>, <em>"UNBOX"</em>,
     * <em>IS_NULL</em>) or slightly mangled names of {@link Message} class factory methods (
     * <em>EXECUTE</em>, <em>INVOKE</em>). For more details on the string encoding of message names
     * see {@link Message#valueOf(java.lang.String)} method.
     *
     * @return string identification of an inter-operability message
     * @see Message#valueOf(java.lang.String)
     * @since 0.11
     */
    String value();

    /**
     * The receiver object class that this message implementation belongs to.
     *
     * An annotation processor generates a {@link ForeignAccess} class, which the
     * {@link TruffleObject} can use to implement {@link TruffleObject#getForeignAccess()}.
     *
     * @return class of the receiver object
     * @since 0.11
     */
    Class<?> receiverType();

    /**
     * The language the message implementation belongs to.
     *
     * @return class of the language object
     * @since 0.11
     */
    Class<? extends TruffleLanguage<?>> language();
}
