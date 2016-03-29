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
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Annotation to put on your node to simplify handling of incoming inter-operability {@link Message
 * messages}.
 *
 * This node needs to be a final class. It extends a base class which will be automatically
 * generated. The generated class defines an abstract <code>accept</code> method that needs to be
 * overwritten. The first argument of <code>accept</code> needs to be a {@link VirtualFrame}. The
 * second argument of <code>accept</code> needs to be a the receiver, i.e., a {@link TruffleObject}.
 * Afterwards, the arguments of the message follow. For example:
 *
 * {@codesnippet AcceptMessageExample}
 *
 * The receiver object needs to implement a static method <code>isInstance</code>, which checks if a
 * given foreign object is an instance of the given receiver type and can therefore be accessed by
 * this node. For example:
 *
 * {@codesnippet isInstanceCheck}
 *
 * Besides the abstract base class, a {@link ForeignAccess} will be generated. The receiver object
 * can then return a singleton instance of this access. For example: <br>
 *
 * {@codesnippet getForeignAccessMethod}
 *
 * @since 0.11
 */
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
     */
    String value();

    /**
     * The receiver object class that this message implementation belongs to.
     *
     * An annotation processor generates a {@link ForeignAccess} class, which the
     * {@link TruffleObject} can use to implement {@link TruffleObject#getForeignAccess()}.
     *
     * @return class of the receiver object
     */
    Class<?> receiverType();

    /**
     * The language the message implementation belongs to.
     *
     * @return class of the language object
     */
    Class<? extends TruffleLanguage<?>> language();
}
