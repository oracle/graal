package joonhwan.agent;

import java.lang.instrument.Instrumentation;

// Agent to initialize buffer and add shutdown hook
public class AgentJoon {
    private static final String BUFFER_CLASS = "jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache";

    public static void premain(String agentArgs, Instrumentation inst) {
        initBuffer();
        addShutdownHook();
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        initBuffer();
        addShutdownHook();
    }

    private static void initBuffer() {
        try {
            Class.forName(BUFFER_CLASS);
            System.out.println("VincentBuffer initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize VincentBuffer: " + e.getMessage());
        }
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Agent: JVM is shutting down, dumping buffer...");
            try {
                Class.forName(BUFFER_CLASS)
                    .getMethod("print")
                    .invoke(null);
                System.out.println("Agent: Buffer dump completed successfully");
            } catch (Exception e) {
                System.err.println("Agent: Failed to dump buffer: " + e.getMessage());
                e.printStackTrace();
            }
        }));
    }
}