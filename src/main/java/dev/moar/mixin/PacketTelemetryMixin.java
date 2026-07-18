package dev.moar.mixin;

import dev.moar.util.PacketTelemetry;
import dev.moar.util.PlacementEngine;
import dev.moar.world.SetbackMonitor;
import io.netty.channel.ChannelHandlerContext;
/*? if >=26.1 {*//*
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
*//*?} else {*/
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
/*?}*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Records placement-relevant packets while packet telemetry is enabled.
/*? if >=26.1 {*//*
@Mixin(Connection.class)
*//*?} else {*/
@Mixin(ClientConnection.class)
/*?}*/
public abstract class PacketTelemetryMixin {

    /*? if >=26.1 {*//*
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 0)
    private void moar$recordOutgoingPacket(Packet<?> packet, CallbackInfo ci) {
        SetbackMonitor.get().onOutgoingPacket(packet);
        PacketTelemetry.recordOutgoing(packet);
        PlacementEngine.onOutgoingPacket(packet);
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 0)
    private void moar$recordIncomingPacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        SetbackMonitor.get().onIncomingPacket(packet);
        PacketTelemetry.recordIncoming(packet);
        // Always-on: feeds the server block-echo ledger used to verify
        // placements against protocol ground truth (ghost-block detection).
        PlacementEngine.onIncomingPacket(packet);
    }
    *//*?} else {*/
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), require = 0)
    private void moar$recordOutgoingPacket(Packet<?> packet, CallbackInfo ci) {
        SetbackMonitor.get().onOutgoingPacket(packet);
        PacketTelemetry.recordOutgoing(packet);
        PlacementEngine.onOutgoingPacket(packet);
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), require = 0)
    private void moar$recordIncomingPacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        SetbackMonitor.get().onIncomingPacket(packet);
        PacketTelemetry.recordIncoming(packet);
        // Always-on: feeds the server block-echo ledger used to verify
        // placements against protocol ground truth (ghost-block detection).
        PlacementEngine.onIncomingPacket(packet);
    }
    /*?}*/
}
