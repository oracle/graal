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
package com.oracle.truffle.espresso.redefinition;

import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.ADD_METHOD_NOT_IMPLEMENTED;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.CIRCULAR_CLASS_DEFINITION;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.CLASS_ATTRIBUTE_CHANGE_NOT_IMPLEMENTED;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.DELETE_METHOD_NOT_IMPLEMENTED;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.FAILS_VERIFICATION;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.HIERARCHY_CHANGE_NOT_IMPLEMENTED;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.INVALID_CLASS;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.INVALID_CLASS_FORMAT;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.NAMES_DONT_MATCH;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.SCHEMA_CHANGE_NOT_IMPLEMENTED;
import static com.oracle.truffle.espresso.jdwp.api.ErrorCodes.UNSUPPORTED_VERSION;

import java.io.Serial;

import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;

public final class RedefinitionException extends Exception {
    @Serial private static final long serialVersionUID = -5767957395371919542L;
    private final RedefinitionError kind;
    private final String message;

    RedefinitionException(RedefinitionError kind, String message) {
        this.kind = kind;
        this.message = message;
    }

    RedefinitionException(RedefinitionError kind) {
        this(kind, null);
    }

    public int getJDWPErrorCode() {
        return kind.jdwpErrorCode;
    }

    public EspressoException throwInstrumentationGuestException(Meta meta) {
        ObjectKlass type = kind.getInstrumentationGuestExceptionType(meta);
        String exceptionMessage = message;
        if (exceptionMessage == null) {
            exceptionMessage = kind.getDefaultMessage();
        }
        if (exceptionMessage != null) {
            throw meta.throwExceptionWithMessage(type, message);
        } else {
            throw meta.throwException(type);
        }
    }

    public enum RedefinitionError {
        InvalidClassFormat(INVALID_CLASS_FORMAT),
        NamesDontMatch(NAMES_DONT_MATCH),
        NoSuperDefFound(INVALID_CLASS),
        UnsupportedVersion(UNSUPPORTED_VERSION),
        CircularClassDefinition(CIRCULAR_CLASS_DEFINITION),
        FailsVerification(FAILS_VERIFICATION),
        SchemaChanged(SCHEMA_CHANGE_NOT_IMPLEMENTED, "class redefinition failed: attempted to change the schema (add/remove fields)"),
        MethodDeleted(DELETE_METHOD_NOT_IMPLEMENTED, "class redefinition failed: attempted to delete a method"),
        MethodAdded(ADD_METHOD_NOT_IMPLEMENTED, "class redefinition failed: attempted to add a method"),
        ClassModifiersChanged(CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED, "class redefinition failed: attempted to change the class modifiers"),
        HierarchyChanged(HIERARCHY_CHANGE_NOT_IMPLEMENTED, "class redefinition failed: attempted to change superclass or interfaces"),
        ClassAttributeChanged(CLASS_ATTRIBUTE_CHANGE_NOT_IMPLEMENTED, "class redefinition failed: attempted to change the class NestHost, NestMembers, Record, or PermittedSubclasses attribute");

        private final int jdwpErrorCode;
        private final String defaultMessage;

        RedefinitionError(int jdwpErrorCode, String defaultMessage) {
            this.jdwpErrorCode = jdwpErrorCode;
            this.defaultMessage = defaultMessage;
        }

        RedefinitionError(int jdwpErrorCode) {
            this(jdwpErrorCode, null);
        }

        ObjectKlass getInstrumentationGuestExceptionType(Meta meta) {
            return switch (this) {
                case InvalidClassFormat -> meta.java_lang_ClassFormatError;
                case NoSuperDefFound, NamesDontMatch -> meta.java_lang_NoClassDefFoundError;
                case UnsupportedVersion -> meta.java_lang_UnsupportedClassVersionError;
                case CircularClassDefinition -> meta.java_lang_ClassCircularityError;
                case FailsVerification -> meta.java_lang_VerifyError;
                case SchemaChanged, MethodDeleted, MethodAdded, ClassModifiersChanged, HierarchyChanged, ClassAttributeChanged -> meta.java_lang_UnsupportedOperationException;
            };
        }

        String getDefaultMessage() {
            return defaultMessage;
        }
    }
}
