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

#define VM_ARG_PREFIX "--vm."
#define VM_CP_ARG_PREFIX "--vm.cp="
#define VM_CLASSPATH_ARG_PREFIX "--vm.classpath="
#define VM_ARG_OFFSET (sizeof(VM_ARG_PREFIX)-1)
#define VM_CP_ARG_OFFSET (sizeof(VM_CP_ARG_PREFIX)-1)
#define VM_CLASSPATH_ARG_OFFSET (sizeof(VM_CLASSPATH_ARG_PREFIX)-1)
#define IS_VM_ARG(ARG) (strncmp(ARG, VM_ARG_PREFIX, VM_ARG_OFFSET) == 0)
#define IS_VM_CP_ARG(ARG) (strncmp(ARG, VM_CP_ARG_PREFIX, VM_CP_ARG_OFFSET) == 0)
#define IS_VM_CLASSPATH_ARG(ARG) (strncmp(ARG, VM_CLASSPATH_ARG_PREFIX, VM_CLASSPATH_ARG_OFFSET) == 0)

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
bool debug = false;
bool relaunch = false;

// platform-independent environment setter
int setenv(std::string key, std::string value) {
    #if defined (_WIN32)
        if(_putenv_s(key.c_str(), value.c_str()) == -1) {
            std::cerr << "_putenv_s failed" << std::endl;
            return -1;
        }
    #else
        if (setenv(key.c_str(), value.c_str(), 1) == -1) {
            perror("setenv failed");
            return -1;
        }
    #endif
    return 0;
}

// get the path to the current executable
std::string exe_path() {
    #if defined (__linux__)
        char *realPath = realpath("/proc/self/exe", NULL);
    #elif defined (__APPLE__)
        char path[PATH_MAX];
        uint32_t path_len = PATH_MAX;
        _NSGetExecutablePath(path, &path_len);
        char *realPath = realpath(path, NULL);
    #elif defined (_WIN32)
        char *realPath = (char *)malloc(_MAX_PATH);
        GetModuleFileNameA(NULL, realPath, _MAX_PATH);
    #endif
    std::string p(realPath);
    free(realPath);
    return p;
}

// get the directory of the current executable
std::string exe_directory() {
    char *path = strdup(exe_path().c_str());
    #if defined (_WIN32)
        // get the directory part
        char drive[_MAX_DRIVE];
        char dir[_MAX_DIR];
        char result[_MAX_DRIVE + _MAX_DIR];
        _splitpath_s(path, drive, _MAX_DRIVE, dir, _MAX_DIR, NULL, 0, NULL, 0);
        _makepath_s(result, _MAX_PATH, drive, dir, NULL, NULL);
    #else
        char *result = dirname(path);
    #endif
    std::string exeDir(result);
    free(path);
    return exeDir;
}

// load the language library (either native library or libjvm) and return a pointer to the JNI_CreateJavaVM function
CreateJVM loadliblang(std::string liblangPath) {
    if (debug) {
        std::cout << "Loading library " << liblangPath << std::endl;
    }
#if defined (__linux__) || defined (__APPLE__)
        void* jvmHandle = dlopen(liblangPath.c_str(), RTLD_NOW);
        if (jvmHandle != NULL) {
            return (CreateJVM) dlsym(jvmHandle, "JNI_CreateJavaVM");
        }
#else
        HMODULE jvmHandle = LoadLibraryA(liblangPath.c_str());
        if (jvmHandle != NULL) {
            return (CreateJVM) GetProcAddress(jvmHandle, "JNI_CreateJavaVM");
        }
#endif
    return NULL;
}

std::string liblang_path(std::string exeDir) {
    std::stringstream liblangPath;
    liblangPath << exeDir << DIR_SEP_STR << LIBLANG_RELPATH_STR;
    return liblangPath.str();
}

