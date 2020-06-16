/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

/**
 * Represents the option key for an option specification.
 *
 * @since 19.0
 */
public final class OptionKey<T> {

    private final OptionType<T> type;
    private final T defaultValue;

    /**
     * Constructs a new option key given a default value. Throws {@link IllegalArgumentException} if
     * no default {@link OptionType} could be {@link OptionType#defaultType(Object) resolved} for
     * the given type. The default value must not be <code>null</code>.
     *
     * @since 19.0
     */
    public OptionKey(T defaultValue) {
        Objects.requireNonNull(defaultValue);
        this.defaultValue = defaultValue;
        this.type = OptionType.defaultType(defaultValue);
        if (type == null) {
            throw new IllegalArgumentException("No default type specified for type " + defaultValue.getClass().getName() + ". Specify the option type explicitly to resolve this.");
        }
    }

    /**
     * Constructs a new option key given a default value and option key.
     *
     * @since 19.0
     */
    public OptionKey(T defaultValue, OptionType<T> type) {
        Objects.requireNonNull(type);
        this.defaultValue = defaultValue;
        this.type = type;
    }

    /**
     * Constructs a new option key to group/accumulate options with common prefixes. This type of
     * options allow to collect key=value pairs whose keys are unknown beforehand e.g. user defined
     * properties. See {@link OptionMap}.
     *
     * Example usage:
     * 
     * <pre>
     * &#64;Option.Group("mylang")
     * public class MiscOptions {
     *     &#64;Option(help = &quot;User-defined properties&quot;, category = OptionCategory.USER) //
     *     public static final OptionKey&lt;OptionMap&lt;String&gt;&gt; Properties = OptionKey.mapOf(String.class);
     *
     *     ...
     * }
     * </pre>
     *
     * Properties can be set using the {@code mylang.Properties} prefix.
     * 
     * <pre>
     * Context context = Context.newBuilder() //
     *                 .option(&quot;mylang.Properties.key&quot;, &quot;value&quot;) //
     *                 .option(&quot;mylang.Properties.user.name&quot;, &quot;guest&quot;) //
     *                 .build();
     * </pre>
     *
     * The option map can be consumed as follows:
     * 
     * <pre>
     * OptionMap&lt;String&gt; properties = getOptions().get(MiscOptions.Properties);
     * properties.get(&quot;key&quot;);       // value
     * properties.get(&quot;user.name&quot;); // guest
     * properties.get(&quot;undefined&quot;); // null
     * </pre>
     *
     * Throws {@link IllegalArgumentException} if no default {@link OptionType} could be
     * {@link OptionType#defaultType(Object) resolved} for the value type.
     * 
     * @since 19.2
     */
    public static <V> OptionKey<OptionMap<V>> mapOf(Class<V> valueClass) {
        OptionType<OptionMap<V>> type = OptionType.mapOf(valueClass);
        if (type == null) {
            throw new IllegalArgumentException("No default type specified for type " + valueClass.getName());
        }
        return new OptionKey<>(OptionMap.empty(), type);
    }

    /**
     * Returns the option type of this key.
     *
     * @since 19.0
     */
    public OptionType<T> getType() {
        return type;
    }

    /**
     * Returns the default value for this option.
     *
     * @since 19.0
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Returns the value of this key given the {@link OptionValues values}.
     *
     * @since 19.0
     */
    public T getValue(OptionValues values) {
        return values.get(this);
    }

    /**
     * Returns <code>true</code> if a value for this key has been set for the given option values or
     * <code>false</code> if no value has been set.
     *
     * @since 19.0
     */
    public boolean hasBeenSet(OptionValues values) {
        return values.hasBeenSet(this);
    }

}
