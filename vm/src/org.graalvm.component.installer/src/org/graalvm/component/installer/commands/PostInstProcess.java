/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

    public PostInstProcess(CommandInput cInput, Feedback fb) {
        this.input = cInput;
        this.feedback = fb;
    }

    public void addComponentInfo(ComponentInfo info) {
        this.infos.add(info);
    }
    
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$\\{([\\p{Alnum}_-]+)\\}");

    String replaceTokens(ComponentInfo info, String message) {
        Map<String, String> tokens = new HashMap<>();
        String graalPath = input.getGraalHomePath().normalize().toString();
        tokens.putAll(info.getRequiredGraalValues());
        tokens.putAll(input.getLocalRegistry().getGraalCapabilities());
        tokens.put(CommonConstants.TOKEN_GRAALVM_PATH, graalPath);

        Matcher m = TOKEN_PATTERN.matcher(message);
        StringBuilder result = null;
        int start = 0;
        int last = 0;
        while (m.find(start)) {
            String token = m.group(1);
            String val = tokens.get(token);
            if (val != null) {
                if (result == null) {
                    result = new StringBuilder(graalPath.length() * 2);
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
            feedback.output("INSTALL_RebuildPolyglotNeeded", File.separator, input.getGraalHomePath().resolve(p).normalize());
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
