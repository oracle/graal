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

#define IS_VM_ARG(ARG) (strncmp(ARG, "--vm.", 5) == 0)
#define IS_VM_CP_ARG(ARG) (strncmp(ARG, "--vm.cp=", 8) == 0)
#define IS_VM_CLASSPATH_ARG(ARG) (strncmp(ARG, "--vm.classpath=", 15) == 0)
#define VM_ARGS "--vmargs="
#define HAS_VM_ARGS(ARGC, ARGV) (strncmp(ARGV[(ARGC)-1], VM_ARGS, sizeof(VM_ARGS)) == 0)

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

void parse_vm_options(int *argc, char **argv, char *exe_dir, JavaVMInitArgs *vm_args, int *vm_arg_indices) {
    // check if vm arg indices have been set on relaunch already
    bool relaunch = false;
    if (HAS_VM_ARGS(*argc, argv)) {
        *vm_arg_indices = (int)strtol(argv[(*argc)-1] + sizeof(VM_ARGS), NULL, 16);
        *argc = (*argc)-1;
        relaunch = true;
    }
    // at least 1, for the classpath (although it might not be there at all)
    vm_args->nOptions = 1;
    #ifdef JVM
        // org.graalvm.launcher.class system property
        vm_args->nOptions++;
    #endif
    // handle vm arguments
    int user_launcher_cp_entries = 0;
    for (int i = 0; i < *argc; i++) {
        #ifdef JVM
            if (strcmp(argv[i], "--native") == 0) {
                fprintf(stdout, "The native version of %s does not exist: cannot use '--native'.\n", argv[0]);
                exit(-1);
            }
        #endif
        if (relaunch && !((1<<i) & *vm_arg_indices)) {
            continue;
        }
        if (IS_VM_CP_ARG(argv[i]) || IS_VM_CLASSPATH_ARG(argv[i])) {
            user_launcher_cp_entries++;
        } else if (IS_VM_ARG(argv[i])) {
            vm_args->nOptions++;
        }
    }
    char **user_cp_entries = NULL;
    char **user_cp_iterator = NULL;
    if (user_launcher_cp_entries) {
        user_cp_entries = (char**)malloc(user_launcher_cp_entries * sizeof(char*));
        user_cp_iterator = user_cp_entries;
    }
    // set vm arguments
    vm_args->options = (JavaVMOption *)malloc(vm_args->nOptions * sizeof(JavaVMOption));
    JavaVMOption *option_ptr = vm_args->options;
    #ifdef JVM
        option_ptr->optionString = "-Dorg.graalvm.launcher.class=" LAUNCHER_CLASS_STR;
        option_ptr++;
    #endif
    int cp_option_cnt = 0;
    for (int i = 0; i < *argc; i++) {
        if (IS_VM_ARG(argv[i])) {
            if (relaunch) {
                if (!((1<<i) & *vm_arg_indices)) {
                    continue;
                }
            } else {
                *vm_arg_indices = *vm_arg_indices | (1 << i);
            }
            if (IS_VM_CP_ARG(argv[i])) {
                *user_cp_iterator = argv[i]+8;
                user_cp_iterator++;
            } else if (IS_VM_CLASSPATH_ARG(argv[i])) {
                *user_cp_iterator = argv[i]+15;
                user_cp_iterator++;
            } else {
                // we need to prepend the vm arg with an additional dash, so we can't just point to the original arg
                int option_size = strlen(argv[i]+5) + 2;
                option_ptr->optionString = (char *)malloc(option_size);
                option_ptr->optionString[0] = '-';
                strcat(option_ptr->optionString, argv[i]+5);
                option_ptr++;
            }
        }
    }
    // set classpath
    const char cp_property[] = "-Djava.class.path=";
    int cp_size = sizeof(cp_property);
    #ifdef JVM
        // add the launcher classpath
        const char *launcher_cp_entries[] = LAUNCHER_CLASSPATH;
        int launcher_cp_cnt = sizeof(launcher_cp_entries) / sizeof(*launcher_cp_entries);
        cp_size += (strlen(exe_dir) + sizeof(DIR_SEP_STR)) * launcher_cp_cnt + sizeof(CP_SEP_STR) * (launcher_cp_cnt-1) + 2;
        for (int i = 0; i < launcher_cp_cnt; i++) {
            cp_size += strlen(launcher_cp_entries[i]);
        }
    #endif
    for (int i = 0; i < user_launcher_cp_entries; i++) {
        cp_size += strlen(user_cp_entries[i]) + sizeof(CP_SEP_STR);
    }
    char *cp = (char *)malloc(cp_size);
    // assemble the classpath string
    strcpy(cp, cp_property);
    #ifdef JVM
        for (int i = 0; i < launcher_cp_cnt; i++) {
            strcat(cp, exe_dir);
            strcat(cp, DIR_SEP_STR);
            strcat(cp, launcher_cp_entries[i]);
            if (i < launcher_cp_cnt-1) {
                strcat(cp, CP_SEP_STR);
            }
        }
    #endif
    for (int i = 0; i < user_launcher_cp_entries; i++) {
        strcat(cp, CP_SEP_STR);
        strcat(cp, user_cp_entries[i]);
    }
    if (user_launcher_cp_entries) {
        free(user_cp_entries);
    }
    option_ptr->optionString = cp;
}

