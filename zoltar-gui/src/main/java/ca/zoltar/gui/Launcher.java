package ca.zoltar.gui;

/**
 * Launcher class that does not extend Application.
 * This is required when JavaFX is on the module path.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
