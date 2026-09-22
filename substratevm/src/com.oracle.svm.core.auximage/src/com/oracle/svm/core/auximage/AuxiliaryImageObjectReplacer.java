/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import org.graalvm.nativeimage.hosted.Feature.DuringSetupAccess;

/**
 * Replaces (or simply observes) objects when they are discovered while constructing an auxiliary
 * image object heap. Similar in concept to {@link DuringSetupAccess#registerObjectReplacer}.
 */
public interface AuxiliaryImageObjectReplacer {
    /** Allows the replacer to interact with the construction of the auxiliary image. */
    interface Access {
    }

    interface EpilogueAccess extends Access {
        /**
         * Replaces the given object with a replacement object. The replacement object itself may
         * be new, but its references must resolve, after applying replacements, to previously
         * discovered objects. No replacers will be called for the replacement object.
         *
         * @param original The object to replace. This object must not have been replaced with
         *            another object before, and it must not itself have replaced another object.
         * @param replacement The object that should replace {@code original}. This object must not
         *            itself have been replaced before.
         */
        void replaceLate(Object original, Object replacement);
    }

    /** Method that is called exactly once, before the start of the traversal. */
    default void prologue(@SuppressWarnings("unused") Access access) {
    }

    /**
     * Called when the passed object is encountered for the first time. When returning a different
     * object, that object will replace the passed original object in the auxiliary image heap, so
     * that all references to the original object will point to the returned object. However,
     * another registered replacer can potentially again replace the returned object. This method is
     * called exactly once for each object.
     */
    Object replace(Object obj, Access access);

    /** Method that is called exactly once, at the end of the traversal. */
    default void epilogue(@SuppressWarnings("unused") EpilogueAccess access) {
    }
}
