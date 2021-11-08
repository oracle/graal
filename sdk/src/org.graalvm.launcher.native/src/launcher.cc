/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <jni.h>
#include <string.h>
#include <string>
#include <iostream>
#include <sstream>
#include <vector>

#define QUOTE(name) #name
#define STR(macro) QUOTE(macro)

#ifdef JVM
    #ifndef LAUNCHER_CLASS
        #error launcher class undefined
    #endif
    #define LAUNCHER_CLASS_STR STR(LAUNCHER_CLASS)
    #ifndef LAUNCHER_CLASSPATH
        #error launcher classpath undefined
    #endif
#endif

#ifndef LIBLANG_RELPATH
    #error path to native library undefined
#endif

#ifndef DIR_SEP
    #error directory separator undefined
#endif

#ifndef CP_SEP
    #error class path separator undefined
#endif

#define LIBLANG_RELPATH_STR STR(LIBLANG_RELPATH)
#define DIR_SEP_STR STR(DIR_SEP)
#define CP_SEP_STR STR(CP_SEP)

#define VM_ARG_OFFSET 5
#define VM_CP_ARG_OFFSET 8
#define VM_CLASSPATH_ARG_OFFSET 15
#define IS_VM_ARG(ARG) (strncmp(ARG, "--vm.", VM_ARG_OFFSET) == 0)
#define IS_VM_CP_ARG(ARG) (strncmp(ARG, "--vm.cp=", VM_CP_ARG_OFFSET) == 0)
#define IS_VM_CLASSPATH_ARG(ARG) (strncmp(ARG, "--vm.classpath=", VM_CLASSPATH_ARG_OFFSET) == 0)

#if defined (__linux__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <limits.h>
    #include <unistd.h>
    #include <errno.h>
#elif defined (__APPLE__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <unistd.h>
    #include <errno.h>
    #include <mach-o/dyld.h>
    #include <sys/syslimits.h>
#elif defined (_WIN32)
    #include <windows.h>
    #include <libloaderapi.h>
    #include <cstdlib>
    #include <process.h>
    #include <errno.h>
#else
    #error platform not supported or undefined
#endif

typedef jint(*CreateJVM)(JavaVM **, void **, void *);
extern char **environ;

char *exe_path() {
    #if defined (__linux__)
        return realpath("/proc/self/exe", NULL);
    #elif defined (__APPLE__)
        char path[PATH_MAX];
        uint32_t path_len = PATH_MAX;
        _NSGetExecutablePath(path, &path_len);
        return realpath(path, NULL);
    #elif defined (_WIN32)
        char *path = (char *)malloc(_MAX_PATH);
        GetModuleFileNameA(NULL, path, _MAX_PATH);
        return path;
    #endif
}

char *exe_directory() {
    #if defined (__linux__)
        return dirname(exe_path());
    #elif defined (__APPLE__)
        return dirname(exe_path());
    #elif defined (_WIN32)
        char *path = exe_path();
        // get the directory part
        char drive[_MAX_DRIVE];
        char dir[_MAX_DIR];
        _splitpath_s(path, drive, _MAX_DRIVE, dir, _MAX_DIR, NULL, 0, NULL, 0);
        _makepath_s(path, _MAX_PATH, drive, dir, NULL, NULL);
        return path;
    #endif
}

CreateJVM loadliblang(char *exe_dir) {
        int size = strlen(exe_dir) + sizeof(DIR_SEP_STR) + sizeof(LIBLANG_RELPATH_STR) + 1;
        char *liblang_path = (char*)malloc(size);
        strcpy(liblang_path, exe_dir);
        strcat(liblang_path, DIR_SEP_STR);
        strcat(liblang_path, LIBLANG_RELPATH_STR);
#if defined (__linux__) || defined (__APPLE__)
        void* jvm_handle = dlopen(liblang_path, RTLD_NOW);
        if (jvm_handle != NULL) {
            return (CreateJVM) dlsym(jvm_handle, "JNI_CreateJavaVM");
        }
#else
        HMODULE jvm_handle = LoadLibraryA(liblang_path);
        if (jvm_handle != NULL) {
            return (CreateJVM) GetProcAddress(jvm_handle, "JNI_CreateJavaVM");
        }
#endif
    return NULL;
}

