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
package jdk.graal.compiler.ide;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.SourceLanguagePosition;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.util.json.JsonFormatter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The IDEReport class is responsible for generating reports for IDE plugins. It provides methods to
 * save various types of reports, such as line reports, unreachable range reports, class reports,
 * field reports, and method reports.
 * <p>
 * The reports are stored in a JSON file, which can be used by an IDE plugin to display the
 * information to the user.
 */
public final class IDEReport {

    // TODO rename filename into filepath (also in IDE plugin)

    private static final String REPORT_KIND_K = "kind";
    private static final String FILENAME_K = "filename";
    private static final String LINE_K = "line";
    private static final String START_LINE_K = "start-line";
    private static final String END_LINE_K = "end-line";
    private static final String MSG_K = "msg";
    private static final String INLINE_CTX_K = "inlinectx";
    private static final String CLASS_K = "class";
    private static final String FIELD_K = "field";
    private static final String MTH_NAME_K = "mthname";
    private static final String MTH_SIG_K = "mthsig";

    private static final String REPORT_KIND_LINE = "LINE";
    private static final String REPORT_KIND_UNREACHABLE_RANGE = "UNREACHABLE";
    private static final String REPORT_KIND_CLASS = "CLASS";
    private static final String REPORT_KIND_CLASS_FIELD = "FIELD";
    private static final String REPORT_KIND_METHOD = "METHOD";

    private static final String REPORTS_LIST_TOPLEVEL_KEY = "reports";
    private static final String USED_METHODS_TOPLEVEL_KEY = "used_methods";

    public static final class Options {
        private Options() {
        }

