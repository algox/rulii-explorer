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
package org.rulii.explorer.fixture;

import org.rulii.model.Runnable;
import org.rulii.registry.RuleRegistry;
import org.rulii.rule.Rule;
import org.rulii.ruleset.RuleSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * A registry keyed by arbitrary names, like Spring bean names, so tests can register a rule
 * under a name that differs from its own. An entry may also be a supplier that throws, to
 * simulate a bean that fails to instantiate.
 */
public final class MapRegistry implements RuleRegistry {

    private final Map<String, Supplier<Runnable<?>>> entries = new LinkedHashMap<>();

    public MapRegistry register(String name, Runnable<?> runnable) {
        entries.put(name, () -> runnable);
        return this;
    }

    public MapRegistry registerFailing(String name, RuntimeException failure) {
        entries.put(name, () -> { throw failure; });
        return this;
    }

    @Override
    public boolean isNameInUse(String name) {
        return entries.containsKey(name);
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public List<Rule> getRules() {
        List<Rule> rules = new ArrayList<>();
        for (String name : entries.keySet()) {
            Runnable<?> runnable = get(name);
            if (runnable instanceof Rule rule) rules.add(rule);
        }
        return rules;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<RuleSet> getRuleSets() {
        List<RuleSet> ruleSets = new ArrayList<>();
        for (String name : entries.keySet()) {
            Runnable<?> runnable = get(name);
            if (runnable instanceof RuleSet<?> ruleSet) ruleSets.add(ruleSet);
        }
        return ruleSets;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R, T extends Runnable<R>> T get(String name) {
        Supplier<Runnable<?>> supplier = entries.get(name);
        return supplier == null ? null : (T) supplier.get();
    }

    @Override
    public Set<String> getNames() {
        return Collections.unmodifiableSet(new TreeSet<>(entries.keySet()));
    }
}
