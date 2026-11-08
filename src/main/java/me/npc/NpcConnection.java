package me.npc;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/** A network connection that goes nowhere. Packets sent to the NPC are discarded. */
public class NpcConnection extends Connection {

    public NpcConnection() {
        super(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(this); // fires channelActive, which sets the internal channel
    }

    @Override public void setReadOnly() { }
    @Override public void handleDisconnection() { }
    @Override public void setListenerForServerboundHandshake(PacketListener listener) { }
    @Override public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> info, T listener) { }

    // Drop outgoing packets so the embedded channel doesn't buffer them forever.
    public void send(Packet<?> packet) { }
    public void send(Packet<?> packet, ChannelFutureListener listener) { }
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) { }
}
