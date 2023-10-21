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
package com.oracle.svm.hosted.analysis;

import java.util.regex.Pattern;

import jdk.graal.compiler.core.common.SuppressSVMWarnings;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.code.BytecodePosition;

public class CallChecker {

    private final Pattern illegalCalleesPattern;
    private final Pattern targetCallersPattern;

    public CallChecker() {
        String[] targetCallers = new String[]{"com\\.oracle\\.graal\\.", "org\\.graalvm[^\\.polyglot\\.nativeapi]"};
        targetCallersPattern = buildPrefixMatchPattern(targetCallers);

        String[] illegalCallees = new String[]{"java\\.util\\.stream", "java\\.util\\.Collection\\.stream", "java\\.util\\.Arrays\\.stream"};
        illegalCalleesPattern = buildPrefixMatchPattern(illegalCallees);
    }

    /**
     * Builds a pattern that checks if the tested string starts with any of the target prefixes,
     * like so: {@code ^(str1(.*)|str2(.*)|str3(.*))}.
     */
    private static Pattern buildPrefixMatchPattern(String[] targetPrefixes) {
        StringBuilder patternStr = new StringBuilder("^(");
        for (int i = 0; i < targetPrefixes.length; i++) {
            String prefix = targetPrefixes[i];
            patternStr.append(prefix);
            patternStr.append("(.*)");
            if (i < targetPrefixes.length - 1) {
                patternStr.append("|");
            }
        }
        patternStr.append(')');
        return Pattern.compile(patternStr.toString());
    }

    public boolean isCallAllowed(BigBang bb, AnalysisMethod caller, AnalysisMethod callee, BytecodePosition srcPosition) {
        String calleeName = callee.getQualifiedName();
        if (illegalCalleesPattern.matcher(calleeName).find()) {
            String callerName = caller.getQualifiedName();
            if (targetCallersPattern.matcher(callerName).find()) {
                SuppressSVMWarnings suppress = caller.getAnnotation(SuppressSVMWarnings.class);
                AnalysisType callerType = caller.getDeclaringClass();
                while (suppress == null && callerType != null) {
                    suppress = callerType.getAnnotation(SuppressSVMWarnings.class);
                    callerType = callerType.getEnclosingType();
                }
                if (suppress != null) {
                    String[] reasons = suppress.value();
                    for (String r : reasons) {
                        if (r.equals("AllowUseOfStreamAPI")) {
                            return true;
                        }
                    }
                }
                String message = "Illegal: Graal/Truffle use of Stream API: " + calleeName;
                int bci = srcPosition.getBCI();
                String trace = caller.asStackTraceElement(bci).toString();
                bb.getUnsupportedFeatures().addMessage(callerName, caller, message, trace);
                return false;
            }
        }
        return true;
    }
}
