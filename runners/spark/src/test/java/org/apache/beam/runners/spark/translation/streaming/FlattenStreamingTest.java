/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.runners.spark.translation.streaming;

import org.apache.beam.runners.spark.EvaluationResult;
import org.apache.beam.runners.spark.SparkRunner;
import org.apache.beam.runners.spark.SparkStreamingPipelineOptions;
import org.apache.beam.runners.spark.io.CreateStream;
import org.apache.beam.runners.spark.translation.streaming.utils.PAssertStreaming;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;

import org.joda.time.Duration;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Test Flatten (union) implementation for streaming.
 */
public class FlattenStreamingTest {

  private static final String[] WORDS_ARRAY_1 = {
      "one", "two", "three", "four"};
  private static final List<Iterable<String>> WORDS_QUEUE_1 =
      Collections.<Iterable<String>>singletonList(Arrays.asList(WORDS_ARRAY_1));
  private static final String[] WORDS_ARRAY_2 = {
          "five", "six", "seven", "eight"};
  private static final List<Iterable<String>> WORDS_QUEUE_2 =
          Collections.<Iterable<String>>singletonList(Arrays.asList(WORDS_ARRAY_2));
  private static final String[] EXPECTED_UNION = {
          "one", "two", "three", "four", "five", "six", "seven", "eight"};
  private static final long TEST_TIMEOUT_MSEC = 1000L;

  @Test
  public void testRun() throws Exception {
    SparkStreamingPipelineOptions options =
        PipelineOptionsFactory.as(SparkStreamingPipelineOptions.class);
    options.setRunner(SparkRunner.class);
    options.setStreaming(true);
    options.setTimeout(TEST_TIMEOUT_MSEC); // run for one interval
    Pipeline p = Pipeline.create(options);

    PCollection<String> w1 =
            p.apply(CreateStream.fromQueue(WORDS_QUEUE_1)).setCoder(StringUtf8Coder.of());
    PCollection<String> windowedW1 =
            w1.apply(Window.<String>into(FixedWindows.of(Duration.standardSeconds(1))));
    PCollection<String> w2 =
            p.apply(CreateStream.fromQueue(WORDS_QUEUE_2)).setCoder(StringUtf8Coder.of());
    PCollection<String> windowedW2 =
            w2.apply(Window.<String>into(FixedWindows.of(Duration.standardSeconds(1))));
    PCollectionList<String> list = PCollectionList.of(windowedW1).and(windowedW2);
    PCollection<String> union = list.apply(Flatten.<String>pCollections());

    PAssertStreaming.assertContents(union, EXPECTED_UNION);

    EvaluationResult res = SparkRunner.create(options).run(p);
    res.close();
  }

}
