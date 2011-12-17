/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.program.option;

import com.sun.max.*;
import com.sun.max.program.ProgramError;

import java.lang.reflect.Field;

/**
 * This class implements a command line option that stores its value in a field
 * via reflection.
 */
public class FieldOption<T> extends Option<T> {

    protected final Object object;
    protected final Field field;
    protected T nullValue;

    public FieldOption(String name, Object object, Field field, T defaultValue, Type<T> type, String help) {
        super(name, defaultValue, type, help);
        this.object = object;
        this.field = field;
        this.nullValue = defaultValue;
    }

    /**
     * Gets the value of this option. This implementation stores the field's value in a reflected field
     * and access requires a reflective access.
     * @return the value of this option
     */
    @Override
    public T getValue() {
        try {
            return Utils.<T>cast(field.get(object));
        } catch (IllegalAccessException e) {
            throw ProgramError.unexpected(e);
        }
    }

    /**
     * Sets the value of this option. This implementation stores the field's value in a reflected field
     * and thus setting the value requires a reflective access.
     * @param value the value to set the new value to
     */
    @Override
    public void setValue(T value) {
        try {
            if (value == null) {
                field.set(object, nullValue);
            } else {
                field.set(object, value);
            }
        } catch (Exception e) {
            throw ProgramError.unexpected("Error updating the value of " + field, e);
        }
    }
}
