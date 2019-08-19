package com.tom.logisticsbridge.network;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import com.tom.logisticsbridge.LogisticsBridge;

import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.exception.TargetNotFoundException;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.LogisticsTileGenericPipe;
import logisticspipes.utils.StaticResolve;
import network.rs485.logisticspipes.util.LPDataInput;
import network.rs485.logisticspipes.util.LPDataOutput;

@StaticResolve
public class SetIDPacket extends CoordinatesPacket {
	public String pid;
	public int id;
	public int side;
	public SetIDPacket(int id) {
		super(id);
	}
	@Override
	public void writeData(LPDataOutput output) {
		super.writeData(output);
		output.writeUTF(pid);
		output.writeInt(id);
		output.writeInt(side);
	}
	@Override
	public void readData(LPDataInput input) {
		super.readData(input);
		pid = input.readUTF();
		id = input.readInt();
		side = input.readInt();
	}
	@Override
	public ModernPacket template() {
		return new SetIDPacket(getId());
	}
	public SetIDPacket setName(String pid) {
		this.pid = pid;
		return this;
	}
	public SetIDPacket setId(int id) {
		this.id = id;
		return this;
	}
	public SetIDPacket setPos(CoreRoutedPipe pipe) {
		setBlockPos(pipe.getPos());
		return this;
	}
	@Override
	public void processPacket(EntityPlayer player) {
		if(side != 0){
			LogisticsBridge.processResIDMod(player, this);
		}else{
			try {
				final LogisticsTileGenericPipe pipe = getPipe(player.world, LTGPCompletionCheck.PIPE);
				if (pipe.pipe instanceof IIdPipe) {
					((IIdPipe)pipe.pipe).setPipeID(id, pid, player);
				}
			} catch (TargetNotFoundException e) {
				IIdPipe pp = getTile(player.world, IIdPipe.class);
				if(pp == null)return;
				pp.setPipeID(id, pid, player);
				return;
			}
		}
	}
	/**
			AEPartLocation side = AEPartLocation.fromOrdinal(this.side - 1);
			IPartHost ph = getTile(player.world, IPartHost.class);
			if(ph == null)return;
			IPart p = ph.getPart(side);
			if(p instanceof IIdPipe){
				if(inc) ((IIdPipe) p).setNextId(player, id);
				else    ((IIdPipe) p).setPrevId(player, id);
			}
	 * */
	public SetIDPacket setSide(int side){
		this.side = side + 1;
		return this;
	}
	public static interface IIdPipe {
		String getPipeID(int id);
		void setPipeID(int id, String pipeID, EntityPlayer player);
		String getName(int id);
		default List<String> list(int id){return Collections.emptyList();}
	}
}
