/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerCommand;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 *
 * @author sdedic
 */
public class PreRemoveCommand implements InstallerCommand {
    private CommandInput input;
    private Feedback feedback;
    
    @Override
    public Map<String, String> supportedOptions() {
        return Collections.EMPTY_MAP;
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        this.input = commandInput;
        this.feedback = feedBack;
    }

    @Override
    public int execute() throws IOException {
        String compId;
        PreRemoveProcess pp = new PreRemoveProcess(input.getGraalHomePath(), feedback);
        while ((compId = input.nextParameter()) != null) {
            ComponentInfo info = input.getLocalRegistry().loadSingleComponent(compId.toLowerCase(), true);
            if (info != null) {
                pp.addComponentInfo(info);
            }
        }
        
        pp.run();
        return 0;
    }
}
