[![Community Project header](https://github.com/newrelic/open-source-office/raw/master/examples/categories/images/Community_Project.png)](https://github.com/newrelic/open-source-office/blob/master/examples/categories/index.md#community-project)

# New Relic OpenTelemetry exporter
An [OpenTelemetry](https://github.com/open-telemetry/opentelemetry-java) exporter for sending spans and metrics
to New Relic using the New Relic Java Telemetry SDK.

For the details on how OpenTelemetry data is mapped to New Relic data, see documentation in
[Our exporter specifications documentation](https://github.com/newrelic/newrelic-exporter-specs)

## How to use

The New Relic OpenTelemetry exporter can be used in two capacities: 

1. [Programmatically](#Programmatic-Usage) - an application takes a dependency on the exporter library, and manually invokes its APIs to report OpenTelemetry data to New Relic.
2. [Auto Instrumentation](#Auto-Instrumentation-Usage) - configure an application to use the OpenTelemetry Java Agent for automatic instrumentation, and report the data to New Relic via the exporter.

In either case, to send the resulting spans and metrics to New Relic, you will need an 
[Insights Insert API Key](https://docs.newrelic.com/docs/insights/insights-data-sources/custom-data/introduction-event-api#).

### Programmatic Usage

The New Relic OpenTelemetry exporter can be used programmatically, allowing an application to invoke its APIs as needed to report OpenTelemetry data to New Relic.

The workflow for programmatic use is as follows:
- Create exporters for the data types to be reported. The exporter currently supports spans and metrics.
- Register the exporters using OpenTelemetry APIs.
- Use the OpenTelemetry APIs to record data, which will be exported to New Relic.
- At the end of the application's lifecycle, call shutdown APIs to stop OpenTelemetry and exporter activity.

[BasicExample.java](opentelemetry-exporters-newrelic/src/test/java/com/newrelic/telemetry/opentelemetry/examples/BasicExample.java) gives a good end to end
demonstration of this workflow. It should be considered the canonical code for this type of workflow since OpenTelemetry internal SDK APIs are still a work in 
progress.

The easiest way to get started using the OpenTelemetry SDK with the New Relic exporters is as follows:

Add required dependencies in `build.gradle` (see [Published Artifacts](#Published-Artifacts) for versions):
```
repositories {
    maven {
        url = "https://oss.sonatype.org/content/repositories/snapshots"
    }
}

dependencies {
    implementation("com.newrelic.telemetry:opentelemetry-exporters-newrelic:{version}")
    implementation("io.opentelemetry:opentelemetry-sdk:{version}")
    implementation("com.newrelic.telemetry:telemetry-core:{version")
    implementation("com.newrelic.telemetry:telemetry-http-okhttp:{version}")
}
```

Call the APIs in the application:
```java
    // Create a configuration object
    NewRelicExporters.Configuration configuration =
      new NewRelicExporters.Configuration(apiKey, "My Service Name")
        .enableAuditLogging()           // Optionally enable audit logging
        .collectionIntervalSeconds(10); // Set the reporting interval for metrics and spans to 10 seconds
    
    // Call start with the configuration
    NewRelicExporters.start(configuration);
    
    // Call the OpenTelemetry SDK to obtain tracers and meters to record data
    Tracer tracer = OpenTelemetry.getGlobalTracerProvider().get("sample-app", "1.0");
    Meter meter = OpenTelemetry.getGlobalMeterProvider().get("sample-app", "1.0");

    // ... when the application is complete, be sure call shutdown to stop the exporters
    NewRelicExporters.shutdown();
```

The previous code hides some boilerplate code that can be further customized if more flexibility is required. [Recording Spans](#Recording-Spans) and 
[Recording Metrics](#Recording-Metrics) describe this and how to use the APIs to record data in more detail.

#### Recording Spans
 
[BasicExample.java](opentelemetry-exporters-newrelic/src/test/java/com/newrelic/telemetry/opentelemetry/examples/BasicExample.java) demonstrates the easiest way
to configure the span exporter. If your application needs more flexibility, it can be further configured as follows:

```java
    // Create a NewRelicSpanExporter
    NewRelicSpanExporter exporter = NewRelicSpanExporter.newBuilder()
        .apiKey(System.getenv("INSIGHTS_INSERT_KEY"))
        .commonAttributes(new Attributes().put(SERVICE_NAME, "best service ever"))
        .build();
    
    // Use the NewRelicSpanExporter to build a OpenTelemetry BatchSpansProcessor
    BatchSpanProcessor spanProcessor = BatchSpanProcessor.newBuilder(exporter)
        .setScheduleDelayMillis(10_000) // Optionally override the default schedule delay
        .build();

    // Register the span processor with the default TracerSdkManagement of the OpenTelemetrySdk
   OpenTelemetrySdk.getTracerManagement().tracerManagement.addSpanProcessor(spanProcessor);
```

Once the span exporter has been registered with the `OpenTelemetrySdk`, spans can be recorded as follows:

```java
    // Create an OpenTelemetry Tracer and use it to record spans
    Tracer tracer = OpenTelemetry.getTracerProvider().get("sample-app", "1.0");
    
    Span span = tracer.spanBuilder("testSpan").setSpanKind(Kind.INTERNAL).startSpan();
    try (Scope scope = tracer.withSpan(span)) {
      //do some work
      Thread.sleep(1000);
      span.end();
    }
```

Find your spans in New Relic One: go to [New Relic One](https://one.newrelic.com/) and select **Distributed Tracing**.

#### Recording Metrics

[BasicExample.java](opentelemetry-exporters-newrelic/src/test/java/com/newrelic/telemetry/opentelemetry/examples/BasicExample.java) demonstrates the easiest way
to configure the metric exporter. If your application needs more flexibility, it can be further configured as follows:

```java
    // Create a NewRelicMetricExporter
    MetricExporter metricExporter =
        NewRelicMetricExporter.newBuilder()
          .apiKey(System.getenv("INSIGHTS_INSERT_KEY"))
          .commonAttributes(new Attributes().put(SERVICE_NAME, "best service ever"))
          .build();

    // Create an IntervalMetricReader configured to batch up metrics every 5 seconds
    IntervalMetricReader intervalMetricReader =
        IntervalMetricReader.builder()
            .setMetricProducers(
                Collections.singleton(OpenTelemetrySdk.getMeterProvider().getMetricProducer()))
            .setExportIntervalMillis(5000) // Batch up metrics every 5 seconds, or on whatever schedule the application requires
            .setMetricExporter(metricExporter)
            .build();
```

Once the `IntervalMetricReader` has been setup with the metric exporter, metrics can be recorded as follows:

```java
    // Recording metrics starts with obtaining a meter. The meter can then be used to obtain instruments
    // to which the application reports measurements.
    Meter meter = OpenTelemetry.getMeterProvider().get("sample-app", "1.0");

    // Obtain a counter from the meter
    LongCounter spanCounter =
        meter
            .longCounterBuilder("spanCounter")
            .setUnit("one")
            .setDescription("Counting all the spans")
            .setMonotonic(true)
            .build();

    // Obtain a measure from the meter
    LongMeasure spanTimer =
        meter
            .longMeasureBuilder("spanTimer")
            .setUnit("ms")
            .setDescription("How long the spans take")
            .setAbsolute(true)
            .build();
    
    // Use the instruments to record measurements
   spanCounter.add(1, "spanName", "testSpan", "isItAnError", "true");
   spanTimer.record(1000, "spanName", "testSpan"); 
```

To find your metrics in New Relic One, go to [New Relic One](https://one.newrelic.com/) and locate your service in the **Entity explorer** 
(based on the `"service.name"` attributes you've used).

### Auto Instrumentation Usage

In order to automatically instrument an application, it must be configured to use the OpenTelemetry Java Agent and to use the New Relic OpenTelemetry exporter 
by passing several `options` to the `java [options] -jar <mainclass>> [args..]` command.

The OpenTelemetry Java Agent must be downloaded (various versions available [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases)),
 and specified via: 

`-javaagent:path/to/opentelemetry-javaagent-<version>-all.jar`

The New Relic OpenTelemetry Exporter must be downloaded 
(various versions available [here](https://repo1.maven.org/maven2/com/newrelic/telemetry/opentelemetry-exporters-newrelic-auto/)), and specified via:

`-Dotel.exporter.jar=path/to/opentelemetry-exporter-newrelic-auto-<version>.jar`

A New Relic Insights Insert API key must be specified via:

`-Dnewrelic.api.key=${INSIGHTS_INSERT_KEY}`

The application's service name _should_ be specified via:

`-Dnewrelic.service.name=best-service-ever`

The configuration can be optionally further customized using the available [system properties](#Configuration-System-Properties).

Bringing it all together, the command to run the application will look something like:

```bash
java -javaagent:path/to/opentelemetry-javaagent-<version>-all.jar \
     -Dotel.exporter.jar=path/to/opentelemetry-exporter-newrelic-auto-<version>.jar \
     -Dnewrelic.api.key=${INSIGHTS_INSERT_KEY} \
     -Dnewrelic.service.name=best-service-ever \
     -jar myapp.jar
```

:warning: If you encounter an error like this:

```
[main] WARN io.opentelemetry.auto.tooling.TracerInstaller - No span exporter found in opentelemetry-exporters-newrelic-auto-0.8.1.jar
```

Check our [release notes](https://github.com/newrelic/opentelemetry-exporter-java/releases) and verify the version of your 
`opentelemetry-exporter-newrelic-auto-<version>.jar` supports the version of `opentelemetry-javaagent-all.jar`.

#### Configuration System Properties

Currently, the New Relic OpenTelemetry exporter supports the following configuration via system properties. 

| System property                                                                  | Purpose                                                                                                                                                                                                            |
|----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `newrelic.api.key`                                                               | **[Required]** The [Insights insert key](https://docs.newrelic.com/docs/telemetry-data-platform/ingest-manage-data/ingest-apis/use-event-api-report-custom-events#register) to report telemetry data to New Relic. |
| `newrelic.service.name`                                                          | **[Recommended]** The service name of this JVM instance, default is `(unknown service)`.                                                                                                                           |
| `newrelic.trace.uri.override`                                                    | The New Relic endpoint to connect to for reporting Spans, default is US Prod. For the EU region use: https://trace-api.eu.newrelic.com/trace/v1                                                                    |
| `newrelic.metric.uri.override`                                                   | The New Relic endpoint to connect to for reporting metrics, default is US Prod. For the EU region use: https://metric-api.eu.newrelic.com/metric/v1                                                                |
| `newrelic.enable.audit.logging`                                                  | Enable verbose audit logging to display the JSON batches sent each harvest.                                                                                                                                        |
| `io.opentelemetry.javaagent.slf4j.simpleLogger.log.com.newrelic.telemetry=debug` | Enable `debug` logging for the exporter when running in the auto-instrumentation agent.                                                                                                                            |
| `otel.exporter.newrelic.enable.audit.logging=true`                               | Enable audit logging for the exporter running in the auto-instrumentation agent.                                                                                                                                   |

## Published Artifacts

This project publishes two artifacts in alignment with the two workflows for using the exporter described in [How to use](#How-to-use):

|Group                 |Name                                 |Link                                                                                                   |Description                                                  |
|----------------------|-------------------------------------|-------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
|com.newrelic.telemetry|opentelemetry-exporters-newrelic     |[Maven](https://search.maven.org/artifact/com.newrelic.telemetry/opentelemetry-exporters-newrelic)     |For [Programmatic Usage](#Programmatic-Usage)                |
|com.newrelic.telemetry|opentelemetry-exporters-newrelic-auto|[Maven](https://search.maven.org/artifact/com.newrelic.telemetry/opentelemetry-exporters-newrelic-auto)|For [Auto Instrumentation Usage](#Auto-Instrumentation-Usage)|

Release notes are available [here](https://github.com/newrelic/opentelemetry-exporter-java/releases).

Javadoc for this project can be found here: [![Javadocs][javadoc-image]][javadoc-url]

## Find and use your data

For tips on how to find and query your data in New Relic, see 
[Find trace/span data](https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/introduction-trace-api#view-data). 

For general querying information, see:
- [Query New Relic data](https://docs.newrelic.com/docs/using-new-relic/data/understand-data/query-new-relic-data)
- [Intro to NRQL](https://docs.newrelic.com/docs/query-data/nrql-new-relic-query-language/getting-started/introduction-nrql)

## Building

CI builds are run on Github Actions:

![PR build](https://github.com/newrelic/opentelemetry-exporter-java/workflows/Java%20PR%20build%20(gradle)/badge.svg?branch=main)

Here are the current and past runs of [the PR build action.](https://github.com/newrelic/opentelemetry-exporter-java/actions?query=workflow%3A%22Java+PR+build+%28gradle%29%22)

The project uses gradle 5 for building, and the gradle wrapper is provided.

To compile, run the tests and build the jar:

`$ ./gradlew build`

## Support

Should you need assistance with New Relic products, you are in good hands with several support channels.

If the issue has been confirmed as a bug or is a feature request, file a GitHub issue.

**Support Channels**

* [New Relic Documentation](https://docs.newrelic.com/docs/integrations/open-source-telemetry-integrations/open-source-telemetry-integration-list/new-relics-opentelemetry-integration): Comprehensive guidance for using our platform
* [New Relic Community](https://discuss.newrelic.com/tags/javaagent): The best place to engage in troubleshooting questions
* [New Relic Developer](https://developer.newrelic.com/): Resources for building a custom observability applications
* [New Relic University](https://learn.newrelic.com/): A range of online training for New Relic users of every level

## Privacy
At New Relic we take your privacy and the security of your information seriously, and are committed to protecting your information. We must emphasize the importance of not sharing personal data in public forums, and ask all users to scrub logs and diagnostic information for sensitive information, whether personal, proprietary, or otherwise.

We define “Personal Data” as any information relating to an identified or identifiable individual, including, for example, your name, phone number, post code or zip code, Device ID, IP address, and email address.

For more information, review [New Relic’s General Data Privacy Notice](https://newrelic.com/termsandconditions/privacy).

## Contribute

We encourage your contributions to improve opentelemetry-exporter-java! Keep in mind that when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project.

If you have any questions, or to execute our corporate CLA (which is required if your contribution is on behalf of a company), drop us an email at opensource@newrelic.com.

**A note about vulnerabilities**

As noted in our [security policy](../../security/policy), New Relic is committed to the privacy and security of our customers and their data. We believe that providing coordinated disclosure by security researchers and engaging with the security community are important means to achieve our security goals.

If you believe you have found a security vulnerability in this project or any of New Relic's products or websites, we welcome and greatly appreciate you reporting it to New Relic through [HackerOne](https://hackerone.com/newrelic).

If you would like to contribute to this project, review [these guidelines](./CONTRIBUTING.md).

To [all contributors](https://github.com/newrelic/opentelemetry-exporter-java/graphs/contributors), we thank you!  Without your contribution, this project would not be what it is today.  We also host a community project page dedicated to [OpenTelemetry Exporter (Java)](https://opensource.newrelic.com/projects/newrelic/opentelemetry-exporter-java).

## License
opentelemetry-exporter-java is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.

[javadoc-image]: https://www.javadoc.io/badge/com.newrelic.telemetry/opentelemetry-exporters-newrelic.svg
[javadoc-url]: https://www.javadoc.io/doc/com.newrelic.telemetry/opentelemetry-exporters-newrelic
