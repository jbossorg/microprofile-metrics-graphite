Microprofile Metrics - Graphite Integration
===========================================

Simple Microprofile Metrics - Graphite integration.
It just reads MP Metrics and send them via borrowed implementation from io.dropwizard.metrics:metrics-graphite (transitive dependency).

Usage
-----

Add Dependency

```
<dependency>
  <groupId>org.jboss.microprofile.metrics</groupId>
  <artifactId>graphite</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

### Example - Thorntail

Create following Application Scoped CDI Bean which thanks to ManagedScheduledExecutorService report all metrics periodically based on configuration.

```java
@ApplicationScoped
public class GraphiteManager {

    protected static final Logger log = Logger.getLogger(GraphiteManager.class);

    private GraphiteReporter graphiteReporter;

    @Inject
    @RegistryType(type = MetricRegistry.Type.APPLICATION)
    MetricRegistry appRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    MetricRegistry baseRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    MetricRegistry vendorRegistry;

    @Resource
    ManagedScheduledExecutorService executor;

    @Inject
    @ConfigurationValue("thorntail.metrics.graphite.enabled")
    private boolean enabled = false;

    @Inject
    @ConfigurationValue("thorntail.metrics.graphite.hostname")
    private String hostname;

    @Inject
    @ConfigurationValue("thorntail.metrics.graphite.port")
    private Integer port;

    @Inject
    @ConfigurationValue("thorntail.metrics.graphite.initial-delay-sec")
    private long initialDelaySec;

    @Inject
    @ConfigurationValue("thorntail.metrics.graphite.interval-sec")
    private long intervalSec;

    @Inject
    @ConfigurationValue("thorntail.metrics.graphite.prefix")
    private String prefix;

    private Set<MetricAttribute> disabledMetricAttributes = new HashSet<>();

    public void init(@Observes @Initialized(ApplicationScoped.class) Object event) {
        log.infof("Init GraphiteReporter enabled: %s, prefix: %s", enabled, prefix);

        if (!enabled) {
            return;
        }
        Graphite graphite = new Graphite(hostname, port);

        graphiteReporter = new GraphiteReporter.Builder().prefixedWith(prefix).disabledMetricAttributes(disabledMetricAttributes).build(graphite);

        log.infof("Starting GraphiteReporter with initialDelaySec: %ss, intervalSec: %ss", initialDelaySec, intervalSec);
        executor.scheduleWithFixedDelay(this::reportAll, initialDelaySec, intervalSec, TimeUnit.SECONDS);
    }

    public void reportAll() {
        graphiteReporter.reportRegistry(MetricRegistry.Type.BASE, baseRegistry);
        graphiteReporter.reportRegistry(MetricRegistry.Type.VENDOR, vendorRegistry);
        graphiteReporter.reportRegistry(MetricRegistry.Type.APPLICATION, appRegistry);
    }

}
```


Development
-----------
## Spin up local graphite instance

```
docker run -d\
 --name graphite\
 --restart=always\
 -p 80:80\
 -p 2003-2004:2003-2004\
 -p 2023-2024:2023-2024\
 -p 8125:8125/udp\
 -p 8126:8126\
 graphiteapp/graphite-statsd
```

See https://graphite.readthedocs.io/en/latest/install.html


## Deploy to repo

```
mvn deploy -Ddeploy-repo-id=$MVN_REPO_ID -Ddeploy-release-repo-url=$MVN_RELEASE_REPO_URL -Ddeploy-snapshot-repo-url=$MVN_SNAPSHOT_REPO_URL
```