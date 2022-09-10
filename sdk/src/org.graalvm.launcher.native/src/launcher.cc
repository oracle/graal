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

#ifndef LAUNCHER_CLASS
    #error launcher class undefined
#endif
#define LAUNCHER_CLASS_STR STR(LAUNCHER_CLASS)
#ifndef LAUNCHER_CLASSPATH
    #error launcher classpath undefined
#endif

#ifndef LIBLANG_RELPATH
    #error path to native library undefined
#endif

#ifndef LIBJVM_RELPATH
    #error path to jvm library undefined
#endif

#ifndef DIR_SEP
    #error directory separator undefined
#endif

#ifndef CP_SEP
    #error class path separator undefined
#endif

#define LIBLANG_RELPATH_STR STR(LIBLANG_RELPATH)
#define LIBJVM_RELPATH_STR STR(LIBJVM_RELPATH)
#define DIR_SEP_STR STR(DIR_SEP)
#define CP_SEP_STR STR(CP_SEP)

#define VM_ARG_PREFIX "--vm."
#define VM_CP_ARG_PREFIX "--vm.cp="
#define VM_CLASSPATH_ARG_PREFIX "--vm.classpath="
#define VM_ARG_OFFSET (sizeof(VM_ARG_PREFIX)-1)
#define VM_CP_ARG_OFFSET (sizeof(VM_CP_ARG_PREFIX)-1)
#define VM_CLASSPATH_ARG_OFFSET (sizeof(VM_CLASSPATH_ARG_PREFIX)-1)
#define IS_VM_ARG(ARG) (ARG.rfind(VM_ARG_PREFIX, 0) != std::string::npos)
#define IS_VM_CP_ARG(ARG) (ARG.rfind(VM_CP_ARG_PREFIX, 0) != std::string::npos)
#define IS_VM_CLASSPATH_ARG(ARG) (ARG.rfind(VM_CLASSPATH_ARG_PREFIX, 0) != std::string::npos)

#define NMT_ARG_NAME "XX:NativeMemoryTracking"
#define NMT_ENV_NAME "NMT_LEVEL_"

#if defined (__linux__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <limits.h>
    #include <unistd.h>
    #include <errno.h>
    #include <sys/types.h>
    #include <sys/stat.h>
#elif defined (__APPLE__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <unistd.h>
    #include <errno.h>
    #include <mach-o/dyld.h>
    #include <sys/syslimits.h>
    #include <sys/stat.h>

    #ifndef LIBJLI_RELPATH
        #error path to jli library undefined
    #endif
    #define LIBJLI_RELPATH_STR STR(LIBJLI_RELPATH)

    /* Support Cocoa event loop on the main thread */
    #include <Cocoa/Cocoa.h>
    #include <objc/objc-runtime.h>
    #include <objc/objc-auto.h>

#elif defined (_WIN32)
    #include <windows.h>
    #include <libloaderapi.h>
    #include <cstdlib>
    #include <process.h>
    #include <errno.h>
    #include <sys/types.h>
    #include <sys/stat.h>
    #define getpid _getpid
    #define stat _stat
#else
    #error platform not supported or undefined
#endif

typedef jint(*CreateJVM)(JavaVM **, void **, void *);
extern char **environ;
bool debug = false;
bool relaunch = false;

/* platform-independent environment setter, use empty value to clear */
int setenv(std::string key, std::string value) {
    if (debug) {
        std::cout << "Setting env variable " << key << "=" << value << std::endl;
    }
    #if defined (_WIN32)
        if(_putenv_s(key.c_str(), value.c_str()) == -1) {
            std::cerr << "_putenv_s failed" << std::endl;
            return -1;
        }
    #else
        if (value.empty()) {
            /* on posix, unsetenv cleares the env variable */
            if (unsetenv(key.c_str()) == -1) {
                perror("unsetenv failed");
                return -1;
            }
        } else {
            if (setenv(key.c_str(), value.c_str(), 1) == -1) {
                perror("setenv failed");
                return -1;
            }
        }
    #endif
    return 0;
}

