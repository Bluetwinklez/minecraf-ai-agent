package com.bluetwinklez.aibuilders.task;

import com.bluetwinklez.aibuilders.AiBuilders;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Holds and ticks the single active task of an NPC. */
public class TaskRunner {
	private static final Map<String, Function<ValueInput, Optional<AgentTask>>> LOADERS = new HashMap<>();

	private @Nullable AgentTask task;
	private boolean paused;
	private AgentTask.State lastState = AgentTask.State.DONE;

	public static void registerLoader(String type, Function<ValueInput, Optional<AgentTask>> loader) {
		LOADERS.put(type, loader);
	}

	public void start(AgentTask newTask, AgentNpc npc, ServerLevel level) {
		stop(npc, level);
		task = newTask;
		paused = false;
		lastState = AgentTask.State.RUNNING;
	}

	public void stop(AgentNpc npc, ServerLevel level) {
		if (task != null) {
			task.onEnd(npc, level);
			task = null;
		}
		paused = false;
		npc.getNavigation().stop();
	}

	public void tick(AgentNpc npc, ServerLevel level) {
		if (task == null || paused) {
			return;
		}
		lastState = task.tick(npc, level);
		if (lastState == AgentTask.State.DONE || lastState == AgentTask.State.FAILED) {
			task.onEnd(npc, level);
			task = null;
		}
	}

	public boolean hasTask() {
		return task != null;
	}

	public @Nullable AgentTask task() {
		return task;
	}

	public boolean isPaused() {
		return paused;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	public AgentTask.State lastState() {
		return lastState;
	}

	public Component status() {
		if (task == null) {
			return Component.empty();
		}
		return paused ? Component.translatable("aibuilders.status.paused") : task.status();
	}

	public void save(ValueOutput output) {
		if (task != null) {
			ValueOutput child = output.child("task");
			child.putString("type", task.type());
			child.putBoolean("paused", paused);
			task.save(child);
		}
	}

	public void load(ValueInput input) {
		input.child("task").ifPresent(child -> {
			String type = child.getStringOr("type", "");
			paused = child.getBooleanOr("paused", false);
			Function<ValueInput, Optional<AgentTask>> loader = LOADERS.get(type);
			if (loader == null) {
				AiBuilders.LOGGER.warn("Unknown saved task type '{}', dropping it", type);
				return;
			}
			task = loader.apply(child).orElse(null);
		});
	}
}
