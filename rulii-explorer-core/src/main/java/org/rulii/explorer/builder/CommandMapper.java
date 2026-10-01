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

import org.rulii.explorer.descriptor.BindInfo;
import org.rulii.explorer.descriptor.BoundName;
import org.rulii.explorer.descriptor.Command;
import org.rulii.explorer.descriptor.CommandType;
import org.rulii.explorer.descriptor.Continuation;
import org.rulii.explorer.descriptor.Handler;
import org.rulii.explorer.descriptor.ReferenceType;
import org.rulii.explorer.descriptor.Resolution;
import org.rulii.explorer.descriptor.Target;
import org.rulii.explorer.descriptor.TargetKind;
import org.rulii.explorer.descriptor.Reference;
import org.rulii.ruleflow.info.CommandInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Maps a flow's {@link CommandInfo} tree to descriptor {@link Command}s, resolving run targets
 * through the {@link Describer} and recording the references (FR-13, FR-21).
 *
 * @author Max Arulananthan
 * @since 1.0
 */
final class CommandMapper {

    private final Describer describer;
    private final Expressions expressions;
    private final String flowId;

    CommandMapper(Describer describer, Expressions expressions, String flowId) {
        super();
        this.describer = describer;
        this.expressions = expressions;
        this.flowId = flowId;
    }

    List<Command> map(List<CommandInfo> infos, String prefix) {
        List<Command> result = new ArrayList<>();
        if (infos == null) return result;
        for (int i = 0; i < infos.size(); i++) result.add(map(infos.get(i), prefix + "[" + i + "]"));
        return result;
    }

    Handler handler(CommandInfo.Handler handler, String path) {
        if (handler == null) return null;
        return new Handler(handler.exceptionType().getName(), map(handler.body(), path));
    }

    private Command map(CommandInfo info, String path) {
        Command.Builder b = new Command.Builder();

        if (info instanceof CommandInfo.Run run) {
            return b.type(CommandType.RUN).target(target(run.target(), path, false)).as(run.as()).scope(run.scope())
                    .params(bind(run.params())).handler(handler(run.handler(), path + ".handler")).build();
        }
        if (info instanceof CommandInfo.Apply apply) {
            return b.type(CommandType.APPLY).expression(expressions.mapFunction(apply.fn())).as(apply.as()).scope(apply.scope())
                    .params(bind(apply.params())).handler(handler(apply.handler(), path + ".handler")).build();
        }
        if (info instanceof CommandInfo.Execute execute) {
            return b.type(CommandType.EXECUTE).expression(expressions.mapAction(execute.action()))
                    .handler(handler(execute.handler(), path + ".handler")).build();
        }
        if (info instanceof CommandInfo.AsyncRun async) {
            Continuation then = async.then() == null ? null
                    : new Continuation(async.then().as(), map(async.then().body(), path + ".thenRun"));
            return b.type(CommandType.ASYNC_RUN).target(target(async.target(), path, true)).as(async.as())
                    .mode(async.mode().name().toLowerCase(Locale.ROOT)).thenRun(then)
                    .handler(handler(async.handler(), path + ".handler")).build();
        }
        if (info instanceof CommandInfo.Await await) {
            return b.type(CommandType.AWAIT).awaitKind(await.kind().name().toLowerCase(Locale.ROOT)).names(await.names())
                    .timeout(await.timeout().toString()).build();
        }
        if (info instanceof CommandInfo.Bind bind) {
            return b.type(CommandType.BIND).bind(bind(bind)).build();
        }
        if (info instanceof CommandInfo.When when) {
            return b.type(CommandType.WHEN).condition(expressions.mapCondition(when.condition()))
                    .then(map(when.then(), path + ".then")).otherwise(map(when.otherwise(), path + ".otherwise")).build();
        }
        if (info instanceof CommandInfo.ForEach forEach) {
            return b.type(CommandType.FOR_EACH).item(forEach.item()).source(expressions.mapFunction(forEach.source()))
                    .stop(expressions.mapCondition(forEach.stop())).body(map(forEach.body(), path + ".body")).build();
        }
        if (info instanceof CommandInfo.Scope scope) {
            return b.type(CommandType.SCOPE).name(scope.name()).body(map(scope.body(), path + ".body")).build();
        }
        if (info instanceof CommandInfo.Exit exit) {
            return b.type(CommandType.EXIT).expression(expressions.mapFunction(exit.result())).build();
        }
        CommandInfo.Custom custom = (CommandInfo.Custom) info;
        return b.type(CommandType.CUSTOM).className(custom.className()).body(map(custom.body(), path + ".body")).build();
    }

    private Target target(CommandInfo.Target target, String path, boolean async) {
        if (target instanceof CommandInfo.Target.Instance instance) {
            String id = describer.idOfInstance(instance.runnable(), flowId + "/" + path + "/target");
            describer.reference(new Reference(flowId, id, ReferenceType.RUNS, async, path, Resolution.DIRECT));
            return new Target(TargetKind.INSTANCE, id, null, null, Resolution.DIRECT);
        }
        if (target instanceof CommandInfo.Target.ByName byName) {
            String id = describer.idByName(byName.name());
            if (id != null) describer.reference(new Reference(flowId, id, ReferenceType.RUNS, async, path, Resolution.BY_NAME));
            return new Target(TargetKind.BY_NAME, id, byName.name(), null, id != null ? Resolution.BY_NAME : Resolution.UNRESOLVED);
        }
        CommandInfo.Target.ByClass byClass = (CommandInfo.Target.ByClass) target;
        String id = describer.idByClass(byClass.type());
        if (id != null) describer.reference(new Reference(flowId, id, ReferenceType.RUNS, async, path, Resolution.BY_CLASS));
        return new Target(TargetKind.BY_CLASS, id, null, byClass.type().getName(), id != null ? Resolution.BY_CLASS : Resolution.UNRESOLVED);
    }

    private BindInfo bind(CommandInfo.Bind bind) {
        if (bind == null) return null;
        Stream<CommandInfo.BoundName> names = bind.names().stream();
        // Declarations keep their declared order; names collected from a map, a bean or a Bindings
        // instance have no meaningful order (and Map.of iterates differently per JVM run), so sort.
        if (bind.kind() == CommandInfo.BindKind.MAP || bind.kind() == CommandInfo.BindKind.BEAN
                || bind.kind() == CommandInfo.BindKind.BINDINGS) {
            names = names.sorted(Comparator.comparing(CommandInfo.BoundName::name));
        }
        List<BoundName> mapped = names
                .map(n -> new BoundName(n.name(), Expressions.typeName(n.type()), expressions.mapFunction(n.expression())))
                .toList();
        return new BindInfo(bind.scope(), bind.kind().name().toLowerCase(Locale.ROOT), mapped, bind.label());
    }
}
