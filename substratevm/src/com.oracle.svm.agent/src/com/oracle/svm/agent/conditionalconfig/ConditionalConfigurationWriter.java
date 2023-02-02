/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.conditionalconfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.oracle.svm.agent.configwithorigins.ConfigurationWithOriginsTracer;
import com.oracle.svm.agent.configwithorigins.ConfigurationWithOriginsWriter;
import com.oracle.svm.agent.tracing.core.TracingResultWriter;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.conditional.ConditionalConfigurationComputer;
import com.oracle.svm.configure.config.conditional.ConditionalConfigurationPredicate;
import com.oracle.svm.configure.config.conditional.MethodCallNode;
import com.oracle.svm.configure.filters.ComplexFilter;

/**
 * Outputs configuration augmented with reachability conditions.
 *
 * This writer leverages the configuration origin information to deduce conditions for the
 * configuration. See {@link ConditionalConfigurationComputer}
 */
public class ConditionalConfigurationWriter implements TracingResultWriter {
    private final ConfigurationWithOriginsTracer tracer;
    private final ComplexFilter userCodeFilter;
    private final ConditionalConfigurationPredicate predicate;

    public ConditionalConfigurationWriter(ConfigurationWithOriginsTracer tracer, ComplexFilter userCodeFilter, ConditionalConfigurationPredicate predicate) {
        this.tracer = tracer;
        this.userCodeFilter = userCodeFilter;
        this.predicate = predicate;
    }

    @Override
    public boolean supportsOnUnloadTraceWriting() {
        return true;
    }

    @Override
    public boolean supportsPeriodicTraceWriting() {
        return false;
    }

    @Override
    public List<Path> writeToDirectory(Path directoryPath) throws IOException {
        MethodCallNode rootNode = tracer.getRootNode();
        List<Path> writtenFiles = new ConfigurationWithOriginsWriter(tracer).writeToDirectory(directoryPath);
        ConfigurationSet config = new ConditionalConfigurationComputer(rootNode, userCodeFilter, predicate).computeConditionalConfiguration();
        writtenFiles.addAll(config.writeConfiguration(configurationFile -> directoryPath.resolve(configurationFile.getFileName())));
        return writtenFiles;
    }
}