int main(int argc, char *argv[], char *envp[]) {
    char *exe_dir = exe_directory();
    CreateJVM createJVM = loadliblang(exe_dir);
    if (!createJVM) {
        fprintf(stderr, "Could not load language library.\n");
        return -1;
    }
    JavaVM *jvm;
    JNIEnv *env;
    JavaVMInitArgs vm_args;
    int vm_arg_indices;
    parse_vm_options(&argc, argv, exe_dir, &vm_args, &vm_arg_indices);
    vm_args.version = JNI_VERSION_1_8;
    vm_args.ignoreUnrecognized = false;

    int res = createJVM(&jvm, (void**)&env, &vm_args);
    if (res != JNI_OK) {
        fprintf(stderr, "Creation of the JVM failed.\n");
        return -1;
    }

    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == NULL) {
        fprintf(stderr, "Byte array class not found.\n");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jclass throwableClass = env->FindClass("java/lang/Throwable");
    if (throwableClass == NULL) {
        fprintf(stderr, "Throwable class not found.\n");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jmethodID getMessageMid = env->GetMethodID(throwableClass, "getMessage", "()Ljava/lang/String;");
    if (getMessageMid == NULL) {
        fprintf(stderr, "Throwable getMessage() method ID not found.\n");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jclass launcherClass = env->FindClass("org/graalvm/launcher/AbstractLanguageLauncher");
    if (launcherClass == NULL) {
        fprintf(stderr, "Launcher class not found.\n");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jmethodID runLauncherMid = env->GetStaticMethodID(launcherClass, "runLauncher", "([[BIJI)V");
    if (runLauncherMid == NULL) {
        fprintf(stderr, "Launcher entry point not found.\n");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jfieldID vmArgsFid = env->GetStaticFieldID(launcherClass, "vmArgs", "I");
    if (vmArgsFid == NULL) {
        fprintf(stderr, "Launcher vm args field not found.\n");
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
            fprintf(stderr, "Error in SetByteArrayRegion:\n");
            env->ExceptionDescribe();
            return -1;
        }
        env->SetObjectArrayElement(args, i, arg);
        if (env->ExceptionCheck()) {
            fprintf(stderr, "Error in SetObjectArrayElement:\n");
            env->ExceptionDescribe();
            return -1;
        }
    }

    // invoke launcher entry point
    env->CallStaticVoidMethod(launcherClass, runLauncherMid, args, argc_native, (long)argv_native, vm_arg_indices);
    jthrowable t = env->ExceptionOccurred();
    if (t) {
        jobject tmsg = env->CallObjectMethod(t, getMessageMid);
        const char *msg = env->GetStringUTFChars((jstring)tmsg, NULL);
        if (strcmp(msg, "Misidentified VM arguments") == 0) {
            env->ExceptionClear();
            // read correct VM arguments from launcher object
            int vm_args = env->GetIntField(launcherClass, vmArgsFid);
            if (env->ExceptionCheck()) {
                fprintf(stderr, "Error in GetIntField:\n");
                env->ExceptionDescribe();
                return -1;
            }
            // copy argv
            char **argv_relaunch = (char**)malloc((argc_native+2) * sizeof(char*));
            memcpy(argv_relaunch, argv_native, argc_native * sizeof(char*));
            int vm_args_option_len = sizeof(VM_ARGS) + 9;
            argv_relaunch[argc_native] = (char*)malloc(vm_args_option_len);
            snprintf(argv_relaunch[argc_native], vm_args_option_len, VM_ARGS "%x", vm_args);
            argv_relaunch[argc_native+1] = NULL;
            // relaunch with correct VM arguments
            const char *path = exe_path();
            execve(path, argv_relaunch, envp);
            // if we reach here, execve failed for sure
            perror("execve failed");
            return -1;
        }
        env->ExceptionDescribe();
        return -1;
    }
	return 0;
}
