package com.bluetwinklez.aibuilders.entity;

import com.bluetwinklez.aibuilders.build.BuildTask;
import com.bluetwinklez.aibuilders.task.AgentTask;
import com.bluetwinklez.aibuilders.task.TaskRunner;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** A player-looking NPC that works on tasks (currently: building schematics). */
public class AgentNpc extends PathfinderMob implements InventoryCarrier {
	public static final EntityDataAccessor<ResolvableProfile> DATA_PROFILE = SynchedEntityData.defineId(
		AgentNpc.class, EntityDataSerializers.RESOLVABLE_PROFILE
	);
	public static final int INVENTORY_SIZE = 27;

	private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
	private final TaskRunner taskRunner = new TaskRunner();
	private String npcName = "Builder";
	private @Nullable UUID owner;
	private Component lastStatus = Component.empty();

	public AgentNpc(final EntityType<? extends AgentNpc> type, final Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setCustomNameVisible(true);
		// Cast: in 26.2 the method lives on GroundPathNavigation, in 26.3 on PathNavigation.
		if (this.getNavigation() instanceof GroundPathNavigation nav) {
			nav.setCanOpenDoors(true);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_PROFILE, ResolvableProfile.Static.EMPTY);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		taskRunner.tick(this, level);
		Component status = taskRunner.status();
		if (!status.equals(lastStatus)) {
			lastStatus = status;
			refreshDisplayName();
		}
	}

	// --- identity -------------------------------------------------------------

	public void setup(String name, String skinPlayer, @Nullable UUID owner) {
		this.npcName = name;
		this.owner = owner;
		this.entityData.set(DATA_PROFILE, ResolvableProfile.createUnresolved(skinPlayer));
		refreshDisplayName();
	}

	public String npcName() {
		return npcName;
	}

	public ResolvableProfile getProfile() {
		return this.entityData.get(DATA_PROFILE);
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public boolean isOwner(Player player) {
		return owner != null && owner.equals(player.getUUID());
	}

	public @Nullable ServerPlayer ownerPlayer(ServerLevel level) {
		return owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
	}

	private void refreshDisplayName() {
		Component name = Component.literal(npcName);
		if (!lastStatus.getString().isEmpty()) {
			name = name.copy().append(Component.literal(" [").append(lastStatus).append("]").withStyle(ChatFormatting.GRAY));
		}
		this.setCustomName(name);
	}

	/** Sends a chat line as this NPC to one player. */
	public void tell(ServerPlayer player, Component message) {
		player.sendSystemMessage(prefix().append(message));
	}

	/** Sends a chat line as this NPC to all players within {@code radius} blocks. */
	public void say(ServerLevel level, Component message, double radius) {
		Component line = prefix().append(message);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) <= radius * radius) {
				player.sendSystemMessage(line);
			}
		}
	}

	private net.minecraft.network.chat.MutableComponent prefix() {
		return Component.literal("<" + npcName + "> ");
	}

	// --- tasks / inventory ----------------------------------------------------

	public TaskRunner taskRunner() {
		return taskRunner;
	}

	@Override
	public SimpleContainer getInventory() {
		return inventory;
	}

	@Override
	protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		if (!(this.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (!isOwner(player) && !player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
			tell(serverPlayer, Component.translatable("aibuilders.npc.not_owner"));
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.isEmpty()) {
			// Hand the stack over to the NPC; whatever does not fit stays with the player.
			ItemStack remainder = inventory.addItem(held.copy());
			int given = held.getCount() - remainder.getCount();
			if (given > 0) {
				held.shrink(given);
				tell(serverPlayer, Component.translatable("aibuilders.npc.received", given, remainder.isEmpty() ? held.getHoverName() : remainder.getHoverName()));
			} else {
				tell(serverPlayer, Component.translatable("aibuilders.npc.inventory_full"));
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		AgentTask task = taskRunner.task();
		if (task instanceof BuildTask build) {
			build.reportTo(this, level, serverPlayer, true);
		} else {
			tell(serverPlayer, Component.translatable("aibuilders.npc.idle"));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Builders cannot be hurt (only /kill and the void get through), same in 26.2 and 26.3. */
	@Override
	public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public boolean removeWhenFarAway(final double distSqr) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		dropInventory(level);
	}

	public void dropInventory(ServerLevel level) {
		for (ItemStack stack : inventory.removeAllItems()) {
			this.spawnAtLocation(level, stack);
		}
	}

	// --- persistence ----------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("npc_name", npcName);
		output.store("profile", ResolvableProfile.CODEC, getProfile());
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		writeInventoryToTag(output);
		taskRunner.save(output);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		npcName = input.getStringOr("npc_name", "Builder");
		input.read("profile", ResolvableProfile.CODEC).ifPresent(p -> this.entityData.set(DATA_PROFILE, p));
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		readInventoryFromTag(input);
		taskRunner.load(input);
		refreshDisplayName();
	}

	public Optional<BuildTask> buildTask() {
		return taskRunner.task() instanceof BuildTask b ? Optional.of(b) : Optional.empty();
	}
}
