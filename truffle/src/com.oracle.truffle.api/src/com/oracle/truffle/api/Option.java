/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
     * {@link OptionDescriptor#getHelp()()}.
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
     * Specifies the category of the option. The generated option descriptor returns this value as
     * result of {@link OptionDescriptor#getCategory()()}.
     *
     * @since 0.27
     */
    OptionCategory category();

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
         * multiple group anmes are specified then descriptors for each combination of group and
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
    @TruffleLanguage.Registration(id = "mylang",   name = "My Language",
                                  version = "1.0", mimeType = "mime")
    abstract static class MyLanguage extends TruffleLanguage<Context> {

        // the descriptor name for MyOption1 is 'mylang.MyOption1'
        @Option(help = "Help Text.", category = OptionCategory.USER)
        static final OptionKey<String>  MyOption1 = new OptionKey<>("");

        // the descriptor name for MyOption2 is 'mylang.MyOption2'
        @Option(help = "Help Text.", category = OptionCategory.USER)
        static final OptionKey<Boolean> MyOption2 = new OptionKey<>(false);

        @Override
        protected Context createContext(TruffleLanguage.Env env) {
            if (env.getOptions().get(MyOption2)) {
                // options are available via environment
            }
            return null;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new MyLanguageOptionDescriptors();
        }
    }
    // END: OptionSnippets.MyLanguage

    // this class is generated by an annotation processor
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
