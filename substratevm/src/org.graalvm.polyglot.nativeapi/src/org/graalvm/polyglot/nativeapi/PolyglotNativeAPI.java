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

import static org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotStatus.poly_generic_failure;
import static org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotStatus.poly_number_expected;
import static org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotStatus.poly_ok;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.ExtendedErrorInfoPointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotCallbackInfo;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotCallbackPointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotContextPointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotContextPointerPointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotEnginePointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotEnginePointerPointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotExtendedErrorInfo;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotHandlePointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotStatus;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotValuePointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.PolyglotValuePointerPointer;
import org.graalvm.polyglot.nativeapi.PolyglotNativeAPITypes.SizeTPointer;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CConst;
import com.oracle.svm.core.c.CHeader;
import com.oracle.svm.core.c.CTypedef;
import com.oracle.svm.core.c.CUnsigned;

@SuppressWarnings("unused")
@CHeader(value = PolyglotAPIHeader.class)
public final class PolyglotNativeAPI {

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    private static ThreadLocal<ErrorInfoHolder> errorInfo = new ThreadLocal<>();
    private static ThreadLocal<CallbackException> exceptionsTL = new ThreadLocal<>();

    private static class ErrorInfoHolder {
        PolyglotExtendedErrorInfo info;
        CCharPointerHolder messageHolder;

        ErrorInfoHolder(PolyglotExtendedErrorInfo info, CCharPointerHolder messageHolder) {
            this.info = info;
            this.messageHolder = messageHolder;
        }
    }

    @CEntryPoint(name = "poly_create_engine", documentation = {
                    "Creates a polyglot engine.",
                    "Engine is a unit that holds configuration, instruments, and compiled code for many contexts",
                    "inside the engine."
    })
    public static PolyglotStatus poly_create_engine(PolyglotIsolateThread thread, PolyglotEnginePointerPointer engine) {
        return withHandledErrors(() -> {
            ObjectHandle handle = createHandle(Engine.create());
            engine.write(handle);
        });
    }

    @CEntryPoint(name = "poly_create_context", documentation = {
                    "Creates a context within an polyglot engine.",
                    "Context holds all of the program data. Each context is by default isolated from all other contexts",
                    "with respect to program data and evaluation semantics.",
                    "Note: context allows access to all resources in embedded programs; ",
                    "in the future this will be restricted and replaced with finer grained APIs."
    })
    public static PolyglotStatus poly_create_context(PolyglotIsolateThread thread, PolyglotEnginePointer engine_handle, PolyglotContextPointerPointer context) {
        return withHandledErrors(() -> {
            Engine engine = ObjectHandles.getGlobal().get(engine_handle);
            Context c = Context.newBuilder().allowAllAccess(true).engine(engine).build();
            context.write(createHandle(c));
        });
    }

