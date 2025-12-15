/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.JNU;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(group = LibJava.class)
public final class Target_java_lang_ProcessImpl {
    @Substitution(hasReceiver = true)
    @TruffleBoundary
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static int forkAndExec(int mode, @JavaType(byte[].class) StaticObject helperpath,
                    @JavaType(byte[].class) StaticObject prog,
                    @JavaType(byte[].class) StaticObject argBlock, int argc,
                    @JavaType(byte[].class) StaticObject envBlock, int envc,
                    @JavaType(byte[].class) StaticObject dir,
                    @JavaType(int[].class) StaticObject fds,
                    boolean redirectErrorStream,
                    @Inject EspressoContext ctx,
                    @Inject EspressoLanguage lang,
                    @Inject TruffleIO io) {
        /*
         * In the end the fds array should hold the FileDescriptor to channels with which the parent
         * can access the input, output and error stream of the newly created process. In some cases
         * the parent already provides the fds.
         */
        ctx.getLibsState().checkCreateProcessAllowed();
        // unwrap everything
        @SuppressWarnings("unused")
        byte[] helperpathArr = helperpath.unwrap(lang);
        byte[] progArr = prog.unwrap(lang);
        byte[] argBlockArr = argBlock.unwrap(lang);
        byte[] envBlockArr = envBlock.unwrap(lang);
        byte[] dirArr = dir.unwrap(lang);
        byte[] fdsArr = fds.unwrap(lang);
        String[] command = new String[argc + 1];

        // set the command string array
        command[0] = JNU.getString(progArr, 0, progArr.length - 1);
        decodeCmdarray(argBlockArr, argc, command);
        TruffleProcessBuilder builder = ctx.getEnv().newProcessBuilder(command);

        // set environment
        Map<String, String> environment = decodeEnv(envBlockArr, envc, ctx);
        builder.environment(environment);

        // set directory
        String dirString = JNU.getString(dirArr, 0, dirArr.length - 1);
        TruffleFile dirTF = io.getPublicTruffleFileSafe(dirString);
        builder.directory(dirTF);

        // set fds
        if (fdsArr[0] == 0) {
            // we trust truffle to do the correct redirection from the parent to the child
            builder.redirectInput(ProcessHandler.Redirect.INHERIT);
            fdsArr[0] = -1;
        } else if (fdsArr[0] == -1) {
            /*
             * Here we would need to create a new stream and set it as standardInput with
             * builder.redirectInput. This is left unimplemented for the moment since it was not
             * used.
             */
            throw JavaSubstitution.unimplemented();

        } else {
            /*
             * In this case, we need to retrieve the channel associated with the fd. Then we need to
             * cast it somehow to an OutputStream and use createRedirectToStream. This is left
             * unimplemented for the moment since it was not used.
             */
            throw JavaSubstitution.unimplemented();
        }

        if (fdsArr[1] == 1) {
            builder.redirectOutput(ProcessHandler.Redirect.INHERIT);
            fdsArr[1] = -1;
        } else {
            throw JavaSubstitution.unimplemented();
        }
        builder.redirectErrorStream(redirectErrorStream);
        if (fdsArr[2] == 2) {
            builder.redirectError(ProcessHandler.Redirect.INHERIT);
            fdsArr[2] = -1;
        } else {
            throw JavaSubstitution.unimplemented();
        }
        Process p;
        try {
            p = builder.start();
        } catch (IOException e) {
            throw Throw.throwIOException(e, ctx);
        }
        return Math.toIntExact(p.pid());
    }

    @Substitution
    public static void init() {
        // nop
    }

    private static void decodeCmdarray(byte[] argBlock, int argc, String[] command) {
        int i = 0;
        int argIndex = 0;
        while (i < argBlock.length && argIndex < argc) {
            int start = i;

            while (i < argBlock.length && argBlock[i] != 0) {
                i++;
            }

            if (start < i) {
                String arg = JNU.getString(argBlock, start, i - start);
                command[argIndex + 1] = arg; // +1 because args[0] is the program path
                argIndex++;
            }
            i++; // skip the null-terminator
        }
    }

    private static Map<String, String> decodeEnv(byte[] envBlockArr, int envc, EspressoContext ctx) {
        Map<String, String> envMap = new HashMap<>();
        int i = 0;
        for (int j = 0; j < envc; j++) {
            int start = i;

            while (i < envBlockArr.length && envBlockArr[i] != 0) {
                i++;
            }

            String envStr = JNU.getString(envBlockArr, start, i - start);
            int equalsIndex = envStr.indexOf('=');
            if (equalsIndex != -1) {
                String key = envStr.substring(0, equalsIndex);
                String value = envStr.substring(equalsIndex + 1);
                envMap.put(key, value);
            } else {
                throw Throw.throwIOException("The byte array encoding for the Enviornment Map Entry didnt include =", ctx);
            }
            i++; // skip the null-terminator
        }
        return envMap;
    }

}
