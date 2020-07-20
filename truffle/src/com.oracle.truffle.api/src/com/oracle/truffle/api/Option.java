/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Iterator;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

/**
 * Describes the attributes of an option whose {@link OptionKey value} is in a static field
 * annotated by this annotation. If used in a class then a package-protected class following the
 * name pattern <code>$classNameOptionDescriptors</code> is generated. The generated class
 * implements {@link OptionDescriptors} and contains all options specified by the declaring class.
 * <p>
 * If the {@link Option} annotation is used in a subclass of {@link TruffleLanguage} or
 * {@link com.oracle.truffle.api.instrumentation.TruffleInstrument} then the option descriptor
 * prefix is automatically inherited from the {@link TruffleLanguage.Registration#id() language id}
 * or the {@link com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration#id()
 * instrument id}. If the class is not a language or an instrument then the {@link Option.Group}
 * annotation can be used on the class to specify a option group name prefix.
 * <p>
 * The {@link OptionDescriptor#getName() option descriptor name} is generated from the
 * {@link Group#value() group name} and the {@link Option#name() option name} separated by
 * {@code '.'}. If the option name is an empty {@link String} then the trailing {@code '.'} will be
 * removed from the descriptor name such that it exactly matches the group name. If, for example,
 * the option group is {@code 'js'} and the option name is inherited from the field name
 * {@code 'ECMACompatibility'} then the full descriptor name is {@code 'js.ECMACompatibility'}.
 * <p>
 * <b>Example usage:</b>
 *
 * {@link OptionSnippets.MyLanguage}.
 *
 * @see OptionDescriptor
 * @see Option.Group
 * @since 0.27
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Option {

    /**
     * Returns a custom name for the option. If not specified then the name of the annotated field
     * is used.
     * <p>
     * The {@link OptionDescriptor#getName() option descriptor name} is generated from the
     * {@link Group#value() group name} and the {@link Option#name() option name} separated by
     * {@code '.'}. If the option name is an empty {@link String} then the trailing {@code '.'} will
     * be removed from the descriptor name such that it exactly matches the group name. If, for
     * example, the option group is {@code 'js'} and the option name is inherited from the field
     * name {@code 'ECMACompatibility'} then the full descriptor name is
     * {@code 'js.ECMACompatibility'}.
     *
     * @since 0.27
     */
    String name() default "";

    /**
     * Returns a help message for the option. New lines can be embedded in the message with
     * {@code "%n"}. The generated an option descriptor returns this value as result of
     * {@link OptionDescriptor#getHelp()}.
     *
     * @since 0.27
     */
    String help();

    /**
     * Returns <code>true</code> if this option is deprecated. The generated option descriptor
     * returns this value as result of {@link OptionDescriptor#isDeprecated()}.
     *
     * @since 0.27
     */
    boolean deprecated() default false;

    /**
     * Returns the deprecation reason and the recommended fix. The generated option descriptor
     * returns this value as result of {@link OptionDescriptor#getDeprecationMessage()}.
     *
     * @since 20.1.0
     */
    String deprecationMessage() default "";

    /**
     * Specifies the category of the option. The generated option descriptor returns this value as
     * result of {@link OptionDescriptor#getCategory()}.
     *
     * @since 0.27
     */
    OptionCategory category();

    /**
     * Defines the stability of this option. The default value is
     * {@link OptionStability#EXPERIMENTAL}.
     *
     * @since 19.0
     */
    OptionStability stability() default OptionStability.EXPERIMENTAL;

    /**
     * Must be applied on classes containing {@link Option option} fields to specify a name prefix
     * if the prefix cannot be inferred by language or instrument.
     * <p>
     * The {@link OptionDescriptor#getName() option descriptor name} is generated from the
     * {@link Group#value() option name prefix} and the {@link Option#name() option name} separated
     * by {@code '.'}. If the option name is an empty {@link String} then the trailing {@code '.'}
     * will be removed from the descriptor name.
     *
     * @since 0.27
     */
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Group {

        /**
         * A set of group names that are used as prefix for all options of the annotated class. If
         * multiple group names are specified then descriptors for each combination of group and
         * option name is generated.
         * <p>
         * The {@link OptionDescriptor#getName() option descriptor name} is generated from the
         * {@link Group#value() group name} and the {@link Option#name() option name} separated by
         * {@code '.'}. If the option name is an empty {@link String} then the trailing {@code '.'}
         * will be removed from the descriptor name such that it exactly matches the group name. If,
         * for example, the option group is {@code 'js'} and the option name is inherited from the
         * field name {@code 'ECMACompatibility'} then the full descriptor name is
         * {@code 'js.ECMACompatibility'}.
         *
         * @since 0.27
         */
        String[] value();

    }

}

class OptionSnippets {

    // @formatter:off

    // BEGIN: OptionSnippets.MyLanguage
    @TruffleLanguage.Registration(id = "mylang", name = "My Language",
                                  version = "1.0")
    abstract static class MyLanguage extends TruffleLanguage<Context> {

        // the descriptor name for MyOption1 is 'mylang.MyOption1'
        @Option(help = "Help Text.", category = OptionCategory.USER,
                stability = OptionStability.STABLE)
        static final OptionKey<String> MyOption1 = new OptionKey<>("");

        // the descriptor name for SecondOption is 'mylang.secondOption'
        @Option(help = "Help Text.", name = "secondOption",
                category = OptionCategory.EXPERT,
                stability = OptionStability.EXPERIMENTAL)
        static final OptionKey<Boolean> SecondOption = new OptionKey<>(false);

        @Override
        protected Context createContext(TruffleLanguage.Env env) {
            if (env.getOptions().get(SecondOption)) {
                // options are available via environment
            }
            return null;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            // this class is generated by the annotation processor
            return new MyLanguageOptionDescriptors();
        }
    }
    // END: OptionSnippets.MyLanguage

    static class MyLanguageOptionDescriptors implements OptionDescriptors {

        public OptionDescriptor get(String optionName) {
            return null;
        }

        public Iterator<OptionDescriptor> iterator() {
            return null;
        }
    }

    static class Context {
    }
}
