package com.supermartijn642.tesseract;

import com.supermartijn642.tesseract.manager.Channel;
import com.supermartijn642.tesseract.manager.TesseractChannelManager;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created 3/19/2020 by SuperMartijn642
 */
public class TesseractTile extends TileEntity {

    private static final IItemHandler EMPTY_ITEM_HANDLER = new IItemHandler() {
        public int getSlots(){
            return 0;
        }

        public ItemStack getStackInSlot(int slot){
            return ItemStack.EMPTY;
        }

        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate){
            return stack;
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate){
            return ItemStack.EMPTY;
        }

        public int getSlotLimit(int slot){
            return 0;
        }
    };

    private static final IFluidHandler EMPTY_FLUID_HANDLER = new IFluidHandler() {
        public IFluidTankProperties[] getTankProperties(){
            return new IFluidTankProperties[0];
        }

        public int fill(FluidStack resource, boolean doFill){
            return 0;
        }

        public FluidStack drain(FluidStack resource, boolean doDrain){
            return null;
        }

        public FluidStack drain(int maxDrain, boolean doDrain){
            return null;
        }
    };
    private static final IEnergyStorage EMPTY_ENERGY_STORAGE = new IEnergyStorage() {
        public int receiveEnergy(int maxReceive, boolean simulate){
            return 0;
        }

        public int extractEnergy(int maxExtract, boolean simulate){
            return 0;
        }

        public int getEnergyStored(){
            return 0;
        }

        public int getMaxEnergyStored(){
            return 0;
        }

        public boolean canExtract(){
            return true;
        }

        public boolean canReceive(){
            return true;
        }
    };

    private final HashMap<EnumChannelType,Integer> channels = new HashMap<>();
    private final HashMap<EnumChannelType,TransferState> transferState = new HashMap<>();
    private RedstoneState redstoneState = RedstoneState.DISABLED;
    private boolean redstone;

    private boolean dataChanged = false;

    public TesseractTile(){
        for(EnumChannelType type : EnumChannelType.values()){
            this.channels.put(type, -1);
            this.transferState.put(type, TransferState.BOTH);
        }
    }

    public void setChannel(EnumChannelType type, int channel){
        if(channel == this.channels.get(type))
            return;
        Channel oldChannel = this.getChannel(type);
        this.channels.put(type, channel);
        if(oldChannel != null)
            oldChannel.removeTesseract(this);
        Channel newChannel = this.getChannel(type);
        if(newChannel != null)
            newChannel.addTesseract(this);
        this.dataChanged();
    }

    public boolean renderOn(){
        return this.redstoneState == RedstoneState.DISABLED || this.redstoneState == (this.redstone ? RedstoneState.HIGH : RedstoneState.LOW);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing){
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ||
            capability == CapabilityEnergy.ENERGY || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing){
        if(capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY){
            if(this.getChannel(EnumChannelType.ITEMS) == null)
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(EMPTY_ITEM_HANDLER);
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.getChannel(EnumChannelType.ITEMS).getItemHandler(this));
        }
        if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY){
            if(this.getChannel(EnumChannelType.FLUID) == null)
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(EMPTY_FLUID_HANDLER);
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.getChannel(EnumChannelType.FLUID).getFluidHandler(this));
        }
        if(capability == CapabilityEnergy.ENERGY){
            if(this.getChannel(EnumChannelType.ENERGY) == null)
                return CapabilityEnergy.ENERGY.cast(EMPTY_ENERGY_STORAGE);
            return CapabilityEnergy.ENERGY.cast(this.getChannel(EnumChannelType.ENERGY).getEnergyStorage(this));
        }
        return super.getCapability(capability, facing);
    }

    public <T> List<T> getSurroundingCapabilities(Capability<T> capability){
        ArrayList<T> list = new ArrayList<>();
        for(EnumFacing facing : EnumFacing.values()){
            TileEntity tile = this.getWorld().getTileEntity(this.pos.offset(facing));
            if(tile != null && !(tile instanceof TesseractTile) && tile.hasCapability(capability, facing.getOpposite())){
                T handler = tile.getCapability(capability, facing.getOpposite());
                if(handler != null)
                    list.add(handler);
            }
        }
        return list;
    }

    public boolean canSend(EnumChannelType type){
        return this.transferState.get(type).canSend() &&
            this.redstoneState == RedstoneState.DISABLED || this.redstoneState == (this.redstone ? RedstoneState.HIGH : RedstoneState.LOW);
    }

    public boolean canReceive(EnumChannelType type){
        return this.transferState.get(type).canReceive() &&
            this.redstoneState == RedstoneState.DISABLED || this.redstoneState == (this.redstone ? RedstoneState.HIGH : RedstoneState.LOW);
    }

    public int getChannelId(EnumChannelType type){
        return this.channels.get(type);
    }

    public TransferState getTransferState(EnumChannelType type){
        return this.transferState.get(type);
    }

    public void cycleTransferState(EnumChannelType type){
        TransferState transferState = this.transferState.get(type);
        this.transferState.put(type, transferState == TransferState.BOTH ? TransferState.SEND : transferState == TransferState.SEND ? TransferState.RECEIVE : TransferState.BOTH);
        this.dataChanged();
    }

    public RedstoneState getRedstoneState(){
        return this.redstoneState;
    }

    public void cycleRedstoneState(){
        this.redstoneState = this.redstoneState == RedstoneState.DISABLED ? RedstoneState.HIGH : this.redstoneState == RedstoneState.HIGH ? RedstoneState.LOW : RedstoneState.DISABLED;
        this.dataChanged();
    }

    public void setPowered(boolean powered){
        if(this.redstone != powered){
            this.redstone = powered;
            this.dataChanged();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound){
        super.writeToNBT(compound);
        compound.setTag("data", this.getData());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound){
        super.readFromNBT(compound);
        if(compound.hasKey("data"))
            this.handleData(compound.getCompoundTag("data"));
    }

    @Override
    public NBTTagCompound getUpdateTag(){
        NBTTagCompound compound = super.getUpdateTag();
        compound.setTag("data", this.getData());
        return compound;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound compound){
        super.handleUpdateTag(compound);
        if(compound.hasKey("data"))
            this.handleData(compound.getCompoundTag("data"));
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket(){
        if(this.dataChanged){
            this.dataChanged = false;
            return new SPacketUpdateTileEntity(this.pos, 0, this.getData());
        }
        return null;
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt){
        this.handleData(pkt.getNbtCompound());
    }

    public NBTTagCompound getData(){
        NBTTagCompound compound = new NBTTagCompound();
        for(EnumChannelType type : EnumChannelType.values()){
            compound.setInteger(type.name(), this.channels.get(type));
            compound.setString("transferState" + type.name(), this.transferState.get(type).name());
        }
        compound.setString("redstoneState", this.redstoneState.name());
        compound.setBoolean("powered", this.redstone);
        return compound;
    }

    public void handleData(NBTTagCompound compound){
        for(EnumChannelType type : EnumChannelType.values()){
            this.channels.put(type, compound.getInteger(type.name()));
            if(compound.hasKey("transferState" + type.name()))
                this.transferState.put(type, TransferState.valueOf(compound.getString("transferState" + type.name())));
        }
        if(compound.hasKey("redstoneState"))
            this.redstoneState = RedstoneState.valueOf(compound.getString("redstoneState"));
        if(compound.hasKey("powered"))
            this.redstone = compound.getBoolean("powered");
    }

    private Channel getChannel(EnumChannelType type){
        if(this.channels.get(type) < 0 || this.world == null)
            return null;
        Channel channel = TesseractChannelManager.getInstance(this.world).getChannelById(type, this.channels.get(type));
        if(channel == null && !this.world.isRemote){
            this.channels.put(type, -1);
            this.dataChanged();
        }
        return channel;
    }

    private void dataChanged(){
        this.dataChanged = true;
        IBlockState state = this.world.getBlockState(this.pos);
        this.world.notifyBlockUpdate(this.pos, state, state, 2);
        this.world.notifyNeighborsOfStateChange(this.pos, this.blockType, false);
        this.markDirty();
    }
}
