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
import java.util.*;

import com.sun.max.program.*;

public interface StaticFieldName {

    String name();

    void setName(String name);

    public interface StringFunction {
        String function(String string);
    }

    public interface Procedure {
        void procedure(StaticFieldName staticFieldName);
    }

    public static final class Static {

        private Static() {
        }

        public static List<StaticFieldName> initialize(Class staticNameFieldClass, StringFunction stringFunction, Procedure procedure) {
            final List<StaticFieldName> sequence = new LinkedList<StaticFieldName>();
            for (Field field : staticNameFieldClass.getDeclaredFields()) {
                if ((field.getModifiers() & Modifier.STATIC) != 0 && StaticFieldName.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        final StaticFieldName value = (StaticFieldName) field.get(null);
                        if (value.name() == null) {
                            String name = field.getName();
                            if (stringFunction != null) {
                                name = stringFunction.function(name);
                            }
                            value.setName(name);
                        }
                        if (procedure != null) {
                            procedure.procedure(value);
                        }
                        sequence.add(value);
                    } catch (IllegalAccessException illegalAccessException) {
                        throw ProgramError.unexpected("could not name value of field: " + field);
                    }
                }
            }
            return sequence;
        }

        public static List<StaticFieldName> initialize(Class staticNameFieldClass, StringFunction stringFunction) {
            return initialize(staticNameFieldClass, stringFunction, null);
        }

        public static List<StaticFieldName> initialize(Class staticNameFieldClass, Procedure procedure) {
            return initialize(staticNameFieldClass, null, procedure);
        }

        public static List<StaticFieldName> initialize(Class staticNameFieldClass) {
            return initialize(staticNameFieldClass, null, null);
        }
    }

}
