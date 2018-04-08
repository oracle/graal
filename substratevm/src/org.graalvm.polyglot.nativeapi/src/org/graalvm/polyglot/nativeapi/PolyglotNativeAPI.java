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

import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_generic_failure;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_number_expected;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_ok;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
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
import org.graalvm.polyglot.nativeapi.types.CBoolPointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedBytePointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedIntPointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedShortPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.ExtendedErrorInfoPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotCallback;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotCallbackInfo;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContext;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEngine;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEnginePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExtendedErrorInfo;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotHandle;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolateThread;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotValue;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotValuePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.SizeTPointer;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CConst;
import com.oracle.svm.core.c.CHeader;
import com.oracle.svm.core.c.CUnsigned;

@SuppressWarnings("unused")
@CHeader(value = PolyglotAPIHeader.class)
public final class PolyglotNativeAPI {

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final int MAX_UNSIGED_BYTE = (1 << 8) - 1;
    private static final int MAX_UNSIGNED_SHORT = (1 << 16) - 1;
    private static final long MAX_UNSIGNED_INT = (1L << 32) - 1;

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
    public static PolyglotStatus poly_create_engine(PolyglotIsolateThread thread, PolyglotEnginePointer engine) {
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
    public static PolyglotStatus poly_create_context(PolyglotIsolateThread thread, PolyglotEngine engine, PolyglotContextPointer context) {
        return withHandledErrors(() -> {
            Engine jEngine = ObjectHandles.getGlobal().get(engine);
            Context c = Context.newBuilder().engine(jEngine).allowAllAccess(true).build();
            context.write(createHandle(c));
        });
    }

    @CEntryPoint(name = "poly_context_eval")
    public static PolyglotStatus poly_context_eval(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer language, CCharPointer name, CCharPointer code,
                    PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context c = ObjectHandles.getGlobal().get(context);
            String languageName = CTypeConversion.toJavaString(language);
            String jName = CTypeConversion.toJavaString(name);
            String jCode = CTypeConversion.toJavaString(code);

            Source sourceCode = Source.newBuilder(languageName, jCode, jName).build();
            result.write(createHandle(c.eval(sourceCode)));
        });
    }

    @CEntryPoint(name = "poly_context_get_bindings")
    public static PolyglotStatus poly_context_get_bindings(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer language, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context jContext = ObjectHandles.getGlobal().get(context);
            String jLanguage = CTypeConversion.toJavaString(language);
            Value languageBindings = jContext.getBindings(jLanguage);
            result.write(createHandle(languageBindings));
        });
    }

    @CEntryPoint(name = "poly_context_get_polyglot_bindings")
    public static PolyglotStatus poly_context_get_polyglot_bindings(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context jContext = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(jContext.getPolyglotBindings()));
        });
    }

    @CEntryPoint(name = "poly_value_execute")
    public static PolyglotStatus poly_value_execute(PolyglotIsolateThread thread, PolyglotValue value, PolyglotValuePointer args, int args_size,
                    PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Value function = fetchHandle(value);
            Object[] jArgs = new Object[args_size];
            for (int i = 0; i < args_size; i++) {
                PolyglotValue handle = args.read(i);
                jArgs[i] = fetchHandle(handle);
            }

            Value resultValue = function.execute(jArgs);
            result.write(createHandle(resultValue));
        });
    }

    @CEntryPoint(name = "poly_release_handle")
    public static PolyglotStatus poly_release_handle(PolyglotIsolateThread thread, PolyglotHandle handle) {
        return withHandledErrors(() -> ObjectHandles.getGlobal().destroy(handle));
    }

    @CEntryPoint(name = "poly_value_is_boolean")
    public static PolyglotStatus poly_value_is_boolean(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jValue.isBoolean()));
        });
    }

    @CEntryPoint(name = "poly_value_as_boolean")
    public static PolyglotStatus poly_value_as_bool(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (jValue.isBoolean()) {
                result.write(CTypeConversion.toCBoolean(jValue.asBoolean()));
            } else {
                throw reportError("Expected type Boolean but got " + jValue.getMetaObject().toString(), PolyglotStatus.poly_boolean_expected);
            }
        });
    }

    @CEntryPoint(name = "poly_create_boolean")
    public static PolyglotStatus poly_create_bool(PolyglotIsolateThread thread, PolyglotContext context, boolean value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(value)));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int8")
    public static PolyglotStatus poly_create_int8(PolyglotIsolateThread thread, PolyglotContext context, byte value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Byte.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int16")
    public static PolyglotStatus poly_create_int16(PolyglotIsolateThread thread, PolyglotContext context, short value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Short.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int32")
    public static PolyglotStatus poly_create_int32(PolyglotIsolateThread thread, PolyglotContext context, int value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Integer.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int64")
    public static PolyglotStatus poly_create_int64(PolyglotIsolateThread thread, PolyglotContext context, long value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Long.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint8")
    public static PolyglotStatus poly_create_uint8(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned byte value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Byte.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint16")
    public static PolyglotStatus poly_create_uint16(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned short value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Short.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint32")
    public static PolyglotStatus poly_create_uint32(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned int value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Integer.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_float")
    public static PolyglotStatus poly_create_float(PolyglotIsolateThread thread, PolyglotContext context, float value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Float.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_double")
    public static PolyglotStatus poly_create_double(PolyglotIsolateThread thread, PolyglotContext context, double value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(Double.valueOf(value))));
        });
    }

    @CEntryPoint(name = "poly_create_char")
    public static PolyglotStatus poly_create_char(PolyglotIsolateThread thread, PolyglotContext context, char c, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(c)));
        });
    }

    @CEntryPoint(name = "poly_create_string_utf8")
    public static PolyglotStatus poly_create_string_utf8(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer value, UnsignedWord length,
                    PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(CTypeConversion.toJavaString(value, length, UTF8_CHARSET))));
        });
    }

    @CEntryPoint(name = "poly_create_null")
    public static PolyglotStatus poly_create_null(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = ObjectHandles.getGlobal().get(context);
            result.write(createHandle(ctx.asValue(null)));
        });
    }

    @CEntryPoint(name = "poly_value_is_null")
    public static PolyglotStatus poly_value_is_null(PolyglotIsolateThread thread, PolyglotValue object, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value value = fetchHandle(object);
            result.write(CTypeConversion.toCBoolean(value.isNull()));
        });
    }

    @CEntryPoint(name = "poly_value_is_string")
    public static PolyglotStatus poly_value_is_string(PolyglotIsolateThread thread, PolyglotValue object, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value value = fetchHandle(object);
            result.write(CTypeConversion.toCBoolean(value.isString()));
        });
    }

    @CEntryPoint(name = "poly_value_is_number")
    public static PolyglotStatus poly_value_is_number(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jValue.isNumber()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_float")
    public static PolyglotStatus poly_value_fits_in_float(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInFloat()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_double")
    public static PolyglotStatus poly_value_fits_in_double(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInDouble()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int8")
    public static PolyglotStatus poly_value_fits_in_int8(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInByte()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int32")
    public static PolyglotStatus poly_value_fits_in_int32(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInInt()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int64")
    public static PolyglotStatus poly_value_fits_in_int64(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInLong()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_uint8")
    public static PolyglotStatus poly_value_fits_in_uint8(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            int intValue = dataObject.asInt();
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInInt() && intValue >= 0 && intValue <= 255));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_uint32")
    public static PolyglotStatus poly_value_fits_in_uint32(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            long longValue = dataObject.asLong();
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInLong() && longValue >= 0 && longValue <= 4228250625L));
        });
    }

    @CEntryPoint(name = "poly_value_as_string_utf8")
    public static PolyglotStatus poly_value_as_string_utf8(PolyglotIsolateThread thread, PolyglotValue value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (jValue.isString()) {
                writeString(jValue.asString(), buffer, buffer_size, result, UTF8_CHARSET);
            } else {
                throw reportError("Expected type String but got " + jValue.getMetaObject().toString(), PolyglotStatus.poly_string_expected);
            }
        });
    }

    @CEntryPoint(name = "poly_value_as_int8")
    public static PolyglotStatus poly_value_as_int8(PolyglotIsolateThread thread, PolyglotValue value, CCharPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asByte());
        });
    }

    @CEntryPoint(name = "poly_value_as_int32")
    public static PolyglotStatus poly_value_as_int32(PolyglotIsolateThread thread, PolyglotValue value, CIntPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asInt());
        });
    }

    @CEntryPoint(name = "poly_value_as_int64")
    public static PolyglotStatus poly_value_as_int64(PolyglotIsolateThread thread, PolyglotValue value, CLongPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asLong());
        });
    }

    @CEntryPoint(name = "poly_value_as_uint8")
    public static PolyglotStatus poly_value_as_uint8(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedBytePointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            int intValue = valueObject.asInt();
            if (intValue < 0 || intValue > MAX_UNSIGED_BYTE) {
                throw reportError("Value " + Integer.toHexString(intValue) + "does not fit in uint8_t", poly_generic_failure);
            }
            result.write((byte) intValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_uint16")
    public static PolyglotStatus poly_value_as_uint16(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedShortPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            int intValue = valueObject.asInt();
            if (intValue < 0 || intValue > MAX_UNSIGNED_SHORT) {
                throw reportError("Value " + Integer.toHexString(intValue) + "does not fit in uint16_t", poly_generic_failure);
            }
            result.write((short) intValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_uint32")
    public static PolyglotStatus poly_value_as_uint32(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedIntPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            long longValue = valueObject.asLong();
            if (longValue < 0 || longValue > MAX_UNSIGNED_INT) {
                throw reportError("Value " + Long.toHexString(longValue) + "does not fit in uint32_t", poly_generic_failure);
            }
            result.write((int) longValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_float")
    public static PolyglotStatus poly_value_as_float(PolyglotIsolateThread thread, PolyglotValue value, CFloatPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(dataObject.asFloat());
        });
    }

    @CEntryPoint(name = "poly_value_as_double")
    public static PolyglotStatus poly_value_as_double(PolyglotIsolateThread thread, PolyglotValue value, CDoublePointer result) {
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
    public static PolyglotStatus poly_number_of_languages(PolyglotIsolateThread thread, PolyglotEngine engine, CIntPointer result) {
        return withHandledErrors(() -> result.write(((Engine) fetchHandle(engine)).getLanguages().size()));
    }

    @CEntryPoint(name = "poly_name_length_of_languages")
    public static PolyglotStatus poly_name_length_of_languages(PolyglotIsolateThread thread, PolyglotEngine engine, CIntPointer length_of_language_names) {
        return withHandledErrors(() -> {
            String[] sortedLangs = sortedLangs(ObjectHandles.getGlobal().get(engine));
            for (int i = 0; i < sortedLangs.length; i++) {
                length_of_language_names.write(i, sortedLangs[i].getBytes(UTF8_CHARSET).length);
            }
        });
    }

    @CEntryPoint(name = "poly_available_languages")
    public static PolyglotStatus poly_available_languages(PolyglotIsolateThread thread, PolyglotEngine engine, CCharPointerPointer lang_array) {
        return withHandledErrors(() -> {
            String[] langs = sortedLangs(ObjectHandles.getGlobal().get(engine));
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
    public static PolyglotStatus poly_create_object(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context c = ObjectHandles.getGlobal().get(context);
            ProxyObject proxy = ProxyObject.fromMap(new HashMap<>());
            result.write(createHandle(c.asValue(proxy)));
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
    public static PolyglotStatus poly_create_function(PolyglotIsolateThread thread, PolyglotContext context, PolyglotCallback callback, VoidPointer data,
                    PolyglotValuePointer value) {
        return withHandledErrors(() -> {
            Context c = ObjectHandles.getGlobal().get(context);
            ProxyExecutable executable = (Value... arguments) -> {
                ObjectHandle[] handleArgs = new ObjectHandle[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    handleArgs[i] = createHandle(arguments[i]);
                }
                PolyglotCallbackInfo cbInfo = (PolyglotCallbackInfo) createHandle(new PolyglotCallbackInfoInternal(handleArgs, data));
                try {
                    PolyglotValue result = callback.invoke((PolyglotIsolateThread) CEntryPointContext.getCurrentIsolateThread(), cbInfo);
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
    public static PolyglotStatus poly_get_callback_info(PolyglotIsolateThread thread, PolyglotCallbackInfo callback_info, SizeTPointer argc, PolyglotValuePointer argv, WordPointer data) {
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
    public static PolyglotStatus poly_throw_error(PolyglotIsolateThread thread, @CConst CCharPointer utf8_code, @CConst CCharPointer utf8_message) {
        return withHandledErrors(() -> exceptionsTL.set(new CallbackException(CTypeConversion.toJavaString(utf8_message), CTypeConversion.toJavaString(utf8_code))));
    }

    @CEntryPoint(name = "poly_value_get_member")
    public static PolyglotStatus poly_value_get_member(PolyglotIsolateThread thread, PolyglotValue object, @CConst CCharPointer utf8_name, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(object);
            result.write(createHandle(jObject.getMember(CTypeConversion.toJavaString(utf8_name))));
        });
    }

    @CEntryPoint(name = "poly_value_put_member")
    public static PolyglotStatus poly_value_put_member(PolyglotIsolateThread thread, PolyglotValue object, @CConst CCharPointer utf8_name, PolyglotValue value) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(object);
            Value jValue = fetchHandle(value);
            jObject.putMember(CTypeConversion.toJavaString(utf8_name), jValue);
        });
    }

    @CEntryPoint(name = "poly_value_has_member")
    public static PolyglotStatus poly_value_has_member(PolyglotIsolateThread thread, PolyglotValue object, @CConst CCharPointer utf8_name, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(object);
            result.write(CTypeConversion.toCBoolean(jObject.hasMember(CTypeConversion.toJavaString(utf8_name))));
        });
    }

    private static void writeString(String valueString, CCharPointer buffer, UnsignedWord length, SizeTPointer result, Charset charset) {
        UnsignedWord stringLength = WordFactory.unsigned(valueString.getBytes(charset).length);
        if (buffer.isNull()) {
            result.write(stringLength);
        } else {
            result.write(CTypeConversion.toCString(valueString, charset, buffer, length));
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
        PolyglotExtendedErrorInfo unmanagedErrorInfo = UnmanagedMemory.malloc(SizeOf.get(PolyglotExtendedErrorInfo.class));
        unmanagedErrorInfo.setErrorCode(errorCode.getCValue());
        CCharPointerHolder holder = CTypeConversion.toCString(t.getMessage());
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
}
