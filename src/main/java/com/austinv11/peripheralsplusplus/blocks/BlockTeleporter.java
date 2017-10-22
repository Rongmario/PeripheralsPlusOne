package com.austinv11.peripheralsplusplus.blocks;

import com.austinv11.peripheralsplusplus.reference.Reference;
import com.austinv11.peripheralsplusplus.tiles.TileEntityTeleporter;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class BlockTeleporter extends BlockPppDirectional implements ITileEntityProvider {
	public static final PropertyInteger TIER = PropertyInteger.create("tier", 0, 1);

	public BlockTeleporter() {
		super();
		this.setDefaultState(this.blockState.getBaseState()
				.withProperty(FACING, EnumFacing.NORTH)
				.withProperty(TIER, 0));
		this.setRegistryName(Reference.MOD_ID, "teleporter");
		this.setUnlocalizedName("teleporter");
	}

	@Override
	public TileEntity createNewTileEntity(World p_149915_1_, int p_149915_2_) {
		return new TileEntityTeleporter();
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, FACING, TIER);
	}

	@Override
	public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer,
								ItemStack stack) {
		EnumFacing dir = placer.getHorizontalFacing().getOpposite();
		double eyeHeight = placer.posY + placer.getEyeHeight();

		if (eyeHeight - pos.getY() > 2)
			dir = EnumFacing.UP;
		else if (pos.getY() - eyeHeight > 0)
			dir = EnumFacing.DOWN;
		worldIn.setBlockState(pos,
				state.withProperty(FACING, dir)
						.withProperty(TIER, stack.getItemDamage()), 2);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return (state.getValue(FACING).getIndex() << 1) | state.getValue(TIER);
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(FACING, EnumFacing.getFront(meta >> 1))
				.withProperty(TIER, meta & 1);
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
									EnumHand hand, @Nullable ItemStack heldItem, EnumFacing side, float hitX,
									float hitY, float hitZ) {
		TileEntityTeleporter tp = (TileEntityTeleporter)world.getTileEntity(pos);
		if (tp == null)
			return false;
		if (player.getHeldItem(hand) == null || !player.getHeldItem(hand).isItemEqual(new ItemStack(Items.REPEATER)))
			return false;
		if (!world.isRemote)
			tp.blockActivated(player, hand);
		return true;
	}

	@Override
	public void getSubBlocks(Item item, CreativeTabs tab, List<ItemStack> list) {
		for (int tier : TIER.getAllowedValues())
			list.add(new ItemStack(this, 1, tier));
	}

	@Override
	public int damageDropped(IBlockState state) {
		return state.getValue(TIER);
	}
}
