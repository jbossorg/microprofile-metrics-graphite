package org.jboss.microprofile.metrics.graphite;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.Metered;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.graphite.GraphiteSender;

import static org.jboss.microprofile.metrics.graphite.MetricAttribute.COUNT;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.M15_RATE;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.M1_RATE;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.M5_RATE;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.MAX;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.MEAN;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.MEAN_RATE;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.MIN;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.P50;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.P75;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.P95;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.P98;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.P99;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.P999;
import static org.jboss.microprofile.metrics.graphite.MetricAttribute.STDDEV;

/**
 * Micro profile Metrics Reporter to Graphite.
 * Inspired by https://github.com/dropwizard/metrics/blob/v4.1.0/metrics-graphite/src/main/java/com/codahale/metrics/graphite/GraphiteReporter.java
 *
 * @author Libor Krzyzanek
 */
public class GraphiteReporter {

    Logger log = LoggerFactory.getLogger(GraphiteReporter.class);

    private GraphiteSender graphite;

    private String prefix;

    private Set<MetricAttribute> disabledMetricAttributes;

    private MetricFilter filter;

    protected GraphiteReporter(GraphiteSender graphite, String prefix, Set<MetricAttribute> disabledMetricAttributes, MetricFilter filter) {
        this.graphite = graphite;
        this.prefix = prefix;
        this.disabledMetricAttributes = disabledMetricAttributes;
        this.filter = filter;
    }

    public void reportRegistry(MetricRegistry.Type scope, MetricRegistry registry) {
        log.debug("Report '{}' Registry", scope.getName());
        reportMetrics(scope.getName(),
                registry.getGauges(filter),
                registry.getCounters(filter),
                registry.getHistograms(filter),
                registry.getMeters(filter),
                registry.getTimers(filter));
    }

    protected void reportMetrics(String scope, SortedMap<String, Gauge> gauges,
            SortedMap<String, Counter> counters,
            SortedMap<String, Histogram> histograms,
            SortedMap<String, Meter> meters,
            SortedMap<String, Timer> timers) {
        final long timestamp = System.currentTimeMillis() / 1000;

        try {
            graphite.connect();

            for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
                reportGauge(scope + "." + entry.getKey(), entry.getValue(), timestamp);
            }
            graphite.flush();

            for (Map.Entry<String, Counter> entry : counters.entrySet()) {
                reportCounter(scope + "." + entry.getKey(), entry.getValue(), timestamp);
            }
            graphite.flush();

            for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
                reportHistogram(scope + "." + entry.getKey(), entry.getValue(), timestamp);
            }
            graphite.flush();

            for (Map.Entry<String, Meter> entry : meters.entrySet()) {
                reportMetered(scope + "." + entry.getKey(), entry.getValue(), timestamp);
            }
            graphite.flush();

