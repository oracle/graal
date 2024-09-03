/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.PrimitiveKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public class MethodArgsUtils {

    /**
     * Tries to match a given candidate with from a given arguments array. A match happens when all
     * given arguments can be {@link ToEspressoNode converted to an espresso object} of the klass of
     * their corresponding parameter's type in the method signature.
     * <p>
     * In case of a variable argument method, all parameters except the last (varargs argument) need
     * to match, then, depending on what arguments are left to match:
     * <ul>
     * <li>If there is a single argument left, and it is an
     * {@link InteropLibrary#hasArrayElements(Object)} interop array}, consider it to be containing
     * the varargs, then append its elements into the returned
     * {@link CandidateMethodWithArgs#getConvertedArgs()}, while trying to match each of its
     * elements with the varargs component type..</li>
     * <li>Else, every remaining parameter is matched with the component type of the varargs.</li>
     * </ul>
     * If at any point, matching an argument is not successful, this method returns {@code null}.
     * <p>
     * Here are a couple examples:
     * <p>
     * Trying to match method {@code int m(long, String)} with the argument types:
     * <ul>
     * <li>{@code [long l, String s]} -> {@code [l, s]}</li>
     * <li>{@code [byte b, String s]} -> {@code [(long) b, s]}</li>
     * <li>{@code [double d, String s]} -> fails</li>
     * <li>{@code [long l, List s]} -> fails</li>
     * <li>{@code [long l]} -> fails</li>
     * </ul>
     * <p>
     * Trying to match method {@code int m(long, String...)} with the interop argument types:
     * <ul>
     * <li>{@code [long l, String s]} -> {@code [l, s]}</li>
     * <li>{@code [byte b, String s]} -> {@code [(long) b, s]}</li>
     * <li>{@code [double d, String s]} -> fails</li>
     * <li>{@code [long l, String[](s1, s2, s3)]} -> [l, s1, s2, s3]</li>
     * <li>{@code [long l, List(s1, s2, s3)]} -> [l, s1, s2, s3]</li>
     * <li>{@code [long l]} -> [l]</li>
     * <li>{@code [long l, NULL]} -> [l, NULL]</li>
     * </ul>
     */
    public static CandidateMethodWithArgs matchCandidate(Method candidate, Object[] arguments, Klass[] parameterKlasses, ToEspressoNode.DynamicToEspresso toEspressoNode) {
        Object[] convertedArgs = convertedArgs(candidate, arguments, parameterKlasses, toEspressoNode);
        if (convertedArgs != null) {
            return new CandidateMethodWithArgs(candidate, convertedArgs, parameterKlasses);
        }
        return null;
    }

    @TruffleBoundary
    private static Object[] convertedArgs(Method candidate, Object[] arguments, Klass[] parameterKlasses, ToEspressoNode.DynamicToEspresso toEspressoNode) {
        assert arguments.length == parameterKlasses.length || (candidate.isVarargs() && arguments.length >= parameterKlasses.length - 1);
        int paramLength = parameterKlasses.length;
        try {
            // Determine if we need to expand a given vararg array
            long expansionLength = 0;
            boolean needsExpansion = false;
            boolean isNullVararg = false;
            if (candidate.isVarargs() && arguments.length == parameterKlasses.length) {
                Object varargArray = arguments[paramLength - 1];
                InteropLibrary lib = InteropLibrary.getUncached();
                if (lib.hasArrayElements(varargArray)) {
                    // Account for the additional array slot in the arguments array
                    expansionLength = lib.getArraySize(varargArray) - 1;
                    needsExpansion = true;
                }
                if (lib.isNull(varargArray)) {
                    isNullVararg = true;
                }
            }

            long arrayLen = arguments.length + expansionLength;
            if (arrayLen > Integer.MAX_VALUE) {
                return null;
            }

            Object[] convertedArgs = new Object[Math.toIntExact(arrayLen)];

            // First, convert args up until the vararg array if any.
            int nonVarargsLen = parameterKlasses.length + (candidate.isVarargs() ? -1 : 0);
            for (int pos = 0; pos < nonVarargsLen; pos++) {
                Klass paramType = parameterKlasses[pos];
                convertedArgs[pos] = toEspressoNode.execute(arguments[pos], paramType);
            }

            if (candidate.isVarargs()) {
                // Then, expand the given vararg array or collect the leftover arguments as the
                // component type of the vararg.
                Klass varargType = ((ArrayKlass) parameterKlasses[paramLength - 1]).getComponentType();
                if (needsExpansion) {
                    Object varargArray = arguments[paramLength - 1];
                    InteropLibrary lib = InteropLibrary.getUncached();
                    assert lib.hasArrayElements(varargArray);
                    // Expand and convert given array.
                    for (int varargPos = 0; varargPos <= expansionLength; varargPos++) {
                        if (!lib.isArrayElementReadable(varargArray, varargPos)) {
                            return null;
                        }
                        Object element = lib.readArrayElement(varargArray, varargPos);
                        convertedArgs[nonVarargsLen + varargPos] = toEspressoNode.execute(element, varargType);
                    }
                } else if (isNullVararg) {
                    convertedArgs[nonVarargsLen] = StaticObject.NULL;
                } else {
                    // Convert leftover arguments.
                    for (int pos = nonVarargsLen; pos < arguments.length; pos++) {
                        convertedArgs[pos] = toEspressoNode.execute(arguments[pos], varargType);
                    }
                }
            }

            return convertedArgs;
        } catch (ArithmeticException // If expansion of the given vararg array overflows
                        | OutOfMemoryError // If converted args array creation fails.
                        | UnsupportedTypeException e) {
            return null;
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    /**
     * From a given varargs method and expanded arguments (see {@link #matchCandidate}), creates a
     * new arguments array containing the original arguments until the varargs argument, then
     * collects the trailing varargs arguments in an array and prepend it. If the trailing arguments
     * is a single {@code null}, then it is prepended instead.
     * <p>
     * For example, Trying to match method {@code int m(long, String...)} with the arguments:
     * <ul>
     * <li>{@code [long l, String s]} -> {@code [l, [s]]}</li>
     * <li>{@code [long l, String s1, String s2, String s3] -> [l, [s1, s2, s3]]}</li>
     * <li>{@code [long l]} -> [l, []]</li>
     * <li>{@code [long l, NULL]} -> [l, NULL]</li>
     */
    public static CandidateMethodWithArgs ensureVarArgsArrayCreated(CandidateMethodWithArgs matched) {
        assert matched.getMethod().isVarargs();
        int varArgsIndex = matched.getParameterTypes().length - 1;
        Klass varArgsArrayType = matched.getParameterTypes()[varArgsIndex];
        Klass varArgsType = ((ArrayKlass) varArgsArrayType).getComponentType();
        boolean isPrimitive = varArgsType.isPrimitive();

        int varArgsLength = matched.getConvertedArgs().length - matched.getParameterTypes().length + 1;
        // special handling for null varargs array
        if (varArgsLength == 1 && matched.getConvertedArgs()[varArgsIndex] == StaticObject.NULL) {
            return matched;
        }

        Object[] finalConvertedArgs = shrinkVarargs(matched, varArgsIndex, varArgsType, isPrimitive, varArgsLength);
        return new CandidateMethodWithArgs(matched.getMethod(), finalConvertedArgs, matched.getParameterTypes());
    }

    @TruffleBoundary
    private static Object[] shrinkVarargs(CandidateMethodWithArgs matched, int varArgsIndex, Klass varArgsType, boolean isPrimitive, int varArgsLength) {
        StaticObject varArgsArray = isPrimitive ? varArgsType.getAllocator().createNewPrimitiveArray(varArgsType, varArgsLength) : varArgsType.allocateReferenceArray(varArgsLength);

        int index = 0;
        for (int i = varArgsIndex; i < matched.getConvertedArgs().length; i++) {
            Object convertedArg = matched.getConvertedArgs()[i];
            if (!isPrimitive) {
                Object[] array = varArgsArray.unwrap(matched.getMethod().getLanguage());
                array[index++] = convertedArg;
            } else {
                putPrimitiveArg(varArgsArray, convertedArg, index, matched.getMethod().getLanguage());
                index++;
            }
        }
        Object[] finalConvertedArgs = new Object[matched.getParameterTypes().length];
        System.arraycopy(matched.getConvertedArgs(), 0, finalConvertedArgs, 0, varArgsIndex);
        finalConvertedArgs[varArgsIndex] = varArgsArray;
        return finalConvertedArgs;
    }

    public static PrimitiveKlass boxedTypeToPrimitiveType(Klass primitiveType) {
        Meta meta = primitiveType.getMeta();
        if (primitiveType == meta.java_lang_Boolean) {
            return meta._boolean;
        } else if (primitiveType == meta.java_lang_Byte) {
            return meta._byte;
        } else if (primitiveType == meta.java_lang_Short) {
            return meta._short;
        } else if (primitiveType == meta.java_lang_Character) {
            return meta._char;
        } else if (primitiveType == meta.java_lang_Integer) {
            return meta._int;
        } else if (primitiveType == meta.java_lang_Long) {
            return meta._long;
        } else if (primitiveType == meta.java_lang_Float) {
            return meta._float;
        } else if (primitiveType == meta.java_lang_Double) {
            return meta._double;
        } else {
            return null;
        }
    }

    public static Klass primitiveTypeToBoxedType(PrimitiveKlass primitiveType) {
        Meta meta = primitiveType.getMeta();
        switch (primitiveType.getPrimitiveJavaKind()) {
            // @formatter:off
            case Int: return meta.java_lang_Integer;
            case Boolean: return meta.java_lang_Boolean;
            case Char: return meta.java_lang_Character;
            case Short: return meta.java_lang_Short;
            case Byte: return meta.java_lang_Byte;
            case Long: return meta.java_lang_Long;
            case Double: return meta.java_lang_Double;
            case Float: return meta.java_lang_Float;
            case Void: return meta.java_lang_Void;
            default: return null;
            // @formatter:on
        }
    }

    static boolean isWideningPrimitiveConversion(Klass toType, Klass fromType) {
        assert toType.isPrimitive();
        Meta meta = toType.getMeta();
        if (fromType == meta._byte) {
            return toType == meta._short || toType == meta._int || toType == meta._long || toType == meta._float || toType == meta._double;
        } else if (fromType == meta._short) {
            return toType == meta._int || toType == meta._long || toType == meta._float || toType == meta._double;
        } else if (fromType == meta._char) {
            return toType == meta._int || toType == meta._long || toType == meta._float || toType == meta._double;
        } else if (fromType == meta._int) {
            return toType == meta._long || toType == meta._float || toType == meta._double;
        } else if (fromType == meta._long) {
            return toType == meta._float || toType == meta._double;
        } else if (fromType == meta._float) {
            return toType == meta._double;
        } else {
            return false;
        }
    }

    private static void putPrimitiveArg(StaticObject varArgsArray, Object arg, int index, EspressoLanguage language) {
        Klass klass = varArgsArray.getKlass();
        Meta meta = klass.getMeta();

        if (klass == meta._boolean_array) {
            boolean[] array = varArgsArray.unwrap(language);
            array[index] = (boolean) arg;
        } else if (klass == meta._int_array) {
            int[] array = varArgsArray.unwrap(language);
            array[index] = (int) arg;
        } else if (klass == meta._long_array) {
            long[] array = varArgsArray.unwrap(language);
            array[index] = (long) arg;
        } else if (klass == meta._double_array) {
            double[] array = varArgsArray.unwrap(language);
            array[index] = (double) arg;
        } else if (klass == meta._float_array) {
            float[] array = varArgsArray.unwrap(language);
            array[index] = (float) arg;
        } else if (klass == meta._short_array) {
            short[] array = varArgsArray.unwrap(language);
            array[index] = (short) arg;
        } else if (klass == meta._byte_array) {
            byte[] array = varArgsArray.unwrap(language);
            array[index] = (byte) arg;
        } else if (klass == meta._char_array) {
            char[] array = varArgsArray.unwrap(language);
            array[index] = (char) arg;
        }
    }
}
