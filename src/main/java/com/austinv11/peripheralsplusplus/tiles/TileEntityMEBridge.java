package com.austinv11.peripheralsplusplus.tiles;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import com.austinv11.collectiveframework.minecraft.reference.ModIds;
import com.austinv11.peripheralsplusplus.init.ModBlocks;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.utils.IPlusPlusPeripheral;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

@Optional.InterfaceList(value = {
		@Optional.Interface(
				modid="appliedenergistics2",
				iface="appeng.api.networking.security.IActionHost",
				striprefs=true),
		@Optional.Interface(
				modid="appliedenergistics2",
				iface="appeng.api.networking.IGridBlock",
				striprefs=true),
		@Optional.Interface(
				modid="appliedenergistics2",
				iface="appeng.api.networking.IGridHost",
				striprefs=true)
})
public class TileEntityMEBridge extends TileEntity implements IActionHost, IGridBlock, ITickable,
		IGridHost, IPlusPlusPeripheral {
	public static String publicName = "meBridge";
	private String name = "tileEntityMEBridge";
	private HashMap<IComputerAccess, Boolean> computers = new HashMap<>();
	private IGridNode node;
	private boolean initialized = false;
	private EntityPlayer placed;

	public TileEntityMEBridge() {
		super();
	}

	public String getName() {
		return name;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		if (worldObj == null || worldObj.isRemote)
			return;
		if (node != null)
			node.destroy();
		node = AEApi.instance().createGridNode(this);
		node.loadFromNBT("node", nbttagcompound);
		initialized = false;
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);
		if (worldObj == null || worldObj.isRemote)
			return nbttagcompound;
		if (node != null)
			node.saveToNBT("node", nbttagcompound);
		return nbttagcompound;
	}

	@Override
	public void update() {
		if (!worldObj.isRemote)
			if (!initialized) {
				node = AEApi.instance().createGridNode(this);
				if (placed != null)
					node.setPlayerID(AEApi.instance().registries().players().getID(placed));
				node.updateState();
				initialized = true;
			}
	}

	@Override
	public String getType() {
		return publicName;
	}

	@Override
	public String[] getMethodNames() {
		return new String[]{"listAll", "listItems", "listCraft", "retrieve", "craft"};
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments)
			throws LuaException, InterruptedException {
		if (!Config.enableMEBridge)
			throw new LuaException("ME Bridges have been disabled");
		if (!Loader.isModLoaded(ModIds.APPLIED_ENGERGISTICS))
			throw new LuaException("Applied Energistics 2 is not installed");
		IMEMonitor<IAEItemStack> grid = ((IStorageGrid)node.getGrid().getCache(IStorageGrid.class))
				.getItemInventory();
		switch (method) {
			case 0:
				return new Object[]{iteratorToMap(grid.getStorageList().iterator(), 0)};
			case 1:
				return new Object[]{iteratorToMap(grid.getStorageList().iterator(), 1)};
			case 2:
				return new Object[]{iteratorToMap(grid.getStorageList().iterator(), 2)};
			case 3:
				return retrieve(arguments, grid);
			case 4:
				return craft(arguments, grid);
		}
		return new Object[0];
	}

	private Object[] craft(Object[] arguments, IMEMonitor<IAEItemStack> monitor) throws LuaException {
		if (arguments.length < 3)
			throw new LuaException("Too few arguments");
		if (!(arguments[0] instanceof String))
			throw new LuaException("Bad argument #1: name should be a string");
		if (!(arguments[1] instanceof Double))
			throw new LuaException("Bad argument #2: Meta should be an number");
		if (!(arguments[2] instanceof Double))
			throw new LuaException("Bad argument #3: amount should be a number");
		String itemName = (String) arguments[0];
		int meta = (int) (double) arguments[1];
		long amount = (long) (double) arguments[2];
		ItemStack toCraft = GameRegistry.makeItemStack(itemName, meta, 1, "");
		if (toCraft == null)
			throw new LuaException("Failed to find item");
		IAEItemStack aeToCraft = findAEStackFromItemStack(monitor, toCraft);
		if (aeToCraft == null)
			throw new LuaException("Failed to find item in AE system");
		if (!aeToCraft.isCraftable())
			throw new LuaException("AE system cannot craft item");
		aeToCraft = aeToCraft.copy();
		aeToCraft.setStackSize(amount);
		synchronized (this) {
			ICraftingGrid craftingGrid = node.getGrid().getCache(ICraftingGrid.class);
			craftingGrid.beginCraftingJob(worldObj, node.getGrid(), new MachineSource(this), aeToCraft, job -> {
                craftingGrid.submitJob(job, null, null, false,
                        new MachineSource(TileEntityMEBridge.this));
                for (IComputerAccess comp : computers.keySet()) {
                    ResourceLocation itemName1 = ForgeRegistries.ITEMS.getKey(job.getOutput().getItem());
                    comp.queueEvent("craftingComplete", new Object[]{
                            itemName1 == null ? "null" : itemName1.toString(),
                            job.getOutput().getStackSize(),
                            job.getByteTotal()
                    });
                }
            });
		}
		return new Object[]{};
	}

	private Object[] retrieve(Object[] arguments, IMEMonitor<IAEItemStack> monitor) throws LuaException {
		if (arguments.length < 4)
			throw new LuaException("Too few arguments");
		if (!(arguments[0] instanceof String))
			throw new LuaException("Bad argument #1: name should be a string");
		if (!(arguments[1] instanceof Double))
			throw new LuaException("Bad argument #2: Meta should be an number");
		if (!(arguments[2] instanceof Double))
			throw new LuaException("Bad argument #3: amount should be a number");
		if (!(arguments[3] instanceof String) && !(arguments[3] instanceof Double))
			throw new LuaException("Bad argument #4: direction should be a string or number");
		String itemName = (String) arguments[0];
		int meta = (int) (double) arguments[1];
		long amount = (long) (double) arguments[2];
		EnumFacing direction;
		if (arguments[3] instanceof String)
			direction = EnumFacing.valueOf(String.valueOf(arguments[3]).toUpperCase(Locale.US));
		else
			direction = EnumFacing.getFront((int) (double) arguments[3]);
		// Check inventory to output to
		IInventory inventory = TileEntityInteractiveSorter.getInventoryForSide(worldObj, getPos(), direction);
		if (inventory == null)
			throw new LuaException("Block is not a valid inventory");
		// Check item is valid
		ItemStack item = GameRegistry.makeItemStack(itemName, meta, 1, "");
		if (item == null)
			throw new LuaException("Item not found");

		long extracted = 0;
		IAEItemStack stack = findAEStackFromItemStack(monitor, item);
		if (stack != null) {
			if (amount > stack.getStackSize())
				amount = stack.getStackSize();
			if (amount > getRemainingSlots(item.getItem(), inventory))
				amount = getRemainingSlots(item.getItem(), inventory);
			IAEItemStack stackToGet = stack.copy();
			stackToGet.setStackSize(amount);
			IAEItemStack resultant = monitor.extractItems(stackToGet, Actionable.MODULATE, new MachineSource(this));
			if (resultant != null) {
				extracted = resultant.getStackSize();
				int[] slots = inventory instanceof ISidedInventory ?
						((ISidedInventory) inventory).getSlotsForFace(direction.getOpposite()) :
						getDefaultSlots(inventory);
				int currentSlot = 0;
				while (!(resultant.getStackSize() < 1) && currentSlot < slots.length) {
					if (inventory.isItemValidForSlot(slots[currentSlot], new ItemStack(resultant.getItem()))) {
						if (inventory.getStackInSlot(slots[currentSlot]) == null) {
							ItemStack toAdd = new ItemStack(resultant.getItem());
							int stackSize = (int) (resultant.getStackSize() <=
									inventory.getInventoryStackLimit() ? resultant.getStackSize() :
									inventory.getInventoryStackLimit());
							toAdd.stackSize = stackSize;
							inventory.setInventorySlotContents(slots[currentSlot], toAdd);
							resultant.setStackSize(resultant.getStackSize()-stackSize);
						} else {
							ItemStack current = inventory.getStackInSlot(slots[currentSlot]);
							ItemStack toAdd = new ItemStack(resultant.getItem());
							if (current != null && current.isItemEqual(toAdd)) {
								int stackSize = (int) (resultant.getStackSize()+current.stackSize <=
										inventory.getInventoryStackLimit() ?
										resultant.getStackSize()+current.stackSize :
										inventory.getInventoryStackLimit());
								int change = stackSize - current.stackSize;
								current.stackSize = stackSize;
								inventory.setInventorySlotContents(slots[currentSlot], current);
								resultant.setStackSize(resultant.getStackSize()-change);
							}
						}
						inventory.markDirty();
					}
					currentSlot++;
				}
			}
		}
		return new Object[]{extracted};
	}

	private int[] getDefaultSlots(IInventory inventory) {
		int[] array = new int[inventory.getSizeInventory()];
		for (int i = 0; i < inventory.getSizeInventory(); i++)
			array[i] = i;
		return array;
	}

	private int getRemainingSlots(Item item, IInventory inventory) {
		int slots = 0;
		for (int i = 0; i < inventory.getSizeInventory(); i++) {
			if (inventory.isItemValidForSlot(i, new ItemStack(item)) && inventory.getStackInSlot(i) == null)
				slots += inventory.getInventoryStackLimit();
			else if (inventory.isItemValidForSlot(i, new ItemStack(item)) &&
					inventory.getStackInSlot(i).getItem() == item &&
					(inventory.getInventoryStackLimit() >= inventory.getStackInSlot(i).stackSize))
				slots += inventory.getInventoryStackLimit() - inventory.getStackInSlot(i).stackSize;
		}
		return slots;
	}

	@Override
	public void attach(IComputerAccess computer) {
		computers.put(computer, true);
	}

	@Override
	public void detach(IComputerAccess computer) {
		computers.remove(computer);
	}

	@Override
	public boolean equals(IPeripheral other) {
		return (this == other);
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if (node != null) {
			node.destroy();
			node = null;
			initialized = false;
		}
	}

	private IAEItemStack findAEStackFromItemStack(IMEMonitor<IAEItemStack> monitor, ItemStack item) {
		IAEItemStack stack = null;
		for (IAEItemStack temp : monitor.getStorageList()) {
			if (temp.isSameType(item)) {
				stack = temp;
				break;
			}
		}
		return stack;
	}

	private HashMap<Integer, Object> iteratorToMap(Iterator<IAEItemStack> iterator, int flag) {
		HashMap<Integer,Object> map = new HashMap<Integer,Object>();
		int i = 1;
		while (iterator.hasNext()) {
			Object o = getObjectFromStack(iterator.next(), flag);
			if (o != null)
				map.put(i++, o);
		}
		return map;
	}

	private Object getObjectFromStack(IAEItemStack stack, int flag) {
		HashMap<String, Object> map = new HashMap<String, Object>();
		ResourceLocation itemResourceLocation = ForgeRegistries.ITEMS.getKey(stack.getItem());
		String itemName = itemResourceLocation == null ? "null" : itemResourceLocation.toString();
		int meta = stack.getItemDamage();
		long amount = stack.getStackSize();
		String displayName = new ItemStack(stack.getItem()).getDisplayName();
		map.put("name", itemName);
		map.put("meta", meta);
		map.put("amount", amount);
		map.put("displayName", displayName);
		if (flag == 0) {
			return map;
		} else if (flag == 1) {
			if (stack.getStackSize() > 0)
				return map;
		} else if (flag == 2) {
			if (stack.isCraftable())
				return map;
		}
		return null;
	}

	@Override
	public double getIdlePowerUsage() {
		return 1;
	}

	@Override
	public EnumSet<GridFlags> getFlags() {
		return EnumSet.of(GridFlags.REQUIRE_CHANNEL);
	}
	
	
	@Override
	public boolean isWorldAccessible() {
		return true;
	}

	@Override
	public DimensionalCoord getLocation() {
		return new DimensionalCoord(this);
	}

	@Override
	public AEColor getGridColor() {
		return AEColor.TRANSPARENT;
	}

	@Override
	public void onGridNotification(GridNotification notification) {
		for (IComputerAccess computer : computers.keySet())
			computer.queueEvent("gridNotification", new Object[]{notification.toString()});
	}

	@Override
	public void setNetworkStatus(IGrid grid, int channelsInUse) {
	}

	@Override
	public EnumSet<EnumFacing> getConnectableSides() {
		return EnumSet.allOf(EnumFacing.class);
	}

	@Override
	public IGridHost getMachine() {
		return this;
	}

	@Override
	public void gridChanged() {
		for (IComputerAccess computer : computers.keySet())
			computer.queueEvent("gridChanged", new Object[0]);
	}

	@Override
	public ItemStack getMachineRepresentation() {
		return new ItemStack(ModBlocks.ME_BRIDGE);
	}

	@Override
	public IGridNode getActionableNode() {
		return node;
	}

	public void setPlayer(EntityPlayer player) {
		placed = player;
	}

	@Override
	public IGridNode getGridNode(AEPartLocation dir) {
		return node;
	}

	@Override
	public AECableType getCableConnectionType(AEPartLocation dir) {
		return AECableType.COVERED;
	}

	@Override
	public void securityBreak() {
		for (IComputerAccess computer : computers.keySet())
			computer.queueEvent("securityBreak", new Object[0]);
		worldObj.setBlockToAir(getPos());
	}

}
