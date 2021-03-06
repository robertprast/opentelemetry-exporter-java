/*
 * Copyright 2020 New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.telemetry.opentelemetry.export;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.newrelic.telemetry.Attributes;
import com.newrelic.telemetry.TelemetryClient;
import com.newrelic.telemetry.spans.SpanBatch;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewRelicSpanExporterTest {

  private final String spanId = "000000000012d685";
  private final String traceId = "000000000063d76f0000000037fe0393";

  @Mock private TelemetryClient sender;
  @Mock private SpanBatchAdapter adapter;

  @Test
  void testExportHappyPath() {
    NewRelicSpanExporter testClass = new NewRelicSpanExporter(adapter, sender);

    SpanData inputSpan = createMinimalSpanData();
    List<SpanData> spans = Collections.singletonList(inputSpan);
    SpanBatch batch = new SpanBatch(Collections.emptyList(), new Attributes());
    when(adapter.adaptToSpanBatches(spans)).thenReturn(Collections.singleton(batch));

    CompletableResultCode result = testClass.export(spans);
    assertTrue(result.isSuccess());
  }

  private SpanData createMinimalSpanData() {
    return TestSpanData.newBuilder()
        .setTraceId(traceId)
        .setSpanId(spanId)
        .setResource(Resource.create(io.opentelemetry.api.common.Attributes.empty()))
        .setName("spanName")
        .setKind(Span.Kind.SERVER)
        .setStatus(SpanData.Status.ok())
        .setStartEpochNanos(456_001_000L)
        .setEndEpochNanos(456_001_100L)
        .setHasEnded(true)
        .build();
  }
}