/* check if file exists */
bool exists(std::string filename) {
    struct stat buffer;
    return (stat(filename.c_str(), &buffer) == 0);
}

/* get the path to the current executable */
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

/* get the directory of the current executable */
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

#if defined (__APPLE__)
/* Load libjli - this is needed on osx for libawt, which uses JLI_* methods.
 * If the GraalVM libjli is not loaded, the osx linker will look up the symbol
 * via the JavaRuntimeSupport.framework (JRS), which will fall back to the
 * system JRE and fail if none is installed
 */
void *load_jli_lib(std::string exeDir) {
    std::stringstream libjliPath;
    libjliPath << exeDir << DIR_SEP_STR << LIBJLI_RELPATH_STR;
    return dlopen(libjliPath.str().c_str(), RTLD_NOW);
}
#endif

/* load the language library (either native library or libjvm) and return a
 * pointer to the JNI_CreateJavaVM function */
CreateJVM load_vm_lib(std::string liblangPath) {
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

std::string vm_path(std::string exeDir, bool jvmMode) {
    std::stringstream liblangPath;
    if (jvmMode) {
        liblangPath << exeDir << DIR_SEP_STR << LIBJVM_RELPATH_STR;
    } else {
        liblangPath << exeDir << DIR_SEP_STR << LIBLANG_RELPATH_STR;
    }
    return liblangPath.str();
}

void parse_vm_option(std::vector<std::string> *vmArgs, std::stringstream *cp, std::string option) {
    if (IS_VM_CP_ARG(option)) {
        *cp << CP_SEP_STR << option.substr(VM_CP_ARG_OFFSET);
    } else if (IS_VM_CLASSPATH_ARG(option)) {
        *cp << CP_SEP_STR << option.substr(VM_CLASSPATH_ARG_OFFSET);
    } else if (IS_VM_ARG(option)) {
        std::stringstream opt;
        opt << '-' << option.substr(VM_ARG_OFFSET);
        vmArgs->push_back(opt.str());
    }
}

/* parse the VM arguments that should be passed to JNI_CreateJavaVM */
void parse_vm_options(int argc, char **argv, std::string exeDir, JavaVMInitArgs *vmInitArgs, bool jvmMode) {
    std::vector<std::string> vmArgs;

    /* check if vm args have been set on relaunch already */
    int vmArgCount = 0;
    char *vmArgInfo = getenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS");
    if (vmArgInfo != NULL) {
        relaunch = true;
        vmArgCount = strtol(vmArgInfo, NULL, 10);
        // clear the env variable
        setenv("GRAALVM_LANGUAGE_LAUNCHER_VMARGS", "");
    }

    /* set optional vm args from LanguageLibraryConfig.option_vars */
    #ifdef LAUNCHER_OPTION_VARS
        const char *launcherOptionVars[] = LAUNCHER_OPTION_VARS;
    #endif

    /* set system properties */
    if (jvmMode) {
        /* this is only needed for jvm mode */
        vmArgs.push_back("-Dorg.graalvm.launcher.class=" LAUNCHER_CLASS_STR);
    }
    std::stringstream executablename;
    executablename << "-Dorg.graalvm.launcher.executablename=";
    char *executablenameEnv = getenv("GRAALVM_LAUNCHER_EXECUTABLE_NAME");
    if (executablenameEnv) {
        executablename << executablenameEnv;
        setenv("GRAALVM_LAUNCHER_EXECUTABLE_NAME", "");
    } else {
        executablename << argv[0];
    }
    vmArgs.push_back(executablename.str());
    if (debug) {
        std::cout<< "org.graalvm.launcher.executablename set to '" << executablename.str() << '\'' << std::endl;
    }

    /* construct classpath - only needed for jvm mode */
    std::stringstream cp;
    cp << "-Djava.class.path=";
    if (jvmMode) {
        /* add the launcher classpath */
        const char *launcherCpEntries[] = LAUNCHER_CLASSPATH;
        int launcherCpCnt = sizeof(launcherCpEntries) / sizeof(*launcherCpEntries);
        for (int i = 0; i < launcherCpCnt; i++) {
            cp << exeDir << DIR_SEP_STR << launcherCpEntries[i];
            if (i < launcherCpCnt-1) {
                cp << CP_SEP_STR;
            }
        }
    }

    /* handle CLI arguments */
    if (!vmArgInfo) {
        for (int i = 0; i < argc; i++) {
            parse_vm_option(&vmArgs, &cp, std::string(argv[i]));
        }
    }

    /* handle relaunch arguments */
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
            parse_vm_option(&vmArgs, &cp, std::string(cur));
            /* clean up env variable */
            setenv(envKey, "");
        }
    }

    /* handle optional vm args from LanguageLibraryConfig.option_vars */
    #ifdef LAUNCHER_OPTION_VARS
    for (int i = 0; i < sizeof(launcherOptionVars)/sizeof(char*); i++) {
        char *optionVar = getenv(launcherOptionVars[i]);
        if (!optionVar) {
            continue;
        }
        if (debug) {
            std::cout << "Launcher option_var found: " << launcherOptionVars[i] << "=" << optionVar << std::endl;
        }
        // we split on spaces
        std::string optionLine(optionVar);
        size_t last = 0;
        size_t next = 0;
        while ((next = optionLine.find(" ", last)) != std::string::npos) {
            std::string option = optionLine.substr(last, next-last);
            parse_vm_option(&vmArgs, &cp, option);
            last = next + 1;
        };
        parse_vm_option(&vmArgs, &cp, optionLine.substr(last));
    }
    #endif

    /* set classpath argument - only needed for jvm mode */
    if (jvmMode) {
        vmArgs.push_back(cp.str());
    }

    vmInitArgs->options = new JavaVMOption[vmArgs.size()];;
    vmInitArgs->nOptions = vmArgs.size();
    JavaVMOption *curOpt = vmInitArgs->options;
    for(const auto& arg: vmArgs) {
        if (debug) {
            std::cout << "Setting VM argument " << arg << std::endl;
        }
        /* env variable for native memory tracking (NMT), obsolete with JDK 18 */
        size_t nmtPos = arg.find(NMT_ARG_NAME);
        if (nmtPos != std::string::npos) {
            nmtPos += sizeof(NMT_ARG_NAME);
            std::string val = arg.substr(nmtPos);
            std::string pid = std::to_string(getpid());
            setenv(std::string(NMT_ENV_NAME) + pid, val);
        }
        curOpt->optionString = strdup(arg.c_str());
        curOpt++;
    }
}

