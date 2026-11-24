package dev.moar.xaero;

import dev.moar.travel.command.TravelCommand;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
/*?}*/

/** A Xaero menu action with a snapshot of the clicked Nether coordinates. */
public final class XaeroTravelOption extends RightClickOption {
    private final int x;
    private final int z;

    public XaeroTravelOption(int index, IRightClickableElement target, int x, int z) {
        super("moar.xaero.travel", index, target);
        this.x = x;
        this.z = z;
    }

    @Override
    public void onAction(Screen screen) {
        if (TravelCommand.startNetherFromMap(x, z)) {
            /*? if >=26.2 {*//*
            Minecraft.getInstance().gui.setScreen(null);
            *//*?} else if >=26.1 {*//*
            Minecraft.getInstance().setScreen(null);
            *//*?} else {*/
            MinecraftClient.getInstance().setScreen(null);
            /*?}*/
        }
    }
}
