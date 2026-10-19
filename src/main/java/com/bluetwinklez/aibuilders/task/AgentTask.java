package com.bluetwinklez.aibuilders.task;

import com.bluetwinklez.aibuilders.entity.AgentNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueOutput;

/** A long-running job an NPC works on, ticked on the server thread. */
public interface AgentTask {
	enum State { RUNNING, WAITING_MATERIALS, DONE, FAILED }

	/** Id used to restore the task from saved data, see {@link TaskRunner#registerLoader}. */
	String type();

	State tick(AgentNpc npc, ServerLevel level);

	/** Short status shown next to the NPC name, e.g. "Building 45%". */
	Component status();

	void save(ValueOutput output);

	/** Called once when the task ends for any reason (done, failed, stopped). */
	default void onEnd(AgentNpc npc, ServerLevel level) {
	}
}
