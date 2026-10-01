/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (c) 1999-2026, Algorithmx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rulii.explorer.boot;

import org.junit.jupiter.api.Test;
import org.rulii.model.Runnable;
import org.rulii.model.UnrulyException;
import org.rulii.registry.RuleRegistry;
import org.rulii.rule.Rule;
import org.rulii.ruleset.RuleSet;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A registry that throws: the application starts, the endpoint answers 500 with an error payload,
 * and nothing else is affected (NFR-22).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoints.web.exposure.include=rulii,health")
class BuildFailureTest {

    @TestConfiguration
    static class BrokenRegistry {
        @Bean
        @Primary
        RuleRegistry brokenRuleRegistry() {
            return new RuleRegistry() {
                @Override public boolean isNameInUse(String name) { return false; }
                @Override public int getCount() { return 0; }
                @Override public List<Rule> getRules() { return List.of(); }
                @SuppressWarnings("rawtypes") @Override public List<RuleSet> getRuleSets() { return List.of(); }
                @Override public <R, T extends Runnable<R>> T get(String name) { return null; }
                @Override public Set<String> getNames() { throw new UnrulyException("registry exploded"); }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Test
    void failureBecomesAnErrorPayload() {
        Http.Response response = Http.get(port, "/actuator/rulii");
        assertEquals(500, response.status());
        assertTrue(response.body().contains("\"descriptorVersion\":\"1.0\""), response.body());
        assertTrue(response.body().contains("registry exploded"), response.body());
        assertEquals(200, Http.get(port, "/actuator/health").status(), "the application is unaffected");
    }
}
