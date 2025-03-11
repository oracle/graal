/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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

import static com.oracle.svm.configure.trace.LazyValueUtils.lazyValue;

import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.SerializationConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;

import jdk.graal.compiler.java.LambdaUtils;

public class SerializationProcessor extends AbstractProcessor {
    private final AccessAdvisor advisor;

    public SerializationProcessor(AccessAdvisor advisor) {
        this.advisor = advisor;
    }

    @Override
    @SuppressWarnings("unchecked")
    void processEntry(EconomicMap<String, Object> entry, ConfigurationSet configurationSet) {
        boolean invalidResult = Boolean.FALSE.equals(entry.get("result"));
        if (invalidResult) {
            return;
        }
        UnresolvedConfigurationCondition condition = UnresolvedConfigurationCondition.alwaysTrue();
        String function = (String) entry.get("function");
        List<?> args = (List<?>) entry.get("args");
        SerializationConfiguration serializationConfiguration = configurationSet.getSerializationConfiguration();
        TypeConfiguration reflectionConfiguration = configurationSet.getReflectionConfiguration();

        if ("ObjectStreamClass.<init>".equals(function) || "ObjectInputStream.readClassDescriptor".equals(function)) {
            expectSize(args, 1);

            if (advisor.shouldIgnore(LazyValueUtils.lazyValue((String) args.get(0)), LazyValueUtils.lazyValue(null), false, entry)) {
                return;
            }

            String reflectionName = (String) args.get(0);

            if (reflectionName.contains(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING)) {
                serializationConfiguration.registerLambdaCapturingClass(condition, reflectionName);
            } else {
                reflectionConfiguration.getOrCreateType(condition, NamedConfigurationTypeDescriptor.fromReflectionName(reflectionName)).setSerializable();
            }
        } else if ("SerializedLambda.readResolve".equals(function)) {
            expectSize(args, 1);

            if (advisor.shouldIgnore(LazyValueUtils.lazyValue((String) args.get(0)), LazyValueUtils.lazyValue(null), entry)) {
                return;
            }

            serializationConfiguration.registerLambdaCapturingClass(condition, (String) args.get(0));
        } else if ("ProxyClassSerialization".equals(function)) {
            expectSize(args, 1);

            List<String> interfaces = (List<String>) args.get(0);

            for (String iface : interfaces) {
                if (advisor.shouldIgnore(lazyValue(iface), LazyValueUtils.lazyValue(null), copyWithUniqueEntry(entry, "ignoredInterface", iface))) {
                    return;
                }
            }

            serializationConfiguration.registerProxyClass(condition, (List<String>) args.get(0));
        }
    }
}
