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

import java.util.Arrays;

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

    @TruffleBoundary
    public static CandidateMethodWithArgs matchCandidate(Method candidate, Object[] arguments, Klass[] parameterKlasses, ToEspressoNode.DynamicToEspresso toEspressoNode) {
        boolean canConvert = true;
        int paramLength = parameterKlasses.length;
        Object[] convertedArgs = new Object[arguments.length];

        for (int j = 0; j < arguments.length; j++) {
            Klass paramType = null;
            Object argument = arguments[j];
            boolean checkNeeded = true;
            try {
                if (candidate.isVarargs() && j >= paramLength - 1) {
                    paramType = ((ArrayKlass) parameterKlasses[paramLength - 1]).getComponentType();
                    InteropLibrary library = InteropLibrary.getUncached();
                    if (arguments.length == paramLength && j == paramLength - 1) {
                        if (library.hasArrayElements(argument)) {
                            long arraySize = library.getArraySize(argument);
                            if (arraySize >= Integer.MAX_VALUE) {
                                canConvert = false;
                                break;
                            }
                            convertedArgs = Arrays.copyOf(convertedArgs, convertedArgs.length + (int) arraySize - 1);
                            for (int l = 0; l < arraySize; l++) {
                                if (library.isArrayElementReadable(argument, l)) {
                                    Object element = library.readArrayElement(argument, l);
                                    convertedArgs[j + l] = toEspressoNode.execute(element, paramType);
                                } else {
                                    canConvert = false;
                                    break;
                                }
                            }
                            checkNeeded = false;
                        } else {
                            if (library.isNull(argument)) {
                                // null varargs array
                                convertedArgs[j] = StaticObject.NULL;
                                checkNeeded = false;
                            }
                        }
                    }
                }
                if (checkNeeded) {
                    if (paramType == null) {
                        paramType = parameterKlasses[j];
                    }
                    convertedArgs[j] = toEspressoNode.execute(argument, paramType);
                }
            } catch (UnsupportedTypeException e) {
                canConvert = false;
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw EspressoError.shouldNotReachHere();
            }
        }
        return canConvert ? new CandidateMethodWithArgs(candidate, convertedArgs, parameterKlasses) : null;
    }

    @TruffleBoundary
    public static CandidateMethodWithArgs ensureVarArgsArrayCreated(CandidateMethodWithArgs matched) {
        int varArgsIndex = matched.getParameterTypes().length - 1;
        Klass varArgsArrayType = matched.getParameterTypes()[varArgsIndex];
        Klass varArgsType = ((ArrayKlass) varArgsArrayType).getComponentType();
        boolean isPrimitive = varArgsType.isPrimitive();

        int varArgsLength = matched.getConvertedArgs().length - matched.getParameterTypes().length + 1;
        // special handling for null varargs array
        if (varArgsLength == 1 && matched.getConvertedArgs()[varArgsIndex] == StaticObject.NULL) {
            return matched;
        }

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
        return new CandidateMethodWithArgs(matched.getMethod(), finalConvertedArgs, matched.getParameterTypes());
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
