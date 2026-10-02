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
package org.rulii.explorer.expression.script;

import java.util.List;

/**
 * The syntax tree of the JavaScript and Java subset the explorer reads: expressions, simple
 * statements, and {@link Opaque} for constructs it parses past but does not interpret. Every
 * node keeps its offsets in the script so an untranslatable part can be shown as written.
 *
 * @author Max Arulananthan
 * @since 1.0
 */
sealed interface ScriptNode {

    int start();

    int end();

    // ── Expressions ─────────────────────────────────────────────────────────

    record Ident(String name, int start, int end) implements ScriptNode { }

    record Num(String text, int start, int end) implements ScriptNode { }

    /** @param value the unescaped string value. */
    record Str(String value, int start, int end) implements ScriptNode { }

    /** @param text the literal as written, backticks included. */
    record Template(String text, int start, int end) implements ScriptNode { }

    record Bool(boolean value, int start, int end) implements ScriptNode { }

    /** {@code null} or {@code undefined}. */
    record Nothing(int start, int end) implements ScriptNode { }

    record ArrayLit(List<ScriptNode> items, int start, int end) implements ScriptNode { }

    /** {@code object.name} or {@code object?.name}. */
    record Member(ScriptNode object, String name, int start, int end) implements ScriptNode { }

    /** {@code object[index]}. */
    record Index(ScriptNode object, ScriptNode index, int start, int end) implements ScriptNode { }

    record Call(ScriptNode callee, List<ScriptNode> args, int start, int end) implements ScriptNode { }

    /** A Java cast, {@code (int) days}: the type changes, the meaning does not. */
    record Cast(String type, ScriptNode operand, int start, int end) implements ScriptNode { }

    /** {@code !}, {@code -}, {@code +}, {@code typeof}, {@code void}, {@code delete}. */
    record Unary(String op, ScriptNode operand, int start, int end) implements ScriptNode { }

    /** {@code ++} or {@code --}, prefix or postfix. */
    record Update(String op, ScriptNode target, int start, int end) implements ScriptNode { }

    /** Arithmetic, comparison, bitwise, {@code in} and {@code instanceof}. */
    record Binary(String op, ScriptNode left, ScriptNode right, int start, int end) implements ScriptNode { }

    /** {@code &&}, {@code ||} or {@code ??}. */
    record Logical(String op, ScriptNode left, ScriptNode right, int start, int end) implements ScriptNode { }

    /** {@code =} and the compound assignments. */
    record Assign(String op, ScriptNode target, ScriptNode value, int start, int end) implements ScriptNode { }

    record Cond(ScriptNode test, ScriptNode then, ScriptNode otherwise, int start, int end) implements ScriptNode { }

    /** Something parsed past but not read: a function, an object literal, {@code new}, a regular expression, {@code this}. */
    record Opaque(String what, int start, int end) implements ScriptNode { }

    // ── Statements ──────────────────────────────────────────────────────────

    /**
     * @param kind {@code var}, {@code let}, {@code const}, or the declared Java type.
     * @param init the initial value, or null.
     */
    record VarDecl(String kind, String name, ScriptNode init, int start, int end) implements ScriptNode { }

    /** @param value the returned expression, or null. */
    record Return(ScriptNode value, int start, int end) implements ScriptNode { }

    /** @param otherwise the else branch, or null. */
    record If(ScriptNode test, List<ScriptNode> then, List<ScriptNode> otherwise, int start, int end) implements ScriptNode { }

    record Program(List<ScriptNode> statements, int start, int end) implements ScriptNode { }
}
