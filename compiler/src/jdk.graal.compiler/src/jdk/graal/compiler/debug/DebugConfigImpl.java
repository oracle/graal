/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import static jdk.graal.compiler.debug.DebugContext.BASIC_LEVEL;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.JavaMethod;

final class DebugConfigImpl implements DebugConfig {

    private final OptionValues options;

    private final DebugFilter logFilter;
    private final DebugFilter dumpFilter;
    private final MethodFilter methodFilter;
    private final List<DebugDumpHandler> dumpHandlers;
    private final PrintStream output;

    DebugConfigImpl(OptionValues options) {
        this(options, TTY.out, Collections.emptyList());
    }

    DebugConfigImpl(OptionValues options, PrintStream output,
                    List<DebugDumpHandler> dumpHandlers) {
        this(options, DebugOptions.Log.getValue(options),
                        DebugOptions.Dump.getValue(options),
                        DebugOptions.MethodFilter.getValue(options),
                        output, dumpHandlers);
    }

    DebugConfigImpl(OptionValues options,
                    String logFilter,
                    String dumpFilter,
                    String methodFilter,
                    PrintStream output,
                    List<DebugDumpHandler> dumpHandlers) {
        this.options = options;
        this.logFilter = DebugFilter.parse(logFilter);
        this.dumpFilter = DebugFilter.parse(dumpFilter);
        if (methodFilter == null || methodFilter.isEmpty()) {
            this.methodFilter = null;
        } else {
            this.methodFilter = MethodFilter.parse(methodFilter);
        }

        this.dumpHandlers = Collections.unmodifiableList(dumpHandlers);
        this.output = output;
    }

    @Override
    public OptionValues getOptions() {
        return options;
    }

    @Override
    public int getLogLevel(DebugContext.Scope scope) {
        return getLevel(scope, logFilter);
    }

    @Override
    public boolean isLogEnabledForMethod(DebugContext.Scope scope) {
        return isEnabledForMethod(scope, logFilter);
    }

    @Override
    public int getDumpLevel(DebugContext.Scope scope) {
        return getLevel(scope, dumpFilter);
    }

    @Override
    public boolean isDumpEnabledForMethod(DebugContext.Scope scope) {
        return isEnabledForMethod(scope, dumpFilter);
    }

    @Override
    public boolean methodFilterMatchesCurrentMethod(DebugContext.Scope scope) {
        return checkMethodFilter(scope);
    }

    @Override
    public PrintStream output() {
        return output;
    }

    private int getLevel(DebugContext.Scope scope, DebugFilter filter) {
        if (filter == null) {
            // null means the value has not been set
            return -1;
        }
        int level = filter.matchLevel(scope);
        if (level >= 0 && !checkMethodFilter(scope)) {
            level = -1;
        }
        return level;
    }

    private boolean isEnabledForMethod(DebugContext.Scope scope, DebugFilter filter) {
        return filter != null && checkMethodFilter(scope);
    }

    private boolean checkMethodFilter(DebugContext.Scope scope) {
        if (methodFilter == null) {
            return true;
        } else {
            JavaMethod lastMethod = null;
            Iterable<Object> context = scope.getCurrentContext();
            for (Object o : context) {
                if (methodFilter != null) {
                    JavaMethod method = DebugConfig.asJavaMethod(o);
                    if (method != null) {
                        if (!DebugOptions.MethodFilterRootOnly.getValue(options)) {
                            if (methodFilter.matches(method)) {
                                return true;
                            }
                        } else {
                            /*
                             * The context values operate as a stack so if we want MethodFilter to
                             * only apply to the root method we have to check only the last method
                             * seen.
                             */
                            lastMethod = method;
                        }
                    }
                }
            }
            if (lastMethod != null && methodFilter.matches(lastMethod)) {
                return true;
            }
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug config:");
        add(sb, "Log", logFilter);
        add(sb, "Dump", dumpFilter);
        add(sb, "MethodFilter", methodFilter);
        return sb.toString();
    }

    private static void add(StringBuilder sb, String name, Object filter) {
        if (filter != null) {
            sb.append(' ');
            sb.append(name);
            sb.append('=');
            if (filter instanceof Object[]) {
                sb.append(Arrays.toString((Object[]) filter));
            } else {
                sb.append(String.valueOf(filter));
            }
        }
    }

    @Override
    public RuntimeException interceptException(DebugContext debug, Throwable e) {
        if (e instanceof BailoutException) {
            boolean causedByCompilerAssert = e instanceof CausableByCompilerAssert && ((CausableByCompilerAssert) e).isCausedByCompilerAssert();
            if (!DebugOptions.InterceptBailout.getValue(options) && !causedByCompilerAssert) {
                return null;
            }
        }

        OptionValues interceptOptions = new OptionValues(options,
                        DebugOptions.Dump, ":" + BASIC_LEVEL,
                        DebugOptions.Log, ":" + BASIC_LEVEL);
        DebugConfigImpl config = new DebugConfigImpl(interceptOptions, output, dumpHandlers);
        ScopeImpl scope = debug.currentScope;
        scope.updateFlags(config);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(baos));
            debug.log("Exception raised in scope %s: %s", debug.getCurrentScopeName(), baos);
            Map<Object, Object> firstSeen = EconomicHashMap.newIdentityMap();
            for (Object o : debug.context()) {
                // Only dump a context object once.
                if (!firstSeen.containsKey(o)) {
                    firstSeen.put(o, o);
                    if (DebugOptions.DumpOnError.getValue(options) || DebugOptions.Dump.getValue(options) != null) {
                        debug.forceDump(o, "Exception: %s", e);
                    }
                    debug.log("Context obj %s", o);
                }
            }
        } finally {
            scope.updateFlags(this);
        }
        return null;
    }

    @Override
    public Collection<DebugDumpHandler> dumpHandlers() {
        return dumpHandlers;
    }
}
