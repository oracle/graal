/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Collection;
import java.util.function.Function;

final class Utilities {

    private Utilities() {
    }

    static void encodeType(StringBuilder into, TypeMirror type, String arrayStart, Function<TypeElement, String> declaredTypeNameFactory) {
        String desc;
        switch (type.getKind()) {
            case BOOLEAN:
                desc = "Z";
                break;
            case BYTE:
                desc = "B";
                break;
            case CHAR:
                desc = "C";
                break;
            case SHORT:
                desc = "S";
                break;
            case INT:
                desc = "I";
                break;
            case LONG:
                desc = "J";
                break;
            case FLOAT:
                desc = "F";
                break;
            case DOUBLE:
                desc = "D";
                break;
            case VOID:
                desc = "V";
                break;
            case ARRAY:
                into.append(arrayStart);
                encodeType(into, ((ArrayType) type).getComponentType(), arrayStart, declaredTypeNameFactory);
                return;
            case DECLARED:
                desc = declaredTypeNameFactory.apply((TypeElement) ((DeclaredType) type).asElement());
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(type.getKind()));
        }
        into.append(desc);
    }

    static void printType(StringBuilder into, TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY:
                into.append(((ArrayType) type).getComponentType()).append("[]");
                break;
            case BOOLEAN:
                into.append("boolean");
                break;
            case BYTE:
                into.append("byte");
                break;
            case CHAR:
                into.append("char");
                break;
            case DECLARED:
                into.append(((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName());
                break;
            case DOUBLE:
                into.append("double");
                break;
            case FLOAT:
                into.append("float");
                break;
            case INT:
                into.append("int");
                break;
            case LONG:
                into.append("long");
                break;
            case SHORT:
                into.append("short");
                break;
            case VOID:
                into.append("void");
                break;
        }
    }

    static String cSymbol(String name) {
        return name.replace("_", "_1").replace("$", "_00024").replace('.', '_');
    }

    static boolean contains(Collection<? extends TypeMirror> collection, TypeMirror mirror, Types types) {
        for (TypeMirror element : collection) {
            if (types.isSameType(element, mirror)) {
                return true;
            }
        }
        return false;
    }
}
