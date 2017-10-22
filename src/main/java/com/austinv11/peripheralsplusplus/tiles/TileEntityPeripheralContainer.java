package com.austinv11.peripheralsplusplus.tiles;

import com.austinv11.collectiveframework.minecraft.tiles.NetworkedTileEntity;
import com.austinv11.collectiveframework.minecraft.utils.Colors;
import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.init.ModBlocks;
import com.austinv11.peripheralsplusplus.lua.LuaObjectPeripheralWrap;
import com.austinv11.peripheralsplusplus.recipe.ContainedPeripheral;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.IPlusPlusPeripheral;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TileEntityPeripheralContainer extends NetworkedTileEntity implements ITickable, IPlusPlusPeripheral {
	private List<ContainedPeripheral> peripheralsContained = new ArrayList<>();
	public static String publicName = "peripheralContainer";
	private  String name = "tileEntityPeripheralContainer";
	private boolean needsUpdate = false;

	public TileEntityPeripheralContainer() {
		super();
	}

	public String getName() {
		return name;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		if (nbttagcompound.hasKey("peripherals")) {
			NBTBase peripheralsBase = nbttagcompound.getTag("peripherals");
			if (!(peripheralsBase instanceof NBTTagList))
				return;
			NBTTagList peripherals = (NBTTagList) peripheralsBase;
			for (int peripheral = 0; peripheral < peripherals.tagCount(); peripheral++) {
				NBTBase peripheralBase = peripherals.get(peripheral);
				if (!(peripheralBase instanceof NBTTagCompound))
					continue;
				addPeripheral(new ContainedPeripheral((NBTTagCompound) peripheralBase));
			}
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		NBTTagList peripherals = new NBTTagList();
		for (ContainedPeripheral peripheral : peripheralsContained)
			peripherals.appendTag(peripheral.toNbt());
		nbttagcompound.setTag("peripherals", peripherals);
		return nbttagcompound;
	}

	@Override
	public String getType() {
		return publicName;
	}

	@Override
	public String[] getMethodNames() {
		return new String[]{"getContainedPeripherals", "wrapPeripheral"};
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
			throws LuaException, InterruptedException {
		if (!Config.enablePeripheralContainer)
			throw new LuaException("Peripheral Containers have been disabled");
		if (method == 0) {
			HashMap<Integer, String> returnVals = new HashMap<Integer,String>();
			for (int i = 0; i < peripheralsContained.size(); i++)
				returnVals.put(i+1, peripheralsContained.get(i).getPeripheral().getType());
			return new Object[]{returnVals};
		}else if (method == 1) {
			if (arguments.length < 1)
				throw new LuaException("Too few arguments");
			if (!(arguments[0] instanceof String))
				throw new LuaException("Bad argument #1 (expected string)");
			return new Object[]{new LuaObjectPeripheralWrap(getPeripheralByName((String)arguments[0]), computer)};
		}
		return new Object[0];
	}

	@Override
	public boolean equals(IPeripheral other) {
		return (this == other);
	}

	@Override
	public void update() {
		for (ContainedPeripheral peripheral : peripheralsContained) {
		    if (!(peripheral.getPeripheral() instanceof ITickable))
		        continue;
			((ITickable) peripheral.getPeripheral()).update();
			if (needsUpdate) {
				worldObj.markAndNotifyBlock(
			            getPos(),
						worldObj.getChunkFromBlockCoords(getPos()),
						worldObj.getBlockState(getPos()),
						worldObj.getBlockState(pos),
                        2);
			    if (!(peripheral.getPeripheral() instanceof TileEntity))
			        continue;
				((TileEntity) peripheral.getPeripheral()).setWorldObj(worldObj);
                ((TileEntity) peripheral.getPeripheral()).setPos(getPos());
			}
		}
	}

	public void addPeripheral(ContainedPeripheral peripheral) {
		if (peripheral.getPeripheral() == null)
			return;
		peripheralsContained.add(peripheral);
		markDirty();
		if (worldObj != null) {
			worldObj.markAndNotifyBlock(
                    getPos(),
					worldObj.getChunkFromBlockCoords(getPos()),
					worldObj.getBlockState(getPos()),
					worldObj.getBlockState(pos),
                    2);
            if (peripheral.getPeripheral() instanceof TileEntity) {
				((TileEntity) peripheral.getPeripheral()).setWorldObj(worldObj);
				((TileEntity) peripheral.getPeripheral()).setPos(getPos());
			}
		} else
			needsUpdate = true;
	}

	private IPeripheral getPeripheralByName(String name) {
		for (ContainedPeripheral peripheral : peripheralsContained)
			if (peripheral.getPeripheral().getType().equals(name))
				return peripheral.getPeripheral();
		return null;
	}

	@Override
	public void attach(IComputerAccess computer) {
		for (ContainedPeripheral peripheral : peripheralsContained)
			peripheral.getPeripheral().attach(computer);
	}

	@Override
	public void detach(IComputerAccess computer) {
		for (ContainedPeripheral peripheral : peripheralsContained)
			peripheral.getPeripheral().detach(computer);
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if (worldObj.isRemote)
			return;
		ItemStack container = new ItemStack(ModBlocks.PERIPHERAL_CONTAINER);
		NBTTagCompound tag = new NBTTagCompound();
		writeToNBT(tag);
		container.setTagCompound(tag);
		List<String> text = new ArrayList<>();
		text.add(Colors.RESET.toString() + Colors.UNDERLINE + "Contained Peripherals:");
		for (ContainedPeripheral peripheral : peripheralsContained)
			text.add(Colors.RESET + peripheral.getBlockResourceLocation().toString());
		NBTHelper.addInfo(container, text);
		worldObj.spawnEntityInWorld(new EntityItem(worldObj, getPos().getX(), getPos().getY(), getPos().getZ(),
				container.copy()));
	}
}
