package com.austinv11.peripheralsplusplus.recipe;

import com.austinv11.collectiveframework.minecraft.utils.Colors;
import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.peripheralsplusplus.hooks.ComputerCraftRegistry;
import com.austinv11.peripheralsplusplus.init.ModBlocks;
import com.austinv11.peripheralsplusplus.reference.Config;
import com.austinv11.peripheralsplusplus.reference.Reference;
import com.austinv11.peripheralsplusplus.utils.TurtleUtil;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ContainerRecipePocket implements IRecipe {
    private final ResourceLocation group;
    private ResourceLocation name;

    public ContainerRecipePocket(ResourceLocation group) {
        this.group = group;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World worldIn) {
        ItemStack container = getPeripheralContainer(inv);
        if (container == null)
            return false;
        List<IPocketUpgrade> peripherals = getPeripherals(inv);
        if (peripherals.size() == 0)
            return false;
        // Check there are no extra items
        int items = 0;
        for (int itemStack = 0; itemStack < inv.getSizeInventory(); itemStack++)
            if (inv.getStackInSlot(itemStack) != null)
                items++;
        if (items != peripherals.size() + 1)
            return false;
        NBTTagList contained = getContainedPeripherals(container);
        for (int containedIndex = 0; containedIndex < contained.tagCount(); containedIndex++) {
            String name = contained.getStringTagAt(containedIndex);
            for (IPocketUpgrade upgrade : peripherals)
                if (name.equals(upgrade.getUpgradeID().toString()))
                    return false;
        }
        return contained.tagCount() + peripherals.size() <= Config.maxNumberOfPeripherals;
    }

    private NBTTagList getContainedPeripherals(ItemStack container) {
        NBTTagList tagList = new NBTTagList();
        if (container.getTagCompound() != null && container.getTagCompound()
                .hasKey(Reference.POCKET_PERIPHERAL_CONTAINER)) {
            NBTBase idTag = container.getTagCompound().getTag(Reference.POCKET_PERIPHERAL_CONTAINER);
            if (!(idTag instanceof NBTTagList))
                return tagList;
            return (NBTTagList)idTag;
        }
        return tagList;
    }

    private List<IPocketUpgrade> getPeripherals(InventoryCrafting inventory) {
        List<IPocketUpgrade> peripherals = new ArrayList<>();
        for (int itemIndex = 0; itemIndex < inventory.getSizeInventory(); itemIndex++) {
            ItemStack itemStack = inventory.getStackInSlot(itemIndex);
            if (itemStack == null || itemStack.stackSize != 1)
                continue;
            for (IPocketUpgrade pocketUpgrade : ComputerCraftRegistry.getPocketUpgrades().values())
                if (pocketUpgrade.getCraftingItem().isItemEqual(itemStack) &&
                        !pocketUpgrade.getCraftingItem().isItemEqual(new ItemStack(ModBlocks.PERIPHERAL_CONTAINER)) &&
                        !peripherals.contains(pocketUpgrade))
                    peripherals.add(pocketUpgrade);
        }
        return peripherals;
    }

    @Nullable
    private ItemStack getPeripheralContainer(InventoryCrafting inventory) {
        ItemStack returnStack = null;
        ItemStack pocket = TurtleUtil.getPocket(true);
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack itemStack = inventory.getStackInSlot(slot);
            if (itemStack == null)
                continue;
            itemStack = itemStack.copy();
            if (pocket.getItem() == itemStack.getItem()) {
                if (itemStack.stackSize != 1 || returnStack != null)
                    return null;
                NBTTagCompound tagCompound = itemStack.getTagCompound();
                if (tagCompound == null)
                    continue;
                if (!tagCompound.getString("upgrade").equals(Reference.POCKET_PERIPHERAL_CONTAINER))
                    continue;
                returnStack = itemStack.copy();
            }
        }
        return returnStack;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack container = getPeripheralContainer(inv);
        List<IPocketUpgrade> peripherals = getPeripherals(inv);
        NBTTagList contained = getContainedPeripherals(container);
        List<String> text = new ArrayList<>();
        if (contained.tagCount() == 0)
            text.add(Colors.RESET.toString() + Colors.UNDERLINE + "Contained Peripherals:");
        for (IPocketUpgrade peripheral : peripherals) {
            contained.appendTag(new NBTTagString(peripheral.getUpgradeID().toString()));
            text.add(Colors.RESET + peripheral.getUpgradeID().toString());
        }
        NBTHelper.setTag(container, Reference.POCKET_PERIPHERAL_CONTAINER, contained);
        NBTHelper.addInfo(container, text);
        return container;
    }

    @Override
    public int getRecipeSize() {
        return 9;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return TurtleUtil.getPocket(true);
    }

    @Override
    public ItemStack[] getRemainingItems(InventoryCrafting inv) {
        return new ItemStack[getRecipeSize()];
    }
}
