/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.staticobject;

/**
 * A trivial default implementation of {@link StaticProperty}.
 *
 * @see StaticProperty
 */
public final class DefaultStaticProperty extends StaticProperty {
    private final String id;

    /**
     * Constructs a new DefaultStaticProperty.
     *
     * @see StaticProperty#StaticProperty(StaticPropertyKind, boolean)
     * @param id the id of the static property, which must be immutable and unique for a given
     *            shape.
     * @param kind the {@link StaticPropertyKind} of the static property
     * @param storeAsFinal if the static property value can be stored in a final field
     */
    public DefaultStaticProperty(String id, StaticPropertyKind kind, boolean storeAsFinal) {
        super(kind, storeAsFinal);
        this.id = id;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public String getId() {
        return id;
    }
}
