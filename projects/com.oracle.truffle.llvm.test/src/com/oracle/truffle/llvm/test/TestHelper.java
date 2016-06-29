/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOption;
import com.oracle.truffle.llvm.tools.Clang;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.GCC;
import com.oracle.truffle.llvm.tools.LLC;
import com.oracle.truffle.llvm.tools.ProgrammingLanguage;
import com.oracle.truffle.llvm.tools.util.PathUtil;
import com.oracle.truffle.llvm.tools.util.ProcessUtil;
import com.oracle.truffle.llvm.tools.util.ProcessUtil.ProcessResult;

public class TestHelper {

    /**
     * Recursively collects files of the specified extensions starting from the given root folder.
     * Ignores folder of name {@link LLVMPaths#IGNORE_FOLDER_NAME}.
     *
     * @param folder the root folder
     * @return list of collected files matching the extension
     */
    public static List<File> collectFilesWithExtension(File folder, final ProgrammingLanguage... languages) {
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException(folder.getAbsolutePath() + " is not a folder!");
        }
        if (languages.length == 0) {
            throw new IllegalArgumentException("no extensions to collect!");
        }
        File[] files = folder.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {
                File fileWithDir = new File(dir, name);
                for (ProgrammingLanguage lang : languages) {
                    if (lang.isFile(fileWithDir)) {
                        return true;
                    }
                }
                return false;
            }
        });

        File[] subDirectories = folder.listFiles(new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory() && !pathname.getName().endsWith(LLVMPaths.IGNORE_FOLDER_NAME);
            }
        });
        List<File> collected = new ArrayList<>();
        collected.addAll(Arrays.asList(files));
        List<List<File>> lists = Arrays.asList(subDirectories).parallelStream().map(f -> collectFilesWithExtension(f, languages)).collect(Collectors.toList());
        for (List<File> list : lists) {
            collected.addAll(list);
        }
        return collected;
    }

    public static TestCaseFiles compileToLLVMIRWithClang(File toBeCompiled, File destinationFile, File expectedFile, Set<TestCaseFlag> flags) {
        return compileToLLVMIRWithClang(toBeCompiled, destinationFile, expectedFile, flags, ClangOptions.builder());
    }

    public static TestCaseFiles compileToLLVMIRWithClang(File toBeCompiled, File destinationFile, File expectedFile, Set<TestCaseFlag> flags, ClangOptions builder) {
        Clang.compileToLLVMIR(toBeCompiled, destinationFile, builder);
        return TestCaseFiles.createFromCompiledFile(toBeCompiled, destinationFile, expectedFile, flags);
    }

    public static TestCaseFiles compileToLLVMIRWithClang(File toBeCompiled, File destinationFile) {
        Clang.compileToLLVMIR(toBeCompiled, destinationFile, ClangOptions.builder());
        return TestCaseFiles.createFromCompiledFile(toBeCompiled, destinationFile, Collections.emptySet());
    }

    public static TestCaseFiles compileToLLVMIRWithClang(File toBeCompiled, File destinationFile, Set<TestCaseFlag> flags, ClangOptions builder) {
        Clang.compileToLLVMIR(toBeCompiled, destinationFile, builder);
        return TestCaseFiles.createFromCompiledFile(toBeCompiled, destinationFile, flags);
    }

    public static TestCaseFiles compileToLLVMIRWithGCC(File toBeCompiled, File destinationFile) {
        GCC.compileToLLVMIR(toBeCompiled, destinationFile);
        return TestCaseFiles.createFromCompiledFile(toBeCompiled, destinationFile, Collections.emptySet());
    }

    public static TestCaseFiles compileToLLVMIRWithGCC(File toBeCompiled, File destinationFile, Set<TestCaseFlag> flags) {
        GCC.compileToLLVMIR(toBeCompiled, destinationFile);
        return TestCaseFiles.createFromCompiledFile(toBeCompiled, destinationFile, flags);
    }

    public static File getTempLLFile(File toBeCompiled, String optionName) {
        String absolutePathToFileName = absolutePathToFileName(toBeCompiled);
        String outputFileName = PathUtil.replaceExtension(absolutePathToFileName, "." + optionName + Constants.TMP_EXTENSION + Constants.LLVM_BITFILE_EXTENSION);
        File destinationFile = new File(LLVMPaths.TEMP_DIRECTORY, outputFileName);
        return destinationFile;
    }

    private static String absolutePathToFileName(File file) {
        String nameString = file.getAbsolutePath().replace(File.separatorChar, '.');
        if (nameString.length() > 100) {
            return nameString.substring(nameString.length() - 100);
        } else {
            return nameString;
        }
    }

    /**
     * Executes the bitcode file that is beforehand compiled by the native LLVM compiler.
     *
     * @param bitcodeFile the bitcode file to be compiled
     */
    public static ProcessResult executeLLVMBinary(File bitcodeFile) {
        try {
            File objectFile = File.createTempFile(absolutePathToFileName(bitcodeFile), ".o");
            File executable = File.createTempFile(absolutePathToFileName(bitcodeFile), ".out");
            LLC.compileBitCodeToObjectFile(bitcodeFile, objectFile);
            GCC.compileObjectToMachineCode(objectFile, executable);
            String command = executable.getAbsolutePath();
            return ProcessUtil.executeNativeCommand(command);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Process launchRemoteTruffle() {
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");
            String jvmciClasspath = System.getProperty("jvmci.class.path.append");
            String className = RemoteLLVMTester.class.getCanonicalName();
            String bootClassPath = LLVMBaseOptionFacade.getRemoteTestBootClassPath();
            String debugOption = asOption(LLVMBaseOption.DEBUG.getKey(), "false");
            String projectRootOption = asOption(LLVMBaseOption.PROJECT_ROOT.getKey(), LLVMBaseOptionFacade.getProjectRoot());
            String options = debugOption + " " + projectRootOption;
            String command = javaBin + " -cp " + classpath + " " + bootClassPath + " -Djvmci.class.path.append=" + jvmciClasspath + " " + options + " " + className;
            Process process = Runtime.getRuntime().exec(command);
            return process;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static String asOption(String key, String value) {
        return "-D" + key + "=" + value;
    }

    public static List<File> getFiles(File directory, boolean recursive) {
        if (!directory.isDirectory()) {
            throw new AssertionError(directory + " is not a directory!");
        }
        List<File> files = new ArrayList<>();
        for (File f : directory.listFiles()) {
            if (f.isDirectory() && recursive) {
                files.addAll(getFiles(f, recursive));
            } else {
                files.add(f);
            }
        }
        return files;
    }

    public static void removeFilesTestCases(List<TestCaseFiles[]> collectedSpecificationFiles, List<TestCaseFiles[]> filesRecursively) {
        for (TestCaseFiles[] alreadyCanExecute : collectedSpecificationFiles) {
            for (TestCaseFiles[] allFilesFile : filesRecursively) {
                if (alreadyCanExecute[0].getOriginalFile().equals(allFilesFile[0].getOriginalFile())) {
                    boolean removed = filesRecursively.remove(allFilesFile);
                    if (!removed) {
                        throw new AssertionError();
                    }
                    break;
                }
            }
        }
    }

    public static void removeFilesFromTestCases(List<File> excludedFiles, List<TestCaseFiles[]> filesRecursively) {
        for (File excludedFile : excludedFiles) {
            List<TestCaseFiles[]> filesToRemove = new ArrayList<>();
            for (TestCaseFiles[] allFilesFile : filesRecursively) {
                if (excludedFile.equals(allFilesFile[0].getOriginalFile())) {
                    filesToRemove.add(allFilesFile);
                }
            }
            for (TestCaseFiles[] remove : filesToRemove) {
                filesRecursively.remove(remove);
            }
        }
    }

}
