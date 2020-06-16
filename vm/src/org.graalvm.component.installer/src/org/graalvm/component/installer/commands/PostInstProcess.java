/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.commands;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.WARN_REBUILD_IMAGES;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 *
 * @author sdedic
 */
final class PostInstProcess {
    private final CommandInput input;
    private final Feedback feedback;
    private final List<ComponentInfo> infos = new ArrayList<>();
    private boolean rebuildPolyglot;

    PostInstProcess(CommandInput cInput, Feedback fb) {
        this.input = cInput;
        this.feedback = fb;
    }

    public void addComponentInfo(ComponentInfo info) {
        this.infos.add(info);
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$\\{([\\p{Alnum}_-]+)\\}");

    String replaceTokens(ComponentInfo info, String message) {
        Map<String, String> tokens = new HashMap<>();
        Path graalPath = input.getGraalHomePath().normalize();

        Path archPath = SystemUtils.getRuntimeLibDir(graalPath, true);

        tokens.put(CommonConstants.TOKEN_GRAALVM_PATH, graalPath.toString());
        tokens.put(CommonConstants.TOKEN_GRAALVM_LANG_DIR, SystemUtils.getRuntimeBaseDir(graalPath).resolve("languages").toString()); // NOI18N
        tokens.put(CommonConstants.TOKEN_GRAALVM_RTLIB_ARCH_DIR, archPath.toString());
        tokens.put(CommonConstants.TOKEN_GRAALVM_RTLIB_DIR, SystemUtils.getRuntimeLibDir(graalPath, false).toString());

        tokens.putAll(info.getRequiredGraalValues());
        tokens.putAll(input.getLocalRegistry().getGraalCapabilities());

        Matcher m = TOKEN_PATTERN.matcher(message);
        StringBuilder result = null;
        int start = 0;
        int last = 0;
        while (m.find(start)) {
            String token = m.group(1);
            String val = tokens.get(token);
            if (val != null) {
                if (result == null) {
                    result = new StringBuilder(archPath.toString().length() * 2);
                }
                result.append(message.substring(last, m.start()));
                result.append(val);
                last = m.end();
            }
            start = m.end();
        }

        if (result == null) {
            return message;
        } else {
            result.append(message.substring(last));
            return result.toString();
        }
    }

    void run() {
        for (ComponentInfo ci : infos) {
            printPostinst(ci);
            rebuildPolyglot |= ci.isPolyglotRebuild();
        }

        if (rebuildPolyglot && WARN_REBUILD_IMAGES) {
            Path p = SystemUtils.fromCommonString(CommonConstants.PATH_JRE_BIN);
            Path toolPath = RebuildImageCommand.findNativeImagePath(input, feedback);
            feedback.output("INSTALL_RebuildPolyglotNeeded", File.separator, input.getGraalHomePath().resolve(p).normalize());
            if (toolPath == null) {
                feedback.output("INSTALL_RebuildPolyglotNeeded2", CommonConstants.NATIVE_IMAGE_ID);
            }
        }
    }

    void printPostinst(ComponentInfo i) {
        String msg = i.getPostinstMessage();
        if (msg != null) {
            String replaced = replaceTokens(i, msg);
            // replace potential fileName etc
            feedback.verbatimOut(replaced, false);
            // add some newlines
            feedback.verbatimOut("", false);
        }
    }
}
