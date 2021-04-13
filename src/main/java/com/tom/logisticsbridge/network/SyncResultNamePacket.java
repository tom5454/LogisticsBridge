package com.tom.logisticsbridge.network;

import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.packets.satpipe.SyncSatelliteNamePacket;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.StaticResolve;
import net.minecraft.entity.player.EntityPlayer;
import network.rs485.logisticspipes.SatellitePipe;

@StaticResolve
public class SyncResultNamePacket extends SyncSatelliteNamePacket {

    public SyncResultNamePacket(int id) {
        super(id);
    }

    @Override
    public void processPacket(EntityPlayer player) {
        final LogisticsTileGenericPipe pipe = getPipe(player.world, LTGPCompletionCheck.PIPE);
        if (pipe == null || pipe.pipe == null) {
            return;
        }

        if(pipe.pipe instanceof SatellitePipe){
            ((SatellitePipe) pipe.pipe).setSatellitePipeName(getString());
        }
    }

    @Override
    public ModernPacket template() {
        return new SyncResultNamePacket(getId());
    }
}
