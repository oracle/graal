/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.trace;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.util.json.JsonParser;

public class TraceProcessor extends AbstractProcessor {
    private final AccessAdvisor advisor;
    private final JniProcessor jniProcessor;
    private final ReflectionProcessor reflectionProcessor;
    private final SerializationProcessor serializationProcessor;
    private final ClassLoadingProcessor classLoadingProcessor;

    public TraceProcessor(AccessAdvisor accessAdvisor) {
        advisor = accessAdvisor;
        jniProcessor = new JniProcessor(this.advisor);
        reflectionProcessor = new ReflectionProcessor(this.advisor);
        serializationProcessor = new SerializationProcessor(this.advisor);
        classLoadingProcessor = new ClassLoadingProcessor();
    }

    @SuppressWarnings("unchecked")
    public void process(Reader reader, ConfigurationSet configurationSet) throws IOException {
        setInLivePhase(false);
        JsonParser parser = new JsonParser(reader);
        List<EconomicMap<String, ?>> trace = (List<EconomicMap<String, ?>>) parser.parse();
        processTrace(trace, configurationSet);
    }

    private void processTrace(List<EconomicMap<String, ?>> trace, ConfigurationSet configurationSet) {
        for (EconomicMap<String, ?> entry : trace) {
            processEntry(entry, configurationSet);
        }
    }

    @Override
    public void processEntry(EconomicMap<String, ?> entry, ConfigurationSet configurationSet) {
        try {
            String tracer = (String) entry.get("tracer");
            switch (tracer) {
                case "meta": {
                    String event = (String) entry.get("event");
                    if (event.equals("phase_change")) {
                        setInLivePhase(entry.get("phase").equals("live"));
                    } else if (event.equals("initialization")) {
                        // not needed for now, but contains version for breaking changes
                    } else {
                        LogUtils.warning("Unknown meta event, ignoring: " + event);
                    }
                    break;
                }
                case "jni":
                    jniProcessor.processEntry(entry, configurationSet);
                    break;
                case "reflect":
                    reflectionProcessor.processEntry(entry, configurationSet);
                    break;
                case "serialization":
                    serializationProcessor.processEntry(entry, configurationSet);
                    break;
                case "classloading":
                    classLoadingProcessor.processEntry(entry, configurationSet);
                    break;
                default:
                    LogUtils.warning("Unknown tracer, ignoring: " + tracer);
                    break;
            }
        } catch (Exception e) {
            StringWriter stackTrace = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTrace));
            LogUtils.warning("Error processing trace entry " + entry.toString() + ": " + stackTrace);
        }
    }

    @Override
    void setInLivePhase(boolean live) {
        advisor.setInLivePhase(live);
        jniProcessor.setInLivePhase(live);
        reflectionProcessor.setInLivePhase(live);
        super.setInLivePhase(live);
    }
}
