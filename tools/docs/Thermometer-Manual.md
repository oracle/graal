# The Truffle Optimization Thermometer Tool

The Truffle Optimization Thermometer Tool indicates how *warmed-up* your
application is. A *cold* application is running mostly in the interpreter, still
has code to compile, may still loading new code, and may be deoptimizing. A
*warmed up* application is stable and running mostly in compiled code.

It's difficult to talk quantitively about how warm an application is, and there
are many subtle factors at work here, so the tool is more of an *indication* than
a *measurement*.

See https://arxiv.org/abs/1602.00602 for some interesting discussion about
virtual machine warmup.

## Basic usage

Take this example Ruby program. It renders an ERB template. Every now and again
another thread swaps the template. After a few seconds a compilation will be
attempted which will fail.

```ruby
require 'erb'

template = ERB.new('The time is <%= time %>')

Thread.new do
  loop do
    sleep 3 + rand(3)
    template = ERB.new("The time was #{Time.now} but is now <%= time #{'    ' * 10_000} %>")
  end
end

Thread.new do
  sleep 3 + rand(3)
  loop do
    Truffle::Graal.bailout 'demo compilation failure'
  end
end

dev_null = File.open('/dev/null', 'w')

loop do
  time = Time.now
  dev_null.puts template.result(binding)
end
```

Run with the `--thermometer` flag (we use
`--vm.Dgraal.TruffleCompilationExceptionsAreThrown=true` to stop the
compilation failure being re-tried).

```
% ruby --vm.Dgraal.TruffleCompilationExceptionsAreThrown=true --thermometer demo.rb
```

You'll see log lines like this:

```
[thermometer] INFO:   5.34s  🥶   39°    0.77 MB  25 ▶  2 ▶ 17  (  2, 28 )   0 ▼
[thermometer] INFO:   5.67s  🥶   44°    0.77 MB  25 ▶  2 ▶ 17  (  2, 28 )   0 ▼
[thermometer] INFO:   6.00s  🤔   73°    0.77 MB   0 ▶  0 ▶ 20  (  2, 43 )   0 ▼
[thermometer] INFO:   6.34s  😊   97°    0.77 MB   0 ▶  0 ▶ 20  (  2, 43 )   0 ▼
[thermometer] INFO:   6.67s  😊  100°    0.77 MB   0 ▶  0 ▶ 20  (  2, 43 )   0 ▼
[thermometer] INFO:   7.00s  🤮   52°    0.84 MB  33 ▶  2 ▶ 26  (  2, 45 )   4 ▼
[thermometer] INFO:   7.33s  🥶   48°    0.84 MB  40 ▶  2 ▶ 26  (  2, 45 )   4 ▼
[thermometer] INFO:   7.67s  🤔   70°    0.84 MB  25 ▶  2 ▶ 27  (  2, 77 )   4 ▼
[thermometer] INFO:   8.00s  🤔   85°    0.84 MB   4 ▶  2 ▶ 29  (  2, 88 )   4 ▼
[thermometer] INFO:   8.33s  😊   94°    0.84 MB   0 ▶  1 ▶ 30  (  2, 92 )   4 ▼
[thermometer] INFO:   8.67s  😊  100°    0.84 MB   0 ▶  1 ▶ 30  (  2, 92 )   4 ▼
```

* `6.34s` is how long the application has been running by wall clock
* `🥶🤔😊` indicate very broadly whether the application is very cold, warming up, or warmed up
* `73°` indicates the *temperature* - how much of the application is compiled - see *Mechanism* below for details of how this is calculated
* `0.84 MB` is how much code has been loaded
* The next three numbers are the current compilation backlog, running compilations, and finished compilations
* The numbers in brackets are failures and dequeued compilations
* The final number is deoptimizations and invalidations

## Monitoring performance

`--thermometer.IPS=test.rb:24` will install an iterations-per-second counter
on any statements at this location. You should ensure there is just one
statement at this location as each statement run will count as an iteration.

```
[thermometer] INFO:   6.67s  🤮   79°    0.167 M i/s    0.81 MB   5 ▶  2 ▶ 24  (  3, 45 )   3 ▼
```

## Advanced usage

* `--thermometer.SamplingPeriod=10` sets the sampling period in ms.

* `--thermometer.ReportingPeriod=300` sets the reporting period in ms.

## Mechanism

The *temperature* is the percentage of samples taken during the period where the
top-most Truffle method activation is running in compiled code. Method preludes
set a per-thread flag to indicate whether they're compiled or not. A separate
high-priority timer thread samples this flag.

The indicator is set to `🥶` for a temperature `< 0.5`, `🤔` for `< 0.9`, and
`😊` otherwise. If there was a deoptimization in the period, it is instead set
to `🤮`. If there was a compilation error in the period, it is instead set to
`😡`.

The indicator isn't set higher than `🤔` if new code was loaded in the period.

## Overhead

Setting the flag is a volatile write. There is some method indirection in the
interpreter. In compiled code the flag is set with these machine instructions.
Note that the flag is set at the root of each logical method, not at the root of
each compilation unit.

```
movabs    $counter,%rax
movl      $0x1,field(%rax)
lock addl $0x0,(%rsp)
```

## Issues

There is a single flag, so the thermometer works best with applications with a
single thread running most of the time.

The sample flag is set for each guest-language method root, so a compilation
unit may set it multiple times increasing overhead.

Counters are `int` so may overflow during a very long running process.

(The `#{'    ' * 10_000}` in the demo is there so code size can be seen to
grow more easily.)
