/*
 * Copyright 2015, 2020 StreamEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.util.streamex.performance;

import one.util.streamex.IntStreamEx;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class PrefixIntStreamBenchmark {
    @Param({"100000"})
    private int N;

    @Benchmark
    public int[] parallelOrdered() {
        return IntStreamEx.range(N).parallel().prefix(Integer::sum).toArray();
    }

    @Benchmark
    public int[] parallelUnordered() {
        return IntStreamEx.range(N).unordered().parallel().prefix(Integer::sum).toArray();
    }
}
