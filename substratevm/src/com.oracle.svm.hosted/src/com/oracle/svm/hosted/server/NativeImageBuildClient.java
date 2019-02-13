/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.server;

import static com.oracle.svm.hosted.server.NativeImageBuildServer.PORT_PREFIX;
import static com.oracle.svm.hosted.server.NativeImageBuildServer.extractArg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.oracle.svm.hosted.server.SubstrateServerMessage.ServerCommand;

public class NativeImageBuildClient {

    private static final String COMMAND_PREFIX = "-command=";
    private static final int EXIT_FAIL = -1;
    public static final int EXIT_SUCCESS = 0;

    private static void usage(Consumer<String> out) {
        out.accept("Usage:");
        out.accept(String.format("  java -cp <svm_jar_path> " + NativeImageBuildClient.class.getName() + " %s<command> [%s<port_number>] [<command_arguments>]", COMMAND_PREFIX,
                        PORT_PREFIX));
    }

    public static int run(String[] argsArray, Consumer<byte[]> out, Consumer<byte[]> err) {
        Consumer<String> outln = s -> out.accept((s + "\n").getBytes());
        final List<String> args = new ArrayList<>(Arrays.asList(argsArray));
        if (args.size() < 1) {
            usage(outln);
            return EXIT_FAIL;
        } else if (args.size() == 1 && (args.get(0).equals("--help"))) {
            usage(outln);
            return EXIT_SUCCESS;
        }

        final Optional<String> command = extractArg(args, COMMAND_PREFIX).map(arg -> arg.substring(COMMAND_PREFIX.length()));
        final Optional<Integer> port = NativeImageBuildServer.extractPort(args);

        if (port.isPresent() && command.isPresent()) {
            ServerCommand serverCommand = ServerCommand.valueOf(command.get());
            return sendRequest(serverCommand, String.join(" ", args).getBytes(), port.get(), out, err);
        } else {
            usage(outln);
            return EXIT_FAIL;
        }
    }

    public static int sendRequest(ServerCommand command, byte[] payload, int port, Consumer<byte[]> out, Consumer<byte[]> err) {
        Consumer<String> outln = s -> out.accept((s + "\n").getBytes());
        Consumer<String> errln = s -> err.accept((s + "\n").getBytes());

        try (
                        Socket svmClient = new Socket((String) null, port);
                        DataOutputStream os = new DataOutputStream(svmClient.getOutputStream());
                        DataInputStream is = new DataInputStream(svmClient.getInputStream())) {

            SubstrateServerMessage.send(new SubstrateServerMessage(command, payload), os);
            if (ServerCommand.GET_VERSION.equals(command)) {
                SubstrateServerMessage response = SubstrateServerMessage.receive(is);
                if (response != null) {
                    outln.accept(new String(response.payload));
                }
            } else {

                SubstrateServerMessage serverCommand;
                while ((serverCommand = SubstrateServerMessage.receive(is)) != null) {
                    Consumer<byte[]> selectedConsumer;
                    switch (serverCommand.command) {
                        case WRITE_OUT:
                            selectedConsumer = out;
                            break;
                        case WRITE_ERR:
                            selectedConsumer = err;
                            break;
                        case SEND_STATUS:
                            /* Exit with exit status sent by server */
                            return ByteBuffer.wrap(serverCommand.payload).getInt();
                        default:
                            throw new RuntimeException("Invalid command sent by the image build server: " + serverCommand.command);
                    }
                    if (selectedConsumer != null) {
                        selectedConsumer.accept(serverCommand.payload);
                    }
                }
                /* Report failure if communication does not end with ExitStatus */
                return EXIT_FAIL;
            }
        } catch (IOException e) {
            if (!ServerCommand.GET_VERSION.equals(command)) {
                errln.accept("Could not connect to image build server running on port " + port);
                errln.accept("Underlying exception: " + e);
            }
            return EXIT_FAIL;
        }
        return EXIT_SUCCESS;
    }

    public static void main(String[] argsArray) {
        System.exit(run(argsArray, System.out::print, System.err::print));
    }
}
