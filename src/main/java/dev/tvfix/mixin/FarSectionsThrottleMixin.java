package dev.tvfix.mixin;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.tvfix.UploadBudget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Целевой класс принадлежит Time Vortex, поэтому указан строкой.
 *
 * FarSections.frame() выглядит так:
 *   while ((task = MADE.poll()) != null) task.run();   // без ограничений
 * Мы подменяем poll(): когда бюджет кадра исчерпан, он возвращает null, цикл завершается,
 * а оставшиеся задачи остаются в очереди до следующего кадра.
 */
@Mixin(targets = "com.plaaasma.timevortex.client.seam.FarSections")
public abstract class FarSectionsThrottleMixin {
	@Inject(method = "frame()V", at = @At("HEAD"))
	private static void timevortexBotiFix$begin(CallbackInfo ci) {
		UploadBudget.begin();
	}

	@WrapOperation(
		method = "frame()V",
		at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentLinkedQueue;poll()Ljava/lang/Object;")
	)
	private static Object timevortexBotiFix$poll(ConcurrentLinkedQueue<?> queue, Operation<Object> original) {
		if (!UploadBudget.mayTake()) {
			if (!queue.isEmpty()) {
				UploadBudget.deferred(queue.size());
			}
			return null;
		}
		Object next = original.call(queue);
		if (next != null) {
			UploadBudget.took();
		}
		return next;
	}
}
