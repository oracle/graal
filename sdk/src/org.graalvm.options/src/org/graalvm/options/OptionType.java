/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.options;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a type of an option that allows to convert string values to Java values.
 *
 * @since 19.0
 */
public final class OptionType<T> {

    private static final Consumer<?> EMPTY_VALIDATOR = new Consumer<>() {
        public void accept(Object t) {
        }
    };

    private final String name;
    private final Converter<T> converter;
    private final Consumer<T> validator;
    private final boolean isOptionMap;
    private final boolean isDefaultType;

    /**
     * Constructs a new option type with name and function that allows to convert a string to the
     * option type and validator of the option values.
     *
     * @param name the name of the type.
     * @param stringConverter a function that converts a string value to the option value. Can throw
     *            {@link IllegalArgumentException} to indicate an invalid string.
     * @param validator used for validating the option value. Throws
     *            {@link IllegalArgumentException} if the value is invalid.
     *
     * @since 19.0
     */
    public OptionType(String name, Function<String, T> stringConverter, Consumer<T> validator) {
        this(name, new Converter<T>() {
            @Override
            public T convert(T previousValue, String key, String value) {
                return stringConverter.apply(value);
            }
        }, validator, false, false);
    }

    private OptionType(String name, Converter<T> converter, Consumer<T> validator, boolean isOptionMap, boolean isDefaultType) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(converter);
        Objects.requireNonNull(validator);
        this.name = name;
        this.converter = converter;
        this.validator = validator;
        this.isOptionMap = isOptionMap;
        this.isDefaultType = isDefaultType;
    }

    // Used only to create default types
    @SuppressWarnings("unchecked")
    private OptionType(String name, Function<String, T> stringConverter, boolean isDefaultType) {
        this(name, new Converter<T>() {
            @Override
            public T convert(T previousValue, String key, String value) {
                return stringConverter.apply(value);
            }
        }, (Consumer<T>) EMPTY_VALIDATOR, false, isDefaultType);
    }

    /**
     * Constructs a new option type with name and function that allows to convert a string to the
     * option type.
     *
     * @param name the name of the type.
     * @param stringConverter a function that converts a string value to the option value. Can throw
     *            {@link IllegalArgumentException} to indicate an invalid string.
     *
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public OptionType(String name, Function<String, T> stringConverter) {
        this(name, stringConverter, (Consumer<T>) EMPTY_VALIDATOR);
    }

    /**
     * @deprecated Use {@link #OptionType(String, Function, Consumer)}
     * @since 19.0
     */
    @Deprecated(since = "19.0")
    @SuppressWarnings("unused")
    public OptionType(String name, T defaultValue, Function<String, T> stringConverter, Consumer<T> validator) {
        this(name, stringConverter, validator);
    }

    /**
     * @deprecated Use {@link #OptionType(String, Function)}
     * @since 19.0
     */
    @Deprecated(since = "19.0")
    @SuppressWarnings("unused")
    public OptionType(String name, T defaultValue, Function<String, T> stringConverter) {
        this(name, stringConverter);
    }

    /**
     * @deprecated
     * @since 19.0
     */
    @Deprecated(since = "19.0")
    public T getDefaultValue() {
        return null;
    }

    /**
     * Returns the name of this type.
     *
     * @since 19.0
     */
    public String getName() {
        return name;
    }

    /**
     * Converts a string value, validates it, and converts it to an object of this type.
     *
     * @throws IllegalArgumentException if the value is invalid or cannot be converted.
     * @since 19.0
     */
    public T convert(String value) {
        T v = converter.convert(null, null, value);
        validate(v);
        return v;
    }

    /**
     * Converts a string value, validates it, and converts it to an object of this type. For option
     * maps includes the previous map stored for the option and the key.
     *
     * @param nameSuffix the key for prefix options.
     * @param previousValue the previous value holded by option.
     * @throws IllegalArgumentException if the value is invalid or cannot be converted.
     * @since 19.2
     */
    @SuppressWarnings("unchecked")
    public T convert(Object previousValue, String nameSuffix, String value) {
        T v = converter.convert((T) previousValue, nameSuffix, value);
        validate(v);
        return v;
    }

    /**
     * Validates an option value and throws an {@link IllegalArgumentException} if the value is
     * invalid.
     *
     * @throws IllegalArgumentException if the value is invalid or cannot be converted.
     * @since 19.0
     */
    public void validate(T value) {
        validator.accept(value);
    }

    /**
     * @since 19.0
     */
    @Override
    public String toString() {
        return "OptionType[name=" + name + "]";
    }

    private static final Map<Class<?>, OptionType<?>> DEFAULTTYPES = new HashMap<>();
    static {
        DEFAULTTYPES.put(Boolean.class, new OptionType<>("Boolean", new Function<String, Boolean>() {
            public Boolean apply(String t) {
                if ("true".equals(t)) {
                    return Boolean.TRUE;
                } else if ("false".equals(t)) {
                    return Boolean.FALSE;
                } else {
                    throw new IllegalArgumentException(String.format("Invalid boolean option value '%s'. The value of the option must be '%s' or '%s'.", t, "true", "false"));
                }
            }
        }, true));
        DEFAULTTYPES.put(Byte.class, new OptionType<>("Byte", new Function<String, Byte>() {
            public Byte apply(String t) {
                try {
                    return Byte.parseByte(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }, true));
        DEFAULTTYPES.put(Integer.class, new OptionType<>("Integer", new Function<String, Integer>() {
            public Integer apply(String t) {
                try {
                    return Integer.parseInt(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }, true));
        DEFAULTTYPES.put(Long.class, new OptionType<>("Long", new Function<String, Long>() {
            public Long apply(String t) {
                try {
                    return Long.parseLong(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }, true));
        DEFAULTTYPES.put(Float.class, new OptionType<>("Float", new Function<String, Float>() {
            public Float apply(String t) {
                try {
                    return Float.parseFloat(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }, true));
        DEFAULTTYPES.put(Double.class, new OptionType<>("Double", new Function<String, Double>() {
            public Double apply(String t) {
                try {
                    return Double.parseDouble(t);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }, true));
        DEFAULTTYPES.put(String.class, new OptionType<>("String", new Function<String, String>() {
            public String apply(String t) {
                return t;
            }
        }, true));
    }

    /**
     * Returns the default option type for a given value. Returns <code>null</code> if no default
     * option type is available for the Java type of this value.
     *
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public static <T> OptionType<T> defaultType(T value) {
        return defaultType((Class<T>) value.getClass());
    }

    /**
     * Returns the default option type for option maps for the given value class. Returns
     * <code>null</code> if no default option type is available for the value class.
     */
    @SuppressWarnings("unchecked")
    static <V> OptionType<OptionMap<V>> mapOf(Class<V> valueClass) {
        final OptionType<V> valueType = defaultType(valueClass);
        if (valueType == null) {
            return null;
        }
        return new OptionType<>("OptionMap", new Converter<OptionMap<V>>() {
            @Override
            public OptionMap<V> convert(OptionMap<V> previousValue, String key, String value) {
                OptionMap<V> map = previousValue;
                if (map == null || map.entrySet().isEmpty()) {
                    map = new OptionMap<>(new HashMap<>());
                }
                map.backingMap.put(key, valueType.convert(map.get(key), key, value));
                return map;
            }
        }, (Consumer<OptionMap<V>>) EMPTY_VALIDATOR, true, true);
    }

    /**
     * Returns the default option type for a class. Returns <code>null</code> if no default option
     * type is available for this Java type.
     *
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    public static <T> OptionType<T> defaultType(Class<T> clazz) {
        OptionType<T> type = (OptionType<T>) DEFAULTTYPES.get(clazz);
        if (type != null) {
            return type;
        }
        if (Enum.class.isAssignableFrom(clazz)) {
            return defaultEnumType(clazz);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> OptionType<T> defaultEnumType(Class<T> clazz) {
        return new OptionType<>(clazz.getSimpleName(), new Function<String, T>() {

            final Map<String, Enum<?>> validValues = new HashMap<>();

            {
                Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) clazz;
                for (Enum<?> constant : enumType.getEnumConstants()) {
                    validValues.put(constant.toString(), constant);
                }
            }

            @SuppressWarnings("rawtypes")
            public T apply(String t) {
                Class<? extends Enum> enumType = (Class<? extends Enum>) clazz;
                if (t != null) {
                    Enum value = validValues.get(t);
                    if (value != null) {
                        return (T) value;
                    }
                }
                // fallthrough to failed
                StringBuilder b = new StringBuilder();
                String sep = "";
                for (Enum constant : enumType.getEnumConstants()) {
                    b.append(sep);
                    b.append('\'');
                    b.append(constant.toString());
                    b.append('\'');
                    sep = ", ";
                }
                throw new IllegalArgumentException("Invalid option value '" + t + "'. Valid options values are: " + b.toString());
            }
        }, true);
    }

    boolean isOptionMap() {
        return isOptionMap;
    }

    boolean isDefaultType() {
        return isDefaultType;
    }

    @FunctionalInterface
    private interface Converter<T> {

        T convert(T previousValue, String key, String value);

    }
}
