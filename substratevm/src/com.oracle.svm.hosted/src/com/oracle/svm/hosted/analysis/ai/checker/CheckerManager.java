package com.oracle.svm.hosted.analysis.ai.checker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Manages a list of checkers and runs them in parallel
 */
public class CheckerManager {

    private final List<Checker<?>> checkers = new ArrayList<>();
    private final ExecutorService executorService;

    public CheckerManager() {
        this(4);
    }
    
    public CheckerManager(int numberOfThreads) {
        this.executorService = Executors.newFixedThreadPool(numberOfThreads);
    }

    public void registerChecker(Checker<?> checker) {
        checkers.add(checker);
    }

    public void runAllAnalyses() throws InterruptedException {
        List<Future<?>> futures = new ArrayList<>();
        for (Checker<?> checker : checkers) {
            futures.add(executorService.submit(checker::check));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        executorService.shutdown();
    }
}