// parse the VM arguments that should be passed to JNI_CreateJavaVM
void parse_vm_options(int argc, char **argv, std::string exeDir, JavaVMInitArgs *vmInitArgs) {
    std::vector<std::string> vmArgs;

    // check if vm args have been set on relaunch already
    int vmArgCount = 0;
    char *vmArgInfo = getenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS");
    if (vmArgInfo != NULL) {
        relaunch = true;
        vmArgCount = strtol(vmArgInfo, NULL, 10);
        // clear the env variable
        setenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS", "");
    }

    // set optional vm args from LanguageLibraryConfig.option_vars
    #ifdef LAUNCHER_OPTION_VARS
        const char *launcherOptionVars[] = LAUNCHER_OPTION_VARS;
    #endif

    // in pure JVM mode, set org.graalvm.launcher.class system property
    #ifdef JVM
        vmArgs.push_back("-Dorg.graalvm.launcher.class=" LAUNCHER_CLASS_STR);
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

    // handle CLI arguments
    if (!vmArgInfo) {
        for (int i = 0; i < argc; i++) {
            if (IS_VM_CP_ARG(argv[i])) {
                cp << CP_SEP_STR << argv[i]+VM_CP_ARG_OFFSET;
            } else if (IS_VM_CLASSPATH_ARG(argv[i])) {
                cp << CP_SEP_STR << argv[i]+VM_CLASSPATH_ARG_OFFSET;
            } else if (IS_VM_ARG(argv[i])) {
                std::stringstream opt;
                opt << '-' << argv[i]+VM_ARG_OFFSET;
                vmArgs.push_back(opt.str());
            }
        }
    }

    // handle relaunch arguments
    else {
        if (debug) {
            std::cout << "Relaunch environment variable detected" << std::endl;
        }
        for (int i = 0; i < vmArgCount; i++) {
            std::stringstream ss;
            ss << "GRAALVM_LANGUAGE_LAUNCHER_VMARGS_" << i;
            std::string envKey = ss.str();
            char *cur = getenv(envKey.c_str());
            if (!cur) {
                std::cerr << "VM arguments specified: " << vmArgCount << " but argument " << i << "missing" << std::endl;
                break;
            }
            if (IS_VM_CP_ARG(cur)) {
                cp << CP_SEP_STR << cur+VM_CP_ARG_OFFSET;
            } else if (IS_VM_CLASSPATH_ARG(cur)) {
                cp << CP_SEP_STR << cur+VM_CLASSPATH_ARG_OFFSET;
            } else if (IS_VM_ARG(cur)) {
                std::stringstream opt;
                opt << '-' << cur+VM_ARG_OFFSET;
                vmArgs.push_back(opt.str());
            }
            // clean up env variable
            setenv(envKey, "");
        }
    }

    // handle optional vm args from LanguageLibraryConfig.option_vars
    #ifdef LAUNCHER_OPTION_VARS
    for (int i = 0; i < sizeof(launcherOptionVars)/sizeof(char*); i++) {
        if (IS_VM_CP_ARG(launcherOptionVars[i])) {
            cp << CP_SEP_STR << launcherOptionVars[i]+VM_CP_ARG_OFFSET;
        } else if (IS_VM_CLASSPATH_ARG(launcherOptionVars[i])) {
            cp << CP_SEP_STR << launcherOptionVars[i]+VM_CLASSPATH_ARG_OFFSET;
        } else if (IS_VM_ARG(launcherOptionVars[i])) {
            std::stringstream opt;
            opt << '-' << launcherOptionVars[i]+VM_ARG_OFFSET;
            vmArgs.push_back(opt.str());
        }
    }
    #endif

    #ifdef JVM
        // set classpath argument
        vmArgs.push_back(cp.str());
    #endif

    vmInitArgs->options = new JavaVMOption[vmArgs.size()];;
    vmInitArgs->nOptions = vmArgs.size();
    JavaVMOption *curOpt = vmInitArgs->options;
    for(const auto& arg: vmArgs) {
        if (debug) {
            std::cout << "Setting VM argument " << arg << std::endl;
        }
        curOpt->optionString = strdup(arg.c_str());
        curOpt++;
    }
}

