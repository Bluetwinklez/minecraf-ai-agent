package com.bluetwinklez.aibuilders.gametest;

import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.config.AiBuildersConfig;
import com.bluetwinklez.aibuilders.entity.AgentNpc;
import com.bluetwinklez.aibuilders.entity.ModEntities;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Opens a real client, spawns a builder next to the player, lets it build a hut and takes screenshots. */
public class BuilderClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Map<BlockPos, BlockState> hut = new HashMap<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				hut.put(new BlockPos(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
				hut.put(new BlockPos(x, 4, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
				for (int y = 1; y < 4; y++) {
					boolean wall = x == 0 || z == 0 || x == 4 || z == 4;
					boolean window = y == 2 && (x == 2 || z == 2);
					if (wall) {
						hut.put(new BlockPos(x, y, z), window ? Blocks.GLASS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
					}
				}
			}
		}
		BuilderGameTests.writeStructurePublic("client_hut", 5, 5, 5, hut);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("gamerule advance_time false");
			singleplayer.getServer().runOnServer(server -> {
				AiBuildersConfig.get().blocksPerSecond = 8;
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				player.snapTo(player.getX(), player.getY(), player.getZ(), 0.0F, 15.0F);
				player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0.0F, 15.0F);
				AgentNpc npc = ModEntities.BUILDER.create(level, EntitySpawnReason.COMMAND);
				npc.snapTo(player.getX() + 1.5, player.getY(), player.getZ() + 3.5, 180.0F, 0.0F);
				npc.setYHeadRot(180.0F);
				npc.setup("Ali", "Notch", player.getUUID());
				level.addFreshEntity(npc);
				BlockPos origin = player.blockPosition().offset(-6, 0, 6);
				npc.taskRunner().start(new BuildTask("client_hut", origin, Mirror.NONE, Rotation.NONE, AiBuildersConfig.BuildMode.CREATIVE), npc, level);
			});
			context.waitTicks(60);
			context.takeScreenshot("aibuilders_npc_start");
			context.waitTicks(400);
			context.takeScreenshot("aibuilders_npc_built");
		}
	}
}
