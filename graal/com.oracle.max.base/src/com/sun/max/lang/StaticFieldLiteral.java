/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.lang;

import java.lang.reflect.*;

import com.sun.max.program.*;

public interface StaticFieldLiteral {

    String literal();

    void setLiteral(String literal);

    Class literalClass();

    void setLiteralClass(Class literalClass);

    public static final class Static {

        private Static() {
        }

        public static void initialize(Class staticFieldLiteralClass) {
            for (Field field : staticFieldLiteralClass.getDeclaredFields()) {
                if ((field.getModifiers() & Modifier.STATIC) != 0 && StaticFieldLiteral.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        final StaticFieldLiteral staticFieldLiteral = (StaticFieldLiteral) field.get(staticFieldLiteralClass);
                        staticFieldLiteral.setLiteral(field.getName());
                        staticFieldLiteral.setLiteralClass(staticFieldLiteralClass);
                    } catch (IllegalAccessException illegalAccessException) {
                        throw ProgramError.unexpected("could not name literal of field: " + field);
                    }
                }
            }
        }
    }

}
