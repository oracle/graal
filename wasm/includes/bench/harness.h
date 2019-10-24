#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

int benchmarkWarmupCount();

void benchmarkSetupOnce();

int benchmarkRun();

// -------------------------
// Function pointer list.
//
// This is done to prevent the deoptimization of these functions.
// They need to be called by the harness programmatically,
// which means that it must be possible to look them up.
// -------------------------
int (*functionWarmupCount)() = benchmarkWarmupCount;

void (*functionSetupOnce)() = benchmarkSetupOnce;

int (*functionRun)() = benchmarkRun;
// -------------------------
// End of the function pointer list.
// -------------------------

int main() {
    struct timeval start, end;

    functionSetupOnce();

    // Execute warmup.
    for (int i = 0; i != functionWarmupCount(); ++i) {
        double res = functionRun();
        printf("Warmup iteration %d, res = %f\n", i + 1, res);
    }

    // Execute the benchmark itself.
    gettimeofday(&start, NULL);
    double res = functionRun();
    gettimeofday(&end, NULL);

    long start_t = start.tv_sec * 1000000 + start.tv_usec;
    long end_t = end.tv_sec * 1000000 + end.tv_usec;
    double time = (end_t - start_t) / 1000000.0;
    printf("time = %.2f\n", time);
    printf("ops/sec = %.2f\n", 1.0 / time);
    printf("res = %f\n", res);
    return 0;
}
