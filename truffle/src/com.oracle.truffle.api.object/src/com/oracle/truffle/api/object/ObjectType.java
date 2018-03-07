/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

/**
 * An extensible object type descriptor for {@link DynamicObject}s.
 *
 * @since 0.8 or earlier
 */
public class ObjectType {
    /**
     * Default constructor.
     *
     * @since 0.8 or earlier
     */
    public ObjectType() {
    }

    /**
     * Delegate method for {@link DynamicObject#equals(Object)}.
     *
     * @since 0.8 or earlier
     */
    public boolean equals(DynamicObject object, Object other) {
        return object == other;
    }

    /**
     * Delegate method for {@link DynamicObject#hashCode()}.
     *
     * @since 0.8 or earlier
     */
    public int hashCode(DynamicObject object) {
        return System.identityHashCode(object);
    }

    /**
     * Delegate method for {@link DynamicObject#toString()}.
     *
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public String toString(DynamicObject object) {
        return "DynamicObject<" + this.toString() + ">@" + Integer.toHexString(hashCode(object));
    }

    /**
     * Create a {@link ForeignAccess} to access a specific {@link DynamicObject}.
     *
     * @param object the object to be accessed
     * @since 0.8 or earlier
     */
    public ForeignAccess getForeignAccessFactory(DynamicObject object) {
        return createDefaultForeignAccess();
    }

    static ForeignAccess createDefaultForeignAccess() {
        return ForeignAccess.create(new com.oracle.truffle.api.interop.ForeignAccess.Factory() {
            @TruffleBoundary
            public boolean canHandle(TruffleObject obj) {
                throw new IllegalArgumentException(obj.toString() + " cannot be shared");
            }

            @Override
            public CallTarget accessMessage(Message tree) {
                throw UnsupportedMessageException.raise(tree);
            }
        });
    }
}
