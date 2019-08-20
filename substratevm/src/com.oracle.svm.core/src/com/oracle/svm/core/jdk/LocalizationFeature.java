/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticFeature
public final class LocalizationFeature implements Feature {

    public static class Options {
        @Option(help = "Make all hosted charsets available at run time")//
        public static final HostedOptionKey<Boolean> AddAllCharsets = new HostedOptionKey<>(false);
    }

    /**
     * Many subclasses of {@link Charset} initialize encoding and decoding tables lazily. They all
     * follow the same pattern: the methods "initc2b" and/or "initb2c" perform the initialization,
     * and then set a field "c2bInitialized" or "b2cInitialized" to true. We run the initialization
     * eagerly by creating an encoder and decoder during image generation in
     * {@link LocalizationFeature#addCharset}. So we know that the "init*" methods do nothing, and
     * we replace calls to them with nothing, i.e,, remove calls to them.
     *
     * We could do all this with individual {@link Substitute method substitutions}, but it would
     * require a lot of substitution methods that all look the same.
     */
    public static final class CharsetNodePlugin implements NodePlugin {

        @Override
        public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            if ((method.getName().equals("initc2b") || method.getName().equals("initb2c")) &&
                            b.getMetaAccess().lookupJavaType(Charset.class).isAssignableFrom(method.getDeclaringClass())) {

                /*
                 * Verify that the "*Initialized" field corresponding with the method was set to
                 * true, i.e., that initialization was done eagerly.
                 */
                ResolvedJavaType charsetType = method.getDeclaringClass();
                ResolvedJavaField initializedField = findStaticField(charsetType, method.getName().substring(4, 7) + "Initialized");
                if (!b.getConstantReflection().readFieldValue(initializedField, null).asBoolean()) {
                    String charsetName = charsetType.getUnqualifiedName();
                    try {
                        Charset charset = Charset.forName(charsetName);
                        addCharset(charset);
                    } catch (UnsupportedCharsetException e) {
                        throw VMError.shouldNotReachHere("Could not find non-initialized charset " + charsetType.getSourceFileName(), e);
                    }
                }

                /* We "handled" the method invocation by doing nothing. */
                return true;
            }
            return false;
        }

        private static ResolvedJavaField findStaticField(ResolvedJavaType declaringClass, String name) {
            for (ResolvedJavaField field : declaringClass.getStaticFields()) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
            throw VMError.shouldNotReachHere();
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess arg0) {
        ImageSingletons.add(LocalizationSupport.class, new LocalizationSupport());

        /*
         * The JDK performs dynamic lookup of charsets by name, which leads to dynamic class
         * loading. We cannot do that, because we need to know all classes ahead of time to perform
         * our static analysis. Therefore, we load and register all standard charsets here. Features
         * that require more than this can add additional charsets.
         */
        if (Options.AddAllCharsets.getValue()) {
            for (Charset c : Charset.availableCharsets().values()) {
                addCharset(c);
            }
        } else {
            addCharset(Charset.defaultCharset());
            addCharset(Charset.forName("US-ASCII"));
            addCharset(Charset.forName("ISO-8859-1"));
            addCharset(Charset.forName("UTF-8"));
            addCharset(Charset.forName("UTF-16BE"));
            addCharset(Charset.forName("UTF-16LE"));
            addCharset(Charset.forName("UTF-16"));
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        LocalizationSupport support = ImageSingletons.lookup(LocalizationSupport.class);
        access.registerAsImmutable(support, LocalizationFeature::isImmutable);
    }

    private static boolean isImmutable(Object object) {
        if (object instanceof sun.util.locale.BaseLocale || object instanceof java.util.Locale) {
            /* These classes have a mutable hash code field. */
            return false;
        }
        if (object instanceof java.util.Map) {
            /* The maps have lazily initialized cache fields (see JavaUtilSubstitutions). */
            return false;
        }
        return true;
    }

    public static void addCharset(Charset charset) {
        Map<String, Charset> charsets = ImageSingletons.lookup(LocalizationSupport.class).charsets;
        charsets.put(charset.name().toLowerCase(), charset);
        for (String name : charset.aliases()) {
            charsets.put(name.toLowerCase(), charset);
        }

        /* Eagerly initialize all the tables necessary for decoding / encoding. */
        charset.newDecoder();
        if (charset.canEncode()) {
            charset.newEncoder();
        }
    }
}
