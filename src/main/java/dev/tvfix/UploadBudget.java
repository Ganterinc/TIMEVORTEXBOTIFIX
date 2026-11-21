package dev.tvfix;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Бюджет на загрузку "далёких секций" Time Vortex за один кадр.
 *
 * Time Vortex в FarSections.frame() вычерпывает ВСЮ очередь готовых секций за один кадр.
 * После открытия двери их сотни, и всё это заливается в один буфер за один кадр:
 * отсюда огромный пролаг и, на слабых машинах, падение в StagingBuffer.
 * Здесь кадр ограничивается по времени и по количеству секций, остаток ждёт следующих кадров.
 *
 * Значения можно поменять аргументами JVM:
 *   -Dtvbotifix.ms=2.5     (миллисекунды на кадр, по умолчанию 2.5)
 *   -Dtvbotifix.max=16     (секций на кадр, по умолчанию 16)
 * Первая секция берётся всегда, поэтому очередь не может застрять.
 * Вызывается только из потока рендера.
 */
public final class UploadBudget {
	private static final Logger LOGGER = LoggerFactory.getLogger("TimeVortexBotiFix");
	private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

	private static final long BUDGET_NS = (long) (readDouble("tvbotifix.ms", 2.5) * 1_000_000.0);
	private static final int MAX_PER_FRAME = Math.max(1, (int) readDouble("tvbotifix.max", 16));

	private static long frameStart;
	private static int taken;

	private UploadBudget() {
	}

	public static void begin() {
		frameStart = System.nanoTime();
		taken = 0;
	}

	public static boolean mayTake() {
		if (taken == 0) {
			return true;
		}
		return taken < MAX_PER_FRAME && System.nanoTime() - frameStart < BUDGET_NS;
	}

	public static void took() {
		taken++;
	}

	/** Вызывается, когда очередь ещё не пуста, а бюджет кадра исчерпан. */
	public static void deferred(int waiting) {
		if (LOGGED.compareAndSet(false, true)) {
			LOGGER.info("Spreading far-section uploads over several frames (first deferral: {} waiting, limit {} per frame / {} ms).",
				waiting, MAX_PER_FRAME, BUDGET_NS / 1_000_000.0);
		}
	}

	private static double readDouble(String key, double fallback) {
		try {
			String value = System.getProperty(key);
			return value == null ? fallback : Double.parseDouble(value);
		} catch (RuntimeException e) {
			return fallback;
		}
	}
}
