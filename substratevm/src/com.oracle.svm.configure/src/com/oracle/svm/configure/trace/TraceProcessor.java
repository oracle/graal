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
import java.io.Reader;
import java.util.List;
import java.util.Map;

import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.core.util.json.JSONParser;

public class TraceProcessor extends AbstractProcessor {
    private final AccessAdvisor advisor;
    private final JniProcessor jniProcessor;
    private final ReflectionProcessor reflectionProcessor;

    public TraceProcessor(AccessAdvisor accessAdvisor, TypeConfiguration jniConfiguration, TypeConfiguration reflectionConfiguration,
                    ProxyConfiguration proxyConfiguration, ResourceConfiguration resourceConfiguration) {
        advisor = accessAdvisor;
        jniProcessor = new JniProcessor(this.advisor, jniConfiguration, reflectionConfiguration);
        reflectionProcessor = new ReflectionProcessor(this.advisor, reflectionConfiguration, proxyConfiguration, resourceConfiguration);
    }

    public TypeConfiguration getJniConfiguration() {
        return jniProcessor.getConfiguration();
    }

    public TypeConfiguration getReflectionConfiguration() {
        return reflectionProcessor.getConfiguration();
    }

    public ProxyConfiguration getProxyConfiguration() {
        return reflectionProcessor.getProxyConfiguration();
    }

    public ResourceConfiguration getResourceConfiguration() {
        return reflectionProcessor.getResourceConfiguration();
    }

    @SuppressWarnings("unchecked")
    public void process(Reader reader) throws IOException {
        setInLivePhase(false);
        JSONParser parser = new JSONParser(reader);
        List<Map<String, ?>> trace = (List<Map<String, ?>>) parser.parse();
        processTrace(trace);
    }

    private void processTrace(List<Map<String, ?>> trace) {
        for (Map<String, ?> entry : trace) {
            processEntry(entry);
        }
    }

    @Override
    public void processEntry(Map<String, ?> entry) {
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
                        logWarning("Unknown meta event, ignoring: " + event);
                    }
                    break;
                }
                case "jni":
                    jniProcessor.processEntry(entry);
                    break;
                case "reflect":
                    reflectionProcessor.processEntry(entry);
                    break;
                default:
                    logWarning("Unknown tracer, ignoring: " + tracer);
                    break;
            }
        } catch (Exception e) {
            logWarning("Error processing trace entry: " + e.toString() + ": " + entry.toString());
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