static int jvm_main_thread(int argc, char *argv[], std::string exeDir, char *jvmModeEnv, bool jvmMode, std::string libPath);

#if defined (__APPLE__)
static void dummyTimer(CFRunLoopTimerRef timer, void *info) {}

static void ParkEventLoop() {
    // RunLoop needs at least one source, and 1e20 is pretty far into the future
    CFRunLoopTimerRef t = CFRunLoopTimerCreate(kCFAllocatorDefault, 1.0e20, 0.0, 0, 0, dummyTimer, NULL);
    CFRunLoopAddTimer(CFRunLoopGetCurrent(), t, kCFRunLoopDefaultMode);
    CFRelease(t);

    // Park this thread in the main run loop.
    int32_t result;
    do {
        result = CFRunLoopRunInMode(kCFRunLoopDefaultMode, 1.0e20, false);
    } while (result != kCFRunLoopRunFinished);
}

struct MainThreadArgs {
    int argc;
    char **argv;
    std::string exeDir;
    char *jvmModeEnv;
    bool jvmMode;
    std::string libPath;
};

static void *apple_main (void *arg)
{
    struct MainThreadArgs *args = (struct MainThreadArgs *) arg;
    int ret = jvm_main_thread(args->argc, args->argv, args->exeDir, args->jvmModeEnv, args->jvmMode, args->libPath);
    exit(ret);
}
#endif /* __APPLE__ */