        @Option(help = "Print build report for the Native Image Intellij plugin.")//
        public static final OptionKey<Boolean> IDEReport = new OptionKey<>(false) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    GraalOptions.TrackNodeSourcePosition.update(values, true);
                }
            }
        };

        @Option(help = "Print build report for the Native Image Intellij plugin for the specified set of files.")//
        public static final OptionKey<String> IDEReportFiltered = new OptionKey<>("") {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
                if (newValue != null) {
                    Options.IDEReport.update(values, true);
                }
            }
        };

    }

    private static IDEReport instance = null;

    private final ClassFilter filter;

    private final ConcurrentLinkedQueue<Map<String, Object>> reportsCollector = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> compiledMethodsCollector = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> inlinedMethodsCollector = new ConcurrentLinkedQueue<>();

    private IDEReport(ClassFilter filter) {
        this.filter = filter;
    }

    public static void createInstance(String filterDescr) {
        instance = new IDEReport(ClassFilter.parseFilterDescr(filterDescr));
    }

    public static int getLineNumber(NodeSourcePosition nodeSourcePosition) {
        SourceLanguagePosition sourceLanguage = nodeSourcePosition.getSourceLanguage();
        if (sourceLanguage == null) {
            return nodeSourcePosition.getMethod().asStackTraceElement(nodeSourcePosition.getBCI()).getLineNumber();
        }
        return sourceLanguage.getLineNumber();
    }

    public static String getFilePath(ResolvedJavaMethod mth) {
        return getFilePath(mth.getDeclaringClass());
    }

    public static String getFilePath(ResolvedJavaType type) {
        var srcFileName = type.getSourceFileName();
        if (srcFileName == null) {
            return null;
        }
        var javaNameWithSlashes = type.toJavaName().replace('.', '/');
        var idxOfLastSlash = javaNameWithSlashes.lastIndexOf('/');
        return javaNameWithSlashes.substring(0, idxOfLastSlash + 1) + srcFileName;
    }

    public static List<QualifiedStacktraceElement> getInliningTrace(NodeSourcePosition srcPos) {
        if (srcPos == null || srcPos.getCaller() == null) {
            return null;
        }
        var trace = new ArrayList<QualifiedStacktraceElement>();
        for (var callerPos = srcPos.getCaller(); callerPos != null; callerPos = callerPos.getCaller()) {
            var filepath = getFilePath(callerPos.getMethod());
            if (filepath == null) {
                filepath = "<unknown>";
            }
            trace.add(new QualifiedStacktraceElement(filepath, getLineNumber(callerPos)));
        }
        return trace;
    }

    public static void runIfEnabled(Consumer<IDEReport> action) {
        if (instance != null) {
            action.accept(instance);
        }
    }

    /**
     * Use {@link IDEReport#isEnabled()} only in situations where
     * {@link IDEReport#runIfEnabled(Consumer)} cannot be used instead
     */
    public static boolean isEnabled() {
        return instance != null;
    }

    /**
     * Saves a report specific to the given source code line. This is a generic method that can be
     * used for inserting arbitrary reports.
     */
    public void saveLineReport(String filepath, String fullyQualifiedClassName, int line, String msg, List<QualifiedStacktraceElement> inliningTrace) {
        if (anyIsNull(filepath, msg) || anyIsNonPositive(line) || !shouldReportClass(fullyQualifiedClassName)) {
            return;
        }
        msg = preprocessMsg(msg);
        var data = new HashMap<String, Object>();
        data.put(REPORT_KIND_K, REPORT_KIND_LINE);
        data.put(FILENAME_K, filepath);
        data.put(LINE_K, line);
        data.put(MSG_K, msg);
        terminateAndSaveReport(data, inliningTrace);
    }

    /**
     * Saves a report indicating that the given range of source code lines is unreachable.
     */
    public void saveUnreachableRangeReport(String filepath, String fullyQualifiedClassName, int startLine, int endLine, String msg, List<QualifiedStacktraceElement> inliningTrace) {
        if (anyIsNull(filepath, msg) || anyIsNonPositive(startLine, endLine) || !shouldReportClass(fullyQualifiedClassName)) {
            return;
        }
        msg = preprocessMsg(msg);
        var data = new HashMap<String, Object>();
        data.put(REPORT_KIND_K, REPORT_KIND_UNREACHABLE_RANGE);
        data.put(FILENAME_K, filepath);
        data.put(START_LINE_K, startLine);
        data.put(END_LINE_K, endLine);
        data.put(MSG_K, msg);
        terminateAndSaveReport(data, inliningTrace);
    }

    /**
     * Saves a class-level report, used, e.g., to present information about class initialization.
     */
    public void saveClassReport(String filepath, String fullyQualifiedClassName, String msg) {
        if (anyIsNull(filepath, fullyQualifiedClassName) || !shouldReportClass(fullyQualifiedClassName)) {
            return;
        }
        msg = preprocessMsg(msg);
        var data = new HashMap<String, Object>();
        data.put(REPORT_KIND_K, REPORT_KIND_CLASS);
        data.put(FILENAME_K, filepath);
        data.put(CLASS_K, fullyQualifiedClassName);
        data.put(MSG_K, msg);
        terminateAndSaveReport(data, null);
    }

    /**
     * Saves a field report that can be used, e.g., to specify that a field has a constant value.
     */
    public void saveFieldReport(String filepath, String fullyQualifiedClassName, String fieldName, String msg) {
        if (anyIsNull(filepath, fullyQualifiedClassName, fieldName, msg) || !shouldReportClass(fullyQualifiedClassName)) {
            return;
        }
        msg = preprocessMsg(msg);
        var data = new HashMap<String, Object>();
        data.put(REPORT_KIND_K, REPORT_KIND_CLASS_FIELD);
        data.put(FILENAME_K, filepath);
        data.put(CLASS_K, fullyQualifiedClassName);
        data.put(FIELD_K, fieldName);
        data.put(MSG_K, msg);
        terminateAndSaveReport(data, null);
    }

    public void saveMethodReport(String filepath, String fullyQualifiedClassName, String methodName, String methodSignature, String msg) {
        if (anyIsNull(filepath, fullyQualifiedClassName, methodName, methodSignature) || !shouldReportClass(fullyQualifiedClassName)) {
            return;
        }
        msg = preprocessMsg(msg);
        var data = new HashMap<String, Object>();
        data.put(REPORT_KIND_K, REPORT_KIND_METHOD);
        data.put(FILENAME_K, filepath);
        data.put(CLASS_K, fullyQualifiedClassName);
        data.put(MTH_NAME_K, methodName);
        data.put(MTH_SIG_K, methodSignature);
        data.put(MSG_K, msg);
        terminateAndSaveReport(data, null);
    }

    public void saveMethodCompiled(String filepath, String fullyQualifiedClassName, String methodName, String methodSignature) {
        if (anyIsNull(filepath, fullyQualifiedClassName, methodName, methodSignature) || !shouldReportClass(fullyQualifiedClassName)) {
            return;
        }
        var data = new HashMap<String, Object>();
        data.put(FILENAME_K, filepath);
        data.put(CLASS_K, fullyQualifiedClassName);
        data.put(MTH_NAME_K, methodName);
        data.put(MTH_SIG_K, methodSignature);
        compiledMethodsCollector.add(data);
    }

    public void saveMethodInlined(String filepath, String fullyQualifiedClassName, String methodName, String methodSignature) {
        if (anyIsNull(filepath, fullyQualifiedClassName, methodName, methodSignature) || !shouldReportClass(fullyQualifiedClassName)) {
            return;
        }
        var data = new HashMap<String, Object>();
        data.put(FILENAME_K, filepath);
        data.put(CLASS_K, fullyQualifiedClassName);
        data.put(MTH_NAME_K, methodName);
        data.put(MTH_SIG_K, methodSignature);
        inlinedMethodsCollector.add(data);
    }

    private void terminateAndSaveReport(Map<String, Object> data, List<QualifiedStacktraceElement> inliningTrace) {
        if (inliningTrace != null) {
            data.put(INLINE_CTX_K, mkInlineCtxJson(inliningTrace));
        }
        reportsCollector.add(data);
    }

    private boolean shouldReportClass(String qualifiedClassName) {
        return filter == null || filter.shouldBeReported(qualifiedClassName);
    }

    /**
     * Prints the reports to a JSON file.
     *
     * @param reportsDir the directory to write the reports to
     */
    public void print(Path reportsDir) {
        var reportName = "native_image_ide_report_" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':', '-') + ".json";
        var usedMethods = compiledMethodsCollector.stream().distinct().collect(Collectors.toList());
        var inlinedOnlyMethods = new HashSet<>(inlinedMethodsCollector);
        usedMethods.forEach(inlinedOnlyMethods::remove);
        for (var inlinedOnlyMethodReportData : inlinedOnlyMethods) {
            var methodName = inlinedOnlyMethodReportData.get(MTH_NAME_K);
            var report = new HashMap<>(inlinedOnlyMethodReportData);
            report.put(REPORT_KIND_K, REPORT_KIND_METHOD);
            report.put(MSG_K, "Method " + methodName + " is not part of the produced image since all its invocations are inlined");
            reportsCollector.add(report);
        }
        usedMethods.addAll(inlinedOnlyMethods);
        var uniqueReports = reportsCollector.stream().distinct().toList();
        var allData = EconomicMap.of(
                        REPORTS_LIST_TOPLEVEL_KEY, uniqueReports,
                        USED_METHODS_TOPLEVEL_KEY, usedMethods);
        var json = JsonFormatter.formatJsonPretty(allData);
        var path = reportsDir.resolve(reportName);
        try {
            Files.createDirectories(reportsDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (var fw = new FileWriter(path.toFile()); var pw = new PrintWriter(fw)) {
            pw.println(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Map<String, Object>> mkInlineCtxJson(List<QualifiedStacktraceElement> stackTraceElements) {
        var result = new ArrayList<Map<String, Object>>();
        for (var ste : stackTraceElements) {
            result.add(Map.of(
                            FILENAME_K, ste.qualifiedFileName(),
                            LINE_K, ste.line()));
        }
        return result;
    }

    private static String preprocessMsg(String msg) {
        return mkEndlExplicit(replaceSpecialMethodNames(msg));
    }

    private static String mkEndlExplicit(String msg) {
        var sb = new StringBuilder();
        for (int i = 0; i < msg.length(); i++) {
            var c = msg.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String replaceSpecialMethodNames(String rawMsg) {
        return rawMsg.replace("<init>", "constructor")
                        .replace("<clinit>", "class initializer");
    }

    public static boolean anyIsNull(Object... objects) {
        for (Object object : objects) {
            if (object == null) {
                return true;
            }
        }
        return false;
    }

    public static boolean anyIsNonPositive(int... numbers) {
        for (int number : numbers) {
            if (number <= 0) {
                return true;
            }
        }
        return false;
    }

}
