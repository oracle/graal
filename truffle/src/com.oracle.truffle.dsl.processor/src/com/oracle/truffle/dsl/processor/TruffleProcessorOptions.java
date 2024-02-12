/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.NodeData;

/**
 * Aggregates all options recognized by {@link TruffleProcessor}.
 *
 * Pass using javac:
 *
 * <pre>
 * -Atruffle.dsl.SuppressAllWarnings=true
 * </pre>
 *
 * Pass to mx build:
 *
 * <pre>
 * mx build -A-Atruffle.dsl.SuppressAllWarnings=true
 * </pre>
 */
public class TruffleProcessorOptions {
    private static final String OptionsPrefix = "truffle.dsl.";
    private static final String GenerateSpecializationStatisticsOptionName = "GenerateSpecializationStatistics";
    private static final String GenerateSlowPathOnlyOptionName = "GenerateSlowPathOnly";
    private static final String GenerateSlowPathOnlyFilterOptionName = "GenerateSlowPathOnlyFilter";
    private static final String SuppressAllWarnings = "SuppressAllWarnings";
    private static final String SuppressWarnings = "SuppressWarnings";
    private static final String CacheSharingWarningsEnabledOptionName = "cacheSharingWarningsEnabled";
    private static final String StateBitWidth = "StateBitWidth";
    private static final String PrintTimings = "PrintTimings";

    private static String getOption(ProcessingEnvironment env, String key) {
        String value = env.getOptions().get(key);
        if (value != null) {
            return value;
        }
        return System.getProperty(key);
    }

    public static Boolean generateSpecializationStatistics(ProcessingEnvironment env) {
        String value = getOption(env, OptionsPrefix + GenerateSpecializationStatisticsOptionName);
        return value == null ? null : Boolean.parseBoolean(value);
    }

    public static boolean generateSlowPathOnly(ProcessingEnvironment env) {
        return Boolean.parseBoolean(getOption(env, OptionsPrefix + GenerateSlowPathOnlyOptionName));
    }

    public static boolean printTimings(ProcessingEnvironment env) {
        return Boolean.parseBoolean(getOption(env, OptionsPrefix + PrintTimings));
    }

    public static String generateSlowPathOnlyFilter(ProcessingEnvironment env) {
        return getOption(env, OptionsPrefix + GenerateSlowPathOnlyFilterOptionName);
    }

    public static boolean suppressAllWarnings(ProcessingEnvironment env) {
        String v = getOption(env, OptionsPrefix + SuppressAllWarnings);
        boolean suppress = Boolean.parseBoolean(v);
        if (suppress) {
            return suppress;
        }
        String[] warnings = suppressDSLWarnings(env);
        if (warnings != null) {
            Set<String> warningsSet = Set.of(warnings);
            if (warningsSet.contains(TruffleSuppressedWarnings.ALL) || warningsSet.contains(TruffleSuppressedWarnings.TRUFFLE)) {
                return true;
            }
        }
        return false;

    }

    public static String[] suppressDSLWarnings(ProcessingEnvironment env) {
        String v = getOption(env, OptionsPrefix + SuppressWarnings);
        if (v != null) {
            return v.split(",");
        }
        return null;
    }

    public static boolean cacheSharingWarningsEnabled(ProcessingEnvironment env) {
        String s = getOption(env, OptionsPrefix + CacheSharingWarningsEnabledOptionName);
        if (s == null) {
            return !TruffleProcessorOptions.generateSlowPathOnly(env);
        }
        return Boolean.parseBoolean(s);
    }

    public static int stateBitWidth(NodeData node) {
        ProcessorContext context = ProcessorContext.getInstance();
        DeclaredType disableStateWidth = context.getTypes().DisableStateBitWidthModification;
        if (disableStateWidth != null) {
            Element element = node.getTemplateType();
            while (element != null) {
                if (ElementUtils.findAnnotationMirror(element, disableStateWidth) != null) {
                    return FlatNodeGenFactory.DEFAULT_MAX_BIT_WIDTH;
                }
                element = element.getEnclosingElement();
            }
        }

        String value = getOption(context.getEnvironment(), OptionsPrefix + StateBitWidth);
        if (value == null) {
            return FlatNodeGenFactory.DEFAULT_MAX_BIT_WIDTH;
        } else {
            return Integer.parseInt(value);
        }
    }

    public static Set<String> getSupportedOptions() {
        HashSet<String> result = new HashSet<>();
        result.add(OptionsPrefix + GenerateSpecializationStatisticsOptionName);
        result.add(OptionsPrefix + GenerateSlowPathOnlyOptionName);
        result.add(OptionsPrefix + GenerateSlowPathOnlyFilterOptionName);
        result.add(OptionsPrefix + CacheSharingWarningsEnabledOptionName);
        result.add(OptionsPrefix + StateBitWidth);
        result.add(OptionsPrefix + SuppressAllWarnings);
        result.add(OptionsPrefix + SuppressWarnings);
        result.add(OptionsPrefix + PrintTimings);
        return result;
    }
}
