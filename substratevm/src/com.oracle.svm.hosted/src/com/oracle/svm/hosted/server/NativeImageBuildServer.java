/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.server;

import static com.oracle.svm.hosted.NativeImageGeneratorRunner.verifyValidJavaVersionAndPlatform;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageBuildTask;
import com.oracle.svm.hosted.NativeImageClassLoader;
import com.oracle.svm.hosted.NativeImageGeneratorRunner;
import com.oracle.svm.hosted.server.SubstrateServerMessage.ServerCommand;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

/**
 * A server for SVM image building that keeps the classpath and JIT compiler code caches warm over
 * consecutive runs. Each compilation is defined by an {@link ImageBuildTask}.
 */
public final class NativeImageBuildServer {

    public static final String PORT_LOG_MESSAGE_PREFIX = "Started image build server on port: ";
    public static final String TASK_PREFIX = "-task=";
    public static final String PORT_PREFIX = "-port=";
    public static final String LOG_PREFIX = "-logFile=";
    private static final int TIMEOUT_MINUTES = 240;
    private static final String GRAALVM_VERSION_PROPERTY = "org.graalvm.version";
    private static final int SERVER_THREAD_POOL_SIZE = 4;
    private static final int FAILED_EXIT_STATUS = -1;

    private static Set<ImageBuildTask> tasks = Collections.synchronizedSet(new HashSet<>());

    private boolean terminated;
    private final int port;
    private PrintStream logOutput;

    /*
     * This is done as System.err and System.logOutput are replaced by reference during analysis.
     */
    private final StreamingServerMessageOutputStream outJSONStream = new StreamingServerMessageOutputStream(ServerCommand.WRITE_OUT, null);
    private final StreamingServerMessageOutputStream errorJSONStream = new StreamingServerMessageOutputStream(ServerCommand.WRITE_ERR, null);
    private final PrintStream serverStdout = new PrintStream(outJSONStream, true);
    private final PrintStream serverStderr = new PrintStream(errorJSONStream, true);

    private final AtomicLong activeBuildTasks = new AtomicLong();
    private Instant lastKeepAliveAction = Instant.now();
    private ThreadPoolExecutor threadPoolExecutor;

    private NativeImageBuildServer(int port, PrintStream logOutput) {
        this.port = port;
        this.logOutput = logOutput;
        threadPoolExecutor = new ThreadPoolExecutor(SERVER_THREAD_POOL_SIZE, SERVER_THREAD_POOL_SIZE, Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<>());

        /*
         * Set the right classloader in the process reaper
         */
        String executorClassHolder = JavaVersionUtil.JAVA_SPEC <= 8 ? "java.lang.UNIXProcess" : "java.lang.ProcessHandleImpl";

        withGlobalStaticField(executorClassHolder, "processReaperExecutor", f -> {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) f.get(null);
            final ThreadFactory factory = executor.getThreadFactory();
            executor.setThreadFactory(r -> {
                Thread t = factory.newThread(r);
                t.setContextClassLoader(NativeImageBuildServer.class.getClassLoader());
                return t;
            });
        });

