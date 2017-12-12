/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.server;

import static com.oracle.svm.hosted.server.NativeImageBuildServer.PORT_PREFIX;
import static com.oracle.svm.hosted.server.NativeImageBuildServer.extractArg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.oracle.shadowed.com.google.gson.Gson;
import com.oracle.svm.hosted.server.SubstrateServerMessage.ServerCommand;

import sun.misc.Signal;

public class NativeImageBuildClient {

    private static final String COMMAND_PREFIX = "-command=";
    private static final int EXIT_FAIL = -1;
    private static final int EXIT_SUCCESS = 0;

    private static void usage() {
        System.out.println("Usage:");
        System.out.println(String.format("  java -cp <svm_jar_path> com.oracle.svm.hosted.server.NativeImageBuildClient %s<command> [%s<port_number>] [<command_arguments>]", COMMAND_PREFIX,
                        PORT_PREFIX));
    }

    public static void main(String[] argsArray) {
        final List<String> args = new ArrayList<>(Arrays.asList(argsArray));
        if (args.size() < 1) {
            usage();
            System.exit(EXIT_FAIL);
        } else if (args.size() == 1 && (args.get(0).equals("--help"))) {
            usage();
            System.exit(EXIT_SUCCESS);
        }

        final Optional<String> command = extractArg(args, COMMAND_PREFIX).map(arg -> arg.substring(COMMAND_PREFIX.length()));
        final Optional<Integer> port = NativeImageBuildServer.extractPort(args);

        if (port.isPresent() && command.isPresent()) {
            Signal.handle(new Signal("INT"), sig -> {
                sendRequest("abort", "image building interrupted by user (Ctrl-C).", port.get());
            });
            int exitCode = sendRequest(command.get(), String.join(" ", args), port.get());
            System.exit(exitCode);
        } else {
            usage();
            System.exit(EXIT_FAIL);
        }
    }

    private static int sendRequest(String command, String payload, int port) {
        Socket svmClient;
        OutputStreamWriter os;
        BufferedReader is;
        try {
            svmClient = new Socket("127.0.0.1", port);
            os = new OutputStreamWriter(svmClient.getOutputStream());
            is = new BufferedReader(new InputStreamReader(svmClient.getInputStream()));
        } catch (IOException e) {
            if (!ServerCommand.version.toString().equals(command)) {
                System.err.println("The image build server is not running on port " + port);
                System.err.println("Underlying exception: " + e);
                return EXIT_FAIL;
            } else {
                return EXIT_SUCCESS;
            }
        }

        try {
            SubstrateServerMessage.send(new SubstrateServerMessage(command, payload), os);
            switch (command) {
                case "version":
                    SubstrateServerMessage response = new Gson().fromJson(is.readLine(), SubstrateServerMessage.class);
                    System.out.println(response.payload);
                    break;
                default:

                    String line;
                    while ((line = is.readLine()) != null) {
                        SubstrateServerMessage serverCommand = new Gson().fromJson(line, SubstrateServerMessage.class);
                        switch (serverCommand.command) {
                            case o:
                                System.out.print(serverCommand.payload);
                                break;
                            case e:
                                System.err.print(serverCommand.payload);
                                break;
                            case s:
                                return Integer.valueOf(serverCommand.payload);
                            default:
                                throw new RuntimeException("Invalid command sent by the image build server: " + serverCommand.command);
                        }
                    }
            }
            os.close();
            is.close();
            svmClient.close();
        } catch (IOException e) {
            System.err.println("Could not stream data from the image build server. Underlying exception: " + e);
            return EXIT_FAIL;
        }
        return EXIT_SUCCESS;
    }
}