int main(int argc, char *argv[]) {
    debug = (getenv("VERBOSE_GRAALVM_LAUNCHERS") != NULL);
    std::string exeDir = exe_directory();
    char* jvmModeEnv = getenv("GRAALVM_LAUNCHER_FORCE_JVM");
    bool jvmMode = (jvmModeEnv && (strcmp(jvmModeEnv, "true") == 0));

    std::string libPath = vm_path(exeDir, jvmMode);

    /* check if the VM library exists */
    if (!jvmMode) {
        if (!exists(libPath)) {
            /* switch to JVM mode */
            libPath = vm_path(exeDir, true);
            jvmMode = true;
        }
    }

#if defined (__APPLE__)
    if (jvmMode) {
        if (!load_jli_lib(exeDir)) {
            std::cerr << "Loading libjli failed." << std::endl;
            return -1;
        }
    }

    struct MainThreadArgs args = { argc, argv, exeDir, jvmModeEnv, jvmMode, libPath};

    /* Inherit stacksize of main thread. Otherwise pthread_create() defaults to
     * 512K on darwin, while the main thread has 8192K.
     */
    pthread_attr_t attr;
    if (pthread_attr_init(&attr) != 0) {
        std::cerr << "Could not initialize pthread attribute structure: " << strerror(errno) << std::endl;
        return -1;
    }
    if (pthread_attr_setstacksize(&attr, pthread_get_stacksize_np(pthread_self())) != 0) {
        std::cerr << "Could not set stacksize in pthread attribute structure." << std::endl;
        return -1;
    }

    /* Create dedicated "main" thread for the JVM. The actual main thread
     * must run the UI event loop on macOS. Inspired by this OpenJDK code:
     * https://github.com/openjdk/jdk/blob/011958d30b275f0f6a2de097938ceeb34beb314d/src/java.base/macosx/native/libjli/java_md_macosx.m#L328-L358
     */
    pthread_t main_thr;
    if (pthread_create(&main_thr, &attr, &apple_main, &args) != 0) {
        std::cerr << "Could not create main thread: " << strerror(errno) << std::endl;
        return -1;
    }
    if (pthread_detach(main_thr)) {
        std::cerr << "pthread_detach() failed: " << strerror(errno) << std::endl;
        return -1;
    }

    ParkEventLoop();
    return 0;
#else
    return jvm_main_thread(argc, argv, exeDir, jvmModeEnv, jvmMode, libPath);
#endif
}

static int jvm_main_thread(int argc, char *argv[], std::string exeDir, char *jvmModeEnv, bool jvmMode, std::string libPath) {
    /* parse VM args */
    JavaVM *vm;
    JNIEnv *env;
    JavaVMInitArgs vmInitArgs;
    vmInitArgs.nOptions = 0;
    parse_vm_options(argc, argv, exeDir, &vmInitArgs, jvmMode);
    vmInitArgs.version = JNI_VERSION_1_8;
    vmInitArgs.ignoreUnrecognized = true;

    /* load VM library - after parsing arguments s.t. NMT
     * tracking variable is already set */
    CreateJVM createVM = load_vm_lib(libPath);
    if (!createVM) {
        std::cerr << "Could not load JVM." << std::endl;
        return -1;
    }

    int res = createVM(&vm, (void**)&env, &vmInitArgs);
    if (res != JNI_OK) {
        std::cerr << "Creation of the VM failed." << std::endl;
        return -1;
    }

    /* free the allocated vm arguments */
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

    /* backup native args */
    char** argv_native = argv;
    int argc_native = argc;

    argv++;
    argc--;

    /* create args string array */
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

    /* invoke launcher entry point */
    env->CallStaticVoidMethod(launcherClass, runLauncherMid, args, argc_native, (jlong)(uintptr_t)(void*)argv_native, relaunch);
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
                /* set environment variables for relaunch vm arguments */
                std::stringstream key;
                key << "GRAALVM_LANGUAGE_LAUNCHER_VMARGS_" << i;
                std::string arg(vmArg);
                if (setenv(key.str(), arg) == -1) {
                    return -1;
                }
            }
            /* relaunch with correct VM arguments */
            std::string path = exe_path();
            execve(path.c_str(), argv_native, environ);
            /* if we reach here, execve failed for sure */
            perror("execve failed");
            return -1;
        }
        env->ExceptionDescribe();
        return -1;
    }
	return 0;
}
