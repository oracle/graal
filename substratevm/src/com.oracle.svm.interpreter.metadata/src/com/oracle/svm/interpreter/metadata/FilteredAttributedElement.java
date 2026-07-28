/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import java.util.ArrayList;
import java.util.Set;

import com.oracle.svm.espresso.classfile.attributes.Attribute;
import com.oracle.svm.espresso.classfile.attributes.AttributedElement;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.shared.util.VMError;

/**
 * An attributed classfile element that keeps only the attributes explicitly used at run time.
 * <p>
 * Implementations define their supported attribute names via {@link #getRetainedAttributes()}. Any
 * lookup for a different attribute is treated as a bug, because that attribute was not retained by
 * {@link #filterAttributes(Attribute[])}.
 */
public interface FilteredAttributedElement extends AttributedElement {
    /**
     * Returns the attribute names that may be retained and queried for this element.
     */
    Set<Symbol<Name>> getRetainedAttributes();

    /**
     * Returns the subset of {@code attributes} whose names are listed in
     * {@link #getRetainedAttributes()}.
     */
    default Attribute[] filterAttributes(Attribute[] attributes) {
        ArrayList<Attribute> filteredAttributes = new ArrayList<>();
        for (Attribute attribute : attributes) {
            if (getRetainedAttributes().contains(attribute.getName())) {
                filteredAttributes.add(attribute);
            }
        }
        if (filteredAttributes.isEmpty()) {
            return Attribute.EMPTY_ARRAY;
        }
        return filteredAttributes.toArray(Attribute.EMPTY_ARRAY);
    }

    @Override
    default Attribute getAttribute(Symbol<Name> attrName) {
        if (!getRetainedAttributes().contains(attrName)) {
            throw VMError.shouldNotReachHere("Unexpected classfile attribute access: " + attrName);
        }
        return AttributedElement.super.getAttribute(attrName);
    }

    @Override
    default <T extends Attribute> T getAttribute(Symbol<Name> attributeName, Class<T> attributeClass) {
        if (!getRetainedAttributes().contains(attributeName)) {
            throw VMError.shouldNotReachHere("Unexpected classfile attribute access: " + attributeName);
        }
        return AttributedElement.super.getAttribute(attributeName, attributeClass);
    }
}
