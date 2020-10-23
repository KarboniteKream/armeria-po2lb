/*
 * Copyright 2020 Klemen Košir, Koji Lin
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

package io.kream.armeria.po2lb;

import java.util.concurrent.atomic.AtomicLong;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.healthcheck.HealthCheckService;
import com.linecorp.armeria.server.healthcheck.SettableHealthChecker;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;

import io.kream.armeria.po2lb.service.PowerOfTwoServiceDecorator;

public class MyServerExtension extends ServerExtension {
    private final AtomicLong requestCount = new AtomicLong(0);
    private final SettableHealthChecker healthChecker = new SettableHealthChecker();

    public long getRequestCount() {
        return requestCount.longValue();
    }

    public void setHealthy(boolean healthy) {
        healthChecker.setHealthy(healthy);
    }

    @Override
    protected void configure(ServerBuilder sb) throws Exception {
        sb.service("/foo", (ctx, req) -> {
            requestCount.incrementAndGet();
            return HttpResponse.of(HttpStatus.OK);
        });

        sb.service("/bar", (ctx, req) -> {
            requestCount.incrementAndGet();
            return HttpResponse.of(HttpStatus.OK);
        }).decorator(PowerOfTwoServiceDecorator::new);

        sb.service("/internal/health", HealthCheckService.of(healthChecker));
    }

    @Override
    protected boolean runForEachTest() {
        return true;
    }
}
