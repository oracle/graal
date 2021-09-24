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
import java.util.Map;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.config.PredefinedClassesConfiguration;
import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.SerializationConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.util.json.JSONParser;

public class TraceProcessor extends AbstractProcessor {
    private final AccessAdvisor advisor;
    private final JniProcessor jniProcessor;
    private final ReflectionProcessor reflectionProcessor;
    private final SerializationProcessor serializationProcessor;
    private final ClassLoadingProcessor classLoadingProcessor;

    private final TraceProcessor omittedConfigProcessor;

    public TraceProcessor(AccessAdvisor accessAdvisor, TypeConfiguration jniConfiguration, TypeConfiguration reflectionConfiguration,
                    ProxyConfiguration proxyConfiguration, ResourceConfiguration resourceConfiguration, SerializationConfiguration serializationConfiguration,
                    PredefinedClassesConfiguration predefinedClassesConfiguration, TraceProcessor omittedConfigProcessor) {
        advisor = accessAdvisor;
        jniProcessor = new JniProcessor(this.advisor, jniConfiguration, reflectionConfiguration);
        reflectionProcessor = new ReflectionProcessor(this.advisor, reflectionConfiguration, proxyConfiguration, resourceConfiguration);
        serializationProcessor = new SerializationProcessor(this.advisor, serializationConfiguration);
        classLoadingProcessor = new ClassLoadingProcessor(predefinedClassesConfiguration);
        this.omittedConfigProcessor = omittedConfigProcessor;
    }

    public TypeConfiguration getJniConfiguration() {
        TypeConfiguration result = jniProcessor.getConfiguration();
        if (omittedConfigProcessor != null) {
            result = TypeConfiguration.copyAndSubtract(result, omittedConfigProcessor.jniProcessor.getConfiguration());
        }
        return result;
    }

    public TypeConfiguration getReflectionConfiguration() {
        TypeConfiguration result = reflectionProcessor.getConfiguration();
        if (omittedConfigProcessor != null) {
            result = TypeConfiguration.copyAndSubtract(result, omittedConfigProcessor.reflectionProcessor.getConfiguration());
        }
        return result;
    }

    public ProxyConfiguration getProxyConfiguration() {
        ProxyConfiguration result = reflectionProcessor.getProxyConfiguration();
        if (omittedConfigProcessor != null) {
            result = new ProxyConfiguration(result);
            result.removeAll(omittedConfigProcessor.reflectionProcessor.getProxyConfiguration());
        }
        return result;
    }

    public ResourceConfiguration getResourceConfiguration() {
        ResourceConfiguration result = reflectionProcessor.getResourceConfiguration();
        if (omittedConfigProcessor != null) {
            result = new ResourceConfiguration(result);
            result.removeAll(omittedConfigProcessor.reflectionProcessor.getResourceConfiguration());
        }
        return result;
    }

    public SerializationConfiguration getSerializationConfiguration() {
        SerializationConfiguration result = serializationProcessor.getSerializationConfiguration();
        if (omittedConfigProcessor != null) {
            result = new SerializationConfiguration(result);
            result.removeAll(omittedConfigProcessor.serializationProcessor.getSerializationConfiguration());
        }
        return result;
    }

    public PredefinedClassesConfiguration getPredefinedClassesConfiguration() {
        return classLoadingProcessor.getPredefinedClassesConfiguration();
    }

    public ConfigurationBase getConfiguration(ConfigurationFile configFile) {
        assert configFile.canBeGeneratedByAgent();
        switch (configFile) {
            case DYNAMIC_PROXY:
                return getProxyConfiguration();
            case JNI:
                return getJniConfiguration();
            case REFLECTION:
                return getReflectionConfiguration();
            case RESOURCES:
                return getResourceConfiguration();
            case SERIALIZATION:
                return getSerializationConfiguration();
            case PREDEFINED_CLASSES_NAME:
                return getPredefinedClassesConfiguration();
            default:
                assert false; // should never reach here
        }
        return null;
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
                case "serialization":
                    serializationProcessor.processEntry(entry);
                    break;
                case "classloading":
                    classLoadingProcessor.processEntry(entry);
                    break;
                default:
                    logWarning("Unknown tracer, ignoring: " + tracer);
                    break;
            }
        } catch (Exception e) {
            StringWriter stackTrace = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTrace));
            logWarning("Error processing trace entry " + entry.toString() + ": " + stackTrace);
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