        System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", NativeImageThreadFactory.class.getName());
        /* initialize the default fork join pool with the application loader */
        if (ForkJoinPool.commonPool().getFactory().getClass() != NativeImageThreadFactory.class) {
            throw VMError.shouldNotReachHere("Wrong thread pool factory: " + ForkJoinPool.commonPool().getFactory().getClass());
        }
    }

    private void log(String commandLine, Object... args) {
        logOutput.printf(commandLine, args);
        logOutput.flush();
    }

    private static void printUsageAndExit() {
        System.out.println("Usage:");
        System.out.println(String.format("  java -cp <compiler_class_path> " + NativeImageBuildServer.class.getName() + " %s<port_number> %s<log_file>", PORT_PREFIX, LOG_PREFIX));
        System.exit(FAILED_EXIT_STATUS);
    }

    public static void main(String[] argsArray) {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("org.graalvm.truffle", false);
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.internal.vm.compiler", false);
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("com.oracle.graal.graal_enterprise", true);
        ModuleSupport.exportAndOpenPackageToUnnamed("java.base", "sun.text.spi", false);
        if (JavaVersionUtil.JAVA_SPEC >= 14) {
            ModuleSupport.exportAndOpenPackageToUnnamed("java.base", "jdk.internal.loader", false);
        }

        if (!verifyValidJavaVersionAndPlatform()) {
            System.exit(FAILED_EXIT_STATUS);
        }
        List<String> args = new ArrayList<>(Arrays.asList(argsArray));
        if (args.size() < 1) {
            printUsageAndExit();
        }
        Optional<Integer> port = extractPort(args);
        if (!port.isPresent()) {
            printUsageAndExit();
        } else {
            Optional<String> logFile = extractLogFile(args);
            PrintStream output = System.out;
            try {
                if (logFile.isPresent()) {
                    File file = new File(logFile.get());
                    if (!file.createNewFile()) {
                        System.err.println("The log file already exists, or could not be created.");
                        System.exit(FAILED_EXIT_STATUS);
                    }
                    output = new PrintStream(new FileOutputStream(file));
                }
                new NativeImageBuildServer(port.get(), output).serve();
            } catch (IOException e) {
                System.err.println("Starting server failed with an exception: " + e);
                System.exit(FAILED_EXIT_STATUS);
            } finally {
                if (logFile.isPresent()) {
                    output.flush();
                    output.close();
                }
            }
        }
    }

    private static Optional<String> extractLogFile(List<String> args) {
        Optional<String> portArg = extractArg(args, LOG_PREFIX);
        return portArg.map(arg -> arg.substring(LOG_PREFIX.length()));
    }

    static Optional<Integer> extractPort(List<String> args) {
        Optional<String> portArg = extractArg(args, PORT_PREFIX);
        try {
            return portArg.map(arg -> Integer.parseInt(arg.substring(PORT_PREFIX.length())));
        } catch (Throwable ignored) {
            System.err.println("error: invalid port number format");
        }
        return Optional.empty();
    }

    static Optional<String> extractArg(List<String> args, String argPrefix) {
        Optional<String> portArg = args.stream().filter(x -> x.startsWith(argPrefix)).reduce((first, second) -> second);
        args.removeIf(a -> a.startsWith(argPrefix));
        return portArg;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void serve() {
        threadPoolExecutor.purge();
        if (port == 0) {
            log("Server selects ephemeral port\n");
        } else {
            log("Try binding server to port " + port + "...\n");
        }
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout((int) TimeUnit.MINUTES.toMillis(TIMEOUT_MINUTES));
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));

            /* NOTE: the following command line gets parsed externally */
            String portLogMessage = PORT_LOG_MESSAGE_PREFIX + serverSocket.getLocalPort();
            System.out.println(portLogMessage);
            System.out.flush();
            log(portLogMessage);

            while (true) {
                Socket socket = serverSocket.accept();

                log("Accepted request from " + socket.getInetAddress().getHostName() + ". Queuing to position: " + threadPoolExecutor.getQueue().size() + "\n");
                threadPoolExecutor.execute(() -> {
                    if (!processRequest(socket)) {
                        closeServerSocket(serverSocket);
                    }
                });
            }
        } catch (SocketTimeoutException ste) {
            log("Compilation server timed out. Shutting down...\n");
        } catch (SocketException se) {
            log("Terminated: " + se.getMessage() + "\n");
            if (!terminated) {
                log("Server error: " + se.getMessage() + "\n");
            }
        } catch (IOException e) {
            log("IOException in the socket operation.", e);
        } finally {
            log("Shutting down server...\n");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            threadPoolExecutor.shutdownNow();
        }
    }

    private void closeServerSocket(ServerSocket serverSocket) {
        try {
            log("Terminating...");
            terminated = true;
            serverSocket.close();
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private boolean processRequest(Socket socket) {
        try {
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            try {
                return processCommand(socket, SubstrateServerMessage.receive(input));
            } catch (Throwable t) {
                log("Execution failed: " + t + "\n");
                t.printStackTrace(logOutput);
                sendExitStatus(output, 1);
            }
        } catch (IOException ioe) {
            log("Failed fetching the output stream.");
        } finally {
            closeConnection(socket);
            log("Connection with the client closed.\n");
            // Remove the application class loader and save a GC on the next compilation
            System.gc();
            System.runFinalization();
            System.gc();
            log("Available Memory: " + Runtime.getRuntime().freeMemory() + "\n");
        }
        return true;
    }

    private static void closeConnection(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private boolean processCommand(Socket socket, SubstrateServerMessage serverCommand) throws IOException {
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        switch (serverCommand.command) {
            case STOP_SERVER:
                log("Received 'stop' request. Shutting down server.\n");
                sendExitStatus(output, 0);
                return false;
            case GET_VERSION:
                log("Received 'version' request. Responding with " + System.getProperty(GRAALVM_VERSION_PROPERTY) + ".\n");
                SubstrateServerMessage.send(new SubstrateServerMessage(serverCommand.command, System.getProperty(GRAALVM_VERSION_PROPERTY).getBytes()), output);
                return Instant.now().isBefore(lastKeepAliveAction.plus(Duration.ofMinutes(TIMEOUT_MINUTES)));
            case BUILD_IMAGE:
                try {
                    long activeTasks = activeBuildTasks.incrementAndGet();
                    if (activeTasks > 1) {
                        String message = "Can not build image: tasks are already running in the server.\n";
                        log(message);
                        sendError(output, message);
                        sendExitStatus(output, -1);
                    } else {
                        log("Starting compilation for request:\n%s\n", serverCommand.payloadString());
                        final ArrayList<String> arguments = new ArrayList<>(Arrays.asList(serverCommand.payloadString().split("\n")));

                        errorJSONStream.writingInterrupted(false);
                        errorJSONStream.setOriginal(socket.getOutputStream());
                        outJSONStream.writingInterrupted(false);
                        outJSONStream.setOriginal(socket.getOutputStream());

                        int exitStatus = withJVMContext(
                                        serverStdout,
                                        serverStderr,
                                        () -> executeCompilation(arguments));
                        sendExitStatus(output, exitStatus);
                        log("Image building completed.\n");

                        lastKeepAliveAction = Instant.now();
                    }
                } finally {
                    activeBuildTasks.decrementAndGet();
                }
                return true;
            case ABORT_BUILD:
                log("Received 'abort' request. Interrupting all image build tasks.\n");
                /*
                 * Busy wait for all writing to complete, otherwise JSON messages are malformed.
                 */
                errorJSONStream.writingInterrupted(true);
                outJSONStream.writingInterrupted(true);

                // Checkstyle: stop
                // noinspection StatementWithEmptyBody
                while (errorJSONStream.isWriting() || outJSONStream.isWriting()) {
                }
                // Checkstyle: start

                outJSONStream.flush();
                errorJSONStream.flush();
                for (ImageBuildTask task : tasks) {
                    threadPoolExecutor.submit(task::interruptBuild);
                }
                sendExitStatus(output, 0);
                return true;
            default:
                log("Invalid command: " + serverCommand.command);
                sendExitStatus(output, 1);
                return true;
        }
    }

    private static void sendExitStatus(DataOutputStream output, int exitStatus) {
        try {
            SubstrateServerMessage.send(new SubstrateServerMessage(ServerCommand.SEND_STATUS, ByteBuffer.allocate(4).putInt(exitStatus).array()), output);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static void sendError(DataOutputStream output, String message) {
        try {
            SubstrateServerMessage.send(new SubstrateServerMessage(ServerCommand.WRITE_ERR, message.getBytes()), output);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static Integer executeCompilation(ArrayList<String> arguments) {
        final String[] classpath = NativeImageGeneratorRunner.extractImageClassPath(arguments);
        NativeImageClassLoader imageClassLoader;
        ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            imageClassLoader = NativeImageGeneratorRunner.installNativeImageClassLoader(classpath);
            final ImageBuildTask task = loadCompilationTask(arguments, imageClassLoader);
            try {
                tasks.add(task);
                return task.build(arguments.toArray(new String[arguments.size()]), imageClassLoader);
            } finally {
                tasks.remove(task);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
        }
    }

    private static int withJVMContext(PrintStream out, PrintStream err, Supplier<Integer> body) {
        Properties previousProperties = (Properties) System.getProperties().clone();
        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;

        System.setOut(out);
        System.setErr(err);
        ResourceBundle.clearCache();
        try {
            return body.get();
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            System.setProperties(previousProperties);
            System.setOut(previousOut);
            System.setErr(previousErr);
            resetGlobalStateInLoggers();
            resetGlobalStateMXBeanLookup();
            resetResourceBundle();
            resetJarFileFactoryCaches();
            resetGlobalStateInGraal();
            withGlobalStaticField("java.lang.ApplicationShutdownHooks", "hooks", f -> {
                @SuppressWarnings("unchecked")
                IdentityHashMap<Thread, Thread> hooks = (IdentityHashMap<Thread, Thread>) f.get(null);
                hooks.forEach((x, y) -> {
                    x.setContextClassLoader(NativeImageBuildServer.class.getClassLoader());
                    y.setContextClassLoader(NativeImageBuildServer.class.getClassLoader());
                });
            });
        }
    }

    private static void resetGlobalStateMXBeanLookup() {
        withGlobalStaticField("com.sun.jmx.mbeanserver.MXBeanLookup", "currentLookup", f -> {
            ThreadLocal<?> currentLookup = (ThreadLocal<?>) f.get(null);
            currentLookup.remove();
        });
        withGlobalStaticField("com.sun.jmx.mbeanserver.MXBeanLookup", "mbscToLookup", f -> {
            try {
                Object mbscToLookup = f.get(null);
                Map<?, ?> map = ReflectionUtil.readField(Class.forName("com.sun.jmx.mbeanserver.WeakIdentityHashMap"), "map", mbscToLookup);
                map.clear();
                ReferenceQueue<?> refQueue = ReflectionUtil.readField(Class.forName("com.sun.jmx.mbeanserver.WeakIdentityHashMap"), "refQueue", mbscToLookup);
                Reference<?> ref;
                do {
                    ref = refQueue.poll();
                } while (ref != null);
            } catch (ClassNotFoundException e) {
                throw VMError.shouldNotReachHere(e);
            }
        });
    }

    private static void resetGlobalStateInLoggers() {
        LogManager.getLogManager().reset();
        withGlobalStaticField("java.util.logging.Level$KnownLevel", "nameToLevels", NativeImageBuildServer::removeImageLoggers);
        withGlobalStaticField("java.util.logging.Level$KnownLevel", "intToLevels", NativeImageBuildServer::removeImageLoggers);
    }

    private static void removeImageLoggers(Field f) throws IllegalAccessException {
        HashMap<Object, Object> newHashMap = new HashMap<>();
        HashMap<?, ?> currentNameToLevels = (HashMap<?, ?>) f.get(null);
        currentNameToLevels.entrySet().stream()
                        .filter(NativeImageBuildServer::isSystemLoaderLogLevelEntry)
                        .forEach(e -> newHashMap.put(e.getKey(), e.getValue()));
        f.set(null, newHashMap);
    }

    private static boolean isSystemLoaderLogLevelEntry(Entry<?, ?> e) {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            return ((List<?>) e.getValue()).stream()
                            .map(x -> getFieldValueOfObject("java.util.logging.Level$KnownLevel", "levelObject", x))
                            .allMatch(NativeImageBuildServer::isSystemClassLoader);
        } else {
            return ((List<?>) e.getValue()).stream()
                            .map(x -> getFieldValueOfObject("java.util.logging.Level$KnownLevel", "mirroredLevel", x))
                            .allMatch(NativeImageBuildServer::isSystemClassLoader);
        }
    }

    private static Object getFieldValueOfObject(String className, String fieldName, Object o) {
        try {
            return ReflectionUtil.readField(Class.forName(className), fieldName, o);
        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static boolean isSystemClassLoader(Object obj) {
        return obj.getClass().getClassLoader() == null ||
                        obj.getClass().getClassLoader() == ClassLoader.getSystemClassLoader() ||
                        obj.getClass().getClassLoader() == ClassLoader.getSystemClassLoader().getParent();
    }

    interface FieldAction {
        void perform(Field f) throws IllegalAccessException;
    }

    private static void withGlobalStaticField(String className, String fieldName, FieldAction action) {
        try {
            Field field = ReflectionUtil.lookupField(Class.forName(className), fieldName);
            action.perform(field);
        } catch (ClassNotFoundException | IllegalAccessException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static void resetGlobalStateInGraal() {
        withGlobalStaticField("org.graalvm.compiler.nodes.NamedLocationIdentity$DB", "map", f -> ((EconomicSet<?>) f.get(null)).clear());
        withGlobalStaticField("org.graalvm.compiler.debug.DebugContext$Immutable", "CACHE", f -> {
            Object[] cache = (Object[]) f.get(null);
            for (int i = 0; i < cache.length; i++) {
                cache[i] = null;
            }
        });
    }

    private static void resetResourceBundle() {
        withGlobalStaticField("java.util.ResourceBundle", "cacheList", list -> ((ConcurrentHashMap<?, ?>) list.get(null)).clear());
    }

    private static void resetJarFileFactoryCaches() {
        withGlobalStaticField("sun.net.www.protocol.jar.JarFileFactory", "fileCache", list -> ((HashMap<?, ?>) list.get(null)).clear());
        withGlobalStaticField("sun.net.www.protocol.jar.JarFileFactory", "urlCache", list -> ((HashMap<?, ?>) list.get(null)).clear());
    }

    private static ImageBuildTask loadCompilationTask(ArrayList<String> arguments, ClassLoader classLoader) {
        Optional<String> taskParameter = arguments.stream().filter(arg -> arg.startsWith(TASK_PREFIX)).findFirst();
        if (!taskParameter.isPresent()) {
            throw UserError.abort("image building task not specified. Provide the fully qualified task name after the \"" + TASK_PREFIX + "\" argument.");
        }
        arguments.removeAll(arguments.stream().filter(arg -> arg.startsWith(TASK_PREFIX)).collect(Collectors.toList()));
        final String task = taskParameter.get().substring(TASK_PREFIX.length());
        try {
            Class<?> imageTaskClass = Class.forName(task, true, classLoader);
            return (ImageBuildTask) imageTaskClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw UserError.abort("image building task " + task + " can not be found. Make sure that " + task + " is present on the classpath.");
        } catch (IllegalArgumentException | InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw UserError.abort("image building task " + task + " must have a public constructor without parameters.");
        }
    }
}
