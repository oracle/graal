/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server;

import java.io.IOException;
import java.nio.file.Path;

import com.oracle.svm.jdwp.bridge.nativebridge.NativeIsolate;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.VMRuntime;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.InterpreterUniverseImpl;
import com.oracle.svm.interpreter.metadata.serialization.SerializationContext;
import com.oracle.svm.interpreter.metadata.serialization.Serializers;
import com.oracle.svm.jdwp.bridge.DebugOptions;
import com.oracle.svm.jdwp.bridge.HSToNativeJDWPBridge;
import com.oracle.svm.jdwp.bridge.JDWPEventHandlerBridge;
import com.oracle.svm.jdwp.bridge.JDWPJNIConfig;
import com.oracle.svm.jdwp.server.impl.ServerJDWP;

@SuppressWarnings("unused")
public class JDWPServer implements JDWPEventHandlerBridge {

    public static final String DEBUGGER_HELP_MESSAGE = """
                                      Native Image JDWP Debugger
                                      --------------------------

                      (See the "VM Invocation Options" section of the JPDA
                       "Connection and Invocation Details" document for more information.)

                    jdwp usage: foobar -XX:JDWPOptions=[help]|[<option>=<value>, ...]

                    Option Name and Value            Description                       Default
                    ---------------------            -----------                       -------
                    suspend=y|n                      wait on startup?                  y
                    transport=<name>                 transport spec                    none
                    address=<listen/attach address>  transport spec                    ""
                    server=y|n                       listen for debugger?              n
                    timeout=<timeout value>          for listen/attach in milliseconds n

                    Examples
                    --------
                      - Using sockets connect to a debugger at a specific address:
                        foobar -XX:JDWPOptions=transport=dt_socket,address=localhost:8000 ...
                      - Using sockets listen for a debugger to attach:
                        foobar -XX:JDWPOptions=transport=dt_socket,server=y,suspend=y ...

                    Notes
                    -----
                      - A timeout value of 0 (the default) is no timeout.

                    """.replaceAll("\n", System.lineSeparator());

    private JDWPHandler jdwpHandler = null;

    /* Called via JNI. */
    @SuppressWarnings("unused")
    public static JDWPEventHandlerBridge createInstance() {
        /*
         * Unlike executables, shared libraries are not initialized properly on SVM. This ensures
         * that the JDWP server isolate is initialized and the startup hooks executed.
         *
         * Since the JDWP server is spawned in a startup hook, this allows lib:svmjdwp to also spawn
         * a debugger (if configured) e.g. and to be debugged by another isolate instance of itself.
         */
        if (ImageInfo.isSharedLibrary() && ImageInfo.inImageRuntimeCode()) {
            VMRuntime.initialize();
        }
        return new JDWPServer();
    }

    /*
     * Must be initialized at build-time to avoid including the serialization (writing) code, since
     * only deserialization (reading) is needed in both, resident and server code.
     */
    private static final SerializationContext.Builder DEFAULT_UNIVERSE_BUILDER = Serializers.newBuilderForInterpreterMetadata();

    @SuppressWarnings("restricted")
    @Override
    public void spawnServer(String jdwpOptions, String additionalOptions, long isolate, long initialThreadId, long jdwpBridgeHandle, String metadataHashString, String metadataPath, boolean tracing) {

        DebugOptions.Options options;
        try {
            options = DebugOptions.parse(jdwpOptions, additionalOptions, true, tracing);
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: JDWP option syntax error: " + jdwpOptions);
            System.exit(1);
            return;
        }

        if (options.help()) {
            System.out.println(DEBUGGER_HELP_MESSAGE);
            System.exit(0);
            return;
        }

        if ("jvm".equals(options.mode())) {
            VMError.guarantee(!ImageInfo.inImageRuntimeCode(), "JDWP server is not running on HotSpot");
            /*
             * No library is loaded here, this is merely a trick to make the current/application
             * class loader to link native methods against symbols on the process/default namespace.
             * This relies on the HotSpot mechanism used to link Java native methods when libraries
             * are statically linked. For this to work the executable must contain a dummy
             * JNI_OnLoad_DEFAULT_NAMESPACE symbol/method that returns a valid JNI version.
             *
             * Another attempted approach was to System.load("/path/to/current/executable"), this
             * works on Mac, but dlopen-ing the current executable fails on Linux with
             * "Cannot dynamically load position-independent executable".
             */
            System.loadLibrary("DEFAULT_NAMESPACE");
        } else {
            VMError.guarantee(ImageInfo.inImageRuntimeCode(), "JDWP server is not running on SubstrateVM");
        }

        try {
            ClassUtils.UNIVERSE = InterpreterUniverseImpl.loadFrom(DEFAULT_UNIVERSE_BUILDER, true, metadataHashString, Path.of(metadataPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Must be initialized after loading the universe/metadata.
        this.jdwpHandler = new JDWPHandler(initialThreadId);

        NativeIsolate nativeIsolate = NativeIsolate.forIsolateId(isolate, JDWPJNIConfig.getInstance());

        ServerJDWP.BRIDGE = HSToNativeJDWPBridge.createHSToNative(nativeIsolate, jdwpBridgeHandle);
        ServerJDWP.initLogging(options.tracing());

        jdwpHandler.doConnect(options);
    }

    @Override
    public void onEventAt(long threadId, long classId, byte typeTag, long methodId, int bci, byte resultTag, long resultPrimitiveOrId, int eventKindFlags) {
        jdwpHandler.getController().getEventListener().onEventAt(threadId, classId, typeTag, methodId, bci, resultTag, resultPrimitiveOrId, eventKindFlags);
    }

    @Override
    public void onThreadStart(long threadId) {
        jdwpHandler.getController().getEventListener().onThreadStart(threadId);
    }

    @Override
    public void onThreadDeath(long threadId) {
        jdwpHandler.getController().getEventListener().onThreadDeath(threadId);
    }

    @Override
    public void onVMDeath() {
        jdwpHandler.getController().getEventListener().onVMDeath();
    }
}
