package io.github.yeray06.wavedefense.game;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs code on libGDX's headless backend. It has no GL, so a no-op {@link GL20} is installed that
 * reports shaders as compiled and linked: rendering code runs end to end, nothing is drawn.
 */
public final class Headless {
  private static final int GL_COMPILE_STATUS = 0x8B81;
  private static final int GL_LINK_STATUS = 0x8B82;

  private Headless() {}

  /** A test step that may throw. */
  @FunctionalInterface
  public interface Step {
    void run() throws Exception;
  }

  /** Runs {@code step} on the libGDX thread, once the application is created, then exits. */
  public static void run(Step step) {
    run(
        new com.badlogic.gdx.ApplicationAdapter() {
          @Override
          public void create() {
            try {
              step.run();
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
            Gdx.app.exit();
          }
        },
        0);
  }

  /**
   * Runs a whole application for at least {@code frames} frames and fails on any exception.
   *
   * @return frames rendered
   */
  public static int run(ApplicationListener app, int frames) {
    CountDownLatch disposed = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    int[] rendered = {0};
    ApplicationListener probe =
        new ApplicationListener() {
          @Override
          public void create() {
            Gdx.gl = Gdx.gl20 = noOpGl();
            guard(app::create);
            guard(() -> app.resize(1280, 720));
          }

          @Override
          public void resize(int width, int height) {
            guard(() -> app.resize(width, height));
          }

          @Override
          public void render() {
            guard(app::render);
            if (++rendered[0] >= frames) {
              Gdx.app.exit();
            }
          }

          @Override
          public void pause() {
            guard(app::pause);
          }

          @Override
          public void resume() {
            guard(app::resume);
          }

          @Override
          public void dispose() {
            guard(app::dispose);
            disposed.countDown();
          }

          private void guard(Runnable step) {
            try {
              step.run();
            } catch (RuntimeException | Error e) {
              failure.compareAndSet(null, e);
              Gdx.app.exit();
            }
          }
        };
    HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
    config.updatesPerSecond = 240;
    new HeadlessApplication(probe, config);
    try {
      if (!disposed.await(30, TimeUnit.SECONDS)) {
        throw new AssertionError("application did not shut down");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    if (failure.get() != null) {
      throw new AssertionError("application failed", failure.get());
    }
    return rendered[0];
  }

  private static GL20 noOpGl() {
    return (GL20)
        Proxy.newProxyInstance(
            GL20.class.getClassLoader(),
            new Class<?>[] {GL20.class},
            (proxy, method, args) -> {
              String name = method.getName();
              if (method.getReturnType() == int.class
                  && (name.startsWith("glCreate") || name.startsWith("glGen"))) {
                return 1;
              }
              if ((name.equals("glGetShaderiv") || name.equals("glGetProgramiv"))
                  && args[2] instanceof IntBuffer status) {
                int pname = (Integer) args[1];
                status.put(0, pname == GL_COMPILE_STATUS || pname == GL_LINK_STATUS ? 1 : 0);
                return null;
              }
              return defaultValue(method.getReturnType());
            });
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
    if (type == String.class) {
      return "";
    }
    return null;
  }
}