int main(int argc, char *argv[]) {
    debug = (getenv("VERBOSE_GRAALVM_LAUNCHERS") != NULL);
    std::string exeDir = exe_directory();
    std::string libPath;
    if (char *libOverridePath = getenv("GRAALVM_LAUNCHER_LIBRARY")) {
        libPath = std::string(libOverridePath);
    } else {
        libPath = liblang_path(exeDir);
    }
    CreateJVM createJVM = loadliblang(libPath);
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
    vmInitArgs.ignoreUnrecognized = true;

    int res = createJVM(&jvm, (void**)&env, &vmInitArgs);
    if (res != JNI_OK) {
        std::cerr << "Creation of the JVM failed." << std::endl;
        return -1;
    }

    // free the allocated vm arguments
    for (int i = 0; i < vmInitArgs.nOptions; i++) {
        free(vmInitArgs.options[i].optionString);
    }
    delete vmInitArgs.options;

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
    jmethodID runLauncherMid = env->GetStaticMethodID(launcherClass, "runLauncher", "([[BIJZ)V");
    if (runLauncherMid == NULL) {
        std::cerr << "Launcher entry point not found." << std::endl;
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jfieldID vmArgsFid = env->GetFieldID(relaunchExceptionClass, "vmArgs", "[Ljava/lang/String;");
    if (vmArgsFid == NULL) {
        std::cerr << "RelaunchException vm args field not found." << std::endl;
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
    env->CallStaticVoidMethod(launcherClass, runLauncherMid, args, argc_native, (long)argv_native, relaunch);
    jthrowable t = env->ExceptionOccurred();
    if (t) {
        if (env->IsInstanceOf(t, relaunchExceptionClass)) {
            if (debug) {
                std::cout << "Relaunch exception has been thrown" << std::endl;
            }
            env->ExceptionClear();
            // read correct VM arguments from exception
            jobjectArray vmArgs = (jobjectArray)env->GetObjectField(t, vmArgsFid);
            if (env->ExceptionCheck()) {
                std::cerr << "Error in GetObjectField:" << std::endl;
                env->ExceptionDescribe();
                return -1;
            }
            jint vmArgCount = env->GetArrayLength(vmArgs);
            if (env->ExceptionCheck()) {
                std::cerr << "Error in GetArrayLength:" << std::endl;
                env->ExceptionDescribe();
                return -1;
            }
            if (debug) {
                std::cout << "Relaunch VM arguments read: " << vmArgCount << std::endl;
            }
            std::stringstream ss;
            ss << vmArgCount;
            if (setenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS", ss.str()) == -1) {
                return -1;
            }
            for (int i = 0; i < vmArgCount; i++) {
                jstring vmArgString = (jstring)env->GetObjectArrayElement(vmArgs, i);
                if (env->ExceptionCheck()) {
                    std::cerr << "Error in GetObjectArrayElement:" << std::endl;
                    env->ExceptionDescribe();
                    return -1;
                }    
                const char *vmArg = env->GetStringUTFChars(vmArgString, NULL);
                if (env->ExceptionCheck()) {
                    std::cerr << "Error in GetStringUTFChars:" << std::endl;
                    env->ExceptionDescribe();
                    return -1;
                }
                // set environment variables
                std::stringstream key;
                key << "GRAALVM_LANGUAGE_LAUNCHER_VMARGS_" << i;
                std::string arg(vmArg);
                if (setenv(key.str(), arg) == -1) {
                    return -1;
                }
            }
            // relaunch with correct VM arguments
            std::string path = exe_path();
            execve(path.c_str(), argv_native, environ);
            // if we reach here, execve failed for sure
            perror("execve failed");
            return -1;
        }
        env->ExceptionDescribe();
        return -1;
    }
	return 0;
}
