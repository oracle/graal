/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import java.util.List;
import java.util.Queue;

class ServerOptionHandler extends NativeImage.OptionHandler<NativeImageServer> {

    private static final String helpTextServer = NativeImage.getResource("/HelpServer.txt");
    private static final String enableServerOption = "--experimental-build-server";

    ServerOptionHandler(NativeImageServer nativeImage) {
        super(nativeImage);
    }

    @Override
    public boolean consume(Queue<String> args) {
        String headArg = args.peek();
        switch (headArg) {
            case "--help-experimental-build-server":
                args.poll();
                nativeImage.showMessage(helpTextServer);
                nativeImage.showNewline();
                System.exit(0);
                return true;
            case DefaultOptionHandler.noServerOption:
                args.poll();
                nativeImage.setUseServer(false);
                return true;
            case enableServerOption:
                args.poll();
                if (!nativeImage.isDryRun()) {
                    nativeImage.setUseServer(true);
                }
                return true;
            case DefaultOptionHandler.verboseServerOption:
                args.poll();
                nativeImage.setVerboseServer(true);
                return true;
        }

        if (headArg.startsWith(DefaultOptionHandler.serverOptionPrefix)) {
            String optionTail = args.poll().substring(DefaultOptionHandler.serverOptionPrefix.length());
            boolean machineWide = false;
            String oList = "list";
            String oCleanup = "cleanup";
            String oShutdown = "shutdown";
            String oWipe = "wipe";
            String oSession = "session=";
            boolean serverCleanup = false;
            boolean serverShutdown = false;
            if (optionTail.startsWith(oList)) {
                optionTail = optionTail.substring(oList.length());
                boolean listDetails = false;
                machineWide = true;
                String oDetails = "-details";
                if (optionTail.startsWith(oDetails)) {
                    optionTail = optionTail.substring(oDetails.length());
                    listDetails = true;
                }
                if (optionTail.isEmpty()) {
                    nativeImage.listServers(machineWide, listDetails);
                    System.exit(0);
                }
            } else if (optionTail.startsWith(oCleanup)) {
                optionTail = optionTail.substring(oCleanup.length());
                serverCleanup = true;
                machineWide = true;
            } else if (optionTail.startsWith(oShutdown)) {
                optionTail = optionTail.substring(oShutdown.length());
                serverShutdown = true;
                String oAll = "-all";
                if (optionTail.startsWith(oAll)) {
                    optionTail = optionTail.substring(oAll.length());
                    machineWide = true;
                }
            } else if (optionTail.equals(oWipe)) {
                nativeImage.wipeMachineDir();
                System.exit(0);
            } else if (optionTail.startsWith(oSession)) {
                nativeImage.setSessionName(optionTail.substring(oSession.length()));
                return true;
            }
            if (optionTail.isEmpty() && (serverCleanup || serverShutdown)) {
                nativeImage.cleanupServers(serverShutdown, machineWide, false);
                System.exit(0);
            }
            NativeImage.showError("Invalid server option: " + headArg);
        }
        return false;
    }

    @Override
    void addFallbackBuildArgs(List<String> buildArgs) {
        if (!nativeImage.useServer()) {
            buildArgs.add(DefaultOptionHandler.noServerOption);
        }
        if (nativeImage.verboseServer()) {
            buildArgs.add(DefaultOptionHandler.verboseServerOption);
        }
    }
}
