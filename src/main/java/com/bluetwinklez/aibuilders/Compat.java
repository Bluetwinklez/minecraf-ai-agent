package com.bluetwinklez.aibuilders;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * Small bridges for APIs that differ between 26.2 and 26.3, so one jar runs on both.
 * Arm swing: 26.2 has {@code swing(InteractionHand)}, 26.3 {@code swing(InteractionHand, SwingAnimation, boolean)}.
 */
public final class Compat {
	private static final MethodHandle SWING = findSwing();
	private static final Object DEFAULT_SWING = findDefaultSwing();

	private Compat() {
	}

	public static void swingMainHand(LivingEntity entity) {
		if (SWING == null) {
			return;
		}
		try {
			if (DEFAULT_SWING != null) {
				SWING.invoke(entity, InteractionHand.MAIN_HAND, DEFAULT_SWING, false);
			} else {
				SWING.invoke(entity, InteractionHand.MAIN_HAND);
			}
		} catch (Throwable t) {
			AiBuilders.LOGGER.debug("swing failed", t);
		}
	}

	private static Object findDefaultSwing() {
		try {
			Class<?> cls = Class.forName("net.minecraft.world.item.component.SwingAnimation");
			return cls.getField("DEFAULT").get(null);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static MethodHandle findSwing() {
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		try {
			Class<?> anim = Class.forName("net.minecraft.world.item.component.SwingAnimation");
			return lookup.findVirtual(LivingEntity.class, "swing", MethodType.methodType(boolean.class, InteractionHand.class, anim, boolean.class))
				.asType(MethodType.methodType(void.class, LivingEntity.class, InteractionHand.class, Object.class, boolean.class));
		} catch (ReflectiveOperationException ignored) {
			// 26.2
		}
		try {
			return lookup.findVirtual(LivingEntity.class, "swing", MethodType.methodType(void.class, InteractionHand.class));
		} catch (ReflectiveOperationException e) {
			AiBuilders.LOGGER.warn("No arm swing method found; NPC arm animations disabled");
			return null;
		}
	}
}
