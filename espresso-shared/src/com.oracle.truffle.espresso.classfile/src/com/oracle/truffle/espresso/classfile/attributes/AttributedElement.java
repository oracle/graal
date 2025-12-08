/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

/**
 * A class file element may contain named attributes.
 */
public interface AttributedElement {
    Attribute[] getAttributes();

    /**
     * Gets the first attribute named {@code attrName} of this element or {@code null} if no such
     * attribute exists.
     */
    default Attribute getAttribute(Symbol<Name> attrName) {
        for (Attribute attribute : getAttributes()) {
            if (attribute.getName() == attrName) {
                return attribute;
            }
        }
        return null;
    }

    /**
     * Gets the first attribute names {@code attrName} of this element, and ensures it is of the
     * expected class before returning it, or {@code null} if no such attribute exists.
     * <p>
     * This method differs from the {@link #getAttribute(Symbol)} method above, in that it safely
     * casts the attribute to the expected class:
     *
     * @implNote Older classfiles may have user-defined attributes with the same name as a known JVM
     *           attribute defined in later versions. In such cases, the {@link ClassfileParser
     *           parser} will keep that attribute as a raw {@link Attribute}, instead of actually
     *           parsing it as a known attribute.
     */
    default <T extends Attribute> T getAttribute(Symbol<Name> attributeName, Class<T> attributeClass) {
        for (Attribute attribute : getAttributes()) {
            if (attributeName == attribute.getName() && attributeClass.isInstance(attribute)) {
                return attributeClass.cast(attribute);
            }
        }
        return null;
    }
}
