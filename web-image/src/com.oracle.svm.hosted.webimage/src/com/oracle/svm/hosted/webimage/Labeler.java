/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;

import jdk.graal.compiler.debug.MetricKey;

/**
 * A utility class used for injecting label pairs and method labels inside JavaScript image. Besides
 * injecting labels, this class offers methods for calculating the size of the image between the
 * label pairs or the size of a method <i>after</i> Closure compiler is applied. <br>
 * Labels can be injected anywhere, except inside of a class declaration. Labels take the following
 * form:
 *
 * <pre>
 *     _ : "LABEL_NAME";
 * </pre>
 * <p>
 * Once the Closure compiler is applied, labels are reduced to a literal string:
 *
 * <pre>
 *     "LABEL_NAME";
 * </pre>
 *
 */
public class Labeler {

    private static final String METHOD_LABEL = "METHOD-";
    private static final String METHOD_LABEL_BODY = "(){}";
    private static final String START_SUFFIX = "-START";
    private static final String END_SUFFIX = "-END";

    /**
     * The method labels take a few more characters beyond the prefix and postfix.
     *
     * <pre>
     * [PREFIX](){}
     * ...
     * [POSTFIX](){}
     * </pre>
     */
    private static final int EXTRA_METHOD_LABEL_SIZE = METHOD_LABEL_BODY.length() + 2;

    /**
     * We use special characters in the prefix and postfix part of the label so that the labeler
     * wouldn't be accidentally (or deliberately) tricked by a user injected string, that looks like
     * a label.
     */
    private static final String LABEL_PREFIX = "\"!p!r!e!f!i!x";
    private static final String LABEL_POSTFIX = "!p!o!s!t!f!i!x\"";

    protected final EconomicMap<Integer, HostedMethod> methodById;

    public Labeler() {
        this.methodById = EconomicMap.create();
    }

    /**
     * Returns an {@link Injection} that injects labeling pairs between a chunk of code. The
     * returned object should be used with a try/catch block.
     *
     * Example:
     *
     * <pre>
     * try(Labeler.Injection injector = labeler.injectMetricLabel(codeBuffer, METRIC_KEY) {
     *     // some code generation of interest.
     * }
     * </pre>
     */
    public Injection injectMetricLabel(CodeBuffer codeBuffer, MetricKey key) {
        return new Injection(codeBuffer, key.getName(), this::injectLabel);
    }

    /**
     * Returns an {@link Injection} that injects labeling pairs for a method body.
     *
     * Labels cannot be injected inside class declaration. Therefore we cannot inject label pair
     * before/after method definition. For that reason, we inject label pairs <i>around the method
     * body<i/>.
     *
     * The label injected is in the form of <b>"METHOD-<i>number</i>"</b> where <i>number</i>
     * uniquely identifies the method for which the label was injected. We use the number as the
     * method identifier, because once the Closure compiles, the original names of the methods are
     * lost. To retrieve the original method we use the given identifier.
     *
     * Example of a class declaration and its method definition with the injected label:
     *
     * <pre>
     *     class Example {
     *         constructor() {
     *             ...
     *         }
     *         "METHOD-0-START"(){}
     *         exampleMethod() {
     *             ...
     *         }
     *         "METHOD-0-END"(){}
     *     }
     * </pre>
     *
     * See also the documentation for {@link Labeler#injectMetricLabel}.
     *
     * @param codeBuffer target of the label injection
     * @param method method for which the label is injected
     * @return the injection object
     */
    public Injection injectMethodLabel(CodeBuffer codeBuffer, HostedMethod method) {
        int methodId = methodById.size();
        methodById.put(methodId, method);
        return new Injection(codeBuffer, METHOD_LABEL + methodId, this::injectFunctionLabel);
    }

    public interface LabelSizeHandler {
        /**
         * Handles the found label start/end pair.
         *
         * The size already excludes all nested label size.
         *
         * The key could be the string name of a metric key or a HostedMethod.
         */
        void handle(Object key, int size);
    }

    /**
     * Represents a start label in the source code.
     */
    private static class LabelItem {
        final String name;
        @SuppressWarnings("unused") final int startIndex;
        final int endIndex;
        int nestedLabelSize;

