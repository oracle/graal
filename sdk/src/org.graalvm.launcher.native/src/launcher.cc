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

#if defined (__linux__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <limits.h>
#elif defined (__APPLE__)
    #include <dlfcn.h>
    #include <stdlib.h>
    #include <libgen.h>
    #include <mach-o/dyld.h>
    #include <sys/syslimits.h>
#elif defined (_WIN32)
    #include <windows.h>
    #include <libloaderapi.h>
    #include <cstdlib>
#else
    #error platform not supported or undefined
#endif

typedef jint(*CreateJVM)(JavaVM **, void **, void *);

char *exe_directory() {
    #if defined (__linux__)
        return dirname(realpath("/proc/self/exe", NULL));
    #elif defined (__APPLE__)
        char path[PATH_MAX];
        uint32_t path_len = PATH_MAX;
        _NSGetExecutablePath(path, &path_len);
        return dirname(realpath(path, NULL));
    #elif defined (_WIN32)
        char *path = (char *)malloc(_MAX_PATH);
        GetModuleFileNameA(NULL, path, _MAX_PATH);
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

int main(int argc, char **argv) {
    char *exe_dir = exe_directory();
    CreateJVM createJVM = loadliblang(exe_dir);
    if (!createJVM) {
        fprintf(stderr, "Could not load language library.\n");
        return -1;
    }
    JavaVM *jvm;
    JNIEnv *env;
    JavaVMInitArgs vm_args;
    JavaVMOption options[4];
    #ifdef JVM
        const char *cp_entries[] = LAUNCHER_CLASSPATH;
        int cp_cnt = sizeof(cp_entries) / sizeof(*cp_entries);
        int size = (strlen(exe_dir) + sizeof(DIR_SEP_STR)) * cp_cnt + sizeof(CP_SEP_STR) * (cp_cnt-1) + 1;
        for (int i = 0; i < cp_cnt; i++) {
            size += strlen(cp_entries[i]);
        }
        const char cp_property[] = "-Djava.class.path=";
        size += sizeof(cp_property);
        char *cp = (char *)malloc(size);
        strcpy(cp, cp_property);
        for (int i = 0; i < cp_cnt; i++) {
            strcat(cp, exe_dir);
            strcat(cp, DIR_SEP_STR);
            strcat(cp, cp_entries[i]);
            if (i < cp_cnt-1) {
                strcat(cp, CP_SEP_STR);
            }
        }
        options[0].optionString = cp;
        options[1].optionString = "-Dorg.graalvm.launcher.class=" LAUNCHER_CLASS_STR;
        vm_args.nOptions = 2;
    #else
        vm_args.nOptions = 0;
    #endif
    vm_args.version = JNI_VERSION_1_8;
    vm_args.options = options;
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
    jclass launcherClass = env->FindClass("org/graalvm/launcher/AbstractLanguageLauncher");
    if (launcherClass == NULL) {
        fprintf(stderr, "Launcher class not found.\n");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }
    jmethodID mid = env->GetStaticMethodID(launcherClass, "runLauncher", "([[BIJ)V");
    if (mid == NULL) {
        fprintf(stderr, "Launcher entry point not found.\n");
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
        }
        return -1;
    }

    // create args string array
    long argv_native = (long)argv;
    int argc_native = argc;
    argv++;
    argc--;

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
    env->CallStaticVoidMethod(launcherClass, mid, args, argc_native, argv_native);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        return -1;
    }
	return 0;
}
