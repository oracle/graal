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
package com.oracle.svm.hosted.image;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.options.Option;

import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.option.HostedOptionKey;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class CCLinkerInvocation implements LinkerInvocation {

    public static class Options {
        @Option(help = "Pass the provided raw option to the linker command that produces the final binary. The possible options are platform specific and passed through without any validation.")//
        public static final HostedOptionKey<String[]> NativeLinkerOption = new HostedOptionKey<>(null);
    }

    protected final List<String> additionalPreOptions = new ArrayList<>();
    protected String compilerCommand = "cc";
    protected final List<String> inputFilenames = new ArrayList<>();
    protected final List<String> rpaths = new ArrayList<>();
    protected final List<String> libpaths = new ArrayList<>();
    protected final List<String> libs = new ArrayList<>();
    protected final Map<ResolvedJavaMethod, String> symbolAliases = new HashMap<>();
    protected Path outputFile;
    protected AbstractBootImage.NativeImageKind outputKind;

    @Override
    public List<String> getInputFiles() {
        return Collections.unmodifiableList(inputFilenames);
    }

    @Override
    public void addInputFile(String filename) {
        inputFilenames.add(filename);
    }

    @Override
    public void addInputFile(int index, String filename) {
        inputFilenames.add(index, filename);
    }

    public AbstractBootImage.NativeImageKind getOutputKind() {
        return outputKind;
    }

    public void setOutputKind(AbstractBootImage.NativeImageKind k) {
        outputKind = k;
    }

    @Override
    public Map<ResolvedJavaMethod, String> getSymbolAliases() {
        return Collections.unmodifiableMap(symbolAliases);
    }

    @Override
    public void addSymbolAlias(ResolvedJavaMethod definition, String alias) {
        symbolAliases.put(definition, alias);
    }

    @Override
    public List<String> getLibPaths() {
        return Collections.unmodifiableList(libpaths);
    }

    @Override
    public void addLibPath(String libPath) {
        addLibPath(libpaths.size(), libPath);
    }

    @Override
    public void addLibPath(int index, String libPath) {
        if (!libPath.isEmpty()) {
            libpaths.add(index, libPath);
        }
    }

    @Override
    public List<String> getRPaths() {
        return Collections.unmodifiableList(rpaths);
    }

    @Override
    public void addRPath(String rPath) {
        addRPath(rpaths.size(), rPath);
    }

    @Override
    public void addRPath(int index, String rPath) {
        if (!rPath.isEmpty()) {
            rpaths.add(rPath);
        }
    }

    @Override
    public Path getOutputFile() {
        return outputFile;
    }

    @Override
    public void setOutputFile(Path out) {
        outputFile = out;
    }

    @Override
    public List<String> getLinkedLibraries() {
        return Collections.unmodifiableList(libs);
    }

    @Override
    public void addLinkedLibrary(String libname) {
        libs.add(libname);
    }

    @Override
    public void addLinkedLibrary(int index, String libname) {
        libs.add(index, libname);
    }

    @Override
    public String getCompilerCommand() {
        return compilerCommand;
    }

    @Override
    public void setCompilerCommand(String command) {
        compilerCommand = command;
    }

    protected abstract void addOneSymbolAliasOption(List<String> cmd, Map.Entry<ResolvedJavaMethod, String> ent);

    protected abstract void setOutputKind(List<String> cmd);

    @Override
    public List<String> getCommand() {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(compilerCommand);
        cmd.add("-v");
        cmd.add("-o");
        cmd.add(outputFile.toString());

        for (String opt : additionalPreOptions) {
            cmd.add(opt);
        }
        setOutputKind(cmd);
        for (Map.Entry<ResolvedJavaMethod, String> ent : symbolAliases.entrySet()) {
            addOneSymbolAliasOption(cmd, ent);
        }
        for (String libpath : libpaths) {
            cmd.add("-L" + libpath);
        }
        for (String rpath : rpaths) {
            cmd.add("-Wl,-rpath");
            cmd.add("-Wl," + rpath);
        }
        cmd.addAll(inputFilenames);
        for (String lib : libs) {
            if (lib.startsWith("-")) {
                cmd.add("-Wl," + lib.replace(" ", ","));
            } else {
                cmd.add("-l" + lib);
            }
        }

        if (Options.NativeLinkerOption.getValue() != null) {
            for (String nativeLinkerOption : Options.NativeLinkerOption.getValue()) {
                cmd.add(nativeLinkerOption);
            }
        }
        return cmd;
    }

    @Override
    public void addAdditionalPreOption(String option) {
        additionalPreOptions.add(option);
    }
}
