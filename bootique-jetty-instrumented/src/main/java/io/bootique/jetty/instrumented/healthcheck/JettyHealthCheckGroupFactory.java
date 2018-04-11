package io.bootique.jetty.instrumented.healthcheck;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.bootique.annotation.BQConfig;
import io.bootique.annotation.BQConfigProperty;
import io.bootique.metrics.health.HealthCheck;
import io.bootique.metrics.health.check.IntValueRangeFactory;
import io.bootique.metrics.health.check.PercentValueRangeFactory;
import io.bootique.metrics.health.check.ValueRange;
import io.bootique.metrics.health.check.ValueRangeCheck;
import io.bootique.value.Percent;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @since 0.25
 */
@BQConfig("Configures Jetty-related health checks.")
public class JettyHealthCheckGroupFactory {

    static final String THREAD_POOL_UTILIZATION_CHECK = "bq.jetty.threadPool.utilization";
    static final String QUEUED_REQUESTS_CHECK = "bq.jetty.threadPool.queuedRequests";

    private IntValueRangeFactory queuedRequestsThresholds;
    private PercentValueRangeFactory poolUtilizationThresholds;

    protected ValueRange<Integer> getQueuedRequestsThresholds() {

        // init min if it wasn't set...
        if (queuedRequestsThresholds != null) {
            if (queuedRequestsThresholds.getMin() == null) {
                queuedRequestsThresholds.setMin(0);
            }

            return queuedRequestsThresholds.createRange();
        }

        // TODO: what is the default max queued requests for our Jetty?
        return ValueRange.builder(Integer.class).min(0).warning(3).critical(15).build();
    }

    @BQConfigProperty
    public void setQueuedRequestsThresholds(IntValueRangeFactory queuedRequestsThresholds) {
        this.queuedRequestsThresholds = queuedRequestsThresholds;
    }

    protected ValueRange<Percent> getPoolUtilizationThresholds() {

        if (poolUtilizationThresholds != null) {
            return poolUtilizationThresholds.createRange();
        }

        // default range
        return ValueRange.builder(Percent.class)
                .min(Percent.ZERO)
                .warning(new Percent(70))
                .critical(new Percent(90))
                .max(Percent.HUNDRED).build();
    }

    @BQConfigProperty
    public void setPoolUtilizationThresholds(PercentValueRangeFactory poolUtilizationThresholds) {
        this.poolUtilizationThresholds = poolUtilizationThresholds;
    }

    public JettyHealthCheckGroup createHealthCheckGroup(MetricRegistry registry) {
        return new JettyHealthCheckGroup(createHealthChecksMap(registry));
    }

    protected Map<String, HealthCheck> createHealthChecksMap(MetricRegistry registry) {
        Map<String, HealthCheck> checks = new HashMap<>(3);
        checks.put(THREAD_POOL_UTILIZATION_CHECK, createThreadPoolUtilizationCheck(registry));
        checks.put(QUEUED_REQUESTS_CHECK, createQueuedRequestsCheck(registry));
        return checks;
    }

    private HealthCheck createThreadPoolUtilizationCheck(MetricRegistry registry) {
        Supplier<Double> deferredGauge = valueFromGauge(registry, resolveMetricName("utilization"));
        Supplier<Percent> deferedPctGauge = () -> new Percent(deferredGauge.get() * 100.);
        ValueRange<Percent> range = getPoolUtilizationThresholds();
        return new ValueRangeCheck<>(range, deferedPctGauge);
    }

    private HealthCheck createQueuedRequestsCheck(MetricRegistry registry) {
        Supplier<Integer> deferredGauge = valueFromGauge(registry, resolveMetricName("queued-requests"));
        ValueRange<Integer> range = getQueuedRequestsThresholds();
        return new ValueRangeCheck<>(range, deferredGauge);
    }

    private String resolveMetricName(String metricLabel) {
        return MetricRegistry.name(QueuedThreadPool.class, "bootique-http", metricLabel);
    }

    private <T> Supplier<T> valueFromGauge(MetricRegistry registry, String name) {

        // using deferred gauge resolving to allow health checks against the system with misconfigured metrics,
        // or Jetty not yet up during health check creation
        return () -> (T) findGauge(registry, name).getValue();
    }

    private <T> Gauge<T> findGauge(MetricRegistry registry, String name) {

        Collection<Gauge> gauges = registry.getGauges((n, m) -> name.equals(n)).values();
        switch (gauges.size()) {
            case 0:
                throw new IllegalArgumentException("Gauge not found: " + name);
            case 1:
                return gauges.iterator().next();
            default:
                throw new IllegalArgumentException("More than one Gauge matching the name: " + name);
        }
    }
}
