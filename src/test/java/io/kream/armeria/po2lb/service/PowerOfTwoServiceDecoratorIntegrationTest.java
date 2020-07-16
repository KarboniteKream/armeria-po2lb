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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.healthcheck.HealthCheckedEndpointGroup;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.SessionProtocol;

import io.kream.armeria.po2lb.MyServerExtension;
import io.kream.armeria.po2lb.common.PowerOfTwoStrategy;

class PowerOfTwoServiceDecoratorIntegrationTest {
    @RegisterExtension
    final MyServerExtension server1 = new MyServerExtension();

    @RegisterExtension
    final MyServerExtension server2 = new MyServerExtension();

    @RegisterExtension
    final MyServerExtension server3 = new MyServerExtension();

    @RegisterExtension
    final MyServerExtension server4 = new MyServerExtension();

    private WebClient client;

    @Test
    void bar() {
        final PowerOfTwoStrategy strategy = new PowerOfTwoStrategy();
        final EndpointGroup endpointGroup = EndpointGroup.of(strategy, server1.httpEndpoint());

        client = Clients.builder(SessionProtocol.HTTP, endpointGroup)
                        .decorator(httpClient -> new PowerOfTwoClientDecorator(httpClient, strategy))
                        .build(WebClient.class);

        final AggregatedHttpResponse response = client.get("/bar").aggregate().join();
        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.headers().getLong("X-Armeria-Request-Count")).isNotNull();
    }

    @Test
    void bar_group() {
        final Endpoint endpoint1 = server1.httpEndpoint();
        final Endpoint endpoint2 = server2.httpEndpoint();
        final PowerOfTwoStrategy strategy = new PowerOfTwoStrategy();
        final EndpointGroup endpointGroup = EndpointGroup.of(strategy, endpoint1, endpoint2);

        client = Clients.builder(SessionProtocol.HTTP, endpointGroup)
                        .decorator(httpClient -> new PowerOfTwoClientDecorator(httpClient, strategy))
                        .build(WebClient.class);

        final AggregatedHttpResponse response = client.get("/bar").aggregate().join();
        assertThat(response.status()).isEqualTo(HttpStatus.OK);
        assertThat(response.headers().getLong("X-Armeria-Request-Count")).isNotNull();
    }

    @Test
    void bar_health_check() throws Exception {
        final Endpoint endpoint1 = server1.httpEndpoint();
        final Endpoint endpoint2 = server2.httpEndpoint();
        final Endpoint endpoint3 = server3.httpEndpoint();
        final Endpoint endpoint4 = server4.httpEndpoint();
        final PowerOfTwoStrategy strategy = new PowerOfTwoStrategy();

        final HealthCheckedEndpointGroup endpointGroup = HealthCheckedEndpointGroup.of(
                EndpointGroup.of(strategy, endpoint1, endpoint2, endpoint3, endpoint4),
                "/internal/health");

        client = Clients.builder(SessionProtocol.HTTP, endpointGroup)
                        .decorator(httpClient -> new PowerOfTwoClientDecorator(httpClient, strategy))
                        .build(WebClient.class);

        for (int i = 0; i < 100; i++) {
            final AggregatedHttpResponse response = client.get("/bar").aggregate().join();
            assertThat(response.status()).isEqualTo(HttpStatus.OK);
        }

        server2.setHealth(false);
        await().until(() -> endpointGroup.endpoints().size() == 3);

        final long previousCount = server2.getRequestCount();
        for (int i = 0; i < 100; i++) {
            final AggregatedHttpResponse response = client.get("/bar").aggregate().join();
            assertThat(response.status()).isEqualTo(HttpStatus.OK);
        }
        assertThat(server2.getRequestCount()).isEqualTo(previousCount);
    }
}
