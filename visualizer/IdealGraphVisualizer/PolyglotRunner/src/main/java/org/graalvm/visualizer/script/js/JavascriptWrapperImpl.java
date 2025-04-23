/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.script.js;

import org.graalvm.visualizer.script.ScriptDefinition;
import org.graalvm.visualizer.script.spi.UserScriptProcessor;
import org.netbeans.api.editor.mimelookup.MimeRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Provides javascript-specific wrapping around the executing code
 *
 * @author sdedic
 */
@MimeRegistration(mimeType = "text/javascript", service = UserScriptProcessor.class)
public class JavascriptWrapperImpl implements UserScriptProcessor {
    private static final Logger LOG = Logger.getLogger(JavascriptWrapperImpl.class.getName());

    private static final AtomicInteger uniq = new AtomicInteger(0);

    private void generateFunctionalHeader(StringBuilder sb, ScriptDefinition def) {
        sb.append("(function("); // NOI18N
        boolean first = true;
        for (String pn : def.getParamNames()) {
            if (!first) {
                sb.append(", "); // NOI18N
            }
            sb.append(pn);
            first = false;
        }
        sb.append("){ "); // NOI18N
    }

    @Override
    public String processUserCode(ScriptDefinition def, String userCode) {
        if (def.isExecuteFunction()) {
            // excute a named function: append a functional, so evaluated script
            // results in an executable
            StringBuilder sb = new StringBuilder(userCode);
            sb.append("\n"); // NOI18N

            generateFunctionalHeader(sb, def);

            // execute the function, using the parameters:
            sb.append(def.getFunctionName()).append('('); // NOI18N
            boolean first = true;
            for (String pn : def.getParamNames()) {
                if (!first) {
                    sb.append(", "); // NOI18N
                }
                sb.append(pn);
                first = false;
            }
            sb.append("); })"); // NOI18N
            return sb.toString();
        }

        if (def.getParamNames().isEmpty()) {
            return userCode;
        }

        StringBuilder sb = new StringBuilder();
        generateFunctionalHeader(sb, def);
        sb.append(userCode);
        sb.append("})"); // NOI18N

        return sb.toString();
    }

    public String assignGlobals(String scriptFilename, Map<String, Object> globalValues) {
        if (globalValues.isEmpty()) {
            return null;
        }
        StringBuilder fb = new StringBuilder();
        List<String> globNames = new ArrayList<>(globalValues.size());
        int index = 0;
        int n = uniq.incrementAndGet();
        // each CustomFilter should produce a different function
        for (String pn : globalValues.keySet()) {
            fb.append("var ").append(pn).append(";\n"); // NOI18N
        }
        fb.append("(function runScript_").append(Integer.toHexString(n)).append("("); // NOI18N
        for (String pn : globalValues.keySet()) {
            if (index > 0) {
                fb.append(", ");
            }
            String nn = "__" + pn; // NOI18N
            fb.append(nn);
            globNames.add(nn);
            index++;
        }
        fb.append("){"); // NOI18N
        index = 0;
        for (String gn : globalValues.keySet()) {
            String pn = globNames.get(index);
            fb.append(gn).append(" = ").append(pn).append(";"); // NOI18N
            index++;
        }
        fb.append("})"); // NOI18N
        return fb.toString();
    }

    @Override
    public String assignGlobals(ScriptDefinition def) {
        return assignGlobals(def.getScriptFilename(), def.getGlobals());
    }
}
