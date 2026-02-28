package dev.litematicaprinter;

import dev.litematicaprinter.command.PrinterCommand;
import dev.litematicaprinter.printer.SchematicPrinter;
import dev.litematicaprinter.schematic.PrinterResourceManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
/*? if >=1.21.10 {*//*
import net.minecraft.util.Identifier;
*//*?}*/
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Litematica Printer — a standalone Fabric mod that automatically places
 * blocks from loaded {@code .litematic} schematics.
 */
public class LitematicaPrinterMod implements ClientModInitializer {

    public static final String MOD_ID = "litematica-printer";
    public static final Logger LOGGER = LoggerFactory.getLogger("LitematicaPrinter");

    private static final SchematicPrinter PRINTER = new SchematicPrinter();

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Litematica Printer initializing...");

        // Register keybinding to toggle the printer
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.litematica-printer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_KP_0,
                /*? if >=1.21.10 {*//*
                KeyBinding.Category.create(Identifier.of("litematica-printer", "category"))
                *//*?} else {*/
                "category.litematica-printer"
                /*?}*/
        ));

        // Register client commands
        PrinterCommand.register();

        // Load saved supply chest positions
        PrinterResourceManager.load();

        // Register tick handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Toggle keybind check
            while (toggleKey.wasPressed()) {
                PRINTER.toggle();
            }

            // Tick the printer
            PRINTER.tick();
        });

        LOGGER.info("Litematica Printer initialized.");
    }

    /** Get the singleton printer instance. */
    public static SchematicPrinter getPrinter() {
        return PRINTER;
    }
}