        LabelItem(String name, int startIndex, int endIndex) {
            this.name = name;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    /**
     * Returns total size of labels in the image.
     *
     * @param jsSource image
     * @param handler Handles the found label start/end pair. The size already excludes all nested
     *            label size.
     */
    public int getSizeBetweenMetricLabels(String jsSource, LabelSizeHandler handler) {
        Pattern pattern = Pattern.compile(LABEL_PREFIX + "(\\S+?)" + ("(" + START_SUFFIX + "|" + END_SUFFIX + ")") + LABEL_POSTFIX);
        Matcher labelMatcher = pattern.matcher(jsSource);

        ArrayList<LabelItem> labelStack = new ArrayList<>(8);

        // Used to record total label sizes
        final String total = "TOTAL";
        labelStack.add(new LabelItem(total, -1, -1));

        Consumer<Integer> addNestedLabelSize = delta -> {
            LabelItem item = labelStack.get(labelStack.size() - 1);
            item.nestedLabelSize += delta;
        };

        boolean found = labelMatcher.find();
        while (found) {
            String label = labelMatcher.group(1);
            int startIndex = labelMatcher.start();
            int endIndex = labelMatcher.end();
            boolean isEndLabel = labelMatcher.group(2).equals(END_SUFFIX);
            int labelSize = labelMatcher.group().length();

            var exception = new IllegalArgumentException("Unmatched metric label found in compiled source: " + label);

            if (isEndLabel) {
                if (labelStack.size() < 1) {
                    throw exception;
                }

                LabelItem item = labelStack.remove(labelStack.size() - 1);
                // Push the nested label size to the enclosing label if it exists.
                addNestedLabelSize.accept(item.nestedLabelSize + labelSize);

                if (item.name.equals(label)) {
                    if (label.startsWith(METHOD_LABEL)) {
                        addNestedLabelSize.accept(EXTRA_METHOD_LABEL_SIZE);
                        int methodId = Integer.parseInt(label.substring(METHOD_LABEL.length()));
                        int size = startIndex - item.endIndex - EXTRA_METHOD_LABEL_SIZE;
                        handler.handle(methodById.get(methodId), size);
                    } else {
                        int size = startIndex - item.endIndex;
                        handler.handle(label, size - item.nestedLabelSize);
                    }
                } else {
                    throw exception;
                }
            } else {
                // record label size to the enclosing label if it exists.
                addNestedLabelSize.accept(labelSize);
                if (label.startsWith(METHOD_LABEL)) {
                    addNestedLabelSize.accept(EXTRA_METHOD_LABEL_SIZE);
                }
                labelStack.add(new LabelItem(label, startIndex, endIndex));
            }

            found = labelMatcher.find();
        }

        assert labelStack.size() == 1 && labelStack.get(0).name.equals(total) : "Dangling metric label found in compiled source.";

        return labelStack.get(0).nestedLabelSize;
    }

    protected void injectLabel(CodeBuffer codeBuffer, String label) {
        codeBuffer.emitText("_:");
        codeBuffer.emitText(LABEL_PREFIX);
        codeBuffer.emitText(label);
        codeBuffer.emitText(LABEL_POSTFIX);
        codeBuffer.emitText(";");
        codeBuffer.emitNewLine();
    }

    /**
     * Inject label as a dummy member function.
     *
     * Example of a class declaration and its method definition with the injected label:
     *
     * <pre>
     *     class Example {
     *         constructor() {
     *             ...
     *         }
     *         "METHOD-0-START"(){}
     *         exampleMethod() {
     *             ...
     *         }
     *         "METHOD-0-END"(){}
     *     }
     * </pre>
     */
    protected void injectFunctionLabel(CodeBuffer codeBuffer, String functionName) {
        codeBuffer.emitText(LABEL_PREFIX);
        codeBuffer.emitText(functionName);
        codeBuffer.emitText(LABEL_POSTFIX);
        codeBuffer.emitText(METHOD_LABEL_BODY);
        codeBuffer.emitNewLine();
    }

    /**
     * A utility class used to inject labeling paris for the metric key used for labeling. Upon
     * creation it injects starting label and upon closing injects ending label. Should be used
     * inside of try/catch block.
     */
    public static final class Injection implements AutoCloseable {
        private final String label;
        private final CodeBuffer codeBuffer;
        private final BiConsumer<CodeBuffer, String> injectAction;

        private Injection(CodeBuffer codeBuffer, String label, BiConsumer<CodeBuffer, String> injectAction) {
            this.label = label;
            this.codeBuffer = codeBuffer;
            this.injectAction = injectAction;
            injectAction.accept(codeBuffer, label + START_SUFFIX);
        }

        @Override
        public void close() {
            injectAction.accept(codeBuffer, label + END_SUFFIX);
        }
    }

    /**
     * A No-op labeler. Replaces the need of conditional labeling of the image, if labeling is
     * turned off through some compiler option.
     */
    public static final class NoOpLabeler extends Labeler {
        public NoOpLabeler() {
        }

        @Override
        protected void injectLabel(CodeBuffer codeBuffer, String label) {
            // Just ignore any request for labeling.
        }

        @Override
        protected void injectFunctionLabel(CodeBuffer codeBuffer, String name) {
            // Just ignore any request for labeling.
        }

        /**
         * A no-op function, will always return a pair of 0s.
         */
        @Override
        public int getSizeBetweenMetricLabels(String jsSource, LabelSizeHandler handler) {
            return 0;
        }
    }
}
