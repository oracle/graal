/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.types;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.Klass;
import com.oracle.truffle.espresso.runtime.KlassRegistry;

/**
 * A string description of a Java runtime type, e.g. a field's type, see #4.3.2.
 */
public final class TypeDescriptor extends Descriptor {

    TypeDescriptor(String string) {
        super(string);
    }

    public static String stringToJava(String string) {
        switch (string.charAt(0)) {
            // @formatter: off
            case 'L': return dottified(string.substring(1, string.length() - 1));
            case '[': return stringToJava(string.substring(1)) + "[]";
            case 'B': return "byte";
            case 'C': return "char";
            case 'D': return "double";
            case 'F': return "float";
            case 'I': return "int";
            case 'J': return "long";
            case 'S': return "short";
            case 'V': return "void";
            case 'Z': return "boolean";
            default: throw new InternalError("invalid type descriptor: " + "\"" + string + "\"");
            // @formatter: on
        }
    }

    public String toJavaName() {
        return stringToJava(toString());
    }

    /**
     * Gets the kind denoted by this type descriptor.
     *
     * @return the kind denoted by this type descriptor
     */
    public JavaKind toKind() {
        if (value.length() == 1) {
            return JavaKind.fromPrimitiveOrVoidTypeChar(value.charAt(0));
        }

        return JavaKind.Object;
    }

    /**
     * Gets the number of array dimensions in this type descriptor.
     */
    public int getArrayDimensions() {
        int dimension = 0;
        while (value.charAt(dimension) == '[') {
            dimension++;
        }
        return dimension;
    }

    /**
     * Resolves this type descriptor to a klass using a given class loader.
     *
     * @param classLoader the class loader used to resolve this type descriptor to a class
     * @return the resolved class or null
     */
    public Klass resolveType(EspressoContext context, DynamicObject classLoader) {
        // FIXME (ld): Recursing up the class registry's ancestors is wrong.
        // This assumes a delegation model where a class loader delegates to class loaders up
        // its class hierarchy only.
        // This will not work with more elaborated loader where delegation may be customized to
        // arbitrary loader, e.g., loader that aren't
        // in its ancestor branch.
        Klass klass = KlassRegistry.get(context, classLoader, this);
        if (klass != null) {
            return klass;
        }
        return null;
    }

    @Override
    public void verify() {
        int endIndex = TypeDescriptors.skipValidTypeDescriptor(value, 0, true);
        if (endIndex != value.length()) {
            throw new ClassFormatError("Invalid type descriptor " + value);
        }
    }
}
