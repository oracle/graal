#include <jni.h>
#include "native.h"

#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <iostream>
using namespace std;
#include <string>

#define BUF_SIZE 1024

enum PIPES { R, W };
static int myPipe[2];
static int oldStdOut;
static std::string captured;
static const string LINE_BREAK = "\r\n";

JNIEXPORT void JNICALL Java_com_oracle_truffle_llvm_pipe_CaptureOutput_startCapturing(JNIEnv *env, jclass c) {
  pipe(myPipe);
  oldStdOut = dup(fileno(stdout));
  fflush(stdout);
  dup2(myPipe[W], fileno(stdout));
}

JNIEXPORT void JNICALL Java_com_oracle_truffle_llvm_pipe_CaptureOutput_stopCapturing(JNIEnv * env, jclass c) {
  fflush(stdout);
  dup2(oldStdOut, fileno(stdout));
  captured.clear();

  if (myPipe[W] > 0) {
    close(myPipe[W]);
  }
  std::string buf;
  buf.resize(BUF_SIZE);

  int bytesRead = 0;
  do {
    bytesRead = read(myPipe[R], &(*buf.begin()), BUF_SIZE);
    captured += buf;         
  } while(bytesRead == BUF_SIZE);

  if (bytesRead > 0) {
    buf.resize(bytesRead);
    captured += buf;
  }
  if (myPipe[R] > 0) {
    close(myPipe[R]);
  }
}


JNIEXPORT jstring JNICALL Java_com_oracle_truffle_llvm_pipe_CaptureOutput_getCapture(JNIEnv * env, jclass c) {
  std::string::size_type idx = captured.find_last_not_of(LINE_BREAK);
  if (idx == std::string::npos) {
    return env->NewStringUTF(captured.c_str());
  } else {
    return env->NewStringUTF(captured.substr(0, idx+1).c_str());
  }
}
