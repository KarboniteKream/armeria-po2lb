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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.AbstractEndpointSelector;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.EndpointSelectionStrategy;
import com.linecorp.armeria.client.endpoint.EndpointSelector;

public class PowerOfTwoStrategy implements EndpointSelectionStrategy {
    private final Map<EndpointGroup, PowerOfTwoSelector> selectors = new ConcurrentHashMap<>();

    @Override
    public EndpointSelector newSelector(EndpointGroup endpointGroup) {
        final PowerOfTwoSelector selector = new PowerOfTwoSelector(endpointGroup);
        selectors.put(endpointGroup, selector);
        return selector;
    }

    public void setInFlightCount(EndpointGroup endpointGroup, Endpoint endpoint, long count) {
        selectors.get(endpointGroup).setInFlightCount(endpoint, count);
    }

    public void increaseInFlightCount(EndpointGroup endpointGroup, Endpoint endpoint) {
        selectors.get(endpointGroup).increaseInFlightCount(endpoint);
    }

    public void decreaseInFlightCount(EndpointGroup endpointGroup, Endpoint endpoint) {
        selectors.get(endpointGroup).decreaseInFlightCount(endpoint);
    }

    private static class PowerOfTwoSelector extends AbstractEndpointSelector {
        private static final AtomicLong DEFAULT_VALUE = new AtomicLong(0);

        private final Map<Endpoint, AtomicLong> inFlightCount = new ConcurrentHashMap<>();

        /**
         * Creates a new instance that selects an {@link Endpoint} from the specified {@link EndpointGroup}.
         *
         * @param endpointGroup Endpoint group.
         */
        protected PowerOfTwoSelector(EndpointGroup endpointGroup) {
            super(endpointGroup);

            endpointGroup.addListener(endpoints -> {
                final Set<Endpoint> newEndpoints = new HashSet<>(endpoints);
                inFlightCount.keySet().removeIf(endpoint -> !newEndpoints.contains(endpoint));
            });
        }

        public void setInFlightCount(Endpoint endpoint, long count) {
            inFlightCount.compute(endpoint, (key, value) -> {
                if (value == null) {
                    value = new AtomicLong(0);
                }

                value.set(count);
                return value;
            });
        }

        public void increaseInFlightCount(Endpoint endpoint) {
            inFlightCount.compute(endpoint, (key, value) -> {
                if (value == null) {
                    value = new AtomicLong(0);
                }

                value.incrementAndGet();
                return value;
            });
        }

        public void decreaseInFlightCount(Endpoint endpoint) {
            inFlightCount.compute(endpoint, (key, value) -> {
                if (value == null) {
                    return new AtomicLong(0);
                }

                value.decrementAndGet();
                return value;
            });
        }

        @Nullable
        @Override
        public Endpoint selectNow(ClientRequestContext ctx) {
            final List<Endpoint> endpoints = group().endpoints();

            if (endpoints.isEmpty()) {
                return null;
            }

            final Endpoint first;
            final Endpoint second;

            if (endpoints.size() == 2) {
                first = endpoints.get(0);
                second = endpoints.get(1);
            } else {
                final int firstIndex = ThreadLocalRandom.current().nextInt(endpoints.size());
                int secondIndex = ThreadLocalRandom.current().nextInt(endpoints.size() - 1);

                if (secondIndex >= firstIndex) {
                    secondIndex += 1;
                }

                first = endpoints.get(firstIndex);
                second = endpoints.get(secondIndex);
            }

            final long firstLoad = inFlightCount.getOrDefault(first, DEFAULT_VALUE).get();
            final long secondLoad = inFlightCount.getOrDefault(second, DEFAULT_VALUE).get();

            return firstLoad <= secondLoad ? first : second;
        }
    }
}
