/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Starts a Java VM through the JNI invocation interface, runs DaemonMain.main, and ends the VM as
 * the JDK's `java` launcher does (JavaMain and LEAVE): DetachCurrentThread, then DestroyJavaVM.
 *
 * DaemonMain leaves a daemon thread running that ignores Thread.interrupt(). The JNI specification
 * has DestroyJavaVM wait for non-daemon threads only, and HotSpot lets it return, so the launcher
 * can exit. If it does not return here, the alarm ends the test with SIGALRM instead of blocking
 * the build.
 *
 * The same program is built twice: against the shared library image, which contains DaemonMain, and
 * against HotSpot's libjvm as the reference, which needs the class path as the first argument.
 */
#include <jni.h>
#include <stdio.h>
#include <unistd.h>

#define DESTROY_TIMEOUT_SECONDS 60

int main(int argc, char **argv) {
    JavaVM *vm;
    JNIEnv *env;
    JavaVMOption options[1];
    char class_path_option[4096];
    JavaVMInitArgs vm_args = {0};
    vm_args.version = JNI_VERSION_10;
    if (argc > 1) {
        snprintf(class_path_option, sizeof(class_path_option), "-Djava.class.path=%s", argv[1]);
        options[0].optionString = class_path_option;
        vm_args.nOptions = 1;
        vm_args.options = options;
    }

    alarm(DESTROY_TIMEOUT_SECONDS);

    if (JNI_CreateJavaVM(&vm, (void **) &env, &vm_args) != JNI_OK) {
        fprintf(stderr, "JNI_CreateJavaVM failed\n");
        return 2;
    }

    jclass cls = (*env)->FindClass(env, "com/oracle/svm/test/jni/invocation/DaemonMain");
    jmethodID main_method = cls == NULL ? NULL : (*env)->GetStaticMethodID(env, cls, "main", "([Ljava/lang/String;)V");
    if (main_method == NULL) {
        fprintf(stderr, "DaemonMain.main not found\n");
        return 3;
    }
    (*env)->CallStaticVoidMethod(env, cls, main_method, NULL);
    int result = (*env)->ExceptionOccurred(env) == NULL ? 0 : 1;

    if ((*vm)->DetachCurrentThread(vm) != JNI_OK) {
        fprintf(stderr, "DetachCurrentThread failed\n");
        return 4;
    }
    if ((*vm)->DestroyJavaVM(vm) != JNI_OK) {
        fprintf(stderr, "DestroyJavaVM failed\n");
        return 5;
    }
    printf("DestroyJavaVM returned\n");
    return result;
}