    @CEntryPoint(name = "poly_eval")
    public static PolyglotStatus poly_eval(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, @CConst CCharPointer language, CCharPointer name, CCharPointer code,
                    PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context c = ObjectHandles.getGlobal().get(poly_context);
            String languageName = CTypeConversion.toJavaString(language);
            String jName = CTypeConversion.toJavaString(name);
            String jCode = CTypeConversion.toJavaString(code);

            Source sourceCode = Source.newBuilder(languageName, jCode, jName).build();
            result.write(createHandle(c.eval(sourceCode)));
        });
    }

    @CEntryPoint(name = "poly_execute")
    public static PolyglotStatus poly_execute(PolyglotIsolateThread thread, PolyglotValuePointer value_handle, PolyglotValuePointerPointer args, int args_size,
                    PolyglotValuePointerPointer return_value) {
        return withHandledErrors(() -> {
            Value function = fetchHandle(value_handle);
            Object[] jArgs = new Object[args_size];
            for (int i = 0; i < args_size; i++) {
                PolyglotValuePointer handle = args.read(i);
                jArgs[i] = fetchHandle(handle);
            }

            Value result = function.execute(jArgs);
            return_value.write(createHandle(result));
        });
    }

    @CEntryPoint(name = "poly_lookup")
    public static PolyglotStatus poly_lookup(PolyglotIsolateThread thread, PolyglotContextPointer context, @CConst CCharPointer language, @CConst CCharPointer symbol_name,
                    PolyglotValuePointerPointer symbol) {
        return withHandledErrors(() -> {
            Context jContext = ObjectHandles.getGlobal().get(context);
            String symbolName = CTypeConversion.toJavaString(symbol_name);
            String jLanguage = CTypeConversion.toJavaString(language);
            Value resultSymbol = jContext.getBindings(jLanguage).getMember(symbolName);
            if (resultSymbol == null) {
                throw reportError("Symbol " + symbolName + " in language " + jLanguage + " not found.", poly_generic_failure);
            } else {
                symbol.write(createHandle(resultSymbol));
            }
        });
    }

    @CEntryPoint(name = "poly_import_symbol")
    public static PolyglotStatus poly_import_symbol(PolyglotIsolateThread thread, PolyglotContextPointer context, @CConst CCharPointer symbol_name, PolyglotValuePointerPointer value) {
        return withHandledErrors(() -> {
            Context jContext = ObjectHandles.getGlobal().get(context);
            String symbolName = CTypeConversion.toJavaString(symbol_name);
            value.write(createHandle(jContext.getPolyglotBindings().getMember(symbolName)));
        });
    }

    @CEntryPoint(name = "poly_export_symbol")
    public static PolyglotStatus poly_export_symbol(PolyglotIsolateThread thread, PolyglotContextPointer context, @CConst CCharPointer symbol_name, PolyglotValuePointer value) {
        return withHandledErrors(() -> {
            Context jContext = ObjectHandles.getGlobal().get(context);
            String symbolName = CTypeConversion.toJavaString(symbol_name);
            jContext.getPolyglotBindings().putMember(symbolName, fetchHandle(value));
        });
    }

    @CEntryPoint(name = "poly_free_handle")
    public static PolyglotStatus poly_free_handle(PolyglotIsolateThread thread, PolyglotHandlePointer handle) {
        return withHandledErrors(() -> ObjectHandles.getGlobal().destroy(handle));
    }

    @CEntryPoint(name = "poly_value_is_boolean")
    public static PolyglotStatus poly_value_is_boolean(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(jValue.isBoolean() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_as_boolean")
    public static PolyglotStatus poly_value_as_boolean(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (jValue.isBoolean()) {
                result.write(jValue.asBoolean() ? 1 : 0);
            } else {
                throw reportError("Expected type Boolean but got " + jValue.getMetaObject().toString(), PolyglotStatus.poly_boolean_expected);
            }
        });
    }

    @CEntryPoint(name = "poly_create_boolean")
    public static PolyglotStatus poly_create_boolean(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, boolean value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(value)));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int8")
    public static PolyglotStatus poly_create_int8(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, byte value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(Byte.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int16")
    public static PolyglotStatus poly_create_int16(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, short value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(Short.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int32")
    public static PolyglotStatus poly_create_int32(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, int value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(Integer.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int64")
    public static PolyglotStatus poly_create_int64(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, long value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(Long.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint8")
    public static PolyglotStatus poly_create_uint8(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, @CUnsigned byte value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(Byte.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint32")
    public static PolyglotStatus poly_create_uint32(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, @CUnsigned int value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(Integer.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_float")
    public static PolyglotStatus poly_create_float(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, float value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(Float.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_double")
    public static PolyglotStatus poly_create_double(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, double value, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(Double.valueOf(value))));
        });
    }

    @CEntryPoint(name = "poly_create_char")
    public static PolyglotStatus poly_create_char(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, char c, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(c)));
        });
    }

    @CEntryPoint(name = "poly_create_string_utf8")
    public static PolyglotStatus poly_create_string_utf8(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, @CConst CCharPointer value, UnsignedWord length,
                    PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(CTypeConversion.toJavaString(value, length))));
        });
    }

    @CEntryPoint(name = "poly_value_create_null")
    public static PolyglotStatus poly_value_create_null(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(poly_context);
            result.write(createHandle(ctx.asValue(null)));
        });
    }

    @CEntryPoint(name = "poly_value_is_null")
    public static PolyglotStatus poly_value_is_null(PolyglotIsolateThread thread, PolyglotValuePointer object, CIntPointer result) {
        return withHandledErrors(() -> {
            Value value = fetchHandle(object);
            result.write(value.isNull() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_is_string")
    public static PolyglotStatus poly_value_is_string(PolyglotIsolateThread thread, PolyglotValuePointer object, CIntPointer result) {
        return withHandledErrors(() -> {
            Value value = fetchHandle(object);
            result.write(value.isString() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_is_number")
    public static PolyglotStatus poly_value_is_number(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(jValue.isNumber() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_float")
    public static PolyglotStatus poly_value_fits_in_float(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(dataObject.fitsInFloat() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_double")
    public static PolyglotStatus poly_value_fits_in_double(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(dataObject.fitsInDouble() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int8")
    public static PolyglotStatus poly_value_fits_in_int8(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(dataObject.fitsInByte() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int32")
    public static PolyglotStatus poly_value_fits_in_int32(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(dataObject.fitsInInt() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int64")
    public static PolyglotStatus poly_value_fits_in_int64(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(dataObject.fitsInLong() ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_uint8")
    public static PolyglotStatus poly_value_fits_in_uint8(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            int intValue = dataObject.asInt();
            result.write((dataObject.fitsInInt() && intValue >= 0 && intValue <= 255) ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_uint32")
    public static PolyglotStatus poly_value_fits_in_uint32(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            long longValue = dataObject.asLong();
            result.write((dataObject.fitsInLong() && longValue >= 0 && longValue <= 4228250625L) ? 1 : 0);
        });
    }

    @CEntryPoint(name = "poly_value_as_string_utf8")
    public static PolyglotStatus poly_value_as_string_utf8(PolyglotIsolateThread thread, PolyglotValuePointer value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (jValue.isString()) {
                writeString(jValue.asString(), buffer, buffer_size, result);
            } else {
                throw reportError("Expected type String but got " + jValue.getMetaObject().toString(), PolyglotStatus.poly_string_expected);
            }
        });
    }

    @CEntryPoint(name = "poly_value_as_int8")
    public static PolyglotStatus poly_value_as_int8(PolyglotIsolateThread thread, PolyglotValuePointer value, CCharPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asByte());
        });
    }

    @CEntryPoint(name = "poly_value_as_int32")
    public static PolyglotStatus poly_value_as_int32(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asInt());
        });
    }

    @CEntryPoint(name = "poly_value_as_int64")
    public static PolyglotStatus poly_value_as_int64(PolyglotIsolateThread thread, PolyglotValuePointer value, CLongPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asLong());
        });
    }

    @CEntryPoint(name = "poly_value_as_uint8")
    public static PolyglotStatus poly_value_as_uint8(PolyglotIsolateThread thread, PolyglotValuePointer value, CCharPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            int intValue = valueObject.asInt();
            if (intValue < 0 || intValue > 255) {
                throw reportError("Value " + Long.toHexString(intValue) + "does not fit in unsigned char", poly_generic_failure);
            }
            result.write((byte) intValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_uint32")
    public static PolyglotStatus poly_value_as_uint32(PolyglotIsolateThread thread, PolyglotValuePointer value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            long longValue = valueObject.asLong();
            if (longValue < 0 || longValue > 4228250625L) {
                throw reportError("Value " + Long.toHexString(longValue) + "does not fit in unsigned int 32", poly_generic_failure);
            }
            result.write((int) longValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_float")
    public static PolyglotStatus poly_value_as_float(PolyglotIsolateThread thread, PolyglotValuePointer value, CFloatPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(dataObject.asFloat());
        });
    }

    @CEntryPoint(name = "poly_value_as_double")
    public static PolyglotStatus poly_value_as_double(PolyglotIsolateThread thread, PolyglotValuePointer value, CDoublePointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            if (dataObject.isNumber()) {
                result.write(dataObject.asDouble());
            } else {
                throw reportError("Value is not a number.", poly_number_expected);
            }
        });
    }

    @CEntryPoint(name = "poly_number_of_languages")
    public static PolyglotStatus poly_number_of_languages(PolyglotIsolateThread thread, PolyglotEnginePointer engine_handle, CIntPointer result) {
        return withHandledErrors(() -> result.write(((Engine) fetchHandle(engine_handle)).getLanguages().size()));
    }

    @CEntryPoint(name = "poly_name_length_of_languages")
    public static PolyglotStatus poly_name_length_of_languages(PolyglotIsolateThread thread, PolyglotEnginePointer engine_handle, CIntPointer length_of_language_names) {
        return withHandledErrors(() -> {
            String[] sortedLangs = sortedLangs(ObjectHandles.getGlobal().get(engine_handle));
            for (int i = 0; i < sortedLangs.length; i++) {
                length_of_language_names.write(i, sortedLangs[i].getBytes(UTF8_CHARSET).length);
            }
        });
    }

    @CEntryPoint(name = "poly_available_languages")
    public static PolyglotStatus poly_available_languages(PolyglotIsolateThread thread, PolyglotEnginePointer engine_handle, CCharPointerPointer lang_array) {
        return withHandledErrors(() -> {
            String[] langs = sortedLangs(ObjectHandles.getGlobal().get(engine_handle));
            for (int i = 0; i < langs.length; i++) {
                CCharPointer name = lang_array.read(i);
                CTypeConversion.toCString(langs[i], UTF8_CHARSET, name, WordFactory.unsigned(langs[i].getBytes(UTF8_CHARSET).length));
            }
        });
    }

    @CEntryPoint(name = "poly_get_last_error_info")
    public static PolyglotStatus poly_get_last_error_info(PolyglotIsolateThread thread, ExtendedErrorInfoPointer result) {
        ErrorInfoHolder errorInfoHolder = errorInfo.get();
        if (errorInfoHolder != null) {
            result.write(errorInfoHolder.info);
            return poly_ok;
        } else {
            return poly_generic_failure;
        }
    }

    @CEntryPoint(name = "poly_create_object")
    public static PolyglotStatus poly_create_object(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Context c = ObjectHandles.getGlobal().get(poly_context);
            ProxyObject proxy = ProxyObject.fromMap(new HashMap<>());
            result.write(createHandle(c.asValue(proxy)));
        });
    }

    @CEntryPoint(name = "poly_set_member")
    public static PolyglotStatus poly_set_member(PolyglotIsolateThread thread, PolyglotValuePointer object, @CConst CCharPointer utf8_name, PolyglotValuePointer value) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(object);
            Value jValue = fetchHandle(value);
            jObject.putMember(CTypeConversion.toJavaString(utf8_name), jValue);
        });
    }

    private static class PolyglotCallbackInfoInternal {
        ObjectHandle[] arguments;
        VoidPointer data;

        PolyglotCallbackInfoInternal(ObjectHandle[] arguments, VoidPointer data) {
            this.arguments = arguments;
            this.data = data;
        }
    }

    @CEntryPoint(name = "poly_create_function")
    public static PolyglotStatus poly_create_function(PolyglotIsolateThread thread, PolyglotContextPointer poly_context, PolyglotCallbackPointer callback, VoidPointer data,
                    PolyglotValuePointerPointer value) {
        return withHandledErrors(() -> {
            Context c = ObjectHandles.getGlobal().get(poly_context);
            ProxyExecutable executable = (Value... arguments) -> {
                ObjectHandle[] handleArgs = new ObjectHandle[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    handleArgs[i] = createHandle(arguments[i]);
                }
                PolyglotCallbackInfo cbInfo = (PolyglotCallbackInfo) createHandle(new PolyglotCallbackInfoInternal(handleArgs, data));
                try {
                    PolyglotValuePointer result = callback.invoke((PolyglotIsolateThread) CEntryPointContext.getCurrentIsolateThread(), cbInfo);
                    CallbackException ce = exceptionsTL.get();
                    if (ce != null) {
                        exceptionsTL.remove();
                        throw ce;
                    } else {
                        return PolyglotNativeAPI.fetchHandle(result);
                    }
                } finally {
                    PolyglotCallbackInfoInternal info = fetchHandle(cbInfo);
                    for (ObjectHandle arg : info.arguments) {
                        freeHandle(arg);
                    }
                    freeHandle(cbInfo);
                }
            };
            value.write(createHandle(c.asValue(executable)));
        });
    }

    @CEntryPoint(name = "poly_get_callback_info")
    public static PolyglotStatus poly_get_callback_info(PolyglotIsolateThread thread, PolyglotCallbackInfo callback_info, SizeTPointer argc, PolyglotValuePointerPointer argv, WordPointer data) {
        return withHandledErrors(() -> {
            PolyglotCallbackInfoInternal callbackInfo = fetchHandle(callback_info);
            UnsignedWord numberOfArguments = WordFactory.unsigned(callbackInfo.arguments.length);
            UnsignedWord bufferSize = argc.read();
            UnsignedWord size = bufferSize.belowThan(numberOfArguments) ? bufferSize : numberOfArguments;
            argc.write(size);
            for (UnsignedWord i = WordFactory.zero(); i.belowThan(size); i = i.add(1)) {
                int index = (int) i.rawValue();
                ObjectHandle argument = callbackInfo.arguments[index];
                argv.write(index, argument);
            }
            data.write(callbackInfo.data);
        });
    }

    @CEntryPoint(name = "poly_throw_error")
    public static PolyglotStatus polylgot_throw_error(PolyglotIsolateThread thread, @CConst CCharPointer utf8_code, @CConst CCharPointer utf8_message) {
        return withHandledErrors(() -> exceptionsTL.set(new CallbackException(CTypeConversion.toJavaString(utf8_message), CTypeConversion.toJavaString(utf8_code))));
    }

    @CEntryPoint(name = "poly_value_to_string_utf8")
    public static PolyglotStatus poly_value_to_string_utf8(PolyglotIsolateThread thread, PolyglotValuePointer value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer string_length) {
        return withHandledErrors(() -> writeString(fetchHandle(value).toString(), buffer, buffer_size, string_length));
    }

    @CEntryPoint(name = "poly_get_member")
    public static PolyglotStatus poly_get_member(PolyglotIsolateThread thread, PolyglotValuePointer object, @CConst CCharPointer utf8_name, PolyglotValuePointerPointer result) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(object);
            result.write(createHandle(jObject.getMember(CTypeConversion.toJavaString(utf8_name))));
        });
    }

    @CEntryPoint(name = "poly_has_member")
    public static PolyglotStatus poly_has_member(PolyglotIsolateThread thread, PolyglotValuePointer object, @CConst CCharPointer utf8_name, CIntPointer result) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(object);
            result.write(jObject.hasMember(CTypeConversion.toJavaString(utf8_name)) ? 1 : 0);
        });
    }

    private static void writeString(String valueString, CCharPointer buffer, UnsignedWord length, SizeTPointer result) {
        UnsignedWord stringLength = WordFactory.unsigned(valueString.getBytes(UTF8_CHARSET).length);
        if (buffer.isNull()) {
            result.write(stringLength);
        } else {
            result.write(CTypeConversion.toCString(valueString, UTF8_CHARSET, buffer, length));
        }
    }

    private static String[] sortedLangs(Engine engine) {
        Set<String> langSet = engine.getLanguages().keySet();
        String[] langs = langSet.toArray(new String[langSet.size()]);
        Arrays.sort(langs);
        return langs;
    }

    private static void resetErrorState() {
        ErrorInfoHolder current = errorInfo.get();
        if (current != null) {
            current.messageHolder.close();
            UnmanagedMemory.free(current.info);
            errorInfo.remove();
        }
    }

    private static RuntimeException reportError(String message, PolyglotStatus errorCode) {
        throw new PolyglotNativeAPIError(errorCode, message);
    }

    private static PolyglotStatus handleThrowable(Throwable t) {
        PolyglotStatus errorCode = t instanceof PolyglotNativeAPIError ? ((PolyglotNativeAPIError) t).getCode() : poly_generic_failure;
        String message = t.getMessage();
        PolyglotExtendedErrorInfo unmanagedErrorInfo = UnmanagedMemory.malloc(SizeOf.get(PolyglotExtendedErrorInfo.class));
        unmanagedErrorInfo.setErrorCode(errorCode.getCValue());
        CCharPointerHolder holder = CTypeConversion.toCString(message);
        CCharPointer value = holder.get();
        unmanagedErrorInfo.setErrorMessage(value);
        errorInfo.set(new ErrorInfoHolder(unmanagedErrorInfo, holder));

        return errorCode;
    }

    private interface VoidThunk {
        void apply() throws Exception;
    }

    private static PolyglotStatus withHandledErrors(VoidThunk func) {
        resetErrorState();
        try {
            func.apply();
            return poly_ok;
        } catch (Throwable t) {
            return handleThrowable(t);
        }
    }

    private static ObjectHandle createHandle(Object result) {
        return ObjectHandles.getGlobal().create(result);
    }

    private static <T> T fetchHandle(ObjectHandle object) {
        return ObjectHandles.getGlobal().get(object);
    }

    private static void freeHandle(ObjectHandle handle) {
        ObjectHandles.getGlobal().destroy(handle);
    }

    public static class CallbackException extends RuntimeException {
        static final long serialVersionUID = 123123098097526L;
        private String errorCode;

        CallbackException(String message, String errorCode) {
            super(message);
            this.errorCode = errorCode;
        }
    }

    @CStruct(value = "poly_thread", isIncomplete = true)
    @CTypedef(name = "poly_thread")
    interface PolyglotIsolateThread extends IsolateThread {
    }
}
