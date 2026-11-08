package dev.moar.util;

/*? if >=26.3 {*//*
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
*//*?} else if >=26.1 {*//*
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
*//*?} else {*/
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
/*?}*/

/** Uses the client player's swing API for the current Minecraft version. */
public final class PlayerSwing {
    private PlayerSwing() {}

    /*? if >=26.3 {*//*
    public static void swingMainHand(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }
    *//*?} else if >=26.1 {*//*
    public static void swingMainHand(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND);
    }
    *//*?} else {*/
    public static void swingMainHand(ClientPlayerEntity player) {
        player.swingHand(Hand.MAIN_HAND);
    }
    /*?}*/
}
