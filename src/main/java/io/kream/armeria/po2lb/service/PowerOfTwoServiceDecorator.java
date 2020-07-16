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

package io.kream.armeria.po2lb.service;

import java.util.concurrent.atomic.AtomicLong;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.SimpleDecoratingHttpService;

public class PowerOfTwoServiceDecorator extends SimpleDecoratingHttpService {
    private final AtomicLong inFlightCount = new AtomicLong(0);

    /**
     * Creates a new instance that decorates the specified {@link HttpService}.
     * @param delegate HttpService delegate.
     */
    public PowerOfTwoServiceDecorator(HttpService delegate) {
        super(delegate);
    }

    @Override
    public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
        ctx.addAdditionalResponseHeader("X-Armeria-Request-Count", inFlightCount.getAndIncrement());
        final HttpResponse response = unwrap().serve(ctx, req);

        response.whenComplete().whenComplete((result, exception) -> {
            inFlightCount.decrementAndGet();
        });

        return response;
    }
}
