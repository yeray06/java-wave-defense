package io.github.yeray06.wavedefense.lwjgl3;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWWindowPosCallback;

/**
 * Keeps the game running while the player drags the window (D-48). On Windows, dragging a window
 * enters a system loop that blocks the game's own loop until the mouse is released; GLFW still
 * reports every position change from inside it, so each one draws (and advances) a frame. libGDX
 * already does the same for resizing. With the mouse held still, Windows sends nothing and the game
 * waits.
 */
final class RedrawWhileMoving extends Lwjgl3WindowAdapter {
  private final ApplicationListener game;

  RedrawWhileMoving(ApplicationListener game) {
    this.game = game;
  }

  @Override
  public void created(Lwjgl3Window window) {
    long handle = window.getWindowHandle();
    GLFW.glfwSetWindowPosCallback(
        handle,
        GLFWWindowPosCallback.create(
            (moved, x, y) -> {
              game.render();
              GLFW.glfwSwapBuffers(moved);
            }));
  }
}
