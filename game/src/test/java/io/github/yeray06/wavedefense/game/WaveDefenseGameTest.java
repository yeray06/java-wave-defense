package io.github.yeray06.wavedefense.game;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import io.github.yeray06.wavedefense.platform.NoOpPlatformServices;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Runs the real game lifecycle on libGDX's headless backend. The headless backend has no GL, so a
 * no-op {@link GL20} is installed: this checks the lifecycle, not what is drawn.
 */
class WaveDefenseGameTest {
  private static final int FRAMES = 10;

  @Test
  void runsCreateRenderAndDisposeWithoutErrors() throws InterruptedException {
    CountDownLatch disposed = new CountDownLatch(1);
    AtomicInteger frames = new AtomicInteger();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    WaveDefenseGame game = new WaveDefenseGame(new NoOpPlatformServices());

    ApplicationListener probe =
        new ApplicationListener() {
          @Override
          public void create() {
            Gdx.gl = Gdx.gl20 = noOpGl();
            guard(game::create);
          }

          @Override
          public void resize(int width, int height) {
            guard(() -> game.resize(width, height));
          }

          @Override
          public void render() {
            guard(game::render);
            if (frames.incrementAndGet() == FRAMES) {
              Gdx.app.exit();
            }
          }

          @Override
          public void pause() {
            guard(game::pause);
          }

          @Override
          public void resume() {
            guard(game::resume);
          }

          @Override
          public void dispose() {
            guard(game::dispose);
            disposed.countDown();
          }

          private void guard(Runnable step) {
            try {
              step.run();
            } catch (RuntimeException e) {
              failure.compareAndSet(null, e);
              Gdx.app.exit();
            }
          }
        };

    HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
    config.updatesPerSecond = 120;
    new HeadlessApplication(probe, config);

    assertTrue(disposed.await(10, TimeUnit.SECONDS), "game did not shut down");
    assertNull(failure.get(), () -> "game failed: " + failure.get());
    // exit() is queued, so the backend may run one more frame before disposing.
    assertTrue(frames.get() >= FRAMES, () -> "only " + frames.get() + " frames rendered");
  }

  private static GL20 noOpGl() {
    return (GL20)
        Proxy.newProxyInstance(
            GL20.class.getClassLoader(),
            new Class<?>[] {GL20.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
  }

  private static Object defaultValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == float.class) {
      return 0f;
    }
    return null;
  }
}
