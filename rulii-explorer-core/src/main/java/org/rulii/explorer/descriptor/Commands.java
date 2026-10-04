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
import java.util.function.BiConsumer;

/**
 * Walks a rule flow command tree, giving each command its path: {@code commands[2]},
 * {@code commands[2].then[0]}, {@code commands[4].handler[0]}, {@code commands[3].thenRun[1]},
 * {@code commands[1].body[0]}, {@code globalHandler[0]}. Problems and references use these paths.
 *
 * @author Algorithmx Development Team
 * @since 1.0
 */
public final class Commands {

    private Commands() {
        super();
    }

    /**
     * Visits every command of a flow, depth first, in order.
     *
     * @param flow    the flow details.
     * @param visitor receives the path and the command.
     */
    public static void walk(RuleFlowDetails flow, BiConsumer<String, Command> visitor) {
        walk(flow.commands(), "commands", visitor);
        if (flow.globalHandler() != null) walk(flow.globalHandler().body(), "globalHandler", visitor);
    }

    /**
     * Visits every command of a list, depth first, in order.
     *
     * @param commands the commands; may be null.
     * @param prefix   the path of the list, such as {@code commands} or {@code commands[2].then}.
     * @param visitor  receives the path and the command.
     */
    public static void walk(List<Command> commands, String prefix, BiConsumer<String, Command> visitor) {
        if (commands == null) return;

        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            String path = prefix + "[" + i + "]";
            visitor.accept(path, command);
            walk(command.then(), path + ".then", visitor);
            walk(command.otherwise(), path + ".otherwise", visitor);
            walk(command.body(), path + ".body", visitor);
            if (command.thenRun() != null) walk(command.thenRun().body(), path + ".thenRun", visitor);
            if (command.handler() != null) walk(command.handler().body(), path + ".handler", visitor);
        }
    }
}
