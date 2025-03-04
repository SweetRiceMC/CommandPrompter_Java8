/*
 * MIT License
 *
 * Copyright (c) 2020 Ethan Bacurio
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.cyr1en.commandprompter.prompt;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.api.Dispatcher;
import com.cyr1en.commandprompter.api.prompt.Prompt;
import com.cyr1en.commandprompter.prompt.prompts.AnvilPrompt;
import com.cyr1en.commandprompter.prompt.prompts.ChatPrompt;
import com.cyr1en.commandprompter.prompt.prompts.PlayerUIPrompt;
import com.cyr1en.commandprompter.prompt.prompts.SignPrompt;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.fusesource.jansi.Ansi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Class that would manage all prompts.
 * <p>
 * We need to register a new prompt into this map. And we simply do that by appending a new
 * Prompt class with its optional argument key.
 * <p>
 * i.e: For chat prompt, the key would just be an empty string, and for an anvil prompt the key
 * would be 'a'.
 */
public class PromptManager extends HashMap<String, Class<? extends Prompt>> {

    private final CommandPrompter plugin;
    private final PromptRegistry promptRegistry;
    private final PromptParser promptParser;
    private final BukkitScheduler scheduler;

    public PromptManager(CommandPrompter commandPrompter) {
        this.plugin = commandPrompter;
        this.promptRegistry = new PromptRegistry(plugin);
        this.promptParser = new PromptParser(this);
        this.scheduler = Bukkit.getScheduler();
        registerPrompts();
    }

    private void registerPrompts() {
        this.put("", ChatPrompt.class);
        this.put("a", AnvilPrompt.class);
        this.put("p", PlayerUIPrompt.class);
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") != null)
            this.put("s", SignPrompt.class);
        else
            plugin.getPluginLogger().warn("ProtocolLib not found. Sign GUI prompt is disabled.");
    }

    @Override
    public Class<? extends Prompt> put(String key, Class<? extends Prompt> value) {
        Class<? extends Prompt> ret = super.put(key, value);
        plugin.getPluginLogger().info("Registered " +
                new Ansi().fgRgb(153, 214, 90).a(value.getSimpleName()));
        return ret;
    }

    public void parse(PromptContext context) {
        int queueHash = promptParser.parsePrompts(context);
        int timeout = plugin.getConfiguration().promptTimeout;
        scheduler.runTaskLater(plugin, () -> cancel(context.getSender(), queueHash), 20L * timeout);
    }

    public void sendPrompt(CommandSender sender) {
        if (!promptRegistry.containsKey(sender)) return;
        if (promptRegistry.get(sender).isEmpty()) return;
        plugin.getPluginLogger().debug("PromptQueue for %s: %s", sender.getName(), promptRegistry.get(sender));
        Prompt prompt = Objects.requireNonNull(promptRegistry.get(sender).peek());
        Bukkit.getScheduler().runTaskLater(plugin, prompt::sendPrompt, 2L);
        plugin.getPluginLogger().debug("Sent %s to %s", prompt.getClass().getSimpleName(), sender.getName());
    }

    public void processPrompt(PromptContext context) {
        CommandSender sender = context.getSender();

        if (!getPromptRegistry().containsKey(sender)) return;
        if (promptRegistry.get(sender).isEmpty()) return;

        getPromptRegistry().get(sender).poll();
        getPromptRegistry().get(sender).addCompleted(context.getContent());
        plugin.getPluginLogger().debug("PromptQueue for %s: %s", sender.getName(), promptRegistry.get(sender));
        if (promptRegistry.get(sender).isEmpty()) {
            PromptQueue queue = promptRegistry.get(sender);

            boolean isCurrentOp = sender.isOp();
            plugin.getPluginLogger().debug("Is Currently OP?: " + isCurrentOp);
            plugin.getPluginLogger().debug("PromptQueue OP: " + queue.isOp());
            if (queue.isOp() && !isCurrentOp) {
                sender.setOp(true);
                plugin.getPluginLogger().debug("Gave OP status temporarily");
            }
            plugin.getPluginLogger().debug("Dispatching for %s: %s", sender.getName(), queue.getCompleteCommand());
            if (plugin.getConfiguration().showCompleted)
                plugin.getMessenger().sendMessage(sender, plugin.getI18N()
                        .getFormattedProperty("CompletedCommand", queue.getCompleteCommand()));

            if(queue.isSetPermissionAttachment())
                Dispatcher.dispatchWithAttachment(plugin, (Player) sender, queue.getCompleteCommand(),
                        plugin.getConfiguration().permissionAttachmentTicks,
                        plugin.getConfiguration().attachmentPermissions.toArray(new String[0]));
            else
                Dispatcher.dispatchCommand(plugin, (Player) sender, queue.getCompleteCommand());

            if (!isCurrentOp) {
                sender.setOp(false);
                plugin.getPluginLogger().debug("Remove OP status");
                // Redundancy for de-op
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getPluginLogger().debug("Remove OP status (redundancy)");
                    sender.setOp(false);
                }, 2L);
            }
            promptRegistry.unregister(sender);
        } else if (sender instanceof Player)
            sendPrompt(sender);

    }

    public PromptRegistry getPromptRegistry() {
        return promptRegistry;
    }

    public PromptParser getParser() {
        return promptParser;
    }

    public void cancel(CommandSender sender, int queueHash) {
        if (!promptRegistry.containsKey(sender)) return;
        plugin.getPluginLogger().debug("queueHash: " + queueHash);
        plugin.getPluginLogger().debug("registryQueueHash: " + promptRegistry.get(sender).hashCode());
        if (queueHash != -1 && queueHash != promptRegistry.get(sender).hashCode()) return;
        promptRegistry.unregister(sender);
        plugin.getMessenger().sendMessage(sender, plugin.getI18N().getProperty("PromptCancel"));
        plugin.getPluginLogger().debug("Command completion called for: %s", sender.getName());
    }

    public void cancel(CommandSender sender) {
        cancel(sender, -1);
    }

    public Pattern getArgumentPattern() {
        String pattern = "-(%s) ";
        HashSet<String> keySet = new HashSet<>(this.keySet());
        keySet.remove("");
        String arguments = String.join("|", keySet);
        Pattern compiled = Pattern.compile(String.format(pattern, arguments));
        plugin.getPluginLogger().debug("ArgumentPattern: " + compiled);
        return compiled;
    }

    public void clearPromptRegistry() {
        promptRegistry.clear();
    }

    public CommandPrompter getPlugin() {
        return plugin;
    }
}
