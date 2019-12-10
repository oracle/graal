import sys
import json
import matplotlib.pyplot as plt

samples = []

for file in sys.argv[1:]:
    with open(file) as stream:
        for line in stream:
            samples.append(json.loads(line))

times = [s['elapsedTime'] / 1e9 for s in samples]

traces = [
    ('Temp', 'sampleReading', 0.01)
]

if 'iterationsPerSecond' in samples[0].keys():
    max_iterations = max([s['iterationsPerSecond'] for s in samples])
    if max_iterations > 1000000:
        iterations_scale = 1000000
        iterations_label = 'MIPS'
    elif max_iterations > 1000:
        iterations_scale = 1000
        iterations_label = 'KIPS'
    else:
        iterations_scale = 1
        iterations_label = 'IPS'

    traces.append((iterations_label, 'iterationsPerSecond', iterations_scale))

traces.extend([
    ('Code (MB)', 'loadedSource', 1024 * 1024),
    ('Queued', 'queued', 1),
    ('Running', 'running', 1),
    ('Finished', 'finished', 1),
    ('Failed', 'failed', 1),
    ('Dequeued', 'dequeued', 1),
    ('Deopts', 'deoptimizations', 1)
])

f, plots = plt.subplots(len(traces), 1, sharex=True)

for n, (plot, (title, key, scale)) in enumerate(zip(plots, traces)):
    values = [s[key] / float(scale) for s in samples]
    plot.plot(times, values, '-')
    min_value = min(values)
    max_value = max(values)
    if min_value == 0 and max_value == 0:
        plot.set_ylim([-1.0, +1.0])
    else:
        range = max_value - min_value
        margin = 0.1 * range
        plot.set_ylim([min_value - margin, max_value + margin])
    plot.set_ylabel(title)
    if n == len(traces) - 1:
        plot.set_xlabel('Time (s)')

plt.show()
