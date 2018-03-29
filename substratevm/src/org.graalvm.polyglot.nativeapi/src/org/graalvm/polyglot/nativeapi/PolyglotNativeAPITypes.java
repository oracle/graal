/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.nativeapi;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

@CContext(PolyglotNativeAPICContext.class)
class PolyglotNativeAPITypes {

    @CEnum("polyglot_status")
    enum PolyglotStatus {
        polyglot_ok,

        polyglot_invalid_arg,

        polyglot_object_expected,

        polyglot_string_expected,

        polyglot_name_expected,

        polyglot_function_expected,

        polyglot_number_expected,

        polyglot_boolean_expected,

        polyglot_array_expected,

        polyglot_generic_failure,

        polyglot_pending_exception,

        polyglot_cancelled,

        polyglot_status_last;

        @CEnumValue
        public native int getCValue();
    }

    @CStruct("polyglot_extended_error_info")
    interface PolyglotExtendedErrorInfo extends PointerBase {

        @CField("error_code")
        void setErrorCode(int errorCode);

        @CField("error_message")
        void setErrorMessage(CCharPointer errorMessage);

    }

    @CPointerTo(value = PolyglotExtendedErrorInfo.class)
    interface ExtendedErrorInfoPointer extends PointerBase {

        void write(PolyglotExtendedErrorInfo value);

    }

    @CPointerTo(nameOfCType = "size_t")
    interface SizeTPointer extends PointerBase {

        UnsignedWord read();

        void write(UnsignedWord value);

    }

    @CPointerTo(nameOfCType = "polyglot_engine")
    interface PolyglotEnginePointer extends PointerBase, ObjectHandle {
    }

    @CPointerTo(nameOfCType = "polyglot_engine*")
    interface PolyglotEnginePointerPointer extends PointerBase, ObjectHandle {

        void write(ObjectHandle value);

    }

    @CPointerTo(nameOfCType = "polyglot_handle")
    interface PolyglotHandlePointer extends PointerBase, ObjectHandle {

    }

    @CPointerTo(nameOfCType = "polyglot_context")
    interface PolyglotContextPointer extends PointerBase, ObjectHandle {
    }

    @CPointerTo(nameOfCType = "polyglot_context*")
    interface PolyglotContextPointerPointer extends PointerBase, ObjectHandle {

        void write(ObjectHandle value);

    }

    @CPointerTo(nameOfCType = "polyglot_value")
    interface PolyglotValuePointer extends PointerBase, ObjectHandle {
    }

    @CPointerTo(nameOfCType = "polyglot_value*")
    interface PolyglotValuePointerPointer extends PointerBase, ObjectHandle {

        PolyglotValuePointer read(int index);

        void write(ObjectHandle value);

        void write(int index, ObjectHandle value);
    }

    @CPointerTo(nameOfCType = "polyglot_callback_info")
    interface PolyglotCallbackInfo extends ObjectHandle, PointerBase {
    }

    interface PolyglotCallbackPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        PolyglotValuePointer invoke(IsolateThread ithread, PolyglotCallbackInfo info);
    }

}
