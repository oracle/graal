/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.graalvm.compiler.debug.DebugConfig;
import org.graalvm.compiler.debug.DebugConfigCustomizer;
import org.graalvm.compiler.debug.GraalDebugConfig;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.DumpPath;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintBinaryGraphPort;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintBinaryGraphs;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintGraphFileName;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintGraphHost;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.PrintXmlGraphPort;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.UniquePathUtilities;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(DebugConfigCustomizer.class)
public class TruffleTreeDebugConfigCustomizer implements DebugConfigCustomizer {

    @Override
    public void customize(DebugConfig config) {
        OptionValues options = config.getOptions();
        if (GraalDebugConfig.Options.PrintGraphFile.getValue(options)) {
            config.dumpHandlers().add(createFilePrinter(options));
        } else {
            config.dumpHandlers().add(createNetworkPrinter(options));
        }
    }

    private static Path getFilePrinterPath(OptionValues options) {
        // Construct the path to the file.
        return UniquePathUtilities.getPath(options, PrintGraphFileName, DumpPath, PrintBinaryGraphs.getValue(options) ? "bgv" : "gv.xml");
    }

    private static TruffleTreeDumpHandler createFilePrinter(OptionValues options) {
        Path path = getFilePrinterPath(options);
        return new TruffleTreeDumpHandler(() -> FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW), options);
    }

    private static TruffleTreeDumpHandler createNetworkPrinter(OptionValues options) {
        String host = PrintGraphHost.getValue(options);
        int port = PrintBinaryGraphs.getValue(options) ? PrintBinaryGraphPort.getValue(options) : PrintXmlGraphPort.getValue(options);
        return new TruffleTreeDumpHandler(() -> SocketChannel.open(new InetSocketAddress(host, port)), options);
    }

}
