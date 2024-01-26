# Debugging the agent with GDB

The native image agent is a native library that uses JVMTI.
In this document you will learn how to debug the agent.

Debugging an agent requires building it with its debug symbols.
To do that, execute:

```bash
$ native-image -g --macro:native-image-agent-library
```

Next, in order to debug the agent, the Java process that runs agent needs to be executed from within GDB, assuming that the `java` executable is located in the same `JAVA_HOME` as `native-image`, the agent and its debug symbols:

```bash
$ gdb java --args -agentlib:native-image-agent=config-output-dir=./native-image-config -jar app.jar
```

You can verify that the debug symbols for the agent have been loaded correctly by doing a function lookup:

```bash
(gdb) info functions .*NativeImageAgent.*
All functions matching regular expression ".*NativeImageAgent.*":
File com/oracle/svm/agent/NativeImageAgent.java:
	java.lang.Thread *com.oracle.svm.agent.NativeImageAgent$$Lambda$6779d0e96277d8fe370fc5f15b4e13f019254f7b::newThread(java.lang.Runnable*);
	java.lang.Object *com.oracle.svm.agent.NativeImageAgent$$Lambda$6a9ae4f1adc7f691a2f72e41ee6242d9fe025668::apply(java.lang.Object*);
	void com.oracle.svm.agent.NativeImageAgent$$Lambda$9615ae4da57d74c8ad20a2e088bd90fe293fc9de::run();
	java.lang.Object *com.oracle.svm.agent.NativeImageAgent$$Lambda$cd8007efd97585a6afbd18bf031cf260625a4fde::apply(java.lang.Object*);
	boolean com.oracle.svm.agent.NativeImageAgent$$Lambda$cdd28b87151f02b2dfa4883a6fb6fe7d0b2ee020::test(java.lang.Object*);
508:	int com.oracle.svm.agent.NativeImageAgent::buildImage(com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv);
663:	void com.oracle.svm.agent.NativeImageAgent::compulsoryDelete(java.nio.file.Path*);
639:	void com.oracle.svm.agent.NativeImageAgent::expectUnmodified(java.nio.file.Path*);
111:	boolean com.oracle.svm.agent.NativeImageAgent::getBooleanTokenValue(java.lang.String*);
654:	java.nio.file.attribute.FileTime *com.oracle.svm.agent.NativeImageAgent::getMostRecentlyModified(java.nio.file.Path*, java.nio.file.attribute.FileTime*);
107:	java.lang.String *com.oracle.svm.agent.NativeImageAgent::getTokenValue(java.lang.String*);
481:	void com.oracle.svm.agent.NativeImageAgent::ignoreConfigFromClasspath(com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv, com.oracle.svm.configure.config.ConfigurationFileCollection*);
405:	void com.oracle.svm.agent.NativeImageAgent::inform(java.lang.String*);
119:	boolean com.oracle.svm.agent.NativeImageAgent::isBooleanOption(java.lang.String*, java.lang.String*);
350:	java.lang.Exception *com.oracle.svm.agent.NativeImageAgent::lambda$onLoadCallback$1(java.io.IOException*);
469:	java.lang.Thread *com.oracle.svm.agent.NativeImageAgent::lambda$setupExecutorServiceForPeriodicConfigurationCapture$2(java.lang.Runnable*);
134:	int com.oracle.svm.agent.NativeImageAgent::onLoadCallback(com.oracle.svm.core.jni.headers.JNIJavaVM, com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv, com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventCallbacks, java.lang.String*);
...
```

Put a breakpoint:

```bash
(gdb) break com.oracle.svm.agent.NativeImageAgent::onLoadCallback
Breakpoint 2 at 0x7ffff4cdf550: file com/oracle/svm/agent/NativeImageAgent.java, line 134.
```

Disabling handling `SIGSEGV` signals in the debugger is highly recommended because HotSpot intentionally and frequently uses them for safepoints:

```bash
(gdb) handle SIGSEGV nostop noprint
```

Finally, execute the process from within GDB to stop at the breakpoint:

```bash
(gdb) run
Starting program: java -agentlib:native-image-agent=config-output-dir=./native-image-config -jar app.jar
[Thread debugging using libthread_db enabled]
Using host libthread_db library "/lib64/libthread_db.so.1".
[Switching to Thread 0x7ffff67ff6c0 (LWP 50250)]

Thread 2 "java" hit Breakpoint 2, com.oracle.svm.agent.NativeImageAgent::onLoadCallback(com.oracle.svm.core.jni.headers.JNIJavaVM, com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv, com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventCallbacks, java.lang.String*) (this=0x7fffe4e5b6f0, vm=0x7ffff7be8fa0 <main_vm>,
    jvmti=<optimized out>, callbacks=<optimized out>, options=<optimized out>) at com/oracle/svm/agent/NativeImageAgent.java:134
```
