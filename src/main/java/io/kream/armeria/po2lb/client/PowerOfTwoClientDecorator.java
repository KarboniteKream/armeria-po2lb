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

package io.kream.armeria.po2lb.client;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingHttpClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;

import io.kream.armeria.po2lb.common.PowerOfTwoStrategy;

public class PowerOfTwoClientDecorator extends SimpleDecoratingHttpClient {
    private final PowerOfTwoStrategy strategy;

    /**
     * Creates a new instance that decorates the specified {@link HttpClient}.
     * @param delegate HttpClient delegate.
     * @param strategy Endpoint selection strategy.
     */
    public PowerOfTwoClientDecorator(HttpClient delegate, PowerOfTwoStrategy strategy) {
        super(delegate);
        this.strategy = strategy;
    }

    @Override
    public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) throws Exception {
        final EndpointGroup endpointGroup = ctx.endpointGroup();
        final Endpoint endpoint = ctx.endpoint();

        if (endpointGroup == null || endpoint == null) {
            return unwrap().execute(ctx, req);
        }

        strategy.increaseInFlightCount(endpointGroup, endpoint);
        final HttpResponse response = unwrap().execute(ctx, req);

        response.whenComplete().whenComplete((result, throwable) -> {
            strategy.decreaseInFlightCount(endpointGroup, endpoint);
        });

        return response;
    }
}