void parse_vm_options(int argc, char **argv, char *exeDir, JavaVMInitArgs *vmInitArgs) {
    // check if vm arg indices have been set on relaunch already
    bool relaunch = false;
    std::vector<bool> vmArgIndices;
    if (char *vmArgInfo = getenv("TRUFFLE_LAUNCHER_VMARGS")) {
        relaunch = true;
        char *cur = vmArgInfo;
        int len = strlen(vmArgInfo);
        while (cur < vmArgInfo + len) {
            long l = strtol(cur, &cur, 10);
            if (*cur = ':') {
                cur++;
            } else {
                break;
            }
            if (l > vmArgIndices.size()) {
                vmArgIndices.reserve(l + 1);
            }
            vmArgIndices[l] = true;
        }
    }

    // set optional vm args from LanguageLibraryConfig.option_vars
    int launcherOptionCount = 0;
    #ifdef LAUNCHER_OPTION_VARS
        const char *launcherOptionVars[] = LAUNCHER_OPTION_VARS;
        launcherOptionCount = sizeof(launcherOptionVars) / sizeof(*launcherOptionVars);
    #endif

    // allocate option array - overapproximate the number of options (argc max + optional option_vars) to avoid iterating argv twice
    vmInitArgs->options = (JavaVMOption *)malloc((argc + launcherOptionCount) * sizeof(JavaVMOption));
    JavaVMOption *curOpt = vmInitArgs->options;

    // in pure JVM mode, set org.graalvm.launcher.class system property
    #ifdef JVM
        curOpt->optionString = "-Dorg.graalvm.launcher.class=" LAUNCHER_CLASS_STR;
        curOpt++;
        vmInitArgs->nOptions++;
    #endif

    // construct classpath
    std::stringstream cp;
    cp << "-Djava.class.path=";
    #ifdef JVM
        // add the launcher classpath
        const char *launcherCpEntries[] = LAUNCHER_CLASSPATH;
        int launcherCpCnt = sizeof(launcherCpEntries) / sizeof(*launcherCpEntries);
        for (int i = 0; i < launcherCpCnt; i++) {
            cp << exeDir << DIR_SEP_STR << launcherCpEntries[i];
            if (i < launcherCpCnt-1) {
                cp << CP_SEP_STR;
            }
        }
    #endif

    // handle vm arguments and user classpath
    for (int i = 0; i < argc; i++) {
        if (relaunch && i < vmArgIndices.size() && !vmArgIndices[i]) {
            continue;
        }
        if (IS_VM_CP_ARG(argv[i])) {
            cp << CP_SEP_STR << argv[i]+VM_CP_ARG_OFFSET;
        } else if (IS_VM_CLASSPATH_ARG(argv[i])) {
            cp << CP_SEP_STR << argv[i]+VM_CLASSPATH_ARG_OFFSET;
        } else if (IS_VM_ARG(argv[i])) {
            std::stringstream opt;
            opt << '-' << argv[i]+VM_ARG_OFFSET;
            curOpt->optionString = strdup(opt.str().c_str());
            curOpt++;
            vmInitArgs->nOptions++;
        }
    }

    // handle optional vm args from LanguageLibraryConfig.option_vars
    #ifdef LAUNCHER_OPTION_VARS
    for (int i = 0; i < launcherOptionCount; i++) {
        if (IS_VM_CP_ARG(launcherOptionVars[i])) {
            cp << CP_SEP_STR << launcherOptionVars[i]+VM_CP_ARG_OFFSET;
        } else if (IS_VM_CLASSPATH_ARG(launcherOptionVars[i])) {
            cp << CP_SEP_STR << launcherOptionVars[i]+VM_CLASSPATH_ARG_OFFSET;
        } else if (IS_VM_ARG(launcherOptionVars[i])) {
            std::stringstream opt;
            opt << '-' << launcherOptionVars[i]+VM_ARG_OFFSET;
            curOpt->optionString = strdup(opt.str().c_str());
            curOpt++;
            vmInitArgs->nOptions++;
        }
    }
    #endif

    // set classpath argument
    curOpt->optionString = strdup(cp.str().c_str());
    curOpt++;
    vmInitArgs->nOptions++;
}

