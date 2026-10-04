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
package org.rulii.explorer.builder;

import org.rulii.explorer.descriptor.Placeholder;
import org.rulii.explorer.expression.plain.Placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The values a script's {@code ${key:default}} placeholders compiled with, read back from the two
 * texts rulii keeps: the source as written and the resolved text the compiler saw. Nothing is
 * looked up; the literal text between the placeholders anchors each value.
 *
 * <p>The alignment is done twice, from the left and from the right, and a value is reported only
 * when both agree. A text that cannot be aligned that way (adjacent placeholders, a value that
 * repeats the text around it, an escaped {@code \${}}, or a resolved text identical to the source
 * because nothing was resolved) yields the placeholders without values rather than a guess.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 *
 */
public final class PlaceholderValues {

    private PlaceholderValues() {
        super();
    }

    /**
     * The placeholders of {@code source} in source order, as written.
     *
     * @param source the script as written; may be null.
     * @return the placeholders; empty when there are none.
     */
    public static List<Placeholder> asWritten(String source) {
        return Placeholders.find(source).stream().map(s -> Placeholder.of(s.key(), s.defaultValue())).toList();
    }

    /**
     * The placeholders of {@code source} in source order, with the values {@code resolved} carries.
     *
     * @param source   the script as written; may be null.
     * @param resolved the text the compiler saw; null when unknown.
     * @return the placeholders; a value is null when it could not be read back without doubt.
     */
    public static List<Placeholder> resolve(String source, String resolved) {
        List<Placeholders.Span> spans = Placeholders.find(source);
        if (spans.isEmpty()) return List.of();
        List<String> values = values(source, spans, resolved);
        List<Placeholder> out = new ArrayList<>(spans.size());
        for (int i = 0; i < spans.size(); i++) {
            Placeholder placeholder = Placeholder.of(spans.get(i).key(), spans.get(i).defaultValue());
            out.add(values.get(i) != null ? placeholder.withValue(values.get(i)) : placeholder);
        }
        return out;
    }

    /** One value per span, or all null when the alignment is not certain. */
    static List<String> values(String source, List<Placeholders.Span> spans, String resolved) {
        List<String> none = Collections.nCopies(spans.size(), null);
        if (resolved == null || resolved.equals(source)) return none;

        List<String> segments = new ArrayList<>(spans.size() + 1);
        int at = 0;
        for (Placeholders.Span span : spans) {
            segments.add(source.substring(at, span.start()));
            at = span.end();
        }
        segments.add(source.substring(at));
        for (int i = 1; i < segments.size() - 1; i++) {
            if (segments.get(i).isEmpty()) return none;
        }

        List<String> forward = forward(segments, resolved);
        List<String> backward = backward(segments, resolved);
        return forward != null && forward.equals(backward) ? forward : none;
    }

    private static List<String> forward(List<String> segments, String resolved) {
        String first = segments.get(0);
        String last = segments.get(segments.size() - 1);
        if (!resolved.startsWith(first) || !resolved.endsWith(last)) return null;
        int end = resolved.length() - last.length();
        int cursor = first.length();
        List<String> values = new ArrayList<>();
        for (int i = 1; i < segments.size() - 1; i++) {
            String segment = segments.get(i);
            int next = resolved.indexOf(segment, cursor);
            if (next < 0 || next + segment.length() > end) return null;
            values.add(resolved.substring(cursor, next));
            cursor = next + segment.length();
        }
        if (cursor > end) return null;
        values.add(resolved.substring(cursor, end));
        return values;
    }

    private static List<String> backward(List<String> segments, String resolved) {
        String first = segments.get(0);
        String last = segments.get(segments.size() - 1);
        if (!resolved.startsWith(first) || !resolved.endsWith(last)) return null;
        int start = first.length();
        int cursor = resolved.length() - last.length();
        List<String> values = new ArrayList<>();
        for (int i = segments.size() - 2; i >= 1; i--) {
            String segment = segments.get(i);
            int previous = cursor - segment.length() < start ? -1 : resolved.lastIndexOf(segment, cursor - segment.length());
            if (previous < start) return null;
            values.add(resolved.substring(previous + segment.length(), cursor));
            cursor = previous;
        }
        if (cursor < start) return null;
        values.add(resolved.substring(start, cursor));
        Collections.reverse(values);
        return values;
    }
}
