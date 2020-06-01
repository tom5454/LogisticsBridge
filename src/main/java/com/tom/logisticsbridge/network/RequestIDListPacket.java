package com.tom.logisticsbridge.network;

import net.minecraft.entity.player.EntityPlayer;

import com.tom.logisticsbridge.LogisticsBridge;
import com.tom.logisticsbridge.network.SetIDPacket.IIdPipe;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.exception.TargetNotFoundException;
import logisticspipes.pipes.basic.CoreUnroutedPipe;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.StaticResolve;
import network.rs485.logisticspipes.util.LPDataInput;
import network.rs485.logisticspipes.util.LPDataOutput;

@StaticResolve
public class RequestIDListPacket extends CoordinatesPacket {
	public int side, id;

	public RequestIDListPacket(int id) {
		super(id);
	}

	@Override
	public void processPacket(EntityPlayer player) {
		IIdPipe rPipe;
		if(side != 0){
			rPipe = LogisticsBridge.processReqIDList(player, this);
		}else{
			try {
				CoreUnroutedPipe cpipe = getPipe(player.getEntityWorld(), LTGPCompletionCheck.PIPE).pipe;
				if (!(cpipe instanceof IIdPipe)) {
					throw new TargetNotFoundException(null, this);
				}
				rPipe = (IIdPipe) cpipe;
			} catch(Exception e){
				IIdPipe pp = getTileAs(player.world, IIdPipe.class);
				if(pp == null)return;
				rPipe = pp;
			}
		}
		if (rPipe == null) {
			return;
		}

		MainProxy.sendPacketToPlayer(PacketHandler.getPacket(ProvideIDListPacket.class).setList(rPipe.list(id)), player);
	}

	@Override
	public void writeData(LPDataOutput output) {
		super.writeData(output);
		output.writeByte(side);
		output.writeInt(id);
	}
	@Override
	public void readData(LPDataInput input) {
		super.readData(input);
		side = input.readByte();
		id = input.readInt();
	}

	@Override
	public ModernPacket template() {
		return new RequestIDListPacket(getId());
	}

	public RequestIDListPacket setSide(int side) {
		this.side = side;
		return this;
	}

	public RequestIDListPacket setId(int id) {
		this.id = id;
		return this;
	}
}
