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
package com.sun.max.asm.gen;

import java.lang.reflect.*;
import java.util.*;

/**
 */
public interface ImmediateParameter extends Parameter {

    public static final class Static {

        private Static() {
        }

        public static <Element_Type extends ImmediateArgument, Argument_Type> List<Element_Type> createSequence(Class<Element_Type> elementType,
                        final Class<Argument_Type> argumentType, Argument_Type... values) throws NoSuchMethodException, IllegalAccessException, InstantiationException, InvocationTargetException {
            final List<Element_Type> result = new ArrayList<Element_Type>();
            final Constructor<Element_Type> elementConstructor = elementType.getConstructor(argumentType);
            for (Argument_Type value : values) {
                result.add(elementConstructor.newInstance(value));
            }
            return result;
        }
    }

}
