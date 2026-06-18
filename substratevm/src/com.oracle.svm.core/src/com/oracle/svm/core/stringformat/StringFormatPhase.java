/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.stringformat;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * See {@link StringFormat} for a description of the overall approach. This class performs the
 * intrinsification at image build time, i.e., it rewrites calls to {@link String#format} with
 * simple format strings to calls of {@link StringFormat#format}.
 * 
 * To simplify the intrinsification, this phase assumes that Escape Analysis has already been
 * executed beforehand. This simplifies the handling of the "arguments" parameter, which is a
 * Object[] array usually filled via Java varargs syntax. Escape analysis combines the array
 * allocation and the various array stores to a single {@link AllocatedObjectNode} that is
 * {@link CommitAllocationNode committed} just before the {@link InvokeWithExceptionNode} that
 * invokes {@link String#format}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class StringFormatPhase extends BasePhase<Providers> {

    public static final class Options {
        @Option(help = "Intrinsify String.format with constant format strings.")//
        public static final HostedOptionKey<Boolean> IntrinsifyStringFormat = new HostedOptionKey<>(true);
    }

    /* Source methods that are intrinsified. */
    private static final Method FORMAT_METHOD = ReflectionUtil.lookupMethod(String.class, "format", String.class, Object[].class);
    private static final Method FORMAT_LOCALE_METHOD = ReflectionUtil.lookupMethod(String.class, "format", Locale.class, String.class, Object[].class);
    private static final Method FORMATTED_METHOD = ReflectionUtil.lookupMethod(String.class, "formatted", Object[].class);
    /* Target methods that perform the intrinsified string formatting. */
    private static final Method INTRINSIC_METHOD = ReflectionUtil.lookupMethod(StringFormat.class, "format", String.class, Object[].class);
    private static final Method INTRINSIC_LOCALE_METHOD = ReflectionUtil.lookupMethod(StringFormat.class, "format", Locale.class, String.class, Object[].class);
    private static final Method INTRINSIC_FORMATTER_FALLBACK_METHOD = ReflectionUtil.lookupMethod(StringFormat.class, "formatWithFormatterFallback", String.class, Object[].class);
    private static final Method INTRINSIC_FORMATTER_FALLBACK_LOCALE_METHOD = ReflectionUtil.lookupMethod(StringFormat.class, "formatWithFormatterFallback", Locale.class, String.class, Object[].class);

    /**
     * Matches a simplified version of the Java format specifiers.
     * %[argument_index$][flags][width][.precision][tT]conversion
     */
    private static final Pattern FORMAT_STRING_PATTERN = Pattern.compile("%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])");
    /**
     * Limit to ensure that format strings that often repeat the same format argument do not lead to
     * an excessive array length of the transformed format specifiers.
     */
    private static final int MAX_FORMAT_SPECIFIERS = 100;

    private final boolean allowFormatterFallback;

    public StringFormatPhase() {
        this(true);
    }

    public StringFormatPhase(boolean allowFormatterFallback) {
        this.allowFormatterFallback = allowFormatterFallback;
    }

    @Override
    protected void run(StructuredGraph graph, Providers providers) {
        ResolvedJavaMethod formatMethod = providers.getMetaAccess().lookupJavaMethod(FORMAT_METHOD);
        ResolvedJavaMethod formatLocaleMethod = providers.getMetaAccess().lookupJavaMethod(FORMAT_LOCALE_METHOD);
        ResolvedJavaMethod formattedMethod = providers.getMetaAccess().lookupJavaMethod(FORMATTED_METHOD);

        for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
            NodeInputList<ValueNode> arguments = callTarget.arguments();
            if (formatMethod.equals(callTarget.targetMethod())) {
                assert arguments.size() == 2;
                processFormat(graph, callTarget, null, arguments.get(0), arguments.get(1), providers, allowFormatterFallback);
            } else if (formatLocaleMethod.equals(callTarget.targetMethod())) {
                assert arguments.size() == 3;
                processFormat(graph, callTarget, arguments.get(0), arguments.get(1), arguments.get(2), providers, allowFormatterFallback);
            } else if (formattedMethod.equals(callTarget.targetMethod())) {
                assert arguments.size() == 2;
                processFormat(graph, callTarget, null, arguments.get(0), arguments.get(1), providers, allowFormatterFallback);
            }
        }
    }

    private static void processFormat(StructuredGraph graph, MethodCallTargetNode callTarget, ValueNode locale, ValueNode formatNode, ValueNode argsNode, Providers providers,
                    boolean allowFormatterFallback) {
        /* We require that the format string is a String constant. */
        if (!formatNode.isJavaConstant()) {
            graph.getDebug().log("Format string is not a constant: %s", formatNode);
            return;
        }
        Object formatObject = providers.getSnippetReflection().asObject(Object.class, formatNode.asJavaConstant());
        if (!(formatObject instanceof String)) {
            graph.getDebug().log("Format string is not a constant string: %s", formatObject);
            return;
        }
        String formatString = (String) formatObject;

        /*
         * We require that Escape Analysis virtualized the argument array allocation and
         * materialized it immediately before the invoke.
         */
        if (!(argsNode instanceof AllocatedObjectNode)) {
            graph.getDebug().log("Format arguments are not an escape-analyzed array: %s", argsNode);
            return;
        }
        if (callTarget.invoke().predecessor() != ((AllocatedObjectNode) argsNode).getCommit()) {
            graph.getDebug().log("Format arguments are not materialized immediately before the invoke: %s", argsNode);
            return;
        }
        List<ValueNode> argumentNodes = extractArrayElements((AllocatedObjectNode) argsNode);

        /*
         * We require that the format string is well formed and contains only a few supported format
         * specifiers.
         */
        Deque<StringFormatSpecifier> formatSpecifiers = parseFormatString(formatString, argumentNodes, allowFormatterFallback, isDecimalIntegerIntrinsicSupported(locale, providers));
        if (formatSpecifiers == null) {
            graph.getDebug().log("Format string is too complicated for intrinsification: %s", formatString);
            return;
        }

        /* Success, we have a valid format string that we can intrinsify. */
        rewriteInvoke(graph, callTarget, locale, formatSpecifiers, providers);
    }

    private static boolean isDecimalIntegerIntrinsicSupported(ValueNode locale, CoreProviders providers) {
        if (locale == null) {
            return true;
        }

        /*
         * Decimal integer formatting is locale sensitive. The direct runtime implementation knows
         * zero digits for constant locales collected in the image heap and a few explicit numbering
         * system extensions. A runtime-created non-constant locale can have a non-ASCII default
         * zero digit even without such an extension, for example Locale.forLanguageTag("fa"). Use
         * Formatter fallback for non-constant explicit-locale %d to preserve locale semantics
         * conservatively without pulling Formatter into no-locale String.format users such as Hello
         * World.
         */
        if (!locale.isJavaConstant()) {
            return false;
        }
        Object localeObject = providers.getSnippetReflection().asObject(Object.class, locale.asJavaConstant());
        return localeObject == null || localeObject instanceof Locale;
    }

    /**
     * The format string parsing is adapted from the JDK code in {@link Formatter}. Unfortunately,
     * all relevant JDK methods and classes are non-public, so we cannot re-use the parsing code
     * from the JDK.
     */
    private static Deque<StringFormatSpecifier> parseFormatString(String formatString, List<ValueNode> argumentNodes, boolean allowFormatterFallback, boolean decimalIntegerIntrinsicSupported) {
        Deque<StringFormatSpecifier> formatSpecifiers = new ArrayDeque<>();
        int index = 0;
        int max = formatString.length();
        int currentArgumentIndex = -1;
        int nextOrdinaryIndex = 0;
        Matcher matcher = FORMAT_STRING_PATTERN.matcher(formatString);
        while (index < max) {
            int n = formatString.indexOf('%', index);
            if (n < 0) {
                /* No more format specifiers, but since i < max there is some trailing text. */
                appendLiteral(formatSpecifiers, formatString.substring(index, max));
                break;
            }

            if (formatSpecifiers.size() > MAX_FORMAT_SPECIFIERS) {
                return null;
            }
            if (index != n) {
                /* Previous characters were fixed text. */
                appendLiteral(formatSpecifiers, formatString.substring(index, n));
            }

            /* Handle modifiers that are actually fixed strings. */
            String fixedString = matchFixedString(formatString, n);
            if (fixedString != null) {
                appendLiteral(formatSpecifiers, fixedString);
                index = n + 2;
                continue;
            }

            /*
             * We have already parsed a '%' at n, so we either have a match or the specifier at n is
             * invalid.
             */
            if (!matcher.find(n) || matcher.start() != n) {
                return null;
            }

            boolean hasNumberedArgumentIndex = false;
            boolean hasArgumentIndex = false;
            if (matcher.start(1) >= 0) {
                hasNumberedArgumentIndex = true;
                hasArgumentIndex = true;
                try {
                    /*
                     * Skip the trailing `$` when parsing the number. Note that we cannot match
                     * negative numbers. But we can match 0, which is reported as an illegal index
                     * when looking up the argument.
                     */
                    int newArgumentIndex = Integer.parseInt(formatString.substring(matcher.start(1), matcher.end(1) - 1));
                    /*
                     * The format string uses 1-based argument numbers, while our internal array
                     * index is 0-based.
                     */
                    currentArgumentIndex = newArgumentIndex - 1;
                } catch (NumberFormatException x) {
                    return null;
                }
            }

            boolean alternate = false;
            boolean zeroPad = false;
            boolean reusePreviousArgument = false;
            boolean intrinsicFlagsSupported = true;
            String flagsString = matcher.start(2) >= 0 ? formatString.substring(matcher.start(2), matcher.end(2)) : "";
            for (int i = 0; i < flagsString.length(); i++) {
                char flag = flagsString.charAt(i);
                if (flag == '#' && !alternate) {
                    alternate = true;
                } else if (flag == '0' && !zeroPad) {
                    zeroPad = true;
                } else if (flag == '<' && !reusePreviousArgument) {
                    reusePreviousArgument = true;
                } else {
                    intrinsicFlagsSupported = false;
                }
            }
            if (reusePreviousArgument) {
                if (hasNumberedArgumentIndex) {
                    return null;
                }
                /* Re-use the currentArgumentIndex from the last conversion. */
                hasArgumentIndex = true;
            }

            boolean hasWidth = false;
            int width = -1;
            String widthString = null;
            if (matcher.start(3) >= 0) {
                widthString = formatString.substring(matcher.start(3), matcher.end(3));
                try {
                    width = Integer.parseInt(widthString);
                    hasWidth = true;
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            boolean hasPrecision = false;
            int precision = -1;
            String precisionString = null;
            if (matcher.start(4) >= 0) {
                hasPrecision = true;
                precisionString = formatString.substring(matcher.start(4) + 1, matcher.end(4));
                try {
                    precision = Integer.parseInt(precisionString);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            boolean dateTime = matcher.start(5) >= 0;
            String conversionString = formatString.substring(matcher.start(6), matcher.end(6));
            assert conversionString.length() == 1;
            char conversion = conversionString.charAt(0);
            if (!consumesArgument(conversion, dateTime)) {
                return null;
            }

            if (!hasArgumentIndex) {
                currentArgumentIndex = nextOrdinaryIndex;
                nextOrdinaryIndex++;
            }
            if (currentArgumentIndex < 0 || currentArgumentIndex >= argumentNodes.size()) {
                return null;
            }

            boolean intrinsicSupported = isIntrinsicSupported(conversion, dateTime, hasPrecision, precisionString, alternate, zeroPad, hasWidth, widthString, intrinsicFlagsSupported,
                            decimalIntegerIntrinsicSupported);
            if (intrinsicSupported) {
                formatSpecifiers.addLast(new StringFormatSpecifier(conversion, argumentNodes.get(currentArgumentIndex), alternate, width, zeroPad ? '0' : ' ', precision));
            } else if (allowFormatterFallback) {
                formatSpecifiers.addLast(new StringFormatSpecifier(toSingleArgumentFormatSpecifier(formatString, matcher, flagsString), argumentNodes.get(currentArgumentIndex)));
            } else {
                return null;
            }
            index = matcher.end();
        }
        return formatSpecifiers;
    }

    private static boolean consumesArgument(char conversion, boolean dateTime) {
        if (dateTime) {
            return "HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc".indexOf(conversion) >= 0;
        }
        return "bBhHsScCdoxXeEfgGaA".indexOf(conversion) >= 0;
    }

    private static boolean isIntrinsicSupported(char conversion, boolean dateTime, boolean hasPrecision, String precisionString, boolean alternate, boolean zeroPad, boolean hasWidth,
                    String widthString, boolean intrinsicFlagsSupported, boolean decimalIntegerIntrinsicSupported) {
        if (dateTime || !intrinsicFlagsSupported || StringFormat.SUPPORTED_CONVERSIONS.indexOf(conversion) < 0) {
            return false;
        }
        if (conversion == StringFormat.DECIMAL_INTEGER && !decimalIntegerIntrinsicSupported) {
            return false;
        }
        if (alternate && conversion != StringFormat.OCTAL_INTEGER && conversion != StringFormat.HEXADECIMAL_INTEGER && conversion != StringFormat.HEXADECIMAL_INTEGER_UPPER) {
            return false;
        }
        if (hasPrecision) {
            if (conversion != StringFormat.BOOLEAN && conversion != StringFormat.BOOLEAN_UPPER && conversion != StringFormat.HASHCODE && conversion != StringFormat.HASHCODE_UPPER &&
                            conversion != StringFormat.STRING && conversion != StringFormat.STRING_UPPER) {
                return false;
            }
            if (precisionString.length() != 1) {
                return false;
            }
        }
        if (hasWidth) {
            if (widthString.length() != 1 || widthString.charAt(0) == '0') {
                return false;
            }
            if (conversion != StringFormat.DECIMAL_INTEGER && conversion != StringFormat.OCTAL_INTEGER && conversion != StringFormat.HEXADECIMAL_INTEGER &&
                            conversion != StringFormat.HEXADECIMAL_INTEGER_UPPER && conversion != StringFormat.BOOLEAN && conversion != StringFormat.BOOLEAN_UPPER &&
                            conversion != StringFormat.HASHCODE && conversion != StringFormat.HASHCODE_UPPER && conversion != StringFormat.STRING && conversion != StringFormat.STRING_UPPER &&
                            conversion != StringFormat.CHARACTER && conversion != StringFormat.CHARACTER_UPPER) {
                return false;
            }
            if (zeroPad && (conversion != StringFormat.DECIMAL_INTEGER && conversion != StringFormat.OCTAL_INTEGER && conversion != StringFormat.HEXADECIMAL_INTEGER &&
                            conversion != StringFormat.HEXADECIMAL_INTEGER_UPPER)) {
                return false;
            }
            if (hasWidth && alternate) {
                return false;
            }
        } else if (zeroPad) {
            return false;
        }
        return true;
    }

    private static String toSingleArgumentFormatSpecifier(String formatString, Matcher matcher, String flagsString) {
        StringBuilder result = new StringBuilder("%");
        for (int i = 0; i < flagsString.length(); i++) {
            char flag = flagsString.charAt(i);
            if (flag != '<') {
                result.append(flag);
            }
        }
        if (matcher.start(3) >= 0) {
            result.append(formatString, matcher.start(3), matcher.end(3));
        }
        if (matcher.start(4) >= 0) {
            result.append(formatString, matcher.start(4), matcher.end(4));
        }
        if (matcher.start(5) >= 0) {
            result.append(formatString, matcher.start(5), matcher.end(5));
        }
        result.append(formatString, matcher.start(6), matcher.end(6));
        return result.toString();
    }

    /**
     * Replaces the original call target with the new intrinsified call target. A new arguments
     * array needs to be materialized immediately before the invoke.
     */
    private static void rewriteInvoke(StructuredGraph graph, MethodCallTargetNode originalCallTarget, ValueNode locale, Deque<StringFormatSpecifier> formatSpecifiers, CoreProviders providers) {
        int argumentCount = 0;
        boolean usesFormatterFallback = false;
        for (StringFormatSpecifier formatSpecifier : formatSpecifiers) {
            argumentCount += formatSpecifier.argumentCount();
            usesFormatterFallback |= formatSpecifier.formatterFormat != null;
        }

        VirtualObjectNode virtualObject = graph.add(new VirtualArrayNode(providers.getMetaAccess().lookupJavaType(Object.class), argumentCount));
        AllocatedObjectNode allocatedObject = graph.unique(new AllocatedObjectNode(virtualObject));
        CommitAllocationNode commitAllocation = graph.add(new CommitAllocationNode());
        commitAllocation.getVirtualObjects().add(virtualObject);
        allocatedObject.setCommit(commitAllocation);
        commitAllocation.addLocks(Collections.emptyList());
        commitAllocation.getEnsureVirtual().add(false);

        HostedStringDeduplication deduplication = HostedStringDeduplication.singleton();
        StringBuilder conversionsBuilder = new StringBuilder();
        for (StringFormatSpecifier formatSpecifier : formatSpecifiers) {
            if (formatSpecifier.literal != null) {
                JavaConstant literal = providers.getConstantReflection().forString(deduplication.deduplicate(formatSpecifier.literal, false));
                commitAllocation.getValues().add(ConstantNode.forConstant(literal, providers.getMetaAccess(), graph));
            } else if (formatSpecifier.formatterFormat != null) {
                JavaConstant formatterFormat = providers.getConstantReflection().forString(deduplication.deduplicate(formatSpecifier.formatterFormat, false));
                commitAllocation.getValues().add(ConstantNode.forConstant(formatterFormat, providers.getMetaAccess(), graph));
                commitAllocation.getValues().add(formatSpecifier.argument);
            } else {
                commitAllocation.getValues().add(formatSpecifier.argument);
            }
            conversionsBuilder.append(formatSpecifier.conversion);
            if (formatSpecifier.alternate) {
                conversionsBuilder.append('#');
            }
            if (formatSpecifier.width >= 0) {
                assert String.valueOf(formatSpecifier.width).length() == 1;
                conversionsBuilder.append(formatSpecifier.width);
                conversionsBuilder.append(formatSpecifier.padding);
            }
            if (formatSpecifier.precision >= 0) {
                assert String.valueOf(formatSpecifier.precision).length() == 1;
                conversionsBuilder.append('.');
                conversionsBuilder.append(formatSpecifier.precision);
            }
        }
        graph.addBeforeFixed(originalCallTarget.invoke().asFixedNode(), commitAllocation);
        assert commitAllocation.verify();

        JavaConstant conversions = providers.getConstantReflection().forString(deduplication.deduplicate(conversionsBuilder.toString(), false));
        ValueNode conversionsNode = ConstantNode.forConstant(conversions, providers.getMetaAccess(), graph);
        Method targetMethod = locale == null ? (usesFormatterFallback ? INTRINSIC_FORMATTER_FALLBACK_METHOD : INTRINSIC_METHOD)
                        : (usesFormatterFallback ? INTRINSIC_FORMATTER_FALLBACK_LOCALE_METHOD : INTRINSIC_LOCALE_METHOD);
        ResolvedJavaMethod intrinsicMethod = providers.getMetaAccess().lookupJavaMethod(targetMethod);
        ValueNode[] intrinsicArguments = locale == null ? new ValueNode[]{conversionsNode, allocatedObject} : new ValueNode[]{locale, conversionsNode, allocatedObject};
        MethodCallTargetNode intrinsicCallTarget = graph
                        .add(new SubstrateMethodCallTargetNode(InvokeKind.Static, intrinsicMethod, intrinsicArguments, originalCallTarget.returnStamp()));
        originalCallTarget.replaceAndDelete(intrinsicCallTarget);

        graph.getDebug().log("Intrinsified format string to %s", conversionsBuilder);
    }

    private static void appendLiteral(Deque<StringFormatSpecifier> formatSpecifiers, String literal) {
        if (formatSpecifiers.size() > 0) {
            StringFormatSpecifier last = formatSpecifiers.getLast();
            if (last.literal != null) {
                formatSpecifiers.removeLast();
                formatSpecifiers.addLast(new StringFormatSpecifier(last.literal + literal));
                return;
            }
        }
        formatSpecifiers.addLast(new StringFormatSpecifier(literal));
    }

    /*
     * Handles the fixed format string %% and %n. Note that we know the line separator already at
     * image build time, so no format specifier is needed for it at run time.
     */
    private static String matchFixedString(String formatString, int n) {
        if (n + 1 < formatString.length()) {
            switch (formatString.charAt(n + 1)) {
                case '%':
                    return "%";
                case 'n':
                    return System.lineSeparator();
            }
        }
        return null;
    }

    /**
     * Extracts the array elements for the provided {@link AllocatedObjectNode} from the
     * {@link CommitAllocationNode}. The {@link CommitAllocationNode} stores the field/array value
     * of all objects that it materializes in a single list of nodes, and it does not maintain the
     * start indices, so we need to search through all allocations that it materializes until we
     * find our allocation.
     */
    private static List<ValueNode> extractArrayElements(AllocatedObjectNode allocatedObjectNode) {
        CommitAllocationNode commitAllocationNode = allocatedObjectNode.getCommit();
        int objectStartIndex = 0;
        for (VirtualObjectNode virtualObject : commitAllocationNode.getVirtualObjects()) {
            if (virtualObject == allocatedObjectNode.getVirtualObject()) {
                /* We found the start of the object we were looking for. */
                assert virtualObject instanceof VirtualArrayNode : virtualObject;
                return commitAllocationNode.getValues().subList(objectStartIndex, objectStartIndex + virtualObject.entryCount());
            }
            objectStartIndex += virtualObject.entryCount();
        }
        throw GraalError.shouldNotReachHere("Did not find virtual object"); // ExcludeFromJacocoGeneratedReport
    }
}
