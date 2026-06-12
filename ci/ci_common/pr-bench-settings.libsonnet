{
  crema_awfy_pr_bench:: {
    name: 'crema',
    metrics: ['time'],
    baseline_benchmarking: true,
  },

  crema_native_image_pr_bench:: self.crema_awfy_pr_bench + {
    metrics: ['time', 'throughput'],
    secondary_metrics: ['binary-size', 'max-rss', 'compile-time'],
    _extra_unicorn_args: [
      "--config-key", "dataserver/fetch/where/metric.object/enum[]=total",
      "--config-key", "dataserver/fetch/where/metric.object/allow-absent=true",
    ],
  },
}