            for (Map.Entry<String, Timer> entry : timers.entrySet()) {
                reportTimer(scope + "." + entry.getKey(), entry.getValue(), timestamp);
            }
            graphite.flush();
        } catch (IOException e) {
            log.warn("Unable to report to Graphite", e);
        } finally {
            try {
                graphite.flush();
                graphite.close();
            } catch (IOException e1) {
                log.warn("Error flushing/closing Graphite", e1);
            }
        }
    }

    private void reportTimer(String name, Timer timer, long timestamp) throws IOException {
        log.trace("report time: {}", name);
        final Snapshot snapshot = timer.getSnapshot();
        sendIfEnabled(MAX, name, convertDuration(snapshot.getMax()), timestamp);
        sendIfEnabled(MEAN, name, convertDuration(snapshot.getMean()), timestamp);
        sendIfEnabled(MIN, name, convertDuration(snapshot.getMin()), timestamp);
        sendIfEnabled(STDDEV, name, convertDuration(snapshot.getStdDev()), timestamp);
        sendIfEnabled(P50, name, convertDuration(snapshot.getMedian()), timestamp);
        sendIfEnabled(P75, name, convertDuration(snapshot.get75thPercentile()), timestamp);
        sendIfEnabled(P95, name, convertDuration(snapshot.get95thPercentile()), timestamp);
        sendIfEnabled(P98, name, convertDuration(snapshot.get98thPercentile()), timestamp);
        sendIfEnabled(P99, name, convertDuration(snapshot.get99thPercentile()), timestamp);
        sendIfEnabled(P999, name, convertDuration(snapshot.get999thPercentile()), timestamp);
        reportMetered(name, timer, timestamp);
    }

    private void reportMetered(String name, Metered meter, long timestamp) throws IOException {
        log.trace("report metered: {}", name);
        sendIfEnabled(COUNT, name, meter.getCount(), timestamp);
        sendIfEnabled(M1_RATE, name, convertRate(meter.getOneMinuteRate()), timestamp);
        sendIfEnabled(M5_RATE, name, convertRate(meter.getFiveMinuteRate()), timestamp);
        sendIfEnabled(M15_RATE, name, convertRate(meter.getFifteenMinuteRate()), timestamp);
        sendIfEnabled(MEAN_RATE, name, convertRate(meter.getMeanRate()), timestamp);
    }

    private void reportHistogram(String name, Histogram histogram, long timestamp) throws IOException {
        log.trace("report histogram: {}", name);
        final Snapshot snapshot = histogram.getSnapshot();
        sendIfEnabled(COUNT, name, histogram.getCount(), timestamp);
        sendIfEnabled(MAX, name, snapshot.getMax(), timestamp);
        sendIfEnabled(MEAN, name, snapshot.getMean(), timestamp);
        sendIfEnabled(MIN, name, snapshot.getMin(), timestamp);
        sendIfEnabled(STDDEV, name, snapshot.getStdDev(), timestamp);
        sendIfEnabled(P50, name, snapshot.getMedian(), timestamp);
        sendIfEnabled(P75, name, snapshot.get75thPercentile(), timestamp);
        sendIfEnabled(P95, name, snapshot.get95thPercentile(), timestamp);
        sendIfEnabled(P98, name, snapshot.get98thPercentile(), timestamp);
        sendIfEnabled(P99, name, snapshot.get99thPercentile(), timestamp);
        sendIfEnabled(P999, name, snapshot.get999thPercentile(), timestamp);
    }

    private void reportCounter(String name, Counter counter, long timestamp) throws IOException {
        log.trace("report counter: {}", name);
        graphite.send(prefix(name, COUNT.getCode()), format(counter.getCount()), timestamp);
    }

    private void reportGauge(String name, Gauge<?> gauge, long timestamp) throws IOException {
        log.trace("report gauge: {}", name);
        final String value = format(gauge.getValue());
        if (value != null) {
            graphite.send(prefix(name), value, timestamp);
        }
    }

    private void sendIfEnabled(MetricAttribute type, String name, double value, long timestamp) throws IOException {
        if (getDisabledMetricAttributes().contains(type)) {
            return;
        }
        graphite.send(prefix(name, type.getCode()), format(value), timestamp);
    }

    private void sendIfEnabled(MetricAttribute type, String name, long value, long timestamp) throws IOException {
        if (getDisabledMetricAttributes().contains(type)) {
            return;
        }
        graphite.send(prefix(name, type.getCode()), format(value), timestamp);
    }

    protected double convertDuration(double duration) {
        return duration;
    }

    protected double convertRate(double rate) {
        return rate;
    }

    private String format(Object o) {
        if (o instanceof Float) {
            return format(((Float) o).doubleValue());
        } else if (o instanceof Double) {
            return format(((Double) o).doubleValue());
        } else if (o instanceof Byte) {
            return format(((Byte) o).longValue());
        } else if (o instanceof Short) {
            return format(((Short) o).longValue());
        } else if (o instanceof Integer) {
            return format(((Integer) o).longValue());
        } else if (o instanceof Long) {
            return format(((Long) o).longValue());
        } else if (o instanceof BigInteger) {
            return format(((BigInteger) o).doubleValue());
        } else if (o instanceof BigDecimal) {
            return format(((BigDecimal) o).doubleValue());
        } else if (o instanceof Boolean) {
            return format(((Boolean) o) ? 1 : 0);
        }
        return null;
    }

    private String format(long n) {
        return Long.toString(n);
    }

    protected String format(double v) {
        // the Carbon plaintext format is pretty underspecified, but it seems like it just wants
        // US-formatted digits
        return String.format(Locale.US, "%2.2f", v);
    }

    private String prefix(String... components) {
        return MetricRegistry.name(prefix, components);
    }

    protected Set<MetricAttribute> getDisabledMetricAttributes() {
        return disabledMetricAttributes;
    }

    public static class Builder {
        private String prefix = "";
        private Set<MetricAttribute> disabledMetricAttributes = new HashSet<>();
        private MetricFilter filter = MetricFilter.ALL;

        /**
         * Prefix all metric names with the given string.
         *
         * @param prefix the prefix for all metric names
         * @return {@code this}
         */
        public Builder prefixedWith(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Don't report the passed metric attributes for all metrics (e.g. "p999", "stddev" or "m15").
         * See {@link MetricAttribute}.
         *
         * @param disabledMetricAttributes a set of {@link MetricAttribute}
         * @return {@code this}
         */
        public Builder disabledMetricAttributes(Set<MetricAttribute> disabledMetricAttributes) {
            this.disabledMetricAttributes = disabledMetricAttributes;
            return this;
        }

        public GraphiteReporter build(GraphiteSender graphite) {
            return new GraphiteReporter(
                    graphite,
                    prefix,
                    disabledMetricAttributes,
                    filter);
        }
    }

}
