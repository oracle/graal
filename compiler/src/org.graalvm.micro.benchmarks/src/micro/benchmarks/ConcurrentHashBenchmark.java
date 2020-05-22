package micro.benchmarks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
public class ConcurrentHashBenchmark extends BenchmarkBase {

    private static final int MULTIPLIER = 10;
    private static final int SIZE = 100000;
    private static final int PARALLELISM = 4;
    private static final Integer[] KEYS = new Integer[SIZE];
    private static final String[] STRKEYS = new String[SIZE];

    private ConcurrentHashMap<Integer, Integer> prefilledMap;
    private ConcurrentHashMap<String, String> prefilledStringMap;

    @Setup(Level.Trial)
    public void setupInts() {
        this.prefilledMap = new ConcurrentHashMap<>();
        for (int i = 0; i < SIZE; i++) {
            KEYS[i] = Integer.valueOf(i);
            Integer key = KEYS[i];
            this.prefilledMap.put(key, key);
        }
    }

    @Setup(Level.Trial)
    public void setupStrings() {
        this.prefilledStringMap = new ConcurrentHashMap<>();
        for (int i = 0; i < SIZE; i++) {
            STRKEYS[i] = String.valueOf(i);
            String key = STRKEYS[i];
            this.prefilledStringMap.put(key, key);
        }
    }

    private int keySum() {
        int sum = 0;
        for (int i = 0; i < SIZE * MULTIPLIER; i++) {
            Integer key = KEYS[i % SIZE];
            sum += prefilledMap.get(key);
        }
        return sum;
    }

    private int inParallel(Supplier<Integer> action) {
        final int[] results = new int[PARALLELISM];
        Thread[] threads = new Thread[PARALLELISM];
        for (int i = 0; i < PARALLELISM; i++) {
            final int index = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    results[index] = action.get();
                }
            };
        }
        for (int i = 0; i < PARALLELISM; i++) {
            threads[i].start();
        }
        int result = 0;
        for (int i = 0; i < PARALLELISM; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            result += results[i];
        }
        return result;
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int sequentialGet() {
        return keySum();
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int parallelGet() {
        return inParallel(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return keySum();
            }
        });
    }

    private int keyHash() {
        int hash = 0;
        for (int i = 0; i < SIZE * MULTIPLIER; i++) {
            String key = STRKEYS[i % SIZE];
            hash ^= prefilledStringMap.get(key).hashCode();
        }
        return hash;
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int sequentialStringGet() {
        return keyHash();
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int parallelStringGet() {
        return inParallel(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return keyHash();
            }
        });
    }

    @OutputTimeUnit(TimeUnit.SECONDS)
    @Benchmark
    public int sequentialPut() {
        final ConcurrentHashMap<Integer, Integer> map = new ConcurrentHashMap<>();
        for (int i = 0; i < SIZE; i++) {
            Integer key = KEYS[i];
            map.put(key, key);
        }
        return map.get(SIZE / 2);
    }
}