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
package org.rulii.explorer.descriptor;

import java.util.List;

/**
 * One rule flow command. Which fields are set depends on {@link #type()}:
 * <ul>
 *   <li>{@code bind}: {@code bind}</li>
 *   <li>{@code run}: {@code target}, {@code as}, {@code scope}, {@code params}, {@code handler}</li>
 *   <li>{@code apply}: {@code expression}, {@code as}, {@code scope}, {@code params}, {@code handler}</li>
 *   <li>{@code execute}: {@code expression}, {@code handler}</li>
 *   <li>{@code async-run}: {@code target}, {@code as}, {@code mode}, {@code thenRun}, {@code handler}</li>
 *   <li>{@code await}: {@code awaitKind}, {@code names}, {@code timeout}</li>
 *   <li>{@code when}: {@code condition}, {@code then}, {@code otherwise}</li>
 *   <li>{@code for-each}: {@code item}, {@code source}, {@code stop}, {@code body}</li>
 *   <li>{@code scope}: {@code name}, {@code body}</li>
 *   <li>{@code exit}: {@code expression} (null when the flow returns its rule context)</li>
 *   <li>{@code custom}: {@code className}, {@code body}</li>
 * </ul>
 *
 * @param type       the command type.
 * @param target     what a run step runs.
 * @param as         binding name for the result.
 * @param scope      scope the result is bound in.
 * @param params     parameters bound in a fresh scope for the step.
 * @param bind       what a bind step binds.
 * @param expression the function, action or result extractor.
 * @param condition  the condition of a when step.
 * @param source     the collection function of a for-each step.
 * @param stop       the stop condition of a for-each step.
 * @param item       the item binding name of a for-each step.
 * @param name       the scope name.
 * @param mode       the async context mode: shared or immutable.
 * @param awaitKind  one, all or any.
 * @param names      the futures awaited.
 * @param timeout    the await timeout as an ISO-8601 duration ({@code PT5S}).
 * @param thenRun    the continuation of an async run.
 * @param handler    the step-level exception handler.
 * @param then       the commands run when a condition holds.
 * @param otherwise  the commands run when it does not.
 * @param body       the commands of a for-each, scope or custom container step.
 * @param className  the class of a custom command.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public record Command(CommandType type, Target target, String as, String scope, BindInfo params, BindInfo bind,
                      Expression expression, Expression condition, Expression source, Expression stop, String item,
                      String name, String mode, String awaitKind, List<String> names, String timeout,
                      Continuation thenRun, Handler handler, List<Command> then, List<Command> otherwise,
                      List<Command> body, String className) {

    public Command {
        names = names == null ? null : Lists.copy(names);
        then = then == null ? null : Lists.copy(then);
        otherwise = otherwise == null ? null : Lists.copy(otherwise);
        body = body == null ? null : Lists.copy(body);
    }

    /** Assembles a command one field at a time; unset fields stay null. */
    public static final class Builder {
        private CommandType type;
        private Target target;
        private String as;
        private String scope;
        private BindInfo params;
        private BindInfo bind;
        private Expression expression;
        private Expression condition;
        private Expression source;
        private Expression stop;
        private String item;
        private String name;
        private String mode;
        private String awaitKind;
        private List<String> names;
        private String timeout;
        private Continuation thenRun;
        private Handler handler;
        private List<Command> then;
        private List<Command> otherwise;
        private List<Command> body;
        private String className;

        public Builder() {
            super();
        }

        public Builder type(CommandType v) { type = v; return this; }
        public Builder target(Target v) { target = v; return this; }
        public Builder as(String v) { as = v; return this; }
        public Builder scope(String v) { scope = v; return this; }
        public Builder params(BindInfo v) { params = v; return this; }
        public Builder bind(BindInfo v) { bind = v; return this; }
        public Builder expression(Expression v) { expression = v; return this; }
        public Builder condition(Expression v) { condition = v; return this; }
        public Builder source(Expression v) { source = v; return this; }
        public Builder stop(Expression v) { stop = v; return this; }
        public Builder item(String v) { item = v; return this; }
        public Builder name(String v) { name = v; return this; }
        public Builder mode(String v) { mode = v; return this; }
        public Builder awaitKind(String v) { awaitKind = v; return this; }
        public Builder names(List<String> v) { names = v; return this; }
        public Builder timeout(String v) { timeout = v; return this; }
        public Builder thenRun(Continuation v) { thenRun = v; return this; }
        public Builder handler(Handler v) { handler = v; return this; }
        public Builder then(List<Command> v) { then = v; return this; }
        public Builder otherwise(List<Command> v) { otherwise = v; return this; }
        public Builder body(List<Command> v) { body = v; return this; }
        public Builder className(String v) { className = v; return this; }

        public Command build() {
            return new Command(type, target, as, scope, params, bind, expression, condition, source, stop, item, name, mode,
                    awaitKind, names, timeout, thenRun, handler, then, otherwise, body, className);
        }
    }
}