int main(int argc, char *argv[]) {
    char *exeDir = exe_directory();
    CreateJVM createJVM = loadliblang(exeDir);
    if (!createJVM) {
        std::cerr << "Could not load language library." << std::endl;
        return -1;
    }
    JavaVM *jvm;
    JNIEnv *env;
    JavaVMInitArgs vmInitArgs;
    vmInitArgs.nOptions = 0;
    parse_vm_options(argc, argv, exeDir, &vmInitArgs);
    vmInitArgs.version = JNI_VERSION_1_8;
    vmInitArgs.ignoreUnrecognized = false;

    int res = createJVM(&jvm, (void**)&env, &vmInitArgs);
    if (res != JNI_OK) {
        std::cerr << "Creation of the JVM failed." << std::endl;
        return -1;
    }

    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == NULL) {
        std::cerr << "Byte array class not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jclass relaunchExceptionClass = env->FindClass("org/graalvm/launcher/AbstractLanguageLauncher$RelaunchException");
    if (relaunchExceptionClass == NULL) {
        std::cerr << "RelaunchException class not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jclass launcherClass = env->FindClass("org/graalvm/launcher/AbstractLanguageLauncher");
    if (launcherClass == NULL) {
        std::cerr << "Launcher class not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jmethodID runLauncherMid = env->GetStaticMethodID(launcherClass, "runLauncher", "([[BIJ)V");
    if (runLauncherMid == NULL) {
        std::cerr << "Launcher entry point not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jfieldID vmArgsFid = env->GetStaticFieldID(launcherClass, "vmArgIndices", "[Z");
    if (vmArgsFid == NULL) {
        std::cerr << "Launcher vm args field not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }

    // backup native args
    char** argv_native = argv;
    int argc_native = argc;

    argv++;
    argc--;

    // create args string array
    jobjectArray args = env->NewObjectArray(argc, byteArrayClass, NULL);
    for (int i = 0; i < argc; i++) {
        int arraySize = strlen(argv[i]);
        jbyteArray arg = env->NewByteArray(arraySize);
        env->SetByteArrayRegion(arg, 0, arraySize, (jbyte *)(argv[i]));
        if (env->ExceptionCheck()) {
            std::cerr << "Error in SetByteArrayRegion:" << std::endl;
            env->ExceptionDescribe();
            return -1;
        }
        env->SetObjectArrayElement(args, i, arg);
        if (env->ExceptionCheck()) {
            std::cerr << "Error in SetObjectArrayElement:" << std::endl;
            env->ExceptionDescribe();
            return -1;
        }
    }

    // invoke launcher entry point
    env->CallStaticVoidMethod(launcherClass, runLauncherMid, args, argc_native, (long)argv_native);
    jthrowable t = env->ExceptionOccurred();
    if (t) {
        if (env->IsInstanceOf(t, relaunchExceptionClass)) {
            env->ExceptionClear();
            // read correct VM arguments from launcher object
            jbooleanArray vmArgs = (jbooleanArray)env->GetStaticObjectField(launcherClass, vmArgsFid);
            if (env->ExceptionCheck()) {
                std::cerr << "Error in GetStaticObjectField:" << std::endl;
                env->ExceptionDescribe();
                return -1;
            }
            jboolean *vmArgElements = env->GetBooleanArrayElements(vmArgs, NULL);
            if (env->ExceptionCheck()) {
                std::cerr << "Error in GetBooleanArrayElements:" << std::endl;
                env->ExceptionDescribe();
                return -1;
            }

            // set environment variable
            std::stringstream vmArgInfo;
            bool first = true;
            for (int i = 0; i < argc; i++) {
                if (vmArgElements[i]) {
                    if (first) {
                        first = false;
                    } else {
                        vmArgInfo << ':';
                    }
                    vmArgInfo << i+1;
                }
            }
            if (setenv("TRUFFLE_LAUNCHER_VMARGS", strdup(vmArgInfo.str().c_str()), 1) == -1) {
                perror("setenv failed");
                return -1;
            }
            // relaunch with correct VM arguments
            const char *path = exe_path();
            execve(path, argv_native, environ);
            // if we reach here, execve failed for sure
            perror("execve failed");
            return -1;
        }
        env->ExceptionDescribe();
        return -1;
    }
	return 0;
}
