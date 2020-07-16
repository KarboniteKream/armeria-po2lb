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

package io.kream.armeria.po2lb.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;

class PowerOfTwoStrategyTest {
    @Test
    void selectNow() {
        final PowerOfTwoStrategy strategy = new PowerOfTwoStrategy();
        final Endpoint endpoint1 = Endpoint.of("192.168.12.1", 8080);
        final Endpoint endpoint2 = Endpoint.of("192.168.12.2", 8080);
        final Endpoint endpoint3 = Endpoint.of("192.168.12.3", 8080);
        final EndpointGroup endpointGroup = EndpointGroup.of(strategy, endpoint1, endpoint2, endpoint3);
        final ClientRequestContext ctx = ClientRequestContext.of(HttpRequest.of(HttpMethod.GET, "/"));

        strategy.increaseInFlightCount(endpointGroup, endpoint1);
        strategy.increaseInFlightCount(endpointGroup, endpoint1);
        strategy.increaseInFlightCount(endpointGroup, endpoint3);

        for (int i = 0; i < 100; i++) {
            final Endpoint endpoint = endpointGroup.selectNow(ctx);
            assertThat(endpoint).isNotEqualTo(endpoint1);
        }

        strategy.setInFlightCount(endpointGroup, endpoint2, 3);

        for (int i = 0; i < 100; i++) {
            final Endpoint endpoint = endpointGroup.selectNow(ctx);
            assertThat(endpoint).isNotEqualTo(endpoint2);
        }
    }
}
